package com.playerlink.mixin;

import com.playerlink.api.IFrequencyOwner;
import com.playerlink.api.IOwnedLink;
import com.simibubi.create.content.redstone.link.LinkBehaviour;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import net.createmod.catnip.data.Couple;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(value = LinkBehaviour.class, remap = false)
public abstract class LinkBehaviourMixin {

    @Shadow(remap = false) public SmartBlockEntity blockEntity;

    @Inject(method = "getNetworkKey", at = @At("RETURN"), remap = false)
    private void playerlink$tagFrequencies(CallbackInfoReturnable<Couple<RedstoneLinkNetworkHandler.Frequency>> cir) {
        Couple<RedstoneLinkNetworkHandler.Frequency> key = cir.getReturnValue();
        if (key == null) return;

        if (!(this.blockEntity instanceof IOwnedLink owned)) return;

        UUID owner = owned.playerlink$getOwner();
        if (owner == null) return;

        RedstoneLinkNetworkHandler.Frequency first = key.getFirst();
        RedstoneLinkNetworkHandler.Frequency second = key.getSecond();
        if (first instanceof IFrequencyOwner fo) fo.playerlink$setOwner(owner);
        if (second instanceof IFrequencyOwner fo) fo.playerlink$setOwner(owner);
    }
}