/*
 * Draco Utility - client-side utility mod for Minecraft (Fabric)
 * Copyright (C) 2026 DUCNGUYEN-creator (Draco Studio)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package studio.draco.utility;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;
import java.util.List;

/** Feature state and per-tick logic. Every feature runs only when Access.allowed() is true. */
public final class Features {
    private Features() {}

    // ---- General ----
    public static boolean fly, autoPvp, pvpPlayers;
    public static double flySpeed = 0.6;
    public static final int[] effectLevels = new int[7];
    public static int maxHealth = 20;

    // ---- ESP ----
    public static boolean esp;
    public static boolean espEntities = true, espItems = true, espOres = true, espLabels = true;

    // ---- X-ray ----
    public static boolean xray;
    public static boolean xrayOutline = true;   // border around hidden solid blocks
    public static boolean xrayFullbright = true; // night-vision effect while X-ray is on
    public static int outlineRadius = 3;

    // ---- Guide line ----
    public static boolean guide;
    public static int guideType = 0;            // index into Targets.Kind
    public static int oreFilter = 0;            // index into Targets.ORE_FILTER_NAMES
    public static int oreRadius = 24;
    public static String guideWaypoint = "";
    public static boolean showWaypoints = true;
    public static String guideInfo;             // text for the HUD, set by WorldOverlay

    // ---- Auto click ----
    public static boolean autoClick;
    public static boolean clickPlace;           // false = break / attack, true = place blocks
    public static int clickDelayTicks = 5;

    // ---- Minimap ----
    public static boolean minimap = true;
    public static int minimapSize = 80;
    public static int minimapZoom = 1;          // blocks per minimap cell

    // ---- Effective state, read from render code and mixins (maybe on worker threads) ----
    public static volatile boolean allowedNow;
    public static volatile boolean espActive;
    public static volatile boolean xrayActive;

    public static PlayerEntity nearestPlayer;
    public static double nearestPlayerDist;

    private static boolean clickHeld;
    private static boolean fullbrightApplied;
    private static int clickCooldown;

    private static final double REACH = 3.0;

    public static Targets.Kind guideKind() {
        Targets.Kind[] kinds = Targets.Kind.values();
        return kinds[Math.max(0, Math.min(guideType, kinds.length - 1))];
    }

    public static void tick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        ClientWorld w = mc.world;

        if (p == null || w == null) {
            allowedNow = false;
            espActive = false;
            xrayActive = false;
            clickHeld = false;
            fullbrightApplied = false;
            nearestPlayer = null;
            guideInfo = null;
            OreScanner.tick(mc, false);
            return;
        }

        boolean ok = Access.allowed(mc);
        allowedNow = ok;
        espActive = esp && ok;

        // X-ray: rebuild chunks when the effective state changes.
        boolean wantXray = xray && ok;
        if (wantXray != xrayActive) {
            xrayActive = wantXray;
            mc.worldRenderer.reload();
        }

        tickFullbright(p, xrayActive && xrayFullbright);
        updateNearestPlayer(p, w);

        if (fly && ok) tickFly(p, mc);
        tickCombat(mc, p, ok);

        boolean needOres = ok && ((espActive && espOres) || (guide && guideKind() == Targets.Kind.ORE));
        OreScanner.tick(mc, needOres);
    }

    // ------------------------------------------------------------ fullbright

    /** Client-side night vision, refreshed before it can start to fade. Removed again when X-ray ends. */
    private static void tickFullbright(ClientPlayerEntity p, boolean want) {
        if (want) {
            StatusEffectInstance cur = p.getStatusEffect(StatusEffects.NIGHT_VISION);
            if (cur == null || (!cur.isInfinite() && cur.getDuration() < 600)) {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 1200, 0, false, false, false));
            }
            fullbrightApplied = true;
        } else if (fullbrightApplied) {
            p.removeStatusEffect(StatusEffects.NIGHT_VISION);
            fullbrightApplied = false;
        }
    }

    private static void updateNearestPlayer(ClientPlayerEntity p, ClientWorld w) {
        PlayerEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (AbstractClientPlayerEntity other : w.getPlayers()) {
            if (other == p || other.isSpectator()) continue;
            double d = p.squaredDistanceTo(other);
            if (d < bestD) {
                bestD = d;
                best = other;
            }
        }
        nearestPlayer = best;
        nearestPlayerDist = best == null ? 0 : Math.sqrt(bestD);
    }

    // ---------------------------------------------------------------- fly

    /** Velocity-based flight: works in survival without needing allowFlying on the server abilities. */
    private static void tickFly(ClientPlayerEntity p, MinecraftClient mc) {
        if (!p.isAlive() || p.hasVehicle() || p.isFallFlying()) return;

        double vy = 0;
        if (mc.options.jumpKey.isPressed()) vy += flySpeed;
        if (mc.options.sneakKey.isPressed()) vy -= flySpeed;

        float forward = p.input.movementForward;
        float sideways = p.input.movementSideways; // positive = left
        double yaw = Math.toRadians(p.getYaw());
        double dx = (-Math.sin(yaw) * forward + Math.cos(yaw) * sideways) * flySpeed;
        double dz = (Math.cos(yaw) * forward + Math.sin(yaw) * sideways) * flySpeed;

        p.setVelocity(dx, vy, dz);
        p.fallDistance = 0;
    }

    // ------------------------------------------------------------- combat

    private static void tickCombat(MinecraftClient mc, ClientPlayerEntity p, boolean ok) {
        if (clickCooldown > 0) clickCooldown--;

        if (!ok || mc.currentScreen != null || mc.interactionManager == null) {
            releaseClick(mc);
            return;
        }

        boolean ready = p.getAttackCooldownProgress(0.5f) >= 1.0f;

        if (autoPvp) {
            LivingEntity target = findTarget(p);
            if (target != null) {
                aimAt(p, target);
                if (ready) {
                    mc.interactionManager.attackEntity(p, target);
                    p.swingHand(Hand.MAIN_HAND);
                }
                releaseClick(mc);
                return;
            }
        }

        if (!autoClick) {
            releaseClick(mc);
            return;
        }

        if (clickPlace) {
            // Place mode: right-click the targeted block face every N ticks.
            releaseClick(mc);
            if (clickCooldown == 0 && mc.crosshairTarget instanceof BlockHitResult hit
                    && hit.getType() == HitResult.Type.BLOCK) {
                ActionResult result = mc.interactionManager.interactBlock(p, Hand.MAIN_HAND, hit);
                if (result.shouldSwingHand()) p.swingHand(Hand.MAIN_HAND);
                clickCooldown = clickDelayTicks;
            }
        } else if (mc.crosshairTarget instanceof EntityHitResult hit) {
            // Attack mode on an entity: one hit per delay, never faster than the weapon cooldown.
            releaseClick(mc);
            if (ready && clickCooldown == 0) {
                mc.interactionManager.attackEntity(p, hit.getEntity());
                p.swingHand(Hand.MAIN_HAND);
                clickCooldown = clickDelayTicks;
            }
        } else {
            // Attack mode on a block: hold the attack key so vanilla keeps mining.
            mc.options.attackKey.setPressed(true);
            clickHeld = true;
        }
    }

    private static void releaseClick(MinecraftClient mc) {
        if (clickHeld) {
            mc.options.attackKey.setPressed(false);
            clickHeld = false;
        }
    }

    /** Nearest valid target in reach: hostile mobs by default, players only if enabled. */
    private static LivingEntity findTarget(ClientPlayerEntity p) {
        Box box = p.getBoundingBox().expand(REACH);
        List<LivingEntity> list = p.getWorld().getEntitiesByClass(LivingEntity.class, box, e ->
                e != p
                        && e.isAlive()
                        && !e.isSpectator()
                        && p.distanceTo(e) <= REACH
                        && (e instanceof Monster || (pvpPlayers && e instanceof PlayerEntity)));
        return list.stream().min(Comparator.comparingDouble(p::distanceTo)).orElse(null);
    }

    private static void aimAt(ClientPlayerEntity p, LivingEntity t) {
        Vec3d eye = p.getEyePos();
        Vec3d to = t.getBoundingBox().getCenter();
        double dx = to.x - eye.x;
        double dy = to.y - eye.y;
        double dz = to.z - eye.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        p.setYaw((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0));
        p.setPitch((float) -Math.toDegrees(Math.atan2(dy, horizontal)));
    }
}
