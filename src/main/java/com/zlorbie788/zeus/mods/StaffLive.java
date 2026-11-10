package com.zlorbie788.zeus.mods;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Setting;
import com.zlorbie788.zeus.core.Zeus;
import com.zlorbie788.zeus.ui.Accent;
import com.zlorbie788.zeus.ui.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/**
 * Staff Live: tells you when streamers you follow (by default DonutSMP staff) go live on YouTube or TikTok. A background
 * thread asks the public live page of each channel once a minute (or as often as you set) and compares the answer with the
 * last one. When a channel turns live you get a message, a chime and a box with the stream title while it lasts. It reads
 * public web pages only and uses no account of yours.
 */
public final class StaffLive extends Module {
    private static final String BROWSER = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36";
    private static final Pattern HANDLE = Pattern.compile("^@?([A-Za-z0-9._-]{2,40})$");
    private static final Pattern PAGE_URL = Pattern.compile("(?i)(youtube\\.com|tiktok\\.com|twitch\\.tv)/(?:@)?([A-Za-z0-9._-]+)");
    private static final String[] PLATFORMS = {"YouTube", "TikTok", "Twitch"};
    private static final Pattern CANONICAL = Pattern.compile("<link rel=\"canonical\" href=\"([^\"]+)\"");
    private static final Pattern TITLE = Pattern.compile("<meta name=\"title\" content=\"([^\"]*)\"");
    private static final int MAX_PAGE = 900_000;

    private final Setting.Text youtubeList = add(new Setting.Text("YouTube channels", "Handles separated by commas, for example wizoxlol, DrDonutt. This is a starter list: change it to whoever you want to follow.", "wizoxlol, pastagamer08, DrDonutt", 300));
    private final Setting.Text tiktokList = add(new Setting.Text("TikTok accounts", "User names separated by commas, for example theauzzi, u_vv7_.", "pastagamer08, theauzzi, u_vv7_", 300));
    private final Setting.Text twitchList = add(new Setting.Text("Twitch channels", "Channel names separated by commas, for example pastagamer08.", "", 300));
    private final Setting.Text addName = add(new Setting.Text("Add or remove a channel", "Type a name, or paste the address of the channel page (a YouTube, TikTok or Twitch link picks the platform for you), then press a button below.", "", 120).temporary());
    private final Setting.Choice addPlatform = add(new Setting.Choice("Platform for a plain name", "Used when you type just a name and not a link.", 0, "YouTube", "TikTok", "Twitch"));
    private final Setting.Action addButton = addSetting(new Setting.Action("Add this channel", "Adds the name or link above to the right list.", this::addChannel));
    private final Setting.Action removeButton = addSetting(new Setting.Action("Remove this channel", "Removes the name or link above from every list.", this::removeChannel));
    private final Setting.Num every = add(new Setting.Num("Check every", "Seconds between two checks of the whole list. Lower is faster but asks the sites more often.", 60, 15, 600, 5, " s"));
    private final Setting.Bool chime = add(new Setting.Bool("Chime", "A short sound when someone goes live.", true));
    private final Setting.Bool panel = add(new Setting.Bool("Show a box while they are live", "A small box with the name and the stream title.", true));
    private final Setting.Choice corner = Hud.corner(this, 0);
    private final Setting.Action copy = addSetting(new Setting.Action("Copy the link of the stream that is live", "Copies the watch link of the first live stream to your clipboard.", this::copyLink));
    private final Setting.Info status = addSetting(new Setting.Info("Status", this::statusLines));

    private record Channel(int platform, String handle) {
        boolean youtube() {
            return platform == 0;
        }

        String key() {
            return new String[] {"yt:", "tt:", "tw:"}[platform] + handle.toLowerCase(java.util.Locale.ROOT);
        }
    }

    private record Stream(boolean live, String title, String link, String nick) {}

    private record Live(String who, String platform, String title, String link, long since) {}

    private static final Stream OFFLINE = new Stream(false, null, null, null);

    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(10)).build();
    private final Map<String, Boolean> was = new ConcurrentHashMap<>();
    private final Map<String, Live> shown = new ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentLinkedQueue<String> news = new java.util.concurrent.ConcurrentLinkedQueue<>();
    private volatile Thread worker;
    private volatile String last = "Not checked yet.";
    private volatile int checked;

    public StaffLive() {
        super("stafflive", "Staff Live", "Tells you when streamers you follow (DonutSMP staff to start with) go live on YouTube, TikTok or Twitch.", Category.UTILITY);
        hudCard();
        alias("stream alert", "streamer live", "live alert", "donut staff live");
    }

    @Override
    protected void onEnable() {
        was.clear();
        shown.clear();
        news.clear();
        checked = 0;
        last = "Checking...";
        Thread t = new Thread(this::run, "Zeus-StaffLive");
        t.setDaemon(true);
        worker = t;
        t.start();
    }

    @Override
    protected void onDisable() {
        Thread t = worker;
        worker = null;
        if (t != null) t.interrupt();
        shown.clear();
    }

    @Override
    public String info() {
        return shown.isEmpty() ? null : shown.size() + " live";
    }

    private List<String> statusLines() {
        List<String> out = new ArrayList<>();
        out.add(last);
        List<Channel> all = parse();
        out.add(all.size() + " channels, " + shown.size() + " live now");
        for (Channel c : all) {
            Boolean state = was.get(c.key());
            Live l = shown.get(c.key());
            out.add((state == null ? "...  " : state ? "LIVE  " : "off   ") + PLATFORMS[c.platform()] + "  " + c.handle() + (l != null && l.title() != null ? "  " + l.title() : ""));
        }
        return out;
    }

    private void copyLink() {
        for (Live l : shown.values()) {
            if (l.link() != null) {
                Minecraft.getInstance().keyboardHandler.setClipboard(l.link());
                Zeus.toast("Copied the link to " + l.who() + "'s stream", true);
                return;
            }
        }
        Zeus.toast("Nobody is live right now", false);
    }

    private static void collect(List<Channel> out, String list, int platform) {
        for (String entry : list.split(",")) {
            Matcher m = HANDLE.matcher(entry.trim());
            if (!m.find()) continue;
            String name = m.group(1);
            if (platform == 2 && !name.matches("[A-Za-z0-9_]{3,25}")) continue;
            out.add(new Channel(platform, name));
        }
    }

    private List<Channel> parse() {
        List<Channel> out = new ArrayList<>();
        collect(out, youtubeList.get(), 0);
        collect(out, tiktokList.get(), 1);
        collect(out, twitchList.get(), 2);
        return out;
    }

    private Setting.Text listOf(int platform) {
        return platform == 0 ? youtubeList : platform == 1 ? tiktokList : twitchList;
    }

    /** The platform and name in what was typed: a page address gives both, a plain name uses the chosen platform. */
    private Channel typed() {
        String raw = addName.get().trim();
        if (raw.isEmpty()) return null;
        Matcher url = PAGE_URL.matcher(raw);
        if (url.find()) {
            String host = url.group(1).toLowerCase(java.util.Locale.ROOT);
            return new Channel(host.startsWith("youtube") ? 0 : host.startsWith("tiktok") ? 1 : 2, url.group(2));
        }
        Matcher h = HANDLE.matcher(raw);
        return h.find() ? new Channel(addPlatform.index(), h.group(1)) : null;
    }

    private void addChannel() {
        Channel c = typed();
        if (c == null) {
            Zeus.toast("Staff Live: type a channel name or paste its link first", false);
            return;
        }
        for (Channel have : parse()) {
            if (have.key().equals(c.key())) {
                Zeus.toast("Staff Live: " + c.handle() + " is already in the list", false);
                return;
            }
        }
        Setting.Text list = listOf(c.platform());
        String now = list.get().trim();
        list.set(now.isEmpty() ? c.handle() : now + ", " + c.handle());
        addName.set("");
        Zeus.save();
        Zeus.toast("Staff Live: added " + c.handle() + " (" + PLATFORMS[c.platform()] + ")", true);
    }

    private void removeChannel() {
        Channel c = typed();
        if (c == null) {
            Zeus.toast("Staff Live: type the channel name to remove first", false);
            return;
        }
        int removed = 0;
        for (int p = 0; p < 3; p++) {
            if (typedFromLink() && p != c.platform()) continue;
            List<String> keep = new ArrayList<>();
            for (String entry : listOf(p).get().split(",")) {
                String e = entry.trim();
                if (e.isEmpty()) continue;
                if (e.replace("@", "").equalsIgnoreCase(c.handle())) removed++;
                else keep.add(e);
            }
            listOf(p).set(String.join(", ", keep));
        }
        addName.set("");
        Zeus.save();
        Zeus.toast(removed > 0 ? "Staff Live: removed " + c.handle() : "Staff Live: " + c.handle() + " was not in the list", removed > 0);
    }

    private boolean typedFromLink() {
        return PAGE_URL.matcher(addName.get()).find();
    }

    // ------------------------------------------------------------------ the worker

    private void run() {
        long next = 0;
        while (worker == Thread.currentThread()) {
            if (System.currentTimeMillis() >= next) {
                Set<String> keys = new HashSet<>();
                for (Channel c : parse()) {
                    if (worker != Thread.currentThread()) return;
                    keys.add(c.key());
                    check(c);
                }
                was.keySet().retainAll(keys);
                shown.keySet().retainAll(keys);
                checked++;
                last = "Last check: " + java.time.LocalTime.now().withNano(0) + " (" + checked + " rounds)";
                next = System.currentTimeMillis() + (long) (every.get() * 1000);
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private void check(Channel c) {
        Stream s;
        try {
            s = c.platform() == 0 ? youtube(c.handle()) : c.platform() == 1 ? tiktok(c.handle()) : twitch(c.handle());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        } catch (IOException | RuntimeException e) {
            Zeus.LOG.debug("Staff Live: the check of {} failed: {}", c.key(), e.toString());
            return;
        }
        if (s == null) return;
        Boolean before = was.put(c.key(), s.live());
        if (s.live() && !Boolean.TRUE.equals(before)) announce(c, s);
        if (!s.live()) shown.remove(c.key());
    }

    private Stream youtube(String handle) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create("https://www.youtube.com/@" + handle + "/live"))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", BROWSER)
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Accept-Encoding", "gzip")
                .header("Cookie", "CONSENT=YES+1; SOCS=CAI")
                .GET().build();
        HttpResponse<InputStream> res = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream raw = res.body()) {
            if (res.statusCode() != 200) return null;
            boolean gzip = res.headers().firstValue("Content-Encoding").map(v -> v.equalsIgnoreCase("gzip")).orElse(false);
            try (Reader in = new InputStreamReader(gzip ? new GZIPInputStream(raw) : raw, StandardCharsets.UTF_8)) {
                StringBuilder page = new StringBuilder();
                char[] buf = new char[16384];
                int n;
                while ((n = in.read(buf)) > 0 && page.length() < MAX_PAGE) page.append(buf, 0, n);
                Matcher canon = CANONICAL.matcher(page);
                if (!canon.find() || !canon.group(1).contains("/watch?v=")) return OFFLINE;
                boolean live = page.indexOf("\"isLiveNow\":true") >= 0;
                if (!live) return OFFLINE;
                Matcher t = TITLE.matcher(page);
                return new Stream(true, t.find() ? clean(t.group(1)) : null, canon.group(1), null);
            }
        }
    }

    private Stream tiktok(String user) throws IOException, InterruptedException {
        String url = "https://www.tiktok.com/api-live/user/room/?aid=1988&app_language=en&app_name=tiktok_web&sourceType=54&uniqueId=" + URLEncoder.encode(user, StandardCharsets.UTF_8);
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20)).header("User-Agent", BROWSER).header("Accept", "application/json").GET().build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) return null;
        JsonElement body = JsonParser.parseString(res.body());
        if (!body.isJsonObject()) return null;
        JsonObject data = obj(body.getAsJsonObject(), "data");
        JsonObject room = obj(data, "liveRoom");
        if (room == null || !room.has("status")) return null;
        boolean live = room.get("status").getAsInt() == 2;
        if (!live) return OFFLINE;
        JsonObject owner = obj(data, "user");
        String nick = owner != null && owner.has("nickname") ? owner.get("nickname").getAsString() : null;
        String title = room.has("title") ? clean(room.get("title").getAsString()) : null;
        return new Stream(true, title, "https://www.tiktok.com/@" + user + "/live", nick);
    }

    private Stream twitch(String name) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create("https://www.twitch.tv/" + name))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", BROWSER)
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Accept-Encoding", "gzip")
                .GET().build();
        HttpResponse<InputStream> res = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream raw = res.body()) {
            if (res.statusCode() != 200) return null;
            boolean gzip = res.headers().firstValue("Content-Encoding").map(v -> v.equalsIgnoreCase("gzip")).orElse(false);
            try (Reader in = new InputStreamReader(gzip ? new GZIPInputStream(raw) : raw, StandardCharsets.UTF_8)) {
                StringBuilder page = new StringBuilder();
                char[] buf = new char[16384];
                int n;
                while ((n = in.read(buf)) > 0 && page.length() < MAX_PAGE) page.append(buf, 0, n);
                // a channel that is live carries a broadcast event in the page's data; an offline one does not
                boolean live = page.indexOf("\"isLiveBroadcast\":true") >= 0;
                if (!live) return OFFLINE;
                Matcher t = Pattern.compile("<meta property=\"og:description\" content=\"([^\"]*)\"").matcher(page);
                return new Stream(true, t.find() ? clean(t.group(1)) : null, "https://www.twitch.tv/" + name, null);
            }
        }
    }

    private static JsonObject obj(JsonObject parent, String key) {
        if (parent == null || !parent.has(key) || !parent.get(key).isJsonObject()) return null;
        return parent.getAsJsonObject(key);
    }

    private static String clean(String raw) {
        if (raw == null) return null;
        String s = raw.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">").trim();
        if (s.isEmpty()) return null;
        return s.length() > 48 ? s.substring(0, 47) + "..." : s;
    }

    private void announce(Channel c, Stream s) {
        String link = s.link() != null ? s.link() : c.platform() == 2 ? "https://www.twitch.tv/" + c.handle() : "https://www.youtube.com/@" + c.handle() + "/live";
        String who = s.nick() != null && !s.nick().equalsIgnoreCase(c.handle()) ? c.handle() + " (" + s.nick() + ")" : c.handle();
        String platform = PLATFORMS[c.platform()];
        shown.put(c.key(), new Live(who, platform, s.title(), link, System.currentTimeMillis()));
        news.add(who + " is live on " + platform + (s.title() == null ? "" : ": " + s.title()) + "  " + link);
    }

    // ------------------------------------------------------------------ on the game thread

    @Override
    protected void onTick(Minecraft mc) {
        String line;
        while ((line = news.poll()) != null) {
            Zeus.toast(line.substring(0, line.indexOf("  http")), true);
            if (mc.player != null) mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("[Zeus] " + line), false);
            if (chime.on()) {
                try {
                    mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.2f, 0.6f));
                } catch (Throwable ignored) {
                    // a missing sound must never break the module
                }
            }
        }
    }

    @Override
    protected void onHud(GuiGraphics g, Minecraft mc, float partial, int w, int h) {
        if (!panel.on() || shown.isEmpty()) return;
        List<String> rows = new ArrayList<>();
        for (Live l : shown.values()) {
            rows.add(l.who() + "  " + l.platform());
            if (l.title() != null) rows.add("  " + l.title());
        }
        int bw = Draw.width("Staff live") + 20;
        for (String r : rows) bw = Math.max(bw, Draw.width(r) + 16);
        int bh = (rows.size() + 1) * 11 + 6;
        int[] at = Hud.place(corner.index(), bw, bh, w, h);
        Hud.panel(g, at[0], at[1], bw, bh);
        Draw.text(g, "Staff live", at[0] + 7, at[1] + 4, 0xFFFF5C6C);
        for (int i = 0; i < rows.size(); i++) Draw.text(g, rows.get(i), at[0] + 7, at[1] + 4 + (i + 1) * 11, rows.get(i).startsWith("  ") ? Accent.DIM : Accent.TEXT);
    }
}
