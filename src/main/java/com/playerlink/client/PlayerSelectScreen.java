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

public class PlayerSelectScreen extends Screen {

    private static final int FACE_SIZE   = 32;
    private static final int TILE_W      = 56;
    private static final int TILE_H      = 60;
    private static final int TILE_PAD    = 6;
    private static final int GRID_TOP    = 60;
    private static final int GRID_BOT_PAD = 60;

    private final BlockPos blockPos;
    @Nullable private final UUID currentOwner;
    private final List<WhitelistResponsePacket.Entry> allEntries;
    private List<WhitelistResponsePacket.Entry> filtered;

    private EditBox searchBox;
    private Button assignButton, clearButton;

    @Nullable private UUID selectedUuid;
    @Nullable private String selectedName;

    private int scroll = 0;

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
        int cx = width / 2;

        int searchW = Math.min(360, width - 40);
        searchBox = new EditBox(font, cx - searchW / 2, 35, searchW, 18,
                Component.translatable("playerlink.gui.select_owner.search"));
        searchBox.setHint(Component.translatable("playerlink.gui.select_owner.search")
                .copy().withStyle(ChatFormatting.DARK_GRAY));
        searchBox.setResponder(s -> refilter());
        addRenderableWidget(searchBox);

        int btnY = height - 30, btnW = 110, gap = 8, totalW = btnW * 3 + gap * 2;
        int btnLeft = cx - totalW / 2;

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

        addRenderableWidget(Button.builder(
                Component.translatable("playerlink.gui.select_owner.button.close"),
                b -> onClose())
                .pos(btnLeft + (btnW + gap) * 2, btnY).size(btnW, 20).build());
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

    private int gridLeft()   { return (width - gridWidth()) / 2; }
    private int gridWidth()  { int c = columns(); return c * TILE_W + (c - 1) * TILE_PAD; }
    private int gridHeight() { return height - GRID_TOP - GRID_BOT_PAD; }
    private int columns()    { return Math.max(1, (width - 40 + TILE_PAD) / (TILE_W + TILE_PAD)); }
    private int rows()       { return (filtered.size() + columns() - 1) / columns(); }
    private int maxScroll()  {
        int needed = rows() * (TILE_H + TILE_PAD);
        return Math.max(0, needed - gridHeight());
    }

    private int tileAt(double mx, double my) {
        int gx = gridLeft();
        int relX = (int)(mx - gx);
        int relY = (int)(my - GRID_TOP) + scroll;
        if (relX < 0 || relY < 0) return -1;
        int col = relX / (TILE_W + TILE_PAD);
        int row = relY / (TILE_H + TILE_PAD);
        if (col >= columns()) return -1;
        int colOff = relX - col * (TILE_W + TILE_PAD);
        int rowOff = relY - row * (TILE_H + TILE_PAD);
        if (colOff > TILE_W || rowOff > TILE_H) return -1;
        int idx = row * columns() + col;
        if (idx < 0 || idx >= filtered.size()) return -1;
        if (my < GRID_TOP || my > GRID_TOP + gridHeight()) return -1;
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
        if (mouseY >= GRID_TOP && mouseY <= GRID_TOP + gridHeight()) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll - (int)(dy * 24)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, dx, dy);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        super.render(g, mouseX, mouseY, pt);
        g.drawCenteredString(font, title, width / 2, 12, 0xFFFFFFFF);

        Component cur = (selectedUuid == null)
                ? Component.translatable("playerlink.gui.select_owner.current.none").copy().withStyle(ChatFormatting.GRAY)
                : Component.translatable("playerlink.gui.select_owner.current",
                        selectedName == null ? selectedUuid.toString().substring(0, 8) : selectedName)
                  .copy().withStyle(ChatFormatting.AQUA);
        g.drawCenteredString(font, cur, width / 2, 24, 0xFFFFFFFF);

        if (filtered.isEmpty()) {
            g.drawCenteredString(font,
                Component.translatable("playerlink.gui.select_owner.empty").copy().withStyle(ChatFormatting.RED),
                width / 2, height / 2, 0xFFFFFFFF);
            return;
        }

        int gx = gridLeft();
        int gy = GRID_TOP;
        int gw = gridWidth();
        int gh = gridHeight();

        g.enableScissor(gx - 2, gy - 2, gx + gw + 2, gy + gh + 2);

        int cols = columns();
        for (int i = 0; i < filtered.size(); i++) {
            int col = i % cols;
            int row = i / cols;
            int tx = gx + col * (TILE_W + TILE_PAD);
            int ty = gy + row * (TILE_H + TILE_PAD) - scroll;
            if (ty + TILE_H < gy || ty > gy + gh) continue;

            var entry = filtered.get(i);
            boolean isSelected = selectedUuid != null && selectedUuid.equals(entry.uuid());
            boolean isCurrent  = currentOwner != null && currentOwner.equals(entry.uuid());

            int bg = isSelected ? 0xFF335577 : isCurrent ? 0xFF224422 : 0x55000000;
            g.fill(tx, ty, tx + TILE_W, ty + TILE_H, bg);

            int border = isSelected ? 0xFF55AAFF : (isCurrent ? 0xFF55FF55 : 0x66FFFFFF);
            g.fill(tx, ty, tx + TILE_W, ty + 1, border);
            g.fill(tx, ty + TILE_H - 1, tx + TILE_W, ty + TILE_H, border);
            g.fill(tx, ty, tx + 1, ty + TILE_H, border);
            g.fill(tx + TILE_W - 1, ty, tx + TILE_W, ty + TILE_H, border);

            ResourceLocation skin = SkinCache.get(entry.uuid(), entry.name());
            int faceX = tx + (TILE_W - FACE_SIZE) / 2;
            int faceY = ty + 6;
            RenderSystem.enableBlend();
            PlayerFaceRenderer.draw(g, skin, faceX, faceY, FACE_SIZE);
            RenderSystem.disableBlend();

            String name = entry.name();
            int maxNameW = TILE_W - 4;
            if (font.width(name) > maxNameW) {
                while (name.length() > 1 && font.width(name + "…") > maxNameW) {
                    name = name.substring(0, name.length() - 1);
                }
                name = name + "…";
            }
            int textColor = isCurrent ? 0xFF55FF55 : 0xFFFFFFFF;
            g.drawString(font, name, tx + (TILE_W - font.width(name)) / 2, ty + FACE_SIZE + 10, textColor, false);
        }

        g.disableScissor();

        if (maxScroll() > 0) {
            int barX = gx + gw + 4;
            g.fill(barX, gy, barX + 3, gy + gh, 0x44FFFFFF);
            int barH = Math.max(20, gh * gh / (gh + maxScroll()));
            int barY = gy + (gh - barH) * scroll / Math.max(1, maxScroll());
            g.fill(barX, barY, barX + 3, barY + barH, 0xFFAAAAAA);
        }
    }

    @Override public boolean isPauseScreen() { return false; }
}
