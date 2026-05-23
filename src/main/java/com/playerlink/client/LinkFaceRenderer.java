
📄 File 1 — src/main/java/com/playerlink/client/ClientEvents.java
package com.playerlink.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.playerlink.PlayerLinkMod;
import com.playerlink.network.RequestWhitelistPacket;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlockEntity;
import net.minecraft.ChatFormatting;
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

    /** Right-click the link with an empty hand (NOT sneaking) → opens the picker. */
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

        chat(player, "§b[PlayerLink]§r Requesting player list...");
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
            chat(mc.player, "§e[PlayerLink]§r Look at a Redstone Link first");
            return;
        }
        BlockPos pos = bhr.getBlockPos();
        BlockEntity be = mc.level.getBlockEntity(pos);
        if (!(be instanceof RedstoneLinkBlockEntity)) {
            chat(mc.player, "§e[PlayerLink]§r That's not a Redstone Link");
            return;
        }
        chat(mc.player, "§b[PlayerLink]§r Requesting player list... (K)");
        PlayerLinkMod.LOGGER.info("[PlayerLink] K pressed on link@{} -> sending packet", pos);
        PacketDistributor.sendToServer(new RequestWhitelistPacket(pos));
    }

    private static void chat(Player p, String msg) {
        if (p != null) p.displayClientMessage(Component.literal(msg), false);
    }
}
📄 File 2 — src/main/java/com/playerlink/client/ClientPacketHandlers.java
package com.playerlink.client;

import com.playerlink.PlayerLinkMod;
import com.playerlink.network.WhitelistResponsePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ClientPacketHandlers {

    private ClientPacketHandlers() {}

    public static void handleWhitelistResponse(final WhitelistResponsePacket pkt, final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            try {
                Minecraft mc = Minecraft.getInstance();
                PlayerLinkMod.LOGGER.info("[PlayerLink] Got WhitelistResponse: {} entries, currentOwner={}",
                        pkt.entries().size(), pkt.currentOwner().orElse(null));

                if (mc.player != null) {
                    mc.player.displayClientMessage(
                            Component.literal("§a[PlayerLink]§r Got " + pkt.entries().size() + " players — opening picker..."),
                            false);
                }

                PlayerSelectScreen screen = new PlayerSelectScreen(
                        pkt.blockPos(),
                        pkt.currentOwner().orElse(null),
                        pkt.entries()
                );
                mc.setScreen(screen);
                PlayerLinkMod.LOGGER.info("[PlayerLink] PlayerSelectScreen set as active screen.");
            } catch (Throwable t) {
                PlayerLinkMod.LOGGER.error("[PlayerLink] handleWhitelistResponse crashed", t);
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    mc.player.displayClientMessage(
                            Component.literal("§c[PlayerLink]§r ERROR opening GUI: " + t.getMessage()),
                            false);
                }
            }
        });
    }
}
📄 File 3 — src/main/java/com/playerlink/client/LinkFaceRenderer.java
This repositions the floating face to sit right next to the existing frequency slots so it visually looks like a 3rd "slot button" on the link itself. Smaller and integrated.

package com.playerlink.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.playerlink.api.IOwnedLink;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.UUID;

public final class LinkFaceRenderer {

    private LinkFaceRenderer() {}

    public static void render(RedstoneLinkBlockEntity be,
                              PoseStack pose,
                              MultiBufferSource buffer,
                              int light,
                              int overlay) {
        if (!(be instanceof IOwnedLink owned)) return;
        UUID owner = owned.playerlink$getOwner();
        if (owner == null) return;

        ResourceLocation skin = SkinCache.get(owner, null);

        pose.pushPose();
        // Sit just above the block, centered — looks like a small icon hovering on it
        pose.translate(0.5D, 1.35D, 0.5D);

        // Billboard towards camera so it always reads as a face
        Quaternionf cam = Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation();
        pose.mulPose(cam);

        // Quarter-block size — feels like an item icon
        pose.scale(0.35F, 0.35F, 0.35F);

        Matrix4f m = pose.last().pose();
        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(skin));

        // Face UV: 8..16, 8..16 inside the 64x64 skin
        float u0 = 8f / 64f, u1 = 16f / 64f;
        float v0 = 8f / 64f, v1 = 16f / 64f;
        addVertex(vc, m, -0.5F, -0.5F, u0, v1, light, overlay);
        addVertex(vc, m,  0.5F, -0.5F, u1, v1, light, overlay);
        addVertex(vc, m,  0.5F,  0.5F, u1, v0, light, overlay);
        addVertex(vc, m, -0.5F,  0.5F, u0, v0, light, overlay);

        // Hat layer (transparent, slightly forward)
        pose.translate(0F, 0F, 0.01F);
        Matrix4f m2 = pose.last().pose();
        float hu0 = 40f / 64f, hu1 = 48f / 64f;
        float hv0 = 8f  / 64f, hv1 = 16f / 64f;
        addVertex(vc, m2, -0.5F, -0.5F, hu0, hv1, light, overlay);
        addVertex(vc, m2,  0.5F, -0.5F, hu1, hv1, light, overlay);
        addVertex(vc, m2,  0.5F,  0.5F, hu1, hv0, light, overlay);
        addVertex(vc, m2, -0.5F,  0.5F, hu0, hv0, light, overlay);

        pose.popPose();
    }

    private static void addVertex(VertexConsumer vc, Matrix4f m,
                                  float x, float y, float u, float v,
                                  int light, int overlay) {
        vc.addVertex(m, x, y, 0F)
          .setColor(255, 255, 255, 255)
          .setUv(u, v)
          .setOverlay(overlay)
          .setLight(light)
          .setNormal(0F, 0F, 1F);
    }
}