package com.zlorbie788.zeus.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * What the menu learns from GitHub, in the background: the news shown on the scroll (NEWS.md on the
 * main branch, or the latest release notes) and whether a newer build is out. Results are cached in
 * config/zeus/news.json so the scroll fills instantly next time and works offline.
 */
public final class MenuOnline {
    private MenuOnline() {
    }

    private static final String REPO = "Zlorbie7881/Zeus-Client";
    private static final String NEWS_URL = "https://raw.githubusercontent.com/" + REPO + "/main/NEWS.md";
    private static final String LATEST_URL = "https://api.github.com/repos/" + REPO + "/releases/latest";
    public static final String RELEASES_PAGE = "https://github.com/" + REPO + "/releases/latest";

    /** One line of the news: kind 0 heading, 1 text, 2 bullet, 3 small, 4 gap. */
    public record Line(int kind, String text) {
    }

    private static volatile List<Line> news = List.of();
    private static volatile int latestBuild;
    private static volatile String latestName = "";
    private static volatile boolean started;
    private static int ownBuild = -1;

    public static List<Line> news() {
        start();
        return news;
    }

    /** This jar's GitHub build number; 0 for a build made on someone's own PC. */
    public static int ownBuild() {
        if (ownBuild < 0) {
            ownBuild = 0;
            try (InputStream in = MenuOnline.class.getResourceAsStream("/assets/zeus/build.properties")) {
                if (in != null) {
                    Properties p = new Properties();
                    p.load(in);
                    ownBuild = Integer.parseInt(p.getProperty("build", "0").trim());
                }
            } catch (Exception ignored) {
            }
        }
        return ownBuild;
    }

    /** The newer build's number when one is out, otherwise 0. */
    public static int updateAvailable() {
        start();
        int own = ownBuild();
        return own > 0 && latestBuild > own ? latestBuild : 0;
    }

    public static String latestName() {
        return latestName;
    }

    private static Path cache() {
        return FabricLoader.getInstance().getConfigDir().resolve("zeus").resolve("news.json");
    }

    public static synchronized void start() {
        if (started) return;
        started = true;
        try {
            if (Files.exists(cache())) {
                JsonObject c = JsonParser.parseString(Files.readString(cache())).getAsJsonObject();
                if (c.has("news")) news = parse(c.get("news").getAsString());
                if (c.has("latest")) latestBuild = c.get("latest").getAsInt();
                if (c.has("latestName")) latestName = c.get("latestName").getAsString();
            }
        } catch (Exception ignored) {
        }
        Thread t = new Thread(MenuOnline::fetch, "Zeus menu news");
        t.setDaemon(true);
        t.start();
    }

    private static void fetch() {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).followRedirects(HttpClient.Redirect.NORMAL).build();
        String newsText = null;
        try {
            HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(NEWS_URL)).timeout(Duration.ofSeconds(8))
                    .header("User-Agent", "ZeusClient").GET().build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (r.statusCode() == 200 && !r.body().isBlank()) newsText = r.body();
        } catch (Exception ignored) {
        }
        try {
            HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(LATEST_URL)).timeout(Duration.ofSeconds(8))
                    .header("User-Agent", "ZeusClient").header("Accept", "application/vnd.github+json").GET().build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (r.statusCode() == 200) {
                JsonObject rel = JsonParser.parseString(r.body()).getAsJsonObject();
                String tag = rel.has("tag_name") ? rel.get("tag_name").getAsString() : "";
                if (tag.startsWith("build-")) latestBuild = Integer.parseInt(tag.substring(6));
                latestName = rel.has("name") && !rel.get("name").isJsonNull() ? rel.get("name").getAsString() : tag;
                if (newsText == null) newsText = fromRelease(rel);
            }
        } catch (Exception ignored) {
        }
        if (newsText != null) news = parse(newsText);
        try {
            JsonObject c = new JsonObject();
            if (newsText != null) c.addProperty("news", newsText);
            else if (Files.exists(cache())) {
                JsonElement old = JsonParser.parseString(Files.readString(cache())).getAsJsonObject().get("news");
                if (old != null) c.add("news", old);
            }
            c.addProperty("latest", latestBuild);
            c.addProperty("latestName", latestName);
            Files.createDirectories(cache().getParent());
            Files.writeString(cache(), c.toString());
        } catch (Exception ignored) {
        }
    }

    /** Release notes as news: the commit message, without the automatic download lines. */
    private static String fromRelease(JsonObject rel) {
        String body = rel.has("body") && !rel.get("body").isJsonNull() ? rel.get("body").getAsString() : "";
        StringBuilder b = new StringBuilder("# What's new\n");
        String name = rel.has("name") && !rel.get("name").isJsonNull() ? rel.get("name").getAsString() : "";
        if (!name.isEmpty()) b.append("~ ").append(name).append("\n");
        for (String l : body.split("\n")) {
            String s = l.trim();
            if (s.startsWith("Automatic build") || s.startsWith("Download the .jar")) continue;
            b.append(s).append("\n");
        }
        return b.toString();
    }

    /** A small subset of Markdown: # headings, - bullets, ~ small print, blank lines, plain text. */
    static List<Line> parse(String md) {
        List<Line> out = new ArrayList<>();
        boolean gap = false;
        for (String raw : md.replace("\r", "").split("\n")) {
            String s = raw.trim();
            if (s.isEmpty()) {
                if (!out.isEmpty()) gap = true;
                continue;
            }
            if (gap) {
                out.add(new Line(4, ""));
                gap = false;
            }
            s = s.replace("**", "").replace("__", "").replace("`", "");
            if (s.startsWith("#")) out.add(new Line(0, s.replaceFirst("^#+\\s*", "")));
            else if (s.startsWith("- ") || s.startsWith("* ")) out.add(new Line(2, s.substring(2).trim()));
            else if (s.startsWith("~")) out.add(new Line(3, s.substring(1).trim()));
            else out.add(new Line(1, s));
            if (out.size() > 80) break;
        }
        return out;
    }
}
