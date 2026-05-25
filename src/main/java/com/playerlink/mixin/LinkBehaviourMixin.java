package com.playerlink.mixin;

import com.playerlink.api.IFrequencyOwner;
import com.playerlink.api.IOwnedLink;
import com.simibubi.create.content.redstone.link.LinkBehaviour;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.createmod.catnip.data.Couple;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(value = LinkBehaviour.class, remap = false)
public abstract class LinkBehaviourMixin {

    @Inject(method = "getNetworkKey", at = @At("RETURN"), remap = false)
    private void playerlink$tagFrequencies(CallbackInfoReturnable<Couple<RedstoneLinkNetworkHandler.Frequency>> cir) {
        Couple<RedstoneLinkNetworkHandler.Frequency> key = cir.getReturnValue();
        if (key == null) return;

        BlockEntityBehaviour selfBeh = (BlockEntityBehaviour) (Object) this;
        SmartBlockEntity be = selfBeh.blockEntity;

        UUID owner = (be instanceof IOwnedLink owned) ? owned.playerlink$getOwner() : null;

        RedstoneLinkNetworkHandler.Frequency first = key.getFirst();
        RedstoneLinkNetworkHandler.Frequency second = key.getSecond();
        if (first instanceof IFrequencyOwner fo) fo.playerlink$setOwner(owner);
        if (second instanceof IFrequencyOwner fo) fo.playerlink$setOwner(owner);
    }
}
