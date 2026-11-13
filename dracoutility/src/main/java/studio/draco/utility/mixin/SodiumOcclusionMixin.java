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

package studio.draco.utility.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import studio.draco.utility.Features;
import studio.draco.utility.XRay;

/**
 * X-ray support for Sodium 0.6.x (package net.caffeinemc). Sodium replaces vanilla's face culling,
 * so the vanilla BlockMixin has no effect when Sodium is installed.
 * @Pseudo + require = 0: silently skipped when Sodium (or this exact method) is not present.
 * The descriptor uses intermediary names because the target is not remapped.
 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockOcclusionCache")
public abstract class SodiumOcclusionMixin {
    @Inject(method = "shouldDrawSide(Lnet/minecraft/class_2680;Lnet/minecraft/class_1922;Lnet/minecraft/class_2338;Lnet/minecraft/class_2350;)Z",
            at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void dracoutility$xray(BlockState state, BlockView world, BlockPos pos, Direction side,
                                   CallbackInfoReturnable<Boolean> cir) {
        if (Features.xrayActive) {
            cir.setReturnValue(!XRay.isHidden(state));
        }
    }
}
