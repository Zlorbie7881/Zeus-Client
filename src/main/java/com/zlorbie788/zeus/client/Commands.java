package com.zlorbie788.zeus.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Zeus;
import com.zlorbie788.zeus.mods.Extras;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;

import java.util.Locale;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public final class Commands {
    private Commands() {}

    public static void init() {
        ClientCommandRegistrationCallback.EVENT.register(Commands::register);
    }

    private static final SuggestionProvider<FabricClientCommandSource> MODULES = (ctx, builder) -> {
        String start = builder.getRemainingLowerCase();
        for (Module m : Zeus.all()) {
            if (m.toggleable() && m.id.startsWith(start)) builder.suggest(m.id);
        }
        return builder.buildFuture();
    };

    private static void say(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui == null) return;
        mc.gui.getChat().addMessage(Component.literal("[Zeus] ").withStyle(ChatFormatting.AQUA).append(Component.literal(text).withStyle(ChatFormatting.WHITE)));
    }

    private static String valueOf(com.zlorbie788.zeus.core.Setting s) {
        if (s instanceof com.zlorbie788.zeus.core.Setting.Bool b) return b.on() ? "on" : "off";
        if (s instanceof com.zlorbie788.zeus.core.Setting.Num n) return n.text();
        if (s instanceof com.zlorbie788.zeus.core.Setting.Choice c) return c.value();
        if (s instanceof com.zlorbie788.zeus.core.Setting.Pick p) return p.label();
        if (s instanceof com.zlorbie788.zeus.core.Setting.Text t) return t.get().isEmpty() ? "(empty)" : t.get();
        if (s instanceof com.zlorbie788.zeus.core.Setting.Info i) {
            java.util.List<String> lines = i.lines();
            return lines.isEmpty() ? null : String.join(" · ", lines.subList(0, Math.min(2, lines.size())));
        }
        return null;
    }

    private static Module find(String id) {
        Module m = Zeus.byName(id);
        if (m == null || !m.toggleable()) {
            say("There is no module called \"" + id + "\". Type /zeus list to see them.");
            return null;
        }
        return m;
    }

    private static int addWaypoint(double x, double y, double z, String name) {
        Extras.Waypoints waypoints = Zeus.get(Extras.Waypoints.class);
        if (waypoints == null || Minecraft.getInstance().level == null) {
            say("Join a world first, then add the waypoint.");
            return 0;
        }
        boolean added = waypoints.addAt(name == null || name.isBlank() ? "Custom waypoint" : name, x, y, z);
        say(added ? "Waypoint added at " + Math.round(x) + " " + Math.round(y) + " " + Math.round(z) + "." : "A waypoint is already saved nearby.");
        return added ? 1 : 0;
    }

    private static void register(CommandDispatcher<FabricClientCommandSource> d, CommandBuildContext ctx) {
        var waypointZ = argument("z", DoubleArgumentType.doubleArg(-30_000_000, 30_000_000))
                .executes(c -> addWaypoint(c.getArgument("x", Double.class), c.getArgument("y", Double.class), c.getArgument("z", Double.class), "Custom waypoint"))
                .then(argument("name", StringArgumentType.greedyString()).executes(c -> addWaypoint(
                        c.getArgument("x", Double.class), c.getArgument("y", Double.class), c.getArgument("z", Double.class),
                        StringArgumentType.getString(c, "name"))));
        var waypointY = argument("y", DoubleArgumentType.doubleArg(-2048, 4096)).then(waypointZ);
        var waypointX = argument("x", DoubleArgumentType.doubleArg(-30_000_000, 30_000_000)).then(waypointY);
        var waypointAdd = literal("add").then(waypointX);
        var waypoint = literal("waypoint").then(waypointAdd);
        LiteralArgumentBuilder<FabricClientCommandSource> root = literal("zeus")
                .then(waypoint)
                .executes(c -> {
                    if (Zeus.destroyed()) {
                        Zeus.revive();
                        say("Zeus is back. Every module can be switched on again.");
                        return 1;
                    }
                    ZeusClient.openLater();
                    return 1;
                })
                .then(literal("open").executes(c -> {
                    ZeusClient.openLater();
                    return 1;
                }))
                .then(literal("keybinds").executes(c -> {
                    ZeusClient.openKeybindsLater();
                    return 1;
                }))
                .then(literal("hud").executes(c -> {
                    ZeusClient.openHudEditorLater();
                    return 1;
                }))
                .then(literal("website").executes(c -> {
                    Module site = Zeus.get("webdashboard");
                    if (site == null) {
                        say("The Web Dashboard module is missing.");
                    } else if (!site.enabled()) {
                        site.setEnabled(true);
                        if (!com.zlorbie788.zeus.client.web.LocalControlServer.running()) say("The website could not start. See the Web Dashboard settings.");
                    } else {
                        com.zlorbie788.zeus.mods.WebDashboard.announceNow();
                    }
                    return 1;
                }).then(literal("stop").executes(c -> {
                    Module site = Zeus.get("webdashboard");
                    if (site != null) site.setEnabled(false);
                    com.zlorbie788.zeus.client.web.LocalControlServer.stop();
                    say("The website is stopped.");
                    return 1;
                })).then(literal("status").executes(c -> {
                    String url = com.zlorbie788.zeus.client.web.LocalControlServer.url();
                    say(url != null ? "The website is running at " + url : "The website is stopped.");
                    return 1;
                })))
                .then(literal("panic").executes(c -> {
                    Zeus.panic(true);
                    say("Everything is off.");
                    return 1;
                }))
                .then(literal("list").executes(c -> {
                    StringBuilder on = new StringBuilder();
                    for (Module m : Zeus.all()) if (m.toggleable() && m.enabled()) on.append(on.length() == 0 ? "" : ", ").append(m.id);
                    say("On: " + (on.length() == 0 ? "nothing" : on));
                    StringBuilder all = new StringBuilder();
                    for (Module m : Zeus.all()) if (m.toggleable()) all.append(all.length() == 0 ? "" : ", ").append(m.id);
                    say("All: " + all);
                    return 1;
                }))
                .then(literal("toggle").then(argument("module", StringArgumentType.word()).suggests(MODULES).executes(c -> {
                    Module m = find(StringArgumentType.getString(c, "module"));
                    if (m != null) {
                        m.toggle();
                        say(m.name + " is " + (m.enabled() ? "on" : "off") + ".");
                    }
                    return 1;
                })))
                .then(literal("on").then(argument("module", StringArgumentType.word()).suggests(MODULES).executes(c -> {
                    Module m = find(StringArgumentType.getString(c, "module"));
                    if (m != null) {
                        m.setEnabled(true);
                        say(m.name + " is " + (m.enabled() ? "on" : "off") + ".");
                    }
                    return 1;
                })))
                .then(literal("off").then(argument("module", StringArgumentType.word()).suggests(MODULES).executes(c -> {
                    Module m = find(StringArgumentType.getString(c, "module"));
                    if (m != null) {
                        m.setEnabled(false);
                        say(m.name + " is off.");
                    }
                    return 1;
                })))
                .then(literal("find").then(argument("text", StringArgumentType.greedyString()).executes(c -> {
                    String q = StringArgumentType.getString(c, "text").trim().toLowerCase(Locale.ROOT);
                    int n = 0;
                    for (Module m : Zeus.all()) {
                        String hay = (m.id + " " + m.name + " " + m.desc + " " + String.join(" ", m.aliases())).toLowerCase(Locale.ROOT);
                        if (!hay.contains(q)) continue;
                        say(m.name + " (" + m.id + ") · " + (m.enabled() ? "on" : "off") + " · " + m.category.name().toLowerCase(Locale.ROOT));
                        if (++n >= 8) break;
                    }
                    if (n == 0) say("Nothing matches \"" + q + "\".");
                    return 1;
                })))
                .then(literal("settings").then(argument("module", StringArgumentType.word()).suggests(MODULES).executes(c -> {
                    Module m = Zeus.byName(StringArgumentType.getString(c, "module"));
                    if (m == null) {
                        say("There is no module called \"" + StringArgumentType.getString(c, "module") + "\".");
                        return 0;
                    }
                    say(m.name + " · " + (m.enabled() ? "on" : "off") + " — " + m.desc);
                    int n = 0;
                    for (com.zlorbie788.zeus.core.Setting s : m.settings()) {
                        if (!s.visible()) continue;
                        String v = valueOf(s);
                        if (v == null) continue;
                        say("  " + s.name + ": " + v);
                        if (++n >= 14) {
                            say("  … the panel shows the rest.");
                            break;
                        }
                    }
                    return 1;
                })));
        d.register(root);
    }
}
