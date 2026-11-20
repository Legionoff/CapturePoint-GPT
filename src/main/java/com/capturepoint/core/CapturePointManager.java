package com.capturepoint.core;

import com.capturepoint.CapturePointMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.world.BossEvent;
import net.minecraft.world.level.Level;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Server-authoritative match state and persistence for Capture Point. */
public final class CapturePointManager {
    public enum Team {
        BLUE, RED;
        public ChatFormatting formatting() { return this == BLUE ? ChatFormatting.AQUA : ChatFormatting.RED; }
        public Team opposite() { return this == BLUE ? RED : BLUE; }
    }
    public enum Phase { WAITING, CAPTURING, HOLDING, PAUSED, COMPLETE }

    public static final class PointDef {
        public String name = "Point";
        public String dimension = Level.OVERWORLD.identifier().toString();
        public int x1, y1, z1, x2, y2, z2;
        public boolean hasPos1, hasPos2;
        public String owner = "";
        public boolean isComplete() { return hasPos1 && hasPos2; }
        public boolean contains(ServerPlayer player) {
            if (!isComplete() || !player.level().dimension().identifier().toString().equals(dimension)) return false;
            BlockPos p = player.blockPosition();
            return p.getX() >= Math.min(x1, x2) && p.getX() <= Math.max(x1, x2)
                    && p.getY() >= Math.min(y1, y2) && p.getY() <= Math.max(y1, y2)
                    && p.getZ() >= Math.min(z1, z2) && p.getZ() <= Math.max(z1, z2);
        }
        public BlockPos minCorner() { return new BlockPos(Math.min(x1,x2), Math.min(y1,y2), Math.min(z1,z2)); }
        public BlockPos maxCorner() { return new BlockPos(Math.max(x1,x2), Math.max(y1,y2), Math.max(z1,z2)); }
    }

    public static final class RoundDef {
        public String name = "New Round";
        public List<PointDef> points = new ArrayList<>();
        public int centerIndex = 0;
        public int captureSeconds = 60;
        public int holdSeconds = 60;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path ROOT = FabricLoader.getInstance().getConfigDir().resolve("capturepoint");
    private static final Path CURRENT = ROOT.resolve("current-round.json");
    private static RoundDef round = new RoundDef();
    private static final Map<UUID, Team> teams = new HashMap<>();
    private static final ServerBossEvent bossBar = new ServerBossEvent(
            UUID.randomUUID(), Component.literal("CAPTURE POINT"), BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.PROGRESS);
    private static MinecraftServer server;
    private static boolean running;
    private static boolean paused;
    private static int activeIndex = -1;
    private static Phase phase = Phase.WAITING;
    private static Phase phaseBeforePause = Phase.WAITING;
    private static Team owner;
    private static Team capturingTeam;
    private static int captureTicks;
    private static int holdTicks;
    private static int tickCounter;

    private CapturePointManager() { }

    public static void init() {
        try {
            Files.createDirectories(ROOT.resolve("rounds"));
            if (Files.exists(CURRENT)) {
                RoundDef loaded = GSON.fromJson(Files.readString(CURRENT), RoundDef.class);
                if (loaded != null) round = loaded;
            }
            Path teamFile = ROOT.resolve("teams.json");
            if (Files.exists(teamFile)) {
                Map<String, String> saved = GSON.fromJson(Files.readString(teamFile), Map.class);
                if (saved != null) saved.forEach((id, value) -> { try { teams.put(UUID.fromString(id), Team.valueOf(value)); } catch (Exception ignored) { } });
            }
        } catch (Exception e) {
            CapturePointMod.LOGGER.error("Could not load Capture Point configuration", e);
        }
    }

    public static void tick(MinecraftServer minecraftServer) {
        server = minecraftServer;
        boolean timerTick = ++tickCounter >= 20;
        if (timerTick) {
            tickCounter = 0;
            drawAllBoundaries(minecraftServer);
        }
        if (!running || paused || phase == Phase.COMPLETE || activeIndex < 0 || activeIndex >= round.points.size()) return;
        PointDef point = round.points.get(activeIndex);
        if (!point.isComplete()) return;

        int blue = 0, red = 0;
        for (ServerPlayer player : minecraftServer.getPlayerList().getPlayers()) {
            Team team = teams.get(player.getUUID());
            if (team != null && point.contains(player)) {
                if (team == Team.BLUE) blue++; else red++;
            }
        }
        Team advantage = blue > red ? Team.BLUE : red > blue ? Team.RED : null;
        updateBossBar(minecraftServer, point, blue, red, advantage);

        if (advantage == null) {
            if (blue == 0 && red == 0) {
                // Empty point: capture progress is preserved, but the post-capture hold timer keeps running.
                if (phase == Phase.PAUSED && phaseBeforePause == Phase.HOLDING) phase = Phase.HOLDING;
                if (phase == Phase.HOLDING && timerTick) {
                    holdTicks++;
                    if (holdTicks >= Math.max(1, round.holdSeconds)) completePoint(minecraftServer, point);
                }
            } else if (phase != Phase.PAUSED) {
                // Equal positive numbers pause the current stage without resetting its progress.
                phaseBeforePause = phase;
                phase = Phase.PAUSED;
            }
            return;
        }
        if (phase == Phase.PAUSED) phase = phaseBeforePause;

        if (owner != null && advantage == owner) {
            // The defender has regained superiority before the takeover was completed.
            if (capturingTeam != null && capturingTeam != owner) {
                capturingTeam = null;
                captureTicks = 0;
                holdTicks = 0;
                phase = Phase.WAITING;
                announce(Component.translatable("message.capturepoint.capture_stopped", teamLabel(owner), point.name));
            }
            return;
        }

        if (capturingTeam == null || capturingTeam != advantage) {
            capturingTeam = advantage;
            captureTicks = 0;
            holdTicks = 0;
            phase = Phase.CAPTURING;
            announce(Component.translatable("message.capturepoint.capture_started", teamLabel(advantage), point.name));
        }

        if (phase == Phase.CAPTURING && timerTick) {
            captureTicks++;
            if (captureTicks >= Math.max(1, round.captureSeconds)) {
                phase = Phase.HOLDING;
                holdTicks = 0;
                announce(Component.translatable("message.capturepoint.hold_started", teamLabel(capturingTeam), point.name));
            }
        } else if (phase == Phase.HOLDING && timerTick) {
            holdTicks++;
            if (holdTicks >= Math.max(1, round.holdSeconds)) {
                completePoint(minecraftServer, point);
            }
        }
    }

    private static void updateBossBar(MinecraftServer s, PointDef point, int blue, int red, Team advantage) {
        for (ServerPlayer player : s.getPlayerList().getPlayers()) bossBar.addPlayer(player);
        Phase shownPhase = phase == Phase.PAUSED ? phaseBeforePause : phase;
        Component stage = Component.translatable(shownPhase == Phase.HOLDING ? "bossbar.capturepoint.stage_hold" : (capturingTeam == null ? "bossbar.capturepoint.stage_waiting" : "bossbar.capturepoint.stage_capture"));
        bossBar.setName(Component.translatable("bossbar.capturepoint.title", point.name, stage, blue, red));
        if (shownPhase == Phase.HOLDING) {
            bossBar.setColor(phase == Phase.PAUSED ? BossEvent.BossBarColor.WHITE : (capturingTeam == Team.BLUE ? BossEvent.BossBarColor.BLUE : BossEvent.BossBarColor.RED));
            bossBar.setProgress(Math.min(1f, (float) holdTicks / Math.max(1, round.holdSeconds)));
        } else if (capturingTeam != null) {
            bossBar.setColor(phase == Phase.PAUSED ? BossEvent.BossBarColor.WHITE : (capturingTeam == Team.BLUE ? BossEvent.BossBarColor.BLUE : BossEvent.BossBarColor.PINK));
            bossBar.setProgress(Math.min(1f, (float) captureTicks / Math.max(1, round.captureSeconds)));
        } else {
            bossBar.setColor(BossEvent.BossBarColor.WHITE);
            bossBar.setProgress(0f);
        }
    }


    private static void drawAllBoundaries(MinecraftServer s) {
        for (int i = 0; i < round.points.size(); i++) {
            PointDef point = round.points.get(i);
            if (!point.isComplete()) continue;
            Team pointOwner = parseTeam(point.owner);
            int color = 0xA0A0A0;
            if (pointOwner != null) color = pointOwner == Team.BLUE ? 0x2878FF : 0xE52B32;
            if (i == activeIndex && capturingTeam != null) {
                boolean holding = phase == Phase.HOLDING || (phase == Phase.PAUSED && phaseBeforePause == Phase.HOLDING);
                if (capturingTeam == Team.BLUE) color = holding ? 0x1768FF : 0x83B8FF;
                else color = holding ? 0xF0202A : 0xFF9A9F;
            }
            DustParticleOptions dust = new DustParticleOptions(color, 0.85f);
            for (ServerLevel level : s.getAllLevels()) {
                if (!level.dimension().identifier().toString().equals(point.dimension)) continue;
                int minX = Math.min(point.x1, point.x2), maxX = Math.max(point.x1, point.x2);
                int minZ = Math.min(point.z1, point.z2), maxZ = Math.max(point.z1, point.z2);
                double y = Math.min(point.y1, point.y2) + 1.02;
                drawEdge(level, dust, minX, minZ, maxX + 1, minZ, y);
                drawEdge(level, dust, maxX + 1, minZ, maxX + 1, maxZ + 1, y);
                drawEdge(level, dust, maxX + 1, maxZ + 1, minX, maxZ + 1, y);
                drawEdge(level, dust, minX, maxZ + 1, minX, minZ, y);
            }
        }
    }

    private static void drawEdge(ServerLevel level, DustParticleOptions dust, int x1, int z1, int x2, int z2, double y) {
        int length = Math.max(Math.abs(x2 - x1), Math.abs(z2 - z1));
        int steps = Math.max(1, Math.min(80, length));
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            double x = x1 + (x2 - x1) * t;
            double z = z1 + (z2 - z1) * t;
            level.sendParticles(dust, x, y, z, 1, 0, 0, 0, 0);
        }
    }

    private static void completePoint(MinecraftServer s, PointDef point) {
        owner = capturingTeam;
        point.owner = owner.name();
        Team winner = owner;
        announce(Component.translatable("message.capturepoint.sector_secured", teamLabel(winner), point.name));
        int next = activeIndex + (winner == Team.RED ? -1 : 1);
        captureTicks = 0;
        holdTicks = 0;
        capturingTeam = null;
        phase = Phase.WAITING;
        phaseBeforePause = Phase.WAITING;
        if (next < 0 || next >= round.points.size()) {
            phase = Phase.COMPLETE;
            running = false;
            bossBar.setName(Component.translatable("bossbar.capturepoint.complete"));
            bossBar.setProgress(1f);
            announce(Component.translatable("message.capturepoint.round_over").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            for (ServerPlayer player : s.getPlayerList().getPlayers()) {
                player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 10));
                player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("message.capturepoint.round_over")));
            }
            bossBar.setVisible(false);
            return;
        }
        activeIndex = next;
        owner = parseTeam(round.points.get(activeIndex).owner);
        announce(Component.translatable("message.capturepoint.next_sector", teamLabel(winner), round.points.get(activeIndex).name));
        saveCurrent();
    }

    private static void announce(Component message) {
        if (server != null) server.getPlayerList().broadcastSystemMessage(message, false);
    }
    private static Component teamLabel(Team team) {
        return Component.literal(team.name()).withStyle(team.formatting(), ChatFormatting.BOLD);
    }

    private static Team parseTeam(String value) {
        if (value == null || value.isBlank()) return null;
        try { return Team.valueOf(value); } catch (IllegalArgumentException ignored) { return null; }
    }

    public static void onPlayerJoin(ServerPlayer player) {
        if (running && server != null) bossBar.addPlayer(player);
    }
    public static void onPlayerLeave(ServerPlayer player) {
        bossBar.removePlayer(player);
    }

    public static void onServerStopping(MinecraftServer stoppingServer) {
        if (server == stoppingServer) {
            saveCurrent();
            for (ServerPlayer player : stoppingServer.getPlayerList().getPlayers()) bossBar.removePlayer(player);
            server = null;
        }
    }

    public static boolean start() {
        if (round.points.isEmpty() || round.centerIndex < 0 || round.centerIndex >= round.points.size()) return false;
        for (PointDef p : round.points) if (!p.isComplete()) return false;
        for (PointDef p : round.points) p.owner = "";
        activeIndex = round.centerIndex;
        owner = null;
        capturingTeam = null;
        captureTicks = holdTicks = tickCounter = 0;
        phase = Phase.WAITING;
        phaseBeforePause = Phase.WAITING;
        paused = false;
        running = true;
        bossBar.setVisible(true);
        if (server != null) for (ServerPlayer p : server.getPlayerList().getPlayers()) bossBar.addPlayer(p);
        announce(Component.translatable("message.capturepoint.round_started").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        return true;
    }
    public static void stop() {
        running = false; paused = false; phase = Phase.WAITING; activeIndex = -1; tickCounter = 0;
        capturingTeam = null; owner = null; captureTicks = holdTicks = 0; phaseBeforePause = Phase.WAITING;
        if (server != null) for (ServerPlayer p : server.getPlayerList().getPlayers()) bossBar.removePlayer(p);
        bossBar.setVisible(false);
    }
    public static void pause() {
        if (running) {
            paused = true;
            bossBar.setColor(BossEvent.BossBarColor.WHITE);
        }
    }
    public static void resume() {
        if (running) paused = false;
    }
    public static boolean isRunning() { return running; }
    public static boolean isPaused() { return paused; }
    public static RoundDef getRound() { return round; }
    public static List<PointDef> getPoints() { return round.points; }
    public static int getActiveIndex() { return activeIndex; }
    public static Phase getPhase() { return phase; }
    public static Map<UUID, Team> getTeams() { return teams; }
    public static Team getTeam(UUID id) { return teams.get(id); }

    public static PointDef createPoint(String name, ServerPlayer player) {
        if (running || findPoint(name) != null) return null;
        PointDef point = new PointDef();
        point.name = name;
        point.dimension = player.level().dimension().identifier().toString();
        BlockPos pos = player.blockPosition();
        point.x1 = point.x2 = pos.getX(); point.y1 = point.y2 = pos.getY(); point.z1 = point.z2 = pos.getZ();
        point.hasPos1 = point.hasPos2 = true;
        round.points.add(point);
        if (round.points.size() == 1) round.centerIndex = 0;
        saveCurrent();
        return point;
    }
    public static PointDef findPoint(String name) {
        return round.points.stream().filter(p -> p.name.equalsIgnoreCase(name)).findFirst().orElse(null);
    }
    public static boolean setCorner(String name, ServerPlayer player, boolean first) {
        if (running) return false;
        PointDef p = findPoint(name); if (p == null) return false;
        BlockPos pos = player.blockPosition();
        String dimension = player.level().dimension().identifier().toString();
        if (!first && p.hasPos1 && !p.dimension.equals(dimension)) return false;
        if (first) {
            if (p.hasPos2 && !p.dimension.equals(dimension)) p.hasPos2 = false;
            p.dimension = dimension;
            p.x1=pos.getX(); p.y1=pos.getY(); p.z1=pos.getZ(); p.hasPos1=true;
        } else {
            p.dimension = dimension;
            p.x2=pos.getX(); p.y2=pos.getY(); p.z2=pos.getZ(); p.hasPos2=true;
        }
        saveCurrent(); return true;
    }
    public static boolean setCenter(String name) {
        if (running) return false;
        int i = indexOf(name); if (i < 0) return false;
        round.centerIndex = i; saveCurrent(); return true;
    }
    public static boolean movePoint(String name, int oneBasedIndex) {
        if (running) return false;
        int old = indexOf(name); int target = oneBasedIndex - 1;
        if (old < 0 || target < 0 || target >= round.points.size()) return false;
        PointDef p = round.points.remove(old); round.points.add(target, p);
        if (round.centerIndex == old) round.centerIndex = target;
        else if (old < round.centerIndex && target >= round.centerIndex) round.centerIndex--;
        else if (old > round.centerIndex && target <= round.centerIndex) round.centerIndex++;
        saveCurrent(); return true;
    }
    public static boolean removePoint(String name) {
        int i = indexOf(name);
        if (i < 0 || running) return false;

        int previousCenter = round.centerIndex;
        round.points.remove(i);
        if (round.points.isEmpty()) {
            round.centerIndex = 0;
        } else if (i < previousCenter) {
            round.centerIndex = previousCenter - 1;
        } else if (i == previousCenter) {
            // If the central point is deleted, choose the point now occupying
            // its old slot, or the new last point when it was at the end.
            round.centerIndex = Math.min(i, round.points.size() - 1);
        }
        saveCurrent();
        return true;
    }
    private static int indexOf(String name) {
        for (int i=0;i<round.points.size();i++) if (round.points.get(i).name.equalsIgnoreCase(name)) return i;
        return -1;
    }
    public static void setTimes(int capture, int hold) {
        round.captureSeconds=Math.max(1,capture); round.holdSeconds=Math.max(1,hold); saveCurrent();
    }
    public static void assign(ServerPlayer player, Team team) { teams.put(player.getUUID(), team); saveTeams(); }
    public static void unassign(ServerPlayer player) { teams.remove(player.getUUID()); saveTeams(); }
    public static void randomBalance() {
        if (server == null) return;
        List<ServerPlayer> players = new ArrayList<>(server.getPlayerList().getPlayers());
        Collections.shuffle(players, new Random());
        teams.clear();
        for (int i=0;i<players.size();i++) teams.put(players.get(i).getUUID(), i % 2 == 0 ? Team.BLUE : Team.RED);
        saveTeams();
    }
    private static void saveTeams() {
        try {
            Map<String, String> saved = new HashMap<>();
            teams.forEach((id, team) -> saved.put(id.toString(), team.name()));
            Files.createDirectories(ROOT);
            Files.writeString(ROOT.resolve("teams.json"), GSON.toJson(saved));
        } catch (IOException e) { CapturePointMod.LOGGER.error("Could not save teams", e); }
    }
    public static void saveCurrent() {
        try { Files.createDirectories(ROOT); Files.writeString(CURRENT, GSON.toJson(round)); }
        catch (IOException e) { CapturePointMod.LOGGER.error("Could not save current round", e); }
    }
    public static boolean saveRound(String name) {
        try {
            Files.createDirectories(ROOT.resolve("rounds"));
            round.name=name;
            Files.writeString(ROOT.resolve("rounds").resolve(safeName(name)+".json"), GSON.toJson(round));
            saveCurrent(); return true;
        } catch (IOException e) { CapturePointMod.LOGGER.error("Could not save round", e); return false; }
    }
    public static boolean loadRound(String name) {
        Path file=ROOT.resolve("rounds").resolve(safeName(name)+".json");
        if (!Files.exists(file) || running) return false;
        try {
            RoundDef loaded=GSON.fromJson(Files.readString(file), RoundDef.class);
            if (loaded==null || loaded.points==null || loaded.points.isEmpty()
                    || loaded.centerIndex < 0 || loaded.centerIndex >= loaded.points.size()) return false;
            round=loaded;
            saveCurrent();
            return true;
        } catch (Exception e) {
            CapturePointMod.LOGGER.error("Could not load round", e);
            return false;
        }
    }

    public static List<String> listSavedRounds() {
        Path directory = ROOT.resolve("rounds");
        if (!Files.isDirectory(directory)) return List.of();
        try (var files = Files.list(directory)) {
            return files.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(file -> file.endsWith(".json"))
                    .map(file -> file.substring(0, file.length() - 5))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        } catch (IOException e) {
            CapturePointMod.LOGGER.error("Could not list saved Capture Point rounds", e);
            return List.of();
        }
    }

    private static String safeName(String name) {
        return name.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
