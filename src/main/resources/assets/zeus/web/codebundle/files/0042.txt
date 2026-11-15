package com.zlorbie788.zeus.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class Usage {
    private Usage() {}

    public static final int SORT_DEFAULT = 0;
    public static final int SORT_USED = 1;
    public static final int SORT_NAME = 2;
    private static final int RECENT_MAX = 8;

    public static final String[] POPULAR = {
            "flight", "speed", "xray", "fullbright", "esp", "freecam", "zoom", "nametags", "waypoints", "killaura",
            "autototem", "autoeat", "autotool", "nofall", "autosprint", "suspicious", "basefinder", "storageesp", "blockesp", "scaffold",
            "fastplace", "fastbreak", "step", "triggerbot", "aimassist", "reach", "autoclicker", "crystalaura", "autoarmor", "hitboxes",
            "chestmemory", "jesus", "spider", "safewalk", "antiafk", "autofish", "itemesp", "holeesp", "lightoverlay", "trajectories",
            "breadcrumbs", "timechanger", "tunnelbasefinder", "mediabasefinder", "caveesp", "spawneresp", "baseclues", "keystrokes", "modulelist", "infohud",
            "armorhud", "potionhud", "targethud", "radar", "compass", "watermark", "highjump", "longjump", "airjump", "antiknockback",
            "autoweapon", "bowaimbot", "bowspam", "surround", "holefiller", "elytraboost", "elytrawaypoint", "freelook", "lowhealth", "targetesp",
            "weatherchanger", "aurora", "chunkborders", "blockoutline", "nuker", "treebot", "tunnelminer", "autoreplenish", "durabilityalert", "coordsnapper", "screenshottweaks",
            "autosell", "auction_house_tweaks", "antivanish", "staffhud", "threatradar", "friends", "knife"
    };

    private static final Map<String, Integer> RANK = new HashMap<>();
    static {
        for (int i = 0; i < POPULAR.length; i++) RANK.put(POPULAR[i], i);
    }

    private static final Set<String> FAVOURITES = new LinkedHashSet<>();
    private static final List<String> RECENT = new ArrayList<>();
    private static final Map<String, Integer> COUNT = new HashMap<>();

    public static int rank(String id) {
        return RANK.getOrDefault(id, POPULAR.length + 1);
    }

    public static boolean favourite(Module m) {
        return FAVOURITES.contains(m.id);
    }

    public static void toggleFavourite(Module m) {
        if (!FAVOURITES.remove(m.id)) FAVOURITES.add(m.id);
        Zeus.markDirty();
    }

    public static int favouriteCount() {
        return FAVOURITES.size();
    }

    public static List<Module> favourites() {
        List<Module> out = new ArrayList<>();
        for (String id : FAVOURITES) {
            Module m = Zeus.get(id);
            if (m != null) out.add(m);
        }
        return out;
    }

    public static List<Module> recent() {
        List<Module> out = new ArrayList<>();
        for (String id : RECENT) {
            Module m = Zeus.get(id);
            if (m != null && m.toggleable()) out.add(m);
        }
        return out;
    }

    public static int count(Module m) {
        return COUNT.getOrDefault(m.id, 0);
    }

    public static void used(Module m) {
        if (!m.toggleable()) return;
        COUNT.merge(m.id, 1, Integer::sum);
        RECENT.remove(m.id);
        RECENT.add(0, m.id);
        while (RECENT.size() > RECENT_MAX) RECENT.remove(RECENT.size() - 1);
        Zeus.markDirty();
    }

    public static int score(Module m) {
        return Math.min(500, count(m)) * 1000 + Math.max(0, POPULAR.length + 2 - rank(m.id));
    }

    public static List<Module> sorted(List<Module> in, int mode) {
        if (mode == SORT_DEFAULT || in.size() < 2) return in;
        List<Module> out = new ArrayList<>(in);
        if (mode == SORT_NAME) out.sort(Comparator.comparing((Module m) -> m.name.toLowerCase(Locale.ROOT)));
        else out.sort((a, b) -> {
            int c = Integer.compare(score(b), score(a));
            return c != 0 ? c : a.name.compareToIgnoreCase(b.name);
        });
        return out;
    }

    public static void save(JsonObject root) {
        JsonObject o = new JsonObject();
        JsonArray fav = new JsonArray();
        for (String id : FAVOURITES) fav.add(id);
        o.add("favourites", fav);
        JsonArray rec = new JsonArray();
        for (String id : RECENT) rec.add(id);
        o.add("recent", rec);
        JsonObject counts = new JsonObject();
        for (Map.Entry<String, Integer> e : COUNT.entrySet()) if (e.getValue() > 0) counts.addProperty(e.getKey(), e.getValue());
        o.add("counts", counts);
        root.add("usage", o);
    }

    public static void load(JsonObject root) {
        FAVOURITES.clear();
        RECENT.clear();
        COUNT.clear();
        if (root == null || !root.has("usage") || !root.get("usage").isJsonObject()) return;
        JsonObject o = root.getAsJsonObject("usage");
        try {
            if (o.has("favourites") && o.get("favourites").isJsonArray()) {
                for (JsonElement e : o.getAsJsonArray("favourites")) if (e.isJsonPrimitive()) FAVOURITES.add(e.getAsString());
            }
            if (o.has("recent") && o.get("recent").isJsonArray()) {
                for (JsonElement e : o.getAsJsonArray("recent")) {
                    if (e.isJsonPrimitive() && RECENT.size() < RECENT_MAX) RECENT.add(e.getAsString());
                }
            }
            if (o.has("counts") && o.get("counts").isJsonObject()) {
                for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("counts").entrySet()) {
                    try {
                        COUNT.put(e.getKey(), Math.max(0, e.getValue().getAsInt()));
                    } catch (RuntimeException ignored) {
                    }
                }
            }
        } catch (RuntimeException e) {
            Zeus.LOG.warn("Could not read the favourites and usage; starting them empty", e);
        }
    }
}
