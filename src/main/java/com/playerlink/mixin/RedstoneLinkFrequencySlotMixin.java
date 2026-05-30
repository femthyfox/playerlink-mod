package com.playerlink.mixin;

import com.playerlink.util.SlotMath;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlock;
import com.simibubi.create.content.redstone.link.RedstoneLinkFrequencySlot;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = RedstoneLinkFrequencySlot.class, remap = false)
public abstract class RedstoneLinkFrequencySlotMixin {

    /** Override Create's slot offset to use our custom positions. */
    @Inject(method = "getLocalOffset", at = @At("HEAD"), cancellable = true, remap = false)
    private void playerlink$overrideOffset(LevelAccessor level,
                                           BlockPos pos,
                                           BlockState state,
                                           CallbackInfoReturnable<Vec3> cir) {
        try {
            boolean isFirst = playerlink$resolveFirst();
            Direction facing = state.getValue(RedstoneLinkBlock.FACING);

            float u = isFirst ? SlotMath.FIRST_U  : SlotMath.SECOND_U;
            float v = isFirst ? SlotMath.FIRST_V  : SlotMath.SECOND_V;

            cir.setReturnValue(SlotMath.localFreqCenter(facing, u, v));
        } catch (Throwable t) {
            // Silently fall through to Create's original
        }
    }

    /**
     * Override Create's 3D distance check with a 2D-in-the-block-face check.
     * Players hit the visible block surface (e.g. plate front on a wall), but
     * the slot's render position may sit at a different depth. Comparing only
     * the axes IN the working-face plane makes hit testing work regardless.
     */
    @Inject(method = "testHit", at = @At("HEAD"), cancellable = true, remap = false)
    private void playerlink$overrideTestHit(LevelAccessor level,
                                            BlockPos pos,
                                            BlockState state,
                                            Vec3 localHit,
                                            CallbackInfoReturnable<Boolean> cir) {
        try {
            boolean isFirst = playerlink$resolveFirst();
            Direction facing = state.getValue(RedstoneLinkBlock.FACING);
            double dist = SlotMath.freqSlotPlanarDistance(facing, isFirst, localHit);
            // 2.5 px radius is a good in-face hit zone — large enough to feel
            // forgiving, small enough that the two slots don't overlap.
            double tolerance = 2.5 / 16.0;
            cir.setReturnValue(dist < tolerance);
        } catch (Throwable t) {
            // Silently fall through to Create's original
        }
    }

    @org.spongepowered.asm.mixin.Unique
    private boolean playerlink$resolveFirst() {
        try {
            return ((ValueBoxTransform.Dual) (Object) this).isFirst();
        } catch (Throwable t) {
            return true;
        }
    }
}
