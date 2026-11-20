package com.capturepoint;

import com.capturepoint.command.CapturePointCommands;
import com.capturepoint.core.CapturePointManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CapturePointMod implements ModInitializer {
    public static final String MOD_ID = "capturepoint";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        CapturePointManager.init();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                CapturePointCommands.register(dispatcher));
        ServerTickEvents.END_SERVER_TICK.register(CapturePointManager::tick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> CapturePointManager.onPlayerJoin(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> CapturePointManager.onPlayerLeave(handler.player));
        ServerLifecycleEvents.SERVER_STOPPING.register(CapturePointManager::onServerStopping);
        LOGGER.info("Capture Point initialized");
    }
}
