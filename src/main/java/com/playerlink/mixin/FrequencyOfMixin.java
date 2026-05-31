package com.playerlink.mixin;

import com.playerlink.api.PlayerLinkApi;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/**
 * Mixin into {@code Frequency.of(ItemStack)} that, on RETURN, reads the
 * thread-local transmit owner set via
 * {@link PlayerLinkApi#beginTransmit(UUID)} and stamps the produced
 * Frequency with it.
 *
 * <p>This is the central interception point -- any code that wraps a Create
 * emit call with {@code beginTransmit}/{@code endTransmit} automatically
 * gets owner-isolated frequencies WITHOUT having to touch the Frequency
 * object itself.
 */
@Mixin(targets = "com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler$Frequency",
       remap = false)
public abstract class FrequencyOfMixin {

    @Inject(method = "of", at = @At("RETURN"), cancellable = false, remap = false, require = 0)
    private static void playerlink$tagFromContext(ItemStack stack,
                                                  CallbackInfoReturnable<RedstoneLinkNetworkHandler.Frequency> cir) {
        UUID owner = PlayerLinkApi.currentTransmitOwner();
        if (owner == null) return;
        PlayerLinkApi.stampOwner(cir.getReturnValue(), owner);
    }
}
