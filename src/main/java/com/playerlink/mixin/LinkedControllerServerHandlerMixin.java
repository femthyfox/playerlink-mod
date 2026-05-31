package com.playerlink.mixin;

import com.playerlink.util.ControllerOwners;
import com.simibubi.create.content.redstone.link.controller.LinkedControllerItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * Mixin into Create's LinkedControllerServerHandler.
 *
 * Tags the transmitted Frequency with the slot's owner UUID so that
 * FrequencyMixin can refuse to match it against links owned by a
 * different player (or against a vanilla unowned link if this slot
 * has an owner).
 *
 * The mixin sets a thread-local owner before receivePressed runs, then
 * Frequency.of (also mixed) reads it during creation. We clear the
 * thread-local at RETURN so we never leak state across packets.
 *
 * Target signature guess (Create 6.0.x):
 *   public static void receivePressed(Level world, BlockPos pos, Player player,
 *                                     int slot, boolean pressed)
 *
 * require = 0 means the mixin silently skips if the method doesn't match,
 * which we'll only know from runtime testing (frequency won't be tagged).
 */
@Mixin(value = com.simibubi.create.content.redstone.link.controller.LinkedControllerServerHandler.class, remap = false)
public abstract class LinkedControllerServerHandlerMixin {

    @Inject(method = "receivePressed", at = @At("HEAD"), remap = false, require = 0)
    private static void playerlink$tagOwnerOnPressHead(Level world, BlockPos pos, Player player,
                                                       int slot, boolean pressed, CallbackInfo ci) {
        if (player == null || slot < 0 || slot >= ControllerOwners.SLOT_COUNT) {
            ControllerOwnerContext.clear();
            return;
        }
        ItemStack ctrl = playerlink$findController(player);
        if (ctrl.isEmpty()) {
            ControllerOwnerContext.clear();
            return;
        }
        UUID owner = ControllerOwners.get(ctrl, slot);
        ControllerOwnerContext.set(owner);
    }

    @Inject(method = "receivePressed", at = @At("RETURN"), remap = false, require = 0)
    private static void playerlink$tagOwnerOnPressReturn(Level world, BlockPos pos, Player player,
                                                         int slot, boolean pressed, CallbackInfo ci) {
        ControllerOwnerContext.clear();
    }

    private static ItemStack playerlink$findController(Player player) {
        ItemStack main = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (main.getItem() instanceof LinkedControllerItem) return main;
        ItemStack off = player.getItemInHand(InteractionHand.OFF_HAND);
        if (off.getItem() instanceof LinkedControllerItem) return off;
        return ItemStack.EMPTY;
    }
}
