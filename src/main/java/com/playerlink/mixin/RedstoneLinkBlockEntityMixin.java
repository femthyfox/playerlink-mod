package com.playerlink.mixin;

import com.playerlink.api.IFrequencyOwner;
import com.playerlink.api.IOwnedLink;
import net.createmod.catnip.data.Couple;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;
import java.util.UUID;

@Mixin(value = com.simibubi.create.content.redstone.link.RedstoneLinkBlockEntity.class, remap = false)
public abstract class RedstoneLinkBlockEntityMixin implements IOwnedLink {

    @Shadow(remap = false) public Level level;
    @Shadow(remap = false) public abstract void setChanged();
    @Shadow(remap = false) public abstract BlockPos getBlockPos();
    @Shadow(remap = false) public abstract BlockState getBlockState();

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
        this.playerlink$ownerUuid = owner;
        setChanged();
        if (this.level != null && !this.level.isClientSide) {
            BlockState st = getBlockState();
            this.level.sendBlockUpdated(getBlockPos(), st, st, 3);
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

    @Inject(method = "getNetworkKey", at = @At("RETURN"), remap = false)
    private void playerlink$tagFrequencies(CallbackInfoReturnable<Couple<RedstoneLinkNetworkHandler.Frequency>> cir) {
        Couple<RedstoneLinkNetworkHandler.Frequency> key = cir.getReturnValue();
        if (key == null) return;
        UUID owner = this.playerlink$ownerUuid;
        if (owner == null) return;
        RedstoneLinkNetworkHandler.Frequency first = key.getFirst();
        RedstoneLinkNetworkHandler.Frequency second = key.getSecond();
        if (first instanceof IFrequencyOwner fo) fo.playerlink$setOwner(owner);
        if (second instanceof IFrequencyOwner fo) fo.playerlink$setOwner(owner);
    }
}