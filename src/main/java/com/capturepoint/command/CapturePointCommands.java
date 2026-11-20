package com.capturepoint.command;

import com.capturepoint.core.CapturePointManager;
import com.capturepoint.core.CapturePointManager.PointDef;
import com.capturepoint.core.CapturePointManager.Team;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionCheck;
import net.minecraft.server.permissions.PermissionLevel;
import java.util.function.Predicate;

public final class CapturePointCommands {
    private static final Predicate<CommandSourceStack> ADMIN_PERMISSION = Commands.hasPermission(
            new PermissionCheck.Require(new Permission.HasCommandLevel(PermissionLevel.ADMINS)));

    private CapturePointCommands() { }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("cappoint")
            .then(Commands.literal("start").requires(ADMIN_PERMISSION).executes(ctx -> {
                boolean ok = CapturePointManager.start();
                ctx.getSource().sendSuccess(() -> Component.literal(ok ? "Capture Point round started." : "Cannot start: create complete points and set a valid center."), false);
                return ok ? 1 : 0;
            }))
            .then(Commands.literal("stop").requires(ADMIN_PERMISSION).executes(ctx -> { CapturePointManager.stop(); ctx.getSource().sendSuccess(() -> Component.literal("Round stopped."), false); return 1; }))
            .then(Commands.literal("pause").requires(ADMIN_PERMISSION).executes(ctx -> { CapturePointManager.pause(); ctx.getSource().sendSuccess(() -> Component.literal("Round paused."), false); return 1; }))
            .then(Commands.literal("resume").requires(ADMIN_PERMISSION).executes(ctx -> { CapturePointManager.resume(); ctx.getSource().sendSuccess(() -> Component.literal("Round resumed."), false); return 1; }))
            .then(Commands.literal("status").requires(ADMIN_PERMISSION).executes(ctx -> {
                var r=CapturePointManager.getRound();
                String active=CapturePointManager.getActiveIndex()>=0 && CapturePointManager.getActiveIndex()<r.points.size()?r.points.get(CapturePointManager.getActiveIndex()).name:"—";
                ctx.getSource().sendSuccess(() -> Component.literal("Round: "+r.name+" | Points: "+r.points.size()+" | Center: "+(r.centerIndex+1)+" | Active: "+active+" | Running: "+CapturePointManager.isRunning()+" | Paused: "+CapturePointManager.isPaused()), false);
                return 1;
            }))
            .then(Commands.literal("point").requires(ADMIN_PERMISSION)
                .then(Commands.literal("create").then(Commands.argument("name", StringArgumentType.word()).executes(ctx -> {
                    ServerPlayer p=ctx.getSource().getPlayerOrException(); String name=StringArgumentType.getString(ctx,"name");
                    PointDef created=CapturePointManager.createPoint(name,p);
                    if (created == null) { ctx.getSource().sendFailure(Component.literal("Point name already exists, or the round is running.")); return 0; }
                    ctx.getSource().sendSuccess(() -> Component.literal("Created point '"+name+"'. Both corners initially use your current block position."), false); return 1;
                })))
                .then(Commands.literal("pos1").then(Commands.argument("name", StringArgumentType.word()).executes(ctx -> setCorner(ctx.getSource(), StringArgumentType.getString(ctx,"name"), true))))
                .then(Commands.literal("pos2").then(Commands.argument("name", StringArgumentType.word()).executes(ctx -> setCorner(ctx.getSource(), StringArgumentType.getString(ctx,"name"), false))))
                .then(Commands.literal("center").then(Commands.argument("name", StringArgumentType.word()).executes(ctx -> {
                    String name=StringArgumentType.getString(ctx,"name"); boolean ok=CapturePointManager.setCenter(name);
                    ctx.getSource().sendSuccess(() -> Component.literal(ok?"Central point set to '"+name+"'.":"Point not found."), false); return ok?1:0;
                })))
                .then(Commands.literal("move").then(Commands.argument("name", StringArgumentType.word()).then(Commands.argument("index", IntegerArgumentType.integer(1)).executes(ctx -> {
                    String name=StringArgumentType.getString(ctx,"name"); int index=IntegerArgumentType.getInteger(ctx,"index"); boolean ok=CapturePointManager.movePoint(name,index);
                    ctx.getSource().sendSuccess(() -> Component.literal(ok?"Point order updated.":"Invalid point or index."), false); return ok?1:0;
                }))))
                .then(Commands.literal("remove").then(Commands.argument("name", StringArgumentType.word()).executes(ctx -> {
                    String name=StringArgumentType.getString(ctx,"name"); boolean ok=CapturePointManager.removePoint(name);
                    ctx.getSource().sendSuccess(() -> Component.literal(ok?"Point removed.":"Point not found, or round is running."), false); return ok?1:0;
                })))
            )
            .then(Commands.literal("team").requires(ADMIN_PERMISSION)
                .then(Commands.literal("random").executes(ctx -> { CapturePointManager.randomBalance(); ctx.getSource().sendSuccess(() -> Component.literal("Players randomly balanced between BLUE and RED."), false); return 1; }))
                .then(Commands.literal("set").then(Commands.argument("player", StringArgumentType.word()).then(Commands.argument("team", StringArgumentType.word()).executes(ctx -> {
                    String playerName=StringArgumentType.getString(ctx,"player"); String teamName=StringArgumentType.getString(ctx,"team");
                    ServerPlayer target=ctx.getSource().getServer().getPlayerList().getPlayerByName(playerName);
                    if (target==null) { ctx.getSource().sendFailure(Component.literal("Player not found or offline.")); return 0; }
                    if (teamName.equalsIgnoreCase("NONE") || teamName.equalsIgnoreCase("UNASSIGNED")) {
                        CapturePointManager.unassign(target);
                        ctx.getSource().sendSuccess(() -> Component.literal(playerName + " removed from teams."), false);
                        return 1;
                    }
                    Team team; try { team=Team.valueOf(teamName.toUpperCase()); } catch (IllegalArgumentException e) { ctx.getSource().sendFailure(Component.literal("Team must be BLUE, RED, or NONE.")); return 0; }
                    CapturePointManager.assign(target,team); ctx.getSource().sendSuccess(() -> Component.literal(playerName+" assigned to "+team.name()+"."), false); return 1;
                }))))
                .then(Commands.literal("list").executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal("Team roster:").withStyle(ChatFormatting.GOLD), false);
                    for (ServerPlayer player : ctx.getSource().getServer().getPlayerList().getPlayers()) {
                        Team team=CapturePointManager.getTeam(player.getUUID());
                        ctx.getSource().sendSuccess(() -> Component.literal(player.getName().getString()+": "+(team==null?"UNASSIGNED":team.name()).toString()).withStyle(team==null?ChatFormatting.GRAY:team.formatting()), false);
                    }
                    return 1;
                }))
            )
            .then(Commands.literal("time").requires(ADMIN_PERMISSION)
                .then(Commands.literal("capture").then(Commands.argument("seconds", IntegerArgumentType.integer(1,3600)).executes(ctx -> {
                    int v=IntegerArgumentType.getInteger(ctx,"seconds"); CapturePointManager.setTimes(v,CapturePointManager.getRound().holdSeconds);
                    ctx.getSource().sendSuccess(() -> Component.literal("Capture time: "+v+" seconds."), false); return 1;
                })))
                .then(Commands.literal("hold").then(Commands.argument("seconds", IntegerArgumentType.integer(1,3600)).executes(ctx -> {
                    int v=IntegerArgumentType.getInteger(ctx,"seconds"); CapturePointManager.setTimes(CapturePointManager.getRound().captureSeconds,v);
                    ctx.getSource().sendSuccess(() -> Component.literal("Hold time: "+v+" seconds."), false); return 1;
                })))
            )
            .then(Commands.literal("save").requires(ADMIN_PERMISSION).then(Commands.argument("name", StringArgumentType.word()).executes(ctx -> {
                String name=StringArgumentType.getString(ctx,"name"); boolean ok=CapturePointManager.saveRound(name);
                ctx.getSource().sendSuccess(() -> Component.literal(ok?"Saved round '"+name+"'.":"Could not save round."), false); return ok?1:0;
            })))
            .then(Commands.literal("load").requires(ADMIN_PERMISSION).then(Commands.argument("name", StringArgumentType.word()).executes(ctx -> {
                String name=StringArgumentType.getString(ctx,"name"); boolean ok=CapturePointManager.loadRound(name);
                ctx.getSource().sendSuccess(() -> Component.literal(ok?"Loaded round '"+name+"'.":"Could not load round. Check name and stop the match first."), false); return ok?1:0;
            })))
            .then(Commands.literal("rounds").requires(ADMIN_PERMISSION).executes(ctx -> {
                var names = CapturePointManager.listSavedRounds();
                if (names.isEmpty()) {
                    ctx.getSource().sendSuccess(() -> Component.literal("No saved rounds."), false);
                } else {
                    ctx.getSource().sendSuccess(() -> Component.literal("Saved rounds: " + String.join(", ", names)), false);
                }
                return names.size();
            }))
            .then(Commands.literal("list").requires(ADMIN_PERMISSION).executes(ctx -> {
                var r=CapturePointManager.getRound();
                ctx.getSource().sendSuccess(() -> Component.literal("Points (center is #"+(r.centerIndex+1)+"):").withStyle(ChatFormatting.GOLD), false);
                for(int i=0;i<r.points.size();i++) {
                    final int n=i+1; PointDef p=r.points.get(i);
                    ctx.getSource().sendSuccess(() -> Component.literal(n+". "+p.name+" | "+(p.isComplete()?"bounds set":"bounds incomplete")+(n-1==r.centerIndex?" | CENTRAL":"")+((p.owner==null || p.owner.isBlank())?"":" | owner: "+p.owner)), false);
                }
                return 1;
            }))
        );
    }

    private static int setCorner(CommandSourceStack source, String name, boolean first) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        boolean ok=CapturePointManager.setCorner(name,source.getPlayerOrException(),first);
        source.sendSuccess(() -> Component.literal(ok?(first?"pos1":"pos2")+" updated for '"+name+"'.":"Point not found."), false);
        return ok?1:0;
    }
}
