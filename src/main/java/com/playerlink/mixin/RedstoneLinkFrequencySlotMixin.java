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

            Vec3 result = SlotMath.localCenter(facing, u, v);
            cir.setReturnValue(result);
        } catch (Throwable t) {
            // Silently fall through to Create's original positioning
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