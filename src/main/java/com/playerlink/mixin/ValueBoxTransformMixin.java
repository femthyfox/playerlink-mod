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

/**
 * Overrides {@link ValueBoxTransform#testHit} for redstone-link frequency
 * slots only, so a click on the block's visible surface (any depth) registers
 * if it's near the slot's working-face position.
 *
 * Targets the parent class because {@code testHit} isn't redefined in
 * {@code RedstoneLinkFrequencySlot} — but we filter at runtime via an
 * {@code instanceof} check so we don't affect any other Create slots.
 */
@Mixin(value = ValueBoxTransform.class, remap = false)
public abstract class ValueBoxTransformMixin {

    @Inject(method = "testHit", at = @At("HEAD"), cancellable = true, remap = false)
    private void playerlink$overrideTestHit(LevelAccessor level,
                                            BlockPos pos,
                                            BlockState state,
                                            Vec3 localHit,
                                            CallbackInfoReturnable<Boolean> cir) {
        if (!(((Object) this) instanceof RedstoneLinkFrequencySlot self)) return;
        try {
            Direction facing = state.getValue(RedstoneLinkBlock.FACING);
            boolean isFirst = self.isFirst();
            double dist = SlotMath.freqSlotPlanarDistance(facing, isFirst, localHit);
            double tolerance = 2.5 / 16.0;
            cir.setReturnValue(dist < tolerance);
        } catch (Throwable t) {
            // fall through to Create's default
        }
    }
}
