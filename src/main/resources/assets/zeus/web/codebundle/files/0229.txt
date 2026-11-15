package com.zlorbie788.zeus.ui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Zeus;

import java.util.LinkedHashMap;
import java.util.Map;

public final class HudLayout {
    private HudLayout() {}

    public static final class Entry {
        public int x;
        public int y;

        public static final float DEFAULT_SCALE = 1.0f;

        public float scale = DEFAULT_SCALE;
        public float opacity = 1.0f;
        public int accent = 0;
        public boolean visible = true;
    }

    public static final float GLOBAL_SCALE = 0.60f;

    public static String actualSizeId;

    public static float scaleOf(Entry e) { return (e == null ? Entry.DEFAULT_SCALE : e.scale) * GLOBAL_SCALE; }

    private static final Map<String, Entry> ENTRIES = new LinkedHashMap<>();
    public static boolean f3Toggled = false;

    public static Entry get(String id) {
        return ENTRIES.computeIfAbsent(id, ignored -> new Entry());
    }

    public static boolean visible(String id) { return get(id).visible; }

    public static float[] pivot(int[] b, int w, int h) {
        float cx = (b[0] + b[2]) / 2f;
        float cy = (b[1] + b[3]) / 2f;
        return new float[] {cx > w / 2f ? b[2] : b[0], cy > h / 2f ? b[3] : b[1]};
    }

    public static int[] screenRect(String id, int w, int h) {
        int[] b = com.zlorbie788.zeus.mods.Hud.bounds(id);
        if (b == null) return null;
        Entry e = get(id);
        float[] p = pivot(b, w, h);
        float s = scaleOf(e);
        return new int[] {
                Math.round(e.x + p[0] + (b[0] - p[0]) * s), Math.round(e.y + p[1] + (b[1] - p[1]) * s),
                Math.round(e.x + p[0] + (b[2] - p[0]) * s), Math.round(e.y + p[1] + (b[3] - p[1]) * s)};
    }

    public static Entry copyOf(String id) {
        Entry e = get(id);
        Entry c = new Entry();
        c.x = e.x; c.y = e.y; c.scale = e.scale; c.opacity = e.opacity; c.accent = e.accent; c.visible = e.visible;
        return c;
    }

    public static void put(String id, Entry e) {
        ENTRIES.put(id, e);
        Zeus.markDirty();
    }

    public static void setPos(String id, int x, int y) {
        Entry e = get(id);
        e.x = clamp(x, -4000, 4000);
        e.y = clamp(y, -4000, 4000);
        Zeus.markDirty();
    }

    public static void setScale(String id, float s) {
        get(id).scale = Math.max(0.5f, Math.min(2.5f, Math.round(s * 20f) / 20f));
        Zeus.markDirty();
    }

    public static void setOpacity(String id, float o) {
        get(id).opacity = Math.max(0.15f, Math.min(1f, Math.round(o * 20f) / 20f));
        Zeus.markDirty();
    }

    public static void setAccent(String id, int accent) {
        get(id).accent = accent;
        Zeus.markDirty();
    }

    public static void move(String id, int dx, int dy) {
        Entry e = get(id);
        e.x = clamp(e.x + dx, -4000, 4000);
        e.y = clamp(e.y + dy, -4000, 4000);
        Zeus.markDirty();
    }

    public static void scale(String id, float delta) {
        Entry e = get(id);
        e.scale = Math.max(0.5f, Math.min(2.5f, Math.round((e.scale + delta) * 20f) / 20f));
        Zeus.markDirty();
    }

    public static void opacity(String id, float delta) {
        Entry e = get(id);
        e.opacity = Math.max(0.15f, Math.min(1f, Math.round((e.opacity + delta) * 20f) / 20f));
        Zeus.markDirty();
    }

    public static void cycleAccent(String id) {
        Entry e = get(id);
        int[] palette = {0, 0xFF4DA3FF, 0xFFB77BFF, Accent.WARN, Accent.OK, Accent.BAD, 0xFFFFFFFF};
        int next = 0;
        for (int i = 0; i < palette.length; i++) if (palette[i] == e.accent) { next = (i + 1) % palette.length; break; }
        e.accent = palette[next];
        Zeus.markDirty();
    }

    public static void reset(String id) {
        ENTRIES.remove(id);
        Zeus.markDirty();
    }

    public static void resetAll() {
        ENTRIES.clear();
        Zeus.markDirty();
    }

    public static JsonObject save() {
        JsonObject root = new JsonObject();
        for (Map.Entry<String, Entry> item : ENTRIES.entrySet()) {
            Entry e = item.getValue();
            JsonObject value = new JsonObject();
            value.addProperty("x", e.x);
            value.addProperty("y", e.y);
            value.addProperty("scale", e.scale);
            value.addProperty("opacity", e.opacity);
            value.addProperty("accent", e.accent);
            value.addProperty("visible", e.visible);
            root.add(item.getKey(), value);
        }
        return root;
    }

    public static void load(JsonObject root) {
        ENTRIES.clear();
        if (root == null) return;
        for (Map.Entry<String, JsonElement> item : root.entrySet()) {
            if (!item.getValue().isJsonObject()) continue;
            JsonObject value = item.getValue().getAsJsonObject();
            Entry e = new Entry();
            e.x = integer(value, "x", 0, -4000, 4000);
            e.y = integer(value, "y", 0, -4000, 4000);
            try { e.scale = Math.max(0.5f, Math.min(2.5f, value.has("scale") ? value.get("scale").getAsFloat() : Entry.DEFAULT_SCALE)); }
            catch (RuntimeException ignored) { e.scale = Entry.DEFAULT_SCALE; }
            try { e.opacity = Math.max(0.15f, Math.min(1f, value.has("opacity") ? value.get("opacity").getAsFloat() : 1f)); }
            catch (RuntimeException ignored) { e.opacity = 1f; }
            try { e.accent = value.has("accent") ? value.get("accent").getAsInt() : 0; }
            catch (RuntimeException ignored) { e.accent = 0; }
            try { e.visible = !value.has("visible") || value.get("visible").getAsBoolean(); }
            catch (RuntimeException ignored) { e.visible = true; }
            ENTRIES.put(item.getKey(), e);
        }
    }

    private static int integer(JsonObject o, String key, int fallback, int min, int max) {
        try { return Math.max(min, Math.min(max, o.has(key) ? o.get(key).getAsInt() : fallback)); }
        catch (RuntimeException ignored) { return fallback; }
    }

    private static int clamp(int n, int min, int max) { return Math.max(min, Math.min(max, n)); }
}
