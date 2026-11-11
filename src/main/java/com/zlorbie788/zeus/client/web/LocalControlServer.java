package com.zlorbie788.zeus.client.web;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Setting;
import com.zlorbie788.zeus.core.Zeus;
import com.zlorbie788.zeus.mods.Brain;
import com.zlorbie788.zeus.mods.WebDashboard;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class LocalControlServer {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final String WEB_ROOT = "/assets/zeus/web/";
    private static final int MAX_BODY = 16 * 1024;
    private static final long STARTED = System.currentTimeMillis();

    private static HttpServer server;
    private static ExecutorService executor;
    private static ScheduledExecutorService taskScheduler;
    private static final ConcurrentMap<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, String> taskLabels = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, Long> taskTimes = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, byte[]> assetCache = new ConcurrentHashMap<>();
    private static String token;
    private static int port = 8765;
    private static String featuresJson;

    private LocalControlServer() {}

    public static synchronized String start(int wantedPort, boolean keepToken) throws IOException {
        if (server != null) return token;
        port = Math.max(1024, Math.min(65535, wantedPort));
        token = keepToken ? storedToken() : freshToken();
        server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 32);
        taskScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Zeus-web-scheduler");
            t.setDaemon(true);
            return t;
        });
        executor = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "Zeus-web");
            t.setDaemon(true);
            return t;
        });
        server.setExecutor(executor);
        server.createContext("/", LocalControlServer::handle);
        server.start();
        Zeus.LOG.info("Zeus website listening on http://127.0.0.1:{}", port);
        return token;
    }

    public static synchronized String start() throws IOException {
        return start(WebDashboard.portNow(), WebDashboard.keepTokenOn());
    }

    public static synchronized void stop() {
        if (server == null) return;
        server.stop(0);
        server = null;
        if (executor != null) executor.shutdownNow();
        executor = null;
        for (ScheduledFuture<?> task : scheduledTasks.values()) task.cancel(false);
        scheduledTasks.clear();
        taskLabels.clear();
        taskTimes.clear();
        if (taskScheduler != null) taskScheduler.shutdownNow();
        taskScheduler = null;
        token = null;
    }

    public static synchronized boolean running() { return server != null; }

    public static synchronized String url() { return server == null ? null : "http://127.0.0.1:" + port; }

    public static synchronized String token() { return token; }

    public static synchronized int portNow() { return port; }

    private static String freshToken() {
        byte[] secret = new byte[32];
        new SecureRandom().nextBytes(secret);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
    }

    private static String storedToken() {
        try {
            Path file = FabricLoader.getInstance().getConfigDir().resolve("zeus").resolve("web-token.txt");
            if (Files.isRegularFile(file)) {
                String saved = Files.readString(file, StandardCharsets.UTF_8).trim();
                if (saved.matches("[A-Za-z0-9_-]{32,}")) return saved;
            }
            String fresh = freshToken();
            Files.createDirectories(file.getParent());
            Files.writeString(file, fresh, StandardCharsets.UTF_8);
            return fresh;
        } catch (IOException | RuntimeException e) {
            Zeus.LOG.warn("Could not keep the web token on disk; using a fresh one", e);
            return freshToken();
        }
    }

    private static void handle(HttpExchange ex) throws IOException {
        try {
            if (!localHost(ex)) {
                json(ex, 403, error("This address is not allowed"));
                return;
            }
            String path = ex.getRequestURI().getPath();
            String method = ex.getRequestMethod();
            if (!path.startsWith("/api/")) {
                if (!"GET".equals(method) && !"HEAD".equals(method)) {
                    json(ex, 405, error("Method not allowed"));
                    return;
                }
                serveStatic(ex, path);
                return;
            }
            if (method.equals("POST") && !sameOrigin(ex)) {
                json(ex, 403, error("Cross-site request refused"));
                return;
            }
            if ("GET".equals(method) && "/api/site".equals(path)) {
                json(ex, 200, site());
                return;
            }
            if ("GET".equals(method) && "/api/features".equals(path)) {
                send(ex, 200, "application/json; charset=utf-8", features());
                return;
            }
            if (!authorized(ex)) {
                json(ex, 401, error("Unauthorized. Paste the access token shown in Minecraft (Web Dashboard settings, or the chat line)."));
                return;
            }
            route(ex, method, path);
        } catch (java.util.concurrent.TimeoutException slow) {
            json(ex, 504, error("Minecraft did not answer in time (is the game paused or loading?)"));
        } catch (Exception failure) {
            Zeus.LOG.warn("Zeus website request failed", failure);
            try {
                json(ex, 500, error("Request failed"));
            } catch (IOException ignored) {
            }
        } finally {
            ex.close();
        }
    }

    private static void route(HttpExchange ex, String method, String path) throws Exception {
        boolean get = "GET".equals(method);
        boolean post = "POST".equals(method);
        if (get && "/api/status".equals(path)) {
            json(ex, 200, onClient(LocalControlServer::status));
        } else if (get && "/api/modules".equals(path)) {
            json(ex, 200, onClient(LocalControlServer::modules));
        } else if (get && "/api/module".equals(path)) {
            String id = query(ex, "id");
            JsonObject out = onClient(() -> moduleDetail(id));
            json(ex, out.has("error") ? 404 : 200, out);
        } else if (post && "/api/module".equals(path)) {
            JsonObject in = body(ex);
            if (in == null) return;
            String id = str(in, "id");
            if (!in.has("enabled") || !in.get("enabled").isJsonPrimitive()) {
                json(ex, 400, error("Expected id and enabled"));
                return;
            }
            boolean enabled = in.get("enabled").getAsBoolean();
            String result = onClient(() -> {
                Module m = Zeus.get(id);
                if (m == null || !m.toggleable()) return "Unknown or non-toggleable module: " + id;
                m.setEnabled(enabled);
                return m.name + " is " + (m.enabled() ? "on" : "off");
            });
            JsonObject out = new JsonObject();
            out.addProperty("message", result);
            json(ex, result.startsWith("Unknown") ? 404 : 200, out);
        } else if (post && "/api/setting".equals(path)) {
            JsonObject in = body(ex);
            if (in == null) return;
            String id = str(in, "id");
            String name = str(in, "name");
            JsonElement value = in.get("value");
            JsonObject out = onClient(() -> changeSetting(id, name, value));
            json(ex, out.has("error") ? 400 : 200, out);
        } else if (post && "/api/action".equals(path)) {
            JsonObject in = body(ex);
            if (in == null) return;
            String id = str(in, "id");
            String name = str(in, "name");
            JsonObject out = onClient(() -> fireAction(id, name));
            json(ex, out.has("error") ? 400 : 200, out);
        } else if (post && "/api/modules/bulk".equals(path)) {
            JsonObject in = body(ex);
            if (in == null) return;
            JsonArray ids = in.has("ids") && in.get("ids").isJsonArray() ? in.getAsJsonArray("ids") : new JsonArray();
            boolean enabled = in.has("enabled") && in.get("enabled").getAsBoolean();
            if (ids.size() > 400) {
                json(ex, 400, error("Too many modules"));
                return;
            }
            int changed = onClient(() -> {
                int n = 0;
                for (JsonElement e : ids) {
                    Module m = Zeus.get(e.getAsString());
                    if (m != null && m.toggleable() && m.enabled() != enabled) {
                        m.setEnabled(enabled);
                        n++;
                    }
                }
                return n;
            });
            JsonObject out = new JsonObject();
            out.addProperty("message", changed + " module(s) switched " + (enabled ? "on" : "off"));
            json(ex, 200, out);
        } else if (get && "/api/atlas".equals(path)) {
            json(ex, 200, onClient(LocalControlServer::atlas));
        } else if (get && "/api/events".equals(path)) {
            long after = 0;
            try {
                after = Long.parseLong(query(ex, "after"));
            } catch (NumberFormatException ignored) {
            }
            JsonArray list = new JsonArray();
            long last = after;
            for (Zeus.Event e : Zeus.eventsAfter(after)) {
                JsonObject o = new JsonObject();
                o.addProperty("id", e.id());
                o.addProperty("at", e.at());
                o.addProperty("text", e.text());
                o.addProperty("good", e.good());
                list.add(o);
                last = Math.max(last, e.id());
            }
            JsonObject out = new JsonObject();
            out.add("events", list);
            out.addProperty("last", last);
            json(ex, 200, out);
        } else if (post && "/api/panic".equals(path)) {
            Minecraft.getInstance().execute(() -> Zeus.panic(false));
            JsonObject out = new JsonObject();
            out.addProperty("message", "All Zeus modules queued to switch off");
            json(ex, 202, out);
        } else if (get && "/api/tasks".equals(path)) {
            json(ex, 200, tasks());
        } else if (post && "/api/task".equals(path)) {
            scheduleTask(ex);
        } else if (post && "/api/task/cancel".equals(path)) {
            cancelTask(ex);
        } else if (post && "/api/auction".equals(path)) {
            auction(ex);
        } else {
            json(ex, 404, error("Not found"));
        }
    }

    private static JsonObject status() {
        Minecraft mc = Minecraft.getInstance();
        JsonObject o = new JsonObject();
        o.addProperty("version", Zeus.VERSION);
        o.addProperty("uptimeSeconds", (System.currentTimeMillis() - STARTED) / 1000);
        boolean in = mc.level != null && mc.player != null;
        o.addProperty("inWorld", in);
        o.addProperty("player", in ? mc.player.getName().getString() : "");
        o.addProperty("server", mc.getCurrentServer() == null ? (in ? "Singleplayer" : "") : mc.getCurrentServer().ip);
        long on = 0;
        long total = 0;
        for (Module m : Zeus.all()) {
            if (!m.toggleable()) continue;
            total++;
            if (m.enabled()) on++;
        }
        o.addProperty("enabled", on);
        o.addProperty("moduleCount", total);
        o.addProperty("fps", Brain.ctx().fps);
        if (in) {
            o.addProperty("health", Math.round(mc.player.getHealth() * 10f) / 10f);
            o.addProperty("maxHealth", Math.round(mc.player.getMaxHealth() * 10f) / 10f);
            o.addProperty("food", mc.player.getFoodData().getFoodLevel());
            o.addProperty("x", Math.round(mc.player.getX() * 10.0) / 10.0);
            o.addProperty("y", Math.round(mc.player.getY() * 10.0) / 10.0);
            o.addProperty("z", Math.round(mc.player.getZ() * 10.0) / 10.0);
            o.addProperty("dimension", mc.level.dimension().toString());
            try {
                var info = mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(mc.player.getUUID());
                if (info != null) o.addProperty("ping", info.getLatency());
            } catch (RuntimeException ignored) {
            }
        }
        return o;
    }

    private static JsonObject atlas() {
        Minecraft mc = Minecraft.getInstance();
        JsonObject o = new JsonObject();
        String server = Brain.ctx().server;
        o.addProperty("server", server);
        o.addProperty("dimension", Brain.ctx().dim);
        o.addProperty("chesWorld", com.zlorbie788.zeus.mods.ChestMemory.worldKey());
        boolean in = mc.player != null && mc.level != null;
        o.addProperty("inWorld", in);
        if (in) {
            o.addProperty("x", Math.round(mc.player.getX() * 10.0) / 10.0);
            o.addProperty("y", Math.round(mc.player.getY() * 10.0) / 10.0);
            o.addProperty("z", Math.round(mc.player.getZ() * 10.0) / 10.0);
        }
        JsonArray bases = new JsonArray();
        for (com.zlorbie788.zeus.mods.Ledger.Entry e : com.zlorbie788.zeus.mods.Ledger.list(server)) {
            JsonObject b = new JsonObject();
            b.addProperty("x", Math.round(e.x * 10.0) / 10.0);
            b.addProperty("z", Math.round(e.z * 10.0) / 10.0);
            b.addProperty("type", e.type);
            b.addProperty("status", e.status);
            b.addProperty("confidence", e.confidence);
            b.addProperty("first", e.first);
            b.addProperty("last", e.last);
            bases.add(b);
        }
        o.add("bases", bases);

        JsonArray chests = new JsonArray();
        for (double[] c : com.zlorbie788.zeus.mods.ChestMemory.spots()) {
            JsonObject j = new JsonObject();
            j.addProperty("x", c[0]);
            j.addProperty("y", c[1]);
            j.addProperty("z", c[2]);
            j.addProperty("at", (long) c[3]);
            j.addProperty("kinds", (int) c[4]);
            chests.add(j);
        }
        o.add("chests", chests);

        JsonArray finds = new JsonArray();
        for (double[] f : com.zlorbie788.zeus.mods.SuspiciousChunks.findsNow()) {
            JsonObject j = new JsonObject();
            j.addProperty("x", f[0]);
            j.addProperty("z", f[1]);
            j.addProperty("score", Math.round(f[2] * 10.0) / 10.0);
            j.addProperty("chunks", (int) f[3]);
            finds.add(j);
        }
        o.add("finds", finds);

        JsonArray people = new JsonArray();
        for (com.zlorbie788.zeus.mods.Dossier.Sight p : com.zlorbie788.zeus.mods.Dossier.sightings()) {
            JsonObject j = new JsonObject();
            j.addProperty("name", p.name);
            j.addProperty("x", Math.round(p.x * 10.0) / 10.0);
            j.addProperty("z", Math.round(p.z * 10.0) / 10.0);
            j.addProperty("last", p.last);
            j.addProperty("seen", p.seen);
            people.add(j);
        }
        o.add("people", people);
        return o;
    }

    private static JsonObject modules() {
        JsonObject root = new JsonObject();
        JsonArray array = new JsonArray();
        for (Module m : Zeus.all()) {
            JsonObject o = new JsonObject();
            o.addProperty("id", m.id);
            o.addProperty("name", m.name);
            o.addProperty("description", m.desc);
            o.addProperty("category", m.category.name());
            o.addProperty("categoryLabel", m.category.label);
            o.addProperty("toggleable", m.toggleable());
            o.addProperty("enabled", m.enabled());
            String info = m.info();
            o.addProperty("info", info == null ? "" : info);
            if (m.error != null) o.addProperty("error", m.error);
            o.addProperty("settings", m.settings().size());
            array.add(o);
        }
        root.add("modules", array);
        return root;
    }

    private static JsonObject moduleDetail(String id) {
        Module m = Zeus.get(id);
        if (m == null) return error("Unknown module");
        JsonObject o = new JsonObject();
        o.addProperty("id", m.id);
        o.addProperty("name", m.name);
        o.addProperty("description", m.desc);
        o.addProperty("category", m.category.label);
        o.addProperty("toggleable", m.toggleable());
        o.addProperty("enabled", m.enabled());
        o.add("settings", settingsOf(m));
        return o;
    }

    private static JsonArray settingsOf(Module m) {
        JsonArray list = new JsonArray();
        for (Setting s : m.settings()) {
            JsonObject o = describe(s);
            if (o != null) list.add(o);
        }
        return list;
    }

    private static JsonObject describe(Setting s) {
        JsonObject o = new JsonObject();
        o.addProperty("name", s.name);
        o.addProperty("desc", s.desc);
        o.addProperty("visible", s.visible());
        if (s instanceof Setting.Bool b) {
            o.addProperty("type", "bool");
            o.addProperty("value", b.on());
        } else if (s instanceof Setting.Num n) {
            o.addProperty("type", "num");
            o.addProperty("value", n.get());
            o.addProperty("min", n.min);
            o.addProperty("max", n.max);
            o.addProperty("step", n.step);
            o.addProperty("unit", n.unit);
            o.addProperty("text", n.text());
        } else if (s instanceof Setting.Choice c) {
            o.addProperty("type", "choice");
            o.addProperty("value", c.index());
            JsonArray options = new JsonArray();
            for (String option : c.options) options.add(option);
            o.add("options", options);
        } else if (s instanceof Setting.Pick p) {
            o.addProperty("type", "pick");
            o.addProperty("value", p.index());
            JsonArray options = new JsonArray();
            for (String label : p.labels) options.add(label);
            o.add("options", options);
        } else if (s instanceof Setting.Text t) {
            o.addProperty("type", "text");
            o.addProperty("value", t.get());
            o.addProperty("max", t.max);
        } else if (s instanceof Setting.Action) {
            o.addProperty("type", "action");
        } else if (s instanceof Setting.Info i) {
            o.addProperty("type", "info");
            JsonArray lines = new JsonArray();
            for (String line : i.lines()) lines.add(line);
            o.add("lines", lines);
        } else {
            return null;
        }
        return o;
    }

    private static JsonObject changeSetting(String id, String name, JsonElement value) {
        Module m = Zeus.get(id);
        if (m == null) return error("Unknown module");
        if (value == null || !value.isJsonPrimitive()) return error("Expected a value");
        for (Setting s : m.settings()) {
            if (!s.name.equals(name)) continue;
            try {
                if (s instanceof Setting.Bool b) b.set(value.getAsBoolean());
                else if (s instanceof Setting.Num n) {
                    double v = value.getAsDouble();
                    if (!Double.isFinite(v)) return error("Not a number");
                    n.set(v);
                } else if (s instanceof Setting.Choice c) c.set(value.getAsInt());
                else if (s instanceof Setting.Pick p) p.set(value.getAsInt());
                else if (s instanceof Setting.Text t) t.set(value.getAsString());
                else return error("This setting cannot be changed here");
            } catch (RuntimeException bad) {
                return error("That value does not fit this setting");
            }
            JsonObject out = new JsonObject();
            out.addProperty("enabled", m.enabled());
            out.add("settings", settingsOf(m));
            return out;
        }
        return error("Unknown setting");
    }

    private static JsonObject fireAction(String id, String name) {
        Module m = Zeus.get(id);
        if (m == null) return error("Unknown module");
        for (Setting s : m.settings()) {
            if (s.name.equals(name) && s instanceof Setting.Action a) {
                a.fire();
                JsonObject out = new JsonObject();
                out.addProperty("message", name + " done");
                out.add("settings", settingsOf(m));
                return out;
            }
        }
        return error("Unknown action");
    }

    private static JsonObject site() {
        JsonObject o = new JsonObject();
        o.addProperty("version", Zeus.VERSION);
        o.addProperty("minecraft", "1.21.11");
        o.addProperty("local", true);
        o.addProperty("discordUrl", WebDashboard.discordUrl());
        o.addProperty("downloadUrl", WebDashboard.downloadUrl());
        o.addProperty("youtubeUrl", WebDashboard.youtubeUrl());
        o.addProperty("twitterUrl", WebDashboard.twitterUrl());
        o.addProperty("tutorialUrl", WebDashboard.tutorialUrl());
        long n = 0;
        for (Module m : Zeus.all()) if (m.toggleable()) n++;
        o.addProperty("moduleCount", n);
        return o;
    }

    private static synchronized String features() {
        if (featuresJson == null) featuresJson = com.zlorbie788.zeus.core.FeatureCatalog.json();
        return featuresJson;
    }

    private static JsonObject tasks() {
        JsonObject root = new JsonObject();
        JsonArray list = new JsonArray();
        long now = System.currentTimeMillis();
        for (var entry : scheduledTasks.entrySet()) {
            JsonObject item = new JsonObject();
            item.addProperty("id", entry.getKey());
            item.addProperty("label", taskLabels.getOrDefault(entry.getKey(), "Scheduled task"));
            long at = taskTimes.getOrDefault(entry.getKey(), now);
            item.addProperty("runAt", at);
            item.addProperty("remainingSeconds", Math.max(0, (at - now + 999) / 1000));
            list.add(item);
        }
        root.add("tasks", list);
        return root;
    }

    private static void scheduleTask(HttpExchange ex) throws Exception {
        JsonObject in = body(ex);
        if (in == null) return;
        String moduleId = str(in, "moduleId");
        String action = str(in, "action");
        int delay = in.has("delaySeconds") ? in.get("delaySeconds").getAsInt() : 0;
        if (!moduleId.matches("[a-z0-9_-]{1,64}") || !(action.equals("enable") || action.equals("disable")) || delay < 1 || delay > 86400) {
            json(ex, 400, error("Choose a module, enable/disable action and delay from 1 to 86400 seconds"));
            return;
        }
        String moduleName = onClient(() -> {
            Module m = Zeus.get(moduleId);
            return m == null || !m.toggleable() ? "" : m.name;
        });
        if (moduleName.isEmpty()) {
            json(ex, 404, error("Unknown or non-toggleable module"));
            return;
        }
        String id = UUID.randomUUID().toString();
        long runAt = System.currentTimeMillis() + delay * 1000L;
        String label = (action.equals("enable") ? "Enable " : "Disable ") + moduleName;
        ScheduledFuture<?> future = taskScheduler.schedule(() -> {
            Minecraft.getInstance().execute(() -> {
                Module m = Zeus.get(moduleId);
                if (m != null && m.toggleable()) m.setEnabled(action.equals("enable"));
            });
            scheduledTasks.remove(id);
            taskLabels.remove(id);
            taskTimes.remove(id);
        }, delay, TimeUnit.SECONDS);
        scheduledTasks.put(id, future);
        taskLabels.put(id, label);
        taskTimes.put(id, runAt);
        JsonObject out = new JsonObject();
        out.addProperty("message", label + " scheduled in " + delay + " seconds.");
        out.addProperty("id", id);
        json(ex, 201, out);
    }

    private static void cancelTask(HttpExchange ex) throws Exception {
        JsonObject in = body(ex);
        if (in == null) return;
        String id = str(in, "id");
        ScheduledFuture<?> future = scheduledTasks.remove(id);
        if (future == null) {
            json(ex, 404, error("Task not found or already completed"));
            return;
        }
        future.cancel(false);
        taskLabels.remove(id);
        taskTimes.remove(id);
        JsonObject out = new JsonObject();
        out.addProperty("message", "Scheduled task cancelled");
        json(ex, 200, out);
    }

    private static void auction(HttpExchange ex) throws Exception {
        JsonObject in = body(ex);
        if (in == null) return;
        String watched = in.has("watchedItems") ? in.get("watchedItems").getAsString() : "elytra,mace,netherite,diamond";
        String ceiling = in.has("maxPrice") ? in.get("maxPrice").getAsString() : "0";
        double discount = in.has("discount") ? in.get("discount").getAsDouble() : 20.0;
        if (watched.length() > 180 || ceiling.length() > 16
                || !ceiling.trim().matches("(?i)[0-9][0-9,]*(?:\\.[0-9]+)?\\s*[kmb]?")
                || !Double.isFinite(discount)) {
            json(ex, 400, error("Invalid auction settings"));
            return;
        }
        String result = onClient(() -> {
            com.zlorbie788.zeus.mods.AuctionHouseTweaks auction = Zeus.get(com.zlorbie788.zeus.mods.AuctionHouseTweaks.class);
            if (auction == null) return "Auction House Tweaks module is unavailable.";
            auction.configureMarketFromDashboard(watched, ceiling, Math.max(1, Math.min(90, discount)));
            if (!auction.enabled()) auction.setEnabled(true);
            return "Market watch configured and enabled. Alerts only; review listings before buying.";
        });
        JsonObject out = new JsonObject();
        out.addProperty("message", result);
        json(ex, 200, out);
    }

    private static boolean localHost(HttpExchange ex) {
        String host = ex.getRequestHeaders().getFirst("Host");
        if (host == null) return false;
        String h = host.toLowerCase(Locale.ROOT);
        int p;
        synchronized (LocalControlServer.class) {
            p = port;
        }
        return h.equals("127.0.0.1:" + p) || h.equals("localhost:" + p) || h.equals("[::1]:" + p);
    }

    private static boolean sameOrigin(HttpExchange ex) {
        String origin = ex.getRequestHeaders().getFirst("Origin");
        if (origin == null) return true;
        String o = origin.toLowerCase(Locale.ROOT);
        int p;
        synchronized (LocalControlServer.class) {
            p = port;
        }
        return o.equals("http://127.0.0.1:" + p) || o.equals("http://localhost:" + p);
    }

    private static boolean authorized(HttpExchange ex) {
        String header = ex.getRequestHeaders().getFirst("Authorization");
        String expected;
        synchronized (LocalControlServer.class) {
            expected = token;
        }
        if (header == null || expected == null || !header.startsWith("Bearer ")) return false;
        return MessageDigest.isEqual(header.substring(7).getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
    }

    private static <T> T onClient(Callable<T> task) throws Exception {
        var future = new CompletableFuture<T>();
        Minecraft.getInstance().execute(() -> {
            try {
                future.complete(task.call());
            } catch (Throwable error) {
                future.completeExceptionally(error);
            }
        });
        return future.get(4, TimeUnit.SECONDS);
    }

    private static JsonObject error(String message) {
        JsonObject o = new JsonObject();
        o.addProperty("error", message);
        return o;
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : "";
    }

    private static String query(HttpExchange ex, String key) {
        String q = ex.getRequestURI().getRawQuery();
        if (q == null) return "";
        for (String part : q.split("&")) {
            int eq = part.indexOf('=');
            if (eq > 0 && part.substring(0, eq).equals(key)) {
                return java.net.URLDecoder.decode(part.substring(eq + 1), StandardCharsets.UTF_8);
            }
        }
        return "";
    }

    private static JsonObject body(HttpExchange ex) throws IOException {
        byte[] bytes = ex.getRequestBody().readNBytes(MAX_BODY + 1);
        if (bytes.length > MAX_BODY) {
            json(ex, 413, error("Request too large"));
            return null;
        }
        try {
            return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (RuntimeException bad) {
            json(ex, 400, error("Invalid JSON"));
            return null;
        }
    }

    private static void json(HttpExchange ex, int code, JsonElement body) throws IOException {
        send(ex, code, "application/json; charset=utf-8", GSON.toJson(body));
    }

    private static void send(HttpExchange ex, int code, String type, String body) throws IOException {
        sendBytes(ex, code, type, body.getBytes(StandardCharsets.UTF_8), false);
    }

    private static void sendBytes(HttpExchange ex, int code, String type, byte[] bytes, boolean cacheable) throws IOException {
        var h = ex.getResponseHeaders();
        h.set("Content-Type", type);
        h.set("Cache-Control", cacheable ? "public, max-age=300" : "no-store");
        h.set("X-Content-Type-Options", "nosniff");
        h.set("Referrer-Policy", "no-referrer");
        h.set("X-Frame-Options", "DENY");
        h.set("Content-Security-Policy", "default-src 'none'; img-src 'self' data:; media-src 'self'; style-src 'self' 'unsafe-inline'; script-src 'self'; font-src 'self'; connect-src 'self'; form-action 'none'; base-uri 'none'; frame-ancestors 'none'");
        boolean head = "HEAD".equals(ex.getRequestMethod());
        ex.sendResponseHeaders(code, head ? -1 : bytes.length);
        if (!head) ex.getResponseBody().write(bytes);
    }

    private static final Map<String, String> TYPES = Map.ofEntries(
            Map.entry("html", "text/html; charset=utf-8"),
            Map.entry("css", "text/css; charset=utf-8"),
            Map.entry("js", "text/javascript; charset=utf-8"),
            Map.entry("json", "application/json; charset=utf-8"),
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("svg", "image/svg+xml"),
            Map.entry("woff", "font/woff"),
            Map.entry("woff2", "font/woff2"),
            Map.entry("txt", "text/plain; charset=utf-8"),
            Map.entry("webp", "image/webp"),
            Map.entry("gif", "image/gif"),
            Map.entry("mp4", "video/mp4"),
            Map.entry("webm", "video/webm"),
            Map.entry("webmanifest", "application/manifest+json"),
            Map.entry("ico", "image/x-icon"));

    private static void serveStatic(HttpExchange ex, String path) throws IOException {
        String name = "/".equals(path) ? "index.html" : path.substring(1);
        if (name.matches("[a-z-]{1,24}(/[a-z0-9-]{0,40})?/?")) name = "index.html";
        if (!name.matches("[A-Za-z0-9_./-]{1,80}") || name.contains("..") || name.startsWith(".")) {
            json(ex, 404, error("Not found"));
            return;
        }
        int dot = name.lastIndexOf('.');
        String type = dot < 0 ? null : TYPES.get(name.substring(dot + 1).toLowerCase(Locale.ROOT));
        if (type == null) {
            json(ex, 404, error("Not found"));
            return;
        }
        byte[] data = assetCache.get(name);
        if (data == null) {
            try (InputStream in = LocalControlServer.class.getResourceAsStream(WEB_ROOT + name)) {
                if (in == null) {
                    json(ex, 404, error("Not found"));
                    return;
                }
                data = in.readAllBytes();
            }
            assetCache.put(name, data);
        }
        sendBytes(ex, 200, type, data, !name.endsWith(".html"));
    }
}
