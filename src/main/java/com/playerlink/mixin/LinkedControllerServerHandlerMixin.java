package com.playerlink.mixin;

import com.playerlink.PlayerLinkMod;
import com.playerlink.util.ControllerOwnerContext;
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
 * Note that Create does NOT pass the slot index server-side — it only ships
 * the frequency items list. We reconstruct the slot by comparing the items
 * list against each of the player's controller slots.
 *
 * All access is wrapped in try/catch so a malformed Create API can never
 * crash the server again — at worst we just skip isolation for that press.
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
            if (ctrl.isEmpty()) {
                PlayerLinkMod.LOGGER.info("[PlayerLink] receivePressed: player {} has no controller in hand", sp.getName().getString());
                return;
            }

            int slot = playerlink$findSlotByItems(ctrl, items);
            if (slot < 0) {
                PlayerLinkMod.LOGGER.info("[PlayerLink] receivePressed: could NOT match items to a slot (slot lookup failed)");
                return;
            }

            UUID owner = ControllerOwners.get(ctrl, slot);
            PlayerLinkMod.LOGGER.info("[PlayerLink] receivePressed: matched slot={}, owner={}", slot, owner);
            if (owner != null) ControllerOwnerContext.set(owner);
        } catch (Throwable t) {
            PlayerLinkMod.LOGGER.warn("[PlayerLink] receivePressed HEAD threw", t);
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
                PlayerLinkMod.LOGGER.info("[PlayerLink] slot lookup using API method '{}'", methodName);
                for (int slot = 0; slot < ControllerOwners.SLOT_COUNT; slot++) {
                    Object result = m.invoke(null, controller, slot);
                    if (result instanceof List<?> list && playerlink$itemsMatch(list, items)) {
                        return slot;
                    }
                }
                return -1;
            } catch (NoSuchMethodException ignored) {
                // Try the next candidate
            } catch (Throwable t) {
                PlayerLinkMod.LOGGER.warn("[PlayerLink] slot lookup via {} threw", methodName, t);
                return -1;
            }
        }
        PlayerLinkMod.LOGGER.info("[PlayerLink] slot lookup: no candidate API matched. Tried: {}. " +
                "Static methods on LinkedControllerItem: {}",
                String.join(", ", candidates),
                playerlink$listStaticMethods());
        return -1;
    }

    /** Diagnostic — lists static methods on LinkedControllerItem so we can find the right API. */
    private static String playerlink$listStaticMethods() {
        StringBuilder sb = new StringBuilder();
        for (java.lang.reflect.Method m : LinkedControllerItem.class.getDeclaredMethods()) {
            if (java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(m.getName()).append("(").append(m.getParameterCount()).append(")");
            }
        }
        return sb.toString();
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
