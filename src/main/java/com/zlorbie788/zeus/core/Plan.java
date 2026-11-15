package com.zlorbie788.zeus.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Works out how to get N of an item from what the player holds, the way a player would: follow the real recipes
 * down to raw materials, get the pickaxe each ore needs first, make a crafting table or furnace only when there
 * is none, then smelt and craft back up. The result is a list of job phrases the AI's job runner understands
 * (chop 2 trees, mine 3 iron, smelt 3 iron ingot, craft 1 iron pickaxe), plus anything it cannot gather alone.
 */
public final class Plan {
    private Plan() {}

    public record Result(List<String> steps, List<String> problems) {
        public boolean empty() {
            return steps.isEmpty();
        }
    }

    /** How a raw material is gathered: the job phrase, how many units one action yields, and the pickaxe tier it needs. */
    private record Source(String verb, String what, double per, int tier, double cost) {}

    private static final Map<String, Source> RAW = new HashMap<>();
    private static final int NONE = 0, WOOD = 1, STONE = 2, IRON = 3, DIAMOND = 4;

    static {
        raw("cobblestone", "mine", "stone", 1, WOOD, 1);
        raw("cobbled_deepslate", "mine", "deepslate", 1, WOOD, 1.5);
        raw("coal", "mine", "coal", 1, WOOD, 3);
        raw("raw_copper", "mine", "copper", 3, STONE, 3);
        raw("raw_iron", "mine", "iron", 1, STONE, 4);
        raw("lapis_lazuli", "mine", "lapis", 5, STONE, 3);
        raw("raw_gold", "mine", "gold", 1, IRON, 7);
        raw("redstone", "mine", "redstone", 4, IRON, 3);
        raw("diamond", "mine", "diamond", 1, IRON, 14);
        raw("emerald", "mine", "emerald", 1, IRON, 20);
        raw("dirt", "mine", "dirt", 1, NONE, 0.6);
        raw("sand", "mine", "sand", 1, NONE, 1);
        raw("red_sand", "mine", "red sand", 1, NONE, 1.5);
        raw("gravel", "mine", "gravel", 1, NONE, 1);
        raw("flint", "mine", "gravel", 0.2, NONE, 5);
        raw("clay_ball", "mine", "clay", 4, NONE, 1);
        raw("white_wool", "shear", "", 1.5, NONE, 3);
        raw("beef", "food", "", 1, NONE, 3);
        raw("porkchop", "food", "", 1, NONE, 3);
        raw("chicken", "food", "", 1, NONE, 3);
        raw("mutton", "food", "", 1, NONE, 3);
        raw("leather", "food", "", 0.5, NONE, 5);
        raw("feather", "food", "", 0.5, NONE, 5);
        raw("wheat", "farm", "", 4, NONE, 3);
    }

    private static void raw(String id, String verb, String what, double per, int tier, double cost) {
        RAW.put(id, new Source(verb, what, per, tier, cost));
    }

    private static boolean isLog(String id) {
        return id.endsWith("_log") || id.endsWith("_stem") || id.endsWith("_wood") && !id.contains("stripped") || id.endsWith("_hyphae");
    }

    // ------------------------------------------------------------------ cost estimate (for choosing recipes)
    private static final Map<String, Double> COST = new HashMap<>();
    private static final double INF = 1e9;

    static double cost(String id, Set<String> visiting) {
        Double c = COST.get(id);
        if (c != null) return c;
        if (isLog(id)) return 2;
        Source s = RAW.get(id);
        double best = s != null ? s.cost : INF;
        if (!visiting.add(id)) return INF;
        try {
            for (Recipes.Grid g : Recipes.grids(id)) {
                double sum = 0.3;
                for (Map.Entry<String, Integer> e : g.uses().entrySet()) sum += specCost(e.getKey(), visiting) * e.getValue();
                best = Math.min(best, sum / g.count());
            }
            for (Recipes.Cook k : Recipes.cooks(id)) best = Math.min(best, specCost(k.input(), visiting) + 1.5);
        } finally {
            visiting.remove(id);
        }
        if (visiting.isEmpty()) COST.put(id, best);
        return best;
    }

    private static double specCost(String spec, Set<String> visiting) {
        double best = INF;
        for (String m : Recipes.members(spec)) best = Math.min(best, cost(m, visiting));
        return best;
    }

    static double gridCost(Recipes.Grid g, Map<String, Integer> inv) {
        double sum = 0;
        for (Map.Entry<String, Integer> e : g.uses().entrySet()) {
            int have = 0;
            for (String m : Recipes.members(e.getKey())) have += inv.getOrDefault(m, 0);
            double each = specCost(e.getKey(), new HashSet<>());
            sum += Math.max(0, e.getValue() - have) * each + Math.min(have, e.getValue()) * 0.01;
        }
        return sum / g.count();
    }

    // ------------------------------------------------------------------ the planner
    private static final class State {
        final Map<String, Integer> inv;
        final Map<String, Double> raw = new LinkedHashMap<>();
        final Map<String, Integer> smelt = new LinkedHashMap<>();
        final Map<String, Integer> craft = new LinkedHashMap<>();
        final List<String> problems = new ArrayList<>();
        boolean needTable, needFurnace;
        int fuel;

        State(Map<String, Integer> inv) {
            this.inv = inv;
        }
    }

    /** The jobs that turn the inventory into N of the item. tableNear / furnaceNear say whether one stands within reach. */
    public static Result make(String item, int count, Map<String, Integer> inventory, boolean tableNear, boolean furnaceNear) {
        return make(item, count, new HashMap<>(inventory), tableNear, furnaceNear, 0);
    }

    private static Result make(String item, int count, Map<String, Integer> inv, boolean tableNear, boolean furnaceNear, int depth) {
        // a dry run on a copy finds which pickaxes and stations the plan needs; those are made first, from the real
        // inventory, and the item itself is then planned from what is left
        State st = new State(new HashMap<>(inv));
        need(st, item, count, 0);
        List<String> steps = new ArrayList<>();
        List<String> problems = new ArrayList<>(st.problems);
        boolean hasTable = tableNear || inv.getOrDefault("crafting_table", 0) > 0;
        boolean hasFurnace = furnaceNear || inv.getOrDefault("furnace", 0) > 0;
        if (st.fuel > 0) {
            int have = inv.getOrDefault("coal", 0) + inv.getOrDefault("charcoal", 0);
            int planks = 0, logs = 0;
            for (Map.Entry<String, Integer> e : inv.entrySet()) {
                if (e.getKey().endsWith("_planks")) planks += e.getValue();
                if (isLog(e.getKey())) logs += e.getValue();
            }
            int missing = st.fuel - have - (planks + logs) * 3 / 2 / 8;
            if (missing > 0) st.raw.merge("coal", (double) missing, Double::sum);
        }
        // the tools and stations everything else needs come first
        if (depth < 3) {
            int tier = NONE;
            for (String r : st.raw.keySet()) {
                Source s = RAW.get(r);
                if (s != null) tier = Math.max(tier, s.tier);
            }
            for (int t = WOOD; t <= tier; t++) {
                if (bestPickaxe(inv) >= t) continue;
                String pick = t == WOOD ? "wooden_pickaxe" : t == STONE ? "stone_pickaxe" : t == IRON ? "iron_pickaxe" : "diamond_pickaxe";
                Result sub = make(pick, 1, inv, tableNear || hasTable, furnaceNear || hasFurnace, depth + 1);
                steps.addAll(sub.steps);
                problems.addAll(sub.problems);
                inv.merge(pick, 1, Integer::sum);
                hasTable = true;            // making a pickaxe leaves a crafting table standing
                if (t >= IRON) hasFurnace = true;
            }
        }
        if (st.needTable && !hasTable && depth < 3) {
            Result sub = make("crafting_table", 1, inv, true, furnaceNear, depth + 1);
            steps.addAll(sub.steps);
            hasTable = true;
        }
        if (!st.smelt.isEmpty() && !hasFurnace && depth < 3) {
            Result sub = make("furnace", 1, inv, hasTable, true, depth + 1);
            steps.addAll(sub.steps);
        }
        boolean probeFuel = st.fuel > 0;
        st = new State(inv);
        need(st, item, count, 0);
        if (st.fuel > 0 || probeFuel) {
            int have = inv.getOrDefault("coal", 0) + inv.getOrDefault("charcoal", 0);
            int missing = st.fuel - have;
            if (missing > 0 && !st.raw.containsKey("coal")) st.raw.merge("coal", (double) missing, Double::sum);
        }
        // gather: wood first, then by the pickaxe each needs
        List<Map.Entry<String, Double>> raws = new ArrayList<>(st.raw.entrySet());
        raws.sort((a, b) -> Double.compare(tierOf(a.getKey()), tierOf(b.getKey())));
        Map<String, Integer> merged = new LinkedHashMap<>();
        for (Map.Entry<String, Double> e : raws) {
            String r = e.getKey();
            double n = e.getValue();
            if (isLog(r) || r.equals("logs")) {
                merged.merge("chop " + "%d" + " trees", (int) Math.ceil(n / 4.0), Integer::sum);
                continue;
            }
            Source s = RAW.get(r);
            if (s == null) continue;
            int actions = (int) Math.ceil(n / s.per);
            String phrase = switch (s.verb) {
                case "mine" -> "mine %d " + s.what;
                case "shear" -> "shear %d";
                case "food" -> "food %d";
                case "farm" -> "farm";
                default -> s.verb + " %d";
            };
            merged.merge(phrase, actions, Integer::sum);
        }
        for (Map.Entry<String, Integer> e : merged.entrySet()) {
            String phrase = e.getKey();
            steps.add(phrase.contains("%d") ? String.format(phrase, Math.max(1, e.getValue())) : phrase);
        }
        for (Map.Entry<String, Integer> e : st.smelt.entrySet()) steps.add("smelt " + e.getValue() + " " + e.getKey().replace('_', ' '));
        for (Map.Entry<String, Integer> e : st.craft.entrySet()) steps.add("craft " + e.getValue() + " " + e.getKey().replace('_', ' '));
        return new Result(steps, problems);
    }

    private static double tierOf(String raw) {
        if (isLog(raw) || raw.equals("logs")) return -1;
        Source s = RAW.get(raw);
        return s == null ? 9 : s.tier + s.cost / 100.0;
    }

    private static int bestPickaxe(Map<String, Integer> inv) {
        if (inv.getOrDefault("netherite_pickaxe", 0) + inv.getOrDefault("diamond_pickaxe", 0) > 0) return DIAMOND;
        if (inv.getOrDefault("iron_pickaxe", 0) > 0) return IRON;
        if (inv.getOrDefault("stone_pickaxe", 0) > 0) return STONE;
        if (inv.getOrDefault("wooden_pickaxe", 0) + inv.getOrDefault("golden_pickaxe", 0) > 0) return WOOD;
        return NONE;
    }

    /** Takes what the inventory already has, then plans the rest of n x item. */
    private static void need(State st, String item, int n, int depth) {
        int have = st.inv.getOrDefault(item, 0);
        int use = Math.min(have, n);
        if (use > 0) st.inv.put(item, have - use);
        n -= use;
        if (n <= 0) return;
        if (depth > 12) {
            st.problems.add(item.replace('_', ' '));
            return;
        }
        // cheapest way: raw, crafted or smelted
        Source src = RAW.get(item);
        double rawCost = isLog(item) ? 2 : src != null ? src.cost : INF;
        Recipes.Grid grid = null;
        double gridCost = INF;
        for (Recipes.Grid g : Recipes.grids(item)) {
            double c = gridCost(g, st.inv);
            if (grid == null || c < gridCost) {
                gridCost = c;
                grid = g;
            }
        }
        Recipes.Cook cook = null;
        double cookCost = INF;
        for (Recipes.Cook k : Recipes.cooks(item)) {
            double c = specCost(k.input(), new HashSet<>()) + 1.5;
            if (cook == null || c < cookCost) {
                cookCost = c;
                cook = k;
            }
        }
        if (rawCost <= gridCost && rawCost <= cookCost && rawCost < INF) {
            st.raw.merge(isLog(item) ? "logs" : item, (double) n, Double::sum);
            return;
        }
        if (grid != null && (gridCost <= cookCost || cook == null)) {
            int times = (n + grid.count() - 1) / grid.count();
            for (Map.Entry<String, Integer> e : grid.uses().entrySet()) needSpec(st, e.getKey(), e.getValue() * times, depth + 1);
            if (!grid.fitsInventory()) st.needTable = true;
            st.craft.merge(item, times * grid.count(), Integer::sum);
            int extra = times * grid.count() - n;
            if (extra > 0) st.inv.merge(item, extra, Integer::sum);
            return;
        }
        if (cook != null) {
            needSpec(st, cook.input(), n, depth + 1);
            st.smelt.merge(item, n, Integer::sum);
            st.fuel += (n + 7) / 8;
            return;
        }
        st.problems.add(item.replace('_', ' '));
    }

    /** A cell that takes any of several items: use what we hold first, then the cheapest to get. */
    private static void needSpec(State st, String spec, int n, int depth) {
        List<String> members = Recipes.members(spec);
        if (members.isEmpty()) {
            st.problems.add(spec.replace('#', ' ').trim().replace('_', ' '));
            return;
        }
        for (String m : members) {
            if (n <= 0) return;
            int have = st.inv.getOrDefault(m, 0);
            int use = Math.min(have, n);
            if (use > 0) {
                st.inv.put(m, have - use);
                n -= use;
            }
        }
        if (n <= 0) return;
        String best = members.get(0);
        double bestCost = INF;
        for (String m : members) {
            double c = cost(m, new HashSet<>());
            if (c < bestCost) {
                bestCost = c;
                best = m;
            }
        }
        need(st, best, n, depth);
    }

    /** A short readable summary of a plan for chat. */
    public static String describe(Result r) {
        if (r.steps.isEmpty()) return "nothing to do";
        return String.join(", then ", r.steps);
    }
}
