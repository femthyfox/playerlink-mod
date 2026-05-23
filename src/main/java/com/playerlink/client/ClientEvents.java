package com.playerlink.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.playerlink.PlayerLinkMod;
import com.playerlink.network.RequestWhitelistPacket;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlockEntity;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = PlayerLinkMod.MODID, value = Dist.CLIENT)
public final class ClientEvents {

    public static final KeyMapping OPEN_OWNER_GUI = new KeyMapping(
            "key.playerlink.open_owner_gui",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K,
            "key.categories.playerlink");

    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_OWNER_GUI);
    }

    /**
     * "Button" trigger: right-click the link with an EMPTY hand, NOT sneaking.
     *  - Create's filter-slot interaction requires an item in hand, so this is unused by Create.
     *  - Sneak+RClick is Create's send/receive toggle, so we explicitly skip that.
     */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().isClientSide()) return;
        Player player = event.getEntity();

        // Avoid Create's sneak+rclick (send/receive toggle)
        if (player.isShiftKeyDown()) return;

        // Only fire if the main hand is empty (no item interaction conflict)
        ItemStack main = player.getMainHandItem();
        ItemStack off  = player.getOffhandItem();
        if (!main.isEmpty() || !off.isEmpty()) return;

        // Only the main hand event (avoid double-fire)
        if (event.getHand() != InteractionHand.MAIN_HAND) return;

        BlockPos pos = event.getPos();
        BlockEntity be = player.level().getBlockEntity(pos);
        if (!(be instanceof RedstoneLinkBlockEntity)) return;

        PlayerLinkMod.LOGGER.info("[PlayerLink] Empty-hand RClick on link@{} -> opening picker", pos);
        PacketDistributor.sendToServer(new RequestWhitelistPacket(pos));

        // Tell Create's normal handler to NOT also run
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null) return;
        while (OPEN_OWNER_GUI.consumeClick()) {
            tryOpenOwnerGuiViaKeybind(mc);
        }
    }

    private static void tryOpenOwnerGuiViaKeybind(Minecraft mc) {
        HitResult hit = mc.hitResult;
        if (hit == null || !(hit instanceof BlockHitResult bhr) || hit.getType() != HitResult.Type.BLOCK) {
            mc.player.displayClientMessage(Component.literal("[PlayerLink] Look at a Redstone Link"), true);
            return;
        }
        BlockPos pos = bhr.getBlockPos();
        BlockEntity be = mc.level.getBlockEntity(pos);
        if (!(be instanceof RedstoneLinkBlockEntity)) {
            mc.player.displayClientMessage(Component.literal("[PlayerLink] That's not a Redstone Link"), true);
            return;
        }
        PlayerLinkMod.LOGGER.info("[PlayerLink] K pressed on link@{} -> opening picker", pos);
        PacketDistributor.sendToServer(new RequestWhitelistPacket(pos));
    }
}