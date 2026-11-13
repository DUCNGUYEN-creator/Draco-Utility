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

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import studio.draco.utility.Features;
import studio.draco.utility.XRay;

@Mixin(Block.class)
public abstract class BlockMixin {
    /**
     * X-ray: hidden blocks draw no faces, every other block always draws all faces.
     * Note: vanilla renderer only. Sodium replaces this culling path.
     */
    @Inject(method = "shouldDrawSide", at = @At("HEAD"), cancellable = true)
    private static void dracoutility$xray(BlockState state, BlockView world, BlockPos pos,
                                          Direction side, BlockPos otherPos,
                                          CallbackInfoReturnable<Boolean> cir) {
        if (Features.xrayActive) {
            cir.setReturnValue(!XRay.isHidden(state));
        }
    }
}
