package com.zlorbie788.zeus.mods;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zlorbie788.zeus.core.Build;
import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Inv;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Notify;
import com.zlorbie788.zeus.core.Setting;
import com.zlorbie788.zeus.core.Zeus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

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

    private final Setting.Bool jobs = add(new Setting.Bool("Do jobs in the world", "Lets the AI chop, mine, quarry, tunnel, farm, replant, collect drops, fight, flee, shoot, shear, milk, eat, place blocks, build a house (gathering the materials first), swim, climb, bridge, light the area with torches, drop items, sleep, explore, walk to coordinates through obstacles and follow players.", true));
    private final Setting.Num jobRange = add(new Setting.Num("Job search range", "How far a job looks for trees, ore and drops.", 16, 6, 40, 2, " blocks")).showIf(() -> jobs.on());
    private final Setting.Num jobReach = add(new Setting.Num("Job mining reach", "How far the AI reaches while breaking a job block.", 4.3, 3.0, 4.5, 0.1, " blocks")).showIf(() -> jobs.on());
    private final Setting.Bool jobWaypoint = add(new Setting.Bool("Waypoint what it finds", "When a mining job finds the ore it was looking for, drop a waypoint on it.", true)).showIf(() -> jobs.on());
    private final Setting.Bool jobDig = add(new Setting.Bool("Dig through obstacles", "Lets the AI break a block that blocks the path instead of only stepping around it.", true)).showIf(() -> jobs.on());
    private final Setting.Info jobStatus = add(new Setting.Info("Job", this::jobLines));

    private enum JobKind { CHOP, MINE_UNTIL, MINE_COUNT, GOTO, FOLLOW, COLLECT, TUNNEL, FARM, SLEEP,
                           REPLANT, EAT, HUNT, FLEE, SHOOT, SHEAR, MILK, QUARRY, PLACE, TORCH, DROP, RETURN, EXPLORE,
                           HOUSE, SWIM, CLIMB, BRIDGE, TILL }

    private static final class Job {
        final JobKind kind;
        String target;
        int wanted;
        Vec3 to;
        int mined;
        int trees;
        long started = System.currentTimeMillis();
        long nextSearch;
        long nextScan;
        long quietSince;
        BlockPos block;
        BlockPos limit;
        String note = "";
        Vec3 lastTarget;
        long lastCheck;
        double lastX;
        double lastZ;
        int blocked;
        int entityId = -1;
        long actionAt;
        int pending;
        int seen;
        int attempts;
        int pathRetries;
        boolean phase;
        double bestDist;
        long stallSince;
        long detourUntil;
        float detourYaw;
        boolean detourSide;

        Job(JobKind kind, String target, int wanted, Vec3 to) {
            this.kind = kind;
            this.target = target == null ? "" : target;
            this.wanted = wanted;
            this.to = to;
        }

        String label() {
            return switch (kind) {
                case CHOP -> (wanted > 1 ? "chopping trees (" + Math.min(trees, wanted) + "/" + wanted + ")" : "chopping a tree")
                        + (mined > 0 ? " (" + mined + " logs)" : "");
                case MINE_UNTIL -> "mining until it finds " + target;
                case MINE_COUNT -> "mining " + target + " (" + Math.min(mined, wanted) + "/" + wanted + ")";
                case GOTO -> "walking to " + Math.round(to.x) + " " + Math.round(to.y) + " " + Math.round(to.z);
                case FOLLOW -> "following " + (target.isBlank() ? "the nearest player" : target);
                case COLLECT -> "picking up drops" + (mined > 0 ? " (" + mined + ")" : "");
                case TUNNEL -> "tunnelling (" + Math.min(mined, wanted) + "/" + wanted + ")";
                case FARM -> "harvesting crops" + (mined > 0 ? " (" + mined + " harvested)" : "");
                case SLEEP -> "looking for a bed";
                case REPLANT -> "planting seeds" + (mined > 0 ? " (" + mined + " planted)" : "");
                case EAT -> "eating";
                case HUNT -> "fighting" + (mined > 0 ? " (" + mined + " hits)" : "");
                case FLEE -> "running away";
                case SHOOT -> "shooting" + (mined > 0 ? " (" + mined + " shots)" : "");
                case SHEAR -> "shearing sheep (" + Math.min(mined, Math.max(1, wanted)) + "/" + Math.max(1, wanted) + ")";
                case MILK -> "milking a cow";
                case QUARRY -> "quarrying (" + Math.min(mined, Math.max(9, wanted * 9)) + "/" + Math.max(9, wanted * 9) + ")";
                case PLACE -> "placing " + target;
                case TORCH -> "placing torches (" + Math.min(mined, Math.max(1, wanted)) + "/" + Math.max(1, wanted) + ")";
                case DROP -> "dropping " + target;
                case RETURN -> "walking back";
                case EXPLORE -> "exploring";
                case HOUSE -> (phase ? "building a house (" + mined + " blocks)"
                        : "gathering materials for a house (" + target.replace('_', ' ') + ")");
                case SWIM -> "swimming";
                case CLIMB -> "towering up (" + Math.min(mined, Math.max(1, wanted)) + "/" + Math.max(1, wanted) + ")";
                case BRIDGE -> "bridging (" + Math.min(mined, Math.max(1, wanted)) + "/" + Math.max(1, wanted) + ")";
                case TILL -> "tilling the ground (" + Math.min(mined, Math.max(1, wanted)) + "/" + Math.max(1, wanted) + ")";
            };
        }
    }

    private Job job;
    private static BlockPos mining;
    private static java.util.List<BlockPos> blueprint;
    private static int blueprintIndex;
    private final java.util.ArrayDeque<String> chain = new java.util.ArrayDeque<>();

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
        super("aipilot", "Zeus AI Functions", "Type /zeus ai <what you want>. It switches modules and changes settings, and it works in the world too: chop trees, mine, quarry, tunnel, farm, fight mobs, shoot, shear, milk, eat, place blocks, light the area, drop items, sleep, explore, walk or follow. Say stop to end a job.", Category.UTILITY);
        anywhere();
        alias("ai", "assistant", "pilot", "zeus ai", "functions", "jobs");
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
        if (self == null) return "Zeus AI Functions is missing.";
        return self.submit(instruction);
    }

    private String submit(String instruction) {
        if (!enabled()) return "Switch Zeus AI Functions on first (/zeus on aipilot).";
        String prompt = instruction == null ? "" : instruction.trim();
        if (prompt.isEmpty()) return "Say what you want after /zeus ai.";
        Step work = jobStep(prompt.toLowerCase(Locale.ROOT));
        if (work != null) {
            if ("stop".equals(work.action())) {
                boolean was = job != null;
                cancelJob(Minecraft.getInstance());
                return was ? "Stopped the job." : "No job was running.";
            }
            startJob(work.value());
            return job == null ? "Could not start that job. Check the Zeus AI Functions settings." : "Zeus AI: " + job.label();
        }
        loadMemory();
        if (!prompt.startsWith("These actions were attempted")) followUps = 0;
        String secret = token.get();
        if (secret == null || secret.isBlank()) return local(prompt, "No token saved, so the built-in parser handled it");
        long now = System.currentTimeMillis();
        long gap = Math.round(wait.get() * 1000.0);
        if (busy) return "Still thinking about the last request.";
        if (now - LAST.get() < gap) return "One moment, the last request was just sent.";
        if (!allow()) return "Hourly request limit reached (" + perHour.i() + "). Raise it in the Zeus AI Functions settings.";
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
        Notify.info("Zeus AI Functions cleared its queued actions and memory");
    }

    private String describe(Throwable t) {
        String message = t.getMessage();
        return message == null || message.isBlank() ? t.getClass().getSimpleName() : message;
    }

    @Override
    protected void onTick(Minecraft mc) {
        if (job != null) {
            tickJob(mc);
            return;
        }
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
            case "job", "task" -> {
                startJob(step.value());
                return;
            }
            case "stop" -> {
                cancelJob(Minecraft.getInstance());
                return;
            }
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
        builder.append("Prefix any job with repeat N to run the whole plan N times, for example: repeat 3 chop a tree then mine 10 stone.\n");
        builder.append("do can also be job, where value is a phrase like: chop tree, chop 3 trees, mine until iron, mine 20 stone, quarry 10, tunnel 30, strip mine, farm, replant, till 9, collect, hunt, flee, shoot, shear 3, milk, eat, sleep, torch 8, place cobblestone here, place torch at 120 64 -300, drop 32 dirt, explore 60, return, build a house, swim to 120 62 -300, climb 8, bridge 12, go to 120 64 -300, come here, follow me, follow Steve, dig down, stop. Chain them in one value with then: chop 2 trees then mine until iron.\n");
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

    @Override
    protected void onDisable() {
        job = null;
        releaseKeys(Minecraft.getInstance());
    }

    private List<String> jobLines() {
        if (!jobs.on()) return List.of("Jobs are switched off in the settings.");
        if (job == null) {
            return List.of("Idle. Try: chop that tree, mine until iron, quarry 8, tunnel 30, farm, replant, hunt, shoot, shear, milk, eat, sleep, torch 8, explore 60.",
                    "Also: place cobblestone here, drop 32 dirt, collect, return, follow me, stop.",
                    "Advanced: build a house, swim to 100 62 -200, climb 8, bridge 12, go to 100 64 -200.",
                    "Chain jobs with then: chop 2 trees then mine until iron");
        }
        long secs = (System.currentTimeMillis() - job.started) / 1000L;
        List<String> out = new ArrayList<>();
        out.add(job.label() + " · " + secs + "s");
        out.add(job.note.isBlank() ? "Say stop to cancel" : job.note);
        if (!chain.isEmpty()) out.add("Then: " + String.join(" → ", chain));
        return out;
    }

    private Step jobStep(String part) {
        if (!jobs.on()) return null;
        String text = part.trim();
        if (text.isEmpty()) return null;
        text = text.replace("coordinates", " ").replace("coords", " ").replace("coord", " ");
        text = text.replaceAll("\\s+", " ").trim();
        if (text.equals("stop") || text.equals("cancel") || text.equals("halt") || text.equals("stop that")
                || text.equals("stop it") || text.equals("stop mining") || text.equals("stop following")
                || text.equals("stop the job") || text.equals("cancel the job")) {
            return new Step("stop", "", "", "");
        }
        if (text.contains(" then ") || text.contains(";") || text.contains("->")) {
            String head = text.split("\\s*(?:,|;|->|\\band then\\b|\\bthen\\b)\\s*")[0].trim();
            if (head.matches("^(chop|cut|break|mine|dig|get|go|goto|walk|come|follow|farm|harvest|collect|pick|tunnel|strip|sleep|replant|torch|quarry|milk|shear|hunt|flee|shoot|eat|drop|return|explore|place|put|house|build|swim|climb|pillar|bridge)\\b.*")) {
                return new Step("job", "", "", text);
            }
        }
        java.util.regex.Matcher forest = java.util.regex.Pattern
                .compile("(?:chop|cut|break|mine|get) (\\d+) trees?").matcher(text);
        if (forest.find()) return new Step("job", "", "", "chop " + forest.group(1) + " trees");
        if (text.contains("tree") || text.equals("get wood") || text.contains("chop wood") || text.contains("get logs")
                || text.contains("cut wood") || text.equals("chop")) {
            return new Step("job", "", "", "chop");
        }
        java.util.regex.Matcher go = java.util.regex.Pattern
                .compile("(?:go|walk|run|move|travel)(?: to)? (-?\\d+)[ ,]+(-?\\d+)[ ,]+(-?\\d+)").matcher(text);
        if (go.find()) return new Step("job", "", "", "go " + go.group(1) + " " + go.group(2) + " " + go.group(3));
        if (text.contains("house") || text.contains("build a home") || text.contains("build a base")) {
            return new Step("job", "", "", "house");
        }
        if (text.contains("swim")) {
            java.util.regex.Matcher sea = java.util.regex.Pattern
                    .compile("(-?\\d+)[ ,]+(-?\\d+)[ ,]+(-?\\d+)").matcher(text);
            return new Step("job", "", "", sea.find()
                    ? "swim " + sea.group(1) + " " + sea.group(2) + " " + sea.group(3) : "swim");
        }
        if (text.contains("pillar") || text.contains("tower up") || text.contains("climb up")) {
            java.util.regex.Matcher count = java.util.regex.Pattern.compile("(\\d+)").matcher(text);
            return new Step("job", "", "", "climb " + (count.find() ? count.group(1) : "5"));
        }
        if (text.contains("bridge")) {
            java.util.regex.Matcher count = java.util.regex.Pattern.compile("(\\d+)").matcher(text);
            return new Step("job", "", "", "bridge " + (count.find() ? count.group(1) : "6"));
        }
        if (text.equals("come here") || text.equals("come to me") || text.equals("come")) return new Step("job", "", "", "come");
        if (text.startsWith("follow")) {
            String who = text.substring(6).replaceFirst("^\\s*(me|the nearest player)$", "").trim();
            return new Step("job", "", "", who.isEmpty() ? "follow" : "follow " + who);
        }
        if (text.contains("farm") || text.contains("harvest") || text.contains("crops")) {
            return new Step("job", "", "", "farm");
        }
        if (text.contains("collect") || text.contains("pick up") || text.contains("pickup") || text.contains("gather the drops")
                || text.contains("gather drops") || text.contains("pick the drops")) {
            return new Step("job", "", "", "collect");
        }
        if (text.contains("tunnel") || text.contains("strip mine")) {
            java.util.regex.Matcher len = java.util.regex.Pattern.compile("(\\d+)").matcher(text);
            return new Step("job", "", "", "tunnel " + (len.find() ? len.group(1) : "24"));
        }
        if (text.contains("sleep") || text.contains("go to bed") || text.contains("find a bed")) {
            return new Step("job", "", "", "sleep");
        }
        if (text.contains("till") || text.contains("hoe the") || text.contains("make farmland")) {
            java.util.regex.Matcher count = java.util.regex.Pattern.compile("(\\d+)").matcher(text);
            return new Step("job", "", "", "till " + (count.find() ? count.group(1) : "9"));
        }
        if (text.contains("replant") || text.contains("plant seed") || text.contains("plant the seeds")) {
            return new Step("job", "", "", "replant");
        }
        if ((text.contains("torch") || text.contains("light up") || text.contains("light the area"))
                && !text.startsWith("place")) {
            java.util.regex.Matcher count = java.util.regex.Pattern.compile("(\\d+)").matcher(text);
            return new Step("job", "", "", "torch " + (count.find() ? count.group(1) : "8"));
        }
        if (text.contains("quarry")) {
            java.util.regex.Matcher depth = java.util.regex.Pattern.compile("(\\d+)").matcher(text);
            return new Step("job", "", "", "quarry " + (depth.find() ? depth.group(1) : "8"));
        }
        if (text.contains("milk")) return new Step("job", "", "", "milk");
        if (text.contains("shear")) {
            java.util.regex.Matcher count = java.util.regex.Pattern.compile("(\\d+)").matcher(text);
            return new Step("job", "", "", "shear " + (count.find() ? count.group(1) : "1"));
        }
        if (text.contains("hunt") || text.contains("kill mobs") || text.contains("kill a mob")
                || text.contains("kill the mobs") || text.contains("slay")) {
            return new Step("job", "", "", "hunt");
        }
        if (text.contains("flee") || text.contains("run away") || text.contains("escape") || text.contains("get away")) {
            return new Step("job", "", "", "flee");
        }
        if (text.contains("shoot") || text.contains("fire an arrow") || text.contains("fire arrows")) {
            return new Step("job", "", "", "shoot");
        }
        if (text.matches(".*\\beat\\b.*") || text.contains("hungry")) return new Step("job", "", "", "eat");
        if (text.startsWith("drop ") || text.startsWith("throw away ") || text.startsWith("toss ")) {
            return new Step("job", "", "", text.replaceFirst("^(drop|throw away|toss) ", "drop "));
        }
        if (text.equals("return") || text.contains("come back") || text.contains("go back")) {
            return new Step("job", "", "", "return");
        }
        if (text.contains("explore") || text.contains("wander") || text.contains("walk around")) {
            java.util.regex.Matcher far = java.util.regex.Pattern.compile("(\\d+)").matcher(text);
            return new Step("job", "", "", "explore " + (far.find() ? far.group(1) : "40"));
        }
        if (text.startsWith("place ") || text.startsWith("put down ")) {
            return new Step("job", "", "", text.replaceFirst("^(place|put down) ", "place "));
        }
        java.util.regex.Matcher mine = java.util.regex.Pattern
                .compile("(?:mine|dig|look) (?:until|till|for) (?:you )?(?:find |hit )?(?:some |a |an )?([a-z_ ]+?)(?: ores?)?$").matcher(text);
        if (mine.find()) return new Step("job", "", "", "mine " + mine.group(1).trim());
        java.util.regex.Matcher many = java.util.regex.Pattern
                .compile("(?:mine|dig|get|collect) (\\d+) ([a-z_ ]+)$").matcher(text);
        if (many.find()) return new Step("job", "", "", "mine " + many.group(1) + " " + many.group(2).trim());
        if (text.equals("dig down") || text.equals("mine down") || text.equals("dig straight down")) return new Step("job", "", "", "dig down");
        if (text.equals("dig up") || text.equals("mine up")) return new Step("job", "", "", "dig up");
        return null;
    }

    private void startJob(String value) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.gameMode == null) {
            Notify.warn("Join a world first.");
            return;
        }
        if (!jobs.on()) {
            Notify.warn("Jobs are switched off in the Zeus AI Functions settings.");
            return;
        }
        String text = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (text.startsWith("job ")) text = text.substring(4).trim();
        int repeat = 1;
        java.util.regex.Matcher loop = java.util.regex.Pattern.compile("^repeat (\\d+) (.+)$").matcher(text);
        if (loop.find()) {
            repeat = Math.max(1, Math.min(20, Integer.parseInt(loop.group(1))));
            text = loop.group(2).trim();
        }
        if (text.isEmpty()) {
            Notify.info("Say what to do: chop a tree, mine until iron, go to 100 64 -200, follow me, stop.");
            return;
        }
        cancelJob(mc);
        chain.clear();
        List<String> steps = new ArrayList<>();
        for (String part : text.split("\\s*(?:,|;|->|\\band then\\b|\\bthen\\b|\\bafter that\\b)\\s*")) {
            String step = part.trim().replaceFirst("^(and|then|please|also)\\s+", "").trim();
            if (!step.isEmpty()) steps.add(step);
        }
        if (steps.isEmpty()) return;
        if (repeat > 1) {
            List<String> once = new ArrayList<>(steps);
            for (int r = 1; r < repeat; r++) steps.addAll(once);
            Notify.info("Zeus AI: repeating the plan " + repeat + " times");
        }
        String first = steps.remove(0);
        if (!startOne(first)) return;
        chain.addAll(steps);
        if (!chain.isEmpty()) {
            lastAction = "job: " + job.label() + " (+" + chain.size() + " queued)";
            Notify.info("Zeus AI: " + chain.size() + " more job(s) queued");
        }
    }

    private boolean startOne(String text) {
        Minecraft mc = Minecraft.getInstance();
        Job started;
        java.util.regex.Matcher forest = java.util.regex.Pattern
                .compile("^(?:chop|cut|break|mine|get) (\\d+) trees?$").matcher(text);
        if (forest.find()) {
            int n = Math.max(1, Math.min(64, Integer.parseInt(forest.group(1))));
            started = new Job(JobKind.CHOP, "logs", n, null);
        } else if ((text.equals("chop") || text.contains("tree") || text.contains("wood") || text.contains("log"))
                && !text.startsWith("mine ") && !text.startsWith("dig ") && !text.startsWith("get ")
                && !text.startsWith("drop") && !text.startsWith("place")) {
            started = new Job(JobKind.CHOP, "logs", 1, null);
        } else if (text.equals("come")) {
            Player nearest = nearestPlayer(mc, 64);
            Vec3 to = nearest != null ? nearest.position() : mc.player.position();
            started = new Job(JobKind.GOTO, "", 0, to);
        } else if (text.startsWith("follow")) {
            started = new Job(JobKind.FOLLOW, text.substring(6).trim(), 0, null);
        } else if (text.startsWith("go ") || text.startsWith("goto ") || text.startsWith("walk ") || text.startsWith("run to ")
                || text.startsWith("travel to ") || text.startsWith("move to ")) {
            String nums = text.replaceFirst("^(go|goto|walk|run to|travel to|move to) ", "").replaceFirst("^to ", "");
            String[] split = nums.split("[ ,]+");
            if (split.length < 3) {
                Notify.warn("Give three numbers, for example: go to 100 64 -200");
                return false;
            }
            try {
                started = new Job(JobKind.GOTO, "", 0,
                        new Vec3(Double.parseDouble(split[0]), Double.parseDouble(split[1]), Double.parseDouble(split[2])));
            } catch (NumberFormatException e) {
                Notify.warn("Those coordinates did not read as numbers.");
                return false;
            }
        } else if (text.startsWith("till") || text.contains("make farmland")) {
            java.util.regex.Matcher count = java.util.regex.Pattern.compile("(\\d+)").matcher(text);
            started = new Job(JobKind.TILL, "",
                    count.find() ? Math.max(1, Math.min(64, Integer.parseInt(count.group(1)))) : 9, null);
        } else if (text.startsWith("replant") || text.contains("plant seed")) {
            started = new Job(JobKind.REPLANT, "", 0, null);
        } else if (text.startsWith("torch") || text.contains("light up")) {
            java.util.regex.Matcher count = java.util.regex.Pattern.compile("(\\d+)").matcher(text);
            started = new Job(JobKind.TORCH, "",
                    count.find() ? Math.max(1, Math.min(64, Integer.parseInt(count.group(1)))) : 8, null);
        } else if (text.startsWith("quarry")) {
            java.util.regex.Matcher depth = java.util.regex.Pattern.compile("(\\d+)").matcher(text);
            started = new Job(JobKind.QUARRY, "",
                    depth.find() ? Math.max(1, Math.min(48, Integer.parseInt(depth.group(1)))) : 8, null);
        } else if (text.startsWith("milk")) {
            started = new Job(JobKind.MILK, "", 1, null);
        } else if (text.startsWith("shear")) {
            java.util.regex.Matcher count = java.util.regex.Pattern.compile("(\\d+)").matcher(text);
            started = new Job(JobKind.SHEAR, "",
                    count.find() ? Math.max(1, Math.min(64, Integer.parseInt(count.group(1)))) : 1, null);
        } else if (text.startsWith("hunt") || text.startsWith("kill ") || text.startsWith("slay")) {
            started = new Job(JobKind.HUNT, "", 0, null);
        } else if (text.startsWith("flee") || text.startsWith("run away") || text.equals("run")) {
            started = new Job(JobKind.FLEE, "", 0, null);
        } else if (text.startsWith("shoot")) {
            started = new Job(JobKind.SHOOT, "", 0, null);
        } else if (text.startsWith("eat")) {
            started = new Job(JobKind.EAT, "", 0, null);
        } else if (text.startsWith("drop") || text.startsWith("throw")) {
            String name = tidy(text.replaceFirst("^(drop|throw away|throw|toss) ", "")
                    .replaceFirst("^(the|some|a|an) ", "").replaceFirst("^\\d+\\s+", ""));
            if (name.isBlank()) {
                Notify.warn("Name an item to drop, for example: drop 32 dirt");
                return false;
            }
            started = new Job(JobKind.DROP, name, 0, null);
        } else if (text.startsWith("return") || text.contains("come back") || text.contains("go back")) {
            started = new Job(JobKind.RETURN, "", 0, mc.player.position());
        } else if (text.startsWith("explore") || text.startsWith("wander") || text.contains("walk around")) {
            java.util.regex.Matcher far = java.util.regex.Pattern.compile("(\\d+)").matcher(text);
            int away = far.find() ? Math.max(5, Math.min(2000, Integer.parseInt(far.group(1)))) : 40;
            float yaw = mc.player.getYRot();
            Vec3 dir = new Vec3(-Math.sin(Math.toRadians(yaw)), 0, Math.cos(Math.toRadians(yaw))).normalize();
            started = new Job(JobKind.EXPLORE, "", away, mc.player.position().add(dir.scale(away)));
        } else if (text.startsWith("house") || text.contains("build a house") || text.contains("make a house")) {
            started = new Job(JobKind.HOUSE, "", 0, null);
        } else if (text.startsWith("swim")) {
            Vec3 water = null;
            java.util.regex.Matcher sea = java.util.regex.Pattern
                    .compile("(-?\\d+)[ ,]+(-?\\d+)[ ,]+(-?\\d+)").matcher(text);
            if (sea.find()) {
                try {
                    water = new Vec3(Double.parseDouble(sea.group(1)), Double.parseDouble(sea.group(2)),
                            Double.parseDouble(sea.group(3)));
                } catch (NumberFormatException ignored) {
                }
            }
            started = new Job(JobKind.SWIM, "", 0, water);
        } else if (text.startsWith("climb") || text.startsWith("pillar")) {
            java.util.regex.Matcher count = java.util.regex.Pattern.compile("(\\d+)").matcher(text);
            started = new Job(JobKind.CLIMB, "", count.find()
                    ? Math.max(1, Math.min(64, Integer.parseInt(count.group(1)))) : 5, null);
        } else if (text.startsWith("bridge")) {
            java.util.regex.Matcher count = java.util.regex.Pattern.compile("(\\d+)").matcher(text);
            started = new Job(JobKind.BRIDGE, "", count.find()
                    ? Math.max(1, Math.min(128, Integer.parseInt(count.group(1)))) : 6, null);
        } else if (text.startsWith("place") || text.startsWith("put down")) {
            String rest = text.replaceFirst("^(place|put down) ", "").trim();
            java.util.regex.Matcher coords = java.util.regex.Pattern
                    .compile("at (-?\\d+)[ ,]+(-?\\d+)[ ,]+(-?\\d+)").matcher(rest);
            Vec3 at = null;
            if (coords.find()) {
                try {
                    at = new Vec3(Double.parseDouble(coords.group(1)), Double.parseDouble(coords.group(2)),
                            Double.parseDouble(coords.group(3)));
                } catch (NumberFormatException ignored) {
                }
                rest = rest.substring(0, coords.start()).trim();
            }
            String block = tidy(rest.replaceFirst("^(a|an|some|the) ", "")
                    .replaceFirst(" (here|there|in front of me)$", "").trim());
            if (block.isBlank()) {
                Notify.warn("Name a block to place, for example: place cobblestone here");
                return false;
            }
            started = new Job(JobKind.PLACE, block, 0, at);
        } else if (text.startsWith("farm") || text.contains("harvest") || text.contains("crop")) {
            started = new Job(JobKind.FARM, "", 0, null);
        } else if (text.startsWith("collect") || text.startsWith("pick up") || text.startsWith("pickup") || text.startsWith("gather")) {
            started = new Job(JobKind.COLLECT, "", 0, null);
        } else if (text.startsWith("tunnel") || text.startsWith("strip") || text.contains("tunnel")) {
            java.util.regex.Matcher len = java.util.regex.Pattern.compile("(\\d+)").matcher(text);
            started = new Job(JobKind.TUNNEL, "", len.find()
                    ? Math.max(1, Math.min(512, Integer.parseInt(len.group(1)))) : 24, null);
        } else if (text.startsWith("sleep") || text.contains(" bed") || text.contains("bed nearby")) {
            started = new Job(JobKind.SLEEP, "", 0, null);
        } else if (text.startsWith("dig down") || text.startsWith("mine down")) {
            started = new Job(JobKind.MINE_COUNT, "down", 30, null);
        } else if (text.startsWith("dig up") || text.startsWith("mine up")) {
            started = new Job(JobKind.MINE_COUNT, "up", 30, null);
        } else if (text.startsWith("mine ") || text.startsWith("dig ") || text.startsWith("get ")) {
            String rest = text.replaceFirst("^(mine|dig|get) ", "").replaceFirst("^(until|till|for) ", "")
                    .replace(" you find", "").replace(" find", "").replaceFirst("^(some|a|an) ", "").trim();
            java.util.regex.Matcher count = java.util.regex.Pattern.compile("^(\\d+) ([a-z_ ]+)$").matcher(rest);
            if (count.find()) {
                int n = Math.max(1, Math.min(512, Integer.parseInt(count.group(1))));
                started = new Job(JobKind.MINE_COUNT, tidy(count.group(2)), n, null);
            } else {
                started = new Job(JobKind.MINE_UNTIL, tidy(rest), 0, null);
            }
            if (started.target.isBlank()) {
                Notify.warn("Name an ore or block, for example: mine until iron");
                return false;
            }
        } else {
            Notify.info("I could not read that as a job. Try: chop that tree, mine until iron, go to 100 64 -200, follow me.");
            return false;
        }
        job = started;
        blueprint = null;
        blueprintIndex = 0;
        lastAction = "job: " + started.label();
        Notify.info("Zeus AI: " + started.label());
        return true;
    }

    private static String tidy(String word) {
        return word.replace(" ores", "").replace(" ore", "").replace("_", " ").trim();
    }

    private void cancelJob(Minecraft mc) {
        int queued = chain.size();
        chain.clear();
        if (job == null) return;
        String label = job.label();
        job = null;
        blueprint = null;
        blueprintIndex = 0;
        if (mc != null && mc.options != null) releaseKeys(mc);
        lastAction = "stopped " + label;
        Notify.info("Zeus AI stopped " + label + (queued > 0 ? " and dropped " + queued + " queued job(s)" : ""));
    }

    private void endJob(Minecraft mc, String reason) {
        finishJob(mc, reason, true);
    }

    private void giveUp(Minecraft mc, String reason) {
        finishJob(mc, reason, false);
    }

    private void finishJob(Minecraft mc, String reason, boolean done) {
        releaseKeys(mc);
        blueprint = null;
        blueprintIndex = 0;
        String label = job == null ? "job" : job.label();
        job = null;
        lastAction = label + ": " + reason;
        Notify.good("Zeus AI: " + label + " — " + reason);
        if (!done) {
            if (!chain.isEmpty()) {
                Notify.info("Zeus AI: dropped " + chain.size() + " queued job(s) after the stop");
                chain.clear();
            }
            return;
        }
        if (!chain.isEmpty()) {
            String next = chain.poll();
            Notify.info("Zeus AI: next up, " + next);
            if (!startOne(next)) chain.clear();
            lastAction = label + ": " + reason + " · now " + (job == null ? "idle" : job.label());
        }
    }

    private void tickJob(Minecraft mc) {
        if (mc.player == null || mc.level == null || mc.gameMode == null) {
            job = null;
            return;
        }
        if (mc.screen != null) {
            releaseKeys(mc);
            return;
        }
        if (!jobs.on()) {
            giveUp(mc, "jobs were switched off");
            return;
        }
        if (mc.player.getHealth() <= 6.0f) {
            giveUp(mc, "stopped, health is low");
            return;
        }
        long budget = switch (job.kind) {
            case QUARRY, TUNNEL, MINE_UNTIL -> 600_000L;
            default -> 300_000L;
        };
        if (System.currentTimeMillis() - job.started > budget) {
            giveUp(mc, "gave up after " + (budget / 60_000L) + " minutes");
            return;
        }
        switch (job.kind) {
            case CHOP -> chopTick(mc);
            case MINE_UNTIL -> mineUntilTick(mc);
            case MINE_COUNT -> mineCountTick(mc);
            case GOTO -> {
                if (job.to == null || walkTo(mc, job.to, 1.6)) endJob(mc, "arrived");
            }
            case FOLLOW -> followTick(mc);
            case COLLECT -> collectTick(mc);
            case TUNNEL -> tunnelTick(mc);
            case FARM -> farmTick(mc);
            case SLEEP -> sleepTick(mc);
            case REPLANT -> replantTick(mc);
            case EAT -> eatTick(mc);
            case HUNT -> huntTick(mc);
            case FLEE -> fleeTick(mc);
            case SHOOT -> shootTick(mc);
            case SHEAR -> shearTick(mc);
            case MILK -> milkTick(mc);
            case QUARRY -> quarryTick(mc);
            case PLACE -> placeTick(mc);
            case TORCH -> torchTick(mc);
            case DROP -> dropTick(mc);
            case RETURN -> returnTick(mc);
            case EXPLORE -> exploreTick(mc);
            case HOUSE -> houseTick(mc);
            case SWIM -> swimTick(mc);
            case CLIMB -> climbTick(mc);
            case BRIDGE -> bridgeTick(mc);
            case TILL -> tillTick(mc);
        }
    }

    private void chopTick(Minecraft mc) {
        BlockState state = job.block == null ? null : mc.level.getBlockState(job.block);
        if (job.block != null && (state == null || state.isAir() || !state.is(BlockTags.LOGS))) {
            job.mined++;
            job.block = null;
        }
        long now = System.currentTimeMillis();
        if (job.block == null && now >= job.nextSearch) {
            job.nextSearch = now + 350L;
            job.block = nearestLog(mc);
            if (job.block != null && job.limit == null) job.limit = job.block;
            if (job.block == null) {
                ItemEntity drop = nearestDrop(mc, 12);
                if (drop != null) {
                    walkTo(mc, drop.position(), 1.1);
                    if (job == null) return;
                    job.quietSince = 0;
                } else if (job.quietSince == 0) {
                    job.quietSince = now;
                } else if (now - job.quietSince > 2200L) {
                    job.trees++;
                    if (job.trees >= Math.max(1, job.wanted)) {
                        endJob(mc, job.trees + " tree(s), " + job.mined + " log(s)");
                        return;
                    }
                    job.limit = null;
                    job.quietSince = 0;
                    job.nextSearch = 0;
                    job.note = "looking for the next tree";
                }
                return;
            }
        }
        if (job.block == null) return;
        if (!mineBlock(mc, job.block)) return;
        if (job == null) return;
        job.mined++;
        job.block = null;
    }

    private void tillTick(Minecraft mc) {
        if (job.mined >= Math.max(1, job.wanted)) {
            endJob(mc, "tilled " + job.mined + " block(s)");
            return;
        }
        int hoe = Inv.hotbar(s -> s.getItem() instanceof net.minecraft.world.item.HoeItem);
        if (hoe < 0) {
            giveUp(mc, "no hoe in the hotbar");
            return;
        }
        long now = System.currentTimeMillis();
        if (now >= job.nextSearch) {
            job.nextSearch = now + 250L;
            job.block = find(mc, jobRange.i(), 2, pos -> {
                BlockState state = mc.level.getBlockState(pos);
                boolean soil = state.is(net.minecraft.world.level.block.Blocks.DIRT)
                        || state.is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK)
                        || state.is(net.minecraft.world.level.block.Blocks.DIRT_PATH);
                return soil && mc.level.getBlockState(pos.above()).isAir();
            });
        }
        if (job.block == null) {
            if (job.quietSince == 0) job.quietSince = now;
            else if (now - job.quietSince > 1500L) endJob(mc, "tilled " + job.mined + " block(s)");
            return;
        }
        job.quietSince = 0;
        BlockPos soil = job.block;
        if (Build.distTo(soil) > Build.REACH) {
            walkTo(mc, centre(soil), 2.2);
            return;
        }
        if (Inv.selected() != hoe) Inv.select(hoe);
        lookAt(mc.player, centre(soil).add(0, 1, 0));
        job.note = "tilling (" + job.mined + "/" + job.wanted + ")";
        if (Build.useOn(Build.top(soil), s -> s.getItem() instanceof net.minecraft.world.item.HoeItem, true)) {
            job.mined++;
            job.block = null;
        }
    }

    private void replantTick(Minecraft mc) {
        int slot = Inv.hotbar(s -> s.is(net.minecraft.world.item.Items.WHEAT_SEEDS)
                || s.is(net.minecraft.world.item.Items.BEETROOT_SEEDS)
                || s.is(net.minecraft.world.item.Items.CARROT)
                || s.is(net.minecraft.world.item.Items.POTATO));
        if (slot < 0) {
            giveUp(mc, "no seeds, carrots or potatoes in the hotbar");
            return;
        }
        long now = System.currentTimeMillis();
        if (now >= job.nextSearch) {
            job.nextSearch = now + 250L;
            job.block = find(mc, jobRange.i(), 3, pos -> mc.level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.FARMLAND)
                    && mc.level.getBlockState(pos.above()).isAir());
        }
        if (job.block == null) {
            if (job.quietSince == 0) job.quietSince = now;
            else if (now - job.quietSince > 1500L) endJob(mc, "planted " + job.mined + " seed(s)");
            return;
        }
        job.quietSince = 0;
        BlockPos soil = job.block;
        if (Build.distTo(soil) > Build.REACH) {
            walkTo(mc, centre(soil), 2.4);
            return;
        }
        job.note = "planting on a farmland block";
        lookAt(mc.player, centre(soil).add(0, 1, 0));
        net.minecraft.world.item.Item seedItem = Inv.get(slot).getItem();
        Inv.with(slot, () -> Build.useOn(Build.top(soil), s -> s.is(seedItem), true));
        job.mined++;
        job.block = null;
    }

    private void eatTick(Minecraft mc) {
        if (mc.player.getFoodData().getFoodLevel() >= 20 && mc.player.getFoodData().getSaturationLevel() > 0f) {
            endJob(mc, "already full");
            return;
        }
        if (mc.player.isUsingItem()) {
            job.note = "eating";
            if (mc.player.getTicksUsingItem() > 60) {
                mc.gameMode.releaseUsingItem(mc.player);
                endJob(mc, "finished eating");
            }
            return;
        }
        int slot = Inv.hotbar(s -> !s.isEmpty() && s.get(net.minecraft.core.component.DataComponents.FOOD) != null);
        if (slot < 0) {
            giveUp(mc, "no food in the hotbar");
            return;
        }
        Inv.select(slot);
        releaseKeys(mc);
        mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        job.note = "eating from the hotbar";
        if (System.currentTimeMillis() - job.started > 5000L) endJob(mc, "ate something");
    }

    private net.minecraft.world.entity.Entity nearestMonster(Minecraft mc, double radius) {
        net.minecraft.world.entity.Entity best = null;
        double bestD = radius * radius;
        for (net.minecraft.world.entity.Entity e : mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(radius))) {
            if (!(e instanceof net.minecraft.world.entity.monster.Monster) || !e.isAlive()) continue;
            double d = e.distanceToSqr(mc.player);
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        return best;
    }

    private net.minecraft.world.entity.Entity nearestEntity(Minecraft mc, double radius,
                                                           java.util.function.Predicate<net.minecraft.world.entity.Entity> match) {
        net.minecraft.world.entity.Entity best = null;
        double bestD = radius * radius;
        for (net.minecraft.world.entity.Entity e : mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(radius))) {
            if (!match.test(e)) continue;
            double d = e.distanceToSqr(mc.player);
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        return best;
    }

    private static int bestWeapon(Minecraft mc) {
        int slot = Inv.hotbar(s -> s.is(net.minecraft.world.item.Items.NETHERITE_SWORD)
                || s.is(net.minecraft.world.item.Items.DIAMOND_SWORD)
                || s.is(net.minecraft.world.item.Items.IRON_SWORD)
                || s.is(net.minecraft.world.item.Items.STONE_SWORD)
                || s.is(net.minecraft.world.item.Items.GOLDEN_SWORD)
                || s.is(net.minecraft.world.item.Items.WOODEN_SWORD));
        if (slot < 0) slot = Inv.hotbar(s -> s.is(net.minecraft.world.item.Items.NETHERITE_AXE)
                || s.is(net.minecraft.world.item.Items.DIAMOND_AXE)
                || s.is(net.minecraft.world.item.Items.IRON_AXE)
                || s.is(net.minecraft.world.item.Items.STONE_AXE)
                || s.is(net.minecraft.world.item.Items.GOLDEN_AXE)
                || s.is(net.minecraft.world.item.Items.WOODEN_AXE));
        return slot;
    }

    private void huntTick(Minecraft mc) {
        net.minecraft.world.entity.Entity target = nearestMonster(mc, 24);
        if (target == null) {
            endJob(mc, "nothing hostile is left");
            return;
        }
        int weapon = bestWeapon(mc);
        if (weapon >= 0 && Inv.selected() != weapon) Inv.select(weapon);
        lookAt(mc.player, target.getEyePosition());
        if (mc.player.distanceTo(target) > 3.0) {
            job.note = "closing in on a " + target.getName().getString();
            walkTo(mc, target.position(), 2.5);
            return;
        }
        mc.options.keyUp.setDown(false);
        mc.options.keyJump.setDown(false);
        if (mc.player.getAttackStrengthScale(0.5f) >= 0.9f) {
            mc.gameMode.attack(mc.player, target);
            mc.player.swing(InteractionHand.MAIN_HAND);
            job.mined++;
        }
        job.note = "hitting a " + target.getName().getString() + " (" + job.mined + " hits)";
    }

    private void fleeTick(Minecraft mc) {
        net.minecraft.world.entity.Entity threat = nearestMonster(mc, 20);
        if (threat == null) {
            endJob(mc, "nothing is chasing");
            return;
        }
        Vec3 away = mc.player.position().subtract(threat.position());
        if (away.lengthSqr() < 0.001) away = new Vec3(1, 0, 0);
        Vec3 to = mc.player.position().add(away.normalize().scale(14));
        mc.options.keySprint.setDown(true);
        job.note = "running from a " + threat.getName().getString();
        if (walkTo(mc, to, 1.5)) endJob(mc, "got away");
    }

    private static int countArrows(Minecraft mc) {
        int n = 0;
        for (net.minecraft.world.entity.Entity e : mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(24))) {
            if (e instanceof net.minecraft.world.entity.projectile.arrow.AbstractArrow arrow
                    && (arrow.getOwner() == null || arrow.getOwner() == mc.player)) n++;
        }
        return n;
    }

    private void shootTick(Minecraft mc) {
        int arrows = countArrows(mc);
        if (job.entityId < 0) {
            job.seen = arrows;
            job.entityId = 0;
        }
        if (arrows < job.seen) job.seen = arrows;
        if (arrows > job.seen) {
            int fresh = Math.min(Math.max(1, job.pending), arrows - job.seen);
            job.mined += fresh;
            job.pending = Math.max(0, job.pending - fresh);
            job.seen = arrows;
        }
        if (job.pending > 0 && System.currentTimeMillis() - job.actionAt > 1500L) {
            giveUp(mc, "the shot never left the bow");
            return;
        }
        if (job.wanted > 0 && job.mined >= job.wanted) {
            endJob(mc, job.mined + " shot(s) landed in the air");
            return;
        }
        net.minecraft.world.entity.Entity target = nearestMonster(mc, 32);
        if (target == null) {
            endJob(mc, job.mined > 0 ? job.mined + " shot(s) fired" : "no target to shoot at");
            return;
        }
        int slot = Inv.hotbar(s -> s.is(net.minecraft.world.item.Items.BOW) || s.is(net.minecraft.world.item.Items.CROSSBOW));
        if (slot < 0) {
            giveUp(mc, "no bow or crossbow in the hotbar");
            return;
        }
        if (Inv.selected() != slot) Inv.select(slot);
        boolean crossbow = mc.player.getMainHandItem().is(net.minecraft.world.item.Items.CROSSBOW);
        int need = crossbow ? 25 : 20;
        double flight = mc.player.distanceTo(target) / 3.0;
        lookAt(mc.player, target.getEyePosition().add(target.getDeltaMovement().scale(flight)));
        if (!mc.player.isUsingItem()) {
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            job.note = "drawing the bow";
            return;
        }
        int charge = mc.player.getTicksUsingItem();
        job.note = "drawing (" + charge + "/" + need + ")";
        if (charge >= need) {
            mc.gameMode.releaseUsingItem(mc.player);
            job.pending++;
            job.actionAt = System.currentTimeMillis();
            job.note = "released a shot";
        }
    }

    private void shearTick(Minecraft mc) {
        if (job.entityId >= 0) {
            net.minecraft.world.entity.Entity entity = mc.level.getEntity(job.entityId);
            if (entity instanceof net.minecraft.world.entity.animal.sheep.Sheep sheep) {
                if (sheep.isSheared()) {
                    job.mined++;
                    job.entityId = -1;
                    job.note = "sheared " + job.mined + " sheep";
                    if (job.mined >= Math.max(1, job.wanted)) endJob(mc, "sheared " + job.mined + " sheep");
                    return;
                }
                if (System.currentTimeMillis() - job.actionAt < 700L) return;
            }
            job.entityId = -1;
            if (++job.attempts > 3) {
                giveUp(mc, "could not shear that sheep");
                return;
            }
            return;
        }
        net.minecraft.world.entity.Entity sheep = nearestEntity(mc, 24,
                e -> e instanceof net.minecraft.world.entity.animal.sheep.Sheep s && s.isAlive() && !s.isSheared());
        if (sheep == null) {
            endJob(mc, job.mined > 0 ? "sheared " + job.mined + " sheep" : "no unshorn sheep nearby");
            return;
        }
        int slot = Inv.hotbar(s -> s.is(net.minecraft.world.item.Items.SHEARS));
        if (slot < 0) {
            giveUp(mc, "no shears in the hotbar");
            return;
        }
        if (Inv.selected() != slot) Inv.select(slot);
        if (mc.player.distanceTo(sheep) > 3.0) {
            job.note = "walking to a sheep";
            walkTo(mc, sheep.position(), 2.2);
            return;
        }
        lookAt(mc.player, sheep.getEyePosition());
        job.entityId = sheep.getId();
        job.actionAt = System.currentTimeMillis();
        job.note = "shearing a sheep";
        mc.gameMode.interact(mc.player, sheep, InteractionHand.MAIN_HAND);
    }

    private void milkTick(Minecraft mc) {
        if (job.entityId >= 0) {
            if (hasMilk(mc)) {
                job.entityId = -1;
                endJob(mc, "milked a cow");
                return;
            }
            if (System.currentTimeMillis() - job.actionAt < 700L) return;
            job.entityId = -1;
            if (++job.attempts > 3) {
                giveUp(mc, "could not milk that cow");
                return;
            }
            return;
        }
        net.minecraft.world.entity.Entity cow = nearestEntity(mc, 24,
                e -> e instanceof net.minecraft.world.entity.animal.cow.Cow c && c.isAlive() && !c.isBaby());
        if (cow == null) {
            endJob(mc, "no cow nearby");
            return;
        }
        int slot = Inv.hotbar(s -> s.is(net.minecraft.world.item.Items.BUCKET));
        if (slot < 0) {
            giveUp(mc, "no empty bucket in the hotbar");
            return;
        }
        if (Inv.selected() != slot) Inv.select(slot);
        if (mc.player.distanceTo(cow) > 3.0) {
            job.note = "walking to a cow";
            walkTo(mc, cow.position(), 2.2);
            return;
        }
        lookAt(mc.player, cow.getEyePosition());
        job.entityId = cow.getId();
        job.actionAt = System.currentTimeMillis();
        job.note = "milking a cow";
        mc.gameMode.interact(mc.player, cow, InteractionHand.MAIN_HAND);
    }

    private static boolean hasMilk(Minecraft mc) {
        java.util.function.Predicate<net.minecraft.world.item.ItemStack> milk = s -> s.is(net.minecraft.world.item.Items.MILK_BUCKET);
        return Inv.hotbar(milk) >= 0 || Inv.main(milk) >= 0 || Inv.offhand(milk);
    }

    private void quarryTick(Minecraft mc) {
        int cap = Math.max(9, job.wanted * 9);
        if (job.mined >= cap) {
            endJob(mc, job.mined + " blocks quarried");
            return;
        }
        long now = System.currentTimeMillis();
        job.note = "quarrying (" + job.mined + "/" + cap + ")";
        if (job.block != null) {
            BlockState state = mc.level.getBlockState(job.block);
            if (state.isAir() || state.canBeReplaced()) {
                job.block = null;
            } else if (mineBlock(mc, job.block)) {
                if (job == null) return;
                job.mined++;
                job.block = null;
            }
            return;
        }
        if (now < job.nextSearch) return;
        job.nextSearch = now + 80L;
        BlockPos pick = quarryTarget(mc, mc.player.blockPosition());
        if (pick == null) {
            // nothing left in reach: fall back to the block under the feet, then admit defeat
            BlockPos below = mc.player.blockPosition().below();
            BlockState under = mc.level.getBlockState(below);
            if (!under.isAir() && under.getFluidState().isEmpty()) {
                job.block = below;
                job.blocked = 0;
                return;
            }
            if (++job.blocked > 20) giveUp(mc, "nothing left to quarry here");
            return;
        }
        job.blocked = 0;
        job.block = pick;
    }

    private BlockPos quarryTarget(Minecraft mc, BlockPos anchor) {
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        int[] layers = {0, -1, -2, 1};
        for (int dy : layers) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos pos = anchor.offset(dx, dy, dz);
                    BlockState state = mc.level.getBlockState(pos);
                    if (state.isAir() || state.canBeReplaced()) continue;
                    if (!state.getFluidState().isEmpty()) continue;
                    if (state.getDestroySpeed(mc.level, pos) < 0f) continue;
                    double d = mc.player.getEyePosition().distanceTo(centre(pos));
                    if (d > jobReach.get() + 2.2) continue;
                    double score = d + Math.abs(dx) * 0.15 + Math.abs(dz) * 0.15 - (dy < 0 ? 0.3 : 0);
                    if (score < bestScore) {
                        bestScore = score;
                        best = pos;
                    }
                }
            }
        }
        return best;
    }

    private void placeTick(Minecraft mc) {
        String name = job.target;
        java.util.function.Predicate<net.minecraft.world.item.ItemStack> item = s -> named(s, name);
        if (ensureHotbar(mc, item) < 0 && !Inv.offhand(item)) {
            giveUp(mc, "no " + name + " in the inventory");
            return;
        }
        BlockPos pos = job.to == null
                ? mc.player.blockPosition().relative(facing(mc.player.getYRot()))
                : BlockPos.containing(job.to);
        if (job.block != null && job.block.equals(pos) && job.quietSince > 0) {
            if (!Build.replaceable(pos)) {
                endJob(mc, "placed " + name);
                return;
            }
            if (System.currentTimeMillis() - job.quietSince > 700L) {
                job.attempts++;
                job.quietSince = 0;
            }
        }
        job.block = pos;
        if (!Build.replaceable(pos)) {
            giveUp(mc, "there is already a block where " + name + " should go");
            return;
        }
        if (job.attempts > 5) {
            giveUp(mc, "could not get " + name + " to place there");
            return;
        }
        if (Build.distTo(pos) > Build.REACH) {
            walkTo(mc, centre(pos), 2.0);
            return;
        }
        lookAt(mc.player, centre(pos));
        if (Build.against(pos, Build.REACH) == null) {
            giveUp(mc, "nothing solid to place " + name + " against");
            return;
        }
        job.note = "placing " + name + (job.attempts > 0 ? " (try " + (job.attempts + 1) + "/6)" : "");
        if (Build.place(pos, item, true, Build.REACH)) job.quietSince = System.currentTimeMillis();
    }

    private void torchTick(Minecraft mc) {
        if (Inv.hotbar(s -> s.is(net.minecraft.world.item.Items.TORCH)) < 0) {
            giveUp(mc, "no torches in the hotbar");
            return;
        }
        long now = System.currentTimeMillis();
        if (now >= job.nextSearch) {
            job.nextSearch = now + 250L;
            job.block = find(mc, jobRange.i(), 3, pos -> Build.solid(pos)
                    && mc.level.getBlockState(pos.above()).isAir()
                    && mc.level.getMaxLocalRawBrightness(pos.above()) < 8);
        }
        if (job.block == null) {
            if (job.quietSince == 0) job.quietSince = now;
            else if (now - job.quietSince > 1500L) endJob(mc, "placed " + job.mined + " torch(es)");
            return;
        }
        job.quietSince = 0;
        BlockPos floor = job.block;
        if (Build.distTo(floor) > Build.REACH) {
            walkTo(mc, centre(floor), 2.2);
            return;
        }
        job.note = "placing a torch";
        lookAt(mc.player, centre(floor).add(0, 1, 0));
        Build.useOn(Build.top(floor), s -> s.is(net.minecraft.world.item.Items.TORCH), true);
        job.mined++;
        job.block = null;
        if (job.mined >= Math.max(1, job.wanted)) endJob(mc, "placed " + job.mined + " torch(es)");
    }

    private void dropTick(Minecraft mc) {
        String name = job.target;
        for (int i = 0; i < 36; i++) {
            net.minecraft.world.item.ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.isEmpty() || !named(stack, name)) continue;
            int count = stack.getCount();
            Inv.click(Inv.menuSlot(i), 1, net.minecraft.world.inventory.ClickType.THROW);
            job.mined += count;
            job.note = "dropped " + job.mined + " item(s)";
            return;
        }
        endJob(mc, "dropped " + job.mined + " item(s)");
    }

    private void returnTick(Minecraft mc) {
        if (job.to == null) {
            endJob(mc, "nothing to go back to");
            return;
        }
        job.note = "walking back";
        if (walkTo(mc, job.to, 2.0)) endJob(mc, "back where the job started");
    }

    private void exploreTick(Minecraft mc) {
        if (job.to == null) {
            endJob(mc, "nowhere to explore");
            return;
        }
        job.note = Math.round(mc.player.position().distanceTo(job.to)) + " blocks to go";
        if (walkTo(mc, job.to, 2.5)) endJob(mc, "explored the area");
    }

    private void houseTick(Minecraft mc) {
        if (job.target.isBlank()) {
            job.target = pickHouseMaterial(mc);
            job.note = "using " + job.target.replace('_', ' ');
        }
        java.util.function.Predicate<net.minecraft.world.item.ItemStack> material = s -> named(s, job.target);
        if (!job.phase) {
            int need = 80;
            int have = countItems(mc, material);
            if (have < need) {
                job.note = "gathering " + (need - have) + " " + job.target.replace('_', ' ')
                        + " for the house (" + have + "/" + need + ")";
                gatherFor(mc, job.target);
                return;
            }
            job.phase = true;
            if (blueprint == null) {
                BlockPos origin = mc.player.blockPosition().relative(facing(mc.player.getYRot()), 2);
                blueprint = housePlan(origin);
                blueprintIndex = 0;
                job.wanted = blueprint.size();
            }
            job.note = "materials ready, starting the build";
        }
        if (blueprint == null || blueprint.isEmpty()) {
            giveUp(mc, "lost the building plan");
            return;
        }
        if (blueprintIndex >= blueprint.size()) {
            blueprint = null;
            endJob(mc, "house finished with " + job.mined + " blocks");
            return;
        }
        BlockPos pos = blueprint.get(blueprintIndex);
        BlockState state = mc.level.getBlockState(pos);
        if (!state.isAir() && !state.canBeReplaced()) {
            blueprintIndex++;
            return;
        }
        if (countItems(mc, material) <= 0) {
            job.phase = false;
            job.note = "out of " + job.target.replace('_', ' ') + ", going to get more";
            return;
        }
        if (ensureHotbar(mc, material) < 0) {
            job.phase = false;
            return;
        }
        if (Build.distTo(pos) > Build.REACH - 0.4) {
            walkTo(mc, centre(pos), 2.0);
            return;
        }
        lookAt(mc.player, centre(pos));
        if (Build.against(pos, Build.REACH) == null) {
            blueprintIndex++;
            return;
        }
        if (Build.place(pos, material, true, Build.REACH)) {
            job.mined++;
            job.quietSince = System.currentTimeMillis();
            job.note = "building the house (" + job.mined + "/" + job.wanted + ")";
            return;
        } else if (System.currentTimeMillis() - job.quietSince > 900L) {
            job.quietSince = System.currentTimeMillis();
            blueprintIndex++;
        }
    }

    private void gatherFor(Minecraft mc, String material) {
        long now = System.currentTimeMillis();
        if (now >= job.nextSearch) {
            job.nextSearch = now + 250L;
            job.block = find(mc, 12, 5, pos -> {
                BlockState state = mc.level.getBlockState(pos);
                if (state.isAir() || !state.getFluidState().isEmpty()) return false;
                if (state.getDestroySpeed(mc.level, pos) < 0f) return false;
                String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(state.getBlock().asItem()).getPath();
                return id.equals(material) || id.contains(material);
            });
        }
        if (job.block == null) {
            ItemEntity drop = nearestDrop(mc, 8);
            if (drop != null) {
                job.quietSince = 0;
                walkTo(mc, drop.position(), 0.8);
                return;
            }
            if (job.quietSince == 0) job.quietSince = now;
            else if (now - job.quietSince > 3000L) {
                giveUp(mc, "could not find any " + material.replace('_', ' ') + " nearby");
            }
            return;
        }
        job.quietSince = 0;
        if (mineBlock(mc, job.block)) {
            if (job == null) return;
            job.block = null;
        }
    }

    private static String pickHouseMaterial(Minecraft mc) {
        // only pick materials that can be gathered in the world when we run short
        String[] gatherable = {"cobblestone", "oak_log", "stone", "dirt", "sand", "deepslate"};
        String best = "cobblestone";
        int bestCount = -1;
        for (String name : gatherable) {
            int n = countItems(mc, s -> named(s, name));
            if (n > bestCount) {
                bestCount = n;
                best = name;
            }
        }
        int planks = countItems(mc, s -> named(s, "oak_planks"));
        if (planks >= 80) return "oak_planks";
        return best;
    }

    private static java.util.List<BlockPos> housePlan(BlockPos origin) {
        java.util.List<BlockPos> plan = new java.util.ArrayList<>();
        int r = 2;
        for (int y = 0; y < 3; y++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    boolean edge = Math.abs(dx) == r || Math.abs(dz) == r;
                    if (!edge) continue;
                    if (dz == r && dx >= -1 && dx <= 1 && y < 2) continue;
                    plan.add(origin.offset(dx, y, dz));
                }
            }
        }
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) plan.add(origin.offset(dx, 3, dz));
        }
        return plan;
    }

    private void swimTick(Minecraft mc) {
        if (job.to == null) {
            BlockPos water = find(mc, 24, 4,
                    pos -> mc.level.getBlockState(pos).getFluidState().getType().is(net.minecraft.tags.FluidTags.WATER));
            if (water == null) {
                giveUp(mc, "no water nearby");
                return;
            }
            job.to = centre(water);
        }
        job.note = mc.player.isInWater() ? "swimming" : "walking to the water";
        if (walkTo(mc, job.to, 0.8)) {
            endJob(mc, mc.player.isInWater() ? "swimming at the spot" : "reached the water");
        }
    }

    private void climbTick(Minecraft mc) {
        if (job.mined >= Math.max(1, job.wanted)) {
            endJob(mc, "climbed " + job.mined + " block(s)");
            return;
        }
        if (job.target.isBlank()) job.target = pickHouseMaterial(mc);
        java.util.function.Predicate<net.minecraft.world.item.ItemStack> material = s -> named(s, job.target);
        if (ensureHotbar(mc, material) < 0) {
            giveUp(mc, "no " + job.target.replace('_', ' ') + " to climb with");
            return;
        }
        BlockPos feet = mc.player.blockPosition();
        job.note = "climbing (" + job.mined + "/" + job.wanted + ")";
        if (mc.player.onGround()) {
            mc.options.keyJump.setDown(true);
            mc.options.keyUp.setDown(false);
            return;
        }
        if (mc.player.getY() - Math.floor(mc.player.getY()) > 0.15 && Build.replaceable(feet)) {
            lookAt(mc.player, centre(feet));
            if (Build.place(feet, material, true, Build.REACH)) job.mined++;
        }
    }

    private void bridgeTick(Minecraft mc) {
        if (job.mined >= Math.max(1, job.wanted)) {
            endJob(mc, "bridged " + job.mined + " block(s)");
            return;
        }
        if (job.target.isBlank()) job.target = pickHouseMaterial(mc);
        java.util.function.Predicate<net.minecraft.world.item.ItemStack> material = s -> named(s, job.target);
        if (ensureHotbar(mc, material) < 0) {
            giveUp(mc, "no " + job.target.replace('_', ' ') + " to bridge with");
            return;
        }
        Direction dir = facing(mc.player.getYRot());
        BlockPos ahead = mc.player.blockPosition().relative(dir).below();
        if (Build.solid(ahead)) {
            mc.options.keyShift.setDown(false);
            mc.options.keyUp.setDown(true);
            job.note = "walking across the bridge";
            return;
        }
        if (!Build.replaceable(ahead)) {
            job.note = "blocked ahead";
            return;
        }
        if (Build.against(ahead, Build.REACH) == null) {
            giveUp(mc, "nothing solid to bridge from");
            return;
        }
        lookAt(mc.player, centre(ahead));
        mc.options.keyShift.setDown(true);
        mc.options.keyUp.setDown(false);
        if (Build.place(ahead, material, true, Build.REACH)) {
            job.mined++;
            job.note = "bridging (" + job.mined + "/" + job.wanted + ")";
        }
    }

    private static int ensureHotbar(Minecraft mc, java.util.function.Predicate<net.minecraft.world.item.ItemStack> p) {
        int slot = Inv.hotbar(p);
        if (slot >= 0) return slot;
        int main = Inv.main(p);
        if (main < 0) return -1;
        int target = -1;
        for (int i = 0; i < 9; i++) {
            if (Inv.get(i).isEmpty()) {
                target = i;
                break;
            }
        }
        if (target < 0) target = Inv.selected();
        Inv.click(Inv.menuSlot(main), target, net.minecraft.world.inventory.ClickType.SWAP);
        return target;
    }

    private static int countItems(Minecraft mc, java.util.function.Predicate<net.minecraft.world.item.ItemStack> p) {
        int n = 0;
        for (int i = 0; i < 36; i++) {
            net.minecraft.world.item.ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty() && p.test(stack)) n += stack.getCount();
        }
        return n;
    }

    private static boolean named(net.minecraft.world.item.ItemStack stack, String wanted) {
        if (stack.isEmpty() || wanted == null || wanted.isBlank()) return false;
        String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        String key = wanted.replace(' ', '_');
        return id.equals(key) || id.contains(key);
    }

    private BlockPos find(Minecraft mc, int radius, int height, java.util.function.Predicate<BlockPos> match) {
        BlockPos centre = mc.player.blockPosition();
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-radius, -height, -radius), centre.offset(radius, height, radius))) {
            if (!match.test(pos)) continue;
            double d = centre.distSqr(pos);
            if (d < bestD) {
                bestD = d;
                best = pos.immutable();
            }
        }
        return best;
    }

    private void collectTick(Minecraft mc) {
        long now = System.currentTimeMillis();
        if (now - job.started > 60_000L) {
            endJob(mc, "picked up what was nearby");
            return;
        }
        ItemEntity drop = nearestDrop(mc, jobRange.get() + 4);
        if (drop == null) {
            if (job.quietSince == 0) job.quietSince = now;
            else if (now - job.quietSince > 1500L) endJob(mc, "no more drops");
            return;
        }
        job.quietSince = 0;
        job.note = "walking to a drop";
        if (walkTo(mc, drop.position(), 0.7) && job != null) {
            job.mined++;
            job.quietSince = 0;
        }
    }

    private void tunnelTick(Minecraft mc) {
        if (job.mined >= job.wanted) {
            endJob(mc, job.mined + " block(s) tunnelled");
            return;
        }
        job.note = "tunnelling " + (job.wanted - job.mined) + " to go";
        tunnel(mc);
    }

    private void farmTick(Minecraft mc) {
        long now = System.currentTimeMillis();
        if (now >= job.nextSearch) {
            job.nextSearch = now + 200L;
            job.block = search(mc, mc.player.blockPosition(), jobRange.i(), 3, AiPilot::ripe);
        }
        if (job.block == null) {
            if (job.quietSince == 0) job.quietSince = now;
            else if (now - job.quietSince > 2000L) endJob(mc, "harvested " + job.mined + " crop(s)");
            return;
        }
        job.quietSince = 0;
        if (!ripe(mc.level.getBlockState(job.block))) {
            job.block = null;
            return;
        }
        job.note = "harvesting a crop";
        if (mineBlock(mc, job.block)) {
            if (job == null) return;
            job.mined++;
            job.block = null;
        }
    }

    private static boolean ripe(BlockState state) {
        return state.getBlock() instanceof net.minecraft.world.level.block.CropBlock crop && crop.isMaxAge(state);
    }

    private void sleepTick(Minecraft mc) {
        long clock = mc.level.getDayTime() % 24000L;
        if (clock < 12300L) {
            giveUp(mc, "it is daytime, no bed needed");
            return;
        }
        long now = System.currentTimeMillis();
        if (job.block == null || !mc.level.getBlockState(job.block).is(BlockTags.BEDS)) {
            if (now >= job.nextSearch) {
                job.nextSearch = now + 400L;
                job.block = search(mc, mc.player.blockPosition(), jobRange.i(), 4, s -> s.is(BlockTags.BEDS));
            }
        }
        if (job.block == null) {
            giveUp(mc, "no bed within reach");
            return;
        }
        if (!walkTo(mc, centre(job.block), 2.0)) return;
        if (job == null) return;
        Vec3 bed = centre(job.block);
        lookAt(mc.player, bed);
        mc.options.keyAttack.setDown(false);
        releaseKeys(mc);
        job.note = "clicking the bed";
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                new net.minecraft.world.phys.BlockHitResult(bed, Direction.UP, job.block, false));
        endJob(mc, "off to sleep");
    }

    private static int bestY(String ore) {
        return switch (ore) {
            case "diamond", "redstone" -> -59;
            case "iron" -> 16;
            case "gold" -> -16;
            case "lapis" -> 0;
            case "emerald" -> 96;
            case "coal" -> 96;
            case "copper" -> 48;
            case "ancient debris", "debris" -> 15;
            case "quartz" -> 32;
            case "amethyst" -> 32;
            default -> 32;
        };
    }

    private void mineUntilTick(Minecraft mc) {
        long now = System.currentTimeMillis();
        int wantY = bestY(job.target);
        double above = mc.player.getY() - wantY;
        if (Math.abs(above) > 6) {
            job.note = "digging to y " + wantY + " (" + (above > 0 ? "down" : "up") + ")";
            digVertical(mc, above > 0 ? Direction.DOWN : Direction.UP);
            return;
        }
        job.note = "tunnelling at y " + Math.round(mc.player.getY());
        BlockPos found = now >= job.nextScan ? nearestOre(mc, job.target, jobRange.i(), 20) : null;
        job.nextScan = now + 400L;
        if (found != null) {
            String name = job.target.substring(0, 1).toUpperCase(Locale.ROOT) + job.target.substring(1);
            if (jobWaypoint.on()) {
                Extras.Waypoints points = Zeus.get(Extras.Waypoints.class);
                if (points != null) points.addAt(name, found.getX() + 0.5, found.getY(), found.getZ() + 0.5);
            }
            endJob(mc, "found " + job.target + " at " + found.getX() + " " + found.getY() + " " + found.getZ());
            return;
        }
        if (now < job.nextSearch) return;
        job.nextSearch = now + 100L;
        tunnel(mc);
    }

    private void mineCountTick(Minecraft mc) {
        if (job.mined >= job.wanted) {
            endJob(mc, job.mined + " block(s) mined");
            return;
        }
        if (job.target.equals("down") || job.target.equals("up")) {
            tunnel(mc);
            return;
        }
        long now = System.currentTimeMillis();
        if (now >= job.nextSearch) {
            job.nextSearch = now + 250L;
            job.block = nearestOre(mc, job.target, 6, 6);
        }
        if (job.block != null) {
            BlockState state = mc.level.getBlockState(job.block);
            if (state.isAir()) job.block = null;
            else if (mineBlock(mc, job.block)) {
                if (job == null) return;
                job.block = null;
                job.mined++;
                return;
            } else {
                return;
            }
        }
        tunnel(mc);
    }

    private void followTick(Minecraft mc) {
        Player target = job.target.isBlank() ? nearestPlayer(mc, 48) : byName(mc, job.target);
        if (target == null) {
            if (job.quietSince == 0) job.quietSince = System.currentTimeMillis();
            else if (System.currentTimeMillis() - job.quietSince > 6000L) giveUp(mc, "lost them");
            releaseKeys(mc);
            return;
        }
        job.quietSince = 0;
        if (walkTo(mc, target.position(), 3.0)) releaseKeys(mc);
    }

    private void digVertical(Minecraft mc, Direction dir) {
        Player p = mc.player;
        BlockPos next = p.blockPosition().relative(dir);
        if (mineBlock(mc, next) && job != null) job.mined++;
    }

    private void tunnel(Minecraft mc) {
        Player p = mc.player;
        Direction dir;
        if (job.target.equals("down")) dir = Direction.DOWN;
        else if (job.target.equals("up")) dir = Direction.UP;
        else dir = facing(p.getYRot());
        if (dir == Direction.DOWN || dir == Direction.UP) {
            BlockPos next = p.blockPosition().relative(dir);
            if (mineBlock(mc, next) && job != null) job.mined++;
            return;
        }
        BlockPos feet = p.blockPosition();
        BlockPos ahead = feet.relative(dir);
        BlockPos head = ahead.above();
        if (!open(mc, ahead)) {
            if (mineBlock(mc, ahead) && job != null) job.mined++;
            return;
        }
        if (!open(mc, head)) {
            if (mineBlock(mc, head) && job != null) job.mined++;
            return;
        }
        walkTo(mc, centre(ahead), 0.7);
    }

    private boolean mineBlock(Minecraft mc, BlockPos pos) {
        BlockState state = mc.level.getBlockState(pos);
        if (state.isAir()) return true;
        if (!state.getFluidState().isEmpty()) {
            giveUp(mc, "stopped at water or lava");
            return false;
        }
        Player p = mc.player;
        Vec3 c = centre(pos);
        if (p.getEyePosition().distanceTo(c) > jobReach.get()) {
            walkTo(mc, c, Math.max(1.2, jobReach.get() - 0.6));
            return false;
        }
        mc.options.keyUp.setDown(false);
        mc.options.keyJump.setDown(false);
        lookAt(p, c);
        Vec3 eye = p.getEyePosition();
        double rx = eye.x - c.x, ry = eye.y - c.y, rz = eye.z - c.z;
        Direction face = Math.abs(ry) >= Math.abs(rx) && Math.abs(ry) >= Math.abs(rz)
                ? (ry >= 0 ? Direction.UP : Direction.DOWN)
                : (Math.abs(rx) >= Math.abs(rz) ? (rx >= 0 ? Direction.EAST : Direction.WEST)
                : (rz >= 0 ? Direction.SOUTH : Direction.NORTH));
        // Holding attack matters: with it released, Minecraft calls stopDestroyBlock every tick
        // and the block never accumulates break progress. Vanilla then finishes the break for us.
        mc.options.keyAttack.setDown(true);
        if (mining == null || !mining.equals(pos) || !mc.gameMode.isDestroying()) {
            mining = pos;
            mc.gameMode.startDestroyBlock(pos, face);
        }
        mc.gameMode.continueDestroyBlock(pos, face);
        p.swing(InteractionHand.MAIN_HAND);
        if (!mc.level.getBlockState(pos).isAir()) return false;
        releaseAttack(mc);
        job.note = "mined " + pos.getX() + " " + pos.getY() + " " + pos.getZ();
        return true;
    }

    private static void lookAt(Player p, Vec3 c) {
        double dx = c.x - p.getX(), dz = c.z - p.getZ();
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        double flat = Math.sqrt(dx * dx + dz * dz);
        float pitch = (float) -Math.toDegrees(Math.atan2(c.y - p.getEyeY(), flat));
        p.setYRot(yaw);
        p.setYHeadRot(yaw);
        p.setXRot(Math.max(-90f, Math.min(90f, pitch)));
    }

    private boolean walkTo(Minecraft mc, Vec3 target, double stop) {
        Player p = mc.player;
        if (mc.level == null) return false;
        double dx = target.x - p.getX(), dz = target.z - p.getZ();
        double flat = Math.sqrt(dx * dx + dz * dz);
        if (flat <= stop) {
            releaseKeys(mc);
            if (job != null) {
                job.lastTarget = null;
                job.blocked = 0;
                job.bestDist = 0;
                job.detourUntil = 0;
            }
            return true;
        }
        if (job == null) return false;
        long now = System.currentTimeMillis();
        if (job.lastTarget == null || job.lastTarget.distanceToSqr(target) > 9.0) {
            job.lastTarget = target;
            job.lastCheck = now;
            job.lastX = p.getX();
            job.lastZ = p.getZ();
            job.blocked = 0;
            job.bestDist = 0;
            job.detourUntil = 0;
            job.pathRetries = 0;
        }
        float base = (float) Math.toDegrees(Math.atan2(-dx, dz));
        if (job.bestDist == 0 || flat < job.bestDist - 0.5) {
            job.bestDist = flat;
            job.stallSince = now;
        } else if (now - job.stallSince > 6000L) {
            job.stallSince = now;
            if (++job.pathRetries <= 3) {
                job.detourYaw = base + (job.detourSide ? 95f : -95f);
                job.detourSide = !job.detourSide;
                job.detourUntil = now + 1400L;
                job.note = "finding a way around the obstacle";
            } else {
                releaseKeys(mc);
                giveUp(mc, "could not find a way through");
                return false;
            }
        }
        if (now - job.lastCheck >= 500L) {
            double moved = Math.hypot(p.getX() - job.lastX, p.getZ() - job.lastZ);
            job.lastX = p.getX();
            job.lastZ = p.getZ();
            job.lastCheck = now;
            if (moved < 0.35) job.blocked++;
            else job.blocked = Math.max(0, job.blocked - 1);
            if (job.blocked >= 6) {
                if (job.blocked == 6) {
                    job.detourYaw = base + (job.detourSide ? 90f : -90f);
                    job.detourSide = !job.detourSide;
                    job.detourUntil = now + 1200L;
                    job.note = "working around something in the way";
                } else if (job.blocked >= 14) {
                    releaseKeys(mc);
                    giveUp(mc, "stuck, giving up");
                    return false;
                }
            }
        }
        if (p.isInLava()) {
            giveUp(mc, "stepped into lava");
            return false;
        }
        if (p.isInWater()) {
            p.setYRot(base);
            p.setYHeadRot(base);
            mc.options.keyUp.setDown(true);
            boolean up = p.isUnderWater() || target.y > p.getY() + 0.6;
            mc.options.keyJump.setDown(up);
            mc.options.keySprint.setDown(true);
            job.note = "swimming";
            return false;
        }
        boolean detour = now < job.detourUntil;
        boolean blockedAhead = !detour && !passable(mc, base);
        boolean bump = p.horizontalCollision;
        if ((blockedAhead || bump) && jobDig.on() && now >= job.actionAt) {
            job.actionAt = now + 300L;
            if (digThrough(mc, base)) {
                job.note = "digging through an obstacle";
                return false;
            }
        }
        float yaw = detour ? job.detourYaw : base;
        if ((blockedAhead || bump) && !detour) {
            job.detourYaw = pickDetour(mc, base);
            job.detourUntil = now + 700L + (long) (Math.random() * 250.0);
            job.note = "stepping around an obstacle";
            yaw = job.detourYaw;
        }
        p.setYRot(yaw);
        p.setYHeadRot(yaw);
        mc.options.keyUp.setDown(true);
        mc.options.keyJump.setDown((blockedAhead || bump) && p.onGround());
        return false;
    }

    private float pickDetour(Minecraft mc, float base) {
        float left = base - 60f;
        float right = base + 60f;
        boolean l = passable(mc, left);
        boolean r = passable(mc, right);
        if (l && !r) return left;
        if (r && !l) return right;
        if (l) {
            job.detourSide = !job.detourSide;
            return job.detourSide ? right : left;
        }
        return base + 180f;
    }

    private static boolean passable(Minecraft mc, float yaw) {
        if (mc.level == null || mc.player == null) return true;
        Direction dir = facing(yaw);
        BlockPos feet = mc.player.blockPosition().relative(dir);
        BlockState a = mc.level.getBlockState(feet);
        BlockState b = mc.level.getBlockState(feet.above());
        if (a.getFluidState().getType().is(net.minecraft.tags.FluidTags.LAVA)
                || b.getFluidState().getType().is(net.minecraft.tags.FluidTags.LAVA)) return false;
        if (!(a.isAir() || a.canBeReplaced() || !a.getFluidState().isEmpty())) return false;
        if (!(b.isAir() || b.canBeReplaced() || !b.getFluidState().isEmpty())) return false;
        int drop = 0;
        BlockPos below = feet.below();
        while (drop < 4 && (mc.level.getBlockState(below).isAir() || !mc.level.getBlockState(below).getFluidState().isEmpty())) {
            below = below.below();
            drop++;
        }
        BlockState landing = mc.level.getBlockState(below);
        return drop < 3 && !landing.getFluidState().getType().is(net.minecraft.tags.FluidTags.LAVA);
    }

    private boolean digThrough(Minecraft mc, float yaw) {
        Player p = mc.player;
        Direction dir = facing(yaw);
        BlockPos feet = p.blockPosition();
        for (int dy = 0; dy <= 1; dy++) {
            BlockPos pos = feet.relative(dir).above(dy);
            BlockState state = mc.level.getBlockState(pos);
            if (state.isAir() || state.canBeReplaced()) continue;
            if (!state.getFluidState().isEmpty()) return false;
            if (Build.distTo(pos) > jobReach.get() - 0.2) return false;
            if (state.getDestroySpeed(mc.level, pos) < 0f) return false;
            mineBlock(mc, pos);
            return true;
        }
        return false;
    }

    private static void releaseKeys(Minecraft mc) {
        if (mc.options == null) return;
        mc.options.keyUp.setDown(false);
        mc.options.keyJump.setDown(false);
        mc.options.keySprint.setDown(false);
        mc.options.keyShift.setDown(false);
        releaseAttack(mc);
    }

    private static void releaseAttack(Minecraft mc) {
        if (mc.options == null) return;
        mc.options.keyAttack.setDown(false);
        mining = null;
        if (mc.gameMode != null && mc.player != null && mc.gameMode.isDestroying()) mc.gameMode.stopDestroyBlock();
    }

    private BlockPos nearestLog(Minecraft mc) {
        BlockPos centre = job.limit != null ? job.limit : mc.player.blockPosition();
        int radius = job.limit != null ? 6 : jobRange.i();
        return search(mc, centre, radius, job.limit != null ? 12 : 16, state -> state.is(BlockTags.LOGS));
    }

    private BlockPos nearestOre(Minecraft mc, String wanted, int radius, int height) {
        return search(mc, mc.player.blockPosition(), radius, height, state -> matches(state, wanted));
    }

    private static boolean matches(BlockState state, String wanted) {
        if (wanted == null || wanted.isBlank()) return false;
        String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        String key = wanted.replace(' ', '_');
        return id.equals(key) || id.equals(key + "_ore") || id.contains(key) && id.endsWith("_ore");
    }

    private BlockPos search(Minecraft mc, BlockPos centre, int radius, int height, java.util.function.Predicate<BlockState> match) {
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-radius, -height, -radius), centre.offset(radius, height, radius))) {
            BlockState state = mc.level.getBlockState(pos);
            if (!match.test(state)) continue;
            double d = centre.distSqr(pos);
            if (d < bestD) {
                bestD = d;
                best = pos.immutable();
            }
        }
        return best;
    }

    private ItemEntity nearestDrop(Minecraft mc, double radius) {
        ItemEntity best = null;
        double bestD = radius * radius;
        for (net.minecraft.world.entity.Entity e : mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(radius))) {
            if (!(e instanceof ItemEntity item)) continue;
            double d = item.distanceToSqr(mc.player);
            if (d < bestD) {
                bestD = d;
                best = item;
            }
        }
        return best;
    }

    private static Player nearestPlayer(Minecraft mc, double radius) {
        Player best = null;
        double bestD = radius * radius;
        for (Player other : mc.level.players()) {
            if (other == mc.player || !other.isAlive()) continue;
            double d = other.distanceToSqr(mc.player);
            if (d < bestD) {
                bestD = d;
                best = other;
            }
        }
        return best;
    }

    private static Player byName(Minecraft mc, String name) {
        for (Player other : mc.level.players()) {
            if (other != mc.player && other.getName().getString().equalsIgnoreCase(name)) return other;
        }
        return nearestPlayer(mc, 48);
    }

    private static boolean open(Minecraft mc, BlockPos pos) {
        BlockState state = mc.level.getBlockState(pos);
        return state.isAir() || state.canBeReplaced();
    }

    private static Vec3 centre(BlockPos pos) {
        return new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    private static Direction facing(float yaw) {
        float y = ((yaw % 360f) + 360f) % 360f;
        if (y >= 315f || y < 45f) return Direction.SOUTH;
        if (y < 135f) return Direction.WEST;
        if (y < 225f) return Direction.NORTH;
        return Direction.EAST;
    }

    private void help() {
        Notify.info("Try: on sprint, off esp, set killaura range to 4, preset pvp, theme violet, undo, status");
        Notify.info("Jobs: chop that tree, mine until you find iron, mine 20 stone, go to 120 64 -300, come here, follow me, dig down, stop");
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
        Step work = jobStep(part);
        if (work != null) return work;
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
