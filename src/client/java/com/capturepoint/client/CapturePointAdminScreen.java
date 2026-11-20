package com.capturepoint.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Server-synchronized Capture Point control panel. */
public final class CapturePointAdminScreen extends Screen {
    private enum Page { HOME, POINTS, TEAMS, ROUND, SAVED }

    private static JsonObject snapshot = new JsonObject();
    private static boolean snapshotReceived;
    private final Page page;
    private String selectedPointName;
    private int pointStart;
    private int blueScroll;
    private int redScroll;
    private EditBox pointName;
    private EditBox playerName;
    private EditBox seconds;
    private EditBox roundName;

    public CapturePointAdminScreen() {
        this(Page.HOME, null, 0, 0, 0);
    }

    private CapturePointAdminScreen(Page page, String selectedPointName, int pointStart, int blueScroll, int redScroll) {
        super(Component.translatable("screen.capturepoint.title"));
        this.page = page;
        this.selectedPointName = selectedPointName;
        this.pointStart = Math.max(0, pointStart);
        this.blueScroll = Math.max(0, blueScroll);
        this.redScroll = Math.max(0, redScroll);
    }

    /** Called on the client thread after the server sends the authorized snapshot. */
    public static void acceptSnapshot(String json) {
        try {
            snapshot = JsonParser.parseString(json).getAsJsonObject();
            snapshotReceived = true;
            Minecraft client = Minecraft.getInstance();
            if (client.gui.screen() instanceof CapturePointAdminScreen current) {
                client.gui.setScreen(new CapturePointAdminScreen(
                        current.page, current.selectedPointName, current.pointStart, current.blueScroll, current.redScroll));
            }
        } catch (RuntimeException exception) {
            // A malformed snapshot is ignored; the existing screen remains usable.
        }
    }

    @Override
    protected void init() {
        int center = width / 2;
        if (page == Page.HOME) {
            int gap = 8;
            int half = (width - 28) / 2;
            addRenderableWidget(button("gui.capturepoint.tab_points", 10, 83, half, 28, () -> open(Page.POINTS)));
            addRenderableWidget(button("gui.capturepoint.tab_teams", 18 + half, 83, half, 28, () -> open(Page.TEAMS)));
            addRenderableWidget(button("gui.capturepoint.tab_round", center - 112, 137, 224, 34, () -> open(Page.ROUND)));
        } else {
            addRenderableWidget(button("gui.capturepoint.tab_points", 8, 43, (width - 24) / 3, 20, () -> open(Page.POINTS)));
            addRenderableWidget(button("gui.capturepoint.tab_teams", 12 + (width - 24) / 3, 43, (width - 24) / 3, 20, () -> open(Page.TEAMS)));
            addRenderableWidget(button("gui.capturepoint.tab_round", 16 + 2 * (width - 24) / 3, 43, (width - 24) / 3, 20, () -> open(Page.ROUND)));
            switch (page) {
                case POINTS -> initPoints();
                case TEAMS -> initTeams();
                case ROUND -> initRound();
                case SAVED -> initSaved();
                default -> { }
            }
        }
        addRenderableWidget(button("gui.capturepoint.close", center - 42, height - 22, 84, 18, this::onClose));
    }

    private void initPoints() {
        JsonArray points = points();
        int total = points.size();
        int visible = Math.min(8, total);
        if (total > 8) {
            addRenderableWidget(button("‹", 5, 72, 18, 18, () -> changePointWindow(-8)));
            addRenderableWidget(button("›", width - 23, 72, 18, 18, () -> changePointWindow(8)));
        }
        int maxStart = Math.max(0, total - visible);
        pointStart = Math.min(pointStart, maxStart);
        int left = total > 8 ? 30 : 18;
        int right = total > 8 ? width - 30 : width - 18;
        int lineY = 83;
        if (visible > 1) {
            int firstX = pointX(0, visible, left, right);
            int lastX = pointX(visible - 1, visible, left, right);
            // The actual line is extracted in extractRenderState to stay lightweight.
        }
        for (int slot = 0; slot < visible; slot++) {
            int globalIndex = pointStart + slot;
            JsonObject point = points.get(globalIndex).getAsJsonObject();
            String name = str(point, "name", "");
            int x = pointX(slot, visible, left, right);
            int color = pointColor(point);
            boolean selected = name.equalsIgnoreCase(selectedPointName == null ? defaultPointName() : selectedPointName);
            Component marker = Component.literal("●").withStyle(style -> style.withColor(color));
            addRenderableWidget(Button.builder(marker, b -> selectPoint(name)).bounds(x - 9, lineY - 9, 18, 18).build());
            if (selected) {
                // Marker highlight is rendered behind the widgets.
            }
        }

        if (total > 0) {
            JsonObject selected = selectedPoint();
            String name = str(selected, "name", "");
            String owner = str(selected, "owner", "");
            String status = owner.isBlank() ? (bool(selected, "center") ? "NEUTRAL" : "—") : owner;
            addRenderableWidget(button("gui.capturepoint.pos1", 8, 162, (width - 28) / 2, 20,
                    () -> pointAction("pos1")));
            addRenderableWidget(button("gui.capturepoint.pos2", 20 + (width - 28) / 2, 162, (width - 28) / 2, 20,
                    () -> pointAction("pos2")));

            int colGap = 4;
            int colWidth = (width - 20 - 3 * colGap) / 4;
            addRenderableWidget(button("‹", 8, 187, colWidth, 20, () -> moveSelectedPoint(-1)));
            addRenderableWidget(button("›", 8 + colWidth + colGap, 187, colWidth, 20, () -> moveSelectedPoint(1)));
            addRenderableWidget(button("gui.capturepoint.set_center", 8 + 2 * (colWidth + colGap), 187, colWidth, 20,
                    () -> pointAction("center")));
            addRenderableWidget(button("gui.capturepoint.remove_point", 8 + 3 * (colWidth + colGap), 187, colWidth, 20,
                    () -> removeSelectedPoint()));
        }

        pointName = edit(8, 137, width - 130, "gui.capturepoint.point_name", "gui.capturepoint.point_hint");
        addRenderableWidget(button("gui.capturepoint.add_point", width - 114, 137, 106, 20, this::createPoint));
    }

    private void initTeams() {
        int margin = 8;
        int gap = 8;
        int colWidth = (width - 2 * margin - gap) / 2;
        int rightX = margin + colWidth + gap;
        List<JsonObject> blue = playersFor("BLUE");
        List<JsonObject> red = playersFor("RED");
        int rows = 4;
        for (int i = 0; i < rows; i++) {
            addTeamRow(blue, blueScroll, i, margin, colWidth, "BLUE");
            addTeamRow(red, redScroll, i, rightX, colWidth, "RED");
        }
        if (blue.size() > rows) {
            addRenderableWidget(button("‹", margin + colWidth / 2 - 22, 157, 20, 16, () -> scrollTeam("BLUE", -1)));
            addRenderableWidget(button("›", margin + colWidth / 2 + 2, 157, 20, 16, () -> scrollTeam("BLUE", 1)));
        }
        if (red.size() > rows) {
            addRenderableWidget(button("‹", rightX + colWidth / 2 - 22, 157, 20, 16, () -> scrollTeam("RED", -1)));
            addRenderableWidget(button("›", rightX + colWidth / 2 + 2, 157, 20, 16, () -> scrollTeam("RED", 1)));
        }
        playerName = edit(margin, 179, 104, "gui.capturepoint.player", "gui.capturepoint.player_hint");
        addRenderableWidget(button("BLUE", 118, 179, 82, 20, () -> assignTeam("BLUE")));
        addRenderableWidget(button("RED", 206, 179, 82, 20, () -> assignTeam("RED")));
        addRenderableWidget(button("gui.capturepoint.random_teams", margin, 203, 150, 18,
                () -> run("cappoint team random")));
    }

    private void addTeamRow(List<JsonObject> players, int scroll, int row, int x, int colWidth, String team) {
        int index = scroll + row;
        if (index >= players.size()) return;
        String name = str(players.get(index), "name", "");
        int y = 79 + row * 19;
        int textWidth = colWidth - 26;
        addRenderableWidget(Button.builder(Component.literal(name), b -> {
            if (playerName != null) playerName.setValue(name);
        }).bounds(x, y, textWidth, 17).build());
        addRenderableWidget(button("×", x + textWidth + 3, y, 20, 17, () -> run("cappoint team set " + name + " NONE")));
    }

    private void initRound() {
        int left = 8;
        int right = width / 2 + 4;
        int col = (width - 20) / 2;
        addRenderableWidget(button("gui.capturepoint.start", left, 79, col, 20, () -> run("cappoint start")));
        addRenderableWidget(button("gui.capturepoint.pause", right, 79, col, 20, () -> run("cappoint pause")));
        addRenderableWidget(button("gui.capturepoint.resume", left, 104, col, 20, () -> run("cappoint resume")));
        addRenderableWidget(button("gui.capturepoint.stop", right, 104, col, 20, () -> run("cappoint stop")));
        addRenderableWidget(button("gui.capturepoint.status", left, 129, col, 20, () -> run("cappoint status")));
        seconds = edit(left, 155, 110, "gui.capturepoint.seconds", "gui.capturepoint.seconds_hint");
        seconds.setValue("60");
        addRenderableWidget(button("gui.capturepoint.capture_time", left + 116, 155, col - 116, 20, () -> setTime("capture")));
        addRenderableWidget(button("gui.capturepoint.hold_time", left, 181, col, 20, () -> setTime("hold")));
        addRenderableWidget(button("gui.capturepoint.tab_saved", right, 181, col, 20, () -> open(Page.SAVED)));
    }

    private void initSaved() {
        int left = 8;
        int right = width / 2 + 4;
        int col = (width - 20) / 2;
        roundName = edit(left, 88, 305, "gui.capturepoint.round_name", "gui.capturepoint.round_hint");
        addRenderableWidget(button("gui.capturepoint.save_round", left, 116, col, 20, () -> withRound("save")));
        addRenderableWidget(button("gui.capturepoint.load_round", right, 116, col, 20, () -> withRound("load")));
        addRenderableWidget(button("gui.capturepoint.list_rounds", left, 144, 305, 20, () -> run("cappoint rounds")));
        addRenderableWidget(button("gui.capturepoint.back", left, 176, 305, 20, () -> open(Page.ROUND)));
    }

    private void changePointWindow(int amount) {
        int maxStart = Math.max(0, points().size() - 8);
        pointStart = Math.max(0, Math.min(maxStart, pointStart + amount));
        refresh();
    }

    private void selectPoint(String name) {
        selectedPointName = name;
        refresh();
    }

    private void moveSelectedPoint(int direction) {
        JsonObject point = selectedPoint();
        if (point == null) return;
        int index = integer(point, "index", -1);
        int target = index + direction + 1;
        if (target >= 1 && target <= points().size()) {
            run("cappoint point move " + str(point, "name", "") + " " + target);
        }
    }

    private void createPoint() {
        if (pointName == null) return;
        String name = pointName.getValue().trim();
        if (name.matches("[A-Za-z0-9_-]{1,32}")) {
            selectedPointName = name;
            run("cappoint point create " + name);
        }
    }

    private void pointAction(String action) {
        JsonObject point = selectedPoint();
        if (point != null) run("cappoint point " + action + " " + str(point, "name", ""));
    }

    private void removeSelectedPoint() {
        JsonObject point = selectedPoint();
        if (point == null) return;
        selectedPointName = null;
        run("cappoint point remove " + str(point, "name", ""));
    }

    private void scrollTeam(String team, int direction) {
        if (team.equals("BLUE")) blueScroll = Math.max(0, Math.min(Math.max(0, playersFor(team).size() - 4), blueScroll + direction * 4));
        else redScroll = Math.max(0, Math.min(Math.max(0, playersFor(team).size() - 4), redScroll + direction * 4));
        refresh();
    }

    private void assignTeam(String team) {
        if (playerName != null && playerName.getValue().trim().matches("[A-Za-z0-9_]{1,16}"))
            run("cappoint team set " + playerName.getValue().trim() + " " + team);
    }

    private void setTime(String type) {
        if (seconds == null) return;
        String value = seconds.getValue().trim();
        if (value.matches("[1-9][0-9]{0,3}") && Integer.parseInt(value) <= 3600)
            run("cappoint time " + type + " " + value);
    }

    private void withRound(String action) {
        if (roundName != null && roundName.getValue().trim().matches("[A-Za-z0-9_-]{1,40}"))
            run("cappoint " + action + " " + roundName.getValue().trim());
    }

    private void open(Page target) {
        minecraft.gui.setScreen(new CapturePointAdminScreen(target, selectedPointName, pointStart, blueScroll, redScroll));
        CapturePointClient.command("cappoint ui");
    }

    private void refresh() {
        minecraft.gui.setScreen(new CapturePointAdminScreen(page, selectedPointName, pointStart, blueScroll, redScroll));
    }

    private void run(String command) {
        CapturePointClient.command(command);
        // Commands are sent in order; the second command returns the authoritative updated state.
        CapturePointClient.command("cappoint ui");
    }

    private Button button(String key, int x, int y, int w, int h, Runnable action) {
        return button(Component.translatable(key), x, y, w, h, action);
    }

    private Button button(Component label, int x, int y, int w, int h, Runnable action) {
        return Button.builder(label, b -> action.run()).bounds(x, y, w, h).build();
    }

    private EditBox edit(int x, int y, int w, String label, String hint) {
        EditBox box = new EditBox(font, x, y, w, 20, Component.translatable(label));
        box.setMaxLength(40);
        box.setHint(Component.translatable(hint));
        addRenderableWidget(box);
        return box;
    }

    private JsonArray points() {
        return snapshot.has("points") && snapshot.get("points").isJsonArray()
                ? snapshot.getAsJsonArray("points") : new JsonArray();
    }

    private JsonArray players() {
        return snapshot.has("players") && snapshot.get("players").isJsonArray()
                ? snapshot.getAsJsonArray("players") : new JsonArray();
    }

    private List<JsonObject> playersFor(String team) {
        List<JsonObject> result = new ArrayList<>();
        for (var element : players()) {
            JsonObject player = element.getAsJsonObject();
            if (team.equals(str(player, "team", "NONE"))) result.add(player);
        }
        result.sort(Comparator.comparing(p -> str(p, "name", ""), String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    private JsonObject selectedPoint() {
        JsonArray all = points();
        if (all.isEmpty()) return null;
        String wanted = selectedPointName == null ? defaultPointName() : selectedPointName;
        for (var element : all) {
            JsonObject point = element.getAsJsonObject();
            if (str(point, "name", "").equalsIgnoreCase(wanted)) return point;
        }
        selectedPointName = defaultPointName();
        for (var element : all) {
            JsonObject point = element.getAsJsonObject();
            if (str(point, "name", "").equalsIgnoreCase(selectedPointName)) return point;
        }
        return all.get(0).getAsJsonObject();
    }

    private String defaultPointName() {
        JsonArray all = points();
        if (all.isEmpty()) return null;
        int center = integer(snapshot, "centerIndex", 0);
        center = Math.max(0, Math.min(center, all.size() - 1));
        return str(all.get(center).getAsJsonObject(), "name", null);
    }

    private static String str(JsonObject object, String key, String fallback) {
        return object != null && object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : fallback;
    }

    private static int integer(JsonObject object, String key, int fallback) {
        try { return object != null && object.has(key) ? object.get(key).getAsInt() : fallback; }
        catch (RuntimeException ignored) { return fallback; }
    }

    private static boolean bool(JsonObject object, String key) {
        return object != null && object.has(key) && object.get(key).getAsBoolean();
    }

    private static int pointColor(JsonObject point) {
        String owner = str(point, "owner", "");
        if ("BLUE".equals(owner)) return 0xFF438BFF;
        if ("RED".equals(owner)) return 0xFFEF4545;
        if (bool(point, "center")) return 0xFFFFD34E;
        return 0xFFB8BEC8;
    }

    private static int pointX(int slot, int count, int left, int right) {
        if (count <= 1) return (left + right) / 2;
        return left + (right - left) * slot / (count - 1);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, 15, 0xFFFFFFFF);
        graphics.centeredText(font, Component.translatable("gui.capturepoint.subtitle"), width / 2, 29, 0xFFAAB7C4);

        if (page == Page.POINTS) {
            JsonArray all = points();
            int visible = Math.min(8, all.size());
            int left = all.size() > 8 ? 30 : 18;
            int right = all.size() > 8 ? width - 30 : width - 18;
            int lineY = 83;
            if (visible > 1) {
                graphics.fill(pointX(0, visible, left, right), lineY - 1,
                        pointX(visible - 1, visible, left, right) + 1, lineY + 2, 0xFF727984);
            }
            for (int slot = 0; slot < visible; slot++) {
                JsonObject point = all.get(pointStart + slot).getAsJsonObject();
                int x = pointX(slot, visible, left, right);
                if (str(point, "name", "").equalsIgnoreCase(selectedPointName == null ? defaultPointName() : selectedPointName)) {
                    graphics.outline(x - 10, lineY - 10, 20, 20, 0xFFFFFFFF);
                }
            }
            if (!snapshotReceived) {
                graphics.centeredText(font, Component.translatable("gui.capturepoint.loading"), width / 2, 111, 0xFFAAB7C4);
            } else if (all.isEmpty()) {
                graphics.centeredText(font, Component.translatable("gui.capturepoint.no_points"), width / 2, 111, 0xFFAAB7C4);
            } else {
                JsonObject point = selectedPoint();
                String owner = str(point, "owner", "");
                String ownerLabel = owner.isBlank() ? (bool(point, "center") ? "NEUTRAL" : "—") : owner;
                String status = str(point, "name", "") + "  •  " + ownerLabel
                        + (bool(point, "center") ? "  •  ★" : "");
                graphics.centeredText(font, Component.literal(status), width / 2, 111, 0xFFFFFFFF);
                String p1 = bool(point, "hasPos1") ? integer(point, "x1", 0) + ", " + integer(point, "y1", 0) + ", " + integer(point, "z1", 0) : "—";
                String p2 = bool(point, "hasPos2") ? integer(point, "x2", 0) + ", " + integer(point, "y2", 0) + ", " + integer(point, "z2", 0) : "—";
                graphics.centeredText(font, Component.literal("POS1: " + p1 + "     POS2: " + p2), width / 2, 124, 0xFFADB7C4);
            }
        }

        if (page == Page.TEAMS) {
            List<JsonObject> blue = playersFor("BLUE");
            List<JsonObject> red = playersFor("RED");
            graphics.centeredText(font, Component.literal("BLUE  " + blue.size()), width / 4, 68, 0xFF5591FF);
            graphics.centeredText(font, Component.literal("RED  " + red.size()), width * 3 / 4, 68, 0xFFFF6565);
            if (!snapshotReceived) graphics.centeredText(font, Component.translatable("gui.capturepoint.loading"), width / 2, 145, 0xFFAAB7C4);
        }

        if (page == Page.ROUND) {
            String round = str(snapshot, "roundName", "New Round");
            String state = bool(snapshot, "running") ? (bool(snapshot, "paused") ? "PAUSED" : "RUNNING") : "STOPPED";
            graphics.centeredText(font, Component.literal(round + "  •  " + state), width / 2, 67, 0xFFAAB7C4);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
