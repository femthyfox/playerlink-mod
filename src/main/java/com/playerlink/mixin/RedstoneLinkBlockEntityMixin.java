package com.playerlink.mixin;

import com.playerlink.api.IOwnedLink;
import com.simibubi.create.Create;
import com.simibubi.create.content.redstone.link.LinkBehaviour;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;
import java.util.UUID;

@Mixin(value = com.simibubi.create.content.redstone.link.RedstoneLinkBlockEntity.class, remap = false)
public abstract class RedstoneLinkBlockEntityMixin implements IOwnedLink {

    @Unique
    private static final String PLAYERLINK_OWNER_KEY = "PlayerLinkOwner";

    @Unique
    @Nullable
    private UUID playerlink$ownerUuid = null;

    @Unique
    private BlockEntity playerlink$self() {
        return (BlockEntity) (Object) this;
    }

    @Unique
    private LinkBehaviour playerlink$findLink() {
        BlockEntity self = playerlink$self();
        if (!(self instanceof SmartBlockEntity sbe)) return null;
        return sbe.getBehaviour(LinkBehaviour.TYPE);
    }

    @Override
    @Nullable
    public UUID playerlink$getOwner() {
        return playerlink$ownerUuid;
    }

    @Override
    public void playerlink$setOwner(@Nullable UUID owner) {
        BlockEntity self = playerlink$self();
        Level level = self.getLevel();

        if (level != null && !level.isClientSide) {
            LinkBehaviour link = playerlink$findLink();

            if (link != null) {
                Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(level, link);
            }

            this.playerlink$ownerUuid = owner;

            if (link != null) {
                Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(level, link);
            }

            self.setChanged();
            BlockState st = self.getBlockState();
            level.sendBlockUpdated(self.getBlockPos(), st, st, 3);
        } else {
            this.playerlink$ownerUuid = owner;
            self.setChanged();
        }
    }

    @Inject(method = "write", at = @At("TAIL"), remap = false)
    private void playerlink$write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci) {
        if (playerlink$ownerUuid != null) {
            compound.putUUID(PLAYERLINK_OWNER_KEY, playerlink$ownerUuid);
        }
    }

    @Inject(method = "read", at = @At("TAIL"), remap = false)
    private void playerlink$read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci) {
        if (compound.hasUUID(PLAYERLINK_OWNER_KEY)) {
            this.playerlink$ownerUuid = compound.getUUID(PLAYERLINK_OWNER_KEY);
        } else {
            this.playerlink$ownerUuid = null;
        }
    }
}
