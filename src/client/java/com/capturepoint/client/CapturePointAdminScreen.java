package com.capturepoint.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Simple, three-section administrator interface. All actions remain server-authorized commands. */
public final class CapturePointAdminScreen extends Screen {
    private enum Page { HOME, ROUND, POINTS, TEAMS, SAVED }
    private final Page page;
    private EditBox pointName, orderIndex, seconds, roundName, playerName;

    public CapturePointAdminScreen() { this(Page.HOME); }
    private CapturePointAdminScreen(Page page) {
        super(Component.translatable("screen.capturepoint.title"));
        this.page = page;
    }

    @Override protected void init() {
        int c = width / 2, left = c - 155, right = c + 5, w = 150, y = 92, gap = 27;
        if (page == Page.HOME) {
            addRenderableWidget(button("gui.capturepoint.tab_points", c - 150, 82, 300, () -> open(Page.POINTS)));
            addRenderableWidget(button("gui.capturepoint.tab_teams", c - 150, 122, 300, () -> open(Page.TEAMS)));
            addRenderableWidget(button("gui.capturepoint.tab_round", c - 150, 162, 300, () -> open(Page.ROUND)));
            addRenderableWidget(button("gui.capturepoint.tab_saved", c - 150, 202, 300, () -> open(Page.SAVED)));
        } else {
            addRenderableWidget(button("gui.capturepoint.back", c - 155, 43, 92, () -> open(Page.HOME)));
            addRenderableWidget(button("gui.capturepoint.tab_points", c - 57, 43, 98, () -> open(Page.POINTS)));
            addRenderableWidget(button("gui.capturepoint.tab_teams", c + 45, 43, 98, () -> open(Page.TEAMS)));
            addRenderableWidget(button("gui.capturepoint.tab_round", c + 147, 43, 98, () -> open(Page.ROUND)));
            switch (page) {
                case ROUND -> {
                    addRenderableWidget(button("gui.capturepoint.start", left, y, w, () -> run("cappoint start")));
                    addRenderableWidget(button("gui.capturepoint.pause", right, y, w, () -> run("cappoint pause"))); y += gap;
                    addRenderableWidget(button("gui.capturepoint.resume", left, y, w, () -> run("cappoint resume")));
                    addRenderableWidget(button("gui.capturepoint.stop", right, y, w, () -> run("cappoint stop"))); y += gap;
                    addRenderableWidget(button("gui.capturepoint.status", left, y, w, () -> run("cappoint status")));
                    addRenderableWidget(button("gui.capturepoint.list", right, y, w, () -> run("cappoint list"))); y += gap;
                    seconds = edit(left, y, 305, "gui.capturepoint.seconds", "gui.capturepoint.seconds_hint");
                    seconds.setValue("60"); y += gap;
                    addRenderableWidget(button("gui.capturepoint.capture_time", left, y, w, () -> setTime("capture")));
                    addRenderableWidget(button("gui.capturepoint.hold_time", right, y, w, () -> setTime("hold")));
                }
                case POINTS -> {
                    pointName = edit(left, y, 305, "gui.capturepoint.point_name", "gui.capturepoint.point_hint"); y += gap;
                    addRenderableWidget(button("gui.capturepoint.create_point", left, y, w, () -> withPoint("point create")));
                    addRenderableWidget(button("gui.capturepoint.set_center", right, y, w, () -> withPoint("point center"))); y += gap;
                    addRenderableWidget(button("gui.capturepoint.pos1", left, y, w, () -> withPoint("point pos1")));
                    addRenderableWidget(button("gui.capturepoint.pos2", right, y, w, () -> withPoint("point pos2"))); y += gap;
                    orderIndex = edit(left, y, 65, "gui.capturepoint.order", "gui.capturepoint.order_hint");
                    addRenderableWidget(button("gui.capturepoint.move_point", left + 75, y, 155, this::movePoint)); y += gap;
                    addRenderableWidget(button("gui.capturepoint.remove_point", left, y, w, () -> withPoint("point remove")));
                    addRenderableWidget(button("gui.capturepoint.list", right, y, w, () -> run("cappoint list")));
                }
                case TEAMS -> {
                    playerName = edit(left, y, 305, "gui.capturepoint.player", "gui.capturepoint.player_hint"); y += gap;
                    addRenderableWidget(button("gui.capturepoint.assign_blue", left, y, w, () -> assignTeam("BLUE")));
                    addRenderableWidget(button("gui.capturepoint.assign_red", right, y, w, () -> assignTeam("RED"))); y += gap;
                    addRenderableWidget(button("gui.capturepoint.random_teams", left, y, 305, () -> run("cappoint team random"))); y += gap;
                    addRenderableWidget(button("gui.capturepoint.list_teams", left, y, 305, () -> run("cappoint team list")));
                }
                case SAVED -> {
                    roundName = edit(left, y, 305, "gui.capturepoint.round_name", "gui.capturepoint.round_hint"); y += gap;
                    addRenderableWidget(button("gui.capturepoint.save_round", left, y, w, () -> withRound("save")));
                    addRenderableWidget(button("gui.capturepoint.load_round", right, y, w, () -> withRound("load"))); y += gap;
                    addRenderableWidget(button("gui.capturepoint.list_rounds", left, y, 305, () -> run("cappoint rounds")));
                }
                default -> {}
            }
        }
        addRenderableWidget(button("gui.capturepoint.close", c - 75, height - 27, 150, this::onClose));
    }

    private void open(Page target) { minecraft.gui.setScreen(new CapturePointAdminScreen(target)); }
    private Button button(String key, int x, int y, int w, Runnable action) {
        return Button.builder(Component.translatable(key), b -> action.run()).bounds(x, y, w, 20).build();
    }
    private EditBox edit(int x, int y, int w, String label, String hint) {
        EditBox box = new EditBox(font, x, y, w, 20, Component.translatable(label));
        box.setMaxLength(40); box.setHint(Component.translatable(hint)); addRenderableWidget(box); return box;
    }
    private boolean validPoint() {
        return pointName != null && pointName.getValue().trim().matches("[A-Za-z0-9_-]+");
    }
    private void withPoint(String action) { if (validPoint()) run("cappoint " + action + " " + pointName.getValue().trim()); }
    private void movePoint() {
        if (!validPoint() || orderIndex == null) return;
        String n = orderIndex.getValue().trim();
        if (n.matches("[1-9][0-9]{0,2}")) run("cappoint point move " + pointName.getValue().trim() + " " + n);
    }
    private void setTime(String type) {
        if (seconds == null) return;
        String n = seconds.getValue().trim();
        if (n.matches("[1-9][0-9]{0,3}") && Integer.parseInt(n) <= 3600) run("cappoint time " + type + " " + n);
    }
    private void assignTeam(String team) {
        if (playerName != null && playerName.getValue().trim().matches("[A-Za-z0-9_]{1,16}"))
            run("cappoint team set " + playerName.getValue().trim() + " " + team);
    }
    private void withRound(String action) {
        if (roundName != null && roundName.getValue().trim().matches("[A-Za-z0-9_-]{1,40}"))
            run("cappoint " + action + " " + roundName.getValue().trim());
    }
    private void run(String command) { CapturePointClient.command(command); }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, 15, 0xFFFFFFFF);
        String subtitle = page == Page.HOME ? "Capture Point" : Component.translatable("gui.capturepoint.subtitle").getString();
        graphics.centeredText(font, Component.literal(subtitle), width / 2, 29, 0xFFAAB7C4);
        if (page == Page.TEAMS) {
            graphics.centeredText(font, Component.literal("BLUE"), width / 2 - 78, 78, 0xFF5577FF);
            graphics.centeredText(font, Component.literal("RED"), width / 2 + 78, 78, 0xFFFF5555);
        }
    }
    @Override public boolean isPauseScreen() { return false; }
}
