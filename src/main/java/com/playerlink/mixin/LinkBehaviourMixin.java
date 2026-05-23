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

    // This constructor is never actually called - it just satisfies the Java compiler
    // because BlockEntityBehaviour has no no-arg constructor.
    protected LinkBehaviourMixin(SmartBlockEntity be) {
        super(be);
    }

    @Inject(method = "getNetworkKey", at = @At("RETURN"), remap = false)
    private void playerlink$tagFrequencies(CallbackInfoReturnable<Couple<Frequency>> cir) {
        Couple<Frequency> key = cir.getReturnValue();
        if (key == null) return;

        // 'blockEntity' is the inherited field from BlockEntityBehaviour
        if (!(this.blockEntity instanceof IOwnedLink iol)) return;
        UUID owner = iol.playerlink$getOwner();
        if (owner == null) return;

        Frequency first = key.getFirst();
        Frequency second = key.getSecond();
        if (first instanceof IFrequencyOwner fo) fo.playerlink$setOwner(owner);
        if (second instanceof IFrequencyOwner fo) fo.playerlink$setOwner(owner);
    }
}