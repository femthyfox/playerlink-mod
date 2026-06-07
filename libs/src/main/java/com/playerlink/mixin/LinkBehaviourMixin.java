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

    @Inject(method = "getNetworkKey", at = @At("RETURN"), cancellable = true, remap = false)
    private void playerlink$tagFrequencies(CallbackInfoReturnable<Couple<RedstoneLinkNetworkHandler.Frequency>> cir) {
        Couple<RedstoneLinkNetworkHandler.Frequency> originalKey = cir.getReturnValue();
        if (originalKey == null) return;

        BlockEntityBehaviour selfBeh = (BlockEntityBehaviour) (Object) this;
        SmartBlockEntity be = selfBeh.blockEntity;

        UUID owner = (be instanceof IOwnedLink owned) ? owned.playerlink$getOwner() : null;

        RedstoneLinkNetworkHandler.Frequency origFirst = originalKey.getFirst();
        RedstoneLinkNetworkHandler.Frequency origSecond = originalKey.getSecond();

        // Build fresh Frequency copies tagged with owner. Never mutate the originals,
        // since Create stores them as HashMap keys and mutating those would corrupt the map.
        RedstoneLinkNetworkHandler.Frequency newFirst = RedstoneLinkNetworkHandler.Frequency.of(origFirst.getStack());
        RedstoneLinkNetworkHandler.Frequency newSecond = RedstoneLinkNetworkHandler.Frequency.of(origSecond.getStack());
        if (newFirst instanceof IFrequencyOwner fo) fo.playerlink$setOwner(owner);
        if (newSecond instanceof IFrequencyOwner fo) fo.playerlink$setOwner(owner);

        cir.setReturnValue(Couple.create(newFirst, newSecond));
    }
}