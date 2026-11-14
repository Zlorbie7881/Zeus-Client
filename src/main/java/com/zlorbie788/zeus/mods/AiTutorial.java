package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Zeus;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.net.URI;
import java.util.Locale;

/**
 * /zeus ai tutorial: a step-by-step setup walkthrough in chat.
 *
 * Every step prints a short card with clickable buttons. Buttons that act inside the client carry
 * a private marker in a copy-to-clipboard click event, which {@link #click} intercepts before
 * vanilla handles it, so they work on every server and never send anything to the server.
 * The walkthrough moves on by itself when the player finishes a step another way, for example
 * by typing /zeus ai key by hand.
 */
public final class AiTutorial {
    private AiTutorial() {}

    private static final String MARK = "zeus-ai-tutorial:";
    private static final int WELCOME = 0, ENABLE = 1, PROVIDER = 2, GET_KEY = 3, SAVE_KEY = 4, TEST = 5, TRY = 6, SAFETY = 7;
    private static final int LAST = SAFETY;
    private static final String[] TITLES = {"Welcome", "Switch the AI on", "Pick a provider", "Get an API key",
            "Save the key", "Test the connection", "Try it out", "Stay in control"};

    private static volatile int step = -1;
    private static volatile String chosen = "";

    public static boolean active() {
        return step >= 0;
    }

    /** Entry point for /zeus ai tutorial [next|back|stop|restart|N]. */
    public static void command(String arg) {
        String a = arg == null ? "" : arg.trim().toLowerCase(Locale.ROOT);
        switch (a) {
            case "", "start", "restart" -> {
                if (a.isEmpty() && active()) {
                    show();
                    return;
                }
                chosen = "";
                go(WELCOME);
            }
            case "next", "continue" -> go(active() ? step + 1 : WELCOME);
            case "back", "previous" -> go(Math.max(0, step - 1));
            case "stop", "exit", "quit", "close", "end" -> stop();
            default -> {
                try {
                    go(Math.max(0, Math.min(LAST, Integer.parseInt(a) - 1)));
                } catch (NumberFormatException e) {
                    say(Component.literal("Use /zeus ai tutorial, then next, back, stop or a step number 1-" + (LAST + 1) + ".").withStyle(ChatFormatting.GRAY));
                }
            }
        }
    }

    private static void stop() {
        step = -1;
        say(Component.literal("Tutorial closed. Reopen it any time with ").withStyle(ChatFormatting.GRAY)
                .append(button("/zeus ai tutorial", ChatFormatting.AQUA, "restart", "Start the setup walkthrough again")));
    }

    private static void go(int next) {
        if (next > LAST) {
            finish();
            return;
        }
        step = next;
        show();
    }

    private static void finish() {
        step = -1;
        rule();
        say(Component.literal("  You're all set! ").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                .append(Component.literal("Type ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal("/zeus ai <anything>").withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" whenever you need it.").withStyle(ChatFormatting.WHITE)));
        rule();
    }

    // ---------------------------------------------------------------- the steps

    private static void show() {
        int s = step;
        if (s < 0) return;
        AiPilot pilot = AiPilot.find();
        if (pilot == null) {
            say(Component.literal("Zeus AI Functions is missing from this build.").withStyle(ChatFormatting.RED));
            step = -1;
            return;
        }
        header(s);
        switch (s) {
            case WELCOME -> {
                text("Zeus AI lets you type what you want in plain words: ");
                say(Component.literal("    \"turn on sprint\", \"set killaura range to 4\", \"mine 10 iron then come back\"").withStyle(ChatFormatting.AQUA));
                text("It runs on an AI provider using your own API key. Several providers have free keys, and it can also run fully offline on your PC.");
                text("This takes about two minutes. Your key is stored only in your Zeus config on this computer.");
                buttons(button("[ Start ]", ChatFormatting.GREEN, "next", "Go to step 1"), close());
            }
            case ENABLE -> {
                Module module = pilot;
                if (module.enabled()) {
                    good("Zeus AI Functions is already on.");
                    buttons(next(), back(), close());
                } else {
                    text("The AI lives in the module Zeus AI Functions. It has to be on to answer.");
                    buttons(button("[ Turn it on ]", ChatFormatting.GREEN, "enable", "Switch Zeus AI Functions on"), back(), close());
                }
            }
            case PROVIDER -> {
                AiPilot.Provider now = AiPilot.currentProvider();
                text("Pick where the AI runs. Click one" + (now != null ? " (now: " + now.label() + ")" : "") + ":");
                for (AiPilot.Provider p : AiPilot.PROVIDERS) {
                    String tag = p.keyless() ? "runs on your PC, no key, no internet" : p.free() ? "free key" : "paid, pay as you go";
                    if (p.id().equals("groq")) tag = "free key, very fast, recommended";
                    say(Component.literal("   ")
                            .append(button("[" + p.label() + "]", p.id().equals("groq") ? ChatFormatting.GREEN : ChatFormatting.AQUA,
                                    "provider:" + p.id(), "Use " + p.label() + " with the model " + p.model()))
                            .append(Component.literal("  " + tag).withStyle(ChatFormatting.GRAY)));
                }
                text("Already have a key from somewhere else? Skip ahead, the key's prefix picks the provider for you.");
                buttons(button("[ Skip ]", ChatFormatting.GRAY, "next", "Keep the current provider"), back(), close());
            }
            case GET_KEY -> {
                AiPilot.Provider p = provider();
                if (p == null) {
                    text("You are using a custom endpoint: " + AiPilot.endpointUrl());
                    text("Get a key from that provider's website, then continue.");
                    buttons(next(), back(), close());
                } else if (p.keyless()) {
                    text(p.label() + " runs the model on your own computer, so there is no key.");
                    if (p.id().equals("ollama")) {
                        text("1. Install Ollama from the link below and start it.");
                        say(Component.literal("    2. In a terminal run: ").withStyle(ChatFormatting.WHITE)
                                .append(Component.literal("ollama pull " + p.model()).withStyle(ChatFormatting.YELLOW)));
                        text("3. Leave Ollama running while you play. A gaming PC with 8 GB+ of video memory works best.");
                    } else {
                        text("1. Install LM Studio, download a model and press Start Server on its Developer tab.");
                        text("2. Use /zeus ai model <name> if the server shows a different model name.");
                    }
                    buttons(link("[ Download ]", p.keys()), button("[ Next: test it ]", ChatFormatting.GREEN, "goto:" + TEST, "Skip the key steps"), back(), close());
                } else {
                    text("1. Open the key page below and sign in (a free account is fine" + (p.free() ? "" : "; this provider needs credit") + ").");
                    text("2. Create a new API key and copy it.");
                    text("3. Come back here and press Next.");
                    say(Component.literal("    Never share a key or paste it in public chat. Anyone with it can use your account.").withStyle(ChatFormatting.GOLD));
                    buttons(link("[ Open " + p.label() + " keys ]", p.keys()), next(), back(), close());
                }
            }
            case SAVE_KEY -> {
                if (AiPilot.hasSecret()) {
                    good("A key is saved. Paste a new one any time with /zeus ai key <key>.");
                    buttons(next(), button("[ Replace key ]", ChatFormatting.YELLOW, null, "/zeus ai key ", "Type /zeus ai key and paste a new key"), back(), close());
                } else if (AiPilot.hasKey()) {
                    good("No key needed for " + AiPilot.endpointUrl() + ".");
                    buttons(next(), back(), close());
                } else {
                    text("Click the button, paste your key (Ctrl+V, or Cmd+V on Mac) and press Enter:");
                    say(Component.literal("    ").append(button("[ /zeus ai key ... ]", ChatFormatting.GREEN, null, "/zeus ai key ",
                            "Puts /zeus ai key in the chat box, ready for your key")));
                    text("The key is saved in your Zeus config and shown masked from now on. The walkthrough continues when it is saved.");
                    buttons(back(), close());
                }
            }
            case TEST -> {
                AiPilot.Provider p = AiPilot.currentProvider();
                text("Provider: " + (p == null ? "custom" : p.label()) + ", model " + AiPilot.modelName() + ".");
                if (AiPilot.testPassed()) {
                    good("Connection works: " + AiPilot.testResult() + ".");
                    buttons(next(), button("[ Test again ]", ChatFormatting.AQUA, "test", "Send one tiny request"), back(), close());
                } else {
                    text("Send one tiny request to check the key, the endpoint and the model together:");
                    buttons(button("[ Run test ]", ChatFormatting.GREEN, "test", "Sends one tiny request to your provider"), back(), close());
                }
            }
            case TRY -> {
                text("Click any example to send it, or type your own after /zeus ai:");
                example("on sprint", "Switch a module on");
                example("what does esp do?", "Ask about a module");
                example("set killaura range to 4", "Change a setting");
                example("preset pvp", "Switch on a group of modules");
                text("In a survival world it can work for you too (say stop to end a job):");
                example("chop a tree", "Walks to the nearest tree and cuts it down");
                example("mine 10 stone", "Mines and counts real drops");
                example("come here", "Walks to you");
                text("Shortcut: press J in game to open chat with /zeus ai already typed (change the key in Controls).");
                buttons(next(), back(), close());
            }
            case SAFETY -> {
                text("A few commands keep you in charge:");
                tip("/zeus ai stop", "Ends whatever job is running, right away.");
                tip("/zeus ai undo", "Puts back the last module or setting the AI changed.");
                tip("/zeus ai plan", "Shows what it planned and what each step did.");
                tip("/zeus ai status", "Key, model, requests used this hour and the last error.");
                tip("/zeus ai test", "Checks the connection again.");
                text("In the Zeus AI Functions settings: Preview only shows what it would do without doing it, and Requests per hour caps how much of your key it can use.");
                buttons(button("[ Finish ]", ChatFormatting.GREEN, "next", "Close the walkthrough"), back());
            }
            default -> {
            }
        }
    }

    private static AiPilot.Provider provider() {
        AiPilot.Provider picked = chosen.isEmpty() ? null : AiPilot.providerById(chosen);
        AiPilot.Provider now = AiPilot.currentProvider();
        return now != null ? now : picked;
    }

    // ---------------------------------------------------------------- events from the AI module

    /** A key was saved (by the button or by typing the command): move past the key steps. */
    static void onKey() {
        if (step == SAVE_KEY || step == GET_KEY || step == PROVIDER) go(TEST);
    }

    /** A connection test finished. */
    static void onTest(boolean ok) {
        if (step != TEST) {
            if (!ok && !active()) {
                say(Component.literal("[Zeus] ").withStyle(ChatFormatting.AQUA)
                        .append(Component.literal("Need help? ").withStyle(ChatFormatting.WHITE))
                        .append(button("[ Open the setup walkthrough ]", ChatFormatting.GREEN, "restart", "/zeus ai tutorial")));
            }
            return;
        }
        if (ok) {
            go(TRY);
            return;
        }
        String why = AiPilot.testResult().toLowerCase(Locale.ROOT);
        text("How to fix it:");
        if (why.contains("key")) tip("Key", "Copy the key again (the whole thing) and save it with /zeus ai key <key>.");
        if (why.contains("not found") || why.contains("model")) {
            tip("Model", "That model name is not available here. Pick one from the list:");
            AiPilot.models();
        }
        if (why.contains("nothing is answering") || why.contains("local ai")) tip("Server", "Start Ollama or LM Studio and keep it open.");
        if (why.contains("connect")) tip("Network", "Check your internet, VPN or firewall.");
        if (why.contains("rate") || why.contains("quota") || why.contains("credit")) tip("Limits", "Wait a minute, or add credit at your provider.");
        buttons(button("[ Test again ]", ChatFormatting.GREEN, "test", "Send one tiny request"),
                button("[ Change provider ]", ChatFormatting.AQUA, "goto:" + PROVIDER, "Back to step 3"),
                button("[ Re-enter key ]", ChatFormatting.YELLOW, null, "/zeus ai key ", "Type /zeus ai key and paste the key"), close());
    }

    // ---------------------------------------------------------------- clicks

    /** Called for every chat click; returns true when it was one of ours. */
    public static boolean click(ClickEvent event) {
        if (!(event instanceof ClickEvent.CopyToClipboard copy)) return false;
        String value = copy.value();
        if (value == null || !value.startsWith(MARK)) return false;
        String action = value.substring(MARK.length());
        Minecraft.getInstance().execute(() -> handle(action));
        return true;
    }

    private static void handle(String action) {
        AiPilot pilot = AiPilot.find();
        if (action.equals("restart")) {
            command("restart");
        } else if (action.equals("next")) {
            command("next");
        } else if (action.equals("back")) {
            command("back");
        } else if (action.equals("stop")) {
            stop();
        } else if (action.equals("enable")) {
            if (pilot != null && !pilot.enabled()) pilot.setEnabled(true);
            if (pilot != null && pilot.enabled()) go(PROVIDER);
            else say(Component.literal("It could not be switched on. Open the panel with /zeus and check Zeus AI Functions.").withStyle(ChatFormatting.RED));
        } else if (action.startsWith("provider:")) {
            chosen = action.substring(9);
            good(AiPilot.useProvider(chosen));
            AiPilot.Provider p = AiPilot.providerById(chosen);
            go(p != null && p.keyless() ? GET_KEY : AiPilot.hasSecret() ? SAVE_KEY : GET_KEY);
        } else if (action.equals("test")) {
            text(AiPilot.test());
        } else if (action.startsWith("goto:")) {
            go(Integer.parseInt(action.substring(5)));
        } else if (action.startsWith("ask:")) {
            String prompt = action.substring(4);
            say(Component.literal("> /zeus ai " + prompt).withStyle(ChatFormatting.GRAY));
            say(Component.literal("[Zeus] ").withStyle(ChatFormatting.AQUA)
                    .append(Component.literal(AiPilot.ask(prompt)).withStyle(ChatFormatting.WHITE)));
        }
    }

    // ---------------------------------------------------------------- chat helpers

    private static void header(int s) {
        rule();
        say(Component.literal("  Zeus AI setup ").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
                .append(Component.literal("· step " + (s + 1) + "/" + (LAST + 1) + " · ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(TITLES[s]).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD)));
        StringBuilder bar = new StringBuilder("  ");
        for (int i = 0; i <= LAST; i++) bar.append(i < s ? "■ " : i == s ? "▣ " : "□ ");
        say(Component.literal(bar.toString()).withStyle(ChatFormatting.DARK_AQUA));
    }

    private static void rule() {
        say(Component.literal("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━").withStyle(ChatFormatting.DARK_GRAY));
    }

    private static void text(String line) {
        say(Component.literal("  " + line).withStyle(ChatFormatting.WHITE));
    }

    private static void good(String line) {
        say(Component.literal("  ✔ " + line).withStyle(ChatFormatting.GREEN));
    }

    private static void tip(String label, String line) {
        say(Component.literal("   • ").withStyle(ChatFormatting.DARK_AQUA)
                .append(Component.literal(label).withStyle(ChatFormatting.AQUA))
                .append(Component.literal("  " + line).withStyle(ChatFormatting.GRAY)));
    }

    private static void example(String prompt, String tip) {
        say(Component.literal("   ").append(button("[ " + prompt + " ]", ChatFormatting.AQUA, "ask:" + prompt, tip + " (click to send)")));
    }

    private static void buttons(MutableComponent... parts) {
        MutableComponent line = Component.literal("  ");
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) line.append(Component.literal("  "));
            line.append(parts[i]);
        }
        say(line);
    }

    private static MutableComponent next() {
        return button("[ Next ]", ChatFormatting.GREEN, "next", "Go to the next step");
    }

    private static MutableComponent back() {
        return button("[ Back ]", ChatFormatting.GRAY, "back", "Go back one step");
    }

    private static MutableComponent close() {
        return button("[ Stop ]", ChatFormatting.RED, "stop", "Close the walkthrough");
    }

    private static MutableComponent button(String label, ChatFormatting colour, String action, String tip) {
        return button(label, colour, action, null, tip);
    }

    /** A clickable label: runs one of our actions, or (with suggest) puts text in the chat box. */
    private static MutableComponent button(String label, ChatFormatting colour, String action, String suggest, String tip) {
        ClickEvent click = suggest != null ? new ClickEvent.SuggestCommand(suggest) : new ClickEvent.CopyToClipboard(MARK + action);
        return Component.literal(label).withStyle(style -> style.withColor(colour).withBold(true).withClickEvent(click)
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(tip))));
    }

    private static MutableComponent link(String label, String url) {
        return Component.literal(label).withStyle(style -> style.withColor(ChatFormatting.LIGHT_PURPLE).withBold(true).withUnderlined(true)
                .withClickEvent(new ClickEvent.OpenUrl(URI.create(url)))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(url))));
    }

    /** Clickable list of models: click one to put /zeus ai model <id> in the chat box. */
    static void models(java.util.List<String> ids, String current) {
        if (ids.isEmpty()) {
            chat("The provider did not list any chat models.", true);
            return;
        }
        say(Component.literal("[Zeus AI] ").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(ids.size() + " model(s). Click one, then press Enter to use it:").withStyle(ChatFormatting.WHITE)));
        int shown = 0;
        MutableComponent line = Component.literal("  ");
        int width = 0;
        for (String id : ids) {
            if (shown >= 60) break;
            boolean now = id.equalsIgnoreCase(current);
            MutableComponent b = button(now ? "[" + id + " ✔]" : "[" + id + "]", now ? ChatFormatting.GREEN : ChatFormatting.AQUA, null,
                    "/zeus ai model " + id, now ? "The model in use" : "Use " + id);
            if (width > 0 && width + id.length() > 70) {
                say(line);
                line = Component.literal("  ");
                width = 0;
            }
            line.append(b).append(Component.literal(" "));
            width += id.length() + 3;
            shown++;
        }
        if (width > 0) say(line);
        if (ids.size() > shown) say(Component.literal("  … and " + (ids.size() - shown) + " more").withStyle(ChatFormatting.GRAY));
    }

    /** The schematic files as chat lines with build and materials buttons. */
    static void schematics(java.util.List<java.nio.file.Path> files) {
        say(Component.literal("[Zeus AI] ").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(files.size() + " schematic(s), newest first:").withStyle(ChatFormatting.WHITE)));
        for (int i = 0; i < Math.min(12, files.size()); i++) {
            String name = SchematicPlan.display(files.get(i));
            say(Component.literal("  " + name + "  ").withStyle(ChatFormatting.WHITE)
                    .append(button("[Build]", ChatFormatting.GREEN, "ask:build schematic " + name, "Gather the materials and build it in front of you"))
                    .append(Component.literal(" "))
                    .append(button("[Materials]", ChatFormatting.AQUA, "ask:materials for " + name, "List what it needs and what you have")));
        }
    }

    /** A persistent chat line from the AI (toasts fade too fast for real answers). */
    public static void chat(String text, boolean problem) {
        if (text == null || text.isBlank()) return;
        AiPilot.talk(false, text);
        MutableComponent line = Component.literal("[Zeus AI] ").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(text).withStyle(problem ? ChatFormatting.GOLD : ChatFormatting.WHITE));
        if (problem && !active()) {
            line.append(Component.literal("  ")).append(button("[ Fix it ]", ChatFormatting.GREEN, "restart", "Open the setup walkthrough"));
        }
        say(line);
    }

    private static void say(Component component) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui == null) return;
        mc.gui.getChat().addMessage(component);
    }
}
