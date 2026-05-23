package com.playerlink.mixin;

import com.playerlink.api.IOwnedLink;
import com.simibubi.create.Create;
import com.simibubi.create.content.redstone.link.LinkBehaviour;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;
import java.util.UUID;

@Mixin(value = com.simibubi.create.content.redstone.link.RedstoneLinkBlockEntity.class, remap = false)
public abstract class RedstoneLinkBlockEntityMixin implements IOwnedLink {

    @Shadow(remap = false) public Level level;
    @Shadow public abstract void setChanged();
    @Shadow public abstract BlockPos getBlockPos();
    @Shadow public abstract BlockState getBlockState();

    @Unique
    private static final String PLAYERLINK_OWNER_KEY = "PlayerLinkOwner";

    @Unique
    @Nullable
    private UUID playerlink$ownerUuid = null;

    @Unique
    private LinkBehaviour playerlink$findLink() {
        BlockEntity self = (BlockEntity) (Object) this;
        if (!(self instanceof SmartBlockEntity sbe)) return null;
        LinkBehaviour lb = sbe.getBehaviour(LinkBehaviour.RECEIVER);
        if (lb == null) lb = sbe.getBehaviour(LinkBehaviour.TRANSMITTER);
        return lb;
    }

    @Override
    @Nullable
    public UUID playerlink$getOwner() {
        return playerlink$ownerUuid;
    }

    @Override
    public void playerlink$setOwner(@Nullable UUID owner) {
        if (this.level != null && !this.level.isClientSide) {
            LinkBehaviour link = playerlink$findLink();

            // 1) Evict from current bucket (still keyed by OLD owner UUID)
            if (link != null) {
                Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(this.level, link);
            }

            // 2) Mutate owner
            this.playerlink$ownerUuid = owner;

            // 3) Re-register under NEW key (LinkBehaviourMixin tags with NEW owner, or null = public)
            if (link != null) {
                Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(this.level, link);
            }

            setChanged();
            BlockState st = getBlockState();
            this.level.sendBlockUpdated(getBlockPos(), st, st, 3);
        } else {
            this.playerlink$ownerUuid = owner;
            setChanged();
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