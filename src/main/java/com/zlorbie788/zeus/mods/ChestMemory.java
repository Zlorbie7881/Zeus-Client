package com.zlorbie788.zeus.mods;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Names;
import com.zlorbie788.zeus.core.Setting;
import com.zlorbie788.zeus.core.Zeus;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Remembers what was in every chest, barrel and shulker box you opened, per server and dimension, so you can ask "where did I
 * put the diamonds?" Type an item into the box and the nearest chests that held it are listed with the amount. Only chests you
 * opened yourself are remembered, and only what your own game was shown: nothing is read from the server that you did not see.
 */
public final class ChestMemory extends Module {
    private static final Gson GSON = new GsonBuilder().create();

    private static final class Rec {
        int x;
        int y;
        int z;
        long at;
        final Map<String, Integer> items = new TreeMap<>();
    }

    private final Map<String, Map<Long, Rec>> world = new HashMap<>();
    private boolean loaded;
    private boolean dirty;
    private long lastSave;
    private BlockPos open;
    private boolean wasOpen;
    private int ticks;

    private final Setting.Text find = add(new Setting.Text("Find item", "Part of an item's name, like diamond, shulker or elytra. The chests that held it are listed below.", "", 32));
    private final Setting.Num shown = add(new Setting.Num("Chests listed", "How many matching chests are listed.", 6, 2, 16, 1, ""));
    private final Setting.Bool sameDim = add(new Setting.Bool("Only this dimension", "Hides chests from the other dimensions.", true));
    private final Setting.Info results = add(new Setting.Info("Where it is", this::lines));

    public ChestMemory() {
        super("chestmemory", "Chest Memory", "Remembers what is in the chests you opened, and finds where an item is.", Category.UTILITY);
        add(new Setting.Action("Waypoint the nearest match", "Adds a waypoint at the nearest chest that holds the item typed above.", this::waypoint));
        add(new Setting.Action("Forget this world's chests", "Removes what is remembered for this server and dimension.", () -> {
            world.remove(key());
            dirty = true;
            Zeus.toast("Chests forgotten", true);
        }));
        add(new Setting.Info("Remembered", () -> {
            Map<Long, Rec> m = world.get(key());
            return List.of((m == null ? 0 : m.size()) + " chests in this world, " + total() + " in all");
        }));
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("zeus-chests.json");
    }

    private static String key() {
        Brain.Ctx c = Brain.ctx();
        return c.server + "|" + c.dim;
    }

    /**
     * Every chest remembered in the world you are in, as {x, y, z, seenAt, kindsOfItem}. Read-only, for the local
     * website: the map draws these as small markers.
     */
    public static java.util.List<double[]> spots() {
        ChestMemory m = com.zlorbie788.zeus.core.Zeus.get(ChestMemory.class);
        if (m == null) return java.util.List.of();
        m.load();
        java.util.List<double[]> out = new java.util.ArrayList<>();
        Map<Long, Rec> here = m.world.get(key());
        if (here == null) return out;
        for (Rec r : here.values()) out.add(new double[]{r.x, r.y, r.z, r.at, r.items.size()});
        return out;
    }

    /** Which world the spots belong to, so the map can say so. */
    public static String worldKey() {
        return key();
    }

    private int total() {
        int n = 0;
        for (Map<Long, Rec> m : world.values()) n += m.size();
        return n;
    }

    private void load() {
        if (loaded) return;
        loaded = true;
        try {
            if (!Files.isRegularFile(file())) return;
            JsonElement root = JsonParser.parseString(Files.readString(file(), StandardCharsets.UTF_8));
            if (!root.isJsonObject()) return;
            for (var e : root.getAsJsonObject().entrySet()) {
                Map<Long, Rec> m = new HashMap<>();
                for (JsonElement el : e.getValue().getAsJsonArray()) {
                    JsonObject o = el.getAsJsonObject();
                    Rec r = new Rec();
                    r.x = o.get("x").getAsInt();
                    r.y = o.get("y").getAsInt();
                    r.z = o.get("z").getAsInt();
                    r.at = o.get("t").getAsLong();
                    for (var it : o.getAsJsonObject("i").entrySet()) r.items.put(it.getKey(), it.getValue().getAsInt());
                    m.put(BlockPos.asLong(r.x, r.y, r.z), r);
                }
                world.put(e.getKey(), m);
            }
        } catch (IOException | RuntimeException ex) {
            Zeus.LOG.warn("Could not read the remembered chests", ex);
        }
    }

    private void save() {
        try {
            JsonObject root = new JsonObject();
            for (var e : world.entrySet()) {
                JsonArray a = new JsonArray();
                for (Rec r : e.getValue().values()) {
                    JsonObject o = new JsonObject();
                    o.addProperty("x", r.x);
                    o.addProperty("y", r.y);
                    o.addProperty("z", r.z);
                    o.addProperty("t", r.at);
                    o.add("i", GSON.toJsonTree(r.items));
                    a.add(o);
                }
                root.add(e.getKey(), a);
            }
            Files.writeString(file(), GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException ex) {
            Zeus.LOG.warn("Could not save the remembered chests", ex);
        }
    }

    @Override
    protected void onEnable() {
        load();
    }

    @Override
    protected void onDisable() {
        if (dirty) save();
        dirty = false;
    }

    private static boolean container(String block) {
        return block.equals("chest") || block.equals("trapped_chest") || block.equals("barrel") || block.endsWith("shulker_box");
    }

    @Override
    protected void onTick(Minecraft mc) {
        if (mc.player == null || mc.level == null) return;
        boolean isOpen = mc.screen instanceof AbstractContainerScreen<?>;
        if (isOpen && !wasOpen) {
            // the block you were looking at when the window opened is the chest
            open = null;
            HitResult h = mc.hitResult;
            if (h instanceof BlockHitResult b && container(Names.block(mc.level.getBlockState(b.getBlockPos()).getBlock()))) open = b.getBlockPos();
        }
        wasOpen = isOpen;
        if (isOpen && open != null && ++ticks % 5 == 0) {
            AbstractContainerMenu menu = ((AbstractContainerScreen<?>) mc.screen).getMenu();
            int n = menu.slots.size() - 36;
            if (n >= 9) record(menu, n);
        }
        if (dirty && System.currentTimeMillis() - lastSave > 10_000) {
            save();
            dirty = false;
            lastSave = System.currentTimeMillis();
        }
    }

    private void record(AbstractContainerMenu menu, int n) {
        Rec r = new Rec();
        r.x = open.getX();
        r.y = open.getY();
        r.z = open.getZ();
        r.at = System.currentTimeMillis();
        for (int i = 0; i < n; i++) {
            ItemStack s = menu.slots.get(i).getItem();
            if (!s.isEmpty()) r.items.merge(Names.item(s), s.getCount(), Integer::sum);
        }
        Map<Long, Rec> m = world.computeIfAbsent(key(), k -> new HashMap<>());
        Rec old = m.get(open.asLong());
        if (old == null || !old.items.equals(r.items)) {
            if (m.size() > 4000 && old == null) return;
            m.put(open.asLong(), r);
            dirty = true;
        }
    }

    private List<Rec> matches(Minecraft mc, String q) {
        List<Rec> out = new ArrayList<>();
        String needle = q.toLowerCase(Locale.ROOT).trim().replace(' ', '_');
        if (needle.isEmpty() || mc.player == null) return out;
        for (var e : world.entrySet()) {
            if (sameDim.on() && !e.getKey().equals(key())) continue;
            if (!e.getKey().startsWith(Brain.ctx().server + "|")) continue;
            for (Rec r : e.getValue().values()) {
                for (String it : r.items.keySet()) {
                    if (it.contains(needle)) {
                        out.add(r);
                        break;
                    }
                }
            }
        }
        out.sort((a, b) -> Double.compare(d2(mc, a), d2(mc, b)));
        return out;
    }

    private static double d2(Minecraft mc, Rec r) {
        double dx = r.x + 0.5 - mc.player.getX();
        double dy = r.y + 0.5 - mc.player.getY();
        double dz = r.z + 0.5 - mc.player.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private List<String> lines() {
        Minecraft mc = Minecraft.getInstance();
        load();
        if (mc.player == null) return List.of("Join a world first.");
        String q = find.get();
        if (q.isBlank()) return List.of("Type an item in the box above.");
        String needle = q.toLowerCase(Locale.ROOT).trim().replace(' ', '_');
        List<Rec> hits = matches(mc, q);
        if (hits.isEmpty()) return List.of("No remembered chest holds \"" + q + "\". Only chests you opened are known.");
        List<String> out = new ArrayList<>();
        int total = 0;
        for (Rec r : hits) for (var e : r.items.entrySet()) if (e.getKey().contains(needle)) total += e.getValue();
        out.add(total + " in " + hits.size() + " chest" + (hits.size() == 1 ? "" : "s"));
        for (int i = 0; i < Math.min(shown.i(), hits.size()); i++) {
            Rec r = hits.get(i);
            int amount = 0;
            String what = "";
            for (var e : r.items.entrySet()) {
                if (e.getKey().contains(needle)) {
                    amount += e.getValue();
                    if (what.isEmpty()) what = e.getKey();
                }
            }
            long mins = (System.currentTimeMillis() - r.at) / 60_000;
            out.add(String.format(Locale.ROOT, "%d x %s   %d, %d, %d   %dm   seen %s ago", amount, what, r.x, r.y, r.z, Math.round(Math.sqrt(d2(mc, r))),
                    mins < 60 ? mins + " min" : mins < 1440 ? mins / 60 + " h" : mins / 1440 + " d"));
        }
        return out;
    }

    private void waypoint() {
        Minecraft mc = Minecraft.getInstance();
        Extras.Waypoints wp = Zeus.get(Extras.Waypoints.class);
        List<Rec> hits = matches(mc, find.get());
        if (wp == null || hits.isEmpty()) {
            Zeus.toast(hits.isEmpty() ? "No chest holds that" : "Waypoints is not available", false);
            return;
        }
        Rec r = hits.get(0);
        boolean added = wp.addAt(find.get().trim() + " chest", r.x + 0.5, r.y, r.z + 0.5);
        Zeus.toast(added ? "Waypoint saved" : "There is already a waypoint there", added);
    }
}
