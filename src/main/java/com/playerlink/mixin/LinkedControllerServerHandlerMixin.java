package com.playerlink.mixin;

import com.playerlink.api.IFrequencyOwner;
import com.playerlink.util.ControllerOwnerContext;
import com.playerlink.util.ControllerOwners;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency;
import com.simibubi.create.content.redstone.link.controller.LinkedControllerItem;
import net.createmod.catnip.data.Couple;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Intercepts LinkedControllerServerHandler.receivePressed so that when a
 * controller button fires frequencies, those frequencies get tagged with the
 * per-slot owner UUID stored on the item. This makes the controller respect
 * player-frequency isolation the same way Redstone Links do.
 *
 * The real receivePressed signature (Create 6.0.x) is:
 *   receivePressed(LevelAccessor, BlockPos, UUID, List<Couple<Frequency>>, boolean)
 *
 * Strategy: before the method runs we match the outgoing Couple<Frequency> list
 * against each slot's toFrequency() to find which slot is firing, then push
 * the slot owner into ControllerOwnerContext. FrequencyOfMixin picks that up
 * when Frequency.of() is called inside the network handler.
 */
@Mixin(value = com.simibubi.create.content.redstone.link.controller.LinkedControllerServerHandler.class, remap = false)
public abstract class LinkedControllerServerHandlerMixin {

    @Inject(method = "receivePressed", at = @At("HEAD"), remap = false, require = 0)
    private static void playerlink$injectOwnerHead(LevelAccessor level,
                                                   BlockPos pos,
                                                   UUID playerUUID,
                                                   List<Couple<Frequency>> frequencies,
                                                   boolean pressed,
                                                   CallbackInfo ci) {
        ControllerOwnerContext.clear();
        if (level == null || playerUUID == null || frequencies == null || frequencies.isEmpty()) return;

        MinecraftServer server = level.getServer();
        if (server == null) return;

        ServerPlayer player = server.getPlayerList().getPlayer(playerUUID);
        if (player == null) return;

        ItemStack controller = playerlink$findController(player);
        if (controller.isEmpty()) return;

        // Match the first Couple<Frequency> being transmitted against each slot
        // using Create's own LinkedControllerItem.toFrequency(stack, slot).
        Couple<Frequency> transmitted = frequencies.get(0);
        for (int slot = 0; slot < ControllerOwners.SLOT_COUNT; slot++) {
            Couple<Frequency> slotFreq = LinkedControllerItem.toFrequency(controller, slot);
            if (playerlink$couplesMatch(slotFreq, transmitted)) {
                UUID owner = ControllerOwners.get(controller, slot);
                if (owner != null) {
                    ControllerOwnerContext.set(owner);
                    // Tag all transmitted frequencies with this owner immediately,
                    // since FrequencyOfMixin only fires on Frequency.of() calls made
                    // after this point. Frequencies already constructed need manual tagging.
                    for (Couple<Frequency> pair : frequencies) {
                        playerlink$tagCouple(pair, owner);
                    }
                }
                return;
            }
        }
    }

    @Inject(method = "receivePressed", at = @At("RETURN"), remap = false, require = 0)
    private static void playerlink$injectOwnerReturn(LevelAccessor level,
                                                     BlockPos pos,
                                                     UUID playerUUID,
                                                     List<Couple<Frequency>> frequencies,
                                                     boolean pressed,
                                                     CallbackInfo ci) {
        ControllerOwnerContext.clear();
    }

    @org.spongepowered.asm.mixin.Unique
    private static ItemStack playerlink$findController(ServerPlayer player) {
        ItemStack main = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (main.getItem() instanceof LinkedControllerItem) return main;
        ItemStack off = player.getItemInHand(InteractionHand.OFF_HAND);
        if (off.getItem() instanceof LinkedControllerItem) return off;
        return ItemStack.EMPTY;
    }

    @org.spongepowered.asm.mixin.Unique
    private static boolean playerlink$couplesMatch(Couple<Frequency> a, Couple<Frequency> b) {
        if (a == null || b == null) return false;
        // Compare by item stacks only (ignoring owner) since the transmitted
        // frequencies don't have an owner yet — that's what we're assigning.
        return ItemStack.matches(a.getFirst().getStack(), b.getFirst().getStack())
            && ItemStack.matches(a.getSecond().getStack(), b.getSecond().getStack());
    }

    @org.spongepowered.asm.mixin.Unique
    private static void playerlink$tagCouple(Couple<Frequency> pair, UUID owner) {
        if (pair.getFirst() instanceof IFrequencyOwner fo) fo.playerlink$setOwner(owner);
        if (pair.getSecond() instanceof IFrequencyOwner fo) fo.playerlink$setOwner(owner);
    }
}
