package com.playerlink.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.playerlink.network.SetOwnerPacket;
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
 * Player-owner selection screen styled to evoke Create's cream/brown UI feel
 * (parchment panel, beveled borders, gold accents).
 */
public class PlayerSelectScreen extends Screen {

    // ─── CREATE-INSPIRED PALETTE ──────────────────────────────────────────
    private static final int COL_BG_DIM       = 0xC8000000; // global dim
    private static final int COL_PANEL_SHADOW = 0xFF1A0E05; // very dark brown
    private static final int COL_PANEL_BORDER = 0xFF3A2412; // dark brown border
    private static final int COL_PANEL_LIGHT  = 0xFFDFC79A; // cream interior
    private static final int COL_PANEL_DARK   = 0xFFB59669; // shadow side of cream
    private static final int COL_TITLE_BAR    = 0xFF5D3927; // brown banner
    private static final int COL_TITLE_HI     = 0xFF7A4A33;
    private static final int COL_TILE_BG      = 0xFFA88B5C;
    private static final int COL_TILE_BG_HI   = 0xFFC2A271;
    private static final int COL_TILE_BORDER  = 0xFF4A2F1A;
    private static final int COL_TILE_SELECT  = 0xFFFFD86A; // gold
    private static final int COL_TILE_CURRENT = 0xFF7BC04A; // green
    private static final int COL_TEXT_DARK    = 0xFF2B1808;
    private static final int COL_TEXT_LIGHT   = 0xFFFFE9C2;
    private static final int COL_TEXT_MUTED   = 0xFF6C4A28;
    private static final int COL_ACCENT_GOLD  = 0xFFFFD86A;

    // ─── LAYOUT ──────────────────────────────────────────────────────────
    private static final int FACE_SIZE   = 32;
    private static final int TILE_W      = 56;
    private static final int TILE_H      = 60;
    private static final int TILE_PAD    = 6;
    private static final int PANEL_MARGIN = 24;

    private final BlockPos blockPos;
    @Nullable private final UUID currentOwner;
    private final List<WhitelistResponsePacket.Entry> allEntries;
    private List<WhitelistResponsePacket.Entry> filtered;

    private EditBox searchBox;
    private Button assignButton, clearButton, closeButton;

    @Nullable private UUID selectedUuid;
    @Nullable private String selectedName;

    private int scroll = 0;

    // ── Panel rectangle (recomputed in init)
    private int panelX, panelY, panelW, panelH;

    // ── Inner content rects
    private int gridX, gridY, gridW, gridH;

    public PlayerSelectScreen(BlockPos pos, @Nullable UUID currentOwner, List<WhitelistResponsePacket.Entry> entries) {
        super(Component.translatable("playerlink.gui.select_owner.title"));
        this.blockPos = pos;
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
        // Panel sizing — centered, leaves room for HUD edges
        panelW = Math.min(440, width - PANEL_MARGIN * 2);
        panelH = Math.min(360, height - PANEL_MARGIN * 2);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;

        // Title bar 22px, search 26px (incl. padding), buttons 28px bottom
        int innerLeft = panelX + 12;
        int innerRight = panelX + panelW - 12;
        int innerWidth = innerRight - innerLeft;

        // Search box just under title bar
        int searchY = panelY + 32;
        searchBox = new EditBox(font, innerLeft + 1, searchY, innerWidth - 2, 16,
                Component.translatable("playerlink.gui.select_owner.search"));
        searchBox.setHint(Component.translatable("playerlink.gui.select_owner.search")
                .copy().withStyle(ChatFormatting.DARK_GRAY));
        searchBox.setResponder(s -> refilter());
        addRenderableWidget(searchBox);

        // Grid region between search and buttons
        gridX = innerLeft;
        gridY = searchY + 22;
        gridW = innerWidth;
        gridH = panelH - (gridY - panelY) - 38;

        // Buttons row at the bottom of the panel
        int btnY = panelY + panelH - 28;
        int btnW = 100, gap = 6, totalW = btnW * 3 + gap * 2;
        int btnLeft = panelX + (panelW - totalW) / 2;

        assignButton = Button.builder(
                Component.translatable("playerlink.gui.select_owner.button.assign"),
                b -> assignSelected())
                .pos(btnLeft, btnY).size(btnW, 20).build();
        assignButton.active = selectedUuid != null;
        addRenderableWidget(assignButton);

        clearButton = Button.builder(
                Component.translatable("playerlink.gui.select_owner.button.clear"),
                b -> { PacketDistributor.sendToServer(new SetOwnerPacket(blockPos, Optional.empty())); onClose(); })
                .pos(btnLeft + btnW + gap, btnY).size(btnW, 20).build();
        clearButton.active = currentOwner != null;
        addRenderableWidget(clearButton);

        closeButton = Button.builder(
                Component.translatable("playerlink.gui.select_owner.button.close"),
                b -> onClose())
                .pos(btnLeft + (btnW + gap) * 2, btnY).size(btnW, 20).build();
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
        PacketDistributor.sendToServer(new SetOwnerPacket(blockPos, Optional.of(selectedUuid)));
        onClose();
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
        // Skip vanilla blur — use our own dim
        g.fill(0, 0, width, height, COL_BG_DIM);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        renderBackground(g, mouseX, mouseY, pt);

        // ── Drop shadow under panel
        g.fill(panelX + 4, panelY + 5, panelX + panelW + 4, panelY + panelH + 5, 0x80000000);

        // ── Outer dark border
        drawBeveledPanel(g, panelX, panelY, panelW, panelH, COL_PANEL_BORDER, COL_PANEL_LIGHT, COL_PANEL_DARK);

        // ── Title bar
        int titleBarH = 18;
        g.fill(panelX + 4, panelY + 4, panelX + panelW - 4, panelY + 4 + titleBarH, COL_TITLE_BAR);
        // Title bar highlight (top edge)
        g.fill(panelX + 4, panelY + 4, panelX + panelW - 4, panelY + 5, COL_TITLE_HI);
        // Title bar shadow (bottom edge)
        g.fill(panelX + 4, panelY + 4 + titleBarH - 1, panelX + panelW - 4, panelY + 4 + titleBarH, 0xFF2A1810);

        // Title text
        g.drawString(font, title, panelX + 12, panelY + 9, COL_TEXT_LIGHT, true);

        // Current owner badge (right side of title bar)
        Component cur = (selectedUuid == null)
                ? Component.translatable("playerlink.gui.select_owner.current.none")
                : Component.translatable("playerlink.gui.select_owner.current",
                        selectedName == null ? selectedUuid.toString().substring(0, 8) : selectedName);
        int curColor = selectedUuid == null ? 0xFFB0B0B0 : COL_ACCENT_GOLD;
        int curX = panelX + panelW - 12 - font.width(cur);
        g.drawString(font, cur, curX, panelY + 9, curColor, true);

        // ── Search field background (dark inset)
        int sx = searchBox.getX() - 2, sy = searchBox.getY() - 2;
        int sw = searchBox.getWidth() + 4, sh = 20;
        drawInsetBox(g, sx, sy, sw, sh, 0xFF2B1A0C, COL_PANEL_DARK);

        // ── Grid background pane (subtle inset)
        drawInsetBox(g, gridX - 4, gridY - 4, gridW + 8, gridH + 8, 0xFF8E7144, COL_PANEL_DARK);

        // Empty whitelist message
        if (filtered.isEmpty()) {
            Component msg = Component.translatable("playerlink.gui.select_owner.empty");
            g.drawString(font, msg,
                    gridX + (gridW - font.width(msg)) / 2,
                    gridY + gridH / 2 - font.lineHeight / 2,
                    COL_TEXT_DARK, false);
        } else {
            drawTileGrid(g, mouseX, mouseY);
        }

        // ── Hint text below grid
        Component hint = Component.literal("Click a player to select  ·  Double-click to confirm")
                .withStyle(ChatFormatting.GRAY);
        g.drawString(font, hint,
                panelX + (panelW - font.width(hint)) / 2,
                panelY + panelH - 38,
                0xFF5C3F1F, false);

        // ── Render widgets (search + buttons)
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

            // Tile gradient (top→bottom)
            int top = isHover ? COL_TILE_BG_HI : COL_TILE_BG;
            int bot = isHover ? COL_TILE_BG : 0xFF8E7144;
            g.fillGradient(tx, ty, tx + TILE_W, ty + TILE_H, top, bot);

            // Border (state-aware)
            int border = isSelected ? COL_TILE_SELECT
                       : isCurrent  ? COL_TILE_CURRENT
                       : COL_TILE_BORDER;
            int borderThickness = (isSelected || isCurrent) ? 2 : 1;
            for (int t = 0; t < borderThickness; t++) {
                g.fill(tx + t, ty + t, tx + TILE_W - t, ty + 1 + t, border);
                g.fill(tx + t, ty + TILE_H - 1 - t, tx + TILE_W - t, ty + TILE_H - t, border);
                g.fill(tx + t, ty + t, tx + 1 + t, ty + TILE_H - t, border);
                g.fill(tx + TILE_W - 1 - t, ty + t, tx + TILE_W - t, ty + TILE_H - t, border);
            }

            // Face well (slight inset behind face)
            int faceX = tx + (TILE_W - FACE_SIZE) / 2;
            int faceY = ty + 6;
            g.fill(faceX - 1, faceY - 1, faceX + FACE_SIZE + 1, faceY + FACE_SIZE + 1, 0xFF3A2412);

            // Player face
            ResourceLocation skin = SkinCache.get(entry.uuid(), entry.name());
            RenderSystem.enableBlend();
            PlayerFaceRenderer.draw(g, skin, faceX, faceY, FACE_SIZE);
            RenderSystem.disableBlend();

            // Name (truncated)
            String name = entry.name();
            int maxNameW = TILE_W - 4;
            if (font.width(name) > maxNameW) {
                while (name.length() > 1 && font.width(name + "…") > maxNameW) {
                    name = name.substring(0, name.length() - 1);
                }
                name = name + "…";
            }
            int textColor = isSelected ? COL_TILE_SELECT
                          : isCurrent  ? COL_TILE_CURRENT
                          : COL_TEXT_DARK;
            g.drawString(font, name, tx + (TILE_W - font.width(name)) / 2, ty + FACE_SIZE + 10, textColor, false);

            // Subtle indicator dot for current-owner
            if (isCurrent) {
                int dotX = tx + TILE_W - 7;
                int dotY = ty + 3;
                g.fill(dotX, dotY, dotX + 4, dotY + 4, COL_TILE_CURRENT);
                g.fill(dotX + 1, dotY + 1, dotX + 3, dotY + 3, 0xFFFFFFFF);
            }
        }

        g.disableScissor();

        // Scrollbar
        if (maxScroll() > 0) {
            int barX = gridX + gridW - 4;
            g.fill(barX, gridY, barX + 3, gridY + gridH, 0x66000000);
            int barH = Math.max(20, gridH * gridH / (gridH + maxScroll()));
            int barY = gridY + (gridH - barH) * scroll / Math.max(1, maxScroll());
            g.fill(barX, barY, barX + 3, barY + barH, COL_ACCENT_GOLD);
        }
    }

    /**
     * Draws a Create-style beveled panel: outer dark border, light inner,
     * gradient top→bottom from light to slightly darker.
     */
    private void drawBeveledPanel(GuiGraphics g, int x, int y, int w, int h,
                                  int borderColor, int lightColor, int darkColor) {
        // Outer 1-px shadow ring
        g.fill(x - 1, y - 1, x + w + 1, y, COL_PANEL_SHADOW);
        g.fill(x - 1, y + h, x + w + 1, y + h + 1, COL_PANEL_SHADOW);
        g.fill(x - 1, y, x, y + h, COL_PANEL_SHADOW);
        g.fill(x + w, y, x + w + 1, y + h, COL_PANEL_SHADOW);

        // Main border (2 px)
        g.fill(x, y, x + w, y + 2, borderColor);
        g.fill(x, y + h - 2, x + w, y + h, borderColor);
        g.fill(x, y, x + 2, y + h, borderColor);
        g.fill(x + w - 2, y, x + w, y + h, borderColor);

        // Interior gradient
        g.fillGradient(x + 2, y + 2, x + w - 2, y + h - 2, lightColor, darkColor);

        // Inner highlight (1-px light line just inside the border)
        g.fill(x + 2, y + 2, x + w - 2, y + 3, 0x40FFFFFF);
        g.fill(x + 2, y + 2, x + 3, y + h - 2, 0x40FFFFFF);
    }

    private void drawInsetBox(GuiGraphics g, int x, int y, int w, int h, int fillColor, int borderColor) {
        g.fill(x, y, x + w, y + h, fillColor);
        // Top + left = dark, bottom + right = light → inset look
        g.fill(x, y, x + w, y + 1, 0xFF1A0E05);
        g.fill(x, y, x + 1, y + h, 0xFF1A0E05);
        g.fill(x, y + h - 1, x + w, y + h, borderColor);
        g.fill(x + w - 1, y, x + w, y + h, borderColor);
    }

    @Override public boolean isPauseScreen() { return false; }
}
