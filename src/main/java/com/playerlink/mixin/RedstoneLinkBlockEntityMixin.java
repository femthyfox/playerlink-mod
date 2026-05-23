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

    @Override
    @Nullable
    public UUID playerlink$getOwner() {
        return playerlink$ownerUuid;
    }

    @Override
    public void playerlink$setOwner(@Nullable UUID owner) {
        BlockEntity self = (BlockEntity) (Object) this;
        Level lvl = self.getLevel();
        boolean serverSide = (lvl != null && !lvl.isClientSide);

        // Find this BE's LinkBehaviour (Create's network-routing helper)
        LinkBehaviour link = null;
        if (self instanceof SmartBlockEntity sbe) {
            link = sbe.getBehaviour(LinkBehaviour.TYPE);
        }

        // 1) Remove from the network with the OLD key (current owner)
        if (serverSide && link != null) {
            try {
                Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(lvl, link);
            } catch (Throwable ignored) {}
        }

        // 2) Actually change the owner
        this.playerlink$ownerUuid = owner;

        // 3) Re-add to the network with the NEW key (new owner)
        if (serverSide && link != null) {
            try {
                Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(lvl, link);
            } catch (Throwable ignored) {}
        }

        // 4) Mark BE dirty + send block update so clients see the new owner
        self.setChanged();
        if (serverSide) {
            BlockState st = self.getBlockState();
            lvl.sendBlockUpdated(self.getBlockPos(), st, st, 3);
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