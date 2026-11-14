package com.zlorbie788.zeus.mods;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zlorbie788.zeus.core.Build;
import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Craft;
import com.zlorbie788.zeus.core.Inv;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Notify;
import com.zlorbie788.zeus.core.Setting;
import com.zlorbie788.zeus.core.Zeus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
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
    private static final int HISTORY_MAX = 10;
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
    // Provider quirks learned at run time: newer OpenAI models want max_completion_tokens and only
    // their default temperature; some local servers reject response_format.  Each flag is set the
    // first time a provider says so, and the request is sent again without the offending field.
    private static volatile boolean completionTokens;
    private static volatile boolean noTemperature;
    private static volatile String lastTest = "";
    private static volatile boolean lastTestOk;
    private static volatile long lastTestAt;
    private static final java.util.concurrent.atomic.AtomicInteger PLANS = new java.util.concurrent.atomic.AtomicInteger();
    private static volatile boolean paused;
    private static volatile String lastAsked = "";
    private static volatile String waiting = "";
    private static volatile long lastLatency;
    static final java.util.concurrent.atomic.AtomicLong TOKENS = new java.util.concurrent.atomic.AtomicLong();
    private static volatile String followPrompt = "";
    private static volatile long followAt;
    private static volatile int followUps;

    private final Setting.Bool jobs = add(new Setting.Bool("Do jobs in the world", "Lets the AI chop, mine, quarry, tunnel, farm, replant, collect drops, fight, flee, shoot, shear, milk, eat, place blocks, craft at a grid or a crafting table it places itself, smelt in a furnace, build a house (gathering the materials first), swim, climb, bridge, light the area with torches, drop items, sleep, explore, walk to coordinates through obstacles and follow players.", true));
    private final Setting.Num jobRange = add(new Setting.Num("Job search range", "How far a job looks for trees, ore and drops.", 16, 6, 40, 2, " blocks")).showIf(() -> jobs.on());
    private final Setting.Num jobReach = add(new Setting.Num("Job mining reach", "How far the AI reaches while breaking a job block.", 4.3, 3.0, 4.5, 0.1, " blocks")).showIf(() -> jobs.on());
    private final Setting.Bool jobWaypoint = add(new Setting.Bool("Waypoint what it finds", "When a mining job finds the ore it was looking for, drop a waypoint on it.", true)).showIf(() -> jobs.on());
    private final Setting.Bool jobDig = add(new Setting.Bool("Dig through obstacles", "Lets the AI break a block that blocks the path instead of only stepping around it.", true)).showIf(() -> jobs.on());
    private final Setting.Info jobStatus = add(new Setting.Info("Job", this::jobLines));

    private enum JobKind { CHOP, MINE_UNTIL, MINE_COUNT, GOTO, FOLLOW, COLLECT, TUNNEL, FARM, SLEEP,
                           REPLANT, EAT, FOOD, FISH, HUNT, FLEE, SHOOT, SHEAR, MILK, QUARRY, PLACE, TORCH, DROP, RETURN, EXPLORE,
                           HOUSE, SWIM, CLIMB, BRIDGE, TILL, CRAFT, SMELT, STORE, TAKE, SCHEMATIC,
                           BREED, PLANT, GUARD, ARMOR }

    private static final class Job {
        final JobKind kind;
        String target;
        int wanted;
        Vec3 to;
        int mined;
        int trees;
        long started = System.currentTimeMillis();
        long nextSearch;
        long healUntil;
        int baseline = -1;
        boolean toolChecked;
        long dropUntil;
        long lastFound;
        Direction stairDir;
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
        boolean vertical;
        long verticalAt;
        BlockPos ore;
        long lightAt;
        int crafts;
        int step;
        long biteAt;
        int toolTried;
        int progressMark;
        long progressAt;
        java.util.List<Vec3> path;
        Vec3 pathFor;
        long pathAt;
        int pathIndex;

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
                case MINE_COUNT -> "mining " + target.replace('_', ' ') + " (" + Math.min(crafts > 0 ? crafts : mined, wanted) + "/" + wanted + ")";
                case GOTO -> "walking to " + Math.round(to.x) + " " + Math.round(to.y) + " " + Math.round(to.z);
                case FOLLOW -> "following " + (target.isBlank() ? "the nearest player" : target);
                case COLLECT -> "picking up drops" + (mined > 0 ? " (" + mined + ")" : "");
                case TUNNEL -> "tunnelling (" + Math.min(mined, wanted) + "/" + wanted + ")";
                case FARM -> "harvesting crops" + (mined > 0 ? " (" + mined + " harvested)" : "");
                case SLEEP -> "looking for a bed";
                case REPLANT -> "planting seeds" + (mined > 0 ? " (" + mined + " planted)" : "");
                case EAT -> "eating";
                case FOOD -> "hunting animals for food" + (mined > 0 ? " (" + mined + " killed)" : "");
                case FISH -> "fishing (" + Math.min(mined, Math.max(1, wanted)) + "/" + Math.max(1, wanted) + ")";
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
                case BREED -> "breeding animals (" + mined + " fed)";
                case PLANT -> "planting saplings (" + mined + "/" + Math.max(1, wanted) + ")";
                case GUARD -> "guarding " + (target.isBlank() ? "you" : target) + (mined > 0 ? " (" + mined + " hits)" : "");
                case ARMOR -> "putting on the best armour";
                case SCHEMATIC -> (phase ? "building " + target + " (" + mined + "/" + Math.max(1, wanted) + ")"
                        : "getting materials for " + target);
                case HOUSE -> (phase ? "building a house (" + mined + " blocks)"
                        : "gathering materials for a house (" + target.replace('_', ' ') + ")");
                case SWIM -> "swimming";
                case CLIMB -> "towering up (" + Math.min(mined, Math.max(1, wanted)) + "/" + Math.max(1, wanted) + ")";
                case BRIDGE -> "bridging (" + Math.min(mined, Math.max(1, wanted)) + "/" + Math.max(1, wanted) + ")";
                case TILL -> "tilling the ground (" + Math.min(mined, Math.max(1, wanted)) + "/" + Math.max(1, wanted) + ")";
                case CRAFT -> "crafting " + target.replace('_', ' ') + " (" + crafts + "/" + Math.max(1, wanted) + ")";
                case SMELT -> "smelting " + target.replace('_', ' ') + " (" + crafts + "/" + Math.max(1, wanted) + ")";
                case STORE -> "putting " + target.replace('_', ' ') + " away (" + mined + ")";
                case TAKE -> "taking " + target.replace('_', ' ') + " out (" + mined + ")";
            };
        }
    }

    private Job job;
    private static BlockPos mining;
    private static long mineTick = -1;
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
    private final Setting.Bool autoTool = add(new Setting.Bool("Auto tool", "Jobs always hold the best tool or weapon for what they hit, swap out tools that are about to break, and make an axe, pickaxe or shovel first when a long job needs one and the materials are there.", true));
    private final Setting.Text prefix = add(new Setting.Text("Chat prefix", "Start a normal chat message with this and it goes to Zeus AI instead of the server, for example: @mine 10 iron. Leave empty to turn it off.", "@", 4));
    private final Setting.Bool chatHud = add(new Setting.Bool("Chat window", "A small chat window on screen for you and Zeus AI: your /zeus ai messages on the right, its answers on the left.", false));
    private final Setting.Choice chatCorner = add(new Setting.Choice("Chat window place", "Which corner the chat window sits in.", 0, "Bottom right", "Top right", "Top left", "Middle left")).showIf(() -> chatHud.on());
    private final Setting.Num chatSize = add(new Setting.Num("Chat window size", "How big the chat window is.", 1.0, 0.6, 1.6, 0.1, "x")).showIf(() -> chatHud.on());
    private final Setting.Bool chatFade = add(new Setting.Bool("Chat window hides when quiet", "Fades the chat window out 30 seconds after the last message. Off: it stays.", true)).showIf(() -> chatHud.on());
    private final Setting.Bool shareChat = add(new Setting.Bool("Share recent chat", "Sends the last few chat lines with each request, so you can say reply to him or what did they say. Off by default because it sends other players' messages to your AI provider.", false));
    private final Setting.Bool selfHeal = add(new Setting.Bool("Retry what failed", "When an action did not take effect, asks the model one more time with the results so it can correct itself.", true));
    private final Setting.Info status = add(new Setting.Info("Status", this::lines));

    public AiPilot() {
        super("aipilot", "Zeus AI Functions", "Type /zeus ai <what you want>. It switches modules and changes settings, and it works in the world too: chop trees, mine, quarry, tunnel, farm, fight mobs, shoot, shear, milk, eat, place blocks, light the area, drop items, sleep, explore, walk or follow, hunt animals for meat, and make the pickaxe it needs before mining. Say stop to end a job.", Category.UTILITY);
        anywhere();
        alias("ai", "assistant", "pilot", "zeus ai", "functions", "jobs");
        try {
            // "@mine 10 iron" in plain chat goes to Zeus AI and never reaches the server
            net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents.ALLOW_CHAT.register(message -> {
                String pre = prefix.get() == null ? "" : prefix.get().trim();
                if (pre.isEmpty() || !enabled() || message == null || !message.startsWith(pre)) return true;
                String rest = message.substring(pre.length()).trim();
                if (rest.isEmpty()) return true;
                Minecraft.getInstance().execute(() -> {
                    AiTutorial.echo(rest);
                    String said = ask(rest);
                    if (said != null && !said.startsWith("Thinking")) AiTutorial.chat(said, false);
                });
                return false;
            });
            net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.CHAT.register((message, signed, sender, params, time) -> hearChat(message.getString()));
            net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
                if (!overlay) hearChat(message.getString());
            });
        } catch (Throwable ignored) {
        }
    }

    private static final java.util.Deque<String> CHAT = new ArrayDeque<>();

    private static void hearChat(String line) {
        if (line == null || line.isBlank() || line.startsWith("[Zeus")) return;
        synchronized (CHAT) {
            CHAT.addLast(line.length() > 140 ? line.substring(0, 140) : line);
            while (CHAT.size() > 8) CHAT.removeFirst();
        }
    }

    /** The conversation for the chat window: who said it, what, when. */
    public record Line(boolean you, String text, long at) {}

    private static final java.util.Deque<Line> TALK = new ArrayDeque<>();

    public static void talk(boolean you, String text) {
        if (text == null || text.isBlank()) return;
        synchronized (TALK) {
            Line last = TALK.peekLast();
            if (last != null && last.you() == you && last.text().equals(text)) return;
            TALK.addLast(new Line(you, text.length() > 240 ? text.substring(0, 240) + "…" : text, System.currentTimeMillis()));
            while (TALK.size() > 30) TALK.removeFirst();
        }
    }

    @Override
    protected void onHud(net.minecraft.client.gui.GuiGraphics g, Minecraft mc, float partial, int w, int h) {
        try {
            drawChat(g, mc, w, h);
        } catch (Throwable ignored) {
        }
    }

    private void drawChat(net.minecraft.client.gui.GuiGraphics g, Minecraft mc, int w, int h) {
        if (!chatHud.on() || mc.options.hideGui) return;
        java.util.List<Line> lines;
        synchronized (TALK) {
            lines = new ArrayList<>(TALK);
        }
        long now = System.currentTimeMillis();
        float alpha = 1f;
        if (chatFade.on()) {
            long quiet = lines.isEmpty() ? Long.MAX_VALUE : now - lines.get(lines.size() - 1).at();
            if (busy || job != null) quiet = 0;
            alpha = quiet < 30_000 ? 1f : Math.max(0f, 1f - (quiet - 30_000) / 1500f);
            if (alpha <= 0.01f) return;
        }
        float sc = (float) chatSize.get();
        int pw = Math.round(170 * sc), maxText = pw - 26;
        // lay out bubbles from the newest up until the window is full
        int maxH = Math.round(150 * sc);
        java.util.List<Object[]> bubbles = new ArrayList<>();
        int used = 0;
        for (int i = lines.size() - 1; i >= 0 && used < maxH; i--) {
            Line l = lines.get(i);
            java.util.List<String> wrapped = wrap(l.text(), maxText);
            int bh = wrapped.size() * 9 + 6;
            if (used + bh + 4 > maxH && !bubbles.isEmpty()) break;
            bubbles.add(0, new Object[] {l, wrapped, bh});
            used += bh + 4;
        }
        String status = busy ? "thinking…" : job != null ? shorten(job.label()) : hasKey() ? "ready" : "no key: /zeus ai tutorial";
        int ph = used + 22;
        int corner = chatCorner.index();
        int x = corner == 0 || corner == 1 ? w - pw - 6 : 6;
        int y = corner == 0 ? h - ph - 52 : corner == 3 ? (h - ph) / 2 : 6;
        int a = Math.round(alpha * 255);
        g.pose().pushMatrix();
        com.zlorbie788.zeus.ui.Draw.rrect(g, x, y, pw, ph, 6, (Math.round(0.72f * a) << 24) | 0x070B16);
        com.zlorbie788.zeus.ui.Draw.rrectLine(g, x, y, pw, ph, 6, (Math.round(0.6f * a) << 24) | 0x3A6ACA);
        com.zlorbie788.zeus.ui.Draw.text(g, "ZEUS AI", x + 7, y + 5, (a << 24) | 0x8CC4FF);
        com.zlorbie788.zeus.ui.Draw.textRight(g, status, x + pw - 7, y + 5, (Math.round(0.7f * a) << 24) | (busy ? 0xFFD166 : 0x9FB2D8));
        int by = y + 17;
        if (bubbles.isEmpty()) {
            com.zlorbie788.zeus.ui.Draw.text(g, "Type /zeus ai <request>", x + 7, by + 2, (Math.round(0.6f * a) << 24) | 0xFFFFFF);
        }
        for (Object[] b : bubbles) {
            Line l = (Line) b[0];
            @SuppressWarnings("unchecked") java.util.List<String> wrapped = (java.util.List<String>) b[1];
            int bh = (int) b[2];
            int tw = 0;
            for (String s2 : wrapped) tw = Math.max(tw, com.zlorbie788.zeus.ui.Draw.width(s2));
            int bw = tw + 10;
            int bx = l.you() ? x + pw - 7 - bw : x + 7;
            int fill = l.you() ? 0x2A5CC8 : 0x1C2436;
            com.zlorbie788.zeus.ui.Draw.rrect(g, bx, by, bw, bh, 4, (Math.round(0.9f * a) << 24) | fill);
            int ty = by + 3;
            for (String s2 : wrapped) {
                com.zlorbie788.zeus.ui.Draw.text(g, s2, bx + 5, ty, (a << 24) | (l.you() ? 0xFFFFFF : 0xE6ECF8));
                ty += 9;
            }
            by += bh + 4;
        }
        g.pose().popMatrix();
    }

    private static java.util.List<String> wrap(String text, int width) {
        java.util.List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String next = line.length() == 0 ? word : line + " " + word;
            if (com.zlorbie788.zeus.ui.Draw.width(next) > width && line.length() > 0) {
                out.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(next);
            }
            if (out.size() >= 6) break;
        }
        if (line.length() > 0 && out.size() < 7) out.add(line.toString());
        return out;
    }

    /** What happened in the game lately (jobs finished or failed, things found), newest last. */
    private static final java.util.Deque<String> EVENTS = new ArrayDeque<>();

    static void event(String text) {
        if (text == null || text.isBlank()) return;
        synchronized (EVENTS) {
            String at = new java.text.SimpleDateFormat("HH:mm").format(new java.util.Date());
            EVENTS.addLast(at + " " + text);
            while (EVENTS.size() > 8) EVENTS.removeFirst();
        }
    }

    /** Pending reminders: due time and text. */
    private static final List<Object[]> REMINDERS = new ArrayList<>();

    public static AiPilot find() {
        return Zeus.get(AiPilot.class);
    }

    public static void key(String value) {
        AiPilot self = find();
        if (self == null) return;
        String clean = value == null ? "" : value.trim();
        if (clean.startsWith("\"") && clean.endsWith("\"") && clean.length() > 1) clean = clean.substring(1, clean.length() - 1).trim();
        if (clean.toLowerCase(Locale.ROOT).startsWith("bearer ")) clean = clean.substring(7).trim();
        self.token.set(clean);
        completionTokens = false;
        noTemperature = false;
        noJsonMode = false;
        lastTestOk = false;
        lastTest = "";
        Provider picked = clean.isBlank() ? null : self.provider();
        Zeus.toast(clean.isBlank() ? "AI key cleared" : "AI key saved", true);
        if (!clean.isBlank()) {
            Provider now = currentProvider();
            Notify.good("Key saved (" + mask(clean) + ")" + (picked != null ? ", recognised as " + picked.label() + ": model " + self.model.get()
                    : now != null ? " for " + now.label() : "") + ". Check it with /zeus ai test");
            AiTutorial.onKey();
        }
    }

    /** First and last characters only, so a key never shows in full in chat or screenshots. */
    public static String mask(String secret) {
        if (secret == null || secret.length() < 10) return "****";
        return secret.substring(0, 4) + "…" + secret.substring(secret.length() - 4);
    }

    /** Recognise the provider from the key's prefix and point the endpoint at it, unless a custom endpoint is set. */
    private Provider provider() {
        String secret = token.get();
        if (secret == null) return null;
        String id = secret.startsWith("gsk_") ? "groq"
                : secret.startsWith("sk-or-") ? "openrouter"
                : secret.startsWith("AIza") ? "gemini"
                : secret.startsWith("sk-proj-") || secret.startsWith("sk-svcacct-") ? "openai"
                : null;
        if (id == null) return null;
        Provider target = providerById(id);
        Provider now = currentProvider();
        boolean custom = now == null && endpoint.get() != null && !endpoint.get().isBlank()
                && !endpoint.get().contains("api.openai.com");
        if (target == null || custom || now == target) return now == target ? target : null;
        endpoint.set(target.url());
        model.set(target.model());
        return target;
    }

    /** True when the AI can be asked: a key is saved, or the endpoint is a local server that needs none. */
    public static boolean hasKey() {
        AiPilot self = find();
        if (self == null) return false;
        if (self.token.get() != null && !self.token.get().isBlank()) return true;
        return local(self.url());
    }

    /** True when a real key is saved (as opposed to a keyless local server). */
    public static boolean hasSecret() {
        AiPilot self = find();
        return self != null && self.token.get() != null && !self.token.get().isBlank();
    }

    /** The endpoint as typed, completed to a chat completions URL when only a base was given. */
    String url() {
        String raw = endpoint.get() == null ? "" : endpoint.get().trim();
        if (raw.isEmpty()) return "https://api.openai.com/v1/chat/completions";
        if (!raw.startsWith("http://") && !raw.startsWith("https://")) {
            raw = (raw.startsWith("localhost") || raw.startsWith("127.") ? "http://" : "https://") + raw;
        }
        while (raw.endsWith("/")) raw = raw.substring(0, raw.length() - 1);
        if (raw.endsWith("/chat/completions")) return raw;
        if (raw.endsWith("/v1") || raw.endsWith("/openai") || raw.endsWith("/v1beta/openai") || raw.endsWith("/api/v1")) {
            return raw + "/chat/completions";
        }
        if (raw.matches("https?://[^/]+")) return raw + "/v1/chat/completions";
        return raw;
    }

    /** A server on this computer or the home network: no key is needed and none is sent. */
    static boolean local(String url) {
        try {
            String host = URI.create(url).getHost();
            if (host == null) return false;
            host = host.toLowerCase(Locale.ROOT);
            return host.equals("localhost") || host.startsWith("127.") || host.equals("0.0.0.0") || host.equals("::1")
                    || host.startsWith("192.168.") || host.startsWith("10.") || host.endsWith(".local")
                    || host.equals("host.docker.internal");
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Provider presets for /zeus ai provider: endpoint, a sensible default model, and where keys come from. */
    public record Provider(String id, String label, String url, String model, String keys, boolean free, boolean keyless) {}

    public static final List<Provider> PROVIDERS = List.of(
            new Provider("groq", "Groq", "https://api.groq.com/openai/v1/chat/completions", "llama-3.3-70b-versatile",
                    "https://console.groq.com/keys", true, false),
            new Provider("gemini", "Google Gemini", "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",
                    "gemini-2.0-flash", "https://aistudio.google.com/app/apikey", true, false),
            new Provider("openrouter", "OpenRouter", "https://openrouter.ai/api/v1/chat/completions", "openai/gpt-4o-mini",
                    "https://openrouter.ai/keys", false, false),
            new Provider("openai", "OpenAI", "https://api.openai.com/v1/chat/completions", "gpt-4o-mini",
                    "https://platform.openai.com/api-keys", false, false),
            new Provider("deepseek", "DeepSeek", "https://api.deepseek.com/chat/completions", "deepseek-chat",
                    "https://platform.deepseek.com/api_keys", false, false),
            new Provider("mistral", "Mistral", "https://api.mistral.ai/v1/chat/completions", "mistral-small-latest",
                    "https://console.mistral.ai/api-keys", false, false),
            new Provider("ollama", "Ollama (on this PC)", "http://localhost:11434/v1/chat/completions", "llama3.1",
                    "https://ollama.com/download", true, true),
            new Provider("lmstudio", "LM Studio (on this PC)", "http://localhost:1234/v1/chat/completions", "local-model",
                    "https://lmstudio.ai", true, true));

    public static Provider providerById(String id) {
        if (id == null) return null;
        String key = id.trim().toLowerCase(Locale.ROOT).replace(" ", "").replace("-", "");
        for (Provider p : PROVIDERS) if (p.id().equals(key) || p.label().toLowerCase(Locale.ROOT).replace(" ", "").startsWith(key)) return p;
        if (key.equals("google")) return providerById("gemini");
        if (key.equals("chatgpt") || key.equals("gpt")) return providerById("openai");
        return null;
    }

    /** Switch the endpoint and model to a provider preset.  Returns what changed, for chat. */
    public static String useProvider(String id) {
        AiPilot self = find();
        if (self == null) return "Zeus AI Functions is missing.";
        Provider p = providerById(id);
        if (p == null) {
            StringBuilder names = new StringBuilder();
            for (Provider each : PROVIDERS) names.append(names.length() == 0 ? "" : ", ").append(each.id());
            return "No provider called " + id + ". Pick one of: " + names + ".";
        }
        self.endpoint.set(p.url());
        self.model.set(p.model());
        noJsonMode = false;
        completionTokens = false;
        noTemperature = false;
        lastTestOk = false;
        lastTest = "";
        return p.label() + " selected: model " + p.model() + (p.keyless() ? ", no key needed." : ".");
    }

    public static String setModel(String name) {
        AiPilot self = find();
        if (self == null) return "Zeus AI Functions is missing.";
        if (name == null || name.isBlank()) return "Model is " + self.model.get() + ".";
        self.model.set(name.trim());
        completionTokens = false;
        noTemperature = false;
        lastTestOk = false;
        return "Model set to " + name.trim() + ". Run /zeus ai test to check it.";
    }

    public static String setEndpoint(String url) {
        AiPilot self = find();
        if (self == null) return "Zeus AI Functions is missing.";
        if (url == null || url.isBlank()) return "Endpoint is " + self.url() + ".";
        self.endpoint.set(url.trim());
        noJsonMode = false;
        completionTokens = false;
        noTemperature = false;
        lastTestOk = false;
        return "Endpoint set to " + self.url() + (local(self.url()) ? " (local, no key needed)." : ".");
    }

    /** Which provider preset the current endpoint belongs to, or null for a custom one. */
    public static Provider currentProvider() {
        AiPilot self = find();
        if (self == null) return null;
        String url = self.url();
        for (Provider p : PROVIDERS) if (url.equalsIgnoreCase(p.url())) return p;
        return null;
    }

    public static String modelName() {
        AiPilot self = find();
        return self == null ? "" : self.model.get();
    }

    public static String endpointUrl() {
        AiPilot self = find();
        return self == null ? "" : self.url();
    }

    public static boolean testPassed() {
        return lastTestOk;
    }

    public static String testResult() {
        return lastTest;
    }

    public static int plansDone() {
        return PLANS.get();
    }

    /** List the models the provider offers, as clickable chat lines that select one. */
    public static String models() {
        AiPilot self = find();
        if (self == null) return "Zeus AI Functions is missing.";
        if (!hasKey()) return "Save a key first: /zeus ai key <key>, or /zeus ai tutorial.";
        String secret = self.token.get();
        WORKER.submit(() -> {
            try {
                List<String> ids = self.listModels(secret);
                java.util.Collections.sort(ids);
                Minecraft.getInstance().execute(() -> AiTutorial.models(ids, self.model.get()));
            } catch (Throwable t) {
                String why = self.describe(t);
                Minecraft.getInstance().execute(() -> AiTutorial.chat("Could not list the models: " + why, true));
            }
        });
        return "Asking the provider which models it has...";
    }

    /** The provider's chat models (blocking; call on the worker thread). */
    List<String> listModels(String secret) throws Exception {
        String chat = url();
        String base = chat.endsWith("/chat/completions") ? chat.substring(0, chat.length() - "/chat/completions".length()) : chat;
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + "/models")).timeout(Duration.ofSeconds(20)).GET();
        if (secret != null && !secret.isBlank()) builder.header("Authorization", "Bearer " + secret.trim());
        HttpResponse<String> response = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException(explainError(response.statusCode(), errorText(response.body())));
        }
        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
        JsonArray data = root.has("data") && root.get("data").isJsonArray() ? root.getAsJsonArray("data")
                : root.has("models") && root.get("models").isJsonArray() ? root.getAsJsonArray("models") : new JsonArray();
        List<String> ids = new ArrayList<>();
        for (JsonElement e : data) {
            String id = e.isJsonObject() && e.getAsJsonObject().has("id") ? e.getAsJsonObject().get("id").getAsString()
                    : e.isJsonObject() && e.getAsJsonObject().has("name") ? e.getAsJsonObject().get("name").getAsString()
                    : e.isJsonPrimitive() ? e.getAsString() : null;
            if (id == null) continue;
            if (id.startsWith("models/")) id = id.substring(7);
            String l = id.toLowerCase(Locale.ROOT);
            if (l.contains("whisper") || l.contains("embed") || l.contains("tts") || l.contains("guard") || l.contains("dall-e")
                    || l.contains("moderation") || l.contains("audio") || l.contains("image") || l.contains("transcribe")
                    || l.contains("realtime") || l.contains("search") || l.contains("babbage") || l.contains("davinci")) continue;
            ids.add(id);
        }
        return ids;
    }

    /**
     * The best replacement when the configured model is gone: same family first (llama-3.3 for a retired
     * llama-3.1), then a known good general chat family, then anything with instruct or chat in its name.
     */
    static String replacementModel(String old, List<String> ids) {
        if (ids.isEmpty()) return null;
        String o = old == null ? "" : old.toLowerCase(Locale.ROOT);
        String family = o.replaceAll("[^a-z]+.*$", "");
        String[] prefer = {"llama-3.3-70b", "gpt-4o-mini", "gpt-4.1-mini", "gemini-2.5-flash", "gemini-2.0-flash", "deepseek-chat",
                "mistral-small", "qwen", "llama", "gpt", "gemini", "mistral"};
        if (!family.isEmpty()) {
            for (String id : ids) {
                String l = id.toLowerCase(Locale.ROOT);
                if (l.startsWith(family) && !l.contains("vision") && !l.contains("preview")) return id;
            }
        }
        for (String want : prefer) {
            for (String id : ids) {
                String l = id.toLowerCase(Locale.ROOT);
                if (l.contains(want) && !l.contains("vision") && !l.contains("mini-tts")) return id;
            }
        }
        for (String id : ids) {
            String l = id.toLowerCase(Locale.ROOT);
            if (l.contains("instruct") || l.contains("chat")) return id;
        }
        return ids.get(0);
    }

    /**
     * Send one tiny request and report the result in chat: proves the key, the endpoint and the model
     * all work, and teaches the request layer any quirks of this provider before a real request.
     */
    public static String test() {
        AiPilot self = find();
        if (self == null) return "Zeus AI Functions is missing.";
        if (!hasKey()) return "No key saved yet. Type /zeus ai key <your key>, or /zeus ai tutorial for help.";
        if (busy) return "Busy with another request, try again in a moment.";
        busy = true;
        String secret = self.token.get();
        lastTest = "testing";
        WORKER.submit(() -> {
            long start = System.currentTimeMillis();
            try {
                String content = self.exchange("Reply with exactly this JSON object: {\"say\":\"ready\",\"actions\":[]}",
                        secret, true);
                Plan plan = self.parse(content);
                long ms = System.currentTimeMillis() - start;
                lastTestOk = true;
                lastTestAt = System.currentTimeMillis();
                lastTest = "connected to " + self.model.get() + " in " + ms + " ms";
                Minecraft.getInstance().execute(() -> {
                    Notify.good("Zeus AI test passed: " + self.model.get() + " answered in " + ms + " ms"
                            + (plan.say().isBlank() ? "" : " (\"" + shorten(plan.say()) + "\")"));
                    AiTutorial.onTest(true);
                });
            } catch (Throwable t) {
                lastTestOk = false;
                lastTest = self.describe(t);
                Minecraft.getInstance().execute(() -> {
                    Notify.bad("Zeus AI test failed: " + lastTest);
                    AiTutorial.onTest(false);
                });
            } finally {
                busy = false;
            }
        });
        return "Testing the connection to " + self.model.get() + "...";
    }

    public static String ask(String instruction) {
        AiPilot self = find();
        if (self == null) return "Zeus AI Functions is missing.";
        return self.submit(instruction);
    }

    private String submit(String instruction) {
        if (!enabled()) return "Switch Zeus AI Functions on first (/zeus on aipilot)" + (hasKey() ? "." : ", or let /zeus ai tutorial walk you through the setup.");
        String typed = instruction == null ? "" : instruction.trim();
        if (typed.isEmpty()) return "Say what you want after /zeus ai.";
        String lowered = typed.toLowerCase(Locale.ROOT).replaceAll("[.!?]+$", "").trim();
        boolean again = lowered.equals("again") || lowered.equals("repeat") || lowered.equals("repeat that") || lowered.equals("do it again")
                || lowered.equals("same again") || lowered.equals("one more time");
        if (again && lastAsked.isBlank()) return "Nothing to repeat yet.";
        final String prompt = again ? lastAsked : typed;
        if (!typed.startsWith("These actions were attempted")) talk(true, typed);
        if (!again && !prompt.startsWith("These actions were attempted")) lastAsked = prompt;
        Step work = jobStep(prompt.toLowerCase(Locale.ROOT));
        if (work != null && !"job".equals(work.action()) && !"stop".equals(work.action())) {
            apply(work);
            return lastAction == null || lastAction.isBlank() ? "Done." : capital(lastAction) + ".";
        }
        if (work != null) {
            if ("stop".equals(work.action())) {
                boolean was = job != null;
                cancelJob(Minecraft.getInstance());
                return was ? "Stopped the job." : "No job was running.";
            }
            startJob(work.value());
            if (job == null && "home set here".equals(lastAction)) return "Home set here. Say go home to come back.";
            return job == null ? "Could not start that job. Check the Zeus AI Functions settings." : "Zeus AI: " + job.label();
        }
        loadMemory();
        if (!prompt.startsWith("These actions were attempted")) followUps = 0;
        String secret = token.get();
        if (!hasKey()) {
            String done = local(prompt, "No AI key saved, so the built-in parser handled it");
            return done + " Set the AI up with /zeus ai tutorial.";
        }
        long now = System.currentTimeMillis();
        long gap = Math.round(wait.get() * 1000.0);
        if (busy || now - LAST.get() < gap) {
            // Hold one request and send it as soon as the AI is free, instead of making the player retype it.
            waiting = prompt;
            return busy ? "Queued: I'll do that right after the current request." : "Queued: sending in a moment.";
        }
        if (!allow()) return "Hourly request limit reached (" + perHour.i() + "). Raise it in the Zeus AI Functions settings.";
        busy = true;
        LAST.set(now);
        failure = "";
        try {
            context = readContext();
        } catch (Throwable t) {
            context = "";
        }
        WORKER.submit(() -> {
            try {
                String answer = exchange(prompt, secret, false);
                Plan plan;
                try {
                    plan = parse(answer);
                } catch (RuntimeException malformed) {
                    answer = exchange(prompt + "\n\nYour previous answer was not valid JSON. Reply with the single JSON object and nothing else.", secret, false);
                    plan = parse(answer);
                }
                reply = plan.say();
                answered = System.currentTimeMillis();
                if (speak.on() && !reply.isBlank()) {
                    String said = reply;
                    Minecraft.getInstance().execute(() -> AiTutorial.chat(said, false));
                }
                remember(prompt, answer);
                PLANS.incrementAndGet();
                recordPlan(prompt, plan.steps());
                for (Step step : plan.steps()) QUEUE.add(step);
                lastAction = plan.steps().isEmpty() ? "nothing to do" : plan.steps().size() + " action(s) queued";
            } catch (Throwable t) {
                failure = describe(t);
                busy = false;
                Minecraft client = Minecraft.getInstance();
                String why = failure;
                client.execute(() -> {
                    AiTutorial.chat("The AI could not answer: " + why, true);
                    local(prompt, "The built-in parser handled it instead");
                });
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

    /** Undo several AI changes at once; all of them when count is 0 or less. */
    public static void undo(int count) {
        int n = 0;
        while (count <= 0 || n < count) {
            synchronized (UNDO) {
                if (UNDO.isEmpty()) break;
            }
            undo();
            n++;
        }
        if (n == 0) Notify.info("Nothing to undo");
        else Notify.good("Undid " + n + " change(s)");
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
        synchronized (REMINDERS) {
            long now = System.currentTimeMillis();
            for (java.util.Iterator<Object[]> it = REMINDERS.iterator(); it.hasNext(); ) {
                Object[] r = it.next();
                if (now < (Long) r[0]) continue;
                it.remove();
                AiTutorial.chat("Reminder: " + r[1], false);
                Zeus.toast("Reminder: " + r[1], true);
            }
        }
        if (!waiting.isEmpty() && !busy && System.currentTimeMillis() - LAST.get() >= Math.round(wait.get() * 1000.0)) {
            String next = waiting;
            waiting = "";
            String said = submit(next);
            if (said != null && !said.startsWith("Thinking")) AiTutorial.chat(said, false);
        }
        if (job != null) {
            try {
                tickJob(mc);
            } catch (Throwable t) {
                // A bug in one job must never take the game down: stop the job, say why, keep going.
                com.zlorbie788.zeus.core.Zeus.LOG.warn("Zeus AI job failed", t);
                try {
                    releaseKeys(mc);
                } catch (Throwable ignored) {
                }
                String what = job == null ? "job" : job.label();
                job = null;
                chain.clear();
                AiTutorial.chat("The " + what + " hit an error and was stopped (" + t.getClass().getSimpleName() + "). Nothing else is affected.", true);
            }
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
        if (speak.on()) AiTutorial.chat(summary, failures() > 0);
        RESULTS.clear();
        followUps = 0;
    }

    private static String capital(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
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
        try {
            applyUnsafe(step);
        } catch (Throwable t) {
            com.zlorbie788.zeus.core.Zeus.LOG.warn("Zeus AI action failed: {}", step, t);
            lastAction = "did not work: " + t.getClass().getSimpleName();
        }
    }

    private void applyUnsafe(Step step) {
        String verb = step.action() == null ? "" : step.action().toLowerCase(Locale.ROOT).trim();
        verb = switch (verb) {
            case "activate", "start", "turn_on", "turn on", "switch_on" -> "on";
            case "deactivate", "turn_off", "turn off", "switch_off" -> "off";
            case "change", "adjust", "configure", "setting", "set_setting", "update" -> "set";
            case "status", "state", "info" -> "report";
            case "note", "save" -> "remember";
            case "describe", "what" -> "explain";
            case "cancel", "halt" -> "stop";
            default -> verb;
        };
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
            case "schematics" -> {
                java.util.List<java.nio.file.Path> files = SchematicPlan.files();
                if (files.isEmpty()) AiTutorial.chat("No schematics yet. Save one with Litematica or drop .litematic files into the schematics folder of this instance.", true);
                else AiTutorial.schematics(files);
                lastAction = files.size() + " schematic(s) listed";
                return;
            }
            case "materials" -> {
                for (String line : schematicReport(step.value())) AiTutorial.chat(line, false);
                lastAction = "listed the materials for " + step.value();
                return;
            }
            case "look", "face", "look_at" -> {
                lastAction = look(step.value());
                return;
            }
            case "remind", "reminder", "timer" -> {
                lastAction = remind(step.value());
                return;
            }
            case "hold", "select", "equip" -> {
                lastAction = hold(step.value());
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
        if (step.module() == null || step.module().isBlank()) {
            if (!verb.isBlank()) lastAction = "did not understand the action " + verb;
            return;
        }
        Module module = findModule(step.module());
        if (module == null) {
            Notify.warn("The AI asked for a module that does not exist: " + step.module());
            lastAction = step.module() + " does not exist";
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
                if (setting instanceof Setting.Secret || setting instanceof Setting.Info) continue;
                String name = normalise(setting.name);
                if (name.isEmpty()) continue;
                if (name.contains(key) || key.contains(name)) {
                    target = setting;
                    break;
                }
            }
        }
        if (target == null) {
            // Last resort: the closest setting name by edit distance, for typos like "rnage".
            int best = Integer.MAX_VALUE;
            for (Setting setting : module.settings()) {
                if (setting instanceof Setting.Secret || setting instanceof Setting.Info) continue;
                String name = normalise(setting.name);
                if (name.isEmpty()) continue;
                int d = distance(name, key);
                if (d < best && d <= Math.max(1, key.length() / 3)) {
                    best = d;
                    target = setting;
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
        if (target instanceof Setting.Bool bool) {
            String lower = value.toLowerCase(Locale.ROOT);
            bool.set(lower.equals("toggle") || lower.equals("flip") ? !bool.on() : truthy(value));
            lastAction = module.name + " " + target.name + " set to " + (bool.on() ? "on" : "off");
            return;
        }
        if (target instanceof Setting.Num num) {
            double was = num.get();
            String clean = value.toLowerCase(Locale.ROOT).trim();
            double next;
            if (clean.equals("max") || clean.equals("maximum") || clean.equals("highest")) next = num.max;
            else if (clean.equals("min") || clean.equals("minimum") || clean.equals("lowest")) next = num.min;
            else if (clean.startsWith("+")) next = was + number(clean.substring(1), Double.NaN);
            else if (clean.startsWith("-") && num.min >= 0) next = was + number(clean, Double.NaN);   // a negative value is impossible, so it is a nudge down
            else next = number(clean, Double.NaN);
            if (Double.isNaN(next)) {
                lastAction = module.name + " " + target.name + " did not change: " + value + " is not a number";
                Notify.warn(lastAction);
                return;
            }
            num.set(next);
            lastAction = module.name + " " + target.name + " set to " + num.text()
                    + (num.get() != next ? " (limit " + trim(num.min) + " to " + trim(num.max) + ")" : "");
            return;
        }
        if (target instanceof Setting.Choice choice) {
            int index = choiceIndex(choice, value);
            if (index < 0) {
                lastAction = module.name + " " + target.name + " did not change: no option " + value
                        + " (options: " + String.join(", ", choice.options) + ")";
                Notify.warn(lastAction);
                return;
            }
            choice.set(index);
            lastAction = module.name + " " + target.name + " set to " + choice.value();
            return;
        }
        if (target instanceof Setting.Pick pick) {
            int index = pickIndex(pick, value);
            if (index < 0) {
                lastAction = module.name + " " + target.name + " did not change: no option " + value;
                Notify.warn(lastAction);
                return;
            }
            pick.set(index);
            lastAction = module.name + " " + target.name + " set to " + pick.label();
            return;
        }
        if (target instanceof Setting.Text text) text.set(value);
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

    /** Exact option, then same letters ignoring spaces and case, then a unique prefix or substring. -1 when none fits. */
    private static int choiceIndex(Setting.Choice choice, String value) {
        return bestOption(choice.options, null, value);
    }

    private static int pickIndex(Setting.Pick pick, String value) {
        return bestOption(pick.labels, pick.ids, value);
    }

    private static int bestOption(String[] labels, String[] ids, String value) {
        String key = normalise(value == null ? "" : value);
        if (key.isEmpty()) return -1;
        int n = labels.length;
        for (int i = 0; i < n; i++) {
            if (labels[i].equalsIgnoreCase(value) || ids != null && i < ids.length && ids[i].equalsIgnoreCase(value)) return i;
        }
        for (int i = 0; i < n; i++) {
            if (normalise(labels[i]).equals(key) || ids != null && i < ids.length && normalise(ids[i]).equals(key)) return i;
        }
        int found = -1;
        for (int i = 0; i < n; i++) {
            if (normalise(labels[i]).startsWith(key)) {
                if (found >= 0) return -1;
                found = i;
            }
        }
        if (found >= 0) return found;
        for (int i = 0; i < n; i++) {
            if (normalise(labels[i]).contains(key)) {
                if (found >= 0) return -1;
                found = i;
            }
        }
        return found;
    }

    /** Levenshtein distance, used to forgive small typos in module and setting names. */
    static int distance(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] t = prev;
            prev = cur;
            cur = t;
        }
        return prev[b.length()];
    }

    /** A module by id, name or alias, then by a close spelling, so "kill_aura" or "autototm" still land. */
    static Module findModule(String text) {
        Module module = Zeus.byName(text);
        if (module != null || text == null) return module;
        String key = normalise(text);
        if (key.length() < 3) return null;
        Module best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (Module m : Zeus.all()) {
            for (String name : new String[]{m.id, m.name}) {
                int d = distance(normalise(name), key);
                if (d < bestDistance) {
                    bestDistance = d;
                    best = m;
                }
            }
        }
        return bestDistance <= Math.max(1, key.length() / 4) ? best : null;
    }

    private String body(String instruction) {
        return body(instruction, false);
    }

    private String body(String instruction, boolean bare) {
        JsonObject root = new JsonObject();
        root.addProperty("model", model.get().trim());
        if (!noTemperature) root.addProperty("temperature", temperature.get());
        root.addProperty(completionTokens ? "max_completion_tokens" : "max_tokens", tokens.i());
        if (strictJson.on() && !noJsonMode) {
            JsonObject format = new JsonObject();
            format.addProperty("type", "json_object");
            root.add("response_format", format);
        }
        JsonArray messages = new JsonArray();
        if (bare) {
            messages.add(message("system", "You answer with one JSON object only."));
        } else {
            messages.add(message("system", system(instruction)));
            synchronized (HISTORY) {
                for (JsonObject past : HISTORY) messages.add(past);
            }
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
        builder.append("You are Zeus AI, the assistant inside the Zeus Minecraft client. You turn what the player types into actions. Answer with one JSON object only, no prose outside it, no code fences.\n");
        builder.append("Schema: {\"say\":\"one short friendly sentence for the player\",\"actions\":[{\"do\":\"<verb>\",\"module\":\"<id>\",\"setting\":\"<exact setting name>\",\"value\":\"<new value>\"}]}\n");
        builder.append("More jobs: breed (or breed cows), plant 6 saplings, guard me (fights mobs near the player until stop), put on armor (wears the best pieces it has), set home, go home.\n");
        builder.append("Schematics: job value build schematic <name> gathers the materials (chops, mines, crafts, smelts what it can) and then builds it 3 blocks in front of the player; add here to restart it at the current spot. do=materials with value <name> lists what it needs; do=schematics lists the files.\n");
        builder.append("Plan like a player: for a goal that needs several jobs, chain them with then in one job value, checking the inventory in the context first. Example, diamonds with no iron pickaxe: prepare then mine until iron then smelt 3 iron then craft iron pickaxe then mine until diamond. Do not repeat steps the inventory shows are already done.\n");
        builder.append("Finding ores: always use the job mine until <ore> (it digs a safe staircase to the right depth). Never use dig down to look for ores; digging straight down is dangerous.\n");
        builder.append("look turns the player toward value: coordinates (x y z), the nearest player or mob, a mob name or a block name. remind sets a chat reminder: value is minutes then the text, for example 10 check the furnace (use 30s or 2h for other units).\n");
        builder.append("Verbs: on, off, toggle, set (need module); job, stop, hold, look, remind, preset, theme, waypoint, explain, remember, forget, report, help, open, hud, keybinds, panic, undo (no module). hold puts an item in hand: value is the item (pickaxe, bread, diamond sword, nothing).\n");
        builder.append("Examples:\n");
        builder.append("turn on sprint and fullbright -> {\"say\":\"Sprint and Fullbright are on.\",\"actions\":[{\"do\":\"on\",\"module\":\"sprint\"},{\"do\":\"on\",\"module\":\"fullbright\"}]}\n");
        builder.append("make killaura reach 4 -> {\"say\":\"Range set to 4.\",\"actions\":[{\"do\":\"set\",\"module\":\"killaura\",\"setting\":\"Range\",\"value\":\"4\"}]}\n");
        builder.append("get me 10 iron then come back -> {\"say\":\"Off to mine iron.\",\"actions\":[{\"do\":\"job\",\"value\":\"mine 10 iron then return\"}]}\n");
        builder.append("what does esp do? -> {\"say\":\"ESP outlines players and mobs through walls.\",\"actions\":[{\"do\":\"explain\",\"value\":\"esp\"}]}\n");
        builder.append("hi / thanks / a question with nothing to change -> {\"say\":\"<a short helpful answer>\",\"actions\":[]}\n");
        builder.append("For a Minecraft or client question (a recipe, where to find something, what a module does), answer it in say in up to three sentences, with real facts for Minecraft 1.21. Do not make up mechanics. If something is ambiguous and a wrong guess would be costly, ask one short question in say and send no actions.\n");
        builder.append("Refer back to the conversation: 'turn it off' or 'more' means the module or setting talked about last.\n");
        builder.append("The context lists nearby blocks and saved waypoints with coordinates. To reach one, send a job like go to X Y Z with those numbers (go to my base, go to the chest, go to the nearest iron ore). Never invent coordinates that are not in the context.\n");
        builder.append("Use the ids from the module list below, not guesses (the example ids above may not exist). Setting values: numbers as plain numbers, on/off for switches, one of the listed options for choices. To nudge a number you may write +1 or -0.5.\n");
        builder.append("Jobs understand tools: before mining a block it cannot harvest it crafts the pickaxe that can, and it will not count a block that dropped nothing. Say 'food' or 'kill for food' to hunt animals and eat.\n");
        builder.append("panic switches every module off. undo reverses the last change the AI made. report reads out what is on. remember stores value as a note for later sessions. theme takes value as one of: ");
        builder.append(String.join(", ", com.zlorbie788.zeus.ui.UiSettings.ACCENT.options)).append(". waypoint drops a waypoint where the player stands, using value as its name.\n");
        builder.append("Rules: module must be an id from the list. For \"set\" the setting name must match a setting the module shows. ");
        builder.append("Only send actions that are needed. Never invent ids. If the request cannot be done, return an empty actions list and explain in say.\n");
        builder.append("Prefix any job with repeat N to run the whole plan N times, for example: repeat 3 chop a tree then mine 10 stone.\n");
        builder.append("go to mines a staircase down, cuts steps up or towers up on its own when the target sits at another height, and it never digs the block under its feet. mine until only closes on ore it can already see, mines it, and reports the block that actually broke.\n");
        builder.append("Movement is planned on the block grid: when the direct line is blocked it searches for a walkable path around walls, up ledges and down drops, never through lava, fire or cactus.\n");
        builder.append("Ore jobs staircase to the depth the ore lives at first (iron y16, diamond y-59) and then tunnel, so ask for mine 16 iron without naming a height.\n");
        builder.append("do can also be job, where value is a phrase like: chop tree, chop 3 trees, mine until iron, mine 20 stone, quarry 10, tunnel 30, strip mine, farm, replant, till 9, collect, hunt, flee, shoot, shear 3, milk, eat, sleep, torch 8, place cobblestone here, place torch at 120 64 -300, drop 32 dirt, explore 60, return, build a house, swim to 120 62 -300, climb 8, bridge 12, go to 120 64 -300, come here, follow me, follow Steve, dig down, stop. Chain them in one value with then: chop 2 trees then mine until iron.\n");
        builder.append("Survival and food: prepare (or gear up) makes a wooden pickaxe, a stone pickaxe, a stone sword and hunts for food in one go. fish 5 casts a rod it must already have, reels on the bobber dip and counts the catch. food 3 kills animals for meat.\n");
        builder.append("Creative mode: every block breaks on the first swing without a tool, nothing is used up when you place it, flying is allowed and hunger never drops. Do not queue crafting or food jobs there, just mine, build and go where you are told.\n");
        builder.append("Chests: store 20 cobblestone, put away 32 dirt, deposit 5 diamond, take 32 iron, loot 16 bread, grab 4 torch. The job finds a chest, barrel or trapped chest within five blocks, opens it and shift-clicks one stack at a time, counts real items against the number asked for, and only matches the exact item: iron means ingots, never ore or a sword, so say iron_ore for ore.\n");
        builder.append("Crafting and furnaces: craft 4 planks, craft a crafting table, craft sticks, craft a furnace, craft a stone pickaxe, craft iron sword, craft torch, craft chest, craft bucket, craft bed, smelt 8 iron, cook 4 porkchop. The job places and opens its own crafting table or furnace, crafts planks or sticks from logs first when it has to, and furnishes fuel itself. Known recipes: planks, stick, crafting_table, furnace, chest, torch, stone_pickaxe, stone_axe, stone_shovel, stone_sword, stone_hoe, iron_pickaxe, iron_sword, bucket, bed.\n");
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
                block.append("  ").append(setting.name).append(" = ").append(value).append(shape(setting)).append('\n');
            }
            if (block.length() == 0) continue;
            String about = module.desc == null ? "" : module.desc.length() > 110 ? module.desc.substring(0, 110) + "…" : module.desc;
            builder.append("Settings of ").append(module.id).append(" (").append(module.name)
                    .append(about.isBlank() ? "" : ": " + about).append("):\n").append(block);
            detailed++;
        }
        return builder.toString();
    }

    /** The allowed values of a setting, so the model picks a value that exists. */
    private static String shape(Setting setting) {
        if (setting instanceof Setting.Num num) {
            return " (" + trim(num.min) + " to " + trim(num.max) + ")";
        }
        if (setting instanceof Setting.Choice choice && choice.options.length <= 12) {
            return " (one of: " + String.join(", ", choice.options) + ")";
        }
        if (setting instanceof Setting.Pick pick && pick.labels.length <= 12) {
            return " (one of: " + String.join(", ", pick.labels) + ")";
        }
        if (setting instanceof Setting.Bool) return " (on/off)";
        return "";
    }

    private static String trim(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
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

    /**
     * Send a request and return the model's message content, adapting to the provider: when it
     * rejects JSON mode, max_tokens or a custom temperature, the field is changed and the request
     * is sent again, and the provider is remembered so later requests go right first time.
     */
    String exchange(String instruction, String secret, boolean bare) throws Exception {
        IllegalStateException last = null;
        boolean healed = false;
        for (int attempt = 0; attempt < 5; attempt++) {
            try {
                long start = System.currentTimeMillis();
                String content = content(call(body(instruction, bare), secret));
                lastLatency = System.currentTimeMillis() - start;
                return content;
            } catch (ProviderError e) {
                last = e;
                String m = e.detail.toLowerCase(Locale.ROOT);
                String current = model.get() == null ? "" : model.get().toLowerCase(Locale.ROOT).trim();
                boolean aboutParameter = m.contains("parameter") || m.contains("max_tokens") || m.contains("temperature") || m.contains("response_format");
                boolean modelGone = (e.code == 404 || e.code == 400) && !aboutParameter
                        && ((!current.isEmpty() && m.contains(current)) || m.contains("model_not_found") || m.contains("model not found")
                        || m.contains("unknown model") || m.contains("invalid model") || m.contains("decommissioned"))
                        && (m.contains("not found") || m.contains("does not exist") || m.contains("decommissioned") || m.contains("deprecated")
                        || m.contains("not supported") || m.contains("invalid") || m.contains("no longer") || m.contains("unknown"));
                if (modelGone && !healed) {
                    // Providers retire models; pick the closest one they still offer instead of breaking.
                    healed = true;
                    String old = model.get();
                    String next = null;
                    try {
                        next = replacementModel(old, listModels(secret));
                    } catch (Exception ignored) {
                    }
                    if (next == null || next.equalsIgnoreCase(old)) throw e;
                    model.set(next);
                    String said = "The model " + old + " is not available any more, so Zeus AI switched to " + next + ".";
                    try {
                        Minecraft.getInstance().execute(() -> AiTutorial.chat(said, false));
                    } catch (Throwable ignored) {
                    }
                    continue;
                }
                if (e.code != 400 && e.code != 422) throw e;
                if (!noJsonMode && (m.contains("response_format") || m.contains("json_object") || m.contains("json mode"))) {
                    noJsonMode = true;
                } else if (!completionTokens && m.contains("max_tokens")) {
                    completionTokens = true;
                } else if (!noTemperature && m.contains("temperature")) {
                    noTemperature = true;
                } else if (!noJsonMode && strictJson.on()) {
                    noJsonMode = true;     // an unexplained 400 is most often JSON mode
                } else {
                    throw e;
                }
            }
        }
        throw last == null ? new IllegalStateException("the provider kept rejecting the request") : last;
    }

    /** A provider error with the HTTP code and the provider's own message. */
    static final class ProviderError extends IllegalStateException {
        final int code;
        final String detail;

        ProviderError(int code, String detail, String message) {
            super(message);
            this.code = code;
            this.detail = detail == null ? "" : detail;
        }
    }

    private String call(String payload, String secret) throws Exception {
        String url = url();
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(45))
                .header("Content-Type", "application/json")
                .header("User-Agent", "ZeusClient")
                .POST(HttpRequest.BodyPublishers.ofString(payload));
        if (secret != null && !secret.isBlank()) builder.header("Authorization", "Bearer " + secret.trim());
        if (url.contains("openrouter.ai")) builder.header("X-Title", "Zeus Client");
        HttpRequest request = builder.build();
        int attempt = 0;
        while (true) {
            HttpResponse<String> response;
            try {
                response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (java.net.ConnectException e) {
                throw new IllegalStateException(local(url)
                        ? "nothing is answering at " + url + ". Is the local AI server running?"
                        : "could not connect to " + URI.create(url).getHost() + ". Check your internet connection.");
            } catch (java.net.http.HttpTimeoutException e) {
                throw new IllegalStateException("the provider took longer than 45 seconds. Try a faster model.");
            }
            int code = response.statusCode();
            if ((code == 429 || code >= 500) && ++attempt < 3) {
                long waitMs = 1200L * attempt;
                String after = response.headers().firstValue("retry-after").orElse("");
                try {
                    if (!after.isBlank()) waitMs = Math.min(10_000L, Math.max(500L, (long) (Double.parseDouble(after.trim()) * 1000)));
                } catch (NumberFormatException ignored) {
                }
                Thread.sleep(waitMs);
                continue;
            }
            if (code < 200 || code >= 300) {
                String detail = errorText(response.body());
                throw new ProviderError(code, detail, explainError(code, detail));
            }
            return response.body();
        }
    }

    /** The provider's own error message out of an error body, or the body itself. */
    private static String errorText(String body) {
        if (body == null) return "";
        try {
            JsonElement root = JsonParser.parseString(body);
            if (root.isJsonArray() && !root.getAsJsonArray().isEmpty()) root = root.getAsJsonArray().get(0);
            if (root.isJsonObject()) {
                JsonObject o = root.getAsJsonObject();
                JsonElement error = o.get("error");
                if (error != null && error.isJsonObject() && error.getAsJsonObject().has("message")) {
                    return error.getAsJsonObject().get("message").getAsString();
                }
                if (error != null && error.isJsonPrimitive()) return error.getAsString();
                if (o.has("message") && o.get("message").isJsonPrimitive()) return o.get("message").getAsString();
            }
        } catch (RuntimeException ignored) {
        }
        return shorten(body);
    }

    /** Turn an HTTP failure into a sentence that says what to do about it. */
    private String explainError(int code, String detail) {
        String said = detail.isBlank() ? "" : " (" + shorten(detail) + ")";
        return switch (code) {
            case 401 -> "the provider rejected the API key. Paste it again with /zeus ai key <key>" + said;
            case 402 -> "the provider account has no credit left" + said;
            case 403 -> "the key is not allowed to use this model or endpoint" + said;
            case 404 -> "the endpoint or the model " + model.get() + " was not found. Check /zeus ai model and /zeus ai endpoint" + said;
            case 413 -> "the request was too large for this model" + said;
            case 429 -> "rate limited or out of quota at the provider, wait a minute" + said;
            default -> code >= 500 ? "the provider had a server error (HTTP " + code + "), try again soon" + said
                    : "HTTP " + code + said;
        };
    }

    /** The model's message text from a chat completions response, whatever shape it came in. */
    private static String content(String body) {
        JsonObject root;
        try {
            root = JsonParser.parseString(body).getAsJsonObject();
        } catch (RuntimeException e) {
            throw new IllegalStateException("the provider sent something that is not JSON: " + shorten(body));
        }
        if (root.has("error") && !root.get("error").isJsonNull()) throw new IllegalStateException("the provider said: " + errorText(body));
        if (root.has("usage") && root.get("usage").isJsonObject()) {
            JsonObject usage = root.getAsJsonObject("usage");
            if (usage.has("total_tokens") && usage.get("total_tokens").isJsonPrimitive()) TOKENS.addAndGet(usage.get("total_tokens").getAsLong());
        }
        JsonArray choices = root.has("choices") && root.get("choices").isJsonArray() ? root.getAsJsonArray("choices") : null;
        if (choices == null || choices.isEmpty()) throw new IllegalStateException("the provider returned no answer");
        JsonObject first = choices.get(0).getAsJsonObject();
        JsonObject message = first.has("message") && first.get("message").isJsonObject() ? first.getAsJsonObject("message") : null;
        if (message == null) throw new IllegalStateException("the provider returned an empty answer");
        String text = flatten(message.get("content"));
        if (text.isBlank() && message.has("tool_calls") && message.get("tool_calls").isJsonArray()) {
            for (JsonElement call : message.getAsJsonArray("tool_calls")) {
                JsonObject fn = call.isJsonObject() ? call.getAsJsonObject().getAsJsonObject("function") : null;
                if (fn != null && fn.has("arguments")) {
                    text = flatten(fn.get("arguments"));
                    break;
                }
            }
        }
        if (text.isBlank()) text = flatten(message.get("reasoning_content"));
        if (text.isBlank()) {
            String why = first.has("finish_reason") && !first.get("finish_reason").isJsonNull() ? first.get("finish_reason").getAsString() : "";
            throw new IllegalStateException(why.equals("length")
                    ? "the answer was cut off. Raise Answer length in the Zeus AI settings"
                    : "the provider returned an empty answer");
        }
        return text;
    }

    private static String flatten(JsonElement element) {
        if (element == null || element.isJsonNull()) return "";
        if (element.isJsonPrimitive()) return element.getAsString();
        if (element.isJsonArray()) {
            StringBuilder out = new StringBuilder();
            for (JsonElement part : element.getAsJsonArray()) {
                if (part.isJsonObject() && part.getAsJsonObject().has("text")) out.append(part.getAsJsonObject().get("text").getAsString());
                else if (part.isJsonPrimitive()) out.append(part.getAsString());
            }
            return out.toString();
        }
        return element.toString();
    }

    private static String shorten(String text) {
        if (text == null) return "";
        String flat = text.replaceAll("\\s+", " ").trim();
        return flat.length() <= 120 ? flat : flat.substring(0, 120) + "...";
    }

    private record Plan(String say, List<Step> steps) {}

    private Plan parse(String content) {
        String json = strip(content);
        JsonObject parsed = JsonParser.parseString(json).getAsJsonObject();
        String say = parsed.has("say") && !parsed.get("say").isJsonNull() ? flatten(parsed.get("say")) : "";
        List<Step> steps = new ArrayList<>();
        JsonElement list = parsed.has("actions") ? parsed.get("actions") : parsed.get("steps");
        JsonArray actions = list != null && list.isJsonArray() ? list.getAsJsonArray() : new JsonArray();
        if (list != null && list.isJsonObject()) actions.add(list);
        for (JsonElement element : actions) {
            if (!element.isJsonObject()) continue;
            JsonObject action = element.getAsJsonObject();
            String verb = text(action, "do");
            if (verb.isBlank()) verb = text(action, "action");
            steps.add(new Step(
                    verb,
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
        int think = text.lastIndexOf("</think>");
        if (think >= 0) text = text.substring(think + 8).trim();
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
            return List.of("Idle. Try: chop that tree, mine until iron (stops the moment it sees ore), quarry 8, tunnel 30, farm, replant, hunt, shoot, shear, milk, eat, sleep, torch 8, explore 60.",
                    "Crafting: craft 4 planks, craft a crafting table, craft stone pickaxe, craft torch, smelt 8 iron.",
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
            if (head.matches("^(chop|cut|break|mine|dig|get|go|goto|walk|come|follow|farm|harvest|collect|pick|tunnel|strip|sleep|replant|torch|quarry|milk|shear|hunt|flee|shoot|eat|food|meat|drop|return|explore|place|put|house|build|swim|climb|pillar|bridge|craft|make|smelt|cook)\\b.*")) {
                return new Step("job", "", "", text);
            }
        }
        if (text.matches("^(build|print|make)( the| my)? schematic.*") || text.startsWith("schematic ")
                || (text.startsWith("build ") && !text.contains("house") && !text.contains("bridge") && SchematicPlan.find(text.substring(6)) != null)) {
            return new Step("job", "", "", text.replaceFirst("^make", "build"));
        }
        if (text.matches("^(list|show)( the| my)? schematics?$") || text.equals("schematics")) return new Step("schematics", "", "", "");
        java.util.regex.Matcher need = java.util.regex.Pattern
                .compile("^(?:get me|get|i need|bring me|gather|farm|collect|go get|make me|find me|find)\\s+(?:(\\d+)\\s+)?(?:some |a |an )?([a-z_ ]+?)s?$").matcher(text);
        if (need.find() && !need.group(2).equals("drop") && !need.group(2).contains("tree")) {
            net.minecraft.world.item.Item item = Craft.exact(need.group(2).trim());
            if (item == null) item = productOf(need.group(2).trim());
            if (item != null && item != Items.AIR) {
                int n = need.group(1) == null ? (item.getDefaultMaxStackSize() > 1 ? 16 : 1) : Integer.parseInt(need.group(1));
                String how = SchematicPlan.jobFor(item, n);
                if (how != null) return new Step("job", "", "", how);
            }
        }
        if (text.matches("^(breed|feed)\\b.*") || text.matches("^plant .*(sapling|tree).*") || text.matches("^(guard|protect|bodyguard)\\b.*")
                || text.contains("defend me") || text.matches(".*\\b(armor|armour)\\b.*") && !text.startsWith("craft") && !text.startsWith("make")
                || text.matches("^(set home|sethome|mark home|this is home|go home|home|return home|back home)$")) {
            return new Step("job", "", "", text);
        }
        java.util.regex.Matcher mats = java.util.regex.Pattern.compile("^(?:materials|material list|what do i need) (?:for )?(.+)$").matcher(text);
        if (mats.find() && SchematicPlan.find(mats.group(1)) != null) return new Step("materials", "", "", mats.group(1));
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
        java.util.regex.Matcher counted = java.util.regex.Pattern
                .compile("^(?:mine|dig|get|collect|gather) (\\d+) ([a-z_ ]+)$").matcher(text);
        if (counted.find() && !counted.group(2).contains("crop") && !counted.group(2).contains("drop")) {
            return new Step("job", "", "", "mine " + counted.group(1) + " " + counted.group(2).trim());
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
        if (text.equals("prepare") || text.equals("gear up") || text.equals("get geared")
                || text.equals("get tools") || text.equals("prepare to mine") || text.equals("bootstrap")
                || text.contains("gear up")) {
            return new Step("job", "", "", "prepare");
        }
        if (text.contains("fish") && !text.contains("fishing rod")) {
            java.util.regex.Matcher count = java.util.regex.Pattern.compile("(\\d+)").matcher(text);
            return new Step("job", "", "", "fish " + (count.find() ? count.group(1) : "3"));
        }
        if (text.contains("food") || text.contains("meat") || text.contains("something to eat")
                || text.contains("kill an animal") || text.contains("kill animals")
                || text.contains("hunt animals") || text.contains("hunt for food")) {
            java.util.regex.Matcher count = java.util.regex.Pattern.compile("(\\d+)").matcher(text);
            return new Step("job", "", "", "food " + (count.find() ? count.group(1) : "3"));
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
        if ((text.startsWith("craft ") || text.startsWith("make ")) && !text.contains("farmland") && !text.contains("house")) {
            return new Step("job", "", "", text.startsWith("craft") ? text : "craft " + text.substring(5).trim());
        }
        if (text.startsWith("store ") || text.startsWith("deposit ") || text.startsWith("put away ")) {
            return new Step("job", "", "", text);
        }
        if (text.startsWith("take ") || text.startsWith("loot ") || text.startsWith("grab ")) {
            return new Step("job", "", "", text);
        }
        if (text.startsWith("smelt ") || text.startsWith("cook ")) {
            return new Step("job", "", "", text.startsWith("smelt") ? text : "smelt " + text.substring(5).trim());
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
        // One word for the whole opening of a world: tools first, then food, so nothing later in
        // the chain has to fail because the player had bare hands.
        if (text.equals("prepare") || text.equals("gear up") || text.equals("get geared")
                || text.equals("get tools") || text.equals("prepare to mine") || text.equals("bootstrap")) {
            if (creative(mc)) {
                Notify.info("Zeus AI: creative mode, there is nothing to gear up");
                return;
            }
            text = "craft 1 wooden pickaxe then craft 1 stone pickaxe then craft 1 stone sword then food 2";
            Notify.info("Zeus AI: tools and food first");
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
        } else if (text.startsWith("fish") || text.contains("go fishing") || text.contains("catch fish")) {
            java.util.regex.Matcher count = java.util.regex.Pattern.compile("(\\d+)").matcher(text);
            started = new Job(JobKind.FISH, "fish",
                    count.find() ? Math.max(1, Math.min(64, Integer.parseInt(count.group(1)))) : 3, null);
        } else if (text.startsWith("food") || text.startsWith("meat") || text.contains("kill for food")
                || text.contains("hunt for food") || text.contains("hunt animals")) {
            java.util.regex.Matcher count = java.util.regex.Pattern.compile("(\\d+)").matcher(text);
            started = new Job(JobKind.FOOD, "meat",
                    count.find() ? Math.max(1, Math.min(32, Integer.parseInt(count.group(1)))) : 3, null);
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
        } else if (text.startsWith("schematic") || text.startsWith("build schematic") || text.startsWith("build the schematic")
                || text.startsWith("print ") || (text.startsWith("build ") && SchematicPlan.find(text.substring(6)) != null
                && !text.contains("house") && !text.contains("bridge"))) {
            String name = text.replaceFirst("^(build the schematic|build my schematic|build schematic|schematic|print|build)\\s*", "").trim();
            boolean fresh = name.endsWith(" here") || name.equals("here");
            name = name.replaceFirst("\\s*here$", "").trim();
            java.nio.file.Path file = SchematicPlan.find(name);
            if (file == null) {
                Notify.warn(SchematicPlan.files().isEmpty()
                        ? "No schematics found. Put .litematic files in the schematics folder of this instance."
                        : "No schematic called " + name + ". Try /zeus ai schematics");
                return false;
            }
            String shown = SchematicPlan.display(file);
            if (schem == null || !schem.file.equals(file) || fresh) {
                try {
                    BlockPos origin = mc.player.blockPosition().relative(facing(mc.player.getYRot()), 3);
                    schem = SchematicPlan.load(file, origin);
                    schemIndex = 0;
                    schemTried.clear();
                    schemSkipped = 0;
                    AiTutorial.chat("Building " + shown + ": " + schem.order.size() + " blocks, " + schem.needs.size()
                            + " kinds of material, starting 3 blocks in front of you at " + origin.getX() + " " + origin.getY() + " " + origin.getZ() + ".", false);
                } catch (Exception e) {
                    Notify.warn("Could not read " + shown + ": " + e.getMessage());
                    return false;
                }
            }
            started = new Job(JobKind.SCHEMATIC, shown, schem.order.size(), null);
        } else if (text.startsWith("breed") || text.contains("feed the animals") || text.contains("feed animals")) {
            started = new Job(JobKind.BREED, text.replaceFirst("^breed( the)?", "").replaceFirst("^ *(animals?|all)", "").trim(), 0, null);
        } else if (text.startsWith("plant") && (text.contains("sapling") || text.contains("tree"))) {
            java.util.regex.Matcher n = java.util.regex.Pattern.compile("(\\d+)").matcher(text);
            started = new Job(JobKind.PLANT, "", n.find() ? Math.max(1, Math.min(64, Integer.parseInt(n.group(1)))) : 4, null);
        } else if (text.startsWith("guard") || text.startsWith("protect") || text.startsWith("bodyguard") || text.contains("defend me")) {
            String who = text.replaceFirst("^(guard|protect|bodyguard|defend)( me)?", "").trim();
            started = new Job(JobKind.GUARD, who, 0, null);
        } else if (text.contains("armor") || text.contains("armour")) {
            started = new Job(JobKind.ARMOR, "", 0, null);
        } else if (text.equals("set home") || text.equals("sethome") || text.equals("this is home") || text.equals("mark home")) {
            Extras.Waypoints points = Zeus.get(Extras.Waypoints.class);
            if (points == null) return false;
            points.addAt("Home", mc.player.getX(), mc.player.getY(), mc.player.getZ());
            lastAction = "home set here";
            return true;
        } else if (text.equals("home") || text.equals("go home") || text.equals("return home") || text.equals("back home")) {
            Extras.Waypoints points = Zeus.get(Extras.Waypoints.class);
            double[] home = points == null ? null : points.findNamed("home");
            if (home == null) {
                Notify.warn("No Home waypoint yet. Say set home where you live.");
                return false;
            }
            started = new Job(JobKind.GOTO, "home", 0, new Vec3(home[0], home[1], home[2]));
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
        } else if ((text.startsWith("craft ") || text.startsWith("make ")) && !text.contains("house")) {
            String rest = text.replaceFirst("^(craft|make) ", "").replaceFirst("^(some|a|an|me) ", "").trim();
            java.util.regex.Matcher many = java.util.regex.Pattern.compile("^(\\d+) (.+)$").matcher(rest);
            if (many.find()) {
                started = new Job(JobKind.CRAFT, tidy(many.group(2)),
                        Math.max(1, Math.min(64, Integer.parseInt(many.group(1)))), null);
            } else {
                started = new Job(JobKind.CRAFT, tidy(rest), 1, null);
            }
            if (started.target.isBlank()) {
                Notify.warn("Name what to craft, for example: craft a crafting table");
                return false;
            }
        } else if (text.startsWith("smelt ") || text.startsWith("cook ")) {
            String rest = text.replaceFirst("^(smelt|cook) ", "").replaceFirst("^(some|a|an|me) ", "").trim();
            java.util.regex.Matcher many = java.util.regex.Pattern.compile("^(\\d+) (.+)$").matcher(rest);
            if (many.find()) {
                started = new Job(JobKind.SMELT, tidy(many.group(2)),
                        Math.max(1, Math.min(64, Integer.parseInt(many.group(1)))), null);
            } else {
                started = new Job(JobKind.SMELT, tidy(rest), 1, null);
            }
            if (started.target.isBlank()) {
                Notify.warn("Name what to smelt, for example: smelt 8 iron");
                return false;
            }
        } else if (text.startsWith("store ") || text.startsWith("put away ") || text.startsWith("deposit ")) {
            String rest = text.replaceFirst("^(store|put away|deposit) ", "")
                    .replaceFirst("^(some|a|an|the) ", "").replaceFirst(" (in|into|inside) (a|the) chest$", "").trim();
            java.util.regex.Matcher many = java.util.regex.Pattern.compile("^(\\d+) (.+)$").matcher(rest);
            started = many.find()
                    ? new Job(JobKind.STORE, tidy(many.group(2)), Math.max(1, Integer.parseInt(many.group(1))), null)
                    : new Job(JobKind.STORE, tidy(rest), 0, null);
            if (started.target.isBlank()) {
                Notify.warn("Name an item to store, for example: store 20 cobblestone");
                return false;
            }
        } else if (text.startsWith("take ") || text.startsWith("loot ") || text.startsWith("grab ")) {
            String rest = text.replaceFirst("^(take out |take |loot |grab )", "")
                    .replaceFirst("^(some|a|an|the) ", "").trim();
            java.util.regex.Matcher many = java.util.regex.Pattern.compile("^(\\d+) (.+)$").matcher(rest);
            started = many.find()
                    ? new Job(JobKind.TAKE, tidy(many.group(2)), Math.max(1, Integer.parseInt(many.group(1))), null)
                    : new Job(JobKind.TAKE, tidy(rest), 0, null);
            if (started.target.isBlank()) {
                Notify.warn("Name an item to take, for example: take 32 iron");
                return false;
            }
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
        event((done ? "finished " : "stopped ") + label + ": " + reason);
        Notify.good("Zeus AI: " + label + " — " + reason);
        if (speak.on()) AiTutorial.chat((done ? "Done: " : "Stopped: ") + label + " — " + reason, !done);
        if (!done) {
            if (!chain.isEmpty()) {
                Notify.info("Zeus AI: dropped " + chain.size() + " queued job(s) after the stop");
                chain.clear();
            }
            // A job that failed on its own (not stopped by the player) goes back to the model with the
            // reason, so it can work around it: make the missing tool, go somewhere else, try another way.
            boolean byPlayer = reason.contains("stopped by") || reason.equals("stopped") || reason.contains("switched off") || reason.contains("you died");
            if (!byPlayer && selfHeal.on() && hasSecret() && followUps < 2 && !lastAsked.isBlank() && followAt == 0) {
                followUps++;
                followPrompt = "These actions were attempted and produced these results: the job '" + label + "' stopped because " + reason
                        + ". The player originally asked: " + lastAsked + ". Work around the problem and carry on with the request, or explain in say why it cannot be done. Reply with the single JSON object and nothing else.";
                followAt = System.currentTimeMillis() + 1500L;
                AiTutorial.chat("That did not work (" + reason + "), thinking of another way…", true);
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
        if (!job.toolChecked) {
            job.toolChecked = true;
            if (autoTool.on() && toolUp(mc)) return;
        }
        if (mc.screen != null && job.kind != JobKind.CRAFT && job.kind != JobKind.SMELT) {
            releaseKeys(mc);
            return;
        }
        if (!jobs.on()) {
            giveUp(mc, "jobs were switched off");
            return;
        }
        if (mc.player.isDeadOrDying()) {
            giveUp(mc, "stopped, you died");
            return;
        }
        if (mc.player.getHealth() <= 6.0f && job.kind != JobKind.EAT && job.kind != JobKind.FLEE) {
            // Low health: stand still and eat to regenerate for a while before abandoning the job.
            long now = System.currentTimeMillis();
            java.util.function.Predicate<ItemStack> edible = s -> s.has(net.minecraft.core.component.DataComponents.FOOD);
            boolean canHeal = mc.player.getFoodData().getFoodLevel() < 20 && countItems(mc, edible) > 0
                    && nearestMonster(mc, 6.0) == null;
            if (canHeal && (job.healUntil == 0L || now < job.healUntil)) {
                if (job.healUntil == 0L) {
                    job.healUntil = now + 40_000L;
                    Notify.warn("Zeus AI: health is low, pausing " + job.label() + " to eat");
                }
                mc.options.keyUp.setDown(false);
                mc.options.keyDown.setDown(false);
                mc.options.keyLeft.setDown(false);
                mc.options.keyRight.setDown(false);
                mc.options.keyAttack.setDown(false);
                job.started += 50L;           // healing time does not count against the job's budget
                if (!mc.player.isUsingItem()) {
                    int slot = ensureHotbar(mc, edible);
                    if (slot >= 0) {
                        if (!Inv.holding(edible)) Inv.select(slot);
                        mc.options.keyUse.setDown(true);
                    }
                }
                job.note = "low health, eating to heal";
                return;
            }
            giveUp(mc, "stopped, health is low" + (canHeal ? " and eating did not help" : ""));
            return;
        }
        if (job.healUntil != 0L && mc.player.getHealth() > 6.0f) {
            job.healUntil = 0L;
            mc.options.keyUse.setDown(false);
        }
        if (mc.screen == null && defend(mc)) return;
        if (mc.screen == null) snack(mc);
        // Auto Eat owns the use key and the hotbar while it eats; fighting it would cancel the food.
        Gear.AutoEat eating = Zeus.get(Gear.AutoEat.class);
        if (eating != null && eating.enabled() && job.kind != JobKind.EAT && mc.player.isUsingItem()) {
            releaseKeys(mc);
            job.note = "waiting for Auto Eat";
            return;
        }
        long budget = switch (job.kind) {
            case QUARRY, TUNNEL, MINE_UNTIL -> 600_000L;
            default -> 300_000L;
        };
        long clock = System.currentTimeMillis();
        if (System.currentTimeMillis() - job.started > budget) {
            giveUp(mc, "gave up after " + (budget / 60_000L) + " minutes");
            return;
        }
        // A job that neither moves nor gets anything done is wedged; say so instead of spinning.
        if (job.progressAt == 0L) {
            job.progressAt = clock;
            job.progressMark = job.mined;
            job.lastX = mc.player.getX();
            job.lastZ = mc.player.getZ();
        }
        boolean moved = Math.abs(mc.player.getX() - job.lastX) > 0.6 || Math.abs(mc.player.getZ() - job.lastZ) > 0.6;
        if (moved || job.mined != job.progressMark) {
            job.lastX = mc.player.getX();
            job.lastZ = mc.player.getZ();
            job.progressMark = job.mined;
            job.progressAt = clock;
        } else if (clock - job.progressAt > 25_000L && waitsOnScreen(job.kind) == false) {
            giveUp(mc, "stuck: nothing changed for 25 seconds");
            return;
        }
        switch (job.kind) {
            case CHOP -> chopTick(mc);
            case MINE_UNTIL -> mineUntilTick(mc);
            case MINE_COUNT -> mineCountTick(mc);
            case GOTO -> {
                if (job.to == null || navigate(mc, job.to)) endJob(mc, "arrived");
            }
            case FOLLOW -> followTick(mc);
            case COLLECT -> collectTick(mc);
            case TUNNEL -> tunnelTick(mc);
            case FARM -> farmTick(mc);
            case SLEEP -> sleepTick(mc);
            case REPLANT -> replantTick(mc);
            case EAT -> eatTick(mc);
            case FOOD -> foodTick(mc);
            case FISH -> fishTick(mc);
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
            case SCHEMATIC -> schematicTick(mc);
            case BREED -> breedTick(mc);
            case PLANT -> plantTick(mc);
            case GUARD -> guardTick(mc);
            case ARMOR -> armorTick(mc);
            case SWIM -> swimTick(mc);
            case CLIMB -> climbTick(mc);
            case BRIDGE -> bridgeTick(mc);
            case TILL -> tillTick(mc);
            case CRAFT -> craftTick(mc);
            case SMELT -> smeltTick(mc);
            case STORE -> storeTick(mc);
            case TAKE -> takeTick(mc);
        }
    }

    // Rather than giving up on a recipe, work out which job produces the ingredient that is short.
    private java.util.List<String> gatherPlan(Minecraft mc, Craft.Recipe recipe) {
        java.util.function.Predicate<ItemStack> cell = Craft.missing(mc.player.inventoryMenu, recipe);
        if (cell == null) return java.util.List.of();
        if (cell == Craft.LOGS) return java.util.List.of("chop 2 trees");
        if (cell == Craft.COBBLE) return java.util.List.of("mine 16 stone");
        if (cell == Craft.COAL) return java.util.List.of("mine 8 coal");
        if (cell == Craft.WOOL) return java.util.List.of("shear 3");
        if (cell == Craft.IRON) return java.util.List.of("mine 6 iron", "smelt 6 iron");
        return java.util.List.of();
    }

    private void craftTick(Minecraft mc) {
        Craft.Recipe recipe = Craft.find(job.target);
        Craft.Book book = recipe == null ? Craft.book(mc, job.target) : null;
        if (recipe == null && book == null) {
            giveUp(mc, "I do not know a recipe for " + job.target.replace('_', ' '));
            return;
        }
        boolean table = recipe != null ? !recipe.small() : book.table();
        String shown = job.target.replace('_', ' ');
        if (mc.screen != null) {
            AbstractContainerMenu menu = mc.player.containerMenu;
            boolean right = table ? menu instanceof CraftingMenu : menu instanceof InventoryMenu;
            if (!right) {
                mc.setScreen(null);
                return;
            }
            if (recipe == null) {
                bookCraft(mc, menu, book, shown);
                return;
            }
            if (!Craft.ready(menu, recipe, table)) {
                job.note = "laying " + shown + " out in the grid";
                Craft.fill(mc, menu, recipe, table);
                if (!Craft.hasAll(menu, recipe) && ++job.step > 6) {
                    mc.setScreen(null);
                    job.step = 0;
                }
                return;
            }
            ItemStack out = menu.getSlot(0).getItem();
            if (out.isEmpty()) {
                if (++job.step > 60) {
                    mc.setScreen(null);
                    giveUp(mc, "the recipe never produced anything");
                }
                return;
            }
            job.crafts += out.getCount();
            Craft.click(mc, menu, 0, 0, ClickType.QUICK_MOVE);
            job.note = "crafted " + shown + " (" + job.crafts + "/" + Math.max(1, job.wanted) + ")";
            if (job.crafts >= Math.max(1, job.wanted)) {
                mc.setScreen(null);
                endJob(mc, "crafted " + job.crafts + " x " + shown);
            }
            return;
        }
        if (job.crafts >= Math.max(1, job.wanted)) {
            endJob(mc, "crafted " + job.crafts + " x " + shown);
            return;
        }
        job.step = 0;
        AbstractContainerMenu backpack = mc.player.inventoryMenu;
        if (recipe != null && !Craft.hasAll(backpack, recipe)) {
            Craft.Recipe sub = Craft.subFor(backpack, recipe);
            if (sub != null && job.attempts++ < 4) {
                chain.addFirst("craft " + Math.max(1, job.wanted) + " " + recipe.name());
                startOne("craft " + Math.max(1, job.wanted) + " " + sub.name());
                return;
            }
            java.util.List<String> plan = gatherPlan(mc, recipe);
            if (!plan.isEmpty() && job.attempts++ < 3) {
                for (int i = 1; i < plan.size(); i++) chain.addLast(plan.get(i));
                chain.addLast("craft " + Math.max(1, job.wanted) + " " + recipe.name());
                startOne(plan.get(0));
                return;
            }
            giveUp(mc, "no materials for " + shown);
            return;
        }
        if (!table) {
            job.note = "opening my inventory";
            mc.setScreen(new InventoryScreen(mc.player));
            return;
        }
        BlockPos bench = stationAt(mc, state -> state.is(Blocks.CRAFTING_TABLE), 5);
        if (bench == null) {
            if (ensureHotbar(mc, s -> s.is(Items.CRAFTING_TABLE)) < 0) {
                if (job.attempts++ < 2 && !job.target.contains("crafting_table")) {
                    String resume = "craft " + Math.max(1, job.wanted) + " " + job.target.replace('_', ' ');
                    chain.addFirst(resume);
                    Notify.info("Zeus AI: making a crafting table first");
                    if (startOne("craft 1 crafting table")) return;
                }
                giveUp(mc, "no crafting table, craft one first");
                return;
            }
            BlockPos spot = spotFor(mc);
            if (spot == null) {
                job.note = "no room for a crafting table";
                return;
            }
            lookAt(mc.player, centre(spot));
            job.note = "placing a crafting table";
            if (Build.place(spot, s -> s.is(Items.CRAFTING_TABLE), true, Build.REACH)) job.note = "placed a crafting table";
            return;
        }
        job.note = "opening the crafting table";
        openStation(mc, bench);
    }

    // The vanilla recipe book fills the grid for us, so anything the player has unlocked is fair game.
    private void bookCraft(Minecraft mc, AbstractContainerMenu menu, Craft.Book book, String shown) {
        if (job.step == 0) {
            Craft.bookPlace(mc, menu, book.entry());
            job.step = 1;
            job.note = "asking the recipe book for " + shown;
            return;
        }
        ItemStack out = menu.getSlot(0).getItem();
        if (!out.isEmpty()) {
            job.crafts += out.getCount();
            Craft.click(mc, menu, 0, 0, ClickType.QUICK_MOVE);
            job.step = 0;
            job.note = "crafted " + shown + " (" + job.crafts + "/" + Math.max(1, job.wanted) + ")";
            if (job.crafts >= Math.max(1, job.wanted)) {
                mc.setScreen(null);
                endJob(mc, "crafted " + job.crafts + " x " + shown);
            }
            return;
        }
        if (++job.step > 80) {
            mc.setScreen(null);
            giveUp(mc, "not enough materials for " + shown);
        }
    }

    private void storeTick(Minecraft mc) {
        java.util.function.Predicate<ItemStack> want = Craft.only(job.target);
        if (want == null) {
            giveUp(mc, "I do not know the item " + job.target.replace('_', ' '));
            return;
        }
        String shown = job.target.replace('_', ' ');
        int limit = Math.max(0, job.wanted);
        if (mc.screen != null) {
            AbstractContainerMenu menu = mc.player.containerMenu;
            if (!(menu instanceof net.minecraft.world.inventory.ChestMenu)) {
                mc.setScreen(null);
                return;
            }
            if (limit > 0 && job.mined >= limit) {
                mc.setScreen(null);
                endJob(mc, "stored " + job.mined + " x " + shown);
                return;
            }
            int from = Craft.find(menu, want);
            if (from < 0) {
                mc.setScreen(null);
                endJob(mc, "stored " + job.mined + " x " + shown);
                return;
            }
            ItemStack stack = menu.getSlot(from).getItem();
            Craft.click(mc, menu, from, 0, ClickType.QUICK_MOVE);
            job.mined += stack.getCount();
            job.note = "storing " + shown + " (" + job.mined + ")";
            return;
        }
        BlockPos chest = chestNear(mc);
        if (chest == null) {
            giveUp(mc, "no chest nearby");
            return;
        }
        job.note = "opening a chest";
        openStation(mc, chest);
    }

    private void takeTick(Minecraft mc) {
        java.util.function.Predicate<ItemStack> want = Craft.only(job.target);
        if (want == null) {
            giveUp(mc, "I do not know the item " + job.target.replace('_', ' '));
            return;
        }
        String shown = job.target.replace('_', ' ');
        int limit = Math.max(0, job.wanted);
        if (mc.screen != null) {
            AbstractContainerMenu menu = mc.player.containerMenu;
            if (!(menu instanceof net.minecraft.world.inventory.ChestMenu)) {
                mc.setScreen(null);
                return;
            }
            if (limit > 0 && job.mined >= limit) {
                mc.setScreen(null);
                endJob(mc, "took " + job.mined + " x " + shown);
                return;
            }
            int chestSlots = menu.slots.size() - 36;
            for (int i = 0; i < chestSlots; i++) {
                ItemStack stack = menu.getSlot(i).getItem();
                if (stack.isEmpty() || !want.test(stack)) continue;
                Craft.click(mc, menu, i, 0, ClickType.QUICK_MOVE);
                job.mined += stack.getCount();
                job.note = "taking " + shown + " (" + job.mined + ")";
                return;
            }
            mc.setScreen(null);
            endJob(mc, "took " + job.mined + " x " + shown);
            return;
        }
        BlockPos chest = chestNear(mc);
        if (chest == null) {
            giveUp(mc, "no chest nearby");
            return;
        }
        job.note = "opening a chest";
        openStation(mc, chest);
    }

    private BlockPos chestNear(Minecraft mc) {
        return stationAt(mc, state -> state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST) || state.is(Blocks.BARREL), 5);
    }

    private void smeltTick(Minecraft mc) {
        String shown = job.target.replace('_', ' ');
        java.util.function.Predicate<ItemStack> input = Craft.smeltInput(job.target);
        if (input == null) {
            giveUp(mc, "I do not know the item " + shown);
            return;
        }
        if (mc.screen != null) {
            AbstractContainerMenu menu = mc.player.containerMenu;
            if (!(menu instanceof FurnaceMenu)) {
                mc.setScreen(null);
                return;
            }
            ItemStack out = menu.getSlot(2).getItem();
            if (!out.isEmpty()) {
                job.crafts += out.getCount();
                Craft.click(mc, menu, 2, 0, ClickType.QUICK_MOVE);
                job.note = "took " + job.crafts + " out of the furnace";
                if (job.crafts >= Math.max(1, job.wanted)) {
                    mc.setScreen(null);
                    endJob(mc, "smelted " + job.crafts + " x " + shown);
                }
                return;
            }
            if (menu.getSlot(0).getItem().isEmpty()) {
                int from = Craft.find(menu, input);
                if (from < 0) {
                    mc.setScreen(null);
                    giveUp(mc, "no " + shown + " to smelt");
                    return;
                }
                Craft.click(mc, menu, from, 0, ClickType.QUICK_MOVE);
                job.note = "loading the furnace";
                return;
            }
            if (menu.getSlot(1).getItem().isEmpty()) {
                int fuel = Craft.find(menu, Craft::fuel);
                if (fuel < 0) {
                    giveUp(mc, "no fuel for the furnace");
                    return;
                }
                Craft.click(mc, menu, fuel, 0, ClickType.QUICK_MOVE);
                job.note = "adding fuel";
                return;
            }
            job.note = "waiting for the furnace";
            return;
        }
        if (job.crafts >= Math.max(1, job.wanted)) {
            endJob(mc, "smelted " + job.crafts + " x " + shown);
            return;
        }
        BlockPos oven = stationAt(mc, state -> state.is(Blocks.FURNACE) || state.is(Blocks.BLAST_FURNACE) || state.is(Blocks.SMOKER), 5);
        if (oven == null) {
            if (ensureHotbar(mc, s -> s.is(Items.FURNACE)) < 0) {
                if (job.attempts++ < 2 && !job.target.contains("furnace")) {
                    String resume = "smelt " + Math.max(1, job.wanted) + " " + job.target.replace('_', ' ');
                    chain.addFirst(resume);
                    Notify.info("Zeus AI: making a furnace first");
                    if (startOne("craft 1 furnace")) return;
                }
                giveUp(mc, "no furnace, craft one first");
                return;
            }
            BlockPos spot = spotFor(mc);
            if (spot == null) {
                job.note = "no room for a furnace";
                return;
            }
            lookAt(mc.player, centre(spot));
            job.note = "placing a furnace";
            Build.place(spot, s -> s.is(Items.FURNACE), true, Build.REACH);
            return;
        }
        job.note = "opening the furnace";
        openStation(mc, oven);
    }

    private BlockPos stationAt(Minecraft mc, java.util.function.Predicate<BlockState> match, int radius) {
        return search(mc, mc.player.blockPosition(), radius, 3, match);
    }

    private BlockPos spotFor(Minecraft mc) {
        Player p = mc.player;
        BlockPos ahead = p.blockPosition().relative(facing(p.getYRot()));
        if (Build.solid(ahead.below()) && Build.canPlace(ahead, Build.REACH)) return ahead;
        if (Build.canPlace(p.blockPosition(), Build.REACH)) return p.blockPosition();
        return null;
    }

    private void openStation(Minecraft mc, BlockPos pos) {
        lookAt(mc.player, centre(pos));
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                new BlockHitResult(centre(pos), Direction.UP, pos, false));
    }

    private static boolean itemMatches(ItemStack stack, String wanted) {
        return Craft.itemMatches(stack, wanted);
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
        if (job.lastFound == 0 || !job.block.equals(job.ore)) {
            job.ore = job.block;
            job.lastFound = now;
        } else if (now - job.lastFound > 12_000L) {
            SKIP_LOGS.add(job.block);
            job.note = "skipping a log it cannot reach";
            job.block = null;
            job.lastFound = 0;
            return;
        }
        if (!mineBlock(mc, job.block)) return;
        if (job == null) return;
        job.mined++;
        job.block = null;
        job.lastFound = 0;
    }

    private void tillTick(Minecraft mc) {
        if (job.mined >= Math.max(1, job.wanted)) {
            endJob(mc, "tilled " + job.mined + " block(s)");
            return;
        }
        int hoe = Inv.hotbar(s -> s.getItem() instanceof net.minecraft.world.item.HoeItem);
        if (hoe < 0) {
            Craft.Recipe recipe = Craft.find("stone_hoe");
            if (recipe != null && job.attempts++ == 0 && Craft.hasAll(mc.player.inventoryMenu, recipe)) {
                chain.addFirst("till " + Math.max(1, job.wanted));
                startOne("craft 1 stone hoe");
                return;
            }
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

    /**
     * Kill animals for food: stalk one animal at a time, hit it when the attack is charged, then
     * move on. It stops with meat in the pack instead of swinging at nothing.
     */
    private void foodTick(Minecraft mc) {
        Player p = mc.player;
        int want = Math.max(1, job.wanted);
        int meat = countMeat(mc);
        if (meat >= want) {
            endJob(mc, "got " + meat + " meat");
            return;
        }
        net.minecraft.world.entity.Entity prey = nearestPrey(mc, 26);
        if (prey == null) {
            endJob(mc, meat > 0 ? "got " + meat + " meat" : "no animals within 26 blocks");
            return;
        }
        int weapon = bestWeapon(mc);
        if (weapon >= 0 && Inv.selected() != weapon) Inv.select(weapon);
        lookAt(p, prey.getEyePosition());
        job.note = "hunting a " + prey.getName().getString() + " (" + meat + "/" + want + " meat)";
        if (p.distanceTo(prey) > 2.8) {
            walkTo(mc, prey.position(), 1.8);
            return;
        }
        mc.options.keyUp.setDown(false);
        mc.options.keyJump.setDown(false);
        if (p.getAttackStrengthScale(0.5f) < 0.9f) return;
        mc.gameMode.attack(p, prey);
        p.swing(InteractionHand.MAIN_HAND);
    }

    /**
     * Fishing: cast, watch the bobber, reel when it dips, and count what comes out. The bobber dip
     * is read from its own motion, which is what the game does for the sound, so no internals are
     * needed and it works on any server that sends entity movement.
     */
    private void fishTick(Minecraft mc) {
        Player p = mc.player;
        int want = Math.max(1, job.wanted);
        if (job.mined >= want) {
            endJob(mc, "caught " + job.mined + " fish");
            return;
        }
        int rod = Inv.hotbar(s -> s.is(Items.FISHING_ROD));
        if (rod < 0) {
            giveUp(mc, "no fishing rod in the hotbar");
            return;
        }
        if (rod != Inv.selected()) Inv.select(rod);
        releaseKeys(mc);
        long now = System.currentTimeMillis();
        net.minecraft.world.entity.projectile.FishingHook hook = p.fishing;
        if (hook == null) {
            if (now < job.nextSearch) return;
            job.nextSearch = now + 900L;
            p.setXRot(20f);
            mc.gameMode.useItem(p, InteractionHand.MAIN_HAND);
            job.note = "casting the line (" + job.mined + "/" + want + " fish)";
            job.step = 0;
            return;
        }
        if (job.step == 0) {
            job.step = 1;
            job.actionAt = now;
        }
        double dip = hook.getY() - (job.to == null ? hook.getY() : job.to.y);
        if (job.to == null || hook.getY() < job.to.y - 0.08) job.to = new Vec3(hook.getX(), hook.getY(), hook.getZ());
        boolean bite = hook.isInWater() && (hook.getDeltaMovement().y < -0.02 || dip < -0.12);
        if (bite) {
            mc.gameMode.useItem(p, InteractionHand.MAIN_HAND);
            job.mined++;
            job.step = 0;
            job.to = null;
            job.nextSearch = now + 700L;
            job.note = "reeling in (" + job.mined + "/" + want + " fish)";
            itemPickup(mc);
            return;
        }
        if (now - job.actionAt > 35_000L) {
            // nothing bit: reel in and cast again rather than waiting forever
            mc.gameMode.useItem(p, InteractionHand.MAIN_HAND);
            job.step = 0;
            job.to = null;
            job.nextSearch = now + 1200L;
            job.note = "nothing bit, recasting";
            return;
        }
        job.note = "waiting for a bite (" + job.mined + "/" + want + " fish)";
    }

    /** Walk over nearby drops so anything the AI just produced is actually in the pack. */
    private void itemPickup(Minecraft mc) {
        ItemEntity drop = nearestDrop(mc, 8);
        if (drop != null) walkTo(mc, drop.position(), 1.0);
    }

    /** Animals worth killing for food, ignoring babies and anything already in water. */
    private net.minecraft.world.entity.Entity nearestPrey(Minecraft mc, double radius) {
        return nearestEntity(mc, radius, e -> {
            if (!(e instanceof net.minecraft.world.entity.animal.Animal animal) || !animal.isAlive()) return false;
            if (animal.isBaby() || animal.isInWater()) return false;
            var type = e.getType();
            return type == net.minecraft.world.entity.EntityType.COW || type == net.minecraft.world.entity.EntityType.PIG
                    || type == net.minecraft.world.entity.EntityType.SHEEP || type == net.minecraft.world.entity.EntityType.CHICKEN
                    || type == net.minecraft.world.entity.EntityType.RABBIT;
        });
    }

    private static int countMeat(Minecraft mc) {
        return countItems(mc, s -> s.is(Items.BEEF) || s.is(Items.PORKCHOP) || s.is(Items.MUTTON)
                || s.is(Items.CHICKEN) || s.is(Items.RABBIT)
                || s.is(Items.COOKED_BEEF) || s.is(Items.COOKED_PORKCHOP) || s.is(Items.COOKED_MUTTON)
                || s.is(Items.COOKED_CHICKEN) || s.is(Items.COOKED_RABBIT));
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

    private static int weaponRank(ItemStack s) {
        if (s.isEmpty()) return 0;
        String id = BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
        int tier = id.startsWith("netherite") ? 6 : id.startsWith("diamond") ? 5 : id.startsWith("iron") ? 4
                : id.startsWith("stone") ? 3 : id.startsWith("golden") ? 2 : id.startsWith("wooden") ? 1 : 0;
        if (id.endsWith("_sword")) return 20 + tier * 2;
        if (id.endsWith("_axe")) return 10 + tier * 2;
        if (id.equals("mace") || id.equals("trident")) return 30;
        return 0;
    }

    private static int bestWeapon(Minecraft mc) {
        int bestSlot = -1, bestRank = 0;
        for (int i = 0; i < 9; i++) {
            int r = usable(Inv.get(i)) ? weaponRank(Inv.get(i)) : 0;
            if (r > bestRank) {
                bestRank = r;
                bestSlot = i;
            }
        }
        if (bestSlot >= 0) return bestSlot;
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

    // ------------------------------------------------------------------ schematics
    private static SchematicPlan schem;
    private static int schemIndex;
    private static int schemSkipped;
    private static final java.util.Map<net.minecraft.world.item.Item, Integer> schemTried = new java.util.HashMap<>();
    private static long schemCellSince;
    private static int schemDeferred;

    /** What is still missing for the rest of the build, as item -> count. */
    private java.util.Map<net.minecraft.world.item.Item, Integer> schemMissing(Minecraft mc) {
        java.util.Map<net.minecraft.world.item.Item, Integer> need = new java.util.LinkedHashMap<>();
        for (int i = schemIndex; i < schem.order.size(); i++) {
            SchematicPlan.Place pl = schem.order.get(i);
            if (mc.level.getBlockState(pl.at()).is(pl.block())) continue;
            need.merge(pl.item(), 1, Integer::sum);
        }
        java.util.Map<net.minecraft.world.item.Item, Integer> missing = new java.util.LinkedHashMap<>();
        for (var e : need.entrySet()) {
            net.minecraft.world.item.Item item = e.getKey();
            int have = countItems(mc, st -> st.is(item));
            if (have < e.getValue()) missing.put(item, e.getValue() - have);
        }
        return missing;
    }

    /** "materials for <schematic>" as chat lines: what it needs, what you have, what it can get itself. */
    public static java.util.List<String> schematicReport(String name) {
        java.util.List<String> out = new java.util.ArrayList<>();
        java.nio.file.Path file = SchematicPlan.find(name);
        if (file == null) {
            out.add(SchematicPlan.files().isEmpty() ? "No schematics in " + SchematicPlan.folders().get(0) : "No schematic called " + name);
            return out;
        }
        Minecraft mc = Minecraft.getInstance();
        try {
            SchematicPlan plan = SchematicPlan.load(file, BlockPos.ZERO);
            out.add(plan.name + ": " + plan.order.size() + " blocks" + (plan.unplaceable > 0 ? " (+" + plan.unplaceable + " without an item)" : ""));
            int shown = 0;
            for (var e : plan.needs.entrySet()) {
                int have = mc.player == null ? 0 : countItems(mc, st -> st.is(e.getKey()));
                String how = SchematicPlan.jobFor(e.getKey(), e.getValue());
                out.add("  " + e.getValue() + " x " + SchematicPlan.itemName(e.getKey()).replace('_', ' ') + "  (have " + have
                        + (have >= e.getValue() ? ", ok)" : how == null ? ", bring these)" : ", will " + how.split(" ")[0] + ")"));
                if (++shown >= 14) {
                    if (plan.needs.size() > shown) out.add("  ... and " + (plan.needs.size() - shown) + " more");
                    break;
                }
            }
        } catch (Exception e) {
            out.add("Could not read " + SchematicPlan.display(file) + ": " + e.getMessage());
        }
        return out;
    }

    /**
     * Before a long job starts: if it needs a kind of tool the player has none of (axe for trees,
     * pickaxe for stone and ore, shovel for dirt, sand and gravel) and the best one we can make is
     * affordable, make it first and come back to the job.  Returns true when a craft was started.
     */
    private boolean toolUp(Minecraft mc) {
        String kind = switch (job.kind) {
            case CHOP -> "axe";
            case MINE_UNTIL, QUARRY, TUNNEL -> "pickaxe";
            case MINE_COUNT -> {
                String t = job.target == null ? "" : job.target;
                yield t.contains("dirt") || t.contains("sand") || t.contains("gravel") || t.contains("clay") ? "shovel"
                        : t.equals("down") || t.equals("up") || productOf(t) != null ? "pickaxe" : "";
            }
            default -> "";
        };
        if (kind.isEmpty() || job.wanted < 3 && job.kind != JobKind.MINE_UNTIL && job.kind != JobKind.CHOP) return false;
        String suffix = "_" + kind;
        if (countItems(mc, st -> usable(st) && BuiltInRegistries.ITEM.getKey(st.getItem()).getPath().endsWith(suffix)) > 0) return false;
        for (String tier : new String[] {"iron", "stone", "wooden"}) {
            Craft.Recipe r = Craft.find(tier + suffix);
            if (r == null || !Craft.hasAll(mc.player.inventoryMenu, r) && !hasAllAnywhere(mc, r)) continue;
            String resume = resumeCommand(job);
            Notify.info("Zeus AI: making a " + tier + " " + kind + " first");
            AiTutorial.chat("No " + kind + " on me, making a " + tier + " " + kind + " first, then " + resume + ".", false);
            if (startOne("craft 1 " + tier + " " + kind)) {
                chain.addFirst(resume);
                return true;
            }
        }
        return false;
    }

    /** Crafting-table recipes are checked against the whole inventory, not the 2x2 grid's view. */
    private static boolean hasAllAnywhere(Minecraft mc, Craft.Recipe r) {
        java.util.Map<java.util.function.Predicate<ItemStack>, Integer> need = new java.util.IdentityHashMap<>();
        for (java.util.function.Predicate<ItemStack> cell : r.cells()) if (cell != null) need.merge(cell, 1, Integer::sum);
        for (var e : need.entrySet()) if (countItems(mc, e.getKey()) < e.getValue()) return false;
        return true;
    }

    /** How much raw material a crafted building part takes per item, roughly. */
    private static float rawPer(String id) {
        if (id.endsWith("_planks")) return 0.25f;            // logs per plank
        if (id.endsWith("_slab")) return 0.5f;
        if (id.endsWith("_stairs")) return 1.5f;
        if (id.endsWith("_fence") || id.endsWith("_fence_gate")) return 1.7f;
        if (id.endsWith("_trapdoor")) return 3f;
        if (id.endsWith("_door")) return 2f;
        if (id.endsWith("_wall")) return 1f;
        if (id.equals("stone_bricks")) return 1f;
        if (id.equals("chest") || id.equals("furnace")) return 8f;
        if (id.equals("crafting_table")) return 4f;
        if (id.equals("ladder") || id.equals("stick")) return 0.6f;
        return 0f;
    }

    /** A gathering job for the raw material a crafted part needs, when the inventory is short of it. */
    private String baseJob(Minecraft mc, net.minecraft.world.item.Item item, int count) {
        String id = SchematicPlan.itemName(item);
        float per = rawPer(id);
        if (per <= 0f) return null;
        boolean stone = id.startsWith("cobblestone") || id.startsWith("stone") || id.equals("furnace");
        if (stone) {
            int need = (int) Math.ceil(count * per) - countItems(mc, st -> st.is(Items.COBBLESTONE) || st.is(Items.STONE));
            return need > 0 ? "mine " + need + " cobblestone" : null;
        }
        if (id.endsWith("_planks")) {
            int logs = (int) Math.ceil(count * per) - countItems(mc, Craft.LOGS);
            return logs > 0 ? "chop " + Math.max(1, (logs + 4) / 5) + " trees" : null;
        }
        // wooden parts: planks first (counted as logs worth)
        int planks = (int) Math.ceil(count * per);
        int have = countItems(mc, Craft.PLANKS) + countItems(mc, Craft.LOGS) * 4;
        if (have >= planks) return null;
        int logs = (planks - have + 3) / 4;
        return "chop " + Math.max(1, (logs + 4) / 5) + " trees";
    }

    private void schematicTick(Minecraft mc) {
        if (schem == null) {
            giveUp(mc, "lost the schematic");
            return;
        }
        if (!job.phase) {
            java.util.Map<net.minecraft.world.item.Item, Integer> missing = schemMissing(mc);
            for (var e : missing.entrySet()) {
                net.minecraft.world.item.Item item = e.getKey();
                int tries = schemTried.getOrDefault(item, 0);
                String how = SchematicPlan.jobFor(item, e.getValue());
                if (how == null || tries >= 2) continue;
                schemTried.put(item, tries + 1);
                String resume = "build schematic " + schem.name;
                String name = SchematicPlan.itemName(item).replace('_', ' ');
                // crafted parts need their raw material first: logs for wooden parts, cobblestone for stone ones
                String base = baseJob(mc, item, e.getValue());
                if (base != null) {
                    AiTutorial.chat("Getting the raw material for " + e.getValue() + " " + name + " first: " + base, false);
                    if (startOne(base)) {
                        chain.addFirst(resume);
                        return;
                    }
                }
                job.note = "getting " + e.getValue() + " " + name;
                AiTutorial.chat("Need " + e.getValue() + " more " + name + " for " + schem.name + ": " + how, false);
                if (startOne(how)) {
                    chain.addFirst(resume);
                    return;
                }
            }
            java.util.List<String> lacking = new java.util.ArrayList<>();
            for (var e : missing.entrySet()) {
                lacking.add(e.getValue() + " " + SchematicPlan.itemName(e.getKey()).replace('_', ' '));
                if (lacking.size() >= 6) break;
            }
            if (!lacking.isEmpty()) {
                AiTutorial.chat("Building " + schem.name + " with what I have. I could not get: " + String.join(", ", lacking)
                        + ". Put them in your inventory and say build schematic " + schem.name + " again to finish.", true);
            }
            job.phase = true;
            job.wanted = schem.order.size();
            schemCellSince = System.currentTimeMillis();
        }
        // skip finished cells
        while (schemIndex < schem.order.size() && mc.level.getBlockState(schem.order.get(schemIndex).at()).is(schem.order.get(schemIndex).block())) {
            schemIndex++;
            job.mined++;
        }
        if (schemIndex >= schem.order.size()) {
            String done = schem.name + " finished: " + job.mined + " blocks" + (schemSkipped > 0 ? ", " + schemSkipped + " skipped" : "");
            schem = null;
            endJob(mc, done);
            return;
        }
        SchematicPlan.Place pl = schem.order.get(schemIndex);
        long now = System.currentTimeMillis();
        if (now - schemCellSince > 9000L) {
            // stuck on this block: try it again at the end of the build, then give up on it
            schemCellSince = now;
            if (schemDeferred < schem.order.size()) {
                schem.order.add(schem.order.remove(schemIndex));
                schemDeferred++;
            } else {
                schemIndex++;
                schemSkipped++;
            }
            return;
        }
        java.util.function.Predicate<ItemStack> item = st -> st.is(pl.item());
        if (countItems(mc, item) <= 0) {
            boolean canGet = SchematicPlan.jobFor(pl.item(), 1) != null && schemTried.getOrDefault(pl.item(), 0) < 2;
            if (canGet) {
                job.phase = false;
                return;
            }
            schemIndex++;
            schemSkipped++;
            schemCellSince = now;
            return;
        }
        BlockState there = mc.level.getBlockState(pl.at());
        if (!there.isAir() && !there.canBeReplaced()) {
            job.note = "clearing " + pl.at().getX() + " " + pl.at().getY() + " " + pl.at().getZ();
            mineBlock(mc, pl.at());
            return;
        }
        // never build into ourselves
        if (mc.player.getBoundingBox().intersects(new net.minecraft.world.phys.AABB(pl.at()))) {
            BlockPos away = pl.at().relative(facing(mc.player.getYRot()).getOpposite(), 2);
            walkTo(mc, centre(away), 0.5);
            return;
        }
        if (Build.distTo(pl.at()) > Build.REACH - 0.5) {
            job.note = "walking to the next block (" + job.mined + "/" + job.wanted + ")";
            walkTo(mc, centre(pl.at()), 2.5);
            return;
        }
        releaseKeys(mc);
        if (ensureHotbar(mc, item) < 0) return;
        lookAt(mc.player, centre(pl.at()));
        if (Build.against(pl.at(), Build.REACH) == null) {
            // nothing to place against yet; come back to it after its neighbours
            if (schemDeferred < schem.order.size()) {
                schem.order.add(schem.order.remove(schemIndex));
                schemDeferred++;
            } else {
                schemIndex++;
                schemSkipped++;
            }
            schemCellSince = now;
            return;
        }
        if (Build.place(pl.at(), item, true, Build.REACH)) {
            job.mined++;
            schemIndex++;
            schemCellSince = now;
            job.note = "building " + schem.name + " (" + job.mined + "/" + job.wanted + ")";
        }
    }

    // ------------------------------------------------------------------ animals, saplings, guard, armour
    private void breedTick(Minecraft mc) {
        long now = System.currentTimeMillis();
        if (now - job.started > 90_000L) {
            endJob(mc, "fed " + job.mined + " animal(s)");
            return;
        }
        String only = job.target == null ? "" : job.target.replaceAll("s$", "");
        net.minecraft.world.entity.animal.Animal best = null;
        double bestD = 20 * 20;
        for (net.minecraft.world.entity.Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof net.minecraft.world.entity.animal.Animal a) || !a.isAlive() || a.isBaby() || a.isInLove()) continue;
            if (!only.isEmpty() && !BuiltInRegistries.ENTITY_TYPE.getKey(a.getType()).getPath().contains(only)) continue;
            if (countItems(mc, a::isFood) <= 0) continue;
            double d = a.distanceToSqr(mc.player);
            if (d < bestD) {
                bestD = d;
                best = a;
            }
        }
        if (best == null) {
            if (job.quietSince == 0) job.quietSince = now;
            else if (now - job.quietSince > 3000L) endJob(mc, job.mined == 0 ? "no animals to feed (or no food they eat)" : "fed " + job.mined + " animal(s)");
            return;
        }
        job.quietSince = 0;
        net.minecraft.world.entity.animal.Animal target = best;
        job.note = "feeding a " + BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).getPath().replace('_', ' ');
        if (target.distanceTo(mc.player) > 2.8) {
            walkTo(mc, target.position(), 1.8);
            return;
        }
        releaseKeys(mc);
        if (now < job.actionAt) return;
        job.actionAt = now + 350L;
        java.util.function.Predicate<ItemStack> food = target::isFood;
        int slot = ensureHotbar(mc, food);
        if (slot < 0) return;
        if (!Inv.holding(food)) Inv.select(slot);
        lookAt(mc.player, target.position().add(0, target.getBbHeight() * 0.6, 0));
        mc.gameMode.interact(mc.player, target, InteractionHand.MAIN_HAND);
        mc.player.swing(InteractionHand.MAIN_HAND);
        job.mined++;
    }

    private void plantTick(Minecraft mc) {
        if (job.mined >= Math.max(1, job.wanted)) {
            endJob(mc, "planted " + job.mined + " sapling(s)");
            return;
        }
        java.util.function.Predicate<ItemStack> sapling = st -> st.is(net.minecraft.tags.ItemTags.SAPLINGS);
        if (countItems(mc, sapling) <= 0) {
            endJob(mc, job.mined == 0 ? "no saplings in the inventory" : "planted " + job.mined + ", out of saplings");
            return;
        }
        long now = System.currentTimeMillis();
        if (job.block == null || now >= job.nextSearch) {
            job.nextSearch = now + 1500L;
            job.block = find(mc, 10, 3, pos -> {
                BlockState soil = mc.level.getBlockState(pos);
                if (!(soil.is(Blocks.GRASS_BLOCK) || soil.is(Blocks.DIRT) || soil.is(Blocks.PODZOL) || soil.is(Blocks.COARSE_DIRT))) return false;
                if (!mc.level.getBlockState(pos.above()).isAir() || !mc.level.getBlockState(pos.above(2)).isAir()) return false;
                // room to grow: nothing woody within three blocks
                for (BlockPos q : BlockPos.betweenClosed(pos.offset(-3, 0, -3), pos.offset(3, 4, 3))) {
                    BlockState st = mc.level.getBlockState(q);
                    if (st.is(BlockTags.LOGS) || st.is(BlockTags.SAPLINGS)) return false;
                }
                return mc.level.canSeeSky(pos.above());
            });
        }
        if (job.block == null) {
            endJob(mc, job.mined == 0 ? "no open ground for saplings nearby" : "planted " + job.mined + " sapling(s)");
            return;
        }
        BlockPos at = job.block.above();
        if (Build.distTo(at) > Build.REACH - 0.6) {
            walkTo(mc, centre(at), 2.5);
            return;
        }
        releaseKeys(mc);
        if (ensureHotbar(mc, sapling) < 0) return;
        lookAt(mc.player, centre(job.block).add(0, 0.5, 0));
        if (Build.place(at, sapling, true, Build.REACH)) {
            job.mined++;
            job.block = null;
            job.note = "planted " + job.mined;
        }
    }

    private void guardTick(Minecraft mc) {
        Player owner = job.target.isBlank() ? null : byName(mc, job.target);
        if (owner == null && !job.target.isBlank()) owner = nearestPlayer(mc, 48);
        Vec3 home = owner != null ? owner.position() : mc.player.position();
        // fight anything hostile near whoever is being guarded
        net.minecraft.world.entity.Entity threat = null;
        double bestD = 8 * 8;
        for (net.minecraft.world.entity.Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof net.minecraft.world.entity.monster.Monster) || !e.isAlive()) continue;
            double d = e.position().distanceToSqr(home);
            if (d < bestD) {
                bestD = d;
                threat = e;
            }
        }
        if (threat != null) {
            job.note = "fighting a " + BuiltInRegistries.ENTITY_TYPE.getKey(threat.getType()).getPath().replace('_', ' ');
            if (threat.distanceTo(mc.player) > 3.0) {
                walkTo(mc, threat.position(), 2.2);
                return;
            }
            releaseKeys(mc);
            int weapon = bestWeapon(mc);
            if (weapon >= 0 && weapon != Inv.selected()) Inv.select(weapon);
            lookAt(mc.player, threat.position().add(0, threat.getBbHeight() * 0.6, 0));
            if (mc.player.getAttackStrengthScale(0.5f) >= 0.92f) {
                mc.gameMode.attack(mc.player, threat);
                mc.player.swing(InteractionHand.MAIN_HAND);
                job.mined++;
            }
            return;
        }
        job.note = owner == null ? "on guard here" : "staying close to " + owner.getName().getString();
        if (owner != null && owner.distanceTo(mc.player) > 4.0) walkTo(mc, owner.position(), 3.0);
        else releaseKeys(mc);
        job.started = System.currentTimeMillis();       // guarding has no time limit; say stop to end it
    }

    private static int armorRank(ItemStack s) {
        if (s.isEmpty()) return 0;
        String id = BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
        int base = id.startsWith("netherite") ? 7 : id.startsWith("diamond") ? 6 : id.startsWith("iron") ? 5
                : id.startsWith("chainmail") ? 4 : id.startsWith("turtle") ? 4 : id.startsWith("golden") ? 3 : id.startsWith("leather") ? 2 : 1;
        return base * 10 + (s.isEnchanted() ? 3 : 0);
    }

    private void armorTick(Minecraft mc) {
        long now = System.currentTimeMillis();
        if (now < job.actionAt) return;
        job.actionAt = now + 250L;
        if (mc.screen != null) return;
        for (net.minecraft.world.entity.EquipmentSlot slot : new net.minecraft.world.entity.EquipmentSlot[] {
                net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST,
                net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET}) {
            ItemStack worn = mc.player.getItemBySlot(slot);
            int wornRank = armorRank(worn);
            int bestInv = -1, bestRank = wornRank;
            for (int i = 0; i < 36; i++) {
                ItemStack st = Inv.get(i);
                var eq = st.get(net.minecraft.core.component.DataComponents.EQUIPPABLE);
                if (eq == null || eq.slot() != slot || st.is(Items.ELYTRA) && slot == net.minecraft.world.entity.EquipmentSlot.CHEST) continue;
                int r = armorRank(st);
                if (r > bestRank) {
                    bestRank = r;
                    bestInv = i;
                }
            }
            if (bestInv < 0) continue;
            int from = Inv.menuSlot(bestInv), to = Inv.armorMenuSlot(slot);
            // pick the better piece up, drop it on the armour slot, put the old one where it came from
            Inv.click(from, 0, ClickType.PICKUP);
            Inv.click(to, 0, ClickType.PICKUP);
            Inv.click(from, 0, ClickType.PICKUP);
            job.mined++;
            job.note = "put on " + BuiltInRegistries.ITEM.getKey(Inv.get(bestInv).getItem()).getPath().replace('_', ' ');
            return;
        }
        endJob(mc, job.mined == 0 ? "already wearing the best armour it has" : "put on " + job.mined + " better piece(s)");
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
        if (swimTo(mc, job.to, 0.8)) {
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

    /** True for the targets that live at a particular depth; stone, dirt and logs do not. */
    private static boolean isOre(String target) {
        return switch (oreKey(target)) {
            case "iron", "gold", "diamond", "redstone", "lapis", "emerald", "coal", "copper",
                 "ancient_debris", "debris", "nether_quartz", "quartz", "amethyst" -> true;
            default -> false;
        };
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
        // Look for the ore before anything else: the old order dug all the way to the ore's
        // usual depth first, so a vein right next to the player was walked straight past.
        XRay ray = Zeus.get(XRay.class);
        boolean seeThrough = ray != null && ray.enabled();
        int radius = seeThrough ? Math.min(48, jobRange.i() * 2) : jobRange.i();
        if (now >= job.nextScan) {
            job.nextScan = now + 600L;
            BlockPos spotted = nearestOre(mc, job.target, radius, 24);
            // Ore sealed inside stone is not a target: the old code walked at it and bounced off
            // the wall. Anything the player can already see is fair game.
            if (spotted != null && (seeThrough || exposed(mc, spotted))) job.ore = spotted;
        }
        if (job.ore != null) {
            BlockState ore = mc.level.getBlockState(job.ore);
            if (ore.isAir() || !matches(ore, job.target)) {
                if (ore.isAir()) reportFind(mc, job.ore, "mined");
                job.ore = null;
            } else if (!seeThrough && !exposed(mc, job.ore)) {
                job.ore = null;
            } else if (Build.distTo(job.ore) > jobReach.get()) {
                job.note = "walking to the " + job.target + " it can see";
                navigate(mc, centre(job.ore));
                return;
            } else {
                job.note = "mining the " + job.target;
                if (mineBlock(mc, job.ore)) reportFind(mc, job.ore, "mined");
                return;
            }
        }
        int wantY = isOre(job.target) ? bestY(oreKey(job.target)) : (int) Math.round(mc.player.getY());
        double above = mc.player.getY() - wantY;
        job.note = Math.abs(above) > 1.5
                ? "staircasing " + (above > 0 ? "down" : "up") + " to y " + wantY + " for " + job.target
                : "tunnelling at y " + Math.round(mc.player.getY()) + " for " + job.target;
        if (now < job.nextSearch) return;
        job.nextSearch = now + 100L;
        if (Math.abs(above) > 1.5) {
            Vec3 column = new Vec3(mc.player.getX(), wantY, mc.player.getZ());
            if (above > 0) stairDown(mc, column);
            else stairUp(mc, column);
            return;
        }
        tunnel(mc);
    }

    private void reportFind(Minecraft mc, BlockPos pos, String how) {
        String name = job.target.substring(0, 1).toUpperCase(Locale.ROOT) + job.target.substring(1);
        if (jobWaypoint.on()) {
            Extras.Waypoints points = Zeus.get(Extras.Waypoints.class);
            if (points != null) points.addAt(name, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        }
        endJob(mc, how + " " + name + " at " + pos.getX() + " " + pos.getY() + " " + pos.getZ());
    }

    private boolean navigate(Minecraft mc, Vec3 to) {
        Player p = mc.player;
        double dy = to.y - p.getY();
        double flat = Math.hypot(to.x - p.getX(), to.z - p.getZ());
        if (flat <= 1.6 && Math.abs(dy) <= 1.6) {
            releaseKeys(mc);
            return true;
        }
        if (p.isInWater() || p.isInLava()) return walkTo(mc, to, 1.6);
        long now = System.currentTimeMillis();
        if (job.verticalAt == 0) job.verticalAt = now + 3500L;
        if (!job.vertical) {
            boolean there = walkTo(mc, to, 1.6);
            if (job == null) return false;
            if (there) {
                // Standing over or under the target: the rest of the way is up or down.
                job.vertical = true;
                job.note = "the rest is up or down, mining to it";
                return false;
            }
            if (now > job.verticalAt || job.blocked >= 6) {
                job.vertical = true;
                job.note = "no way on foot, mining straight to the height";
            }
            return false;
        }
        if (Math.abs(dy) <= 1.2) {
            job.vertical = false;
            job.verticalAt = now + 3500L;
            job.blocked = 0;
            job.note = "back on the level, walking";
            return false;
        }
        if (dy < 0) stairDown(mc, to);
        else stairUp(mc, to);
        return false;
    }

    private void stairDown(Minecraft mc, Vec3 to) {
        if (job == null) return;
        Player p = mc.player;
        long now = System.currentTimeMillis();
        if (now < job.detourUntil) {
            job.note = "turning away from the lava";
            return;
        }
        double dx = to.x - p.getX(), dz = to.z - p.getZ();
        // Straight above or below the goal there is no direction to head in; keep one direction for
        // the whole staircase.  Following the camera made it wobble between two directions while it
        // looked at each block, which dug a spiral shaft that looked like digging straight down.
        Direction dir;
        if (Math.hypot(dx, dz) >= 1.5) {
            dir = facing((float) Math.toDegrees(Math.atan2(-dx, dz)));
        } else {
            if (job.stairDir == null) job.stairDir = facing(p.getYRot());
            dir = job.stairDir;
        }
        BlockPos feet = p.blockPosition();
        BlockPos step = feet.relative(dir);
        BlockPos floor = step.below();
        BlockPos under = floor.below();
        if (unsafe(mc, step) || unsafe(mc, step.above()) || unsafe(mc, floor) || unsafe(mc, under)
                || unsafeAround(mc, step) || unsafeAround(mc, floor) || unsafe(mc, step.above().above())) {
            rotateSideways(mc);
            return;
        }
        if (open(mc, floor) && open(mc, under)) {
            // the next step down opens onto a drop or a cave: bridge it with a block, or turn away
            java.util.function.Predicate<ItemStack> block = s -> !s.isEmpty() && s.getItem() instanceof net.minecraft.world.item.BlockItem bi
                    && !(bi.getBlock() instanceof net.minecraft.world.level.block.FallingBlock) && bi.getBlock().defaultBlockState().isSolid();
            if (ensureHotbar(mc, block) >= 0 && Build.replaceable(under)) {
                lookAt(p, centre(under));
                if (Build.place(under, block, true, Build.REACH)) {
                    job.note = "filling a drop under the staircase";
                    return;
                }
            }
            rotateSideways(mc);
            return;
        }
        if (!open(mc, step.above())) {
            job.note = "digging a staircase down";
            mineBlock(mc, step.above());
            return;
        }
        if (!open(mc, step)) {
            job.note = "digging a staircase down";
            mineBlock(mc, step);
            return;
        }
        if (!open(mc, floor)) {
            BlockState below = mc.level.getBlockState(under);
            if (below.getDestroySpeed(mc.level, under) < 0f || !below.getFluidState().isEmpty()) {
                rotateSideways(mc);
                return;
            }
            job.note = "cutting the next step down";
            mineBlock(mc, floor);
            return;
        }
        job.note = "walking down the staircase";
        walkTo(mc, centre(step), 0.6);
        support(mc);
    }

    private void stairUp(Minecraft mc, Vec3 to) {
        if (job == null) return;
        Player p = mc.player;
        long now = System.currentTimeMillis();
        if (now < job.detourUntil) {
            job.note = "turning away from the lava";
            return;
        }
        double dx = to.x - p.getX(), dz = to.z - p.getZ();
        // Straight above or below the goal there is no direction to head in; keep one direction for
        // the whole staircase.  Following the camera made it wobble between two directions while it
        // looked at each block, which dug a spiral shaft that looked like digging straight down.
        Direction dir;
        if (Math.hypot(dx, dz) >= 1.5) {
            dir = facing((float) Math.toDegrees(Math.atan2(-dx, dz)));
        } else {
            if (job.stairDir == null) job.stairDir = facing(p.getYRot());
            dir = job.stairDir;
        }
        BlockPos feet = p.blockPosition();
        BlockPos step = feet.relative(dir).above();
        if (unsafe(mc, step) || unsafe(mc, step.above())) {
            rotateSideways(mc);
            return;
        }
        if (!open(mc, step)) {
            job.note = "cutting steps up";
            mineBlock(mc, step);
            return;
        }
        if (!open(mc, step.above())) {
            job.note = "cutting headroom";
            mineBlock(mc, step.above());
            return;
        }
        if (!Build.solid(step.below())) {
            pillar(mc);
            return;
        }
        float yaw = dir.toYRot();
        p.setYRot(yaw);
        p.setYHeadRot(yaw);
        mc.options.keyUp.setDown(true);
        mc.options.keyJump.setDown(true);
        job.note = "jumping up the steps";
    }

    private void pillar(Minecraft mc) {
        if (job == null) return;
        Player p = mc.player;
        if (p.onGround()) {
            mc.options.keyJump.setDown(true);
            mc.options.keyUp.setDown(false);
            job.note = "jumping to tower up";
            return;
        }
        BlockPos below = p.blockPosition().below();
        if (!Build.replaceable(below)) {
            job.note = "no room to place a block under me";
            return;
        }
        java.util.function.Predicate<net.minecraft.world.item.ItemStack> block =
                s -> !s.isEmpty() && s.getItem() instanceof net.minecraft.world.item.BlockItem;
        if (ensureHotbar(mc, block) < 0) {
            giveUp(mc, "no blocks to tower up with");
            return;
        }
        lookAt(p, centre(below));
        mc.options.keyUp.setDown(false);
        mc.options.keyShift.setDown(false);
        if (Build.place(below, block, true, Build.REACH) && job != null) {
            job.mined++;
            job.note = "placing blocks under myself to climb";
        }
    }

    private void support(Minecraft mc) {
        if (job == null) return;
        Player p = mc.player;
        if (p.onGround() || p.getDeltaMovement().y > -0.35 || p.isInWater() || p.isInLava()) return;
        BlockPos below = p.blockPosition().below();
        int drop = 0;
        BlockPos probe = below;
        while (drop < 4 && !Build.solid(probe) && mc.level.getBlockState(probe).getFluidState().isEmpty()) {
            probe = probe.below();
            drop++;
        }
        if (drop < 2 || drop >= 4 || !Build.replaceable(below)) return;
        java.util.function.Predicate<net.minecraft.world.item.ItemStack> block =
                s -> !s.isEmpty() && s.getItem() instanceof net.minecraft.world.item.BlockItem;
        if (ensureHotbar(mc, block) < 0) return;
        lookAt(p, centre(below));
        if (Build.place(below, block, true, Build.REACH) && job != null) job.note = "catching myself on a block";
    }

    private void rotateSideways(Minecraft mc) {
        if (job == null) return;
        Player p = mc.player;
        float turn = job.detourSide ? 90f : -90f;
        job.detourSide = !job.detourSide;
        job.detourUntil = System.currentTimeMillis() + 700L;
        p.setYRot(p.getYRot() + turn);
        p.setYHeadRot(p.getYRot());
        if (job.stairDir != null) job.stairDir = turn > 0 ? job.stairDir.getClockWise() : job.stairDir.getCounterClockWise();
        job.note = "looking for a safer way through";
    }

    /** Lava or water next to a block we are about to open would pour in. */
    private static boolean unsafeAround(Minecraft mc, BlockPos pos) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (unsafe(mc, pos.relative(d))) return true;
        }
        return false;
    }

    private static boolean unsafe(Minecraft mc, BlockPos pos) {
        return mc.level != null && !mc.level.getBlockState(pos).getFluidState().isEmpty();
    }

    private static void equipTool(Minecraft mc, BlockState state) {
        if (creative(mc)) return;
        int best = -1;
        float speed = 1.0f;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = Inv.get(i);
            if (!usable(stack)) continue;
            float found = stack.getDestroySpeed(state);
            if (found > speed + 0.01f) {
                speed = found;
                best = i;
            }
        }
        if (best < 0) {
            // Nothing in the hotbar cuts it: pull the best tool out of the backpack instead.
            int from = -1;
            float bestInv = 1.0f;
            for (int i = 9; i < 36; i++) {
                ItemStack stack = Inv.get(i);
                if (!usable(stack)) continue;
                float found = stack.getDestroySpeed(state);
                if (found > bestInv + 0.01f) {
                    bestInv = found;
                    from = i;
                }
            }
            if (from >= 0) {
                Inv.click(Inv.menuSlot(from), Inv.selected(), ClickType.SWAP);
                return;
            }
            return;
        }
        if (best != Inv.selected()) Inv.select(best);
    }

    /** Jobs that legitimately stand still while a screen is open. */
    private static boolean waitsOnScreen(JobKind kind) {
        return switch (kind) {
            case CRAFT, SMELT, STORE, TAKE, SLEEP, EAT -> true;
            default -> false;
        };
    }

    /**
     * Creative mode has no harvest rules, no tool tiers and no block cost, so every gate that exists
     * to protect a survival job is skipped there instead of sending the AI off to craft a pickaxe it
     * does not need.
     */
    private static boolean creative(Minecraft mc) {
        return mc.player != null && mc.player.isCreative();
    }

    /** True when something in the inventory can actually harvest this block and not just break it. */
    private static boolean canHarvest(Minecraft mc, BlockState state) {
        if (creative(mc)) return true;
        if (!state.requiresCorrectToolForDrops()) return true;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = Inv.get(i);
            if (!stack.isEmpty() && usable(stack) && stack.isCorrectToolForDrops(state)) return true;
        }
        return false;
    }

    /**
     * The cheapest pickaxe the mod knows how to make that satisfies this block, asked from the
     * game itself rather than a tier table, so gold, deepslate and modded ores all behave.
     */
    private static String neededPickaxe(BlockState state) {
        for (String name : new String[] {"wooden_pickaxe", "stone_pickaxe", "iron_pickaxe", "diamond_pickaxe"}) {
            Craft.Recipe recipe = Craft.find(name);
            if (recipe == null) continue;
            if (new ItemStack(recipe.result()).isCorrectToolForDrops(state)) return name;
        }
        return "iron_pickaxe";
    }

    /** An ore is only worth walking to when a face of it is already in open air. */
    private static boolean exposed(Minecraft mc, BlockPos pos) {
        if (mc.level == null) return false;
        for (Direction d : Direction.values()) {
            if (mc.level.isEmptyBlock(pos.relative(d))) return true;
        }
        return false;
    }

    /** The instruction that gets us back to this job after a detour, such as making a pickaxe. */
    private static String resumeCommand(Job j) {
        return switch (j.kind) {
            case MINE_COUNT -> "mine " + Math.max(1, j.wanted) + " " + j.target;
            case MINE_UNTIL -> "mine until " + j.target;
            case CHOP -> "chop " + Math.max(1, j.wanted) + " trees";
            case QUARRY -> "quarry " + Math.max(1, j.wanted);
            case TUNNEL -> "tunnel " + Math.max(1, j.wanted);
            case FOOD -> "food " + Math.max(1, j.wanted);
            default -> j.target == null || j.target.isBlank() ? "stop" : j.target;
        };
    }

    private static boolean usable(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return !stack.isDamageableItem() || stack.getMaxDamage() - stack.getDamageValue() > 5;
    }

    /** What a mining request is really after: "stone" means the cobblestone it drops, "iron" raw iron. */
    private static net.minecraft.world.item.Item productOf(String target) {
        String t = target == null ? "" : target.toLowerCase(Locale.ROOT).trim().replace(' ', '_');
        if (t.endsWith("s") && !t.endsWith("ss") && t.length() > 3 && !t.equals("glass")) t = t.substring(0, t.length() - 1);
        t = t.replace("_ore", "").replace("deepslate_", "");
        return switch (t) {
            case "stone", "cobble", "cobblestone", "rock" -> Items.COBBLESTONE;
            case "deepslate", "cobbled_deepslate" -> Items.COBBLED_DEEPSLATE;
            case "iron", "raw_iron" -> Items.RAW_IRON;
            case "gold", "raw_gold" -> Items.RAW_GOLD;
            case "copper", "raw_copper" -> Items.RAW_COPPER;
            case "coal" -> Items.COAL;
            case "diamond" -> Items.DIAMOND;
            case "emerald" -> Items.EMERALD;
            case "redstone" -> Items.REDSTONE;
            case "lapi", "lapis", "lapis_lazuli" -> Items.LAPIS_LAZULI;
            case "quartz", "nether_quartz" -> Items.QUARTZ;
            case "clay", "clay_ball" -> Items.CLAY_BALL;
            case "grass", "grass_block", "dirt" -> Items.DIRT;
            default -> {
                net.minecraft.world.item.Item item = Craft.exact(t);
                yield item == null || item == Items.AIR ? null : item;
            }
        };
    }

    /** The blocks that drop it: stone for cobblestone, grass and dirt for dirt, gravel for flint. */
    private static java.util.Set<net.minecraft.world.level.block.Block> sourcesOf(String target, net.minecraft.world.item.Item product) {
        java.util.Set<net.minecraft.world.level.block.Block> set = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        if (product == Items.COBBLESTONE) set.addAll(java.util.List.of(Blocks.STONE, Blocks.COBBLESTONE));
        else if (product == Items.COBBLED_DEEPSLATE) set.addAll(java.util.List.of(Blocks.DEEPSLATE, Blocks.COBBLED_DEEPSLATE));
        else if (product == Items.DIRT) set.addAll(java.util.List.of(Blocks.DIRT, Blocks.GRASS_BLOCK, Blocks.COARSE_DIRT, Blocks.ROOTED_DIRT));
        else if (product == Items.FLINT) set.add(Blocks.GRAVEL);
        else if (product == Items.CLAY_BALL) set.add(Blocks.CLAY);
        else if (product == Items.SAND) set.add(Blocks.SAND);
        else if (product == Items.GRAVEL) set.add(Blocks.GRAVEL);
        if (set.isEmpty()) set.addAll(blocksFor(target));
        if (set.isEmpty() && product instanceof net.minecraft.world.item.BlockItem bi) set.add(bi.getBlock());
        return set;
    }

    private void mineCountTick(Minecraft mc) {
        if (job.target.equals("down") || job.target.equals("up")) {
            if (job.mined >= job.wanted) {
                endJob(mc, job.mined + " block(s) mined");
                return;
            }
            tunnel(mc);
            return;
        }
        // Count what actually lands in the inventory, not blocks broken: dirt dug on the way to
        // stone, or a block whose drop was never picked up, used to count as a success.
        net.minecraft.world.item.Item product = productOf(job.target);
        java.util.function.Predicate<ItemStack> counts = product == null ? null : st -> st.is(product);
        if (counts != null) {
            int now0 = countItems(mc, counts);
            if (job.baseline < 0) job.baseline = now0;
            job.crafts = Math.max(0, now0 - job.baseline);
            if (job.crafts >= job.wanted) {
                endJob(mc, "got " + job.crafts + " " + job.target.replace('_', ' '));
                return;
            }
        } else if (job.mined >= job.wanted) {
            endJob(mc, job.mined + " block(s) mined");
            return;
        }
        int have = counts == null ? job.mined : job.crafts;
        long now = System.currentTimeMillis();
        // pick up what we just broke before moving on
        if (now < job.dropUntil) {
            ItemEntity drop = nearestDrop(mc, 6);
            if (drop != null) {
                job.note = "picking up the drops (" + have + "/" + job.wanted + ")";
                walkTo(mc, drop.position(), 0.4);
                return;
            }
        }
        XRay ray = Zeus.get(XRay.class);
        boolean seeThrough = ray != null && ray.enabled();
        java.util.Set<net.minecraft.world.level.block.Block> sources = sourcesOf(job.target, product);
        if (now >= job.nextSearch) {
            job.nextSearch = now + 500L;
            BlockPos found = sources.isEmpty() ? null
                    : search(mc, mc.player.blockPosition(), Math.max(12, jobRange.i()), 10, st -> sources.contains(st.getBlock()));
            // only blocks the player could walk up to; buried ones are reached by tunnelling
            job.block = found != null && (seeThrough || exposed(mc, found)) ? found : null;
            if (job.block != null) job.lastFound = now;
        }
        if (job.block != null) {
            BlockState state = mc.level.getBlockState(job.block);
            if (!sources.contains(state.getBlock())) {
                job.block = null;
            } else if (Build.distTo(job.block) > jobReach.get()) {
                job.note = "heading for " + job.target + " at " + job.block.getX() + " " + job.block.getY() + " " + job.block.getZ();
                navigate(mc, centre(job.block));
                return;
            } else if (mineBlock(mc, job.block)) {
                if (job == null) return;
                job.block = null;
                job.mined++;
                job.dropUntil = now + 2500L;
                return;
            } else {
                return;
            }
        }
        boolean stoneLike = sources.contains(Blocks.STONE) || sources.contains(Blocks.DEEPSLATE) || isOre(job.target);
        if (!stoneLike) {
            // surface material with none in sight: wander to find some, then give up honestly
            if (job.lastFound == 0) job.lastFound = now;
            if (now - job.lastFound > 45_000L) {
                giveUp(mc, "found no " + job.target.replace('_', ' ') + " nearby (" + have + "/" + job.wanted + ")");
                return;
            }
            if (job.to == null || Math.hypot(job.to.x - mc.player.getX(), job.to.z - mc.player.getZ()) < 3) {
                float yaw = mc.player.getYRot() + (float) (Math.random() * 120 - 60);
                job.to = mc.player.position().add(-Math.sin(Math.toRadians(yaw)) * 24, 0, Math.cos(Math.toRadians(yaw)) * 24);
            }
            job.note = "looking for " + job.target.replace('_', ' ') + " (" + have + "/" + job.wanted + ")";
            walkTo(mc, job.to, 2.0);
            return;
        }
        int wantY = isOre(job.target) ? bestY(oreKey(job.target)) : (int) Math.round(mc.player.getY());
        BlockPos under = mc.player.blockPosition().below();
        // stone sits under the soil: staircase a few blocks down until the floor is stone
        if (!isOre(job.target) && !sources.contains(mc.level.getBlockState(under).getBlock())
                && !sources.contains(mc.level.getBlockState(under.below()).getBlock()) && mc.player.getY() > mc.level.getMinY() + 10) {
            wantY = (int) Math.round(mc.player.getY()) - 3;
        }
        double drift = mc.player.getY() - wantY;
        if (Math.abs(drift) > 2.0) {
            job.note = "heading down to " + job.target.replace('_', ' ') + " (" + have + "/" + job.wanted + ")";
            Vec3 column = new Vec3(mc.player.getX(), wantY, mc.player.getZ());
            if (drift > 0) stairDown(mc, column);
            else stairUp(mc, column);
            return;
        }
        job.note = "tunnelling for " + job.target.replace('_', ' ') + " (" + have + "/" + job.wanted + ")";
        int before = job.mined;
        tunnel(mc);
        if (job != null && job.mined > before) job.dropUntil = now + 1500L;
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
        // Never dig the block under our own feet: a staircase keeps a floor and misses lava.
        if (dir == Direction.DOWN) {
            stairDown(mc, new Vec3(p.getX(), p.getY() - 6.0, p.getZ()));
            return;
        }
        if (dir == Direction.UP) {
            stairUp(mc, new Vec3(p.getX(), p.getY() + 6.0, p.getZ()));
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
        if (state.getDestroySpeed(mc.level, pos) < 0f) {
            if (job != null) job.note = "bedrock in the way";
            return false;
        }
        if (creative(mc)) {
            Player cp = mc.player;
            Vec3 mid = centre(pos);
            if (cp.getEyePosition().distanceTo(mid) > 5.5) {
                walkTo(mc, mid, 1.2);
                return false;
            }
            releaseAttack(mc);
            lookAt(cp, mid);
            if (job != null) job.note = "breaking " + pos.getX() + " " + pos.getY() + " " + pos.getZ();
            if (mc.gameMode.destroyBlock(pos) && mc.level.getBlockState(pos).isAir()) {
                if (job != null && job.kind == JobKind.MINE_UNTIL && matches(state, job.target)) job.ore = pos;
                light(mc);
                return true;
            }
            return mc.level.getBlockState(pos).isAir();
        }
        // Breaking a block that needs a tool without one drops nothing and never finishes the
        // job, so make the tool first and come back instead of grinding at it forever.
        if (state.requiresCorrectToolForDrops() && !canHarvest(mc, state)) {
            String pick = neededPickaxe(state);
            String shown = pick.replace('_', ' ');
            if (job != null) job.note = "needs a " + shown;
            if (job != null && job.toolTried == 0) {
                job.toolTried = 1;
                String resume = resumeCommand(job);
                releaseAttack(mc);
                if (Craft.find(pick) != null && startOne("craft 1 " + shown)) {
                    chain.addFirst(resume);
                    Notify.warn("Zeus AI: making a " + shown + " first, then back to " + resume);
                    return false;
                }
                giveUp(mc, "no " + shown + " and no way to make one");
            }
            return false;
        }
        if (job != null) job.toolTried = 0;
        equipTool(mc, state);
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
        // and the block never accumulates break progress.  While attack is held and the crosshair
        // is on the block, vanilla already advances the break once per tick; adding our own
        // continueDestroyBlock on top doubled the speed, the server refused the early break and the
        // block came back (the "breaks every block twice" bug).  So: crosshair on the block, let
        // vanilla mine it; crosshair somewhere else (air, an entity), drive the break ourselves.
        mc.options.keyAttack.setDown(true);
        // Something between us and the block (leaves round a log, a stray dirt block, a torch): the
        // crosshair lands on it first, so clear it.  Vanilla mines whatever the crosshair is on while
        // attack is held, so all we have to do is hold the right tool for it and wait.
        if (mc.hitResult instanceof BlockHitResult obstacleHit && mc.hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK
                && !obstacleHit.getBlockPos().equals(pos)) {
            BlockPos ob = obstacleHit.getBlockPos();
            BlockState obState = mc.level.getBlockState(ob);
            boolean breakable = !obState.isAir() && obState.getFluidState().isEmpty() && obState.getDestroySpeed(mc.level, ob) >= 0f
                    && !obState.is(Blocks.CHEST) && !obState.is(Blocks.TRAPPED_CHEST) && !obState.is(BlockTags.BEDS);
            if (breakable && (!obState.requiresCorrectToolForDrops() || canHarvest(mc, obState) || obState.getDestroySpeed(mc.level, ob) < 3f)) {
                equipTool(mc, obState);
                if (job != null) job.note = "clearing " + BuiltInRegistries.BLOCK.getKey(obState.getBlock()).getPath().replace('_', ' ') + " in the way";
                mining = ob;
                return false;
            }
            // unbreakable or not worth it: step to a different side of the block
            if (job != null) {
                job.blocked++;
                if (job.blocked % 40 == 0) rotateSideways(mc);
                walkTo(mc, c.add(p.getLookAngle().scale(-0.5)).add(job.detourSide ? 1.2 : -1.2, 0, job.detourSide ? -1.2 : 1.2), 0.8);
            }
            return false;
        }
        boolean vanillaHas = mc.hitResult instanceof BlockHitResult bh
                && mc.hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK && bh.getBlockPos().equals(pos);
        if (!vanillaHas) {
            long tick = mc.level.getGameTime();
            if (tick != mineTick) {
                mineTick = tick;
                if (mining == null || !mining.equals(pos) || !mc.gameMode.isDestroying()) {
                    mining = pos;
                    mc.gameMode.startDestroyBlock(pos, face);
                } else {
                    mc.gameMode.continueDestroyBlock(pos, face);
                }
                p.swing(InteractionHand.MAIN_HAND);
            }
        } else {
            mining = pos;
        }
        if (!mc.level.getBlockState(pos).isAir()) return false;
        releaseAttack(mc);
        job.note = "mined " + pos.getX() + " " + pos.getY() + " " + pos.getZ();
        // Breaking the vein on the way down counts as finding it.
        if (job.kind == JobKind.MINE_UNTIL && matches(state, job.target)) job.ore = pos;
        light(mc);
        return true;
    }

    // A job should not stop just because a zombie walked into the tunnel, and a dark tunnel is how
    // you collect them in the first place, so jobs swing back and drop torches as they go.
    private boolean defend(Minecraft mc) {
        if (job == null) return false;
        if (job.kind == JobKind.HUNT || job.kind == JobKind.FLEE || job.kind == JobKind.SHOOT) return false;
        Player p = mc.player;
        if (p.getAttackStrengthScale(0.5f) < 0.85f) return false;
        net.minecraft.world.entity.Entity mob = nearestMonster(mc, 3.2);
        if (mob == null) return false;
        int weapon = bestWeapon(mc);
        if (weapon >= 0 && weapon != Inv.selected()) Inv.select(weapon);
        releaseAttack(mc);
        lookAt(p, mob.position().add(0, mob.getBbHeight() * 0.6, 0));
        mc.gameMode.attack(p, mob);
        p.swing(InteractionHand.MAIN_HAND);
        job.note = "fighting off a " + mob.getName().getString();
        return true;
    }

    // Jobs run for minutes; a human eats before mining the next vein instead of starving at 3 hunger.
    private void snack(Minecraft mc) {
        if (job == null || creative(mc)) return;
        Player p = mc.player;
        if (p.getFoodData().getFoodLevel() > 18) {
            if (p.isUsingItem()) return;
            return;
        }
        if (p.isUsingItem()) return;
        long now = System.currentTimeMillis();
        if (now < job.biteAt) return;
        job.biteAt = now + 900L;
        java.util.function.Predicate<ItemStack> edible = s -> s.has(net.minecraft.core.component.DataComponents.FOOD);
        int slot = ensureHotbar(mc, edible);
        if (slot < 0) return;
        if (!Inv.holding(edible)) Inv.select(slot);
        mc.options.keyUse.setDown(true);
        if (job != null) job.note = "eating something";
    }

    private void light(Minecraft mc) {
        if (job == null) return;
        long now = System.currentTimeMillis();
        if (now < job.lightAt) return;
        job.lightAt = now + 1200L;
        BlockPos feet = mc.player.blockPosition();
        if (mc.level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, feet) > 4) return;
        java.util.function.Predicate<net.minecraft.world.item.ItemStack> torch =
                s -> s.is(net.minecraft.world.item.Items.TORCH);
        if (Inv.hotbar(torch) < 0) return;
        if (!Build.replaceable(feet) || Build.against(feet, Build.REACH) == null) return;
        lookAt(mc.player, centre(feet));
        if (Build.place(feet, torch, true, Build.REACH) && job != null) job.note = "lighting the tunnel";
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
                job.path = null;
            }
            return true;
        }
        if (p.isCreative() && p.getAbilities().flying) {
            // Walking logic is meaningless in creative flight: no gravity, no jump, no ledges.
            double dy = target.y - p.getY();
            if (flat <= stop && Math.abs(dy) < 1.0) {
                releaseKeys(mc);
                return true;
            }
            double step = Math.min(0.42, flat);
            releaseKeys(mc);
            p.setDeltaMovement(flat > 0.01 ? dx / flat * step : 0,
                    Math.max(-0.42, Math.min(0.42, dy * 0.35)),
                    flat > 0.01 ? dz / flat * step : 0);
            if (job != null) job.note = "flying to " + Math.round(target.x) + " " + Math.round(target.y) + " " + Math.round(target.z);
            return flat <= stop && Math.abs(dy) < 1.0;
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
            job.path = null;
        }
        if (job.path != null) return followPath(mc, p, stop);
        float base = (float) Math.toDegrees(Math.atan2(-dx, dz));
        if (job.bestDist == 0 || flat < job.bestDist - 0.5) {
            job.bestDist = flat;
            job.stallSince = now;
        } else if (now - job.stallSince > 6000L) {
            job.stallSince = now;
            if (++job.pathRetries <= 3) {
                job.path = planPath(mc, target, stop);
                if (job.path != null) {
                    job.pathFor = target;
                    job.pathIndex = 0;
                    job.blocked = 0;
                    job.note = "found a way around, following it";
                    return false;
                }
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
                    job.path = planPath(mc, target, stop);
                    if (job.path != null) {
                        job.pathFor = target;
                        job.pathIndex = 0;
                        job.blocked = 0;
                        job.note = "found a way around, following it";
                        return false;
                    }
                    job.detourYaw = base + (job.detourSide ? 90f : -90f);
                    job.detourSide = !job.detourSide;
                    job.detourUntil = now + 1200L;
                    job.note = "working around something in the way";
                } else if (job.blocked >= 10 && job.blocked < 20) {
                    // still stuck after a detour and a new route: dig straight through whatever it is
                    if (digThrough(mc, base)) {
                        job.note = "stuck, digging through";
                        return false;
                    }
                    // or hop up onto it with a block if it is a ledge
                    if (p.onGround() && Build.solid(BlockPos.containing(p.position().add(-Math.sin(Math.toRadians(base)), 0, Math.cos(Math.toRadians(base)))))) {
                        mc.options.keyJump.setDown(true);
                    }
                } else if (job.blocked >= 20) {
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
        if (p.isInWater() || p.isInLava()) return swimTo(mc, target, stop);
        boolean detour = now < job.detourUntil;
        boolean blockedAhead = !detour && !passable(mc, base);
        boolean bump = p.horizontalCollision;
        // A wall straight ahead is worth a plan right away, not three seconds of walking into it.
        if (blockedAhead && now >= job.pathAt) {
            job.pathAt = now + 2500L;
            job.path = planPath(mc, target, stop);
            if (job.path != null) {
                job.pathFor = target;
                job.pathIndex = 0;
                job.blocked = 0;
                job.note = "routing around the wall";
                return false;
            }
        }
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

    // Water is not walking: the AI keeps its head above the surface, dives only when the target is
    // below it and it still has air, and keeps pushing forward so a shore can actually be climbed.
    private boolean swimTo(Minecraft mc, Vec3 to, double stop) {
        Player p = mc.player;
        double dx = to.x - p.getX(), dz = to.z - p.getZ();
        double flat = Math.hypot(dx, dz);
        double dy = to.y - p.getY();
        if (flat <= stop && Math.abs(dy) <= 1.2) {
            releaseKeys(mc);
            return true;
        }
        if (!p.isInWater() && !p.isInLava()) return walkTo(mc, to, stop);
        float yaw = flat < 0.35 ? p.getYRot() : (float) Math.toDegrees(Math.atan2(-dx, dz));
        p.setYRot(yaw);
        p.setYHeadRot(yaw);
        int air = p.getAirSupply();
        boolean dive = dy < -0.5 && air > 140 && !p.isInLava();
        boolean rise = !dive && (air < 220 || dy > 0.5 || !p.isUnderWater());
        mc.options.keyJump.setDown(rise);
        mc.options.keyShift.setDown(dive);
        mc.options.keyUp.setDown(flat > Math.max(0.6, stop * 0.5));
        mc.options.keySprint.setDown(flat > 3.0);
        // Against a bank: keep jumping so the step out of the water is taken as soon as it can be.
        if (p.horizontalCollision) mc.options.keyJump.setDown(true);
        if (job != null) {
            job.note = air < 140 ? "swimming up for air" : dive ? "diving to the target" : "swimming";
        }
        return false;
    }

    private static final class PathNode implements Comparable<PathNode> {
        final BlockPos pos;
        final double f;

        PathNode(BlockPos pos, double f) {
            this.pos = pos;
            this.f = f;
        }

        public int compareTo(PathNode other) {
            return Double.compare(f, other.f);
        }
    }

    /** A block a player can stand inside: two blocks of room and a solid, dry floor. */
    private static boolean standable(Minecraft mc, BlockPos pos) {
        if (mc.level == null) return false;
        return passes(mc, pos) && passes(mc, pos.above()) && floorSolid(mc, pos);
    }

    private static boolean floorSolid(Minecraft mc, BlockPos pos) {
        BlockPos under = pos.below();
        BlockState floor = mc.level.getBlockState(under);
        return !floor.getCollisionShape(mc.level, under).isEmpty() && floor.getFluidState().isEmpty();
    }

    /** A block a pickaxe, axe or shovel gets through quickly, and that is not water. */
    private static boolean diggable(Minecraft mc, BlockPos pos) {
        BlockState state = mc.level.getBlockState(pos);
        if (!state.getFluidState().isEmpty()) return false;
        float hardness = state.getDestroySpeed(mc.level, pos);
        return hardness >= 0f && hardness <= 3.0f;
    }

    /** A block that can be walked through without being burned, drowned or slowed. */
    private static boolean passes(Minecraft mc, BlockPos pos) {
        BlockState state = mc.level.getBlockState(pos);
        if (!state.getFluidState().isEmpty()) return false;
        if (!state.getCollisionShape(mc.level, pos).isEmpty()) return false;
        return !state.is(net.minecraft.world.level.block.Blocks.FIRE)
                && !state.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)
                && !state.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW)
                && !state.is(net.minecraft.world.level.block.Blocks.CACTUS);
    }

    /**
     * Bounded A* over the block grid, used when walking straight at the target bumps into something.
     *
     * <p>Nodes are places a player can stand; edges are one step in a cardinal direction at the
     * same height, one step up, or a drop of up to three blocks. Lava, fire, powder snow and
     * cactus are never entered, so a path that exists is a path that can be walked. The search is
     * capped in both nodes and distance, so a hopeless target costs a few milliseconds at most and
     * returns null instead of freezing the game.
     */
    private java.util.List<Vec3> planPath(Minecraft mc, Vec3 target, double stop) {
        Player p = mc.player;
        boolean digging = job != null && jobDig.on();
        BlockPos start = BlockPos.containing(p.getX(), Math.floor(p.getY() + 0.01), p.getZ());
        BlockPos goal = BlockPos.containing(target);
        if (!standable(mc, start) && standable(mc, start.above())) start = start.above();
        java.util.PriorityQueue<PathNode> open = new java.util.PriorityQueue<>();
        java.util.HashMap<BlockPos, Double> cost = new java.util.HashMap<>();
        java.util.HashMap<BlockPos, BlockPos> from = new java.util.HashMap<>();
        open.add(new PathNode(start, 0));
        cost.put(start, 0.0);
        BlockPos best = start;
        double bestH = Math.sqrt(start.distSqr(goal));
        Direction[] ways = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
        int budget = 900;
        while (!open.isEmpty() && budget-- > 0) {
            PathNode node = open.poll();
            double g = cost.getOrDefault(node.pos, Double.MAX_VALUE);
            double h = Math.sqrt(node.pos.distSqr(goal));
            if (h < bestH) {
                bestH = h;
                best = node.pos;
            }
            if (h <= Math.max(1.0, stop * 0.5)) {
                best = node.pos;
                break;
            }
            for (Direction way : ways) {
                BlockPos side = node.pos.relative(way);
                for (int dy = 1; dy >= -3; dy--) {
                    BlockPos next = side.offset(0, dy, 0);
                    if (next.distSqr(start) > 1024) continue;
                    boolean walkable = standable(mc, next);
                    if (!walkable) {
                        // A sharp tunnel is still a path, but only when digging is allowed and the
                        // block is soft: never carve through obsidian and never into a wall of it.
                        if (!digging || !floorSolid(mc, next)) continue;
                        boolean feet = diggable(mc, next);
                        boolean head = passes(mc, next.above()) || diggable(mc, next.above());
                        if (!feet || !head) continue;
                    }
                    if (dy > 0 && !passes(mc, node.pos.above(2))) continue;
                    double step = 1.0 + (dy > 0 ? 2.5 : -dy * 0.6) + (walkable ? 0.0 : 7.0);
                    double nextCost = g + step;
                    if (nextCost >= cost.getOrDefault(next, Double.MAX_VALUE)) continue;
                    cost.put(next, nextCost);
                    from.put(next, node.pos);
                    open.add(new PathNode(next, nextCost + Math.sqrt(next.distSqr(goal)) * 1.05));
                }
            }
        }
        java.util.ArrayList<Vec3> path = new java.util.ArrayList<>();
        for (BlockPos at = best; at != null && !at.equals(start); at = from.get(at)) {
            path.add(0, new Vec3(at.getX() + 0.5, at.getY(), at.getZ() + 0.5));
            if (path.size() > 64) break;
        }
        return path.isEmpty() ? null : path;
    }

    /** Follow a planned path one waypoint at a time. Returns true once the last one is reached. */
    private boolean followPath(Minecraft mc, Player p, double stop) {
        while (job.pathIndex < job.path.size()) {
            Vec3 wp = job.path.get(job.pathIndex);
            if (Math.hypot(wp.x - p.getX(), wp.z - p.getZ()) > 0.55 || Math.abs(wp.y - p.getY()) > 1.3) break;
            job.pathIndex++;
        }
        if (job.pathIndex >= job.path.size()) {
            job.path = null;
            boolean there = job.pathFor != null
                    && p.position().distanceTo(job.pathFor) <= Math.max(2.0, stop);
            if (there) releaseKeys(mc);
            return there;
        }
        Vec3 wp = job.path.get(job.pathIndex);
        BlockPos at = BlockPos.containing(wp.x, wp.y, wp.z);
        if (!passes(mc, at) || !passes(mc, at.above())) {
            BlockPos dig = passes(mc, at) ? at.above() : at;
            job.note = "clearing the way through " + dig.getX() + " " + dig.getY() + " " + dig.getZ();
            mineBlock(mc, dig);
            return false;
        }
        double dx = wp.x - p.getX(), dz = wp.z - p.getZ();
        double flat = Math.hypot(dx, dz);
        float yaw = flat < 0.08 ? p.getYRot() : (float) Math.toDegrees(Math.atan2(-dx, dz));
        p.setYRot(yaw);
        p.setYHeadRot(yaw);
        mc.options.keyUp.setDown(flat > 0.12);
        mc.options.keySprint.setDown(flat > 2.4);
        mc.options.keyJump.setDown(p.onGround()
                && (wp.y > p.getY() + 0.55 || p.horizontalCollision));
        job.note = "following a path (" + (job.pathIndex + 1) + "/" + job.path.size() + ")";
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
            // A human opens a door. Breaking it costs a tool and looks like nothing else in the game.
            if (state.getBlock() instanceof net.minecraft.world.level.block.DoorBlock
                    || state.getBlock() instanceof net.minecraft.world.level.block.FenceGateBlock) {
                lookAt(p, centre(pos));
                mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(centre(pos), Direction.UP, pos, false));
                return true;
            }
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

    private static final java.util.Set<BlockPos> SKIP_LOGS = new java.util.HashSet<>();

    /**
     * The next log: on the current tree first, lowest first so the trunk comes down from the bottom,
     * and never one that is too high to reach from the ground (those were what made it stand under a
     * tree swinging at nothing).  Logs that are part of a building (planks or glass next to them) are
     * left alone.
     */
    private BlockPos nearestLog(Minecraft mc) {
        BlockPos centre = job.limit != null ? job.limit : mc.player.blockPosition();
        int radius = job.limit != null ? 6 : jobRange.i();
        int height = job.limit != null ? 12 : 16;
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        Vec3 eye = mc.player.getEyePosition();
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-radius, -height, -radius), centre.offset(radius, height, radius))) {
            BlockState st = mc.level.getBlockState(pos);
            if (!st.is(BlockTags.LOGS) || SKIP_LOGS.contains(pos)) continue;
            // a log is reachable when some walkable spot near its trunk puts it within reach
            int ground = pos.getY();
            BlockPos probe = pos.below();
            int steps = 0;
            while (steps < 12 && !Build.solid(probe) && mc.level.getBlockState(probe).getFluidState().isEmpty()) {
                probe = probe.below();
                steps++;
            }
            ground = probe.getY() + 1;
            if (pos.getY() - ground > 4) continue;
            if (built(mc, pos)) continue;
            double d = pos.distToCenterSqr(eye.x, eye.y, eye.z);
            double score = d + (pos.getY() - ground) * 6.0;
            if (score < bestScore) {
                bestScore = score;
                best = pos.immutable();
            }
        }
        return best;
    }

    /** Planks, glass, doors or stairs touching a log: it is someone's house, not a tree. */
    private static boolean built(Minecraft mc, BlockPos pos) {
        for (Direction d : Direction.values()) {
            BlockState n = mc.level.getBlockState(pos.relative(d));
            if (n.is(BlockTags.PLANKS) || n.is(BlockTags.DOORS) || n.is(BlockTags.STAIRS) || n.is(Blocks.GLASS)
                    || n.is(BlockTags.WOODEN_SLABS) || n.is(Blocks.GLASS_PANE)) return true;
        }
        return false;
    }

    private BlockPos nearestOre(Minecraft mc, String wanted, int radius, int height) {
        java.util.Set<net.minecraft.world.level.block.Block> blocks = blocksFor(wanted);
        if (blocks.isEmpty()) return null;
        return search(mc, mc.player.blockPosition(), radius, height, state -> blocks.contains(state.getBlock()));
    }

    private static final java.util.Map<String, java.util.Set<net.minecraft.world.level.block.Block>> BLOCKS_FOR = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Every block a name matches, worked out once.  Scanning tens of thousands of blocks used to look
     * up and compare each block's id string, which is what made ore searches stutter.
     */
    private static java.util.Set<net.minecraft.world.level.block.Block> blocksFor(String wanted) {
        String key = wanted == null ? "" : wanted.toLowerCase(Locale.ROOT).trim();
        return BLOCKS_FOR.computeIfAbsent(key, k -> {
            java.util.Set<net.minecraft.world.level.block.Block> set = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
            for (net.minecraft.world.level.block.Block b : BuiltInRegistries.BLOCK) {
                if (matches(b.defaultBlockState(), k)) set.add(b);
            }
            return set;
        });
    }

    private static boolean matches(BlockState state, String wanted) {
        if (wanted == null || wanted.isBlank() || state.isAir()) return false;
        String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        for (String key : keys(wanted)) {
            if (id.equals(key) || id.equals("deepslate_" + key) || id.endsWith("_ore") && id.contains(key)) return true;
        }
        return false;
    }

    // "diamonds", "diamond ore", "Diamond" and "deepslate diamond" all have to find diamond ore.
    private static java.util.List<String> keys(String wanted) {
        java.util.LinkedHashSet<String> keys = new java.util.LinkedHashSet<>();
        String key = wanted.toLowerCase(Locale.ROOT).trim().replace(' ', '_');
        keys.add(key);
        if (key.endsWith("_ores")) keys.add(key.substring(0, key.length() - 5));
        if (key.endsWith("_ore")) keys.add(key.substring(0, key.length() - 4));
        if (key.endsWith("s") && key.length() > 3) keys.add(key.substring(0, key.length() - 1));
        if (key.equals("debris") || key.equals("netherite") || key.equals("netherite_scrap")) keys.add("ancient_debris");
        return new java.util.ArrayList<>(keys);
    }

    private static String oreKey(String wanted) {
        java.util.List<String> keys = keys(wanted == null ? "" : wanted);
        return keys.isEmpty() ? "" : keys.get(keys.size() - 1);
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

    /** Turn to face coordinates, the nearest player or mob, or the nearest block of a kind. */
    private String look(String value) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return "not in a world";
        String v = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        java.util.regex.Matcher xyz = java.util.regex.Pattern.compile("(-?\\d+(?:\\.\\d+)?)\\s+(-?\\d+(?:\\.\\d+)?)\\s+(-?\\d+(?:\\.\\d+)?)").matcher(v);
        if (xyz.find()) {
            Vec3 at = new Vec3(Double.parseDouble(xyz.group(1)) + 0.5, Double.parseDouble(xyz.group(2)) + 0.5, Double.parseDouble(xyz.group(3)) + 0.5);
            lookAt(mc.player, at);
            return "looking at " + xyz.group(1) + " " + xyz.group(2) + " " + xyz.group(3);
        }
        String want = v.replaceAll("^(at |the |nearest |closest |a |an )+", "").trim();
        net.minecraft.world.entity.Entity best = null;
        double bestD = 48 * 48;
        for (net.minecraft.world.entity.Entity e : mc.level.entitiesForRendering()) {
            if (e == mc.player || !e.isAlive()) continue;
            boolean fits = want.isEmpty() || want.equals("player") && e instanceof Player
                    || (want.equals("mob") || want.equals("monster") || want.equals("enemy")) && e instanceof net.minecraft.world.entity.monster.Monster
                    || e.getName().getString().toLowerCase(Locale.ROOT).contains(want)
                    || BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath().replace('_', ' ').contains(want);
            if (!fits) continue;
            double d = e.distanceToSqr(mc.player);
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        if (best != null) {
            lookAt(mc.player, best.position().add(0, best.getBbHeight() * 0.8, 0));
            return "looking at " + best.getName().getString();
        }
        BlockPos at = mc.player.blockPosition();
        BlockPos found = null;
        int foundD = Integer.MAX_VALUE;
        String id = want.replace(' ', '_');
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -16; dx <= 16; dx++) for (int dy = -8; dy <= 8; dy++) for (int dz = -16; dz <= 16; dz++) {
            pos.set(at.getX() + dx, at.getY() + dy, at.getZ() + dz);
            String path = BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(pos).getBlock()).getPath();
            if (id.isEmpty() || !path.contains(id)) continue;
            int d = dx * dx + dy * dy + dz * dz;
            if (d < foundD) {
                foundD = d;
                found = pos.immutable();
            }
        }
        if (found == null) return "did not see " + value + " nearby";
        lookAt(mc.player, Vec3.atCenterOf(found));
        return "looking at the " + want + " at " + found.getX() + " " + found.getY() + " " + found.getZ();
    }

    /** "10 check the furnace" or "10|check the furnace": a reminder in chat after that many minutes. */
    private String remind(String value) {
        String v = value == null ? "" : value.trim();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(\\d+(?:\\.\\d+)?)\\s*(?:(seconds?|secs?|s|minutes?|mins?|m|hours?|h)\\b)?\\s*[|:,-]?\\s*(.*)$").matcher(v);
        if (!m.find()) return "did not understand the reminder " + v;
        double n = Double.parseDouble(m.group(1));
        String unit = m.group(2) == null ? "m" : m.group(2);
        long ms = unit.startsWith("s") ? (long) (n * 1000) : unit.startsWith("h") ? (long) (n * 3_600_000) : (long) (n * 60_000);
        ms = Math.max(1000L, Math.min(24L * 3_600_000L, ms));
        String text = m.group(3).isBlank() ? "time is up" : m.group(3).trim();
        synchronized (REMINDERS) {
            REMINDERS.add(new Object[] {System.currentTimeMillis() + ms, text});
        }
        String when = ms >= 60_000 ? Math.round(ms / 60_000.0) + " min" : ms / 1000 + " s";
        Notify.good("Reminder set: " + text + " in " + when);
        return "reminder in " + when + ": " + text;
    }

    /** Put an item matching the words in hand, moving it to the hotbar when it is in the inventory. */
    private String hold(String value) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return "not in a world";
        String want = value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        if (want.isEmpty()) return "did not say what to hold";
        if (want.equals("nothing") || want.equals("empty") || want.equals("empty_hand") || want.equals("fist")) {
            for (int i = 0; i < 9; i++) {
                if (mc.player.getInventory().getItem(i).isEmpty()) {
                    Inv.select(i);
                    return "holding nothing (slot " + (i + 1) + ")";
                }
            }
            return "did not find an empty hotbar slot";
        }
        String bare = want.replaceAll("^(a|an|the|my)_", "");
        java.util.function.Predicate<ItemStack> match = st -> {
            if (st.isEmpty()) return false;
            String id = BuiltInRegistries.ITEM.getKey(st.getItem()).getPath();
            String name = st.getHoverName().getString().toLowerCase(Locale.ROOT).replace(' ', '_');
            return id.equals(bare) || name.equals(bare) || id.endsWith("_" + bare) || id.contains(bare) || name.contains(bare);
        };
        int slot = ensureHotbar(mc, match);
        if (slot < 0) return "did not find " + value + " in the inventory";
        Inv.select(slot);
        return "holding " + mc.player.getInventory().getItem(slot).getHoverName().getString();
    }

    private void help() {
        if (!hasKey()) Notify.info("Not set up yet: /zeus ai tutorial walks you through it in about two minutes");
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
        if (speak.on()) AiTutorial.chat(message, plan.steps().isEmpty());
        return message;
    }

    private Plan parseLocal(String prompt) {
        String clean = prompt.toLowerCase(Locale.ROOT).trim();
        List<Step> steps = new ArrayList<>();
        String carry = "";
        for (String piece : CLAUSE.split(clean)) {
            String part = piece.trim().replaceAll("^(please|pls|can you|could you|and)\\s+", "").replaceAll("[.!?]+$", "").trim();
            Step step = clause(part);
            // "turn on sprint and fullbright": the second part has no verb, so it inherits "on" instead of toggling.
            if (step != null && "toggle".equals(step.action()) && !carry.isEmpty() && !startsWithVerb(part)) {
                step = new Step(carry, step.module(), "", "");
            }
            if (step != null && ("on".equals(step.action()) || "off".equals(step.action()))) carry = step.action();
            else if (step != null && !"toggle".equals(step.action())) carry = "";
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
        if (part.startsWith("hold ") || part.startsWith("equip ")) return new Step("hold", "", "", part.substring(part.indexOf(' ') + 1).trim());
        if (part.startsWith("look at ") || part.startsWith("face ")) return new Step("look", "", "", part.substring(part.startsWith("face ") ? 5 : 8).trim());
        java.util.regex.Matcher rem = java.util.regex.Pattern.compile("^remind me in (\\d+(?:\\.\\d+)?) ?(?:(seconds?|secs?|s|minutes?|mins?|m|hours?|h)\\b)? ?(?:to |that |about )?(.+)$").matcher(part);
        if (rem.find()) return new Step("remind", "", "", rem.group(1) + " " + (rem.group(2) == null ? "m" : rem.group(2)) + " " + rem.group(3));
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
        java.util.regex.Matcher nudge = java.util.regex.Pattern
                .compile("^(increase|raise|decrease|lower|reduce) (.+?)(?: by ([0-9.]+))?$").matcher(part);
        if (nudge.find()) {
            boolean up = nudge.group(1).equals("increase") || nudge.group(1).equals("raise");
            String by = nudge.group(3) == null ? "1" : nudge.group(3);
            Step step = settingStep(nudge.group(2) + " to " + (up ? "+" : "-") + by);
            if (step != null) return step;
        }
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

    private static boolean startsWithVerb(String part) {
        for (String[] list : new String[][]{ON_WORDS, OFF_WORDS, TOGGLE_WORDS}) {
            for (String word : list) if (part.startsWith(word)) return true;
        }
        return false;
    }

    private Step moduleStep(String action, String words) {
        if (words.isEmpty()) return null;
        Module module = findModule(words);
        if (module == null || BLOCKED.contains(module.id)) return null;
        return new Step(action, module.id, "", "");
    }

    private Step settingStep(String body) {
        int at = body.indexOf(" to ");
        String left;
        String value;
        if (at > 0) {
            left = body.substring(0, at).trim();
            value = body.substring(at + 4).trim();
        } else {
            // "set killaura range 4" or "set esp players on": the last word is the value.
            int split = body.lastIndexOf(' ');
            if (split <= 0) return null;
            left = body.substring(0, split).trim();
            value = body.substring(split + 1).trim();
        }
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

    private String readContext() {
        Minecraft client = Minecraft.getInstance();
        StringBuilder builder = new StringBuilder();
        builder.append("Theme: ").append(com.zlorbie788.zeus.ui.UiSettings.ACCENT.value()).append('.');
        if (client.player == null || client.level == null) {
            return builder.append(" The player is not in a world, so jobs cannot run; module and setting changes still work.").toString();
        }
        var p = client.player;
        builder.append(" World: ").append(client.level.dimension().identifier())
                .append(client.hasSingleplayerServer() ? " (singleplayer)" : " (multiplayer)").append('.');
        long clock = client.level.getDayTime() % 24000L;
        builder.append(" Time: ").append(clock < 12300L || clock > 23850L ? "day" : "night").append('.');
        builder.append(" Health ").append(Math.round(p.getHealth())).append("/20, food ")
                .append(p.getFoodData().getFoodLevel()).append("/20, armor ").append(p.getArmorValue()).append('.');
        builder.append(" Position ").append(Math.round(p.getX())).append(' ').append(Math.round(p.getY())).append(' ')
                .append(Math.round(p.getZ())).append(", facing ").append(p.getDirection().getName()).append('.');
        ItemStack held = p.getMainHandItem();
        builder.append(" Holding ").append(held.isEmpty() ? "nothing" : held.getCount() + " " + held.getHoverName().getString()).append('.');
        if (client.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit
                && client.hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            builder.append(" Looking at ").append(client.level.getBlockState(hit.getBlockPos()).getBlock().getName().getString())
                    .append(" at ").append(hit.getBlockPos().getX()).append(' ').append(hit.getBlockPos().getY())
                    .append(' ').append(hit.getBlockPos().getZ()).append('.');
        }
        int hostile = 0;
        int players = 0;
        for (net.minecraft.world.entity.Entity e : client.level.entitiesForRendering()) {
            if (e == p || !e.isAlive() || e.distanceToSqr(p) > 24 * 24) continue;
            if (e instanceof net.minecraft.world.entity.monster.Monster) hostile++;
            else if (e instanceof Player) players++;
        }
        builder.append(" Nearby: ").append(hostile).append(" hostile mob(s), ").append(players).append(" other player(s).");
        java.util.Map<String, Integer> items = new java.util.LinkedHashMap<>();
        var inventory = p.getInventory();
        for (int i = 0; i < Math.min(36, inventory.getContainerSize()); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) continue;
            items.merge(stack.getHoverName().getString(), stack.getCount(), Integer::sum);
        }
        if (!items.isEmpty()) {
            List<java.util.Map.Entry<String, Integer>> sorted = new ArrayList<>(items.entrySet());
            sorted.sort((a, b) -> b.getValue() - a.getValue());
            builder.append(" Inventory:");
            for (int i = 0; i < Math.min(14, sorted.size()); i++) {
                builder.append(i == 0 ? " " : ", ").append(sorted.get(i).getValue()).append(' ').append(sorted.get(i).getKey());
            }
            builder.append('.');
        } else {
            builder.append(" Inventory empty.");
        }
        builder.append(nearbyBlocks(client));
        java.util.List<java.nio.file.Path> schematics = SchematicPlan.files();
        if (!schematics.isEmpty()) {
            builder.append(" Schematics available:");
            for (int i = 0; i < Math.min(10, schematics.size()); i++) builder.append(i == 0 ? " " : ", ").append(SchematicPlan.display(schematics.get(i)));
            builder.append('.');
        }
        Extras.Waypoints points = Zeus.get(Extras.Waypoints.class);
        if (points != null) {
            try {
                List<String> saved = points.forAi(8);
                if (!saved.isEmpty()) builder.append(" Saved waypoints (use their coordinates with go to): ").append(String.join("; ", saved)).append('.');
            } catch (RuntimeException ignored) {
            }
        }
        synchronized (EVENTS) {
            if (!EVENTS.isEmpty()) builder.append(" Recent events (oldest first): ").append(String.join(" | ", EVENTS)).append('.');
        }
        if (shareChat.on()) {
            synchronized (CHAT) {
                if (!CHAT.isEmpty()) builder.append(" Recent chat (oldest first): ").append(String.join(" | ", CHAT)).append('.');
            }
        }
        if (job != null) builder.append(" Job running: ").append(job.label()).append(job.note == null || job.note.isBlank() ? "" : " (" + job.note + ")").append('.');
        builder.append(" FPS ").append(client.getFps()).append('.');
        builder.append(" Modules on:");
        int shown = 0;
        for (Module module : Zeus.all()) {
            if (!module.enabled()) continue;
            builder.append(' ').append(module.id);
            if (++shown >= 24) break;
        }
        if (shown == 0) builder.append(" none");
        return builder.toString();
    }

    /** Useful blocks around the player (containers, stations, beds, ores in view of the air), nearest of each kind. */
    private static String nearbyBlocks(Minecraft client) {
        if (client.player == null || client.level == null) return "";
        BlockPos at = client.player.blockPosition();
        java.util.Map<String, int[]> nearest = new java.util.LinkedHashMap<>();
        java.util.Map<String, Integer> counts = new java.util.HashMap<>();
        int r = 12;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dy = -6; dy <= 6; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    pos.set(at.getX() + dx, at.getY() + dy, at.getZ() + dz);
                    BlockState state = client.level.getBlockState(pos);
                    if (state.isAir()) continue;
                    String kind = interesting(state);
                    if (kind == null) continue;
                    int d2 = dx * dx + dy * dy + dz * dz;
                    counts.merge(kind, 1, Integer::sum);
                    int[] best = nearest.get(kind);
                    if (best == null || d2 < best[3]) nearest.put(kind, new int[] {pos.getX(), pos.getY(), pos.getZ(), d2});
                }
            }
        }
        if (nearest.isEmpty()) return "";
        StringBuilder out = new StringBuilder(" Nearby blocks (nearest of each):");
        int shown = 0;
        for (var e : nearest.entrySet()) {
            int[] p = e.getValue();
            out.append(shown == 0 ? " " : "; ").append(e.getKey());
            int n = counts.getOrDefault(e.getKey(), 1);
            if (n > 1) out.append(" x").append(n);
            out.append(" at ").append(p[0]).append(' ').append(p[1]).append(' ').append(p[2]);
            if (++shown >= 12) break;
        }
        return out.append('.').toString();
    }

    private static String interesting(BlockState state) {
        net.minecraft.world.level.block.Block b = state.getBlock();
        if (b == Blocks.CHEST || b == Blocks.TRAPPED_CHEST || b == Blocks.BARREL) return "chest";
        if (b == Blocks.ENDER_CHEST) return "ender chest";
        if (b == Blocks.CRAFTING_TABLE) return "crafting table";
        if (b == Blocks.FURNACE || b == Blocks.BLAST_FURNACE || b == Blocks.SMOKER) return "furnace";
        if (state.is(BlockTags.BEDS)) return "bed";
        if (b == Blocks.ANVIL || b == Blocks.CHIPPED_ANVIL || b == Blocks.DAMAGED_ANVIL) return "anvil";
        if (b == Blocks.ENCHANTING_TABLE) return "enchanting table";
        if (b == Blocks.SPAWNER) return "spawner";
        if (state.is(BlockTags.LOGS)) return "log";
        if (b == Blocks.WATER) return null;
        if (b == Blocks.LAVA) return "lava";
        String id = BuiltInRegistries.BLOCK.getKey(b).getPath();
        if (id.endsWith("_ore") || id.equals("ancient_debris")) return id.replace("deepslate_", "").replace("_ore", " ore");
        return null;
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
        Provider provider = currentProvider();
        out.add((hasSecret() ? "Key " + mask(token.get()) : hasKey() ? "Local, no key" : "No key (/zeus ai tutorial)")
                + " · " + (provider == null ? "custom endpoint" : provider.label()) + " · " + model.get());
        if (!lastTest.isBlank()) out.add((lastTestOk ? "Test passed: " : lastTest.equals("testing") ? "Test: " : "Test failed: ") + shorten(lastTest));
        out.add((busy ? "Working" : QUEUE.isEmpty() ? "Idle" : QUEUE.size() + " action(s) running")
                + " · " + USED.get() + "/" + perHour.i() + " this hour" + (preview.on() ? " · preview" : "")
                + " · " + NOTES.size() + " note(s)"
                + (TOKENS.get() > 0 ? " · " + TOKENS.get() + " tokens this session" : "")
                + (lastLatency > 0 ? " · last answer " + lastLatency + " ms" : ""));
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

    /** Everything /zeus ai status shows. */
    public static List<String> statusLines() {
        AiPilot self = find();
        List<String> out = new ArrayList<>();
        if (self == null) {
            out.add("Zeus AI Functions is missing.");
            return out;
        }
        out.add("Zeus AI Functions is " + (self.enabled() ? "on" : "off (switch it on with /zeus on aipilot)")
                + (self.job != null ? " · job: " + self.job.label() : ""));
        out.add("Endpoint: " + self.url());
        out.addAll(self.lines());
        if (!hasKey()) out.add("Not set up yet: /zeus ai tutorial");
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
