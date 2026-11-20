package com.capturepoint.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import com.capturepoint.network.CapturePointSyncPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.InputConstants;

public final class CapturePointClient implements ClientModInitializer {
    private static KeyMapping openMenuKey;

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(CapturePointSyncPayload.TYPE, (payload, context) ->
                context.client().execute(() -> CapturePointAdminScreen.acceptSnapshot(payload.json())));

        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath("capturepoint", "admin"));
        openMenuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.capturepoint.open_menu",
                InputConstants.Type.KEYBOARD,
                InputConstants.KEY_J,
                category));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openMenuKey.consumeClick()) {
                if (client.player == null || client.gui.screen() != null) continue;
                var commandNode = client.player.connection.getCommands().getRoot().getChild("cappoint");
                // The root literal may be visible to everyone; check an admin-only child.
                boolean canUseAdminCommands = commandNode != null && commandNode.getChild("start") != null;
                if (canUseAdminCommands) {
                    client.gui.setScreen(new CapturePointAdminScreen());
                    command("cappoint ui");
                } else {
                    client.player.sendOverlayMessage(Component.translatable("message.capturepoint.admin_only"));
                }
            }
        });
    }

    public static void command(String command) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) client.player.connection.sendCommand(command);
    }
}
