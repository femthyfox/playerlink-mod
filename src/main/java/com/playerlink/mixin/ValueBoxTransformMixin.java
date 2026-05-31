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

            double distMine  = SlotMath.freqSlotPlanarDistance(facing, isFirst, localHit);
            double distOther = SlotMath.freqSlotPlanarDistance(facing, !isFirst, localHit);

            // ~4 px clickable radius in the working-face plane. The "closer
            // wins" check prevents both slots from claiming the click when
            // their zones overlap.
            double tolerance = 4.0 / 16.0;
            boolean hit = distMine < tolerance && distMine <= distOther;
            cir.setReturnValue(hit);
        } catch (Throwable t) {
            // fall through to Create's default
        }
    }
}