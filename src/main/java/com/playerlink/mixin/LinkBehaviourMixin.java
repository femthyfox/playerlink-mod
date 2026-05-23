package com.playerlink.mixin;

import com.playerlink.api.IFrequencyOwner;
import com.playerlink.api.IOwnedLink;
import com.simibubi.create.content.redstone.link.LinkBehaviour;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.createmod.catnip.data.Couple;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(value = LinkBehaviour.class, remap = false)
public abstract class LinkBehaviourMixin extends BlockEntityBehaviour {

    protected LinkBehaviourMixin(SmartBlockEntity be) {
        super(be);
    }

    @Inject(method = "getNetworkKey", at = @At("RETURN"), remap = false)
    private void playerlink$tagFrequencies(CallbackInfoReturnable<Couple<Frequency>> cir) {
        Couple<Frequency> key = cir.getReturnValue();
        if (key == null) return;

        // Get the BE's current owner (may be null if owner was cleared)
        UUID owner = null;
        if (this.blockEntity instanceof IOwnedLink iol) {
            owner = iol.playerlink$getOwner();
        }

        Frequency first = key.getFirst();
        Frequency second = key.getSecond();

        // ALWAYS overwrite the stamp — including with null when no owner.
        // This clears any leftover stamp from a previous owner.
        // Skip Frequency.EMPTY (shared singleton across all links — must stay untagged).
        if (first instanceof IFrequencyOwner fo && first != Frequency.EMPTY) {
            fo.playerlink$setOwner(owner);
        }
        if (second instanceof IFrequencyOwner fo && second != Frequency.EMPTY) {
            fo.playerlink$setOwner(owner);
        }
    }
}