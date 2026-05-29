package com.playerlink.mixin;

import com.simibubi.create.content.redstone.link.RedstoneLinkBlock;
import com.simibubi.create.content.redstone.link.RedstoneLinkFrequencySlot;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
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
    private void playerlink$overrideOffset(LevelAccessor level, BlockPos pos, BlockState state,
                                           CallbackInfoReturnable<Vec3> cir) {
        boolean first = ((com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform.Dual) (Object) this).isFirst();

        Direction facing = state.getValue(RedstoneLinkBlock.FACING);

        // Wall-mounted (horizontal-facing) link: stack both slots vertically near the BOTTOM
        // of the visible face, leaving the upper portion free for the player face overlay.
        if (facing.getAxis().isHorizontal()) {
            float y = first ? 6f : 3f;   // first slot just above second
            Vec3 location = VecHelper.voxelSpace(8f, y, 3.01f);
            location = playerlink$rotateHorizontally(state, location);
            cir.setReturnValue(location);
            return;
        }

        // Top/bottom-facing link: keep Create's default layout for now
        // (no clear "down" direction on a flat-top face)
        Vec3 location = VecHelper.voxelSpace(8f, 3.01f, 5.5f);
        if (first) location = location.add(0, 0, 5f / 16f);
        location = VecHelper.rotateCentered(location, facing == Direction.DOWN ? 180 : 0, Axis.X);
        cir.setReturnValue(location);
    }

    @org.spongepowered.asm.mixin.Unique
    private Vec3 playerlink$rotateHorizontally(BlockState state, Vec3 location) {
        Direction facing = state.getValue(RedstoneLinkBlock.FACING);
        float yRot = 0;
        if (facing == Direction.SOUTH) yRot = 180;
        else if (facing == Direction.WEST) yRot = 90;
        else if (facing == Direction.EAST) yRot = -90;
        return VecHelper.rotateCentered(location, yRot, Axis.Y);
    }
}