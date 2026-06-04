package com.playerlink.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.playerlink.network.SetControllerSlotOwnerPacket;
import com.playerlink.network.SetOwnerPacket;
import com.playerlink.network.SetTypewriterKeyOwnerPacket;
import com.playerlink.network.SetTypewriterOwnerPacket;
import com.playerlink.network.WhitelistResponsePacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Player-owner selection screen. Used in three modes:
 *   • block-link mode     — sends SetOwnerPacket on assign
 *   • controller mode     — sends SetControllerSlotOwnerPacket on assign,
 *                           and returns to the Linked Controller screen on close.
 *   • typewriter mode     — sends SetTypewriterOwnerPacket on assign
 */
public class PlayerSelectScreen extends Screen {

    // ─── PALETTE ───────────────────────────────────────────────────────────
    private static final int COL_BG_DIM        = 0xB0000000;

    // Stone panel — between stone and dark-gray concrete
    private static final int COL_STONE_TOP     = 0xFF8A8A8A;
    private static final int COL_STONE_BOT     = 0xFF3D3D3D;
    private static final int COL_STONE_BORDER  = 0xFF1A1A1A;
    private static final int COL_STONE_SHADOW  = 0xFF000000;

    // Spruce wood — actual Minecraft spruce-plank tones
    private static final int COL_SPRUCE        = 0xFF6B4F2E;
    private static final int COL_SPRUCE_HI     = 0xFF8C6A40;
    private static final int COL_SPRUCE_DARK   = 0xFF3F2A14;
    private static final int COL_SPRUCE_LIGHT  = 0xFFA88256;
    private static final int COL_SPRUCE_FACE_WELL = 0xFFA88256;

    // Brass (buttons)
    private static final int COL_BRASS_TOP     = 0xFFE6C572;
    private static final int COL_BRASS_BOT     = 0xFFB68A3F;
    private static final int COL_BRASS_BORDER  = 0xFF5A3E1B;
    private static final int COL_BRASS_HI      = 0xFFFFE49A;

    // Redstone accents — deep darker red
    private static final int COL_REDSTONE      = 0xFF7A1A1A;
    private static final int COL_REDSTONE_HI   = 0xFFA82828;

    // Text
    private static final int COL_TEXT_DARK     = 0xFF1A0F05;
    private static final int COL_TEXT_LIGHT    = 0xFFFFF5DC;
    private static final int COL_TEXT_MUTED    = 0xFF555555;

    // ─── LAYOUT ──────────────────────────────────────────────────────────
    private static final int FACE_SIZE   = 32;
    private static final int TILE_W      = 68;
    private static final int TILE_H      = 62;
    private static final int TILE_PAD    = 6;
    private static final int PANEL_MARGIN = 24;

    private final BlockPos blockPos;
    private final int controllerSlot;
    private final boolean typewriterMode;
    private final int typewriterGlfwKey;     // >= 0 when in per-key typewriter mode
    @Nullable private final Screen returnScreen;
    @Nullable private final UUID currentOwner;
    private final List<WhitelistResponsePacket.Entry> allEntries;
    private List<WhitelistResponsePacket.Entry> filtered;

    private EditBox searchBox;
    private BrassButton assignButton, clearButton, closeButton;

    @Nullable private UUID selectedUuid;
    @Nullable private String selectedName;

    private int scroll = 0;
    private int panelX, panelY, panelW, panelH;
    private int gridX, gridY, gridW, gridH;

    /** Factory: open in block-link mode. */
    public static PlayerSelectScreen forBlock(BlockPos pos,
                                              @Nullable UUID currentOwner,
                                              List<WhitelistResponsePacket.Entry> entries) {
        return new PlayerSelectScreen(pos, -1, false, -1, null, currentOwner, entries);
    }

    /** Factory: open in controller-slot mode. */
    public static PlayerSelectScreen forControllerSlot(int slotIndex,
                                                       @Nullable UUID currentOwner,
                                                       List<WhitelistResponsePacket.Entry> entries,
                                                       @Nullable Screen returnScreen) {
        return new PlayerSelectScreen(BlockPos.ZERO, slotIndex, false, -1, returnScreen, currentOwner, entries);
    }

    /** Factory: open in typewriter mode. */
    public static PlayerSelectScreen forTypewriter(BlockPos pos,
                                                   @Nullable UUID currentOwner,
                                                   List<WhitelistResponsePacket.Entry> entries) {
        return new PlayerSelectScreen(pos, -1, true, -1, null, currentOwner, entries);
    }

    /** Factory: open in per-key typewriter mode. */
    public static PlayerSelectScreen forTypewriterKey(BlockPos pos,
                                                      int glfwKey,
                                                      @Nullable UUID currentOwner,
                                                      List<WhitelistResponsePacket.Entry> entries) {
        return new PlayerSelectScreen(pos, -1, true, glfwKey, null, currentOwner, entries);
    }

    /** Back-compat single-arg constructor. */
    public PlayerSelectScreen(BlockPos pos, @Nullable UUID currentOwner, List<WhitelistResponsePacket.Entry> entries) {
        this(pos, -1, false, -1, null, currentOwner, entries);
    }

    private PlayerSelectScreen(BlockPos pos,
                               int controllerSlot,
                               boolean typewriterMode,
                               int typewriterGlfwKey,
                               @Nullable Screen returnScreen,
                               @Nullable UUID currentOwner,
                               List<WhitelistResponsePacket.Entry> entries) {
        super(Component.translatable(controllerSlot >= 0
                ? "playerlink.gui.select_owner.controller_title"
                : typewriterMode
                    ? "playerlink.gui.select_owner.typewriter_title"
                    : "playerlink.gui.select_owner.title"));
        this.blockPos = pos;
        this.controllerSlot = controllerSlot;
        this.typewriterMode = typewriterMode;
        this.typewriterGlfwKey = typewriterGlfwKey;
        this.returnScreen = returnScreen;
        this.currentOwner = currentOwner;
        this.allEntries = entries;
        this.filtered = new ArrayList<>(entries);
        this.selectedUuid = currentOwner;
        if (currentOwner != null) {
            this.selectedName = entries.stream()
                .filter(e -> e.uuid().equals(currentOwner))
                .map(WhitelistResponsePacket.Entry::name)
                .findFirst().orElse(null);
        }
    }

    @Override
    protected void init() {
        panelW = Math.min(440, width - PANEL_MARGIN * 2);
        panelH = Math.min(370, height - PANEL_MARGIN * 2);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;

        int innerLeft = panelX + 12;
        int innerRight = panelX + panelW - 12;
        int innerWidth = innerRight - innerLeft;

        int searchY = panelY + 50;
        searchBox = new EditBox(font, innerLeft + 1, searchY, innerWidth - 2, 16,
                Component.translatable("playerlink.gui.select_owner.search"));
        searchBox.setHint(Component.translatable("playerlink.gui.select_owner.search")
                .copy().withStyle(ChatFormatting.DARK_GRAY));
        searchBox.setResponder(s -> refilter());
        addRenderableWidget(searchBox);

        gridX = innerLeft;
        gridY = searchY + 22;
        gridW = innerWidth;
        gridH = panelH - (gridY - panelY) - 40;

        int btnY = panelY + panelH - 26;
        int btnW = 80, gap = 6, totalW = btnW * 3 + gap * 2;
        int btnLeft = panelX + (panelW - totalW) / 2;

        assignButton = new BrassButton(btnLeft, btnY, btnW, 18,
                Component.translatable("playerlink.gui.select_owner.button.assign"),
                b -> assignSelected());
        assignButton.active = selectedUuid != null;
        addRenderableWidget(assignButton);

        clearButton = new BrassButton(btnLeft + btnW + gap, btnY, btnW, 18,
                Component.translatable("playerlink.gui.select_owner.button.clear"),
                b -> {
                    if (controllerSlot >= 0) {
                        PacketDistributor.sendToServer(new SetControllerSlotOwnerPacket(controllerSlot, Optional.empty()));
                    } else if (typewriterMode && typewriterGlfwKey >= 0) {
                        PacketDistributor.sendToServer(new SetTypewriterKeyOwnerPacket(blockPos, typewriterGlfwKey, Optional.empty()));
                    } else if (typewriterMode) {
                        PacketDistributor.sendToServer(new SetTypewriterOwnerPacket(blockPos, Optional.empty()));
                    } else {
                        PacketDistributor.sendToServer(new SetOwnerPacket(blockPos, Optional.empty()));
                    }
                    onClose();
                });
        clearButton.active = currentOwner != null;
        addRenderableWidget(clearButton);

        closeButton = new BrassButton(btnLeft + (btnW + gap) * 2, btnY, btnW, 18,
                Component.translatable("playerlink.gui.select_owner.button.close"),
                b -> onClose());
        addRenderableWidget(closeButton);
    }

    private void refilter() {
        String q = searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        filtered = new ArrayList<>();
        for (var e : allEntries) {
            if (q.isEmpty() || e.name().toLowerCase(Locale.ROOT).contains(q)) filtered.add(e);
        }
        scroll = 0;
    }

    private void assignSelected() {
        if (selectedUuid == null) return;
        if (controllerSlot >= 0) {
            PacketDistributor.sendToServer(new SetControllerSlotOwnerPacket(controllerSlot, Optional.of(selectedUuid)));
        } else if (typewriterMode && typewriterGlfwKey >= 0) {
            PacketDistributor.sendToServer(new SetTypewriterKeyOwnerPacket(blockPos, typewriterGlfwKey, Optional.of(selectedUuid)));
        } else if (typewriterMode) {
            PacketDistributor.sendToServer(new SetTypewriterOwnerPacket(blockPos, Optional.of(selectedUuid)));
        } else {
            PacketDistributor.sendToServer(new SetOwnerPacket(blockPos, Optional.of(selectedUuid)));
        }
        onClose();
    }

    @Override
    public void onClose() {
        // In controller mode, jump back to the controller screen instead of
        // closing all the way out to the world.
        if (returnScreen != null && minecraft != null) {
            minecraft.setScreen(returnScreen);
            return;
        }
        super.onClose();
    }

    private int columns()    { return Math.max(1, (gridW + TILE_PAD) / (TILE_W + TILE_PAD)); }
    private int rows()       { return (filtered.size() + columns() - 1) / columns(); }
    private int maxScroll()  {
        int needed = rows() * (TILE_H + TILE_PAD);
        return Math.max(0, needed - gridH);
    }

    private int tileAt(double mx, double my) {
        int relX = (int)(mx - gridX);
        int relY = (int)(my - gridY) + scroll;
        if (relX < 0 || relY < 0) return -1;
        int col = relX / (TILE_W + TILE_PAD);
        int row = relY / (TILE_H + TILE_PAD);
        if (col >= columns()) return -1;
        int colOff = relX - col * (TILE_W + TILE_PAD);
        int rowOff = relY - row * (TILE_H + TILE_PAD);
        if (colOff > TILE_W || rowOff > TILE_H) return -1;
        int idx = row * columns() + col;
        if (idx < 0 || idx >= filtered.size()) return -1;
        if (my < gridY || my > gridY + gridH) return -1;
        return idx;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int idx = tileAt(mouseX, mouseY);
            if (idx >= 0) {
                var entry = filtered.get(idx);
                if (selectedUuid != null && selectedUuid.equals(entry.uuid())) {
                    assignSelected();
                    return true;
                }
                selectedUuid = entry.uuid();
                selectedName = entry.name();
                assignButton.active = true;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
        if (mouseY >= gridY && mouseY <= gridY + gridH) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll - (int)(dy * 24)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, dx, dy);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // No-op. Single dim drawn in render() to avoid the double-dim caused
        // by vanilla Screen.render() calling renderBackground a second time.
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        g.fill(0, 0, width, height, COL_BG_DIM);

        // ── Drop shadow under panel
        g.fill(panelX + 3, panelY + 4, panelX + panelW + 3, panelY + panelH + 4, 0x90000000);

        // ── Stone panel
        drawStonePanel(g, panelX, panelY, panelW, panelH);

        // ── Spruce title bar
        int titleBarH = 20;
        int tbx = panelX + 4, tby = panelY + 4;
        int tbw = panelW - 8;
        g.fillGradient(tbx, tby, tbx + tbw, tby + titleBarH, COL_SPRUCE_LIGHT, COL_SPRUCE_HI);
        for (int i = 1; i < 4; i++) {
            int gy = tby + (titleBarH * i / 4);
            g.fill(tbx, gy, tbx + tbw, gy + 1, COL_SPRUCE);
        }
        g.fill(tbx, tby, tbx + tbw, tby + 1, COL_SPRUCE_LIGHT);
        g.fill(tbx, tby + titleBarH - 1, tbx + tbw, tby + titleBarH, COL_SPRUCE_DARK);

        // Title text
        g.drawString(font, title, panelX + 12, panelY + 10, COL_TEXT_LIGHT, true);

        // ── Owner-info strip
        int stripY = tby + titleBarH + 2;
        int stripH = 14;
        drawInsetBox(g, tbx, stripY, tbw, stripH, 0xFF4A4A4A, COL_STONE_TOP);

        Component cur = (selectedUuid == null)
                ? Component.translatable("playerlink.gui.select_owner.current.none")
                : Component.translatable("playerlink.gui.select_owner.current",
                        selectedName == null ? selectedUuid.toString().substring(0, 8) : selectedName);
        int curColor = selectedUuid == null ? 0xFFBBBBBB : 0xFFFFFFFF;
        int curX = panelX + (panelW - font.width(cur)) / 2;
        g.drawString(font, cur, curX, stripY + 3, curColor, true);

        // ── Search field frame (dark inset)
        int sx = searchBox.getX() - 2, sy = searchBox.getY() - 2;
        int sw = searchBox.getWidth() + 4, sh = 20;
        drawInsetBox(g, sx, sy, sw, sh, 0xFF1F1F1F, 0xFFB0B0B0);

        // ── Grid background
        drawInsetBox(g, gridX - 4, gridY - 4, gridW + 8, gridH + 8, 0xFF4A4A4A, COL_STONE_TOP);
        drawGridDecor(g, gridX - 4, gridY - 4, gridW + 8, gridH + 8);

        if (filtered.isEmpty()) {
            Component msg = Component.translatable("playerlink.gui.select_owner.empty");
            g.drawString(font, msg,
                    gridX + (gridW - font.width(msg)) / 2,
                    gridY + gridH / 2 - font.lineHeight / 2,
                    COL_TEXT_LIGHT, true);
        } else {
            drawTileGrid(g, mouseX, mouseY);
        }

        // ── Hint text
        Component hint = Component.literal("Click a player to select");
        int hintX = panelX + (panelW - font.width(hint)) / 2;
        int hintY = panelY + panelH - 40;
        g.drawString(font, hint, hintX, hintY, 0xFFFFFFFF, true);

        // ── Buttons + search render via super
        super.render(g, mouseX, mouseY, pt);
    }

    private void drawTileGrid(GuiGraphics g, int mouseX, int mouseY) {
        g.enableScissor(gridX, gridY, gridX + gridW, gridY + gridH);

        int cols = columns();
        int hoveredIdx = tileAt(mouseX, mouseY);

        for (int i = 0; i < filtered.size(); i++) {
            int col = i % cols;
            int row = i / cols;
            int tx = gridX + col * (TILE_W + TILE_PAD);
            int ty = gridY + row * (TILE_H + TILE_PAD) - scroll;
            if (ty + TILE_H < gridY || ty > gridY + gridH) continue;

            var entry = filtered.get(i);
            boolean isSelected = selectedUuid != null && selectedUuid.equals(entry.uuid());
            boolean isCurrent  = currentOwner != null && currentOwner.equals(entry.uuid());
            boolean isHover    = i == hoveredIdx;

            // Spruce-wood tile body
            int top = isHover ? COL_SPRUCE_LIGHT : COL_SPRUCE_HI;
            int bot = isHover ? COL_SPRUCE_HI    : COL_SPRUCE;
            g.fillGradient(tx, ty, tx + TILE_W, ty + TILE_H, top, bot);

            // Subtle plank grain
            g.fill(tx + 2, ty + TILE_H / 2, tx + TILE_W - 2, ty + TILE_H / 2 + 1, COL_SPRUCE_DARK);

            // Border
            int border = isSelected ? COL_REDSTONE_HI
                       : isCurrent  ? COL_REDSTONE
                       : COL_SPRUCE_DARK;
            int borderThickness = (isSelected || isCurrent) ? 2 : 1;
            for (int t = 0; t < borderThickness; t++) {
                g.fill(tx + t, ty + t, tx + TILE_W - t, ty + 1 + t, border);
                g.fill(tx + t, ty + TILE_H - 1 - t, tx + TILE_W - t, ty + TILE_H - t, border);
                g.fill(tx + t, ty + t, tx + 1 + t, ty + TILE_H - t, border);
                g.fill(tx + TILE_W - 1 - t, ty + t, tx + TILE_W - t, ty + TILE_H - t, border);
            }

            // Face well
            int faceX = tx + (TILE_W - FACE_SIZE) / 2;
            int faceY = ty + 6;
            g.fill(faceX - 2, faceY - 2, faceX + FACE_SIZE + 2, faceY + FACE_SIZE + 2, COL_SPRUCE_DARK);
            g.fill(faceX - 1, faceY - 1, faceX + FACE_SIZE + 1, faceY + FACE_SIZE + 1, COL_SPRUCE_FACE_WELL);

            ResourceLocation skin = SkinCache.get(entry.uuid(), entry.name());
            RenderSystem.enableBlend();
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            PlayerFaceRenderer.draw(g, skin, faceX, faceY, FACE_SIZE);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            RenderSystem.disableBlend();

            // Name truncation
            String name = entry.name();
            int maxNameW = TILE_W - 4;
            if (font.width(name) > maxNameW) {
                while (name.length() > 1 && font.width(name + "…") > maxNameW) {
                    name = name.substring(0, name.length() - 1);
                }
                name = name + "…";
            }
            // Cream text on dark spruce — high contrast
            int textColor = isSelected ? 0xFFFFFFFF
                          : isCurrent  ? 0xFFFFE0E0
                          : COL_TEXT_LIGHT;
            g.drawString(font, name, tx + (TILE_W - font.width(name)) / 2, ty + FACE_SIZE + 10, textColor, true);

            // Redstone-glow dot for current owner
            if (isCurrent) {
                int dotX = tx + TILE_W - 8;
                int dotY = ty + 3;
                g.fill(dotX, dotY, dotX + 5, dotY + 5, COL_REDSTONE);
                g.fill(dotX + 1, dotY + 1, dotX + 4, dotY + 4, COL_REDSTONE_HI);
                g.fill(dotX + 2, dotY + 2, dotX + 3, dotY + 3, 0xFFFFFFFF);
            }
        }

        g.disableScissor();

        // Scrollbar (brass-tinted)
        if (maxScroll() > 0) {
            int barX = gridX + gridW - 4;
            g.fill(barX, gridY, barX + 3, gridY + gridH, 0x66000000);
            int barH = Math.max(20, gridH * gridH / (gridH + maxScroll()));
            int barY = gridY + (gridH - barH) * scroll / Math.max(1, maxScroll());
            g.fill(barX, barY, barX + 3, barY + barH, COL_BRASS_TOP);
        }
    }

    private void drawStonePanel(GuiGraphics g, int x, int y, int w, int h) {
        // Outer shadow ring
        g.fill(x - 1, y - 1, x + w + 1, y, COL_STONE_SHADOW);
        g.fill(x - 1, y + h, x + w + 1, y + h + 1, COL_STONE_SHADOW);
        g.fill(x - 1, y, x, y + h, COL_STONE_SHADOW);
        g.fill(x + w, y, x + w + 1, y + h, COL_STONE_SHADOW);

        // 2-px dark stone border
        g.fill(x, y, x + w, y + 2, COL_STONE_BORDER);
        g.fill(x, y + h - 2, x + w, y + h, COL_STONE_BORDER);
        g.fill(x, y, x + 2, y + h, COL_STONE_BORDER);
        g.fill(x + w - 2, y, x + w, y + h, COL_STONE_BORDER);

        // Stone gradient interior
        g.fillGradient(x + 2, y + 2, x + w - 2, y + h - 2, COL_STONE_TOP, COL_STONE_BOT);

        // Inner top/left highlight
        g.fill(x + 2, y + 2, x + w - 2, y + 3, 0x40FFFFFF);
        g.fill(x + 2, y + 2, x + 3, y + h - 2, 0x40FFFFFF);
    }

    private void drawInsetBox(GuiGraphics g, int x, int y, int w, int h, int fillColor, int lightColor) {
        g.fill(x, y, x + w, y + h, fillColor);
        // Top + left = darker (inset shadow)
        g.fill(x, y, x + w, y + 1, COL_STONE_SHADOW);
        g.fill(x, y, x + 1, y + h, COL_STONE_SHADOW);
        // Bottom + right = lighter (inset highlight)
        g.fill(x, y + h - 1, x + w, y + h, lightColor);
        g.fill(x + w - 1, y, x + w, y + h, lightColor);
    }

    /**
     * Decorative brick pattern + corner rivets drawn over the recessed grid backplate.
     */
    private void drawGridDecor(GuiGraphics g, int x, int y, int w, int h) {
        int brickW = 16, brickH = 6;
        int xStart = x + 2;
        int yStart = y + 2;
        int xEnd = x + w - 2;
        int yEnd = y + h - 2;

        int rowIdx = 0;
        for (int by = yStart; by < yEnd; by += brickH + 1) {
            int offset = (rowIdx++ % 2 == 0) ? 0 : brickW / 2;
            for (int bx = xStart - offset; bx < xEnd; bx += brickW + 1) {
                int x1 = Math.max(bx, xStart);
                int x2 = Math.min(bx + brickW, xEnd);
                int y1 = by;
                int y2 = Math.min(by + brickH, yEnd);
                if (x2 - x1 <= 1 || y2 - y1 <= 1) continue;
                g.fill(x1, y1, x2, y1 + 1, 0x22FFFFFF);
                g.fill(x1, y2 - 1, x2, y2, 0x22000000);
            }
        }

        // Corner rivets
        drawRivet(g, x + 3, y + 3);
        drawRivet(g, x + w - 7, y + 3);
        drawRivet(g, x + 3, y + h - 7);
        drawRivet(g, x + w - 7, y + h - 7);
    }

    private void drawRivet(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 4, y + 4, COL_STONE_SHADOW);
        g.fill(x, y, x + 3, y + 3, COL_BRASS_BORDER);
        g.fill(x + 1, y + 1, x + 3, y + 3, COL_BRASS_BOT);
        g.fill(x + 1, y + 1, x + 2, y + 2, COL_BRASS_HI);
    }

    // ──────────────────────────────────────────────────────────────────────
    // Brass-painted button
    // ──────────────────────────────────────────────────────────────────────
    private class BrassButton extends Button {
        BrassButton(int x, int y, int w, int h, Component msg, OnPress onPress) {
            super(x, y, w, h, msg, onPress, DEFAULT_NARRATION);
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            boolean hovered = isHoveredOrFocused();
            float alpha = this.active ? 1f : 0.5f;

            int top = hovered ? COL_BRASS_HI : COL_BRASS_TOP;
            int bot = hovered ? COL_BRASS_TOP : COL_BRASS_BOT;

            // Outer shadow
            g.fill(x, y + h, x + w, y + h + 1, COL_STONE_SHADOW);

            int border = hovered ? COL_REDSTONE : COL_BRASS_BORDER;
            g.fill(x, y, x + w, y + 1, border);
            g.fill(x, y + h - 1, x + w, y + h, border);
            g.fill(x, y, x + 1, y + h, border);
            g.fill(x + w - 1, y, x + w, y + h, border);

            g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, top, bot);
            g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x70FFFFFF);

            int textColor = this.active ? COL_TEXT_DARK : 0x80000000;
            int labelX = x + (w - font.width(getMessage())) / 2;
            int labelY = y + (h - font.lineHeight) / 2 + 1;
            g.drawString(font, getMessage(), labelX, labelY, textColor, false);
        }
    }

    @Override public boolean isPauseScreen() { return false; }
}