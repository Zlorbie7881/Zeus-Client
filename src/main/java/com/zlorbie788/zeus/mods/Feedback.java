package com.zlorbie788.zeus.mods;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zlorbie788.zeus.core.Zeus;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class Feedback {
    private Feedback() {}

    private static final Map<String, Set<Long>> NO = new HashMap<>();
    private static final Map<String, Set<Long>> YES = new HashMap<>();
    private static final Map<String, Double> BIAS = new HashMap<>();
    private static boolean loaded;
    private static final Gson GSON = new GsonBuilder().create();

    private static final double STEP_NO = 2.0;
    private static final double STEP_YES = 1.5;
    private static final double MAX_UP = 12;
    private static final double MAX_DOWN = -6;

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("zeus-feedback.json");
    }

    public static long pack(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    private static void load() {
        if (loaded) return;
        loaded = true;
        try {
            if (!Files.isRegularFile(file())) return;
            JsonObject root = JsonParser.parseString(Files.readString(file(), StandardCharsets.UTF_8)).getAsJsonObject();
            read(root.getAsJsonObject("no"), NO);
            read(root.getAsJsonObject("yes"), YES);
            JsonObject b = root.getAsJsonObject("bias");
            if (b != null) for (Map.Entry<String, JsonElement> e : b.entrySet()) BIAS.put(e.getKey(), e.getValue().getAsDouble());
        } catch (IOException | RuntimeException e) {
            Zeus.LOG.warn("Could not read what you told Zeus about its finds", e);
        }
    }

    private static void read(JsonObject o, Map<String, Set<Long>> into) {
        if (o == null) return;
        for (Map.Entry<String, JsonElement> e : o.entrySet()) {
            Set<Long> set = new HashSet<>();
            for (JsonElement el : e.getValue().getAsJsonArray()) set.add(el.getAsLong());
            into.put(e.getKey(), set);
        }
    }

    private static void save() {
        try {
            JsonObject root = new JsonObject();
            root.add("no", write(NO));
            root.add("yes", write(YES));
            JsonObject b = new JsonObject();
            for (Map.Entry<String, Double> e : BIAS.entrySet()) b.addProperty(e.getKey(), e.getValue());
            root.add("bias", b);
            Files.createDirectories(file().getParent());
            Files.writeString(file(), GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            Zeus.LOG.warn("Could not save what you told Zeus about its finds", e);
        }
    }

    private static JsonObject write(Map<String, Set<Long>> m) {
        JsonObject o = new JsonObject();
        for (Map.Entry<String, Set<Long>> e : m.entrySet()) {
            JsonArray a = new JsonArray();
            for (long l : e.getValue()) a.add(l);
            o.add(e.getKey(), a);
        }
        return o;
    }

    private static String area(String server, String dim) {
        return server + "|" + dim;
    }

    public static void dismiss(String server, String dim, long[][] chunks) {
        load();
        Set<Long> set = NO.computeIfAbsent(area(server, dim), k -> new HashSet<>());
        Set<Long> yes = YES.get(area(server, dim));
        for (long[] c : chunks) {
            set.add(pack((int) c[0], (int) c[1]));
            if (yes != null) yes.remove(pack((int) c[0], (int) c[1]));
        }
        BIAS.merge(server, STEP_NO, (a, b) -> Math.min(MAX_UP, a + b));
        save();
    }

    public static void confirm(String server, String dim, long[][] chunks) {
        load();
        Set<Long> set = YES.computeIfAbsent(area(server, dim), k -> new HashSet<>());
        Set<Long> no = NO.get(area(server, dim));
        for (long[] c : chunks) {
            set.add(pack((int) c[0], (int) c[1]));
            if (no != null) no.remove(pack((int) c[0], (int) c[1]));
        }
        BIAS.merge(server, -STEP_YES, (a, b) -> Math.max(MAX_DOWN, a + b));
        save();
    }

    public static boolean dismissed(String server, String dim, int cx, int cz) {
        load();
        Set<Long> s = NO.get(area(server, dim));
        return s != null && s.contains(pack(cx, cz));
    }

    public static boolean confirmed(String server, String dim, int cx, int cz) {
        load();
        Set<Long> s = YES.get(area(server, dim));
        return s != null && s.contains(pack(cx, cz));
    }

    public static double bias(String server) {
        load();
        return BIAS.getOrDefault(server, 0.0);
    }

    public static int count(String server, String dim, boolean yes) {
        load();
        Set<Long> s = (yes ? YES : NO).get(area(server, dim));
        return s == null ? 0 : s.size();
    }

    public static void forgetAll() {
        load();
        NO.clear();
        YES.clear();
        BIAS.clear();
        save();
    }

    static void reloadForTest() {
        NO.clear();
        YES.clear();
        BIAS.clear();
        loaded = false;
    }
}
