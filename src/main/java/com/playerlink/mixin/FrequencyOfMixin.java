package com.playerlink.mixin;

import com.playerlink.api.IFrequencyOwner;
import com.playerlink.util.ControllerOwnerContext;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(targets = "com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler$Frequency", remap = false)
public abstract class FrequencyOfMixin {

    @Inject(method = "of", at = @At("RETURN"), cancellable = false, remap = false, require = 0)
    private static void playerlink$tagFromContext(ItemStack stack,
                                                  CallbackInfoReturnable<RedstoneLinkNetworkHandler.Frequency> cir) {
        UUID owner = ControllerOwnerContext.get();
        if (owner == null) return;
        Object result = cir.getReturnValue();
        if (result instanceof IFrequencyOwner fo) {
            fo.playerlink$setOwner(owner);
        }
    }
}