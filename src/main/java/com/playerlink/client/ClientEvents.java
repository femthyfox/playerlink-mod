package com.playerlink.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.playerlink.PlayerLinkMod;
import com.playerlink.network.RequestWhitelistPacket;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlockEntity;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = PlayerLinkMod.MODID, value = Dist.CLIENT)
public final class ClientEvents {

    private ClientEvents() {}

    public static final KeyMapping OPEN_OWNER_GUI = new KeyMapping(
            "key.playerlink.open_owner_gui",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            "key.categories.playerlink"
    );

    public static void registerKeyMappings(final RegisterKeyMappingsEvent event) {
        event.register(OPEN_OWNER_GUI);
    }

    @SubscribeEvent
    public static void onClientTick(final ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null) return;

        while (OPEN_OWNER_GUI.consumeClick()) {
            tryOpenOwnerGui(mc, mc.player, mc.level);
        }
    }

    @SubscribeEvent
    public static void onUseBlock(final PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide() == false) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!event.getItemStack().isEmpty()) return;
        if (event.getEntity().isShiftKeyDown()) return;

        BlockEntity be = event.getLevel().getBlockEntity(event.getPos());
        if (!(be instanceof RedstoneLinkBlockEntity)) return;

        PlayerLinkMod.LOGGER.info("[PlayerLink] Use-key on link at {}, sending packet", event.getPos());
        PacketDistributor.sendToServer(new RequestWhitelistPacket(event.getPos()));
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    private static void tryOpenOwnerGui(Minecraft mc, LocalPlayer player, ClientLevel level) {
        HitResult hit = mc.hitResult;
        if (!(hit instanceof BlockHitResult bhr) || hit.getType() != HitResult.Type.BLOCK) return;
        BlockEntity be = level.getBlockEntity(bhr.getBlockPos());
        if (!(be instanceof RedstoneLinkBlockEntity)) return;
        PlayerLinkMod.LOGGER.info("[PlayerLink] K-key on link at {}, sending packet", bhr.getBlockPos());
        PacketDistributor.sendToServer(new RequestWhitelistPacket(bhr.getBlockPos()));
    }
}