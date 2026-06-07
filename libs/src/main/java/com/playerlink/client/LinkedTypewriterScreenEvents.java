package com.playerlink.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.playerlink.PlayerLinkMod;
import com.playerlink.api.ITypewriterKeyOwner;
import com.playerlink.network.RequestTypewriterWhitelistPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@EventBusSubscriber(modid = PlayerLinkMod.MODID, value = Dist.CLIENT)
public final class LinkedTypewriterScreenEvents {

    private LinkedTypewriterScreenEvents() {}

    // Candidate screen class names — tried in order, first match wins.
    private static final String[] SCREEN_CLASS_CANDIDATES = {
        "dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.screen.LinkedTypewriterScreen",
        "dev.simulated_team.simulated.content.blocks.redstone.typewriter.screen.LinkedTypewriterScreen",
        "dev.simulated_team.simulated.content.redstone.linked_typewriter.screen.LinkedTypewriterScreen",
        "dev.simulated_team.simulated.content.redstone.typewriter.screen.LinkedTypewriterScreen",
    };

    // Candidate field names for the list of key-row widgets on the screen.
    private static final String[] KEY_LIST_FIELD_CANDIDATES = {
        "allKeys", "keys", "keyWidgets", "keyButtons", "keyRows", "keyList"
    };

    // Candidate field names for the per-widget key index.
    private static final String[] KEY_NUM_FIELD_CANDIDATES = {
        "keyNum", "key", "keyIndex", "index", "id", "slotIndex"
    };

    private static final int FACE_SIZE = 16;

    @Nullable private static Class<?> cachedScreenClass;
    private static boolean screenClassResolved = false;

    @Nullable
    private static Class<?> screenClass() {
        if (!screenClassResolved) {
            screenClassResolved = true;
            for (String name : SCREEN_CLASS_CANDIDATES) {
                try {
                    cachedScreenClass = Class.forName(name);
                    PlayerLinkMod.LOGGER.info("[PlayerLink] Typewriter screen class found: {}", name);
                    break;
                } catch (ClassNotFoundException ignored) {}
            }
            if (cachedScreenClass == null) {
                PlayerLinkMod.LOGGER.info("[PlayerLink] Typewriter screen class not found — Simulated mod absent or class path changed");
            }
        }
        return cachedScreenClass;
    }

    private static boolean isTypewriterScreen(Screen screen) {
        Class<?> cls = screenClass();
        return cls != null && cls.isInstance(screen);
    }

    // ── Reflection helpers ────────────────────────────────────────────────

    /** Walk the class hierarchy looking for a field by name. */
    @Nullable
    private static Field findField(Class<?> cls, String name) {
        for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {}
        }
        return null;
    }

    /** Try to get an int value from a field on obj. */
    private static int reflectInt(Object obj, String fieldName, int fallback) {
        try {
            Field f = findField(obj.getClass(), fieldName);
            if (f == null) return fallback;
            f.setAccessible(true);
            return f.getInt(obj);
        } catch (Throwable t) {
            return fallback;
        }
    }

    /** Try to call getX() / getY() / getWidth() / getHeight() on a widget. */
    private static int callGetter(Object widget, String method, int fallback) {
        try {
            Method m = widget.getClass().getMethod(method);
            return (int) m.invoke(widget);
        } catch (Throwable t) {
            // fall back to field
            String field = method.substring(3, 4).toLowerCase() + method.substring(4);
            return reflectInt(widget, field, fallback);
        }
    }

    /**
     * Find the BlockPos of the Typewriter from its screen by scanning all fields
     * for a BlockPos value, or by finding a BlockEntity and calling getBlockPos().
     */
    @Nullable
    private static BlockPos findBlockPos(Screen screen) {
        for (Class<?> c = screen.getClass(); c != null; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                f.setAccessible(true);
                try {
                    Object val = f.get(screen);
                    if (val instanceof BlockPos pos) return pos;
                    if (val instanceof BlockEntity be) return be.getBlockPos();
                } catch (Throwable ignored) {}
            }
        }
        return null;
    }

    /**
     * Get the allKeys list from the screen (or its block entity / menu).
     * Returns null if not found.
     */
    @Nullable
    @SuppressWarnings("unchecked")
    private static List<Object> findAllKeys(Screen screen) {
        // Try on the screen itself
        List<Object> found = tryGetAllKeys(screen);
        if (found != null) return found;

        // Try through a menu field → block entity
        for (Class<?> c = screen.getClass(); c != null; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                f.setAccessible(true);
                try {
                    Object val = f.get(screen);
                    if (val == null || val instanceof Screen) continue;
                    // try allKeys on this sub-object
                    List<Object> sub = tryGetAllKeys(val);
                    if (sub != null) return sub;
                } catch (Throwable ignored) {}
            }
        }
        return null;
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private static List<Object> tryGetAllKeys(Object obj) {
        for (String name : KEY_LIST_FIELD_CANDIDATES) {
            Field f = findField(obj.getClass(), name);
            if (f == null) continue;
            f.setAccessible(true);
            try {
                Object val = f.get(obj);
                if (val instanceof List<?> list && !list.isEmpty()) {
                    PlayerLinkMod.LOGGER.info("[PlayerLink] Typewriter key-list found via field '{}'", name);
                    return (List<Object>) list;
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    /** Try all keyNum field candidates; returns -1 if none found. */
    private static int resolveKeyNum(Object widget) {
        for (String name : KEY_NUM_FIELD_CANDIDATES) {
            int v = reflectInt(widget, name, Integer.MIN_VALUE);
            if (v != Integer.MIN_VALUE) return v;
        }
        return -1;
    }

    // ── Layout cache ──────────────────────────────────────────────────────

    private record FaceSlot(int x, int y, int keyNum) {}

    @Nullable private static List<FaceSlot> cachedSlots;
    @Nullable private static Screen lastScreen;

    private static List<FaceSlot> getSlots(Screen screen) {
        if (screen != lastScreen) {
            lastScreen = screen;
            cachedSlots = null;
        }
        if (cachedSlots != null) return cachedSlots;

        List<Object> allKeys = findAllKeys(screen);
        if (allKeys == null || allKeys.isEmpty()) {
            cachedSlots = List.of();
            return cachedSlots;
        }

        List<FaceSlot> slots = new ArrayList<>(allKeys.size());
        for (Object kw : allKeys) {
            int kn  = resolveKeyNum(kw);
            int kx  = callGetter(kw, "getX", -1);
            int ky  = callGetter(kw, "getY", -1);
            int kw2 = callGetter(kw, "getWidth", 60);
            if (kn < 0 || kx < 0 || ky < 0) continue;
            // Place face button to the right of the key widget with a 2 px gap
            slots.add(new FaceSlot(kx + kw2 + 2, ky + (callGetter(kw, "getHeight", FACE_SIZE) - FACE_SIZE) / 2, kn));
        }
        cachedSlots = slots;
        return cachedSlots;
    }

    // ── Events ────────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onRender(final ScreenEvent.Render.Post event) {
        if (!isTypewriterScreen(event.getScreen())) return;
        Screen screen = event.getScreen();
        List<FaceSlot> slots = getSlots(screen);
        if (slots.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        BlockPos typewriterPos = findBlockPos(screen);
        if (typewriterPos == null) return;

        // Get ITypewriterKeyOwner from the client-side block entity
        ITypewriterKeyOwner keyOwner = null;
        if (mc.level != null) {
            BlockEntity be = mc.level.getBlockEntity(typewriterPos);
            if (be instanceof ITypewriterKeyOwner ko) keyOwner = ko;
        }

        GuiGraphics g  = event.getGuiGraphics();
        int mouseX = event.getMouseX();
        int mouseY = event.getMouseY();

        for (FaceSlot slot : slots) {
            int x = slot.x();
            int y = slot.y();
            boolean hover = mouseX >= x && mouseX < x + FACE_SIZE
                         && mouseY >= y && mouseY < y + FACE_SIZE;

            g.fill(x - 1, y - 1, x + FACE_SIZE + 1, y + FACE_SIZE + 1, 0xFF373737);
            g.fill(x, y, x + FACE_SIZE, y + FACE_SIZE, hover ? 0xFFFFFFC0 : 0xFF8B8B8B);

            UUID owner = keyOwner == null ? null : keyOwner.playerlink$getKeyOwner(slot.keyNum());
            if (owner != null) {
                String name = resolveName(owner);
                ResourceLocation skin = SkinCache.get(owner, name);
                RenderSystem.enableBlend();
                RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
                PlayerFaceRenderer.draw(g, skin, x, y, FACE_SIZE);
                RenderSystem.disableBlend();
            } else {
                int cx = x + FACE_SIZE / 2;
                int cy = y + FACE_SIZE / 2;
                g.fill(cx - 3, cy - 1, cx + 3, cy + 1, 0xFF555555);
                g.fill(cx - 1, cy - 3, cx + 1, cy + 3, 0xFF555555);
            }

            if (hover) {
                String tip = owner != null ? "Player Frequency" : "Click to assign owner";
                g.renderTooltip(mc.font, Component.literal(tip), mouseX, mouseY);
            }
        }
    }

    @SubscribeEvent
    public static void onClick(final ScreenEvent.MouseButtonPressed.Pre event) {
        if (!isTypewriterScreen(event.getScreen())) return;
        if (event.getButton() != 0) return;

        Screen screen = event.getScreen();
        List<FaceSlot> slots = getSlots(screen);
        if (slots.isEmpty()) return;

        BlockPos typewriterPos = findBlockPos(screen);
        if (typewriterPos == null) return;

        double mx = event.getMouseX();
        double my = event.getMouseY();

        for (FaceSlot slot : slots) {
            if (mx >= slot.x() && mx < slot.x() + FACE_SIZE
             && my >= slot.y() && my < slot.y() + FACE_SIZE) {
                PacketDistributor.sendToServer(new RequestTypewriterWhitelistPacket(typewriterPos, slot.keyNum()));
                event.setCanceled(true);
                return;
            }
        }
    }

    @Nullable
    private static String resolveName(UUID id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        var p = mc.level.getPlayerByUUID(id);
        return p == null ? null : p.getGameProfile().getName();
    }
}
