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

/**
 * Attaches an owner UUID to each RedstoneLinkBlockEntity, persists it
 * via NBT, and propagates it onto the Frequency objects returned by
 * {@code getNetworkKey()} so the network handler routes signals
 * scoped per-player.
 *
 * Target: com.simibubi.create.content.redstone.link.RedstoneLinkBlockEntity
 */
@Mixin(value = com.simibubi.create.content.redstone.link.RedstoneLinkBlockEntity.class, remap = false)
public abstract class RedstoneLinkBlockEntityMixin implements IOwnedLink {

    // --- shadowed BlockEntity members (provided by the target / its superclass) ---
    @Shadow(remap = false) public Level level;
    @Shadow public abstract void setChanged();
    @Shadow public abstract BlockPos getBlockPos();
    @Shadow public abstract BlockState getBlockState();

    // --- our additions ---
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

    /**
     * Inject into Create's BE write() to persist the owner UUID.
     * Signature in Create 6.0 (1.21.1):
     *   protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket)
     */
   // @Inject(method = "write", at = @At("TAIL"), remap = false)
   // private void playerlink$write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci) {
   //    if (playerlink$ownerUuid != null) {
   //         compound.putUUID(PLAYERLINK_OWNER_KEY, playerlink$ownerUuid);
        }
    }

   // @Inject(method = "read", at = @At("TAIL"), remap = false)
   // private void playerlink$read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci) {
   //     if (compound.hasUUID(PLAYERLINK_OWNER_KEY)) {
   //         this.playerlink$ownerUuid = compound.getUUID(PLAYERLINK_OWNER_KEY);
   //    } else {
   //         this.playerlink$ownerUuid = null;
        }
    }

    /**
     * After Create constructs the {@code Couple<Frequency>} used as the
     * network routing key, tag both Frequencies with our owner UUID.
     *
     * Method name in Create 6.0: {@code getNetworkKey()}.
     */
   // @Inject(method = "getNetworkKey", at = @At("RETURN"), remap = false)
   // private void playerlink$tagFrequencies(CallbackInfoReturnable<Couple<RedstoneLinkNetworkHandler.Frequency>> cir) {
   //     Couple<RedstoneLinkNetworkHandler.Frequency> key = cir.getReturnValue();
   //     if (key == null) return;
   //     UUID owner = this.playerlink$ownerUuid;
   //     // Owner == null => vanilla shared behaviour (backward compatible)
   //     if (owner == null) return;
   //     RedstoneLinkNetworkHandler.Frequency first = key.getFirst();
   //     RedstoneLinkNetworkHandler.Frequency second = key.getSecond();
   //     if (first instanceof IFrequencyOwner fo) fo.playerlink$setOwner(owner);
   //    if (second instanceof IFrequencyOwner fo) fo.playerlink$setOwner(owner);
    }
}
