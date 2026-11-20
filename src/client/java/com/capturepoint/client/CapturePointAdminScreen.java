package com.capturepoint.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Tabbed administrator control panel. The screen only sends commands; every
 * mutating operation is validated again by the authoritative server.
 */
public final class CapturePointAdminScreen extends Screen {
    private enum Tab { ROUND, POINTS, TEAMS, SETTINGS, SAVED }

    private final Tab tab;
    private EditBox pointName;
    private EditBox orderIndex;
    private EditBox seconds;
    private EditBox roundName;
    private EditBox playerName;

    public CapturePointAdminScreen() {
        this(Tab.ROUND);
    }

    private CapturePointAdminScreen(Tab tab) {
        super(Component.translatable("screen.capturepoint.title"));
        this.tab = tab;
    }

    @Override
    protected void init() {
        int center = this.width / 2;
        int left = center - 155;
        int right = center + 5;
        int width = 150;
        int y = 96;
        int h = 18;
        int gap = 22;

        addRenderableWidget(tabButton(Tab.ROUND, center - 155, 43, 98));
        addRenderableWidget(tabButton(Tab.POINTS, center - 53, 43, 98));
        addRenderableWidget(tabButton(Tab.TEAMS, center + 49, 43, 98));
        addRenderableWidget(tabButton(Tab.SETTINGS, center - 155, 66, 98));
        addRenderableWidget(tabButton(Tab.SAVED, center - 53, 66, 98));

        switch (tab) {
            case ROUND -> {
                addRenderableWidget(button("gui.capturepoint.start", left, y, width, () -> run("cappoint start")));
                addRenderableWidget(button("gui.capturepoint.pause", right, y, width, () -> run("cappoint pause"))); y += gap;
                addRenderableWidget(button("gui.capturepoint.resume", left, y, width, () -> run("cappoint resume")));
                addRenderableWidget(button("gui.capturepoint.stop", right, y, width, () -> run("cappoint stop"))); y += gap;
                addRenderableWidget(button("gui.capturepoint.status", left, y, width, () -> run("cappoint status")));
                addRenderableWidget(button("gui.capturepoint.list", right, y, width, () -> run("cappoint list")));
            }
            case POINTS -> {
                pointName = edit(left, y, 305, "gui.capturepoint.point_name", "gui.capturepoint.point_hint"); y += gap;
                addRenderableWidget(button("gui.capturepoint.create_point", left, y, width, () -> withPoint("point create")));
                addRenderableWidget(button("gui.capturepoint.set_center", right, y, width, () -> withPoint("point center"))); y += gap;
                addRenderableWidget(button("gui.capturepoint.pos1", left, y, width, () -> withPoint("point pos1")));
                addRenderableWidget(button("gui.capturepoint.pos2", right, y, width, () -> withPoint("point pos2"))); y += gap;
                orderIndex = edit(left, y, 65, "gui.capturepoint.order", "gui.capturepoint.order_hint");
                addRenderableWidget(button("gui.capturepoint.move_point", left + 75, y, 155, this::movePoint)); y += gap;
                addRenderableWidget(button("gui.capturepoint.remove_point", left, y, width, () -> withPoint("point remove")));
                addRenderableWidget(button("gui.capturepoint.list", right, y, width, () -> run("cappoint list")));
            }
            case TEAMS -> {
                playerName = edit(left, y, 305, "gui.capturepoint.player", "gui.capturepoint.player_hint"); y += gap;
                addRenderableWidget(button("gui.capturepoint.assign_blue", left, y, width, () -> assignTeam("BLUE")));
                addRenderableWidget(button("gui.capturepoint.assign_red", right, y, width, () -> assignTeam("RED"))); y += gap;
                addRenderableWidget(button("gui.capturepoint.random_teams", left, y, width, () -> run("cappoint team random")));
                addRenderableWidget(button("gui.capturepoint.list_teams", right, y, width, () -> run("cappoint team list")));
            }
            case SETTINGS -> {
                seconds = edit(left, y, 305, "gui.capturepoint.seconds", "gui.capturepoint.seconds_hint");
                seconds.setValue("60"); y += gap;
                addRenderableWidget(button("gui.capturepoint.capture_time", left, y, width, () -> setTime("capture")));
                addRenderableWidget(button("gui.capturepoint.hold_time", right, y, width, () -> setTime("hold")));
            }
            case SAVED -> {
                roundName = edit(left, y, 305, "gui.capturepoint.round_name", "gui.capturepoint.round_hint"); y += gap;
                addRenderableWidget(button("gui.capturepoint.save_round", left, y, width, () -> withRound("save")));
                addRenderableWidget(button("gui.capturepoint.load_round", right, y, width, () -> withRound("load"))); y += gap;
                addRenderableWidget(button("gui.capturepoint.list_rounds", left, y, 305, () -> run("cappoint rounds")));
            }
        }

        addRenderableWidget(button("gui.capturepoint.close", center - 75, this.height - 25, 150, this::onClose));
    }

    private Button tabButton(Tab target, int x, int y, int width) {
        String key = switch (target) {
            case ROUND -> "gui.capturepoint.tab_round";
            case POINTS -> "gui.capturepoint.tab_points";
            case TEAMS -> "gui.capturepoint.tab_teams";
            case SETTINGS -> "gui.capturepoint.tab_settings";
            case SAVED -> "gui.capturepoint.tab_saved";
        };
        return Button.builder(Component.translatable(key), b -> minecraft.gui.setScreen(new CapturePointAdminScreen(target)))
                .bounds(x, y, width, 18).build();
    }

    private Button button(String key, int x, int y, int width, Runnable action) {
        return Button.builder(Component.translatable(key), b -> action.run()).bounds(x, y, width, 18).build();
    }

    private EditBox edit(int x, int y, int width, String labelKey, String hintKey) {
        EditBox box = new EditBox(font, x, y, width, 18, Component.translatable(labelKey));
        box.setMaxLength(40);
        box.setHint(Component.translatable(hintKey));
        addRenderableWidget(box);
        return box;
    }

    private boolean validPoint() {
        return pointName != null && !pointName.getValue().trim().isEmpty()
                && pointName.getValue().trim().matches("[A-Za-z0-9_-]+");
    }

    private void withPoint(String action) {
        if (validPoint()) run("cappoint " + action + " " + pointName.getValue().trim());
    }

    private void movePoint() {
        if (!validPoint() || orderIndex == null) return;
        String index = orderIndex.getValue().trim();
        if (index.matches("[1-9][0-9]{0,2}")) {
            run("cappoint point move " + pointName.getValue().trim() + " " + index);
        }
    }

    private void setTime(String type) {
        if (seconds == null) return;
        String value = seconds.getValue().trim();
        if (value.matches("[1-9][0-9]{0,3}") && Integer.parseInt(value) <= 3600) {
            run("cappoint time " + type + " " + value);
        }
    }

    private void assignTeam(String team) {
        if (playerName != null && playerName.getValue().trim().matches("[A-Za-z0-9_]{1,16}")) {
            run("cappoint team set " + playerName.getValue().trim() + " " + team);
        }
    }

    private void withRound(String action) {
        if (roundName != null && roundName.getValue().trim().matches("[A-Za-z0-9_-]{1,40}")) {
            run("cappoint " + action + " " + roundName.getValue().trim());
        }
    }

    private void run(String command) {
        CapturePointClient.command(command);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        extractBackground(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, 15, 0xFFFFFFFF);
        graphics.centeredText(font, Component.translatable("gui.capturepoint.subtitle"), width / 2, 29, 0xFFAAB7C4);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
