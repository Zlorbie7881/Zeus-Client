package com.zlorbie788.zeus.mods;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Setting;
import com.zlorbie788.zeus.core.Zeus;
import net.minecraft.client.Minecraft;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Pattern;

/**
 * Sends a message to a Discord channel (through a webhook you create yourself) when the finders discover something:
 * a spawner, a big base or a room full of storage. Everything goes through one background thread, one message at a time,
 * with a pause between messages, and a find is only ever sent once. The address is kept in your own config file and is only
 * ever used to post to Discord: it must look like a real Discord webhook address, otherwise it is refused.
 */
public final class DiscordWebhook extends Module {
    private static final Pattern URL = Pattern.compile("^https://(?:(?:ptb|canary)\\.)?discord(?:app)?\\.com/api/webhooks/\\d{5,25}/[A-Za-z0-9_\\-]{20,120}$");

    private final Setting.Secret address = add(new Setting.Secret("address"));
    private final Setting.Bool spawners = add(new Setting.Bool("Spawners", "Send a message when a spawner is found.", true));
    private final Setting.Bool bases = add(new Setting.Bool("Big bases", "Send a message when Base Finder or Suspicious Chunks finds a big base.", true));
    private final Setting.Bool storage = add(new Setting.Bool("Storage rooms", "Send a message when Storage ESP finds many containers close together.", true));
    private final Setting.Num storageMin = add(new Setting.Num("Storage room from", "How many containers close together make a storage room.", 12, 4, 60, 1, "")).showIf(() -> this.storage.on());
    private final Setting.Bool server = add(new Setting.Bool("Include server address", "Put the server you are on in the message.", true));
    private final Setting.Bool who = add(new Setting.Bool("Include your name", "Put your player name in the message.", true));
    private final Setting.Num pause = add(new Setting.Num("Pause between messages", "Discord limits how fast a channel can be written to.", 3, 1, 30, 1, " s"));
    private final Setting.Info status = add(new Setting.Info("Status", this::status));
    private final Setting.Action paste = add(new Setting.Action("Paste webhook from clipboard", "Copy the webhook address in Discord (Channel settings > Integrations > Webhooks), then press this.", this::pasteFromClipboard));
    private final Setting.Text typed = add(new Setting.Text("Or type or paste it here", "Click the box, press Ctrl+V (or type the address), then press the button under it.", "", 200).temporary());
    private final Setting.Action useTyped = add(new Setting.Action("Use the typed webhook", "Saves the address typed above.", this::useTyped));
    private final Setting.Action test = add(new Setting.Action("Send a test message", "Posts a short test message to the channel.", this::sendTest));
    private final Setting.Action clear = add(new Setting.Action("Forget the webhook", "Removes the saved address.", this::forget));

    private static final LinkedBlockingQueue<String> QUEUE = new LinkedBlockingQueue<>();
    private static final Set<String> SENT = new HashSet<>();
    private static volatile String last = "Nothing sent yet.";
    private static volatile int sentCount;
    private static Thread worker;
    private static HttpClient http;

    public DiscordWebhook() {
        super("webhook", "Discord Webhook", "Sends spawners, big bases and storage rooms to a Discord channel as clean embeds.", Category.UTILITY);
    }

    // ------------------------------------------------------------ settings and status

    private boolean ready() {
        return URL.matcher(address.get()).matches();
    }

    private List<String> status() {
        List<String> out = new ArrayList<>();
        if (!ready()) {
            out.add("No webhook yet.");
            out.add("In Discord: Channel settings > Integrations > Webhooks > New Webhook > Copy Webhook URL,");
            out.add("then press \"Paste webhook from clipboard\" here.");
        } else {
            out.add("Webhook: " + masked(address.get()));
            out.add(enabled() ? "Switched on: finds are sent. Sent so far: " + sentCount : "Switched off: nothing is sent.");
        }
        out.add(last);
        return out;
    }

    private static String masked(String u) {
        int i = u.lastIndexOf('/');
        return i < 0 ? "?" : u.substring(0, i + 1) + "••••••••";
    }

    private void pasteFromClipboard() {
        String clip = "";
        try {
            clip = Minecraft.getInstance().keyboardHandler.getClipboard();
        } catch (RuntimeException ignored) {
            // no clipboard
        }
        take(clip);
    }

    private void useTyped() {
        take(typed.get());
        typed.set("");
    }

    private void take(String clip) {
        clip = clip == null ? "" : clip.trim();
        if (!URL.matcher(clip).matches()) {
            last = "That is not a Discord webhook address.";
            Zeus.toast("That is not a Discord webhook address", false);
            return;
        }
        address.set(clip);
        last = "Webhook saved.";
        Zeus.toast("Webhook saved", true);
    }

    private void forget() {
        address.set("");
        last = "Webhook removed.";
    }

    private void sendTest() {
        if (!ready()) {
            last = "Paste a webhook first.";
            return;
        }
        JsonObject e = embed("⚡ Zeus is connected", "This channel will receive your finds.", 0x2F86FF);
        field(e, "Status", "Test message", true);
        field(e, "Version", Zeus.VERSION, true);
        push(wrap(e), address.get());
    }

    // ------------------------------------------------------------ what the finders call

    private static DiscordWebhook live() {
        DiscordWebhook w = Zeus.get(DiscordWebhook.class);
        return w != null && w.enabled() && w.ready() ? w : null;
    }

    public static void spawner(Minecraft mc, int x, int y, int z, boolean trial) {
        DiscordWebhook w = live();
        if (w == null || !w.spawners.on() || !once("spawner:" + x + "," + y + "," + z)) return;
        JsonObject e = w.embed(trial ? "🌀 Trial spawner found" : "💀 Spawner found", trial ? "A trial spawner was found nearby." : "A monster spawner was found nearby.", trial ? 0x35E0D0 : 0xFF8A1F);
        w.where(e, mc, x, y, z);
        push(wrap(e), w.address.get(), w.pause.i());
    }

    public static void bigBase(Minecraft mc, double x, double z, int chunks, int minY, int maxY, String kind) {
        DiscordWebhook w = live();
        if (w == null || !w.bases.on() || !once("base:" + Math.round(x / 48) + "," + Math.round(z / 48))) return;
        JsonObject e = w.embed("🏠 Big base found", "A large build was found: about " + chunks + " chunks wide.", 0xFF5C6C);
        field(e, "Size", chunks + " chunks", true);
        field(e, "Mostly", kind, true);
        if (minY != Integer.MAX_VALUE) field(e, "Height", "Y " + minY + " to " + maxY, true);
        w.where(e, mc, (int) Math.round(x), minY == Integer.MAX_VALUE ? (int) mc.player.getY() : (minY + maxY) / 2, (int) Math.round(z));
        push(wrap(e), w.address.get(), w.pause.i());
    }

    public static void storageRoom(Minecraft mc, int x, int y, int z, int count) {
        DiscordWebhook w = live();
        if (w == null || !w.storage.on() || count < w.storageMin.i() || !once("storage:" + (x >> 5) + "," + (z >> 5))) return;
        JsonObject e = w.embed("📦 Storage room found", count + " containers close together.", 0xFFC857);
        field(e, "Containers", Integer.toString(count), true);
        w.where(e, mc, x, y, z);
        push(wrap(e), w.address.get(), w.pause.i());
    }

    public static int storageMinimum() {
        DiscordWebhook w = Zeus.get(DiscordWebhook.class);
        return w == null ? 12 : w.storageMin.i();
    }

    /** True the first time a find is reported, so nothing is sent twice. */
    private static boolean once(String key) {
        synchronized (SENT) {
            if (SENT.size() > 4000) SENT.clear();
            return SENT.add(key);
        }
    }

    // ------------------------------------------------------------ building the message

    private JsonObject embed(String title, String text, int color) {
        JsonObject e = new JsonObject();
        e.addProperty("title", title);
        e.addProperty("description", text);
        e.addProperty("color", color);
        e.addProperty("timestamp", Instant.now().toString());
        JsonObject footer = new JsonObject();
        footer.addProperty("text", "Zeus Client " + Zeus.VERSION);
        e.add("footer", footer);
        return e;
    }

    private static void field(JsonObject embed, String name, String value, boolean inline) {
        JsonArray fields = embed.has("fields") ? embed.getAsJsonArray("fields") : new JsonArray();
        JsonObject f = new JsonObject();
        f.addProperty("name", name);
        f.addProperty("value", value.isEmpty() ? "-" : value);
        f.addProperty("inline", inline);
        fields.add(f);
        embed.add("fields", fields);
    }

    private void where(JsonObject e, Minecraft mc, int x, int y, int z) {
        field(e, "Location", "`" + x + ", " + y + ", " + z + "`", true);
        field(e, "Dimension", dimension(mc), true);
        if (server.on()) {
            String s = "Singleplayer";
            try {
                if (mc.getCurrentServer() != null) s = mc.getCurrentServer().ip;
            } catch (RuntimeException ignored) {
                // keep the default
            }
            field(e, "Server", "`" + s + "`", true);
        }
        if (mc.player != null) {
            double d = Math.sqrt(mc.player.distanceToSqr(x + 0.5, y, z + 0.5));
            field(e, "Distance from you", Math.round(d) + " blocks", true);
            if (who.on()) field(e, "Found by", mc.player.getName().getString(), true);
        }
    }

    private static String dimension(Minecraft mc) {
        if (mc.level == null) return "Unknown";
        String s = mc.level.dimension().toString();
        int i = s.lastIndexOf(':');
        String name = i < 0 ? s : s.substring(i + 1).replace("]", "").trim();
        return switch (name) {
            case "overworld" -> "Overworld";
            case "the_nether" -> "Nether";
            case "the_end" -> "The End";
            default -> name;
        };
    }

    private static String wrap(JsonObject embed) {
        JsonObject root = new JsonObject();
        root.addProperty("username", "Zeus");
        JsonArray arr = new JsonArray();
        arr.add(embed);
        root.add("embeds", arr);
        JsonObject allowed = new JsonObject();
        allowed.add("parse", new JsonArray()); // nothing in the message can ping anyone
        root.add("allowed_mentions", allowed);
        return root.toString();
    }

    // ------------------------------------------------------------ sending (one thread, one message at a time)

    private static void push(String body, String url) {
        push(body, url, 3);
    }

    private static void push(String body, String url, int pauseSeconds) {
        synchronized (QUEUE) {
            if (QUEUE.size() > 30) return;
            QUEUE.add(url + "\n" + pauseSeconds + "\n" + body);
            if (worker == null || !worker.isAlive()) {
                worker = new Thread(DiscordWebhook::run, "zeus-webhook");
                worker.setDaemon(true);
                worker.start();
            }
        }
    }

    private static void run() {
        if (http == null) http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
        while (true) {
            String job;
            try {
                job = QUEUE.poll(20, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                return;
            }
            if (job == null) return;
            String[] parts = job.split("\n", 3);
            int pauseMs = 3000;
            try {
                pauseMs = Math.max(1000, Integer.parseInt(parts[1]) * 1000);
            } catch (NumberFormatException ignored) {
                // default
            }
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create(parts[0]))
                        .timeout(Duration.ofSeconds(12))
                        .header("Content-Type", "application/json")
                        .header("User-Agent", "ZeusClient/" + Zeus.VERSION)
                        .POST(HttpRequest.BodyPublishers.ofString(parts[2]))
                        .build();
                HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
                int code = res.statusCode();
                if (code / 100 == 2) {
                    sentCount++;
                    last = "Last message sent.";
                } else if (code == 429) {
                    last = "Discord asked to slow down; will retry.";
                    pauseMs = 6000;
                    QUEUE.add(job);
                } else if (code == 404 || code == 401) {
                    last = "Discord says this webhook does not exist any more (error " + code + ").";
                } else {
                    last = "Discord answered with error " + code + ".";
                }
            } catch (java.io.IOException | RuntimeException e) {
                last = "Could not reach Discord: " + e.getClass().getSimpleName();
            } catch (InterruptedException e) {
                return;
            }
            try {
                Thread.sleep(pauseMs);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    @Override
    public String info() {
        return ready() ? null : "no webhook";
    }
}
