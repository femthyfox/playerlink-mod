package com.playerlink.mixin;

import com.playerlink.api.IFrequencyOwner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Objects;
import java.util.UUID;

/**
 * Adds an "owner UUID" field to Create's Frequency class and folds it
 * into equals() / hashCode() so frequencies belonging to different
 * players never compare equal.
 *
 * Target: com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler$Frequency
 */
@Mixin(targets = "com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler$Frequency", remap = false)
public abstract class FrequencyMixin implements IFrequencyOwner {

    @Unique
    private UUID playerlink$owner = null;

    @Override
    public UUID playerlink$getOwner() {
        return playerlink$owner;
    }

    @Override
    public void playerlink$setOwner(UUID owner) {
        this.playerlink$owner = owner;
    }

    @Inject(method = "equals", at = @At("HEAD"), cancellable = true, remap = false)
    private void playerlink$equals(Object other, CallbackInfoReturnable<Boolean> cir) {
        if (!(other instanceof IFrequencyOwner fo)) {
            // Different class entirely - let original method handle it.
            return;
        }
        UUID a = this.playerlink$owner;
        UUID b = fo.playerlink$getOwner();
        if (!Objects.equals(a, b)) {
            cir.setReturnValue(false);
        }
        // If owners match (or both null), fall through to original equals
        // which still compares item-stack content.
    }

    @Inject(method = "hashCode", at = @At("RETURN"), cancellable = true, remap = false)
    private void playerlink$hashCode(CallbackInfoReturnable<Integer> cir) {
        int original = cir.getReturnValue();
        int ownerHash = (playerlink$owner == null) ? 0 : playerlink$owner.hashCode();
        cir.setReturnValue(original * 31 + ownerHash);
    }
}
