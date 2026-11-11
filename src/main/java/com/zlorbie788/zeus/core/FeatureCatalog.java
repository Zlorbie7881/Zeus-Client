package com.zlorbie788.zeus.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Locale;

public final class FeatureCatalog {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private FeatureCatalog() {}

    public static String json() {
        JsonObject root = new JsonObject();
        JsonArray cats = new JsonArray();
        for (Category c : Category.values()) {
            JsonObject o = new JsonObject();
            o.addProperty("id", c.name());
            o.addProperty("label", c.label);
            o.addProperty("color", String.format(Locale.ROOT, "#%06X", c.color & 0xFFFFFF));
            cats.add(o);
        }
        JsonArray mods = new JsonArray();
        for (Module m : Zeus.all()) {
            if (!m.toggleable()) continue;
            JsonObject o = new JsonObject();
            o.addProperty("id", m.id);
            o.addProperty("name", m.name);
            o.addProperty("description", m.desc);
            o.addProperty("category", m.category.name());
            o.addProperty("rank", Usage.rank(m.id));
            o.addProperty("weight", m.isLight() ? "heavy" : "light");
            addCost(o, m);
            if (m.defaultOn()) o.addProperty("defaultOn", true);
            if (!m.aliases().isEmpty()) {
                JsonArray al = new JsonArray();
                for (String x : m.aliases()) al.add(x);
                o.add("aliases", al);
            }
            if (m.isHudWidget()) o.addProperty("hud", true);
            JsonArray sets = new JsonArray();
            for (Setting s : m.settings()) {
                JsonObject so = describe(s);
                if (so != null) sets.add(so);
            }
            if (sets.size() > 0) o.add("settings", sets);
            mods.add(o);
        }
        root.add("categories", cats);
        root.add("modules", mods);
        return GSON.toJson(root);
    }

    private static boolean declares(Module m, String name, Class<?>... args) {
        for (Class<?> c = m.getClass(); c != null && c != Module.class; c = c.getSuperclass()) {
            try {
                c.getDeclaredMethod(name, args);
                return true;
            } catch (NoSuchMethodException e) {
            }
        }
        return false;
    }

    private static void addCost(JsonObject o, Module m) {
        boolean tick = declares(m, "onTick", Minecraft.class);
        boolean hud = declares(m, "onHud", GuiGraphics.class, Minecraft.class, float.class, int.class, int.class);
        boolean world = declares(m, "onHudWorld", GuiGraphics.class, Minecraft.class, float.class, int.class, int.class);
        boolean scan = m.isLight();
        int score = (tick ? 1 : 0) + (hud ? 1 : 0) + (world ? 3 : 0) + (scan ? 3 : 0);
        int tier = score == 0 ? 1 : score <= 2 ? 2 : score <= 4 ? 3 : score <= 6 ? 4 : 5;
        o.addProperty("cost", tier);
        JsonArray runs = new JsonArray();
        if (tick) runs.add("tick");
        if (hud) runs.add("hud");
        if (world) runs.add("world");
        if (scan) runs.add("scan");
        o.add("runs", runs);
    }

    private static JsonObject describe(Setting s) {
        if (s instanceof Setting.Secret || s.name == null || s.name.isBlank()) return null;
        JsonObject o = new JsonObject();
        o.addProperty("name", s.name);
        if (s.desc != null && !s.desc.isBlank()) o.addProperty("desc", s.desc);
        if (s instanceof Setting.Bool b) {
            o.addProperty("type", "switch");
            o.addProperty("default", b.initial());
        } else if (s instanceof Setting.Num n) {
            o.addProperty("type", "slider");
            o.addProperty("min", n.min);
            o.addProperty("max", n.max);
            o.addProperty("step", n.step);
            if (n.unit != null && !n.unit.isEmpty()) o.addProperty("unit", n.unit.trim());
            o.addProperty("default", n.initial());
        } else if (s instanceof Setting.Choice c) {
            o.addProperty("type", "choice");
            JsonArray opts = new JsonArray();
            for (String x : c.options) opts.add(x);
            o.add("options", opts);
            o.addProperty("default", c.initialOption());
        } else if (s instanceof Setting.Pick p) {
            o.addProperty("type", "pick");
            JsonArray opts = new JsonArray();
            for (String x : p.labels) opts.add(x);
            o.add("options", opts);
        } else if (s instanceof Setting.Text) {
            o.addProperty("type", "text");
        } else if (s instanceof Setting.Action) {
            o.addProperty("type", "button");
        } else if (s instanceof Setting.Info) {
            o.addProperty("type", "info");
        } else {
            o.addProperty("type", "other");
        }
        return o;
    }
}
