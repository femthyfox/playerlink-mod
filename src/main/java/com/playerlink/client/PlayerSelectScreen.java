package com.playerlink.client;

import com.playerlink.network.SetOwnerPacket;
import com.playerlink.network.WhitelistResponsePacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * GUI shown after the player presses the "Open Link Owner" keybind
 * while looking at a Redstone Link. Lists all whitelisted players;
 * the player can pick one to assign as the link's owner, or clear it.
 */
public class PlayerSelectScreen extends Screen {

    private final BlockPos blockPos;
    @Nullable
    private final UUID currentOwner;
    private final List<WhitelistResponsePacket.Entry> allEntries;

    private EditBox searchBox;
    private PlayerList list;
    private Button assignButton;
    private Button clearButton;

    public PlayerSelectScreen(BlockPos blockPos, @Nullable UUID currentOwner, List<WhitelistResponsePacket.Entry> entries) {
        super(Component.translatable("playerlink.gui.select_owner.title"));
        this.blockPos = blockPos;
        this.currentOwner = currentOwner;
        this.allEntries = entries;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int listTop = 60;
        int listBottom = this.height - 60;
        int listWidth = Math.min(360, this.width - 40);
        int listLeft = centerX - listWidth / 2;

        // Search box
        this.searchBox = new EditBox(this.font, listLeft, 35, listWidth, 18,
                Component.translatable("playerlink.gui.select_owner.search"));
        this.searchBox.setHint(Component.translatable("playerlink.gui.select_owner.search")
                .copy().withStyle(ChatFormatting.DARK_GRAY));
        this.searchBox.setResponder(s -> refilter());
        this.addRenderableWidget(this.searchBox);

        // List
        this.list = new PlayerList(this.minecraft, listWidth, listBottom - listTop, listTop, 22);
        this.list.setX(listLeft);
        this.addRenderableWidget(this.list);

        // Buttons
        int btnY = this.height - 30;
        int btnW = 110;
        int gap = 8;
        int totalW = btnW * 3 + gap * 2;
        int btnLeft = centerX - totalW / 2;

        this.assignButton = Button.builder(
                Component.translatable("playerlink.gui.select_owner.button.assign"),
                b -> assignSelected())
                .pos(btnLeft, btnY).size(btnW, 20).build();
        this.assignButton.active = false;
        this.addRenderableWidget(this.assignButton);

        this.clearButton = Button.builder(
                Component.translatable("playerlink.gui.select_owner.button.clear"),
                b -> clearOwner())
                .pos(btnLeft + (btnW + gap), btnY).size(btnW, 20).build();
        this.clearButton.active = (currentOwner != null);
        this.addRenderableWidget(this.clearButton);

        this.addRenderableWidget(Button.builder(
                Component.translatable("playerlink.gui.select_owner.button.close"),
                b -> onClose())
                .pos(btnLeft + (btnW + gap) * 2, btnY).size(btnW, 20).build());

        refilter();
    }

    private void refilter() {
        String q = this.searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        List<WhitelistResponsePacket.Entry> filtered = new ArrayList<>();
        for (WhitelistResponsePacket.Entry e : allEntries) {
            if (q.isEmpty() || e.name().toLowerCase(Locale.ROOT).contains(q)) {
                filtered.add(e);
            }
        }
        this.list.refresh(filtered);
        updateButtonState();
    }

    private void updateButtonState() {
        this.assignButton.active = this.list.getSelected() != null;
    }

    private void assignSelected() {
        PlayerEntry sel = this.list.getSelected();
        if (sel == null) return;
        PacketDistributor.sendToServer(new SetOwnerPacket(blockPos, Optional.of(sel.entry.uuid())));
        onClose();
    }

    private void clearOwner() {
        PacketDistributor.sendToServer(new SetOwnerPacket(blockPos, Optional.empty()));
        onClose();
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        super.render(gfx, mouseX, mouseY, partialTick);
        gfx.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFFFF);

        Component currentText;
        if (currentOwner == null) {
            currentText = Component.translatable("playerlink.gui.select_owner.current.none")
                    .copy().withStyle(ChatFormatting.GRAY);
        } else {
            String displayName = allEntries.stream()
                    .filter(e -> e.uuid().equals(currentOwner))
                    .map(WhitelistResponsePacket.Entry::name)
                    .findFirst()
                    .orElse(currentOwner.toString().substring(0, 8));
            currentText = Component.translatable("playerlink.gui.select_owner.current", displayName)
                    .copy().withStyle(ChatFormatting.AQUA);
        }
        gfx.drawCenteredString(this.font, currentText, this.width / 2, 24, 0xFFFFFFFF);

        if (allEntries.isEmpty()) {
            gfx.drawCenteredString(this.font,
                    Component.translatable("playerlink.gui.select_owner.empty")
                            .copy().withStyle(ChatFormatting.RED),
                    this.width / 2, this.height / 2, 0xFFFFFFFF);
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }

    /* ------------------------- List components ------------------------- */

    private class PlayerList extends ObjectSelectionList<PlayerEntry> {

        public PlayerList(Minecraft mc, int width, int height, int top, int itemHeight) {
            super(mc, width, height, top, itemHeight);
        }

        public void refresh(List<WhitelistResponsePacket.Entry> entries) {
            this.clearEntries();
            for (WhitelistResponsePacket.Entry e : entries) {
                this.addEntry(new PlayerEntry(e));
            }
        }

        @Override
        public void setSelected(@Nullable PlayerEntry entry) {
            super.setSelected(entry);
            PlayerSelectScreen.this.updateButtonState();
        }

        @Override
        public int getRowWidth() { return this.width - 12; }

        @Override
        protected int scrollBarX() { return this.getX() + this.width - 6; }
    }

    public class PlayerEntry extends ObjectSelectionList.Entry<PlayerEntry> {
        private final WhitelistResponsePacket.Entry entry;

        public PlayerEntry(WhitelistResponsePacket.Entry entry) {
            this.entry = entry;
        }

        @Override
        public Component getNarration() {
            return Component.literal(entry.name());
        }

        @Override
        public void render(GuiGraphics gfx, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovered, float partialTick) {
            boolean isCurrent = currentOwner != null && currentOwner.equals(entry.uuid());
            int colour = isCurrent ? 0xFF55FFFF : 0xFFFFFFFF;
            gfx.drawString(PlayerSelectScreen.this.font, entry.name(), left + 6, top + 6, colour);
            if (isCurrent) {
                gfx.drawString(PlayerSelectScreen.this.font,
                        Component.literal("● current").withStyle(ChatFormatting.AQUA),
                        left + width - 70, top + 6, 0xFF55FFFF);
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0) {
                PlayerList parent = PlayerSelectScreen.this.list;
                parent.setSelected(this);
                return true;
            }
            return false;
        }
    }
}
