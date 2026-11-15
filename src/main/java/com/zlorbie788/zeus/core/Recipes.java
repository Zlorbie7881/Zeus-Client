package com.zlorbie788.zeus.core;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Every vanilla recipe, read from assets/zeus/ai/recipes.txt (generated from the game's own recipe files by
 * tools/ai/make_recipes.py). It works the same in single player and on servers, for recipes the player has
 * not unlocked too, so the AI can plan and craft anything the game can make.
 */
public final class Recipes {
    private Recipes() {}

    /** A grid recipe: cells are item ids, #tags or a|b alternatives, "" for a gap, row by row in a w x h box. */
    public record Grid(String result, int count, int w, int h, List<String> cells, boolean shaped) {
        /** How many of each ingredient spec one craft uses. */
        public Map<String, Integer> uses() {
            Map<String, Integer> m = new LinkedHashMap<>();
            for (String c : cells) if (!c.isEmpty()) m.merge(c, 1, Integer::sum);
            return m;
        }

        public boolean fitsInventory() {
            return w <= 2 && h <= 2 && cells.stream().filter(c -> !c.isEmpty()).count() <= 4;
        }
    }

    public record Cook(String result, int count, String input) {}

    private static final Map<String, List<Grid>> GRIDS = new HashMap<>();
    private static final Map<String, List<Cook>> COOKS = new HashMap<>();
    private static final Map<String, List<String>> TAGS = new HashMap<>();
    private static boolean loaded;

    private static synchronized void load() {
        if (loaded) return;
        loaded = true;
        try (InputStream in = Recipes.class.getResourceAsStream("/assets/zeus/ai/recipes.txt")) {
            if (in == null) return;
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] p = line.split("\\|", -1);
                try {
                    switch (p[0]) {
                        case "C" -> {
                            int w = Integer.parseInt(p[3]), h = Integer.parseInt(p[4]);
                            List<String> cells = List.of(String.join("|", java.util.Arrays.copyOfRange(p, 5, p.length)).split(";", -1));
                            GRIDS.computeIfAbsent(p[1], k -> new ArrayList<>()).add(new Grid(p[1], Integer.parseInt(p[2]), w, h, cells, true));
                        }
                        case "S" -> {
                            List<String> cells = List.of(String.join("|", java.util.Arrays.copyOfRange(p, 3, p.length)).split(";", -1));
                            GRIDS.computeIfAbsent(p[1], k -> new ArrayList<>()).add(new Grid(p[1], Integer.parseInt(p[2]), 3, 3, cells, false));
                        }
                        case "F" -> COOKS.computeIfAbsent(p[1], k -> new ArrayList<>())
                                .add(new Cook(p[1], Integer.parseInt(p[2]), String.join("|", java.util.Arrays.copyOfRange(p, 3, p.length))));
                        case "G" -> TAGS.put(p[1], List.of(p[2].split(",")));
                        default -> { }
                    }
                } catch (RuntimeException e) {
                    Zeus.LOG.debug("Zeus AI: skipped recipe line {}", line);
                }
            }
        } catch (Exception e) {
            Zeus.LOG.warn("Zeus AI: could not read the recipe table", e);
        }
    }

    public static List<Grid> grids(String result) {
        load();
        return GRIDS.getOrDefault(result, List.of());
    }

    public static List<Cook> cooks(String result) {
        load();
        return COOKS.getOrDefault(result, List.of());
    }

    public static boolean known(String id) {
        load();
        return GRIDS.containsKey(id) || COOKS.containsKey(id);
    }

    /** The items a cell may hold, in the order the game lists them. */
    public static List<String> members(String spec) {
        load();
        List<String> out = new ArrayList<>();
        for (String alt : spec.split("\\|")) {
            if (alt.isEmpty()) continue;
            if (alt.startsWith("#")) out.addAll(TAGS.getOrDefault(alt.substring(1), List.of()));
            else out.add(alt);
        }
        return out;
    }

    public static Predicate<ItemStack> predicate(String spec) {
        List<Predicate<ItemStack>> alts = new ArrayList<>();
        for (String alt : spec.split("\\|")) {
            if (alt.isEmpty()) continue;
            if (alt.startsWith("#")) {
                Identifier id = Identifier.tryParse(alt.substring(1));
                if (id == null) continue;
                TagKey<Item> tag = TagKey.create(Registries.ITEM, id);
                List<String> known = members(alt);
                alts.add(s -> s.is(tag) || known.contains(id(s)));
            } else {
                Item item = item(alt);
                if (item != null) alts.add(s -> s.is(item));
            }
        }
        return s -> {
            if (s.isEmpty()) return false;
            for (Predicate<ItemStack> p : alts) if (p.test(s)) return true;
            return false;
        };
    }

    public static Item item(String id) {
        Identifier key = Identifier.tryParse(id);
        if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) return null;
        return BuiltInRegistries.ITEM.getValue(key);
    }

    public static String id(ItemStack s) {
        return BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
    }

    /** The grid recipe as the 3x3 cells Craft lays out (shapeless ones packed into the 2x2 corner when they fit). */
    public static Craft.Recipe toCraft(Grid g) {
        List<Predicate<ItemStack>> cells = new ArrayList<>(9);
        for (int i = 0; i < 9; i++) cells.add(null);
        if (g.shaped()) {
            for (int r = 0; r < g.h(); r++)
                for (int c = 0; c < g.w(); c++) {
                    String spec = g.cells().get(r * g.w() + c);
                    if (!spec.isEmpty()) cells.set(r * 3 + c, predicate(spec));
                }
        } else {
            int[] order = {0, 1, 3, 4, 2, 5, 6, 7, 8};
            int k = 0;
            for (String spec : g.cells()) if (!spec.isEmpty() && k < 9) cells.set(order[k++], predicate(spec));
        }
        Item result = item(g.result());
        return new Craft.Recipe(g.result(), cells, result, g.count());
    }

    /** The recipe to craft an item with, preferring one the inventory can already pay for. */
    public static Craft.Recipe craftFor(String id, Map<String, Integer> inventory) {
        List<Grid> all = grids(id);
        if (all.isEmpty()) return null;
        Grid best = null;
        double bestCost = Double.MAX_VALUE;
        for (Grid g : all) {
            double cost = Plan.gridCost(g, inventory);
            if (cost < bestCost) {
                bestCost = cost;
                best = g;
            }
        }
        return toCraft(best == null ? all.get(0) : best);
    }

    /** What goes into a furnace to make this item, or null when it cannot be smelted. */
    public static Predicate<ItemStack> smeltInput(String result) {
        List<Cook> all = cooks(result);
        if (all.isEmpty()) return null;
        List<Predicate<ItemStack>> ps = new ArrayList<>();
        for (Cook c : all) ps.add(predicate(c.input()));
        return s -> {
            for (Predicate<ItemStack> p : ps) if (p.test(s)) return true;
            return false;
        };
    }
}
