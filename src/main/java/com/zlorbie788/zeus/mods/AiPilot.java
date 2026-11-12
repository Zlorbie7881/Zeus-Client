package com.zlorbie788.zeus.mods;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Notify;
import com.zlorbie788.zeus.core.Setting;
import com.zlorbie788.zeus.core.Zeus;
import net.minecraft.client.Minecraft;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public final class AiPilot extends Module {
    private static final Gson GSON = new Gson();
    private static final Set<String> BLOCKED = Set.of("discordwebhook", "webhook", "webdashboard", "streamerprivacy");

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Zeus AI");
        thread.setDaemon(true);
        return thread;
    });

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final ConcurrentLinkedQueue<Step> QUEUE = new ConcurrentLinkedQueue<>();
    private static final Deque<JsonObject> HISTORY = new ArrayDeque<>();
    private static final int HISTORY_MAX = 6;
    private static final AtomicLong LAST = new AtomicLong();
    private static final AtomicLong WINDOW = new AtomicLong();
    private static final java.util.concurrent.atomic.AtomicInteger USED = new java.util.concurrent.atomic.AtomicInteger();
    private static final Deque<Undo> UNDO = new ArrayDeque<>();
    private static final int UNDO_MAX = 80;
    private static volatile boolean busy;
    private static volatile String context = "";
    private static volatile String reply = "";
    private static volatile String failure = "";
    private static volatile String lastAction = "";
    private static volatile long answered;
    private static final java.util.List<String> PLAN = new java.util.ArrayList<>();
    private static final java.util.List<String> PLAN_RESULTS = new java.util.ArrayList<>();
    private static volatile String lastPrompt = "";
    private static int planStep;
    private static final java.util.List<String> NOTES = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static final java.util.List<String> RESULTS = new java.util.ArrayList<>();
    private static final int NOTES_MAX = 10;
    private static final java.nio.file.Path MEMORY = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("zeus-ai-memory.json");
    private static volatile boolean memoryLoaded;
    private static volatile boolean noJsonMode;
    private static volatile String followPrompt = "";
    private static volatile long followAt;
    private static volatile int followUps;

    private record Step(String action, String module, String setting, String value) {}

    private record Undo(Module module, Setting setting, boolean wasOn, String oldText, int oldIndex, double oldNum) {}

    private final Setting.Secret token = add(new Setting.Secret("apiKey"));
    private final Setting.Text model = add(new Setting.Text("Model", "The model asked for at the endpoint. Any model the provider serves works.", "gpt-4o-mini", 60));
    private final Setting.Text endpoint = add(new Setting.Text("Endpoint", "An OpenAI-compatible chat completions URL.", "https://api.openai.com/v1/chat/completions", 160));
    private final Setting.Bool toggles = add(new Setting.Bool("Switch modules", "Let the AI switch modules on and off.", true));
    private final Setting.Bool settings = add(new Setting.Bool("Change settings", "Let the AI change numeric, choice, text and switch settings.", true));
    private final Setting.Bool speak = add(new Setting.Bool("Answer in chat", "Show what the AI says as a message.", true));
    private final Setting.Bool preview = add(new Setting.Bool("Preview only", "Show what the AI would change without touching a single module.", false));
    private final Setting.Num perRequest = add(new Setting.Num("Most actions per request", "How many actions one answer may perform.", 6, 1, 20, 1, ""));
    private final Setting.Num temperature = add(new Setting.Num("Creativity", "How much freedom the model gets when reading a request. Low stays predictable.", 0.2, 0, 1, 0.1, ""));
    private final Setting.Num tokens = add(new Setting.Num("Answer length", "The longest answer the model may send. Longer costs more.", 600, 150, 2000, 50, " tokens"));
    private final Setting.Num perHour = add(new Setting.Num("Requests per hour", "A cap so a stuck loop cannot drain your credits.", 40, 5, 240, 5, ""));
    private final Setting.Num wait = add(new Setting.Num("Seconds between requests", "Keeps the client from hammering the provider.", 4, 1, 60, 1, " s"));
    private final Setting.Bool strictJson = add(new Setting.Bool("Strict JSON", "Asks the provider for a JSON object so the answer parses first time. It switches itself off if the provider rejects the option.", true));
    private final Setting.Bool selfHeal = add(new Setting.Bool("Retry what failed", "When an action did not take effect, asks the model one more time with the results so it can correct itself.", true));
    private final Setting.Info status = add(new Setting.Info("Status", this::lines));

    public AiPilot() {
        super("aipilot", "AI Pilot", "Type /zeus ai <what you want> and the AI works out which modules to switch and which settings to change, then does it.", Category.UTILITY);
        anywhere();
        alias("ai", "assistant", "pilot");
    }

    public static AiPilot find() {
        return Zeus.get(AiPilot.class);
    }

    public static void key(String value) {
        AiPilot self = find();
        if (self == null) return;
        self.token.set(value);
        if (value != null && !value.isBlank()) self.provider();
        Zeus.toast(value == null || value.isBlank() ? "AI token cleared" : "AI token saved", true);
    }

    private void provider() {
        String secret = token.get();
        if (secret == null) return;
        boolean stock = endpoint.get().contains("api.openai.com");
        if (secret.startsWith("gsk_")) {
            if (stock) {
                endpoint.set("https://api.groq.com/openai/v1/chat/completions");
                if (model.get().startsWith("gpt")) model.set("llama-3.3-70b-versatile");
            }
        } else if (secret.startsWith("sk-or-")) {
            if (stock) {
                endpoint.set("https://openrouter.ai/api/v1/chat/completions");
                if (model.get().startsWith("gpt")) model.set("openai/gpt-4o-mini");
            }
        } else if (secret.startsWith("AIza")) {
            if (stock) {
                endpoint.set("https://generativelanguage.googleapis.com/v1beta/openai/chat/completions");
                if (model.get().startsWith("gpt")) model.set("gemini-2.0-flash");
            }
        }
    }

    public static boolean hasKey() {
        AiPilot self = find();
        return self != null && self.token.get() != null && !self.token.get().isBlank();
    }

    public static String ask(String instruction) {
        AiPilot self = find();
        if (self == null) return "AI Pilot is missing.";
        return self.submit(instruction);
    }

    private String submit(String instruction) {
        if (!enabled()) return "Switch AI Pilot on first (/zeus on aipilot).";
        String prompt = instruction == null ? "" : instruction.trim();
        if (prompt.isEmpty()) return "Say what you want after /zeus ai.";
        loadMemory();
        if (!prompt.startsWith("These actions were attempted")) followUps = 0;
        String secret = token.get();
        if (secret == null || secret.isBlank()) return local(prompt, "No token saved, so the built-in parser handled it");
        long now = System.currentTimeMillis();
        long gap = Math.round(wait.get() * 1000.0);
        if (busy) return "Still thinking about the last request.";
        if (now - LAST.get() < gap) return "One moment, the last request was just sent.";
        if (!allow()) return "Hourly request limit reached (" + perHour.i() + "). Raise it in the AI Pilot settings.";
        busy = true;
        LAST.set(now);
        failure = "";
        context = readContext();
        WORKER.submit(() -> {
            try {
                String answer;
                try {
                    answer = call(body(prompt), secret);
                } catch (RuntimeException first) {
                    if (!strictJson.on() || noJsonMode || !String.valueOf(first.getMessage()).contains("400")) throw first;
                    noJsonMode = true;
                    answer = call(body(prompt), secret);
                }
                Plan plan;
                try {
                    plan = read(answer);
                } catch (RuntimeException malformed) {
                    answer = call(body(prompt + "\n\nYour previous answer was not valid JSON. Reply with the single JSON object and nothing else."), secret);
                    plan = read(answer);
                }
                reply = plan.say();
                answered = System.currentTimeMillis();
                if (speak.on() && !reply.isBlank()) Notify.good(reply);
                remember(prompt, answer);
                recordPlan(prompt, plan.steps());
                for (Step step : plan.steps()) QUEUE.add(step);
                lastAction = plan.steps().isEmpty() ? "nothing to do" : plan.steps().size() + " action(s) queued";
            } catch (Throwable t) {
                failure = describe(t);
                busy = false;
                Minecraft client = Minecraft.getInstance();
                client.execute(() -> local(prompt, "The AI could not answer (" + failure + "), so the built-in parser handled it"));
                return;
            } finally {
                busy = false;
            }
        });
        return "Thinking about it...";
    }

    private boolean allow() {
        long hour = System.currentTimeMillis() / 3_600_000L;
        if (WINDOW.get() != hour) {
            WINDOW.set(hour);
            USED.set(0);
        }
        return USED.incrementAndGet() <= Math.max(1, perHour.i());
    }

    public static void undo() {
        Undo last;
        synchronized (UNDO) {
            last = UNDO.pollFirst();
        }
        if (last == null || last.module() == null) {
            Notify.info("Nothing to undo");
            return;
        }
        if (last.setting() == null) {
            last.module().setEnabled(last.wasOn());
            Notify.good(last.module().name + " is back " + (last.wasOn() ? "on" : "off"));
            return;
        }
        Setting setting = last.setting();
        if (setting instanceof Setting.Bool bool) bool.set(last.wasOn());
        else if (setting instanceof Setting.Num num) num.set(last.oldNum());
        else if (setting instanceof Setting.Choice choice) choice.set(last.oldIndex());
        else if (setting instanceof Setting.Pick pick) pick.set(last.oldIndex());
        else if (setting instanceof Setting.Text text) text.set(last.oldText());
        Notify.good(last.module().name + ": " + setting.name + " put back");
    }

    private static void push(Undo undo) {
        synchronized (UNDO) {
            UNDO.addFirst(undo);
            while (UNDO.size() > UNDO_MAX) UNDO.removeLast();
        }
    }

    public static void reset() {
        QUEUE.clear();
        synchronized (UNDO) {
            UNDO.clear();
        }
        synchronized (HISTORY) {
            HISTORY.clear();
        }
        NOTES.clear();
        RESULTS.clear();
        synchronized (PLAN) {
            PLAN.clear();
            PLAN_RESULTS.clear();
            planStep = 0;
            lastPrompt = "";
        }
        followAt = 0;
        followPrompt = "";
        followUps = 0;
        saveMemory();
        reply = "";
        failure = "";
        lastAction = "";
        Notify.info("AI Pilot cleared its queued actions and memory");
    }

    private String describe(Throwable t) {
        String message = t.getMessage();
        return message == null || message.isBlank() ? t.getClass().getSimpleName() : message;
    }

    @Override
    protected void onTick(Minecraft mc) {
        int limit = perRequest.i();
        int done = 0;
        Step step;
        while (done < limit && (step = QUEUE.poll()) != null) {
            apply(step);
            RESULTS.add(lastAction == null ? "" : lastAction);
            recordOutcome(lastAction);
            done++;
        }
        if (!QUEUE.isEmpty()) return;
        if (followAt > 0) {
            if (System.currentTimeMillis() < followAt) return;
            followAt = 0;
            String prompt = followPrompt;
            followPrompt = "";
            RESULTS.clear();
            submit(prompt);
            return;
        }
        if (RESULTS.isEmpty()) return;
        String summary = summarise();
        int bad = failures();
        if (bad > 0 && selfHeal.on() && followUps < 2 && hasKey() && !preview.on()) {
            followUps++;
            followPrompt = "These actions were attempted and produced these results: " + summary + ". Fix the ones that failed. Reply with the single JSON object and nothing else.";
            followAt = System.currentTimeMillis() + Math.max(1500L, Math.round(wait.get() * 1000.0) + 250L);
            Notify.info("AI: correcting " + bad + " action(s) that did not take effect");
            return;
        }
        if (speak.on()) Notify.info("AI: " + summary);
        RESULTS.clear();
        followUps = 0;
    }

    private static boolean failed(String result) {
        if (result == null || result.isBlank()) return true;
        String lower = result.toLowerCase(Locale.ROOT);
        return lower.contains("did not") || lower.contains("has no setting") || lower.contains("missing")
                || lower.contains("may not touch") || lower.contains("does not exist") || lower.contains("never answered")
                || lower.contains("refused");
    }

    private static int failures() {
        int bad = 0;
        for (String result : RESULTS) if (failed(result)) bad++;
        return bad;
    }

    private static String summarise() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < RESULTS.size(); i++) {
            if (i > 0) builder.append("; ");
            String result = RESULTS.get(i);
            builder.append(result == null ? "no effect" : result.length() > 90 ? result.substring(0, 90) : result);
        }
        return builder.toString();
    }

    private void apply(Step step) {
        if (step == null) return;
        String verb = step.action() == null ? "" : step.action().toLowerCase(Locale.ROOT);
        if (verb.equals("report")) {
            report();
            return;
        }
        if (preview.on()) {
            Notify.info("Would " + describe(step));
            lastAction = "preview: " + describe(step);
            return;
        }
        switch (verb) {
            case "open", "panel" -> {
                com.zlorbie788.zeus.client.ZeusClient.openLater();
                lastAction = "opened the panel";
                return;
            }
            case "hud" -> {
                com.zlorbie788.zeus.client.ZeusClient.openHudEditorLater();
                lastAction = "opened the HUD editor";
                return;
            }
            case "keybinds" -> {
                com.zlorbie788.zeus.client.ZeusClient.openKeybindsLater();
                lastAction = "opened the keybinds";
                return;
            }
            case "panic" -> {
                if (toggles.on()) Zeus.panic(true);
                lastAction = "turned everything off";
                return;
            }
            case "undo" -> {
                undo();
                return;
            }
            case "remember" -> {
                note(step.value());
                return;
            }
            case "forget" -> {
                NOTES.clear();
                saveMemory();
                lastAction = "forgot its notes";
                Notify.info("AI memory cleared");
                return;
            }
            case "preset" -> {
                lastAction = preset(step.value());
                return;
            }
            case "help" -> {
                help();
                return;
            }
            case "explain" -> {
                explain(step.value());
                return;
            }
            case "theme" -> {
                theme(step.value());
                return;
            }
            case "waypoint" -> {
                Extras.Waypoints points = Zeus.get(Extras.Waypoints.class);
                Minecraft client = Minecraft.getInstance();
                if (points != null && client.player != null) {
                    String name = step.value() == null || step.value().isBlank() ? "AI waypoint" : step.value();
                    points.addAt(name, client.player.getX(), client.player.getY(), client.player.getZ());
                    lastAction = "waypoint " + name;
                }
                return;
            }
            default -> {
            }
        }
        if (step.module() == null || step.module().isBlank()) return;
        Module module = Zeus.byName(step.module());
        if (module == null) {
            Notify.warn("The AI asked for a module that does not exist: " + step.module());
            return;
        }
        if (BLOCKED.contains(module.id)) {
            Notify.warn("The AI may not touch " + module.name + ".");
            return;
        }
        lastAction = describe(step);
        String action = verb;
        switch (action) {
            case "on", "enable" -> {
                if (toggles.on()) {
                    push(new Undo(module, null, module.enabled(), null, 0, 0));
                    module.setEnabled(true);
                }
                lastAction = confirm(module, true);
            }
            case "off", "disable" -> {
                if (toggles.on()) {
                    push(new Undo(module, null, module.enabled(), null, 0, 0));
                    module.setEnabled(false);
                }
                lastAction = confirm(module, false);
            }
            case "toggle" -> {
                if (toggles.on()) {
                    push(new Undo(module, null, module.enabled(), null, 0, 0));
                    module.toggle();
                }
                lastAction = module.name + " is now " + (module.enabled() ? "on" : "off");
            }
            case "set" -> {
                if (settings.on()) setValue(module, step);
            }
            default -> {
            }
        }
    }

    private void setValue(Module module, Step step) {
        String wanted = step.setting() == null ? "" : step.setting().trim();
        if (wanted.isEmpty()) return;
        String key = normalise(wanted);
        Setting target = null;
        for (Setting setting : module.settings()) {
            if (setting instanceof Setting.Secret) continue;
            if (normalise(setting.name).equals(key)) {
                target = setting;
                break;
            }
        }
        if (target == null) {
            for (Setting setting : module.settings()) {
                if (setting instanceof Setting.Secret) continue;
                String name = normalise(setting.name);
                if (name.contains(key) || key.contains(name)) {
                    target = setting;
                    break;
                }
            }
        }
        if (target == null) {
            Notify.warn(module.name + " has no setting like " + wanted + ".");
            lastAction = module.name + " has no setting like " + wanted;
            return;
        }
        String value = step.value() == null ? "" : step.value().trim();
        push(new Undo(module, target,
                target instanceof Setting.Bool bool ? bool.on() : false,
                target instanceof Setting.Text text ? text.get() : null,
                target instanceof Setting.Choice choice ? choice.index() : target instanceof Setting.Pick pick ? pick.index() : 0,
                target instanceof Setting.Num num ? num.get() : 0));
        if (target instanceof Setting.Bool bool) bool.set(truthy(value));
        else if (target instanceof Setting.Num num) num.set(number(value, num.get()));
        else if (target instanceof Setting.Choice choice) choice.set(choiceIndex(choice, value));
        else if (target instanceof Setting.Text text) text.set(value);
        else if (target instanceof Setting.Pick pick) pick.set(pickIndex(pick, value));
        lastAction = module.name + " " + target.name + " set to " + value;
    }

    private static String normalise(String text) {
        StringBuilder builder = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = Character.toLowerCase(text.charAt(i));
            if (c >= 'a' && c <= 'z') builder.append(c);
            else if (c >= '0' && c <= '9') builder.append(c);
        }
        return builder.toString();
    }

    private String confirm(Module module, boolean want) {
        if (module.enabled() == want) return module.name + (want ? " is on" : " is off");
        if (module.locked()) return module.name + " is locked: " + module.note();
        return module.name + " did not switch" + (module.error == null ? "" : " (" + module.error + ")");
    }

    private String describe(Step step) {
        String verb = step.action() == null ? "do" : step.action().toLowerCase(Locale.ROOT);
        if (verb.equals("waypoint")) {
            String name = step.value() == null || step.value().isBlank() ? "AI waypoint" : step.value();
            return "add a waypoint named " + name;
        }
        if (verb.equals("theme")) {
            String name = step.value() == null || step.value().isBlank() ? "a theme" : step.value();
            return "switch the theme to " + name;
        }
        if (verb.equals("report")) return "read out the client state";
        if (step.module() == null || step.module().isBlank()) return verb;
        if (verb.equals("set")) return "set " + step.module() + " " + step.setting() + " to " + step.value();
        return verb + " " + step.module();
    }

    private static void remember(String prompt, String answer) {
        synchronized (HISTORY) {
            HISTORY.addLast(message("user", prompt.length() > 400 ? prompt.substring(0, 400) : prompt));
            HISTORY.addLast(message("assistant", answer.length() > 900 ? answer.substring(0, 900) : answer));
            while (HISTORY.size() > HISTORY_MAX) HISTORY.removeFirst();
        }
        saveMemory();
    }

    private static boolean truthy(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.equals("on") || lower.equals("true") || lower.equals("yes") || lower.equals("1") || lower.equals("enable");
    }

    private static double number(String value, double fallback) {
        try {
            return Double.parseDouble(value.replace("%", "").replace(",", "").trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int choiceIndex(Setting.Choice choice, String value) {
        for (int i = 0; i < choice.options.length; i++) {
            if (choice.options[i].equalsIgnoreCase(value)) return i;
        }
        return choice.index();
    }

    private static int pickIndex(Setting.Pick pick, String value) {
        for (int i = 0; i < pick.ids.length; i++) {
            if (pick.ids[i].equalsIgnoreCase(value) || (i < pick.labels.length && pick.labels[i].equalsIgnoreCase(value))) return i;
        }
        return pick.index();
    }

    private String body(String instruction) {
        JsonObject root = new JsonObject();
        root.addProperty("model", model.get().trim());
        root.addProperty("temperature", temperature.get());
        root.addProperty("max_tokens", tokens.i());
        if (strictJson.on() && !noJsonMode) {
            JsonObject format = new JsonObject();
            format.addProperty("type", "json_object");
            root.add("response_format", format);
        }
        JsonArray messages = new JsonArray();
        messages.add(message("system", system(instruction)));
        synchronized (HISTORY) {
            for (JsonObject past : HISTORY) messages.add(past);
        }
        messages.add(message("user", instruction));
        root.add("messages", messages);
        return GSON.toJson(root);
    }

    private static JsonObject message(String role, String content) {
        JsonObject item = new JsonObject();
        item.addProperty("role", role);
        item.addProperty("content", content);
        return item;
    }

    private String system(String prompt) {
        StringBuilder builder = new StringBuilder();
        builder.append("You control the Zeus Minecraft client. Answer with one JSON object only, no prose, no code fences.\n");
        builder.append("Schema: {\"say\":\"one short sentence for the player\",\"actions\":[{\"do\":\"on|off|toggle|set|open|hud|keybinds|panic|theme|undo|waypoint\",\"module\":\"<id>\",\"setting\":\"<exact setting name>\",\"value\":\"<new value>\"}]}\n");
        builder.append("open, hud, keybinds, panic, theme, undo and waypoint need no module field. panic switches every module off. undo reverses the last change the AI made. theme takes value as one of: ");
        builder.append(String.join(", ", com.zlorbie788.zeus.ui.UiSettings.ACCENT.options)).append(". waypoint drops a waypoint where the player stands, using value as its name.\n");
        builder.append("Rules: module must be an id from the list. For \"set\" the setting name must match a setting the module shows. ");
        builder.append("Only send actions that are needed. Never invent ids. If the request cannot be done, return an empty actions list and explain in say.\n");
        if (!context.isBlank()) builder.append("Context: ").append(context).append('\n');
        if (!NOTES.isEmpty()) builder.append("Notes from earlier sessions: ").append(String.join(" | ", NOTES)).append('\n');
        builder.append("Presets available through do=preset: pvp, crystal, survival, performance, privacy.\n");
        builder.append("Modules (id: name [on/off]):\n");
        int count = 0;
        for (Module module : Zeus.all()) {
            if (!module.toggleable() || BLOCKED.contains(module.id)) continue;
            builder.append(module.id).append(": ").append(module.name).append(module.enabled() ? " [on]" : " [off]").append('\n');
            if (++count >= 220) break;
        }
        String lower = prompt == null ? "" : prompt.toLowerCase(Locale.ROOT);
        int detailed = 0;
        for (Module module : Zeus.all()) {
            if (detailed >= 16 || BLOCKED.contains(module.id)) continue;
            boolean named = mentions(lower, module);
            if (!named && !module.enabled()) continue;
            StringBuilder block = new StringBuilder();
            for (Setting setting : module.settings()) {
                if (setting instanceof Setting.Secret || !setting.visible()) continue;
                String value = read(setting);
                if (value == null) continue;
                block.append("  ").append(setting.name).append(" = ").append(value).append('\n');
            }
            if (block.length() == 0) continue;
            builder.append("Settings of ").append(module.id).append(":\n").append(block);
            detailed++;
        }
        return builder.toString();
    }

    private static boolean mentions(String lower, Module module) {
        if (lower.isBlank()) return false;
        if (lower.contains(module.id)) return true;
        if (lower.contains(module.name.toLowerCase(Locale.ROOT))) return true;
        for (String alias : module.aliases()) {
            if (alias.length() > 2 && lower.contains(alias.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private static String read(Setting setting) {
        if (setting instanceof Setting.Bool bool) return bool.on() ? "on" : "off";
        if (setting instanceof Setting.Num num) return num.text();
        if (setting instanceof Setting.Choice choice) return choice.value();
        if (setting instanceof Setting.Pick pick) return pick.label();
        if (setting instanceof Setting.Text text) return text.get().isEmpty() ? "(empty)" : text.get();
        return null;
    }

    private String call(String payload, String secret) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint.get().trim()))
                .timeout(Duration.ofSeconds(25))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + secret)
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
        int attempt = 0;
        while (true) {
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            int code = response.statusCode();
            if (code == 429 || code >= 500) {
                if (++attempt >= 3) throw new IllegalStateException("HTTP " + code + " " + shorten(response.body()));
                Thread.sleep(900L * attempt);
                continue;
            }
            if (code < 200 || code >= 300) {
                throw new IllegalStateException("HTTP " + code + " " + shorten(response.body()));
            }
            return response.body();
        }
    }

    private static String shorten(String text) {
        if (text == null) return "";
        String flat = text.replaceAll("\\s+", " ").trim();
        return flat.length() <= 120 ? flat : flat.substring(0, 120) + "...";
    }

    private record Plan(String say, List<Step> steps) {}

    private Plan read(String body) {
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        JsonArray choices = root.getAsJsonArray("choices");
        if (choices == null || choices.isEmpty()) throw new IllegalStateException("the provider returned no choices");
        JsonObject first = choices.get(0).getAsJsonObject();
        JsonObject message = first.getAsJsonObject("message");
        String content = message != null && message.has("content") ? message.get("content").getAsString() : "";
        String json = strip(content);
        JsonObject parsed = JsonParser.parseString(json).getAsJsonObject();
        String say = parsed.has("say") && !parsed.get("say").isJsonNull() ? parsed.get("say").getAsString() : "";
        List<Step> steps = new ArrayList<>();
        JsonArray actions = parsed.has("actions") && parsed.get("actions").isJsonArray() ? parsed.getAsJsonArray("actions") : new JsonArray();
        for (JsonElement element : actions) {
            if (!element.isJsonObject()) continue;
            JsonObject action = element.getAsJsonObject();
            steps.add(new Step(
                    text(action, "do"),
                    text(action, "module"),
                    text(action, "setting"),
                    text(action, "value")));
            if (steps.size() >= 20) break;
        }
        return new Plan(say, steps);
    }

    private static String text(JsonObject object, String key) {
        if (!object.has(key) || object.get(key).isJsonNull()) return "";
        JsonElement value = object.get(key);
        return value.isJsonPrimitive() ? value.getAsString() : value.toString();
    }

    private static String strip(String content) {
        String text = content == null ? "" : content.trim();
        if (text.startsWith("```")) {
            int first = text.indexOf('\n');
            if (first >= 0) text = text.substring(first + 1);
            int last = text.lastIndexOf("```");
            if (last >= 0) text = text.substring(0, last);
        }
        int open = text.indexOf('{');
        int close = text.lastIndexOf('}');
        if (open >= 0 && close > open) text = text.substring(open, close + 1);
        return text.trim();
    }

    private void report() {
        Minecraft client = Minecraft.getInstance();
        List<String> on = new ArrayList<>();
        for (Module module : Zeus.all()) if (module.enabled()) on.add(module.name);
        StringBuilder builder = new StringBuilder();
        builder.append("FPS ").append(client.getFps());
        if (client.player != null) builder.append(", health ").append(Math.round(client.player.getHealth())).append("/20");
        builder.append(", ").append(on.size()).append(" modules on");
        if (!on.isEmpty()) builder.append(": ").append(String.join(", ", on.subList(0, Math.min(8, on.size()))));
        reply = builder.toString();
        answered = System.currentTimeMillis();
        lastAction = "reported the client state";
        if (speak.on()) Notify.info(reply);
    }

    private void help() {
        Notify.info("Try: on sprint, off esp, set killaura range to 4, preset pvp, theme violet, remember I play crystal pvp, undo, status");
        lastAction = "showed the command help";
    }

    private void explain(String value) {
        Module module = Zeus.byName(value);
        if (module == null) {
            Notify.warn("No module called " + value);
            lastAction = "no module called " + value;
            return;
        }
        Notify.info(module.name + ": " + module.desc);
        int shown = 0;
        for (Setting setting : module.settings()) {
            if (setting instanceof Setting.Secret || !setting.visible()) continue;
            String read = read(setting);
            if (read == null) continue;
            Notify.info("  " + setting.name + " = " + read);
            if (++shown >= 6) break;
        }
        lastAction = "explained " + module.name;
    }

    private static final java.util.Map<String, String[]> PRESETS = java.util.Map.of(
            "pvp", new String[]{"Auto Totem", "Auto Armor", "Auto Eat", "Aim Assist", "Auto Clicker", "Hitboxes"},
            "crystal", new String[]{"Crystal Aura", "Anchor Aura", "Auto Totem", "Auto Armor", "Auto Eat"},
            "survival", new String[]{"Auto Totem", "Auto Armor", "Auto Eat", "Auto Fish", "Fast Place"},
            "performance", new String[]{"No Hurt Cam", "No Break Delay", "Snap Tap"},
            "privacy", new String[]{"Streamer Privacy"});

    private String preset(String value) {
        String key = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        String[] names = PRESETS.get(key);
        if (names == null) return "No preset called " + value + ". Try pvp, crystal, survival, performance or privacy.";
        if (!toggles.on()) return "Switching modules is turned off in the AI settings.";
        int on = 0;
        for (String name : names) {
            Module module = Zeus.byName(name);
            if (module == null || BLOCKED.contains(module.id)) continue;
            if (!module.enabled()) {
                push(new Undo(module, null, false, null, 0, 0));
                module.setEnabled(true);
            }
            if (module.enabled()) on++;
        }
        Notify.good("Preset " + key + ": " + on + " module(s) on");
        return "preset " + key + " with " + on + " modules";
    }

    private static void note(String text) {
        if (text == null || text.isBlank()) return;
        String clean = text.trim();
        NOTES.removeIf(n -> n.equalsIgnoreCase(clean));
        NOTES.add(clean);
        while (NOTES.size() > NOTES_MAX) NOTES.remove(0);
        saveMemory();
        Notify.good("Noted: " + clean);
    }

    private static void saveMemory() {
        JsonObject root = new JsonObject();
        JsonArray notes = new JsonArray();
        for (String note : NOTES) notes.add(note);
        root.add("notes", notes);
        JsonArray history = new JsonArray();
        synchronized (HISTORY) {
            for (JsonObject past : HISTORY) history.add(past);
        }
        root.add("history", history);
        try {
            java.nio.file.Files.createDirectories(MEMORY.getParent());
            java.nio.file.Path temp = MEMORY.resolveSibling("zeus-ai-memory.json.tmp");
            java.nio.file.Files.writeString(temp, root.toString(), java.nio.charset.StandardCharsets.UTF_8);
            java.nio.file.Files.move(temp, MEMORY, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ignored) {
        }
    }

    private static void loadMemory() {
        if (memoryLoaded) return;
        memoryLoaded = true;
        if (!java.nio.file.Files.isRegularFile(MEMORY)) return;
        try (java.io.Reader reader = java.nio.file.Files.newBufferedReader(MEMORY, java.nio.charset.StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (root.has("notes") && root.get("notes").isJsonArray()) {
                for (JsonElement element : root.getAsJsonArray("notes")) {
                    if (element.isJsonPrimitive()) NOTES.add(element.getAsString());
                }
                while (NOTES.size() > NOTES_MAX) NOTES.remove(0);
            }
            if (root.has("history") && root.get("history").isJsonArray()) {
                synchronized (HISTORY) {
                    HISTORY.clear();
                    for (JsonElement element : root.getAsJsonArray("history")) {
                        if (!element.isJsonObject()) continue;
                        HISTORY.addLast(element.getAsJsonObject());
                        while (HISTORY.size() > HISTORY_MAX) HISTORY.removeFirst();
                    }
                }
            }
        } catch (Exception ignored) {
            NOTES.clear();
        }
    }

    private static final java.util.regex.Pattern CLAUSE = java.util.regex.Pattern.compile("\\s*(?:,|;|\\bthen\\b|\\band\\b)\\s*", java.util.regex.Pattern.CASE_INSENSITIVE);
    private static final String[] ON_WORDS = {"turn on ", "switch on ", "enable ", "start ", "on "};
    private static final String[] OFF_WORDS = {"turn off ", "switch off ", "disable ", "stop ", "off "};
    private static final String[] TOGGLE_WORDS = {"toggle ", "flip "};

    private String local(String prompt, String note) {
        Plan plan = parseLocal(prompt);
        recordPlan(prompt, plan.steps());
        int done = 0;
        for (Step step : plan.steps()) {
            apply(step);
            recordOutcome(lastAction);
            if (preview.on()) continue;
            done++;
        }
        reply = plan.say();
        answered = System.currentTimeMillis();
        lastAction = done == 0 ? "nothing to do" : done + " action(s) by the built-in parser";
        String message = plan.say().isBlank() ? note : note + ". " + plan.say();
        if (speak.on()) Notify.info(message);
        return message;
    }

    private Plan parseLocal(String prompt) {
        String clean = prompt.toLowerCase(Locale.ROOT).trim();
        List<Step> steps = new ArrayList<>();
        for (String piece : CLAUSE.split(clean)) {
            Step step = clause(piece.trim());
            if (step != null) steps.add(step);
        }
        if (steps.isEmpty()) {
            return new Plan("I could not find a module or command in that. Try: on sprint, off esp, set aimbot range to 4, theme violet, undo.", steps);
        }
        return new Plan(steps.size() == 1 ? "Done." : steps.size() + " changes done.", steps);
    }

    private Step clause(String part) {
        if (part.isEmpty()) return null;
        switch (part) {
            case "panic", "everything off", "off everything", "all off", "disable everything", "turn everything off", "turn all off":
                return new Step("panic", "", "", "");
            case "undo", "revert", "undo that", "undo last":
                return new Step("undo", "", "", "");
            case "open", "panel", "menu", "open panel", "open menu", "show panel":
                return new Step("open", "", "", "");
            case "hud", "hud editor", "move hud", "edit hud":
                return new Step("hud", "", "", "");
            case "keybinds", "binds", "keys", "keybinds editor":
                return new Step("keybinds", "", "", "");
            case "status", "report", "what is on", "whats on", "what's on", "fps", "ping", "how are my frames":
                return new Step("report", "", "", "");
            case "forget", "forget everything", "clear your memory":
                return new Step("forget", "", "", "");
            case "help", "commands", "what can you do", "examples":
                return new Step("help", "", "", "");
            default:
        }
        if (part.startsWith("explain ")) return new Step("explain", "", "", part.substring(8).trim());
        if (part.startsWith("what does ") && part.endsWith(" do")) return new Step("explain", "", "", part.substring(10, part.length() - 3).trim());
        if (part.startsWith("remember ")) return new Step("remember", "", "", part.substring(9).trim());
        if (part.startsWith("preset ")) return new Step("preset", "", "", part.substring(7).trim());
        if (part.startsWith("set up for ")) return new Step("preset", "", "", part.substring(11).trim());
        if (part.startsWith("theme ")) return new Step("theme", "", "", part.substring(6).trim());
        if (part.startsWith("waypoint")) {
            String name = part.length() > 9 ? part.substring(9).trim() : "";
            return new Step("waypoint", "", "", name);
        }
        if (part.startsWith("set ")) return settingStep(part.substring(4).trim());
        for (String word : ON_WORDS) {
            if (part.startsWith(word)) return moduleStep("on", part.substring(word.length()).trim());
        }
        for (String word : OFF_WORDS) {
            if (part.startsWith(word)) return moduleStep("off", part.substring(word.length()).trim());
        }
        for (String word : TOGGLE_WORDS) {
            if (part.startsWith(word)) return moduleStep("toggle", part.substring(word.length()).trim());
        }
        return moduleStep("toggle", part);
    }

    private Step moduleStep(String action, String words) {
        if (words.isEmpty()) return null;
        Module module = Zeus.byName(words);
        if (module == null || BLOCKED.contains(module.id)) return null;
        return new Step(action, module.id, "", "");
    }

    private Step settingStep(String body) {
        int at = body.indexOf(" to ");
        if (at <= 0) return null;
        String left = body.substring(0, at).trim();
        String value = body.substring(at + 4).trim();
        if (value.isEmpty()) return null;
        for (Module module : Zeus.all()) {
            if (BLOCKED.contains(module.id)) continue;
            for (String key : new String[]{module.id, module.name.toLowerCase(Locale.ROOT)}) {
                if (!left.startsWith(key + " ")) continue;
                return new Step("set", module.id, left.substring(key.length() + 1).trim(), value);
            }
        }
        int split = left.lastIndexOf(' ');
        if (split <= 0) return null;
        Module module = Zeus.byName(left.substring(0, split));
        if (module == null || BLOCKED.contains(module.id)) return null;
        return new Step("set", module.id, left.substring(split + 1).trim(), value);
    }

    private String theme(String value) {
        String wanted = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (wanted.isEmpty()) {
            Notify.warn("Name a theme, for example violet or glacier.");
            return null;
        }
        String[] options = com.zlorbie788.zeus.ui.UiSettings.ACCENT.options;
        for (int i = 0; i < options.length; i++) {
            String option = options[i].toLowerCase(Locale.ROOT);
            if (!option.equals(wanted) && !option.startsWith(wanted) && !option.contains(wanted)) continue;
            com.zlorbie788.zeus.ui.UiSettings.ACCENT.set(i);
            Notify.good("Theme: " + options[i]);
            lastAction = "theme " + options[i];
            return options[i];
        }
        Notify.warn("No theme called " + value);
        return null;
    }

    private static String readContext() {
        Minecraft client = Minecraft.getInstance();
        StringBuilder builder = new StringBuilder();
        builder.append("Theme: ").append(com.zlorbie788.zeus.ui.UiSettings.ACCENT.value()).append('.');
        if (client.player == null || client.level == null) {
            return builder.append(" The player is not in a world.").toString();
        }
        builder.append(" World: ").append(client.level.dimension().identifier()).append('.');
        builder.append(" Health: ").append(Math.round(client.player.getHealth())).append("/20, food ")
                .append(client.player.getFoodData().getFoodLevel()).append("/20.");
        builder.append(" Position: ").append(Math.round(client.player.getX())).append(' ')
                .append(Math.round(client.player.getY())).append(' ')
                .append(Math.round(client.player.getZ())).append('.');
        builder.append(" FPS: ").append(client.getFps()).append('.');
        builder.append(" Modules on:");
        int shown = 0;
        for (Module module : Zeus.all()) {
            if (!module.enabled()) continue;
            builder.append(' ').append(module.id);
            if (++shown >= 12) break;
        }
        if (shown == 0) builder.append(" none");
        return builder.toString();
    }

    private void recordPlan(String prompt, List<Step> steps) {
        synchronized (PLAN) {
            PLAN.clear();
            PLAN_RESULTS.clear();
            planStep = 0;
            lastPrompt = prompt == null ? "" : prompt;
            for (Step step : steps) PLAN.add(describe(step));
        }
    }

    private static void recordOutcome(String result) {
        synchronized (PLAN) {
            String text = result == null ? "" : result;
            while (PLAN_RESULTS.size() < planStep) PLAN_RESULTS.add("");
            if (planStep < PLAN_RESULTS.size()) PLAN_RESULTS.set(planStep, text);
            else PLAN_RESULTS.add(text);
            planStep++;
        }
    }

    private List<String> lines() {
        List<String> out = new ArrayList<>();
        out.add((hasKey() ? "Token saved" : "Token missing (/zeus ai key)") + " · " + model.get());
        out.add((busy ? "Working" : QUEUE.isEmpty() ? "Idle" : QUEUE.size() + " action(s) running")
                + " · " + USED.get() + "/" + perHour.i() + " this hour" + (preview.on() ? " · preview" : "")
                + " · " + NOTES.size() + " note(s)");
        if (!lastPrompt.isBlank()) out.add("Asked: " + shorten(lastPrompt));
        synchronized (PLAN) {
            int shown = 0;
            for (int i = 0; i < PLAN.size() && shown < 4; i++) {
                String result = i < PLAN_RESULTS.size() ? PLAN_RESULTS.get(i) : "";
                String mark = result.isEmpty() ? "·" : failed(result) ? "x" : "+";
                out.add(mark + " " + shorten(PLAN.get(i)) + (result.isEmpty() ? "" : " > " + shorten(result)));
                shown++;
            }
            if (PLAN.size() > shown) out.add("… " + (PLAN.size() - shown) + " more step(s)");
            if (PLAN.isEmpty()) out.add("No plan yet. Try /zeus ai on sprint");
        }
        if (!failure.isBlank()) out.add("Last error: " + shorten(failure));
        else if (!reply.isBlank()) out.add("Says: " + shorten(reply));
        synchronized (UNDO) {
            if (!UNDO.isEmpty()) out.add("Undo: " + UNDO.size() + " step(s) back (/zeus ai undo)");
        }
        return out;
    }

    public static List<String> planLines() {
        List<String> out = new ArrayList<>();
        synchronized (PLAN) {
            if (lastPrompt.isBlank() && PLAN.isEmpty()) {
                out.add("No plan yet. Try /zeus ai on sprint");
                return out;
            }
            if (!lastPrompt.isBlank()) out.add("Asked: " + lastPrompt);
            for (int i = 0; i < PLAN.size(); i++) {
                String result = i < PLAN_RESULTS.size() ? PLAN_RESULTS.get(i) : "";
                String mark = result.isEmpty() ? "·" : failed(result) ? "x" : "+";
                out.add(mark + " " + PLAN.get(i) + (result.isEmpty() ? "" : " > " + result));
            }
        }
        return out;
    }

    @Override
    public String info() {
        if (!hasKey()) return "no token";
        if (busy) return "thinking";
        if (!QUEUE.isEmpty()) return QUEUE.size() + " queued";
        synchronized (PLAN) {
            if (!PLAN.isEmpty()) {
                int bad = 0;
                for (String result : PLAN_RESULTS) if (failed(result)) bad++;
                return bad > 0 ? bad + " failed" : PLAN.size() + " step(s)";
            }
        }
        return "ready";
    }
}
