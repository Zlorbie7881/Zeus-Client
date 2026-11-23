package com.zlorbie788.zeus.mods;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Setting;
import com.zlorbie788.zeus.core.Zeus;
import com.zlorbie788.zeus.render.Projector;
import com.zlorbie788.zeus.render.Wire;
import com.zlorbie788.zeus.ui.Accent;
import com.zlorbie788.zeus.ui.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Where the DonutSMP anticheat is currently off, read from the public anticheat feed.
 * Port of SoberClient's Anti Cheat module (dev.sober.modules.AntiCheat and dev.sober.util.AcOffFeed).
 */
public final class AnticheatFeed extends Module {
    private static final String DEFAULT_FEED = "https://anticheat.imjules.be/api.json";
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /** One entry of the feed: a region, its G# number and the world position the alert came from. */
    public record Spot(boolean test, int goliath, int oldGoliath, String region, String coords, double x, double y, double z) {
        String key() {
            return region + "|" + coords + "|" + goliath + "|" + test;
        }
    }

    private final Setting.Text feedUrl = add(new Setting.Text("Feed address", "Where the anticheat feed is read from.", DEFAULT_FEED, 200));
    private final Setting.Num interval = add(new Setting.Num("Check every", "Seconds between two checks of the feed.", 10, 3, 120, 1, " s"));
    private final Setting.Bool includeTests = add(new Setting.Bool("Include test spots", "Also show the entries the feed marks as tests.", false));
    private final Setting.Bool chatAlert = add(new Setting.Bool("Chat alert", "A short message when a spot the feed had not listed appears.", true));
    private final Setting.Bool card = add(new Setting.Bool("Status card", "A card in the corner with the spots and their distance.", true));
    private final Setting.Bool beams = add(new Setting.Bool("Beams at the spots", "A beam into the sky at every spot in range.", true));
    private final Setting.Num beamRange = add(new Setting.Num("Beam range", "Only draw beams for spots this close to you.", 512, 64, 2048, 32, " blocks")).showIf(() -> this.beams.on());
    private final Setting.Info status = add(new Setting.Info("Status", this::statusLines));

    private volatile List<Spot> spots = List.of();
    private volatile String feedError = "";
    private volatile long fetchedAt;
    private volatile long version;
    private volatile boolean polling;

    private long lastPoll;
    private long seenVersion;
    private final Set<String> known = new HashSet<>();
    private boolean seeded;

    public AnticheatFeed() {
        super("anticheatfeed", "Anticheat Feed", "Shows where the DonutSMP anticheat is off, from the public anticheat feed.", Category.UTILITY);
    }

    @Override
    protected void onEnable() {
        lastPoll = 0L;
        seenVersion = 0L;
        version = 0L;
        known.clear();
        seeded = false;
    }

    @Override
    protected void onDisable() {
        spots = List.of();
        feedError = "";
        fetchedAt = 0L;
        version = 0L;
        seenVersion = 0L;
        known.clear();
        seeded = false;
    }

    @Override
    protected void onTick(Minecraft mc) {
        long now = System.currentTimeMillis();
        if (!polling && now - lastPoll >= (long) (this.interval.get() * 1000.0)) {
            lastPoll = now;
            poll();
        }
        if (version != seenVersion) {
            seenVersion = version;
            announce();
        }
    }

    private void poll() {
        String address = feedUrl.get().trim();
        if (address.isEmpty()) {
            feedError = "No feed address set.";
            return;
        }
        final URI uri;
        try {
            uri = URI.create(address);
        } catch (IllegalArgumentException bad) {
            feedError = "That feed address is not a link.";
            return;
        }
        polling = true;
        Thread worker = new Thread(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder(uri)
                        .timeout(Duration.ofSeconds(8))
                        .header("User-Agent", "Zeus/" + Zeus.VERSION)
                        .GET()
                        .build();
                HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    feedError = "Feed answered " + response.statusCode() + ".";
                    return;
                }
                List<Spot> parsed = parse(response.body());
                spots = List.copyOf(parsed);
                feedError = "";
                fetchedAt = System.currentTimeMillis();
                version++;
            } catch (Exception failure) {
                feedError = "Feed not reachable.";
            } finally {
                polling = false;
            }
        }, "Zeus anticheat feed");
        worker.setDaemon(true);
        worker.start();
    }

    /** Reads the feed body, which lists its spots under "alerts". */
    static List<Spot> parse(String body) {
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        List<Spot> out = new ArrayList<>();
        JsonElement alerts = root.get("alerts");
        if (alerts == null || !alerts.isJsonArray()) return out;
        for (JsonElement element : alerts.getAsJsonArray()) {
            if (!element.isJsonObject()) continue;
            JsonObject o = element.getAsJsonObject();
            boolean test = o.has("test") && !o.get("test").isJsonNull() && o.get("test").getAsBoolean();
            int goliath = readInt(o, "goliath", -1);
            int newGoliath = readInt(o, "newGoliath", goliath);
            int oldGoliath = readInt(o, "oldGoliath", goliath);
            String region = readString(o, "region", "Unknown");
            String coords = readString(o, "coords", "");
            double x = 0.0;
            double y = 0.0;
            double z = 0.0;
            JsonElement position = o.get("position");
            if (position != null && position.isJsonObject()) {
                JsonObject p = position.getAsJsonObject();
                x = readDouble(p, "x");
                y = readDouble(p, "y");
                z = readDouble(p, "z");
            } else if (!coords.isBlank()) {
                String[] parts = coords.trim().split("\\s+");
                if (parts.length >= 3) {
                    try {
                        x = Double.parseDouble(parts[0]);
                        y = Double.parseDouble(parts[1]);
                        z = Double.parseDouble(parts[2]);
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            out.add(new Spot(test, newGoliath, oldGoliath, region, coords, x, y, z));
        }
        return out;
    }

    private static int readInt(JsonObject o, String key, int fallback) {
        JsonElement e = o.get(key);
        return e != null && !e.isJsonNull() ? e.getAsInt() : fallback;
    }

    private static double readDouble(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && !e.isJsonNull() ? e.getAsDouble() : 0.0;
    }

    private static String readString(JsonObject o, String key, String fallback) {
        JsonElement e = o.get(key);
        return e != null && !e.isJsonNull() ? e.getAsString() : fallback;
    }

    /** The spots the settings let through. */
    private List<Spot> visible() {
        List<Spot> all = spots;
        if (includeTests.on()) return all;
        List<Spot> out = new ArrayList<>();
        for (Spot spot : all) if (!spot.test()) out.add(spot);
        return out;
    }

    private void announce() {
        List<Spot> list = visible();
        if (!seeded) {
            for (Spot spot : list) known.add(spot.key());
            seeded = true;
            return;
        }
        List<Spot> fresh = new ArrayList<>();
        for (Spot spot : list) if (known.add(spot.key())) fresh.add(spot);
        Set<String> live = new HashSet<>();
        for (Spot spot : list) live.add(spot.key());
        known.retainAll(live);
        if (fresh.isEmpty()) return;
        Spot first = fresh.get(0);
        if (chatAlert.on()) {
            StringBuilder text = new StringBuilder("Anticheat off at ").append(first.region());
            if (!first.coords().isBlank()) text.append(' ').append(first.coords());
            if (fresh.size() > 1) text.append(" (+").append(fresh.size() - 1).append(" more)");
            Zeus.toast(text.toString(), false);
        }
    }

    private List<String> statusLines() {
        List<String> out = new ArrayList<>();
        if (!feedError.isEmpty()) out.add(feedError);
        if (fetchedAt == 0L) {
            out.add("Waiting for the first check.");
        } else {
            out.add("Last checked " + Math.max(0L, (System.currentTimeMillis() - fetchedAt) / 1000L) + " s ago.");
        }
        out.add(visible().size() + " spot(s) with the anticheat off.");
        return out;
    }

    @Override
    public String info() {
        if (!enabled()) return null;
        if (!feedError.isEmpty() && fetchedAt == 0L) return "!";
        int count = visible().size();
        return count <= 0 ? null : Integer.toString(count);
    }

    @Override
    protected void onHud(GuiGraphics g, Minecraft mc, float partial, int w, int h) {
        if (!card.on()) return;
        List<Spot> list = visible();
        if (list.isEmpty() && feedError.isEmpty() && fetchedAt == 0L) return;
        int cw = 232;
        int rows = Math.min(3, list.size());
        int ch = 34 + (rows > 0 ? rows * 11 : 11);
        int x = 8;
        int y = 8;
        boolean bad = !feedError.isEmpty();
        int accent = bad ? Accent.BAD : Accent.WARN;
        Draw.shadow(g, x, y, cw, ch, 8, 6, 0.45f);
        Draw.rrect(g, x, y, cw, ch, 8, 0xFF0D1220, 0xFF171D31);
        Draw.rrectLine(g, x, y, cw, ch, 8, Draw.rgba(accent, 0.75f));
        Draw.text(g, "ANTICHEAT OFF", x + 9, y + 6, accent);
        Draw.textRight(g, list.size() + " spot" + (list.size() == 1 ? "" : "s"), x + cw - 9, y + 6, Accent.DIM);
        if (bad) {
            Draw.text(g, Draw.fit(feedError, cw - 18), x + 9, y + 20, 0xFFFF9AA2);
            Draw.text(g, Draw.fit(fetchedAt == 0L ? "No data yet." : "Showing the last good check.", cw - 18), x + 9, y + 30, Accent.DIM);
            return;
        }
        if (rows == 0) {
            Draw.text(g, "Every region reports on.", x + 9, y + 20, Accent.DIM);
            return;
        }
        for (int i = 0; i < rows; i++) {
            Spot spot = list.get(i);
            int distance = mc.player == null ? -1
                    : (int) Math.round(Math.sqrt(mc.player.distanceToSqr(spot.x(), spot.y(), spot.z())));
            String line = spot.region()
                    + (spot.goliath() >= 0 ? "  G#" + spot.goliath() : "")
                    + (spot.test() ? "  test" : "")
                    + (distance >= 0 ? "  " + distance + "m" : "");
            Draw.text(g, Draw.fit(line, cw - 18), x + 9, y + 20 + (i * 11), i == 0 ? 0xFFFFD9A3 : Accent.DIM);
        }
    }

    @Override
    protected void onHudWorld(GuiGraphics g, Minecraft mc, float partial, int w, int h) {
        if (!beams.on() || mc.player == null) return;
        List<Spot> list = visible();
        if (list.isEmpty()) return;
        double limit = this.beamRange.get();
        double limit2 = limit * limit;
        if (!Projector.prepare(mc, partial, w, h)) return;
        long now = System.currentTimeMillis();
        float pulse = 0.55f + 0.45f * (float) Math.sin(now / 220.0);
        for (Spot spot : list) {
            if (mc.player.distanceToSqr(spot.x(), spot.y(), spot.z()) > limit2) continue;
            int color = Draw.rgba(0xFFFFB03A, 0.55f * pulse);
            Wire.line(g, spot.x() + 0.5, spot.y(), spot.z() + 0.5,
                    spot.x() + 0.5, spot.y() + 96.0, spot.z() + 0.5, 2.2f, color, true);
        }
    }

    public static List<String> help() {
        return List.of("Reads the public anticheat feed and marks every spot where the anticheat is off.",
                "The feed is somebody else's service: when it is down, the card says so and nothing is invented.");
    }
}
