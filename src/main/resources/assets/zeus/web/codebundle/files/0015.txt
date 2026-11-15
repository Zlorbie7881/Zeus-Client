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

    private static void doctor() {
        int broken = 0;
        for (Module m : Zeus.all()) {
            if (m.error == null || m.error.isBlank()) continue;
            broken++;
            if (broken <= 6) say(m.name + ": " + m.error);
        }
        say(broken == 0 ? "Every module is running clean." : broken + " module(s) reported an error. The rest are fine.");
        java.util.List<com.zlorbie788.zeus.core.Prof.Row> rows = com.zlorbie788.zeus.core.Prof.report();
        if (rows.isEmpty()) {
            say("No cost numbers yet: profiling turns on by itself when the frame rate drops.");
        } else {
            StringBuilder b = new StringBuilder("Heaviest per frame: ");
            for (int i = 0; i < Math.min(3, rows.size()); i++) {
                if (i > 0) b.append(", ");
                b.append(rows.get(i).module().name).append(' ').append(String.format(java.util.Locale.ROOT, "%.1f%%", rows.get(i).percent()));
            }
            say(b.toString());
        }
        int on = 0;
        for (Module m : Zeus.all()) if (m.enabled()) on++;
        say(Zeus.all().size() + " modules loaded, " + on + " switched on.");
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> ahTree() {
        return literal("ah")
                .then(literal("on").executes(c -> {
                    say(com.zlorbie788.zeus.mods.AhSniper.arm(true));
                    return 1;
                }))
                .then(literal("off").executes(c -> {
                    say(com.zlorbie788.zeus.mods.AhSniper.arm(false));
                    return 1;
                }))
                .then(literal("rules").then(argument("text", StringArgumentType.greedyString()).executes(c -> {
                    say(com.zlorbie788.zeus.mods.AhSniper.rules(StringArgumentType.getString(c, "text")));
                    return 1;
                })))
                .then(literal("status").executes(c -> {
                    say(com.zlorbie788.zeus.mods.AhSniper.status());
                    return 1;
                }));
    }

    private static final SuggestionProvider<FabricClientCommandSource> AI_PROVIDERS = (ctx, builder) -> {
        String start = builder.getRemainingLowerCase();
        for (com.zlorbie788.zeus.mods.AiPilot.Provider p : com.zlorbie788.zeus.mods.AiPilot.PROVIDERS) {
            if (p.id().startsWith(start)) builder.suggest(p.id());
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<FabricClientCommandSource> AI_TUTORIAL = (ctx, builder) -> {
        for (String s : new String[]{"next", "back", "stop", "restart"}) if (s.startsWith(builder.getRemainingLowerCase())) builder.suggest(s);
        return builder.buildFuture();
    };

    private static LiteralArgumentBuilder<FabricClientCommandSource> aiTree(String name) {
        return literal(name)
                .executes(c -> {
                    aiHelp();
                    return 1;
                })
                .then(literal("tutorial").executes(c -> {
                    com.zlorbie788.zeus.mods.AiTutorial.command("");
                    return 1;
                }).then(argument("step", StringArgumentType.word()).suggests(AI_TUTORIAL).executes(c -> {
                    com.zlorbie788.zeus.mods.AiTutorial.command(StringArgumentType.getString(c, "step"));
                    return 1;
                })))
                .then(literal("setup").executes(c -> {
                    com.zlorbie788.zeus.mods.AiTutorial.command("restart");
                    return 1;
                }))
                .then(literal("help").executes(c -> {
                    aiHelp();
                    return 1;
                }))
                .then(literal("key")
                        .executes(c -> {
                            say(com.zlorbie788.zeus.mods.AiPilot.hasSecret()
                                    ? "A key is saved. Replace it with /zeus ai key <key>, or remove it with /zeus ai key clear."
                                    : "No key saved. Paste one with /zeus ai key <key>, or follow /zeus ai tutorial.");
                            return 1;
                        })
                        .then(literal("clear").executes(c -> {
                            com.zlorbie788.zeus.mods.AiPilot.key("");
                            say("The AI key was removed from this install.");
                            return 1;
                        }))
                        .then(argument("token", StringArgumentType.greedyString()).executes(c -> {
                            com.zlorbie788.zeus.mods.AiPilot.key(StringArgumentType.getString(c, "token"));
                            return 1;
                        })))
                .then(literal("test").executes(c -> {
                    say(com.zlorbie788.zeus.mods.AiPilot.test());
                    return 1;
                }))
                .then(literal("provider")
                        .executes(c -> {
                            com.zlorbie788.zeus.mods.AiPilot.Provider p = com.zlorbie788.zeus.mods.AiPilot.currentProvider();
                            StringBuilder all = new StringBuilder();
                            for (com.zlorbie788.zeus.mods.AiPilot.Provider each : com.zlorbie788.zeus.mods.AiPilot.PROVIDERS) {
                                all.append(all.length() == 0 ? "" : ", ").append(each.id());
                            }
                            say("Provider: " + (p == null ? "custom (" + com.zlorbie788.zeus.mods.AiPilot.endpointUrl() + ")" : p.label())
                                    + ". Switch with /zeus ai provider <" + all + ">");
                            return 1;
                        })
                        .then(argument("id", StringArgumentType.word()).suggests(AI_PROVIDERS).executes(c -> {
                            say(com.zlorbie788.zeus.mods.AiPilot.useProvider(StringArgumentType.getString(c, "id")));
                            return 1;
                        })))
                .then(literal("models").executes(c -> {
                    say(com.zlorbie788.zeus.mods.AiPilot.models());
                    return 1;
                }))
                .then(literal("model")
                        .executes(c -> {
                            say(com.zlorbie788.zeus.mods.AiPilot.setModel(""));
                            return 1;
                        })
                        .then(argument("name", StringArgumentType.greedyString()).executes(c -> {
                            say(com.zlorbie788.zeus.mods.AiPilot.setModel(StringArgumentType.getString(c, "name")));
                            return 1;
                        })))
                .then(literal("endpoint")
                        .executes(c -> {
                            say(com.zlorbie788.zeus.mods.AiPilot.setEndpoint(""));
                            return 1;
                        })
                        .then(argument("url", StringArgumentType.greedyString()).executes(c -> {
                            say(com.zlorbie788.zeus.mods.AiPilot.setEndpoint(StringArgumentType.getString(c, "url")));
                            return 1;
                        })))
                .then(literal("undo").executes(c -> {
                    com.zlorbie788.zeus.mods.AiPilot.undo();
                    return 1;
                }).then(literal("all").executes(c -> {
                    com.zlorbie788.zeus.mods.AiPilot.undo(0);
                    return 1;
                })).then(argument("count", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 50)).executes(c -> {
                    com.zlorbie788.zeus.mods.AiPilot.undo(com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "count"));
                    return 1;
                })))
                .then(literal("plan").executes(c -> {
                    for (String line : com.zlorbie788.zeus.mods.AiPilot.planLines()) say(line);
                    return 1;
                }))
                .then(literal("reset").executes(c -> {
                    com.zlorbie788.zeus.mods.AiPilot.reset();
                    say("Zeus AI Functions cleared its queue and memory.");
                    return 1;
                }))
                .then(literal("status").executes(c -> {
                    com.zlorbie788.zeus.mods.AiPilot pilot = com.zlorbie788.zeus.mods.AiPilot.find();
                    if (pilot == null) {
                        say("Zeus AI Functions is missing.");
                        return 1;
                    }
                    for (String line : com.zlorbie788.zeus.mods.AiPilot.statusLines()) say(line);
                    return 1;
                }))
                .then(argument("instruction", StringArgumentType.greedyString()).executes(c -> {
                    say(com.zlorbie788.zeus.mods.AiPilot.ask(StringArgumentType.getString(c, "instruction")));
                    return 1;
                }));
    }

    private static void aiHelp() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui == null) return;
        say("Zeus AI: type /zeus ai <what you want>, e.g. /zeus ai turn on sprint, /zeus ai mine 10 iron then come back");
        String[][] rows = {
                {"/zeus ai tutorial", "step-by-step setup"},
                {"/zeus ai test", "check the key, endpoint and model"},
                {"/zeus ai provider <id>", "groq, gemini, openrouter, openai, deepseek, mistral, ollama, lmstudio"},
                {"/zeus ai models", "list the provider's models, click one to use it"},
                {"/zeus ai model <name>", "change the model"},
                {"/zeus ai again", "repeat the last request"},
                {"/zeus ai stop / undo [n|all] / plan / status", "stay in control"},
        };
        for (String[] row : rows) {
            mc.gui.getChat().addMessage(Component.literal("  " + row[0]).withStyle(s -> s.withColor(ChatFormatting.AQUA)
                            .withClickEvent(new ClickEvent.SuggestCommand(row[0].replaceAll(" <.*|/ .*| /.*", "") + " ")))
                    .append(Component.literal("  " + row[1]).withStyle(ChatFormatting.GRAY)));
        }
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
                .then(literal("seed")
                        .then(literal("test").executes(c -> {
                            say(com.zlorbie788.zeus.mods.SeedEsp.testNow());
                            return 1;
                        }))
                        .executes(c -> {
                            say(com.zlorbie788.zeus.mods.SeedEsp.statusText());
                            return 1;
                        })
                        .then(argument("seed", StringArgumentType.greedyString()).executes(c -> {
                    say(com.zlorbie788.zeus.mods.SeedEsp.useSeed(StringArgumentType.getString(c, "seed")));
                    return 1;
                })))
                .then(literal("doctor").executes(c -> {
                    doctor();
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
                .then(aiTree("ai"))
                .then(aiTree("Ai"))
                .then(ahTree())
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
