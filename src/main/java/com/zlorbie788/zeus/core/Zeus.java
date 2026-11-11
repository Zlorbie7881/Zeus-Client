package com.zlorbie788.zeus.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zlorbie788.zeus.mods.Mods;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.util.EnumMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;

public final class Zeus {
    private Zeus() {}

    public static final Logger LOG = LoggerFactory.getLogger("Zeus");
    public static final String VERSION = "0.9.9.0";

    private static final int DEFAULTS_REV = 3;

    private static final Map<String, Module> MODULES = new LinkedHashMap<>();
    private static List<Module> ALL_MODULES = List.of();
    private static final Map<Category, List<Module>> CATEGORY_MODULES = new EnumMap<>(Category.class);
    private static boolean initialized;

    private static Module[] active = new Module[0];
    private static boolean activeDirty = true;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static boolean dirty;
    private static long dirtyAt;
    private static boolean wasInWorld;

    private static final Set<String> reconnectModules = new LinkedHashSet<>();

    private static final Set<String> launchRestoreModules = new LinkedHashSet<>();

    public static final Module.Holder UI = new Module.Holder();

    public static void init() {
        if (initialized) return;
        com.zlorbie788.zeus.ui.UiSettings.touch();
        for (Module m : Mods.create()) {
            Module previous = MODULES.putIfAbsent(m.id, m);
            if (previous != null) LOG.warn("Ignoring duplicate Zeus module id '{}' ({}).", m.id, m.getClass().getName());
        }
        ALL_MODULES = List.copyOf(MODULES.values());
        com.zlorbie788.zeus.mods.ServerSwitch.configure(ALL_MODULES);
        for (Category category : Category.values()) {
            List<Module> grouped = new ArrayList<>();
            for (Module module : ALL_MODULES) if (module.category == category) grouped.add(module);
            CATEGORY_MODULES.put(category, List.copyOf(grouped));
        }
        load();
        initialized = true;
    }

    static void activeChanged() {
        activeDirty = true;
        activeVersion++;
    }

    private static int activeVersion;

    public static int activeVersion() {
        return activeVersion;
    }

    private static Module[] running() {
        if (activeDirty) {
            activeDirty = false;
            List<Module> on = new ArrayList<>();
            for (Module m : ALL_MODULES) if (m.enabled()) on.add(m);
            active = on.toArray(new Module[0]);
        }
        return active;
    }

    public static List<Module> all() {
        return ALL_MODULES;
    }

    public static List<Module> in(Category c) {
        return CATEGORY_MODULES.getOrDefault(c, List.of());
    }

    public static Module get(String id) {
        return MODULES.get(id);
    }

    public static Module byName(String text) {
        if (text == null) return null;
        String key = squash(text);
        if (key.isEmpty()) return null;
        Module direct = MODULES.get(text.toLowerCase(java.util.Locale.ROOT).trim());
        if (direct != null) return direct;
        for (Module m : ALL_MODULES) {
            if (squash(m.id).equals(key) || squash(m.name).equals(key)) return m;
        }
        for (Module m : ALL_MODULES) {
            for (String a : m.aliases()) if (squash(a).equals(key)) return m;
        }
        return null;
    }

    private static String squash(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = Character.toLowerCase(s.charAt(i));
            if (c >= 'a' && c <= 'z' || c >= '0' && c <= '9') b.append(c);
        }
        return b.toString();
    }

    private static final Map<Class<?>, Module> BY_TYPE = new java.util.concurrent.ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public static <T extends Module> T get(Class<T> type) {
        Module known = BY_TYPE.get(type);
        if (known != null) return (T) known;
        for (Module m : ALL_MODULES) {
            if (type.isInstance(m)) {
                BY_TYPE.put(type, m);
                return (T) m;
            }
        }
        return null;
    }

    public static int enabledCount(Category c) {
        int n = 0;
        for (Module m : in(c)) if (m.enabled() && m.toggleable()) n++;
        return n;
    }

    private static boolean shouldReconnect(Module module) {
        return switch (com.zlorbie788.zeus.mods.ServerSwitch.mode()) {
            case 1 -> false;
            case 2 -> true;
            case 3 -> com.zlorbie788.zeus.mods.ServerSwitch.keep(module.id);
            default -> !module.startsOff();
        };
    }

    public static void tick(Minecraft mc) {
        if (destroyed) return;
        boolean in = Env.inWorld();
        if (wasInWorld && !in) {
            reconnectModules.clear();
            for (Module m : MODULES.values()) {
                if (m.enabled() && shouldReconnect(m)) reconnectModules.add(m.id);
                m.quiet();
            }
            Env.reset();
        }
        if (!wasInWorld && in) {
            for (Module m : MODULES.values()) m.cleanSlate();
            if (!launchRestoreModules.isEmpty()) {
                Set<String> restoreAtLaunch = new LinkedHashSet<>(launchRestoreModules);
                launchRestoreModules.clear();
                for (String id : restoreAtLaunch) {
                    Module module = MODULES.get(id);
                    if (module != null && module.toggleable() && !module.enabled()) module.setEnabled(true);
                }
            }
            if (!reconnectModules.isEmpty()) {
                Set<String> restore = new LinkedHashSet<>(reconnectModules);
                reconnectModules.clear();
                for (String id : restore) {
                    Module module = MODULES.get(id);
                    if (module != null && module.toggleable()) module.setEnabled(true);
                }
            }
            try {
                com.zlorbie788.zeus.mods.Profiles.onWorldEntered();
            } catch (RuntimeException e) {
                LOG.warn("Could not switch the profile for this server", e);
            }
            try {
                com.zlorbie788.zeus.mods.Exposure.onWorldEntered();
            } catch (RuntimeException e) {
                LOG.warn("Could not start the exposure meter", e);
            }
        }
        wasInWorld = in;
        if (!in) {
            flushIfDue();
            return;
        }
        Env.tick();
        com.zlorbie788.zeus.ui.Perf.refresh();
        Prof.sync(com.zlorbie788.zeus.ui.UiSettings.SHOW_COST.on());
        for (Module m : running()) {
            if (m.enabled()) m.tick(mc);
        }
        AntiKick.tick(mc, running());
        flushIfDue();
    }

    public static void hud(GuiGraphics g, Minecraft mc, float partial, int w, int h) {
        if (destroyed) return;
        if (mc.player != null && mc.options != null) {
            boolean f3down = mc.options.hideGui;
            if (f3down && !com.zlorbie788.zeus.ui.HudLayout.f3Toggled) {
                com.zlorbie788.zeus.ui.HudLayout.f3Toggled = true;
                for (Module m : running()) {
                    if (!m.enabled() || !m.isHudWidget()) continue;
                    com.zlorbie788.zeus.ui.HudLayout.Entry layout = com.zlorbie788.zeus.ui.HudLayout.get(m.id);
                    layout.visible = false;
                }
            } else if (!f3down) {
                com.zlorbie788.zeus.ui.HudLayout.f3Toggled = false;
                for (Module m : running()) {
                    if (!m.enabled() || !m.isHudWidget()) continue;
                    com.zlorbie788.zeus.ui.HudLayout.Entry layout = com.zlorbie788.zeus.ui.HudLayout.get(m.id);
                    if (!layout.visible) {
                        layout.visible = true;
                        Zeus.markDirty();
                    }
                }
            }
        }
        if (Prof.on) Prof.roll();
        if (mc != null && mc.screen instanceof com.zlorbie788.zeus.ui.ZeusScreen) {
            drawToasts(g, mc, w, h);
            return;
        }
        com.zlorbie788.zeus.mods.Hud.newFrame();
        Module[] on = running();
        com.zlorbie788.zeus.ui.Draw.cheap = com.zlorbie788.zeus.ui.Perf.level() >= 1;
        try {
            for (Module m : on) {
                if (!m.enabled() || !m.isHudWidget()) continue;
                com.zlorbie788.zeus.ui.HudLayout.Entry layout = com.zlorbie788.zeus.ui.HudLayout.get(m.id);
                if (!layout.visible) continue;
                g.pose().pushMatrix();
                com.zlorbie788.zeus.mods.Hud.beginWidget(m.id);
                float size = m.id.equals(com.zlorbie788.zeus.ui.HudLayout.actualSizeId)
                        ? 1f : com.zlorbie788.zeus.ui.HudLayout.scaleOf(layout);
                float[] pivot = size == 1f || com.zlorbie788.zeus.mods.Hud.bounds(m.id) == null
                        ? new float[] {0f, 0f}
                        : com.zlorbie788.zeus.ui.HudLayout.pivot(com.zlorbie788.zeus.mods.Hud.bounds(m.id), w, h);
                g.pose().translate(layout.x + pivot[0], layout.y + pivot[1]);
                g.pose().scale(size, size);
                g.pose().translate(-pivot[0], -pivot[1]);
                try { m.hud(g, mc, partial, w, h); }
                finally {
                    com.zlorbie788.zeus.mods.Hud.endWidget();
                    g.pose().popMatrix();
                }
            }
            for (Module m : on) {
                if (!m.enabled()) continue;
                if (!m.isHudWidget()) m.hud(g, mc, partial, w, h);
                m.hudWorld(g, mc, partial, w, h);
            }
        } finally {
            com.zlorbie788.zeus.ui.Draw.cheap = false;
            com.zlorbie788.zeus.ui.Tag.end();
        }
        drawToasts(g, mc, w, h);
    }

    private static boolean destroyed;

    public static boolean destroyed() {
        return destroyed;
    }

    public static void destroy() {
        for (Module m : MODULES.values()) m.quiet();
        destroyed = true;
        markDirty();
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof com.zlorbie788.zeus.ui.ZeusScreen) mc.setScreen(null);
        save();
    }

    public static void revive() {
        destroyed = false;
        markDirty();
        save();
    }

    public static void panic(boolean tell) {
        for (Module m : MODULES.values()) m.quiet();
        if (tell) toast("Everything is off", true);
    }

    private record Toast(String text, boolean good, long until) {}

    private static final List<Toast> TOASTS = new ArrayList<>();

    public record Event(long id, long at, String text, boolean good) {}

    private static final List<Event> EVENT_LOG = new ArrayList<>();
    private static long eventSeq;

    public static void event(String text, boolean good) {
        if (text == null || text.isEmpty()) return;
        synchronized (EVENT_LOG) {
            EVENT_LOG.add(new Event(++eventSeq, System.currentTimeMillis(), text, good));
            while (EVENT_LOG.size() > 200) EVENT_LOG.remove(0);
        }
    }

    public static List<Event> eventsAfter(long after) {
        synchronized (EVENT_LOG) {
            List<Event> out = new ArrayList<>();
            for (Event e : EVENT_LOG) if (e.id() > after) out.add(e);
            return out;
        }
    }

    public static void toast(String text, boolean good) {
        event(text, good);
        if (!com.zlorbie788.zeus.ui.UiSettings.TOASTS.on()) return;
        synchronized (TOASTS) {
            TOASTS.removeIf(t -> t.text.equals(text));
            TOASTS.add(new Toast(text, good, System.currentTimeMillis() + 2600));
            while (TOASTS.size() > 4) TOASTS.remove(0);
        }
    }

    private static void drawToasts(GuiGraphics g, Minecraft mc, int w, int h) {
        long now = System.currentTimeMillis();
        synchronized (TOASTS) {
            TOASTS.removeIf(t -> t.until < now);
            int y = 6;
            for (Toast t : TOASTS) {
                float a = Math.min(1f, (t.until - now) / 400f);
                com.zlorbie788.zeus.ui.Draw.toast(g, mc, w / 2, y, t.text, t.good, a);
                y += 17;
            }
        }
    }

    public static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("zeus.json");
    }

    public static void markDirty() {
        dirty = true;
        dirtyAt = System.currentTimeMillis();
    }

    private static void flushIfDue() {
        if (dirty && System.currentTimeMillis() - dirtyAt > 800) save();
    }

    public static void save() {
        Path target = file();
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(temporary, GSON.toJson(snapshot(false)), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            dirty = false;
            com.zlorbie788.zeus.mods.Profiles.onSaved(null);
        } catch (IOException | RuntimeException e) {
            dirty = true;
            dirtyAt = System.currentTimeMillis();
            LOG.warn("Could not save the Zeus settings; another save will be attempted", e);
            try { Files.deleteIfExists(temporary); } catch (IOException ignored) {}
        }
    }

    public static JsonObject snapshot(boolean share) {
        JsonObject root = new JsonObject();
        root.addProperty("schema", 2);
        root.addProperty("clientVersion", VERSION);
        root.addProperty("defaults", DEFAULTS_REV);
        JsonObject mods = new JsonObject();
        for (Module m : MODULES.values()) {
            JsonObject o = new JsonObject();
            boolean remember = !m.startsOff() || com.zlorbie788.zeus.ui.UiSettings.REMEMBER_MODULE_STATES.on();
            boolean effectiveEnabled = m.enabled() || reconnectModules.contains(m.id);
            if (m.toggleable() && remember && effectiveEnabled != m.defaultOn()) o.addProperty("enabled", effectiveEnabled);
            JsonObject s = new JsonObject();
            for (Setting st : m.settings()) if (!(share && st instanceof Setting.Secret)) st.persist(s);
            if (s.size() > 0) o.add("settings", s);
            if (o.size() > 0) mods.add(m.id, o);
        }
        root.add("modules", mods);
        JsonObject ui = new JsonObject();
        for (Setting st : UI.settings()) st.persist(ui);
        com.zlorbie788.zeus.ui.UiState.save(root);
        root.add("ui", ui);
        root.add("hudLayout", com.zlorbie788.zeus.ui.HudLayout.save());
        root.addProperty("destroyed", destroyed);
        if (!share) Usage.save(root);
        return root;
    }

    public static void apply(JsonObject root) {
        JsonObject mods = objectOrEmpty(root, "modules");
        for (Module m : ALL_MODULES) {
            JsonObject o = objectOrEmpty(mods, m.id);
            JsonObject settings = objectOrEmpty(o, "settings");
            for (Setting st : m.settings()) {
                if (st instanceof Setting.Secret && !settings.has(st.name)) continue;
                restoreSafely(st, settings, m.id);
            }
            if (!m.startsOff()) m.setEnabledQuiet(readBoolean(o, "enabled", m.defaultOn()));
        }
        JsonObject ui = objectOrEmpty(root, "ui");
        for (Setting st : UI.settings()) restoreSafely(st, ui, "ui");
        try {
            com.zlorbie788.zeus.ui.HudLayout.load(objectOrEmpty(root, "hudLayout"));
            com.zlorbie788.zeus.ui.UiState.load(root);
        } catch (RuntimeException e) {
            LOG.warn("Could not restore Zeus window positions; using defaults", e);
        }
        for (Module m : ALL_MODULES) if (m.enabled()) m.restart();
        markDirty();
    }

    private static JsonObject objectOrEmpty(JsonObject parent, String key) {
        if (parent == null || !parent.has(key) || !parent.get(key).isJsonObject()) return new JsonObject();
        return parent.getAsJsonObject(key);
    }

    private static boolean readBoolean(JsonObject object, String key, boolean fallback) {
        try {
            JsonElement value = object.get(key);
            return value != null && value.isJsonPrimitive() ? value.getAsBoolean() : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static void restoreSafely(Setting setting, JsonObject values, String owner) {
        try {
            setting.restore(values);
        } catch (RuntimeException e) {
            LOG.warn("Ignoring invalid saved value for setting '{}' in '{}'", setting.name, owner, e);
        }
    }

    private static void load() {
        for (Module m : ALL_MODULES) for (Setting st : m.settings()) restoreSafely(st, new JsonObject(), m.id);
        for (Setting st : UI.settings()) restoreSafely(st, new JsonObject(), "ui");

        JsonObject root = new JsonObject();
        try {
            if (Files.isRegularFile(file())) {
                JsonElement parsed = JsonParser.parseString(Files.readString(file(), StandardCharsets.UTF_8));
                if (parsed.isJsonObject()) root = parsed.getAsJsonObject();
                else LOG.warn("Zeus settings file is not a JSON object; defaults will be used");
            }
        } catch (IOException | RuntimeException e) {
            LOG.warn("Could not read the Zeus settings; defaults will be used", e);
        }

        int defaultsRev = root.has("defaults") && root.get("defaults").isJsonPrimitive() ? root.get("defaults").getAsInt() : 0;
        boolean newDefaults = defaultsRev < DEFAULTS_REV;

        JsonObject mods = objectOrEmpty(root, "modules");
        for (Module m : ALL_MODULES) {
            JsonObject module = objectOrEmpty(mods, m.id);
            JsonObject settings = objectOrEmpty(module, "settings");
            for (Setting st : m.settings()) restoreSafely(st, newDefaults && st.resetsOnRev() ? new JsonObject() : settings, m.id);
        }
        JsonObject ui = objectOrEmpty(root, "ui");
        for (Setting st : UI.settings()) restoreSafely(st, ui, "ui");
        try {
            com.zlorbie788.zeus.ui.HudLayout.load(objectOrEmpty(root, "hudLayout"));
            com.zlorbie788.zeus.ui.UiState.load(root);
        } catch (RuntimeException e) {
            LOG.warn("Could not restore Zeus window positions; using defaults", e);
        }
        destroyed = root.has("destroyed") && root.get("destroyed").isJsonPrimitive() && root.get("destroyed").getAsBoolean();
        Usage.load(root);

        launchRestoreModules.clear();
        for (Module m : ALL_MODULES) {
            if (!m.toggleable()) continue;
            boolean remember = !m.startsOff() || com.zlorbie788.zeus.ui.UiSettings.REMEMBER_MODULE_STATES.on();
            if (!remember) continue;
            JsonObject module = objectOrEmpty(mods, m.id);
            if (readBoolean(module, "enabled", m.defaultOn()) || (newDefaults && m.defaultOn())) launchRestoreModules.add(m.id);
        }
    }
}
