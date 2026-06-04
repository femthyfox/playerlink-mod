package com.playerlink.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.playerlink.PlayerLinkMod;
import com.playerlink.compat.TypewriterCompat;
import com.playerlink.network.RequestTypewriterKeyWhitelistPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
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

/**
 * Renders player-frequency face slots inside the Linked Typewriter GUI.
 *
 * The Typewriter screen has KeyRow objects (each an ArrayList<KeyWidget>).
 * KeyWidget has:
 *   - int keyNum           (the GLFW key code)
 *   - getX() / getY()     (from AbstractSimiWidget → AbstractWidget)
 *   - getWidth() / getHeight()
 *
 * We render one face button to the RIGHT of the first KeyWidget in each row.
 * Clicking opens the player-select screen for that key's GLFW code.
 */
@EventBusSubscriber(modid = PlayerLinkMod.MODID, value = Dist.CLIENT)
public final class LinkedTypewriterScreenEvents {

    private static final int FACE_SIZE = 14;
    private static final String TW_SCREEN_CLASS =
            "dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.screen.LinkedTypewriterScreen";

    private static boolean classChecked = false;
    private static Class<?> twScreenClass = null;
    // allKeys field is List<KeyRow> in LinkedTypewriterScreen
    private static Field allKeysField = null;
    // keyNum field is int in KeyWidget
    private static Field keyNumField = null;
    // contentHolder getter on the menu
    private static Method getContentHolderMethod = null;

    private LinkedTypewriterScreenEvents() {}

    @SubscribeEvent
    public static void onRender(final ScreenEvent.Render.Post event) {
        Screen screen = event.getScreen();
        if (!isTypewriterScreen(screen)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        BlockEntity be = getTypewriterBe(screen);
        List<KeyInfo> keys = extractKeys(screen);
        if (keys.isEmpty()) return;

        GuiGraphics g  = event.getGuiGraphics();
        int mouseX     = event.getMouseX();
        int mouseY     = event.getMouseY();

        for (KeyInfo ki : keys) {
            int x = ki.faceX;
            int y = ki.faceY;
            boolean hover = mouseX >= x && mouseX < x + FACE_SIZE
                         && mouseY >= y && mouseY < y + FACE_SIZE;

            // Border + background
            g.fill(x - 1, y - 1, x + FACE_SIZE + 1, y + FACE_SIZE + 1, 0xFF373737);
            g.fill(x, y, x + FACE_SIZE, y + FACE_SIZE,
                   hover ? 0xFFFFFFC0 : 0xFF8B8B8B);

            UUID owner = be == null ? null : TypewriterCompat.getKeyOwner(be, ki.glfwKey);
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
                String tip = owner != null
                        ? "Player Frequency (click to change)"
                        : "Click to set player frequency";
                g.renderTooltip(mc.font, Component.literal(tip), mouseX, mouseY);
            }
        }
    }

    @SubscribeEvent
    public static void onClick(final ScreenEvent.MouseButtonPressed.Pre event) {
        Screen screen = event.getScreen();
        if (!isTypewriterScreen(screen)) return;
        if (event.getButton() != 0) return;

        BlockEntity be = getTypewriterBe(screen);
        if (be == null) return;

        List<KeyInfo> keys = extractKeys(screen);
        double mx = event.getMouseX();
        double my = event.getMouseY();

        for (KeyInfo ki : keys) {
            if (mx >= ki.faceX && mx < ki.faceX + FACE_SIZE
             && my >= ki.faceY && my < ki.faceY + FACE_SIZE) {
                PacketDistributor.sendToServer(
                        new RequestTypewriterKeyWhitelistPacket(be.getBlockPos(), ki.glfwKey));
                event.setCanceled(true);
                return;
            }
        }
    }

    // ── Key extraction ────────────────────────────────────────────────────────

    private static final class KeyInfo {
        final int glfwKey;
        final int faceX;
        final int faceY;
        KeyInfo(int glfwKey, int faceX, int faceY) {
            this.glfwKey = glfwKey;
            this.faceX   = faceX;
            this.faceY   = faceY;
        }
    }

    private static List<KeyInfo> extractKeys(Screen screen) {
        List<KeyInfo> result = new ArrayList<>();
        if (allKeysField == null || keyNumField == null) return result;
        try {
            @SuppressWarnings("unchecked")
            List<Object> allKeys = (List<Object>) allKeysField.get(screen);
            if (allKeys == null) return result;

            for (Object keyRow : allKeys) {
                // KeyRow extends ArrayList<KeyWidget> — iterable directly
                if (!(keyRow instanceof Iterable<?> row)) continue;
                for (Object widget : row) {
                    // Get glfwKey (field name confirmed: "keyNum")
                    int glfwKey = keyNumField.getInt(widget);
                    // Get screen position via standard AbstractWidget getX/getY
                    int wx = callIntMethod(widget, "getX");
                    int wy = callIntMethod(widget, "getY");
                    int ww = callIntMethod(widget, "getWidth");
                    int wh = callIntMethod(widget, "getHeight");
                    // Place face button immediately to the right, vertically centred
                    int faceX = wx + ww + 2;
                    int faceY = wy + (wh - FACE_SIZE) / 2;
                    result.add(new KeyInfo(glfwKey, faceX, faceY));
                    break; // One face per row — use first widget
                }
            }
        } catch (Throwable t) {
            PlayerLinkMod.LOGGER.debug("[PlayerLink] extractKeys failed: {}", t.getMessage());
        }
        return result;
    }

    // ── Reflection init ───────────────────────────────────────────────────────

    private static boolean isTypewriterScreen(Screen screen) {
        if (!TypewriterCompat.isAvailable()) return false;
        initReflection();
        return twScreenClass != null && twScreenClass.isInstance(screen);
    }

    private static void initReflection() {
        if (classChecked) return;
        classChecked = true;
        try {
            twScreenClass = Class.forName(TW_SCREEN_CLASS);

            // allKeys field: List<KeyRow> in LinkedTypewriterScreen
            // Field is named "allKeys" — confirmed from source
            allKeysField = twScreenClass.getDeclaredField("allKeys");
            allKeysField.setAccessible(true);

            // keyNum field on KeyWidget — confirmed from source
            Class<?> keyWidgetClass = Class.forName(
                    "dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.screen.widgets.KeyWidget");
            keyNumField = keyWidgetClass.getDeclaredField("keyNum");
            keyNumField.setAccessible(true);

            // contentHolder: GhostItemMenu<T>.getContentHolder() is inherited
            // from AbstractSimiContainerScreen → menu → contentHolder field
            Class<?> menuClass = Class.forName(
                    "dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.screen.LinkedTypewriterMenuCommon");
            getContentHolderMethod = menuClass.getMethod("getContentHolder");

            PlayerLinkMod.LOGGER.info("[PlayerLink] LinkedTypewriterScreenEvents reflection ready.");
        } catch (Throwable t) {
            PlayerLinkMod.LOGGER.warn("[PlayerLink] LinkedTypewriterScreenEvents reflection failed: {}", t.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    @Nullable
    private static BlockEntity getTypewriterBe(Screen screen) {
        try {
            Method getMenu = screen.getClass().getMethod("getMenu");
            Object menu = getMenu.invoke(screen);
            if (getContentHolderMethod == null) return null;
            Object be = getContentHolderMethod.invoke(menu);
            return be instanceof BlockEntity bEntity ? bEntity : null;
        } catch (Throwable ignored) {}
        return null;
    }

    private static int callIntMethod(Object obj, String name) {
        try {
            return (int) obj.getClass().getMethod(name).invoke(obj);
        } catch (Throwable t) { return 0; }
    }

    @Nullable
    private static String resolveName(UUID id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        var p = mc.level.getPlayerByUUID(id);
        return p == null ? null : p.getGameProfile().getName();
    }
}
