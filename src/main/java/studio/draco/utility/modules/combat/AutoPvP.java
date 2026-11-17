/*
 * Draco Utility - client framework for Minecraft (Fabric)
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

package studio.draco.utility.modules.combat;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import studio.draco.utility.Draco;
import studio.draco.utility.core.module.Module;
import studio.draco.utility.core.module.ModuleCategory;
import studio.draco.utility.core.policy.Capability;
import studio.draco.utility.core.setting.Setting;
import studio.draco.utility.core.setting.SettingGroup;
import studio.draco.utility.event.TickEvent;
import studio.draco.utility.services.EntityService;
import studio.draco.utility.services.Targets;

/**
 * Attacks the best valid target in reach (kill aura). It follows the normal game rules, because
 * those are what a server enforces: reach is measured from your eyes to the target's hitbox, the
 * attack cooldown must be charged, and the target must be visible. Each rule is a setting, so on
 * your own server or in singleplayer you can loosen them; the active server profile decides whether
 * the module may run at all (it needs the "Combat automation" capability).
 */
public final class AutoPvP extends Module {
    public enum Priority { NEAREST, LOWEST_HEALTH }

    private final Setting.Bool players = general.bool("players", "Players", "Attack other players.", false);
    private final Setting.Bool hostile = general.bool("hostile", "Hostile mobs", "Attack hostile mobs.", true);
    private final Setting.Bool passive = general.bool("passive", "Passive mobs", "Attack passive mobs.", false);
    private final Setting.Dbl range = general.dbl("range", "Range", "Reach in blocks, measured from your eyes to the target's hitbox. Survival reach is 3.0.", 3.0, 1.0, 6.0);
    private final Setting.Bool rotate = general.bool("rotate", "Rotate", "Turn towards the target.", true);

    private final SettingGroup rules = group("Rules");
    private final Setting.Choice<Priority> priority = rules.choice("priority", "Priority", "Which target to pick when several are in reach.", Priority.NEAREST);
    private final Setting.Dbl minCharge = rules.dbl("minCharge", "Attack charge", "Attack only when the cooldown meter is at least this full (1.0 = full damage).", 1.0, 0.1, 1.0);
    private final Setting.Bool lineOfSight = rules.bool("lineOfSight", "Require line of sight", "Do not attack targets you cannot see (behind walls).", true);
    private final Setting.Int fov = rules.integer("fov", "Field of view", "Only targets within this many degrees of where you look. 360 = all around.", 360, 30, 360);
    private final Setting.Bool pauseWhileUsing = rules.bool("pauseWhileUsing", "Pause while using an item", "Do not attack while eating, drawing a bow or blocking.", true);
    private final Setting.Bool ignoreTamed = rules.bool("ignoreTamed", "Ignore your pets", "Never attack tamed animals that belong to you.", true);

    public AutoPvP() {
        super("autopvp", "Auto PvP", "Attacks the best valid target in reach, using the normal reach, cooldown and visibility rules. Needs a profile that allows combat automation.",
                ModuleCategory.COMBAT);
        capabilities(Capability.COMBAT);
        requires(EntityService.class);
        aliases("killaura");
    }

    @Override
    protected void onActivate() {
        Draco.get().services.get(EntityService.class).acquire(this);
        on(TickEvent.Post.class, e -> tick());
    }

    @Override
    protected void onDeactivate() {
        Draco.get().services.get(EntityService.class).release(this);
    }

    private boolean wants(Targets.Kind kind) {
        return switch (kind) {
            case PLAYER -> players.get();
            case HOSTILE -> hostile.get();
            case PASSIVE -> passive.get();
            default -> false;
        };
    }

    /** Squared distance from the eyes to the closest point of the hitbox, which is how reach is measured. */
    private static double reachSq(Vec3d eye, Box box) {
        double x = MathHelper.clamp(eye.x, box.minX, box.maxX);
        double y = MathHelper.clamp(eye.y, box.minY, box.maxY);
        double z = MathHelper.clamp(eye.z, box.minZ, box.maxZ);
        double dx = eye.x - x, dy = eye.y - y, dz = eye.z - z;
        return dx * dx + dy * dy + dz * dz;
    }

    private static double angleTo(ClientPlayerEntity p, Vec3d to) {
        Vec3d eye = p.getEyePos();
        double dx = to.x - eye.x, dz = to.z - eye.z;
        double yawToTarget = Math.toDegrees(Math.atan2(dz, dx)) - 90.0;
        return Math.abs(MathHelper.wrapDegrees(yawToTarget - p.getYaw()));
    }

    private boolean valid(ClientPlayerEntity p, LivingEntity le) {
        if (le == p || !le.isAlive() || le.isInvulnerable()) return false;
        if (ignoreTamed.get() && le instanceof TameableEntity pet && pet.isTamed() && pet.isOwner(p)) return false;
        if (lineOfSight.get() && !p.canSee(le)) return false;
        if (fov.get() < 360 && angleTo(p, le.getBoundingBox().getCenter()) > fov.get() / 2.0) return false;
        return true;
    }

    private void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.interactionManager == null || mc.currentScreen != null) return;
        if (pauseWhileUsing.get() && p.isUsingItem()) return;

        Vec3d eye = p.getEyePos();
        double max = range.get() * range.get();
        LivingEntity target = null;
        double bestScore = Double.MAX_VALUE;
        for (EntityService.Tracked t : Draco.get().services.get(EntityService.class).entities()) {
            if (!wants(t.kind()) || !(t.entity() instanceof LivingEntity le)) continue;
            double reach = reachSq(eye, le.getBoundingBox());
            if (reach > max || !valid(p, le)) continue;
            double score = priority.get() == Priority.LOWEST_HEALTH ? le.getHealth() + reach * 1.0E-3 : reach;
            if (score < bestScore) {
                bestScore = score;
                target = le;
            }
        }
        if (target == null) return;

        if (rotate.get()) {
            Vec3d to = target.getBoundingBox().getCenter();
            double dx = to.x - eye.x, dy = to.y - eye.y, dz = to.z - eye.z;
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            p.setYaw((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0));
            p.setPitch((float) -Math.toDegrees(Math.atan2(dy, horizontal)));
        }
        if (p.getAttackCooldownProgress(0.5f) >= minCharge.get()) {
            mc.interactionManager.attackEntity(p, target);
            p.swingHand(Hand.MAIN_HAND);
        }
    }
}
