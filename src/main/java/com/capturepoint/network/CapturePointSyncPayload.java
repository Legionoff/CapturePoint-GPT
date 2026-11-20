package com.capturepoint.network;

import com.capturepoint.core.CapturePointManager;
import com.capturepoint.core.CapturePointManager.PointDef;
import com.capturepoint.core.CapturePointManager.Team;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/** Server-authoritative snapshot used by the admin interface. */
public record CapturePointSyncPayload(String json) implements CustomPacketPayload {
    public static final Type<CapturePointSyncPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("capturepoint", "admin_sync"));
    public static final StreamCodec<ByteBuf, CapturePointSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            CapturePointSyncPayload::json,
            CapturePointSyncPayload::new
    );
    private static final Gson GSON = new Gson();

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static CapturePointSyncPayload create(ServerPlayer viewer) {
        JsonObject root = new JsonObject();
        var round = CapturePointManager.getRound();
        root.addProperty("roundName", round.name);
        root.addProperty("centerIndex", round.centerIndex);
        root.addProperty("captureSeconds", round.captureSeconds);
        root.addProperty("holdSeconds", round.holdSeconds);
        root.addProperty("running", CapturePointManager.isRunning());
        root.addProperty("paused", CapturePointManager.isPaused());
        root.addProperty("activeIndex", CapturePointManager.getActiveIndex());

        JsonArray points = new JsonArray();
        for (int i = 0; i < round.points.size(); i++) {
            PointDef point = round.points.get(i);
            JsonObject entry = new JsonObject();
            entry.addProperty("index", i);
            entry.addProperty("name", point.name);
            entry.addProperty("owner", point.owner == null ? "" : point.owner);
            entry.addProperty("dimension", point.dimension);
            entry.addProperty("complete", point.isComplete());
            entry.addProperty("center", i == round.centerIndex);
            entry.addProperty("x1", point.x1);
            entry.addProperty("y1", point.y1);
            entry.addProperty("z1", point.z1);
            entry.addProperty("x2", point.x2);
            entry.addProperty("y2", point.y2);
            entry.addProperty("z2", point.z2);
            entry.addProperty("hasPos1", point.hasPos1);
            entry.addProperty("hasPos2", point.hasPos2);
            points.add(entry);
        }
        root.add("points", points);

        JsonArray players = new JsonArray();
        for (ServerPlayer player : viewer.level().getServer().getPlayerList().getPlayers()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("name", player.getName().getString());
            Team team = CapturePointManager.getTeam(player.getUUID());
            entry.addProperty("team", team == null ? "NONE" : team.name());
            players.add(entry);
        }
        root.add("players", players);
        return new CapturePointSyncPayload(GSON.toJson(root));
    }
}
