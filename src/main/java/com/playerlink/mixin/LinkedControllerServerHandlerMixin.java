package com.playerlink.mixin;

import com.playerlink.PlayerLinkMod;
import com.playerlink.util.ControllerOwners;
import com.simibubi.create.content.redstone.link.controller.LinkedControllerItem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.UUID;

/**
 * Mixin into Create's LinkedControllerServerHandler#receivePressed.
 *
 * Real Create 6.0.10 signature (from the previous crash log):
 *   receivePressed(LevelAccessor level, BlockPos pos, UUID uuid,
 *                  List items, boolean pressed)
 *
 * Create does NOT pass the slot index server-side — it only ships the
 * frequency items list. We reconstruct the slot by comparing the items
 * list against each of the player's controller slots via reflection.
 *
 * Wrapped in try/catch so a Create API change can never crash the server.
 */
@Mixin(value = com.simibubi.create.content.redstone.link.controller.LinkedControllerServerHandler.class, remap = false)
public abstract class LinkedControllerServerHandlerMixin {

    @Inject(method = "receivePressed", at = @At("HEAD"), remap = false, require = 0)
    private static void playerlink$tagOwnerOnPressHead(LevelAccessor level, BlockPos pos, UUID uuid,
                                                       List<ItemStack> items, boolean pressed,
                                                       CallbackInfo ci) {
        ControllerOwnerContext.clear();
        try {
            if (level == null || uuid == null || items == null) return;
            MinecraftServer server = level.getServer();
            if (server == null) return;
            ServerPlayer sp = server.getPlayerList().getPlayer(uuid);
            if (sp == null) return;

            ItemStack ctrl = playerlink$findController(sp);
            if (ctrl.isEmpty()) return;

            int slot = playerlink$findSlotByItems(ctrl, items);
            if (slot < 0) return;

            UUID owner = ControllerOwners.get(ctrl, slot);
            if (owner != null) ControllerOwnerContext.set(owner);
        } catch (Throwable t) {
            PlayerLinkMod.LOGGER.debug("[PlayerLink] receivePressed HEAD swallowed", t);
            ControllerOwnerContext.clear();
        }
    }

    @Inject(method = "receivePressed", at = @At("RETURN"), remap = false, require = 0)
    private static void playerlink$tagOwnerOnPressReturn(LevelAccessor level, BlockPos pos, UUID uuid,
                                                         List<ItemStack> items, boolean pressed,
                                                         CallbackInfo ci) {
        ControllerOwnerContext.clear();
    }

    private static ItemStack playerlink$findController(ServerPlayer sp) {
        ItemStack main = sp.getMainHandItem();
        if (main.getItem() instanceof LinkedControllerItem) return main;
        ItemStack off = sp.getOffhandItem();
        if (off.getItem() instanceof LinkedControllerItem) return off;
        return ItemStack.EMPTY;
    }

    /**
     * Find which slot's stored frequency items match the items received by
     * receivePressed. Uses reflection so an unexpected API rename in Create
     * doesn't crash us — we just silently return -1 and skip isolation.
     */
    private static int playerlink$findSlotByItems(ItemStack controller, List<ItemStack> items) {
        String[] candidates = { "getFrequencyItems", "getFrequencyItemsFor", "getNetworkKey" };
        for (String methodName : candidates) {
            try {
                java.lang.reflect.Method m =
                        LinkedControllerItem.class.getDeclaredMethod(methodName, ItemStack.class, int.class);
                m.setAccessible(true);
                for (int slot = 0; slot < ControllerOwners.SLOT_COUNT; slot++) {
                    Object result = m.invoke(null, controller, slot);
                    if (result instanceof List<?> list && playerlink$itemsMatch(list, items)) {
                        return slot;
                    }
                }
                return -1;
            } catch (NoSuchMethodException ignored) {
                // try next candidate
            } catch (Throwable t) {
                PlayerLinkMod.LOGGER.debug("[PlayerLink] slot lookup failed via {}", methodName, t);
                return -1;
            }
        }
        return -1;
    }

    private static boolean playerlink$itemsMatch(List<?> a, List<ItemStack> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (!(a.get(i) instanceof ItemStack as)) return false;
            if (!ItemStack.matches(as, b.get(i))) return false;
        }
        return true;
    }
}