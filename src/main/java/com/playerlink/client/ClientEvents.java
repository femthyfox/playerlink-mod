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

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().isClientSide()) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;

        Player player = event.getEntity();
        if (player.isShiftKeyDown()) return;

        ItemStack main = player.getMainHandItem();
        ItemStack off  = player.getOffhandItem();
        if (!main.isEmpty() || !off.isEmpty()) return;

        BlockPos pos = event.getPos();
        BlockEntity be = player.level().getBlockEntity(pos);
        if (!(be instanceof RedstoneLinkBlockEntity)) return;

        chat(player, "[PlayerLink] Requesting player list...");
        PlayerLinkMod.LOGGER.info("[PlayerLink] empty-hand RClick on link@{} -> sending packet", pos);
        PacketDistributor.sendToServer(new RequestWhitelistPacket(pos));

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
            chat(mc.player, "[PlayerLink] Look at a Redstone Link first");
            return;
        }
        BlockPos pos = bhr.getBlockPos();
        BlockEntity be = mc.level.getBlockEntity(pos);
        if (!(be instanceof RedstoneLinkBlockEntity)) {
            chat(mc.player, "[PlayerLink] That is not a Redstone Link");
            return;
        }
        chat(mc.player, "[PlayerLink] Requesting player list... (K)");
        PlayerLinkMod.LOGGER.info("[PlayerLink] K pressed on link@{} -> sending packet", pos);
        PacketDistributor.sendToServer(new RequestWhitelistPacket(pos));
    }

    private static void chat(Player p, String msg) {
        if (p != null) p.displayClientMessage(Component.literal(msg), false);
    }
}