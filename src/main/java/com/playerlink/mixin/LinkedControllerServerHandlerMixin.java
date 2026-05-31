package com.playerlink.mixin;

import com.playerlink.PlayerLinkMod;
import com.playerlink.api.PlayerLinkApi;
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

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

/**
 * Mixin into Create's {@code LinkedControllerServerHandler#receivePressed}.
 *
 * <p>Create does NOT pass the slot index server-side — only the
 * frequency-items list. We reconstruct which controller slot fired the
 * event by comparing that list against each of the player's 6 controller
 * slots' stored frequency items, then push the slot's owner UUID into
 * {@link PlayerLinkApi#beginTransmit} so {@link FrequencyOfMixin} can
 * stamp every produced Frequency with it.
 *
 * <p>The reflective lookup of Create's per-slot frequency-items method is
 * cached the first time it succeeds — we never reflect twice on the
 * hot path of every button press.
 */
@Mixin(value = com.simibubi.create.content.redstone.link.controller.LinkedControllerServerHandler.class,
       remap = false)
public abstract class LinkedControllerServerHandlerMixin {

    /** Candidate method names on {@link LinkedControllerItem} for per-slot freq items. */
    private static final String[] SLOT_ITEMS_METHODS = {
            "getFrequencyItems", "getFrequencyItemsFor", "getNetworkKey"
    };

    /** Resolved method handle — cached after first successful lookup. */
    private static volatile Method CACHED_SLOT_ITEMS_METHOD = null;
    private static volatile boolean LOOKUP_RESOLVED = false;

    @Inject(method = "receivePressed", at = @At("HEAD"), remap = false, require = 0)
    private static void playerlink$tagOwnerOnPressHead(LevelAccessor level, BlockPos pos, UUID uuid,
                                                       List<ItemStack> items, boolean pressed,
                                                       CallbackInfo ci) {
        PlayerLinkApi.endTransmit(); // defensive — clear any stale context
        try {
            if (level == null || uuid == null || items == null) return;
            MinecraftServer server = level.getServer();
            if (server == null) return;
            ServerPlayer sp = server.getPlayerList().getPlayer(uuid);
            if (sp == null) return;

            ItemStack ctrl = playerlink$findController(sp);
            if (ctrl.isEmpty()) {
                PlayerLinkMod.LOGGER.info("[PlayerLink] receivePressed: player {} has no controller in hand",
                        sp.getName().getString());
                return;
            }

            int slot = playerlink$findSlotByItems(ctrl, items);
            if (slot < 0) {
                PlayerLinkMod.LOGGER.info("[PlayerLink] receivePressed: could NOT match items to a slot");
                return;
            }

            UUID owner = PlayerLinkApi.readSlotOwner(ctrl, slot);
            PlayerLinkMod.LOGGER.info("[PlayerLink] receivePressed: matched slot={}, owner={}", slot, owner);
            if (owner != null) PlayerLinkApi.beginTransmit(owner);
        } catch (Throwable t) {
            PlayerLinkMod.LOGGER.warn("[PlayerLink] receivePressed HEAD threw", t);
            PlayerLinkApi.endTransmit();
        }
    }

    @Inject(method = "receivePressed", at = @At("RETURN"), remap = false, require = 0)
    private static void playerlink$tagOwnerOnPressReturn(LevelAccessor level, BlockPos pos, UUID uuid,
                                                         List<ItemStack> items, boolean pressed,
                                                         CallbackInfo ci) {
        PlayerLinkApi.endTransmit();
    }

    private static ItemStack playerlink$findController(ServerPlayer sp) {
        ItemStack main = sp.getMainHandItem();
        if (main.getItem() instanceof LinkedControllerItem) return main;
        ItemStack off = sp.getOffhandItem();
        if (off.getItem() instanceof LinkedControllerItem) return off;
        return ItemStack.EMPTY;
    }

    /**
     * Match {@code items} (the list Create just sent) against each of the
     * controller's 6 slots' stored frequency items. Returns the matching
     * slot index or -1 if none.
     */
    private static int playerlink$findSlotByItems(ItemStack controller, List<ItemStack> items) {
        Method m = playerlink$resolveSlotItemsMethod();
        if (m == null) return -1;
        try {
            for (int slot = 0; slot < PlayerLinkApi.slotCount(); slot++) {
                Object result = m.invoke(null, controller, slot);
                if (result instanceof List<?> list && playerlink$itemsMatch(list, items)) {
                    return slot;
                }
            }
        } catch (Throwable t) {
            PlayerLinkMod.LOGGER.warn("[PlayerLink] slot match via {} threw", m.getName(), t);
        }
        return -1;
    }

    /**
     * Resolves Create's per-slot frequency-items method exactly once and
     * caches the {@link Method} for subsequent calls. On first failure,
     * logs the available static methods so the right name can be added
     * to {@link #SLOT_ITEMS_METHODS}.
     */
    private static Method playerlink$resolveSlotItemsMethod() {
        if (LOOKUP_RESOLVED) return CACHED_SLOT_ITEMS_METHOD;
        synchronized (LinkedControllerServerHandlerMixin.class) {
            if (LOOKUP_RESOLVED) return CACHED_SLOT_ITEMS_METHOD;
            for (String name : SLOT_ITEMS_METHODS) {
                try {
                    Method candidate = LinkedControllerItem.class
                            .getDeclaredMethod(name, ItemStack.class, int.class);
                    candidate.setAccessible(true);
                    CACHED_SLOT_ITEMS_METHOD = candidate;
                    PlayerLinkMod.LOGGER.info("[PlayerLink] slot-items API resolved: '{}'", name);
                    LOOKUP_RESOLVED = true;
                    return candidate;
                } catch (NoSuchMethodException ignored) {
                    // try next
                }
            }
            PlayerLinkMod.LOGGER.info(
                "[PlayerLink] slot-items API NOT FOUND. Tried: {}. Static methods on LinkedControllerItem: {}",
                String.join(", ", SLOT_ITEMS_METHODS),
                playerlink$listStaticMethods());
            LOOKUP_RESOLVED = true;
            return null;
        }
    }

    private static String playerlink$listStaticMethods() {
        StringBuilder sb = new StringBuilder();
        for (Method m : LinkedControllerItem.class.getDeclaredMethods()) {
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
