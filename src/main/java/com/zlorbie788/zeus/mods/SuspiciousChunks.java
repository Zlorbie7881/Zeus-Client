package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Env;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Setting;
import com.zlorbie788.zeus.core.Zeus;
import com.zlorbie788.zeus.render.Projector;
import com.zlorbie788.zeus.ui.Accent;
import com.zlorbie788.zeus.render.ChunkLook;
import com.zlorbie788.zeus.ui.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Finds chunks that look built by someone. Every loaded chunk is scored by the blocks that almost never occur on their
 * own (storage, workstations, redstone, lights, metal blocks, glass, concrete and so on) plus item frames, armor stands and
 * minecarts with chests. Naturally generated structures (villages, mineshafts, strongholds, bastions and the like) are
 * recognised from their signature blocks, named, and left out of the red list unless there are clear signs of a base
 * inside them. Neighbouring flagged chunks are merged into one find with a centre, a size, a depth and a direction.
 */
public class SuspiciousChunks extends Module {
    private static final String[] GROUPS = {"storage", "workstations", "redstone", "lighting", "metal blocks", "building"};
    private static final String[] STRUCTS = {"Mineshaft", "Stronghold", "Bastion", "Nether fortress", "Ancient city", "End city", "Ocean monument", "Trial chambers", "Village", "Temple", "Mansion", "Dungeon"};
    /** How many marker points a chunk needs to count as that structure. */
    private static final int[] STRUCT_MIN = {18, 12, 24, 30, 5, 12, 24, 8, 16, 14, 70, 9};
    private static final String[] DIRS = {"E", "SE", "S", "SW", "W", "NW", "N", "NE"};

    private final Setting.Choice sensitivity;
    private final Setting.Choice scanPace;
    private final Setting.Num range;
    private final Setting.Bool scanMultiplayer = add(new Setting.Bool("Scan loaded multiplayer chunks", "Allow scanning only chunk data your client has already received and loaded. No hidden or unloaded chunks are queried; this can use extra CPU on busy servers.", false));
    private final Setting.Choice where;
    private final Setting.Choice natural;
    private final Setting.Bool entities;
    private final Setting.Bool growthEvidence;
    private final Setting.Bool clearance;
    private final Setting.Bool minedGeodeScan;
    private final Setting.Num minCluster;
    private final Setting.Bool radar;
    private final Setting.Choice corner;
    private final Setting.Num mapSize;
    private final Setting.Bool remember;
    private final Setting.Bool raid;
    private final Setting.Choice mapDetail;
    private final Setting.Num tile;
    private final Setting.Choice markerStyle;
    private final Setting.Num lineWidth;
    private final Setting.Choice lineColor;
    private final Setting.Bool tracer;
    private final Setting.Bool maybe;
    private final Setting.Bool world;
    private final Setting.Num markerRange;
    private final Setting.Bool alert;
    private final Setting.Bool activity;
    private final Setting.Num activityMin;
    private final Setting.Choice activityMemory;
    private final Setting.Bool activityRing;
    private final Setting.Bool useActivity;
    private final Setting.Info list;
    private final Setting.Info geodeList;
    private final Setting.Action rescan;

    /** {weight * 10, group, structure + 1 (0 = none), marker weight, tell (1/0)} per block. */
    private static final ConcurrentHashMap<Block, int[]> WEIGHT = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Block, String> GROWTH_KIND = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Block, Integer> GEODE_KIND = new ConcurrentHashMap<>();

    /** The score of one chunk. */
    private static final class Score {
        double total;
        double tell;
        double ents;
        double strong;
        double mid;
        double weak;
        int strongCount;
        final double[] groups = new double[GROUPS.length];
        final int[] markers = new int[STRUCTS.length];
        final int[] under = new int[STRUCTS.length];
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        double ySum;
        int yCount;
        long at;
        boolean flagged;
        int structure = -1;
        int structPts;
        final int[] roles = new int[BaseBrain.ROLES];
        long foot;
        int[] clues;
        final List<Long> keys = new ArrayList<>();
        double underground;
        int items;
        int grownPlants;
        int youngPlants;
        int growthKinds;
        /** Under-grown plants in this chunk (see Clearance): proof nobody has been here. */
        int owed;
        int owedKinds;
        /** How many of the 8 chunks around it are untouched too. */
        int clearRing;
        int tunnelRuns;
        int verticalShafts;
        BaseBrain.Verdict verdict;
    }

    /** Several flagged chunks that touch each other. */
    private static final class Find {
        double cx;
        double cz;
        int chunks;
        double total;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        double tell;
        int strongCount;
        final double[] groups = new double[GROUPS.length];
        final List<long[]> cells = new ArrayList<>();
        String structure;
        BaseBrain.Verdict verdict;
        Ledger.Entry entry;
        int fires;
        int items;
        int tunnelRuns;
        int verticalShafts;
    }

    private final Map<Long, Score> chunks = new HashMap<>();
    private final Map<Long, GeodeHint> geodeHints = new HashMap<>();
    private final Map<Long, Double> entScore = new HashMap<>();
    private final Map<Long, Integer> itemCount = new HashMap<>();
    /** Per chunk: ender pearls, name-tagged mobs, villagers, the biggest herd of one kind. */
    private final Map<Long, int[]> clueMap = new HashMap<>();
    private boolean hub;
    private long lastLearn;
    private long lastLedger;
    private final Set<Long> announced = new HashSet<>();
    private List<Find> finds = new ArrayList<>();
    private List<long[]> queue = new ArrayList<>();
    /** Next scan candidate. Avoids ArrayList.remove(0), which shifts the whole queue for every chunk. */
    private int queueIndex;
    private int tick;

    public SuspiciousChunks() {
        this("suspicious", "Suspicious Chunks", "Finds chunks that look built by someone: bases, farms, hidden rooms. Names natural structures.", 2, 1);
    }

    /** {@code level} is the starting sensitivity (0 low, 1 medium, 2 high) and {@code smallest} the smallest find in chunks. */
    protected SuspiciousChunks(String id, String name, String desc, int level, int smallest) {
        this(id, name, desc, level, smallest, 0);
    }

    /** Variant constructor lets focused scanners choose a default vertical region. */
    protected SuspiciousChunks(String id, String name, String desc, int level, int smallest, int defaultWhere) {
        super(id, name, desc, Category.SCANNER);
        light();
        hudCard();
        this.sensitivity = add(new Setting.Choice("How sure to be", "How much has to be in a chunk before it is flagged. Medium finds a small hut with a bed, a chest and a furnace. High finds even a bed and a chest. Very high flags anything a player could have placed.", level, "Low", "Medium", "High", "Very high"));
        this.scanPace = add(new Setting.Choice("Scan pace", "Fast uses less frame time; Thorough spends more time processing the queue each tick. This changes scan throughput, not the scoring rules.", 0, "Fast", "Balanced", "Thorough"));
        this.range = add(new Setting.Num("Scan distance", "How many chunks around you are checked. Only chunks your game has loaded can be scanned, so more than your render distance does nothing.", 14, 3, 20, 1, " chunks"));
        this.where = add(new Setting.Choice("Where", "Look everywhere, only underground (hidden bases) or only above the surface.", defaultWhere, "Everywhere", "Underground only", "Above ground only"));
        this.natural = add(new Setting.Choice("Natural structures", "Villages, mineshafts, bastions and so on are named. Hide them from the list, show them in blue, or treat them like any other chunk.", 1, "Hide", "Show in blue", "Flag like the rest"));
        this.entities = add(new Setting.Bool("Count entities", "Item frames, armor stands, paintings and minecarts with chests add to the score.", true));
        this.growthEvidence = add(new Setting.Bool("Farm growth evidence", "Uses mature/immature crop patterns as a small supporting clue. Crop fields never flag a chunk by themselves.", true));
        this.clearance = add(new Setting.Bool("No-growth proof", "Crops that are ripe, kelp that reaches the top, tall cane, long vines: plants grow whether or not anybody watches. A chunk loaded for hours whose plants are still short was never ticked with a player in it, so a base cannot be hidden by building it to look natural. Chunks surrounded by untouched ones are flagged as a cleared area. This is evidence of the opposite kind from everything else here, and it is the one anti-xray servers cannot fake, because they would have to grow the plants for real.", true));
        this.minedGeodeScan = add(new Setting.Bool("Detect mined geodes", "Finds loaded geodes with many exposed budding blocks but very few remaining crystals. This is a separate exploration clue, not a base score.", true));
        this.minCluster = add(new Setting.Num("Smallest find size", "Ignore finds made of fewer chunks than this. 1 keeps one-chunk huts.", smallest, 1, 6, 1, " chunks"));
        this.radar = add(new Setting.Bool("Map", "A map of the chunks around you. Flagged chunks are coloured by how sure the find is: red is sure, amber is likely, green is weak.", true));
        this.corner = Hud.corner(this, 3).showIf(() -> this.radar.on());
        this.mapDetail = add(new Setting.Choice("Map detail", "Full: title, coordinates, nearest find and legend. Compact: just the grid and one line. Tiny: only the grid.", 0, "Full", "Compact", "Tiny")).showIf(() -> this.radar.on());
        this.mapSize = add(new Setting.Num("Map size", "How many chunks it shows each way from you.", 7, 2, 10, 1, " chunks")).showIf(() -> this.radar.on());
        this.tile = add(new Setting.Num("Tile size", "The size of one chunk on the map.", 11, 4, 18, 1, " px")).showIf(() -> this.radar.on());
        this.maybe = add(new Setting.Bool("Show near misses", "Chunks that almost made it (20 % or more, but under the cut-off) are tinted amber on the map and listed on the card, so you can see what Zeus is unsure about.", true));
        this.tracer = add(new Setting.Bool("Line to the nearest find", "A line on the screen that points at the nearest find.", true));
        this.world = add(new Setting.Bool("Markers in the world", "Marks flagged chunks the way Anubis does: a flat translucent tile on the Y=63 plane, an edge round the outside of each group, and a tracer to the middle of each area. Likely mined geodes are purple.", true));
        this.markerRange = add(new Setting.Num("Marker range", "Flagged chunks closer than this get a tile.", 6, 1, 16, 1, " chunks")).showIf(() -> this.world.on());
        this.alert = add(new Setting.Bool("Alert", "A message when a new find shows up.", true));
        this.activity = add(new Setting.Bool("Watch for activity", "Counts the blocks that change far from you (somebody digging, building, filling chests) and points at chunks where it is happening right now, even when no base shows there yet. Your own work and water, lava, leaves and crops are not counted.", true));
        this.activityMin = add(new Setting.Num("Activity needs", "How many weighted block changes make a chunk count as active. Lower finds more, but also single stray changes.", 6, 2, 40, 1, "")).showIf(() -> this.activity.on());
        this.activityMemory = add(new Setting.Choice("Activity memory", "How quickly old block-change evidence fades. Short is responsive; long is better for slow building or mining.", 1, "15 seconds", "30 seconds", "60 seconds", "120 seconds")).showIf(() -> this.activity.on());
        this.useActivity = add(new Setting.Bool("Activity counts as evidence", "A chunk that already shows signs of a player (beds, storage, player-made blocks) is judged more likely a base while others are changing blocks in it.", true)).showIf(() -> this.activity.on());
        this.activityRing = add(new Setting.Bool("Pulse active chunks on the map", "A pulsing ring on the map around chunks where someone is working.", true)).showIf(() -> this.activity.on());
        this.markerStyle = add(new Setting.Choice("Outline", "Top edge: only the top of the find is outlined, so the whole chunk is not boxed in. Top edge + posts adds short corner posts. Full frame is the old curtain.", 0, "Top edge", "Top edge + posts", "Full frame")).showIf(() -> false);
        this.lineWidth = add(new Setting.Num("Outline width", "Thickness of the outline.", 1.6, 0.8, 4.0, 0.2, "")).showIf(() -> false);
        this.lineColor = add(new Setting.Choice("Tile colour", "Automatic: the Anubis red (purple for a mined geode). Or pick one.", 0, com.zlorbie788.zeus.render.EspKit.COLOR_NAMES)).showIf(() -> this.world.on());
        this.remember = add(new Setting.Bool("Remember bases", "Keeps a list of the bases found on each server (config/zeus-bases.json) so it can tell later whether one was raided.", true));
        this.raid = add(new Setting.Bool("Raid check", "Compares a base with what it looked like last time, and looks for fire and scattered items, to tell whether it was raided.", true));
        add(new Setting.Action("Not a base (nearest find)", "Tell Zeus the nearest find is not a base. It is never flagged again and Zeus gets a little stricter on this server.", () -> answer(false)));
        add(new Setting.Action("This is a base (nearest find)", "Tell Zeus the nearest find is a base. It stays flagged and Zeus gets a little more willing on this server.", () -> answer(true)));
        add(new Setting.Action("Save finds as waypoints", "Adds a waypoint for every flagged find (the nearest ten) that has none yet, named after what it is. They show through walls with the Waypoints module.", this::waypointFinds));
        add(new Setting.Action("Copy report", "Copies what Zeus sees in the best chunks (counts, structure, confidence) so you can paste it into a message when a base is not found.", this::copyReport));
        add(new Setting.Info("What you taught it", this::taught));
        add(new Setting.Action("Forget what I taught it", "Removes every answer you gave, on every server.", () -> {
            Feedback.forgetAll();
            Zeus.toast("Forgot your answers", true);
        }));
        add(new Setting.Action("Forget saved bases", "Empties the list of remembered bases.", Ledger::forgetAll));
        this.list = add(new Setting.Info("Finds", this::describe));
        this.geodeList = add(new Setting.Info("Mined geode clues", this::geodeLines));
        add(new Setting.Info("Activity right now", this::activityLines));
        add(new Setting.Info("The chunk you are in", this::here));
        this.rescan = add(new Setting.Action("Scan again", "Forgets everything and scans the area from scratch.", this::reset));
    }

    private List<String> taught() {
        Brain.Ctx c = Brain.ctx();
        int no = Feedback.count(c.server, c.dim, false);
        int yes = Feedback.count(c.server, c.dim, true);
        double b = Feedback.bias(c.server);
        if (no + yes == 0 && b == 0) return List.of("Nothing yet. Stand near a find and use the two buttons above.");
        return List.of(no + " chunks marked not a base, " + yes + " marked a base here.",
                String.format(Locale.ROOT, "Cut-off on this server: %+.1f points.", b));
    }

    /** The user says whether the nearest find is a base. */
    private void answer(boolean isBase) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || finds.isEmpty()) {
            Zeus.toast("No find to answer about", false);
            return;
        }
        Find best = null;
        double bd = Double.MAX_VALUE;
        for (Find f : finds) {
            double d = Math.hypot(f.cx - mc.player.getX(), f.cz - mc.player.getZ());
            if (d < bd) {
                bd = d;
                best = f;
            }
        }
        Brain.Ctx c = Brain.ctx();
        long[][] cells = best.cells.toArray(new long[0][]);
        if (isBase) Feedback.confirm(c.server, c.dim, cells);
        else Feedback.dismiss(c.server, c.dim, cells);
        Zeus.toast((isBase ? "Remembered as a base" : "Never flagged again") + ": " + Math.round(best.cx) + ", " + Math.round(best.cz), true);
        rebuild(mc);
    }

    private final Set<Long> activeAnnounced = new HashSet<>();

    /** Separate exploration clue: likely harvested geodes are never counted as player bases. */
    private record GeodeHint(int shell, int budding, int crystals, int exposedBudding) {}

    private void waypointFinds() {
        Minecraft mc = Minecraft.getInstance();
        Extras.Waypoints wp = Zeus.get(Extras.Waypoints.class);
        if (mc.player == null || wp == null || finds.isEmpty()) {
            Zeus.toast(wp == null ? "Waypoints is not available" : "No finds to save yet", false);
            return;
        }
        List<Find> sorted = new ArrayList<>(finds);
        sorted.sort((a, b) -> Double.compare(dist(a, mc), dist(b, mc)));
        int added = 0;
        for (int i = 0; i < Math.min(10, sorted.size()); i++) {
            Find f = sorted.get(i);
            double y = f.minY == Integer.MAX_VALUE ? mc.player.getY() : (f.minY + f.maxY) / 2.0;
            if (wp.addAt(f.verdict.type + " " + f.verdict.confidence + "%", f.cx, y, f.cz)) added++;
        }
        Zeus.toast(added == 0 ? "Every find already has a waypoint" : "Saved " + added + " waypoint" + (added == 1 ? "" : "s"), true);
    }

    private List<String> activityLines() {
        List<String> out = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        if (!enabled() || !activity.on() || mc.player == null) return List.of(activity.on() ? "Switch the module on." : "Switched off in the settings.");
        List<long[]> hot = Activity.hot(activityMin.i());
        for (int i = 0; i < Math.min(4, hot.size()); i++) {
            long[] h = hot.get(i);
            double x = h[0] * 16 + 8;
            double z = h[1] * 16 + 8;
            out.add(String.format(Locale.ROOT, "Active   %d, %d   %dm %s   (%.1f changes)", Math.round(x), Math.round(z),
                    Math.round(Math.hypot(x - mc.player.getX(), z - mc.player.getZ())), direction(x - mc.player.getX(), z - mc.player.getZ()), h[2] / 10.0));
        }
        if (out.isEmpty()) out.add("Quiet. Nobody is changing blocks within reach of your game.");
        return out;
    }

    private void watchActivity(Minecraft mc) {
        if (!activity.on()) return;
        List<long[]> hot = Activity.hot(activityMin.i());
        Set<Long> now = new HashSet<>();
        for (long[] h : hot) {
            long k = key((int) h[0], (int) h[1]);
            now.add(k);
            if (activeAnnounced.add(k) && alert.on()) {
                double x = h[0] * 16 + 8;
                double z = h[1] * 16 + 8;
                Zeus.toast("Activity at " + Math.round(x) + ", " + Math.round(z) + " (" + Math.round(Math.hypot(x - mc.player.getX(), z - mc.player.getZ())) + "m): someone is changing blocks there", true);
            }
        }
        activeAnnounced.retainAll(now);
    }

    private void reset() {
        activeAnnounced.clear();
        chunks.clear();
        geodeHints.clear();
        entScore.clear();
        announced.clear();
        finds = new ArrayList<>();
        queue.clear();
        queueIndex = 0;
    }

    private int queueRemaining() {
        return Math.max(0, queue.size() - queueIndex);
    }

    @Override
    protected void onEnable() {
        Activity.setCounting(activity.on());
        Activity.setMemorySeconds(switch (activityMemory.index()) { case 0 -> 15; case 2 -> 60; case 3 -> 120; default -> 30; });
        reset();
    }

    @Override
    protected void onDisable() {
        Activity.setCounting(false);
        reset();
    }

    /** How many blocks that no natural structure places (chests, hoppers, concrete...) a flagged chunk must hold. */
    private int minStrong() {
        return switch (sensitivity.index()) {
            case 0 -> 6;
            case 1 -> 3;
            default -> 2;
        };
    }

    /** A chunk that is not flagged but showed some evidence of a player. */
    private boolean isMaybe(Score s) {
        return maybe.on() && !s.flagged && s.verdict != null && s.verdict.confidence >= 20 && (s.structure < 0 || natural.index() == 2);
    }

    private List<String> nearMisses(Minecraft mc) {
        List<String> out = new ArrayList<>();
        if (!maybe.on() || mc.player == null) return out;
        List<Map.Entry<Long, Score>> list = new ArrayList<>();
        for (Map.Entry<Long, Score> e : chunks.entrySet()) if (isMaybe(e.getValue())) list.add(e);
        list.sort((a, b) -> Integer.compare(b.getValue().verdict.confidence, a.getValue().verdict.confidence));
        for (int i = 0; i < Math.min(3, list.size()); i++) {
            Score s = list.get(i).getValue();
            double x = kx(list.get(i).getKey()) * 16 + 8;
            double z = kz(list.get(i).getKey()) * 16 + 8;
            out.add(String.format(Locale.ROOT, "Maybe %d %%   %d, %d   %dm %s", s.verdict.confidence, Math.round(x), Math.round(z),
                    Math.round(Math.hypot(x - mc.player.getX(), z - mc.player.getZ())), direction(x - mc.player.getX(), z - mc.player.getZ())));
            out.add("    " + s.verdict.why());
        }
        return out;
    }

    /** Why the chunk under you is or is not flagged. The first thing to read when a base is not found. */
    private List<String> here() {
        List<String> out = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        if (!enabled() || mc.player == null) {
            out.add("Switch the module on and stand in a world.");
            return out;
        }
        int cx = mc.player.getBlockX() >> 4;
        int cz = mc.player.getBlockZ() >> 4;
        Score s = chunks.get(key(cx, cz));
        if (s == null) {
            out.add("This chunk has not been scanned yet (" + queueRemaining() + " waiting, " + chunks.size() + " done).");
            return out;
        }
        double learned = Brain.learning() ? Brain.baseline().crowding : 0.08;
        int cut = (int) Math.round(BaseBrain.cutoff(sensitivity.index(), learned) + Feedback.bias(Brain.ctx().server));
        BaseBrain.Verdict v = s.verdict;
        if (v == null) {
            out.add("Judging...");
            return out;
        }
        out.add(v.confidence + " % " + v.type.toLowerCase(Locale.ROOT) + ", needs " + cut + " %" + (s.flagged ? "  ·  FLAGGED" : "  ·  not flagged"));
        out.add("Found here: " + s.roles[BaseBrain.R_BED] + " beds, " + s.roles[BaseBrain.R_CONTAINER] + " storage, " + s.roles[BaseBrain.R_WORK] + " workstations, "
                + s.roles[BaseBrain.R_HOPPER] + " hoppers, " + s.roles[BaseBrain.R_LIGHT] + " lights, " + Long.bitCount(s.foot) + " kinds of player-made blocks"
                + (s.clues == null ? "" : ", " + s.clues[0] + " pearls, " + s.clues[1] + " named mobs, " + s.clues[2] + " villagers, herd of " + s.clues[3]));
        out.add(s.structure >= 0 ? "Looks like a natural structure: " + STRUCTS[s.structure] + ". Beds and chests count for less here." : "No natural structure here, so a bed, a chest or a furnace counts.");
        if (!v.reasons.isEmpty()) out.add("Because: " + v.why());
        if (!s.flagged && v.confidence < cut) out.add("Raise \"How sure to be\" to " + (sensitivity.index() >= 3 ? "its maximum (it is)" : "High or Very high") + " to flag it.");
        return out;
    }

    private double threshold() {
        return switch (sensitivity.index()) {
            case 0 -> 140;
            case 1 -> 60;
            default -> 30;
        };
    }

    // ------------------------------------------------------------------ the weights

    private static int[] weigh(Block b) {
        int[] w = WEIGHT.get(b);
        if (w == null) {
            String path;
            try {
                String id = BuiltInRegistries.BLOCK.getKey(b).toString();
                path = id.substring(id.indexOf(':') + 1);
            } catch (RuntimeException e) {
                path = "";
            }
            int[] base = weighPath(path);
            int[] m = markerOf(path);
            w = new int[] {base[0], base[1], m[0], m[1], isTell(path) ? 1 : 0, strength(path), roleOf(path), footOf(path)};
            WEIGHT.put(b, w);
        }
        return w;
    }

    /**
     * How much a block says "a player was here": 2 for blocks that no natural structure places (or places only in a way the
     * structure check handles), 1 for ordinary workstations and lights, 0 for everything that villages, ruins, mineshafts
     * and the Nether also have. Blocks of strength 0 and 1 can only add a little to a chunk that already has strong ones.
     */
    static int strength(String p) {
        if (p.isEmpty()) return 0;
        if (isTell(p)) return 2;
        if (p.equals("chest") || p.equals("trapped_chest") || p.equals("barrel") || p.endsWith("shulker_box")) return 2;
        switch (p) {
            case "dispenser", "dropper", "piston", "redstone_torch", "redstone_wall_torch", "lever", "target", "powered_rail", "detector_rail",
                 "activator_rail", "end_rod", "sea_lantern", "glass", "tinted_glass", "bookshelf", "chiseled_bookshelf", "copper_block", "raw_gold_block",
                 "raw_iron_block", "coal_block", "lectern", "smithing_table", "cartography_table", "fletching_table", "stonecutter", "grindstone", "loom":
                return 2;
            case "furnace", "blast_furnace", "smoker", "crafting_table", "anvil", "chipped_anvil", "damaged_anvil", "cauldron", "lantern", "soul_lantern",
                 "campfire", "soul_campfire", "torch", "wall_torch", "soul_torch", "soul_wall_torch", "tnt", "redstone_wire", "jack_o_lantern":
                return 1;
            default:
                break;
        }
        if (p.endsWith("_wool") || p.endsWith("_bed")) return 1;
        return 0;
    }

    /** What a block means to the judge: a bed, a container, a light and so on (see {@link BaseBrain}). */
    static int roleOf(String p) {
        if (p.isEmpty()) return 0;
        if (p.endsWith("_bed")) return BaseBrain.R_BED;
        if (p.equals("hopper")) return BaseBrain.R_HOPPER;
        if (p.contains("chest") || p.equals("barrel") || p.endsWith("shulker_box")) return BaseBrain.R_CONTAINER;
        switch (p) {
            case "torch", "wall_torch", "soul_torch", "soul_wall_torch", "lantern", "soul_lantern", "campfire", "soul_campfire", "sea_lantern", "glowstone", "redstone_lamp", "end_rod":
                return BaseBrain.R_LIGHT;
            case "diamond_block", "emerald_block", "gold_block", "netherite_block", "beacon":
                return BaseBrain.R_VALUABLE;
            case "furnace", "blast_furnace", "smoker", "crafting_table", "anvil", "chipped_anvil", "damaged_anvil", "enchanting_table", "brewing_stand", "smithing_table", "grindstone", "loom", "stonecutter", "cartography_table", "fletching_table", "lectern":
                return BaseBrain.R_WORK;
            case "fire", "soul_fire":
                return BaseBrain.R_FIRE;
            case "nether_portal":
                return BaseBrain.R_PORTAL;
            default:
                return 0;
        }
    }

    private static final ConcurrentHashMap<String, Integer> FOOT = new ConcurrentHashMap<>();

    /** The kind of a player-made block ("door", "planks", "glass"...), 1 to 62, or 0 for blocks nature makes. Every colour of a thing is one kind. */
    static int footOf(String p) {
        if (p.isEmpty()) return 0;
        String k;
        if (p.endsWith("_bed")) k = "bed";
        else if (p.equals("iron_door") || p.equals("iron_trapdoor")) k = "iron door";
        else if (p.endsWith("_trapdoor")) k = "trapdoor";
        else if (p.endsWith("_door")) k = "door";
        else if (p.endsWith("_planks")) k = "planks";
        else if (p.endsWith("_stairs")) k = "stairs";
        else if (p.endsWith("_slab")) k = "slab";
        else if (p.endsWith("_fence_gate")) k = "gate";
        else if (p.endsWith("_fence")) k = "fence";
        else if (p.endsWith("_wall") && !p.endsWith("wall_torch")) k = "wall";
        else if (p.endsWith("_sign") || p.endsWith("_hanging_sign")) k = "sign";
        else if (p.endsWith("glass") || p.endsWith("glass_pane")) k = "glass";
        else if (p.endsWith("_wool")) k = "wool";
        else if (p.endsWith("_carpet") && !p.startsWith("moss")) k = "carpet";
        else if (p.endsWith("_concrete")) k = "concrete";
        else if (p.endsWith("glazed_terracotta")) k = "glazed terracotta";
        else if (p.endsWith("shulker_box")) k = "shulker";
        else if (p.endsWith("_button")) k = "button";
        else if (p.endsWith("_pressure_plate")) k = "plate";
        else if (p.startsWith("stripped_")) k = "stripped log";
        else if (p.endsWith("bricks") && !p.contains("nether") && !p.contains("mud") && !p.contains("end_stone") && !p.contains("deepslate") && !p.contains("blackstone") && !p.contains("tuff") && !p.contains("cracked") && !p.contains("mossy") && !p.contains("infested")) k = "bricks";
        else switch (p) {
            case "chest", "trapped_chest" -> k = "chest";
            case "barrel" -> k = "barrel";
            case "ender_chest" -> k = "ender chest";
            case "furnace", "blast_furnace", "smoker" -> k = "furnace";
            case "crafting_table" -> k = "crafting table";
            case "anvil", "chipped_anvil", "damaged_anvil" -> k = "anvil";
            case "enchanting_table", "brewing_stand", "smithing_table", "grindstone", "stonecutter", "loom", "cartography_table", "fletching_table", "lectern" -> k = "workstation";
            case "bookshelf", "chiseled_bookshelf" -> k = "bookshelf";
            case "ladder" -> k = "ladder";
            case "scaffolding" -> k = "scaffolding";
            case "cobblestone" -> k = "cobblestone";
            case "torch", "wall_torch", "soul_torch", "soul_wall_torch", "lantern", "soul_lantern", "campfire", "soul_campfire", "sea_lantern", "glowstone", "redstone_lamp", "end_rod" -> k = "light";
            case "hopper", "dispenser", "dropper", "piston", "sticky_piston", "observer", "repeater", "comparator", "redstone_torch", "redstone_wall_torch", "lever", "redstone_block", "target", "daylight_detector", "note_block", "jukebox" -> k = "redstone";
            case "rail", "powered_rail", "detector_rail", "activator_rail" -> k = "rail";
            case "tnt" -> k = "tnt";
            case "iron_block", "gold_block", "diamond_block", "emerald_block", "netherite_block", "lapis_block", "copper_block", "beacon" -> k = "metal block";
            case "item_frame", "painting", "flower_pot", "cauldron", "composter", "smooth_stone", "smooth_quartz", "quartz_block", "quartz_pillar", "polished_andesite", "polished_granite", "polished_diorite", "terracotta", "nether_portal" -> k = p;
            default -> { return 0; }
        }
        return FOOT.computeIfAbsent(k, x -> Math.min(62, FOOT.size() + 1));
    }

    /** {structure + 1, points} for blocks that give a natural structure away. */
    static int[] markerOf(String p) {
        switch (p) {
            case "rail", "cobweb": return new int[] {1, 1};
            case "end_portal_frame": return new int[] {2, 12};
            case "iron_bars", "cracked_stone_bricks", "mossy_stone_bricks", "infested_stone_bricks", "infested_cracked_stone_bricks": return new int[] {2, 1};
            case "polished_blackstone_bricks", "gilded_blackstone", "cracked_polished_blackstone_bricks", "chiseled_polished_blackstone", "polished_blackstone_brick_stairs", "polished_blackstone_brick_slab", "polished_blackstone_brick_wall": return new int[] {3, 1};
            case "nether_brick_fence", "nether_bricks", "red_nether_bricks", "nether_brick_stairs", "nether_wart": return new int[] {4, 1};
            case "reinforced_deepslate", "sculk_shrieker", "sculk_sensor", "sculk_catalyst", "sculk": return new int[] {5, 2};
            case "purpur_block", "purpur_pillar", "purpur_stairs", "purpur_slab", "end_stone_bricks": return new int[] {6, 1};
            case "prismarine", "prismarine_bricks", "dark_prismarine", "wet_sponge": return new int[] {7, 1};
            case "trial_spawner", "vault": return new int[] {8, 8};
            case "tuff_bricks", "chiseled_tuff_bricks", "copper_grate", "chiseled_tuff", "waxed_copper_grate": return new int[] {8, 1};
            case "dirt_path", "farmland": return new int[] {9, 1};
            case "bell", "composter", "hay_block": return new int[] {9, 3};
            case "lectern", "loom", "cartography_table", "fletching_table", "smithing_table", "grindstone", "stonecutter": return new int[] {9, 2};
            case "chiseled_sandstone", "cut_sandstone", "mossy_cobblestone", "tripwire_hook", "tripwire": return new int[] {10, 1};
            case "dark_oak_planks", "dark_oak_stairs", "dark_oak_door": return new int[] {11, 1};
            case "spawner": return new int[] {12, 8};
            default: break;
        }
        if (p.equals("oak_fence")) return new int[] {1, 1};
        return new int[] {0, 0};
    }

    /** Blocks that nature and generated structures do not place, so finding them is a strong sign of a player. */
    static boolean isTell(String p) {
        return p.endsWith("_concrete") || p.endsWith("glazed_terracotta") || p.endsWith("stained_glass") || p.endsWith("stained_glass_pane")
                || p.equals("beacon") || p.equals("netherite_block") || p.equals("diamond_block") || p.equals("emerald_block")
                || p.equals("iron_block") || p.equals("lapis_block") || p.equals("redstone_block") || p.equals("hopper") || p.equals("comparator")
                || p.equals("repeater") || p.equals("observer") || p.equals("redstone_lamp") || p.equals("daylight_detector") || p.equals("note_block")
                || p.equals("sticky_piston") || p.equals("dropper") || p.equals("ender_chest") || p.equals("enchanting_table") || p.equals("lightning_rod") || p.equals("chiseled_quartz_block")
                || p.equals("quartz_bricks") || p.equals("quartz_pillar") || p.equals("smooth_quartz")
                || p.equals("iron_door") || p.equals("iron_trapdoor") || p.equals("player_head") || p.equals("player_wall_head") || p.equals("lodestone")
                || p.equals("respawn_anchor") || p.equals("conduit") || p.equals("jukebox") || p.equals("slime_block") || p.equals("honey_block")
                || p.equals("heavy_weighted_pressure_plate") || p.equals("light_weighted_pressure_plate") || p.equals("crafter") || p.equals("dragon_egg")
                || p.equals("copper_bulb") || p.equals("calibrated_sculk_sensor") || p.equals("beehive");
    }

    static int[] weighPath(String p) {
        if (p.isEmpty()) return new int[] {0, 0};
        // storage
        if (p.equals("ender_chest")) return new int[] {60, 0};
        if (p.contains("chest") || p.endsWith("shulker_box") || p.equals("barrel")) return new int[] {40, 0};
        // things only a player puts down
        switch (p) {
            case "lodestone", "respawn_anchor", "conduit", "jukebox", "crafter", "beehive", "copper_bulb", "calibrated_sculk_sensor":
                return new int[] {50, 1};
            case "iron_door", "iron_trapdoor":
                return new int[] {30, 5};
            case "player_head", "player_wall_head":
                return new int[] {40, 5};
            case "slime_block", "honey_block", "heavy_weighted_pressure_plate", "light_weighted_pressure_plate":
                return new int[] {30, 2};
            default:
                break;
        }
        // workstations
        switch (p) {
            case "enchanting_table", "brewing_stand":
                return new int[] {60, 1};
            case "furnace", "blast_furnace", "smoker", "crafting_table", "anvil", "chipped_anvil", "damaged_anvil", "grindstone", "loom",
                 "stonecutter", "smithing_table", "fletching_table", "cartography_table", "lectern", "cauldron":
                return new int[] {30, 1};
            default:
                break;
        }
        // redstone
        switch (p) {
            case "hopper", "dispenser", "dropper", "repeater", "comparator", "daylight_detector", "redstone_block", "tnt":
                return new int[] {50, 2};
            case "observer", "piston", "sticky_piston", "redstone_lamp", "powered_rail", "detector_rail", "activator_rail":
                return new int[] {40, 2};
            case "lever", "note_block", "target", "redstone_wire", "redstone_torch", "redstone_wall_torch":
                return new int[] {25, 2};
            default:
                break;
        }
        // lighting
        switch (p) {
            case "sea_lantern", "end_rod":
                return new int[] {40, 3};
            case "lantern", "soul_lantern", "campfire", "soul_campfire", "jack_o_lantern":
                return new int[] {20, 3};
            case "torch", "wall_torch", "soul_torch", "soul_wall_torch":
                return new int[] {12, 3};
            default:
                break;
        }
        // metal and gem blocks
        switch (p) {
            case "beacon", "netherite_block":
                return new int[] {300, 4};
            case "diamond_block", "emerald_block":
                return new int[] {120, 4};
            case "gold_block", "iron_block", "lapis_block", "copper_block", "raw_gold_block", "raw_iron_block", "coal_block":
                return new int[] {80, 4};
            default:
                break;
        }
        // building
        if (p.equals("glass") || p.endsWith("_glass") || p.endsWith("glass_pane") || p.equals("tinted_glass")) return new int[] {25, 5};
        if (p.endsWith("_concrete")) return new int[] {30, 5};
        if (p.endsWith("glazed_terracotta")) return new int[] {40, 5};
        if (p.endsWith("_wool")) return new int[] {20, 5};
        if (p.endsWith("_carpet") && !p.startsWith("moss")) return new int[] {20, 5};
        if (p.endsWith("_bed")) return new int[] {40, 5};
        if (p.startsWith("stripped_")) return new int[] {30, 5};
        if (p.equals("bookshelf") || p.equals("chiseled_bookshelf")) return new int[] {30, 5};
        if (p.startsWith("quartz_") || p.equals("smooth_quartz") || p.equals("chiseled_quartz_block")) return new int[] {40, 5};
        if (p.equals("smooth_quartz") || p.equals("smooth_stone") || p.startsWith("cut_copper") || p.startsWith("polished_") && !p.contains("basalt") && !p.contains("blackstone") && !p.contains("deepslate") && !p.contains("tuff")) return new int[] {15, 5};
        if (p.endsWith("_door") || p.endsWith("trapdoor")) return new int[] {15, 5};
        if (p.endsWith("_sign") || p.endsWith("_hanging_sign")) return new int[] {20, 5};
        if (p.endsWith("_stairs") || p.endsWith("_slab") || p.endsWith("_fence") || p.endsWith("_fence_gate") || p.endsWith("_wall")) return new int[] {8, 5};
        if (p.endsWith("_planks") || p.equals("bricks") || p.equals("stone_bricks") || p.equals("ladder")) return new int[] {6, 5};
        return new int[] {0, 0};
    }

    // ------------------------------------------------------------------ the scan

    private static long key(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    private static int kx(long k) {
        return (int) (k >> 32);
    }

    private static int kz(long k) {
        return (int) k;
    }

    @Override
    protected void onTick(Minecraft mc) {
        // Multiplayer scanning is opt-in and reads only chunks already loaded by the client.
        // No packet tricks, chunk requests, or access to server-side/unloaded world data are used.
        if (!Env.local() && !scanMultiplayer.on()) return;
        if (mc.level == null || mc.player == null) return;
        int pcx = mc.player.getBlockX() >> 4;
        int pcz = mc.player.getBlockZ() >> 4;
        int rc = range.i();
        long now = System.currentTimeMillis();
        Activity.setCounting(activity.on());
        Activity.setMemorySeconds(switch (activityMemory.index()) { case 0 -> 15; case 2 -> 60; case 3 -> 120; default -> 30; });
        if (tick % 10 == 5) watchActivity(mc);
        if (tick++ % 20 == 0) {
            queue = new ArrayList<>();
            queueIndex = 0;
            for (int dx = -rc; dx <= rc; dx++) {
                for (int dz = -rc; dz <= rc; dz++) {
                    Score s = chunks.get(key(pcx + dx, pcz + dz));
                    // the area around you is checked again now and then, because you build and dig
                    long age = now - (s == null ? 0 : s.at);
                    long max = Math.abs(dx) <= 3 && Math.abs(dz) <= 3 ? 8000 : 90000;
                    if (s == null || age > max) queue.add(new long[] {dx * dx + dz * dz, pcx + dx, pcz + dz});
                }
            }
            queue.sort((a, b) -> Long.compare(a[0], b[0]));
            final int fcx = pcx;
            final int fcz = pcz;
            chunks.keySet().removeIf(k -> Math.abs(kx(k) - fcx) > rc + 3 || Math.abs(kz(k) - fcz) > rc + 3);
            geodeHints.keySet().removeIf(k -> Math.abs(kx(k) - fcx) > rc + 3 || Math.abs(kz(k) - fcz) > rc + 3);
            entScore.keySet().removeIf(k -> Math.abs(kx(k) - fcx) > rc + 3 || Math.abs(kz(k) - fcz) > rc + 3);
            scanEntities(mc, rc);
            rebuild(mc);
        }
        long baseBudget = switch (scanPace.index()) {
            case 0 -> 750_000L;
            case 2 -> 3_000_000L;
            default -> 1_500_000L;
        };
        long deadline = System.nanoTime() + Brain.budget(baseBudget);
        boolean any = false;
        while (queueIndex < queue.size() && System.nanoTime() < deadline) {
            long[] c = queue.get(queueIndex++);
            scan(mc, (int) c[1], (int) c[2], now);
            any = true;
        }
        if (any && queueIndex >= queue.size()) {
            queue.clear();
            queueIndex = 0;
            rebuild(mc);
        }
    }

    /** Focused scanners can add terrain-shape evidence without changing the general base scanner. */
    protected boolean useTunnelGeometry() {
        return false;
    }

    private static BlockState stateAt(LevelChunkSection[] sections, int minSectionY, int x, int y, int z) {
        int sectionIndex = (y >> 4) - minSectionY;
        if (sectionIndex < 0 || sectionIndex >= sections.length || sections[sectionIndex] == null) return null;
        return sections[sectionIndex].getBlockState(x, y & 15, z);
    }

    private static boolean air(LevelChunkSection[] sections, int minSectionY, int x, int y, int z) {
        BlockState state = stateAt(sections, minSectionY, x, y, z);
        return state == null || state.isAir();
    }

    private static boolean solidDry(LevelChunkSection[] sections, int minSectionY, int x, int y, int z) {
        BlockState state = stateAt(sections, minSectionY, x, y, z);
        return state != null && !state.isAir() && state.getFluidState().isEmpty();
    }

    /**
     * The terrain-shape part of the suspicious-chunk approach. It looks for
     * enclosed, two-block-high horizontal passages and narrow vertical shafts in the
     * already-loaded chunk. Geometry is only supporting evidence: caves alone never
     * become a base, and no neighbouring/unloaded chunk is requested.
     */
    private static int[] tunnelGeometry(LevelChunk chunk, int cx, int cz, int surface) {
        LevelChunkSection[] sections = chunk.getSections();
        int minSectionY = chunk.getMinSectionY();
        int minY = minSectionY << 4;
        int maxY = minY + sections.length * 16 - 2;
        int top = Math.min(maxY, surface - 3);
        int bottom = minY + 2;
        if (top <= bottom) return new int[] {0, 0};
        int runs = 0;
        int shafts = 0;

        // Sample every second Y level to keep the scan bounded while covering deep tunnels.
        for (int y = bottom; y <= top; y += 2) {
            for (int z = 1; z < 15; z++) {
                for (int x = 1; x < 15; x++) {
                    // A three-cell horizontal passage, with floor, roof and side walls.
                    if (x <= 12 && air(sections, minSectionY, x, y, z) && air(sections, minSectionY, x, y + 1, z)
                            && air(sections, minSectionY, x + 1, y, z) && air(sections, minSectionY, x + 1, y + 1, z)
                            && air(sections, minSectionY, x + 2, y, z) && air(sections, minSectionY, x + 2, y + 1, z)
                            && solidDry(sections, minSectionY, x + 1, y - 1, z) && solidDry(sections, minSectionY, x + 1, y + 2, z)
                            && solidDry(sections, minSectionY, x + 1, y, z - 1) && solidDry(sections, minSectionY, x + 1, y + 1, z - 1)
                            && solidDry(sections, minSectionY, x + 1, y, z + 1) && solidDry(sections, minSectionY, x + 1, y + 1, z + 1)) {
                        runs++;
                    }
                    if (z <= 12 && air(sections, minSectionY, x, y, z) && air(sections, minSectionY, x, y + 1, z)
                            && air(sections, minSectionY, x, y, z + 1) && air(sections, minSectionY, x, y + 1, z + 1)
                            && air(sections, minSectionY, x, y, z + 2) && air(sections, minSectionY, x, y + 1, z + 2)
                            && solidDry(sections, minSectionY, x, y - 1, z + 1) && solidDry(sections, minSectionY, x, y + 2, z + 1)
                            && solidDry(sections, minSectionY, x - 1, y, z + 1) && solidDry(sections, minSectionY, x - 1, y + 1, z + 1)
                            && solidDry(sections, minSectionY, x + 1, y, z + 1) && solidDry(sections, minSectionY, x + 1, y + 1, z + 1)) {
                        runs++;
                    }
                    // A narrow, enclosed vertical shaft (three or more air blocks).
                    if (air(sections, minSectionY, x, y, z) && air(sections, minSectionY, x, y + 1, z) && air(sections, minSectionY, x, y + 2, z)
                            && solidDry(sections, minSectionY, x - 1, y + 1, z) && solidDry(sections, minSectionY, x + 1, y + 1, z)
                            && solidDry(sections, minSectionY, x, y + 1, z - 1) && solidDry(sections, minSectionY, x, y + 1, z + 1)) {
                        shafts++;
                    }
                }
            }
        }
        return new int[] {Math.min(32, runs), Math.min(12, shafts)};
    }

    private boolean yWanted(int y, int seaLevel) {
        return switch (where.index()) {
            case 1 -> y < seaLevel - 8;
            case 2 -> y >= seaLevel - 8;
            default -> true;
        };
    }

    /**
     * Returns -1 for non-crops, 0 for an immature crop and 1 for a mature crop.
     * This deliberately remains a weak signal: natural farms and villages are common.
     */
    private static String growthKind(Block block) {
        return GROWTH_KIND.computeIfAbsent(block, b -> {
            String path;
            try { path = BuiltInRegistries.BLOCK.getKey(b).getPath(); }
            catch (RuntimeException ex) { return ""; }
            return path.equals("wheat") || path.equals("carrots") || path.equals("potatoes")
                    || path.equals("beetroots") || path.equals("nether_wart") || path.equals("sweet_berry_bush")
                    || path.equals("cave_vines") || path.equals("cave_vines_plant") || path.equals("cocoa")
                    || path.equals("pumpkin_stem") || path.equals("melon_stem") || path.equals("bamboo")
                    || path.equals("kelp") || path.equals("kelp_plant") || path.equals("bee_nest") || path.equals("beehive") ? path : "";
        });
    }

    private static int growthStage(BlockState state, String path) {
        if (path.isEmpty()) return -1;
        for (var entry : state.getValues().entrySet()) {
            String property = entry.getKey().getName();
            String value = entry.getValue().toString();
            if (property.equals("berries")) return Boolean.parseBoolean(value) ? 1 : 0;
            if (property.equals("age") || property.equals("stage") || property.equals("honey_level")) {
                try {
                    int age = Integer.parseInt(value);
                    int max = entry.getKey().getPossibleValues().size() - 1;
                    return max >= 0 && age >= max ? 1 : 0;
                } catch (NumberFormatException ignored) { return 0; }
            }
        }
        return -1;
    }

    /** 1=shell, 2=budding amethyst, 3=bud/cluster, 4=amethyst block. */
    private static int geodeKind(Block block) {
        return GEODE_KIND.computeIfAbsent(block, b -> {
            String path;
            try { path = BuiltInRegistries.BLOCK.getKey(b).getPath(); }
            catch (RuntimeException ex) { return 0; }
            if (path.equals("calcite") || path.equals("smooth_basalt")) return 1;
            if (path.equals("budding_amethyst")) return 2;
            if (path.equals("small_amethyst_bud") || path.equals("medium_amethyst_bud")
                    || path.equals("large_amethyst_bud") || path.equals("amethyst_cluster")) return 3;
            if (path.equals("amethyst_block")) return 4;
            return 0;
        });
    }

    private static boolean exposedBudding(Minecraft mc, BlockPos pos, int cx, int cz) {
        int open = 0;
        for (Direction direction : Direction.values()) {
            BlockPos neighbour = pos.relative(direction);
            // Stay inside this already-loaded chunk; do not request neighbouring chunks just to score a clue.
            if ((neighbour.getX() >> 4) != cx || (neighbour.getZ() >> 4) != cz) continue;
            if (mc.level.getBlockState(neighbour).isAir()) open++;
        }
        return open >= 2;
    }

    private List<String> geodeLines() {
        if (!enabled()) return List.of("Switch the scanner on to inspect loaded chunks.");
        if (!minedGeodeScan.on()) return List.of("Disabled in settings.");
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || geodeHints.isEmpty()) return List.of("No likely mined geodes in the scanned area.");
        List<Map.Entry<Long, GeodeHint>> sorted = new ArrayList<>(geodeHints.entrySet());
        sorted.sort((a, b) -> Double.compare(
                Math.hypot(kx(a.getKey()) * 16 + 8 - mc.player.getX(), kz(a.getKey()) * 16 + 8 - mc.player.getZ()),
                Math.hypot(kx(b.getKey()) * 16 + 8 - mc.player.getX(), kz(b.getKey()) * 16 + 8 - mc.player.getZ())));
        List<String> out = new ArrayList<>();
        for (int i = 0; i < Math.min(5, sorted.size()); i++) {
            var entry = sorted.get(i);
            GeodeHint h = entry.getValue();
            int x = kx(entry.getKey()) * 16 + 8;
            int z = kz(entry.getKey()) * 16 + 8;
            int distance = (int) Math.round(Math.hypot(x - mc.player.getX(), z - mc.player.getZ()));
            out.add(String.format(Locale.ROOT, "Likely mined geode   %d, %d   %dm   %d exposed budding / %d crystals", x, z, distance, h.exposedBudding(), h.crystals()));
            out.add("    evidence: " + h.shell() + " calcite/basalt shell blocks, " + h.budding() + " budding amethyst");
        }
        return out;
    }

    private void scan(Minecraft mc, int cx, int cz, long now) {
        LevelChunk chunk = Env.chunk(mc, cx, cz);
        if (chunk == null) return;
        Score s = new Score();
        s.at = now;
        LevelChunkSection[] sections = chunk.getSections();
        int minY = chunk.getMinSectionY() << 4;
        int sea = 63;
        int surf = Brain.surface(mc, (cx << 4) + 8, (cz << 4) + 8);
        double underW = 0;
        double allW = 0;
        double bestSection = 0;
        Set<String> cropKinds = new HashSet<>();
        int geodeShell = 0, geodeBudding = 0, geodeCrystals = 0, geodeExposed = 0;
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection sec = sections[i];
            if (sec == null || sec.hasOnlyAir() || !sec.maybeHas(st -> {
                int[] w = weigh(st.getBlock());
                return w[0] > 0 || w[2] > 0 || w[6] > 0 || w[7] > 0
                        || (growthEvidence.on() && !growthKind(st.getBlock()).isEmpty())
                        || (minedGeodeScan.on() && geodeKind(st.getBlock()) != 0);
            })) continue;
            int by = minY + i * 16;
            double here = 0;
            for (int y = 0; y < 16; y++) {
                boolean wanted = yWanted(by + y, sea);
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = sec.getBlockState(x, y, z);
                        Block block = state.getBlock();
                        int[] w = weigh(block);
                        if (minedGeodeScan.on()) {
                            int geode = geodeKind(block);
                            if (geode == 1) geodeShell++;
                            else if (geode == 2) {
                                geodeBudding++;
                                BlockPos pos = new BlockPos((cx << 4) + x, by + y, (cz << 4) + z);
                                if (exposedBudding(mc, pos, cx, cz)) geodeExposed++;
                            } else if (geode == 3) geodeCrystals++;
                        }
                        if (growthEvidence.on() && wanted) {
                            String crop = growthKind(block);
                            int stage = growthStage(state, crop);
                            if (stage >= 0) {
                                if (stage == 1) s.grownPlants++; else s.youngPlants++;
                                cropKinds.add(crop);
                            }
                        }
                        if (w[2] > 0) {
                            s.markers[w[2] - 1] += w[3];
                            if (surf != Integer.MIN_VALUE && by + y < surf - 8) s.under[w[2] - 1] += w[3];
                        }
                        if (w[7] > 0 && wanted) s.foot |= 1L << w[7];
                        if (w[6] > 0 && wanted) {
                            s.roles[w[6]]++;
                            if ((w[6] == BaseBrain.R_BED || w[6] == BaseBrain.R_CONTAINER || w[6] == BaseBrain.R_VALUABLE) && s.keys.size() < 60) {
                                s.keys.add(Ledger.pack((cx << 4) + x, by + y, (cz << 4) + z));
                            }
                        }
                        if (w[0] == 0 || !wanted) continue;
                        allW += w[0];
                        if (surf != Integer.MIN_VALUE && by + y < surf - 6) underW += w[0];
                        double v = w[0] / 10.0;
                        here += v;
                        if (w[5] == 2) {
                            s.strong += v;
                            s.strongCount++;
                        } else if (w[5] == 1) {
                            s.mid += v;
                        } else {
                            s.weak += v;
                        }
                        if (w[4] == 1) s.tell += v;
                        s.groups[w[1]] += v;
                        s.ySum += by + y;
                        s.yCount++;
                        if (by + y < s.minY) s.minY = by + y;
                        if (by + y > s.maxY) s.maxY = by + y;
                    }
                }
            }
            bestSection = Math.max(bestSection, here);
        }
        s.growthKinds = cropKinds.size();
        if (clearance.on()) {
            Clearance.Result cr = Clearance.of(chunk);
            s.owed = cr.owed();
            s.owedKinds = cr.kinds();
        }
        if (useTunnelGeometry() && surf != Integer.MIN_VALUE) {
            int[] geometry = tunnelGeometry(chunk, cx, cz, surf);
            s.tunnelRuns = geometry[0];
            s.verticalShafts = geometry[1];
        }
        long chunkKey = key(cx, cz);
        if (minedGeodeScan.on() && geodeShell >= 10 && geodeBudding >= 4 && geodeExposed >= 3
                && geodeCrystals <= Math.max(2, geodeBudding / 5)) {
            geodeHints.put(chunkKey, new GeodeHint(geodeShell, geodeBudding, geodeCrystals, geodeExposed));
        } else {
            geodeHints.remove(chunkKey);
        }
        // ordinary blocks (planks, stairs, torches...) only add a little to a chunk that already holds blocks a player placed
        s.total = s.strong + Math.min(s.mid, s.strong + 6) + Math.min(s.weak, s.strong * 0.5 + 3);
        // growth-pattern clue: mature crop diversity supports an existing build signal,
        // but is capped and cannot independently make a farm look like a hidden base.
        if (growthEvidence.on() && s.strongCount > 0 && s.grownPlants > 0) {
            double growth = Math.min(5.0, s.grownPlants * 0.12 + s.youngPlants * 0.015);
            if (s.growthKinds >= 3) growth *= 1.2;
            s.total += growth;
        }
        // Tunnel geometry is a low-weight corroborating signal, never a standalone base detector.
        // Require existing player evidence (for example storage, lighting or distinctive blocks).
        if (useTunnelGeometry() && (s.strongCount > 0 || s.tell > 0 || s.roles[BaseBrain.R_LIGHT] > 0)) {
            s.total += Math.min(8.0, s.tunnelRuns * 0.35 + s.verticalShafts * 0.9);
        }
        // a chunk that is only one kind of thing (a field of lanterns) is less telling than one that mixes several kinds
        int kinds = 0;
        for (double g : s.groups) if (g >= 3) kinds++;
        double adjusted = s.total * (kinds >= 3 ? 1.25 : kinds == 2 ? 1.0 : 0.7);
        // a small, dense build in one spot is more telling than the same amount spread out
        if (s.total > 0 && bestSection / s.total > 0.6 && s.total > threshold() * 0.4) adjusted *= 1.25;
        s.total = adjusted;
        s.underground = allW <= 0 ? 0 : surf == Integer.MIN_VALUE ? 0.3 : underW / allW;
        if (s.strongCount == 0) s.total = Math.min(s.total, 8);
        double bestRatio = 0;
        for (int i = 0; i < STRUCTS.length; i++) {
            // mineshafts, strongholds, ancient cities, trial chambers and dungeons are underground: fences or rails on the surface are a player's
            boolean undergroundOnly = i == 0 || i == 1 || i == 4 || i == 7 || i == 11;
            if (undergroundOnly && surf != Integer.MIN_VALUE && s.under[i] < s.markers[i] * 0.6) continue;
            double ratio = s.markers[i] / (double) STRUCT_MIN[i];
            if (ratio >= 1.0 && ratio > bestRatio) {
                bestRatio = ratio;
                s.structure = i;
                s.structPts = s.markers[i];
            }
        }
        chunks.put(key(cx, cz), s);
    }

    private void scanEntities(Minecraft mc, int rc) {
        entScore.clear();
        itemCount.clear();
        clueMap.clear();
        if (!entities.on()) return;
        Map<Long, Map<String, Integer>> herds = new HashMap<>();
        for (Entity e : Env.entities(mc, rc * 16)) {
            double v;
            EntityType<?> t = e.getType();
            long ck = key(e.getBlockX() >> 4, e.getBlockZ() >> 4);
            String tid = entityId(t);
            int[] cl = clueMap.computeIfAbsent(ck, k -> new int[4]);
            switch (tid) {
                case "ender_pearl" -> cl[0]++;
                case "villager", "wandering_trader" -> cl[2]++;
                case "cow", "pig", "sheep", "chicken", "horse", "donkey", "mule", "llama", "goat", "rabbit", "mooshroom", "camel" ->
                        herds.computeIfAbsent(ck, k -> new HashMap<>()).merge(tid, 1, Integer::sum);
                default -> { }
            }
            if (e.hasCustomName() && !tid.equals("player") && !tid.equals("item") && !tid.equals("armor_stand") && !tid.endsWith("item_frame")) cl[1]++;
            if (t == EntityType.ITEM) {
                itemCount.merge(key(e.getBlockX() >> 4, e.getBlockZ() >> 4), 1, Integer::sum);
                continue;
            }
            if (t == EntityType.ITEM_FRAME || t == EntityType.GLOW_ITEM_FRAME) v = 14;
            else if (t == EntityType.ARMOR_STAND) v = 14;
            else if (t == EntityType.CHEST_MINECART || t == EntityType.HOPPER_MINECART) v = 20;
            else if (t == EntityType.PAINTING) v = 8;
            else continue;
            entScore.merge(ck, v, Double::sum);
        }
        for (Map.Entry<Long, Map<String, Integer>> h : herds.entrySet()) {
            int most = 0;
            for (int n : h.getValue().values()) most = Math.max(most, n);
            clueMap.computeIfAbsent(h.getKey(), k -> new int[4])[3] = most;
        }
    }

    private static String entityId(EntityType<?> t) {
        try {
            String id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(t).toString();
            return id.substring(id.indexOf(':') + 1);
        } catch (RuntimeException e) {
            return "";
        }
    }

    private void judgeAll(int mode, boolean hubMode, boolean nether) {
        Map<Long, Double> ev = new HashMap<>();
        for (Map.Entry<Long, Score> en : chunks.entrySet()) {
            Score s = en.getValue();
            BaseBrain.Features ft = features(s, mode == 2);
            if (activity.on() && useActivity.on()) ft.activity = Activity.heat(kx(en.getKey()), kz(en.getKey()));
            s.verdict = BaseBrain.judge(ft, hubMode, nether);
            if (s.structure < 0 || mode == 2) ev.put(en.getKey(), Math.max(0, s.verdict.evidence - 0.5));
        }
        if (hubMode) return; // in a hub every neighbour looks built; that proves nothing
        for (Map.Entry<Long, Score> en : chunks.entrySet()) {
            long k = en.getKey();
            double sum = 0;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    sum += ev.getOrDefault(key(kx(k) + dx, kz(k) + dz), 0.0);
                }
            }
            BaseBrain.boost(en.getValue().verdict, sum);
        }
    }

    /** Everything Zeus knows about the best chunks, as text, to paste into a message when something is not found. */
    private void copyReport() {
        Minecraft mc = Minecraft.getInstance();
        StringBuilder b = new StringBuilder();
        Brain.Ctx c = Brain.ctx();
        b.append("Zeus ").append(name).append(" report: ").append(c.server).append(" / ").append(c.dim).append(", ").append(chunks.size()).append(" chunks scanned, ")
                .append(queueRemaining()).append(" waiting, ").append(finds.size()).append(" finds, pace=").append(scanPace.value()).append(", cut-off ")
                .append(Math.round(BaseBrain.cutoff(sensitivity.index(), Brain.learning() ? Brain.baseline().crowding : 0.08) + Feedback.bias(c.server))).append(" %, fps ").append(c.fps).append('\n');
        List<Map.Entry<Long, Score>> all = new ArrayList<>(chunks.entrySet());
        all.removeIf(e -> e.getValue().verdict == null);
        all.sort((x, y) -> Integer.compare(y.getValue().verdict.confidence, x.getValue().verdict.confidence));
        for (int i = 0; i < Math.min(12, all.size()); i++) {
            Score s = all.get(i).getValue();
            b.append(String.format(Locale.ROOT, "%d %% %s at %d,%d flagged=%b beds=%d storage=%d work=%d hoppers=%d lights=%d portal=%d kinds=%d tunnels=%d shafts=%d struct=%s | %s%n",
                    s.verdict.confidence, s.verdict.type, kx(all.get(i).getKey()) * 16 + 8, kz(all.get(i).getKey()) * 16 + 8, s.flagged, s.roles[BaseBrain.R_BED], s.roles[BaseBrain.R_CONTAINER],
                    s.roles[BaseBrain.R_WORK], s.roles[BaseBrain.R_HOPPER], s.roles[BaseBrain.R_LIGHT], s.roles[BaseBrain.R_PORTAL], Long.bitCount(s.foot), s.tunnelRuns, s.verticalShafts,
                    s.structure >= 0 ? STRUCTS[s.structure] : "none", s.verdict.why()));
        }
        try {
            mc.keyboardHandler.setClipboard(b.toString());
            Zeus.toast("Report copied. Paste it into a message.", true);
        } catch (RuntimeException e) {
            Zeus.toast("Could not copy the report", false);
        }
    }

    /** The finds of this session as {x, z, score, chunks}. Read-only, for the local website's map. */
    public static java.util.List<double[]> findsNow() {
        SuspiciousChunks m = com.zlorbie788.zeus.core.Zeus.get(SuspiciousChunks.class);
        if (m == null) return java.util.List.of();
        java.util.List<double[]> out = new java.util.ArrayList<>();
        for (Find f : m.finds) out.add(new double[]{f.cx, f.cz, f.total, f.chunks});
        return out;
    }

    /** Decides which chunks are flagged and merges neighbours into finds. */
    private void rebuild(Minecraft mc) {
        int mode = natural.index();
        boolean nether = Brain.ctx().dim.equals("nether");
        // 1. judge every chunk on its own, then let the chunks around it vouch for it
        for (Map.Entry<Long, Score> en : chunks.entrySet()) {
            Score s = en.getValue();
            s.ents = entScore.getOrDefault(en.getKey(), 0.0);
            s.items = itemCount.getOrDefault(en.getKey(), 0);
            s.clues = clueMap.get(en.getKey());
            if (s.structure == 0) s.ents = 0; // minecarts in a mineshaft are normal
        }
        judgeAll(mode, false, nether);
        // 2. how crowded with builds is this area? a spawn or a hub would flag half of itself
        double learned = Brain.learning() ? Brain.baseline().crowding : 0.08;
        int cut = BaseBrain.cutoff(sensitivity.index(), learned);
        String fbServer = Brain.ctx().server;
        String fbDim = Brain.ctx().dim;
        int cutFb = (int) Math.round(cut + Feedback.bias(fbServer));
        int first = 0;
        for (Score s : chunks.values()) if (s.verdict.confidence >= cut) first++;
        double crowding = chunks.isEmpty() ? 0 : first / (double) chunks.size();
        hub = chunks.size() >= 30 && crowding > 0.3;
        if (hub) judgeAll(mode, true, nether);
        if (Brain.learning() && chunks.size() >= 40 && System.currentTimeMillis() - lastLearn > 10000) {
            lastLearn = System.currentTimeMillis();
            Brain.baseline().add(crowding);
            Brain.learned();
        }
        // 2b. the opposite evidence: a chunk nobody ticked, sitting inside a ring of chunks nobody ticked
        if (clearance.on() && mc.player != null && !hub) {
            int pcx = mc.player.getBlockX() >> 4;
            int pcz = mc.player.getBlockZ() >> 4;
            for (Map.Entry<Long, Score> en : chunks.entrySet()) {
                Score s = en.getValue();
                if (s.verdict == null || s.owed > 0) continue;
                int cx = kx(en.getKey());
                int cz = kz(en.getKey());
                // right around you every chunk ticks, so a hole there means nothing
                if (Math.abs(cx - pcx) <= 4 && Math.abs(cz - pcz) <= 4) continue;
                int ring = 0;
                int kinds = 0;
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dz == 0) continue;
                        Score n = chunks.get(key(cx + dx, cz + dz));
                        if (n != null && n.owed >= 3) {
                            ring++;
                            kinds = Math.max(kinds, n.owedKinds);
                        }
                    }
                }
                if (ring < 5) continue;
                s.clearRing = ring;
                int conf = Math.min(90, 56 + ring * 3 + Math.min(8, kinds * 2));
                if (conf <= s.verdict.confidence) continue;
                s.verdict.confidence = conf;
                s.verdict.type = "Cleared area";
                s.verdict.reasons.clear();
                s.verdict.reasons.add(ring + " of the 8 chunks around it are untouched");
                s.verdict.reasons.add("its plants would have grown: nobody has been near");
                if (s.verdict.evidence <= 0) s.verdict.evidence = ring;
                if (s.minY == Integer.MAX_VALUE) {
                    s.minY = Math.max(-64, mc.player.getBlockY() - 40);
                    s.maxY = Math.min(320, mc.player.getBlockY() + 40);
                }
            }
        }
        for (Map.Entry<Long, Score> en : chunks.entrySet()) {
            Score s = en.getValue();
            int fx = kx(en.getKey());
            int fz = kz(en.getKey());
            boolean yes = Feedback.confirmed(fbServer, fbDim, fx, fz);
            s.flagged = !Feedback.dismissed(fbServer, fbDim, fx, fz)
                    && (yes || s.verdict.confidence >= cutFb && s.verdict.evidence > 0);
        }
        // 3. merge touching flagged chunks
        List<Find> out = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (Map.Entry<Long, Score> en : chunks.entrySet()) {
            long k = en.getKey();
            if (!en.getValue().flagged || !seen.add(k)) continue;
            Find f = new Find();
            java.util.ArrayDeque<Long> todo = new java.util.ArrayDeque<>();
            todo.add(k);
            double wx = 0;
            double wz = 0;
            double ws = 0;
            List<BaseBrain.Verdict> parts = new ArrayList<>();
            while (!todo.isEmpty()) {
                long c = todo.poll();
                Score s = chunks.get(c);
                f.chunks++;
                f.cells.add(new long[] {kx(c), kz(c)});
                parts.add(s.verdict);
                double weight = Math.max(1.0, s.total + s.ents);
                f.total += s.total + s.ents;
                f.tell += s.tell;
                f.strongCount += s.strongCount;
                f.fires += s.roles[BaseBrain.R_FIRE];
                f.items += s.items;
                f.tunnelRuns += s.tunnelRuns;
                f.verticalShafts += s.verticalShafts;
                wx += (kx(c) * 16 + 8) * weight;
                wz += (kz(c) * 16 + 8) * weight;
                ws += weight;
                if (s.minY < f.minY) f.minY = s.minY;
                if (s.maxY > f.maxY) f.maxY = s.maxY;
                for (int i = 0; i < f.groups.length; i++) f.groups[i] += s.groups[i];
                if (s.structure >= 0 && f.structure == null) f.structure = STRUCTS[s.structure];
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        long n = key(kx(c) + dx, kz(c) + dz);
                        Score ns = chunks.get(n);
                        if (ns != null && ns.flagged && seen.add(n)) todo.add(n);
                    }
                }
            }
            f.cx = wx / ws;
            f.cz = wz / ws;
            f.verdict = BaseBrain.merge(parts);
            // a find inside a village, mineshaft or other natural structure stays hidden when the player's blocks do not show clearly
            if (mode == 0 && f.structure != null && f.verdict.confidence < cut + 12) continue;
            if (f.chunks >= minCluster.i()) out.add(f);
        }
        finds = out;
        if (remember.on() && Brain.learning() && System.currentTimeMillis() - lastLedger > 4000) {
            lastLedger = System.currentTimeMillis();
            String server = Brain.ctx().server + "|" + Brain.ctx().dim;
            for (Find f : out) {
                Set<Long> keys = new HashSet<>();
                for (long[] c : f.cells) {
                    Score s = chunks.get(key((int) c[0], (int) c[1]));
                    if (s != null) keys.addAll(s.keys);
                }
                boolean weak = f.fires >= 3 || f.items >= 8;
                f.entry = Ledger.observe(server, f.cx, f.cz, f.verdict.type, f.verdict.confidence, keys,
                        pos -> chunks.containsKey(key(Ledger.ux(pos) >> 4, Ledger.uz(pos) >> 4)), weak && raid.on()).entry();
            }
        }
        if (alert.on() && mc.player != null) {
            for (Find f : out) {
                long id = key((int) Math.floor(f.cx / 16), (int) Math.floor(f.cz / 16));
                if (announced.add(id)) {
                    Zeus.toast(name + ": " + f.verdict.type.toLowerCase(Locale.ROOT) + " (" + f.verdict.confidence + " %) at " + Math.round(f.cx) + ", " + Math.round(f.cz) + " (" + f.verdict.why() + ")", true);
                    found(mc, f.cx, f.cz, f.chunks, f.minY, f.maxY, topGroup(f.groups), f.structure);
                }
            }
        }
    }

    private BaseBrain.Features features(Score s, boolean ignoreNature) {
        BaseBrain.Features f = new BaseBrain.Features();
        f.total = s.total;
        f.tell = s.tell;
        f.strong = s.strongCount + (int) (s.ents / 14);
        f.ents = s.ents;
        System.arraycopy(s.groups, 0, f.groups, 0, f.groups.length);
        System.arraycopy(s.roles, 0, f.roles, 0, f.roles.length);
        f.underground = s.underground;
        f.structure = ignoreNature ? -1 : s.structure;
        f.structPoints = s.structPts;
        f.items = s.items;
        f.foot = Long.bitCount(s.foot);
        int[] cl = s.clues;
        if (cl != null) {
            f.pearls = cl[0];
            f.named = cl[1];
            f.villagers = s.structure == 8 ? 0 : cl[2];
            f.herd = cl[3];
        }
        return f;
    }

    /** A new find has appeared. Base Finder uses this to send the Discord message. */
    protected void found(Minecraft mc, double x, double z, int chunks, int minY, int maxY, String kind, String structure) {
        if (chunks >= 4) DiscordWebhook.bigBase(mc, x, z, chunks, minY, maxY, kind);
    }

    private static String findName(Find f) {
        String base = f.chunks >= 4 ? "big base" : f.chunks >= 2 ? "base" : "build";
        return f.structure != null ? base + " in a " + f.structure.toLowerCase(Locale.ROOT) : base;
    }

    private static String topGroup(double[] groups) {
        int best = 0;
        for (int i = 1; i < groups.length; i++) if (groups[i] > groups[best]) best = i;
        return GROUPS[best];
    }

    // ------------------------------------------------------------------ what it shows

    private static String direction(double dx, double dz) {
        double a = Math.toDegrees(Math.atan2(dz, dx));
        int i = (int) Math.round(((a % 360) + 360) % 360 / 45.0) % 8;
        return DIRS[i];
    }

    private List<String> describe() {
        List<String> out = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        if (!enabled()) {
            out.add("Switch the module on to scan.");
            return out;
        }
        if (mc.player == null) return out;
        List<Find> sorted = new ArrayList<>(finds);
        sorted.sort((a, b) -> Double.compare(dist(a, mc), dist(b, mc)));
        for (int i = 0; i < Math.min(5, sorted.size()); i++) {
            Find f = sorted.get(i);
            String y = f.minY == Integer.MAX_VALUE ? "" : "   y " + f.minY + " to " + f.maxY;
            String st = f.entry == null ? "" : "   " + f.entry.status;
            out.add(String.format(Locale.ROOT, "%s %d %%   %d, %d   %dm %s%s%s", f.verdict.type, f.verdict.confidence, Math.round(f.cx), Math.round(f.cz), Math.round(dist(f, mc)),
                    direction(f.cx - mc.player.getX(), f.cz - mc.player.getZ()), y, st));
            out.add("    because: " + f.verdict.why() + (f.entry != null && !f.entry.detail.isEmpty() ? "  ·  " + f.entry.detail : ""));
            if (useTunnelGeometry() && (f.tunnelRuns > 0 || f.verticalShafts > 0)) {
                out.add("    tunnel shape: " + f.tunnelRuns + " enclosed passage samples, " + f.verticalShafts + " shaft samples (supporting evidence only)");
            }
        }
        if (sorted.size() < 3) out.addAll(nearMisses(mc));
        if (hub) out.add("This area is full of builds (spawn or hub), so only the clearest bases are flagged.");
        if (natural.index() == 1) {
            Map<String, int[]> seen = new java.util.TreeMap<>();
            for (Map.Entry<Long, Score> e : chunks.entrySet()) {
                Score s = e.getValue();
                if (s.structure < 0 || s.flagged) continue;
                seen.computeIfAbsent(STRUCTS[s.structure], k -> new int[1])[0]++;
            }
            for (Map.Entry<String, int[]> e : seen.entrySet()) out.add(e.getKey() + ": " + e.getValue()[0] + " chunk" + (e.getValue()[0] == 1 ? "" : "s") + " (natural)");
        }
        if (out.isEmpty()) out.add("Nothing flagged yet. " + chunks.size() + " chunks scanned.");
        return out;
    }

    private static double dist(Find f, Minecraft mc) {
        return Math.sqrt(sq(f.cx - mc.player.getX()) + sq(f.cz - mc.player.getZ()));
    }

    private static double sq(double v) {
        return v * v;
    }

    @Override
    public String info() {
        return finds.isEmpty() ? null : Integer.toString(finds.size());
    }

    @Override
    protected void onHud(GuiGraphics g, Minecraft mc, float partial, int w, int h) {
        if (mc.player == null) return;
        if (radar.on()) drawRadar(g, mc, w, h);
    }

    @Override
    protected void onHudWorld(GuiGraphics g, Minecraft mc, float partial, int w, int h) {
        if (mc.player == null) return;
        if (world.on() && Projector.prepare(mc, partial, w, h)) drawMarkers(g, mc, w, h);
    }

    /** Whether the marker tracer to the nearest find is drawn. Variants with no HUD turn it off. */
    protected boolean hudTracer() {
        return true;
    }

    // ------------------------------------------------------------------ the map

    private static final int[] GROUP_COLORS = {0xFFFFC857, 0xFFB77BFF, 0xFFFF4D5E, 0xFFFF9F43, 0xFF4DD2FF, 0xFF5EE6A0, 0xFFFF7AB6};
    private static final String[] GROUP_LABELS = {"Storage", "Workstations", "Redstone", "Lighting", "Metal blocks", "Building", "Frames and stands"};
    private static final int NATURAL_COLOR = 0xFF4C86E8;
    /** Sure, likely, weak: the red, amber and green of the map and its legend. */
    private static final int[] BAND_COLORS = {0xFFE2555F, 0xFFE0A93C, 0xFF45CE93};

    /** Which kind of thing a flagged chunk is mostly made of; the last index is item frames and the like. */
    private static int groupOf(double[] groups) {
        int best = -1;
        double top = 0;
        for (int i = 0; i < groups.length; i++) {
            if (groups[i] > top) {
                top = groups[i];
                best = i;
            }
        }
        return best < 0 ? GROUPS.length : best;
    }

    private static int[] anchorCell(Find f) {
        long[] best = f.cells.get(0);
        double bd = Double.MAX_VALUE;
        for (long[] c : f.cells) {
            double d = sq(c[0] * 16 + 8 - f.cx) + sq(c[1] * 16 + 8 - f.cz);
            if (d < bd) {
                bd = d;
                best = c;
            }
        }
        return new int[] {(int) best[0], (int) best[1]};
    }

    private void drawRadar(GuiGraphics g, Minecraft mc, int w, int h) {
        int r = mapSize.i();
        int n = 2 * r + 1;
        int cell = tile.i();
        int detail = mapDetail.index();
        int pad = detail == 2 ? 5 : 9;
        int pcx = mc.player.getBlockX() >> 4;
        int pcz = mc.player.getBlockZ() >> 4;
        int gridW = n * cell;
        int headH = detail == 2 ? 0 : 34;
        int footH = detail == 2 ? 0 : 40;
        int pw = gridW + pad * 2;
        int ph = headH + gridW + footH + pad * 2;
        int[] at = Hud.place(corner.index(), pw, ph, w, h);
        int x = at[0];
        int y = at[1];
        int accent = Hud.accent();
        Hud.panel(g, x, y, pw, ph);
        Draw.rrectLine(g, x, y, pw, ph, Hud.round(7), Draw.rgba(0xFF8FA6A0, 0.10f));

        if (headH > 0) radarHead(g, x, y, pad, pw, accent);
        int flagged = 0;

        int rx = x + pad;
        int ry = y + pad + headH;
        int rrad = Math.max(4, Hud.round(8));
        Draw.rrect(g, rx, ry, gridW, gridW, rrad, 0xFF0B1315, 0xFF070D0F);
        Draw.rrectLine(g, rx, ry, gridW, gridW, rrad, Draw.rgba(0xFF4E7A84, 0.16f));
        for (int i = 1; i < n; i++) {
            int l = rx + i * cell;
            int t = ry + i * cell;
            g.fill(l, ry + 2, l + 1, ry + gridW - 2, Draw.rgba(0xFF2B4750, 0.34f));
            g.fill(rx + 2, t, rx + gridW - 2, t + 1, Draw.rgba(0xFF2B4750, 0.34f));
        }
        int ccx = rx + gridW / 2;
        int ccy = ry + gridW / 2;
        ringSeg(g, ccx, ccy, gridW * 0.22f, 1f, Draw.rgba(0xFF35617E, 0.55f));
        ringSeg(g, ccx, ccy, gridW * 0.44f, 1f, Draw.rgba(0xFF3E708F, 0.75f));

        // the way you face, as a soft wedge with a lit leading edge
        float yaw = (float) Math.toRadians(mc.player.getYRot());
        float dirx = -(float) Math.sin(yaw);
        float diry = (float) Math.cos(yaw);
        float mid = (float) Math.atan2(diry, dirx);
        float half = (float) Math.toRadians(32f);
        sector(g, ccx, ccy, dirx, diry, gridW * 0.50f, 32f, Draw.rgba(0xFF8E2740, 0.55f));
        for (int k = 0; k < 26; k++) {
            float a0 = mid - half + 2 * half * k / 26f;
            float a1 = mid - half + 2 * half * (k + 1) / 26f;
            Draw.line(g, ccx + (float) Math.cos(a0) * gridW * 0.50f, ccy + (float) Math.sin(a0) * gridW * 0.50f,
                    ccx + (float) Math.cos(a1) * gridW * 0.50f, ccy + (float) Math.sin(a1) * gridW * 0.50f,
                    1f, Draw.rgba(0xFFE2555F, 0.30f));
        }

        // the chunks themselves: red is sure, amber is likely, green is weak, blue is a natural structure
        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                Score s = chunks.get(key(pcx + dx, pcz + dz));
                if (s == null) continue;
                int col;
                boolean lit = false;
                if (s.flagged) {
                    flagged++;
                    col = BAND_COLORS[band(s)];
                    lit = true;
                } else if (s.structure >= 0 && natural.index() == 1) {
                    col = 0xFF2C4A72;
                } else if (isMaybe(s)) {
                    col = 0xFF6B5320;
                } else {
                    continue;
                }
                int ts = Math.max(4, Math.round(cell * 0.72f));
                int tx = rx + (dx + r) * cell + (cell - ts) / 2;
                int ty = ry + (dz + r) * cell + (cell - ts) / 2;
                int br = Math.min(3, Hud.round(3));
                if (lit) {
                    Draw.rrect(g, tx, ty, ts, ts, br, Draw.rgba(col, 0.26f));
                    Draw.rrectLine(g, tx, ty, ts, ts, br, Draw.rgba(col, 0.92f));
                    g.fill(tx + 2, ty, tx + ts - 2, ty + 1, Draw.rgba(0xFFFFFFFF, 0.14f));
                } else {
                    Draw.rrect(g, tx + 1, ty + 1, ts - 2, ts - 2, 2, col);
                }
                if (activity.on() && activityRing.on() && Activity.heat(pcx + dx, pcz + dz) >= activityMin.i()) {
                    float pulse = 0.55f + 0.45f * (float) Math.sin(System.nanoTime() / 1.8e8);
                    Draw.rrectLine(g, tx - 2, ty - 2, ts + 4, ts + 4, 4, Draw.rgba(0xFFFF8F4D, pulse));
                }
            }
        }

        selfArrow(g, ccx, ccy, dirx, diry, cell);
        north(g, rx + gridW - 16, ry + 5);
        if (gridW >= 70) {
            int sbx = rx + 9;
            int sby = ry + gridW - 13;
            Draw.textS(g, "2 chunks", sbx, sby - 12, 0xFFC4D0DC, 0.85f);
            int bar = Math.min(gridW - 18, cell * 2);
            g.fill(sbx, sby, sbx + bar, sby + 2, 0xFFE8EFFA);
            g.fill(sbx, sby - 2, sbx + 1, sby, 0xFFE8EFFA);
            g.fill(sbx + bar - 1, sby - 2, sbx + bar, sby, 0xFFE8EFFA);
        }

        if (footH > 0) {
            int fw = pw - pad * 2;
            int fy = ry + gridW + 8;
            int br = Hud.round(6);
            Draw.rrect(g, rx, fy, fw, 24, br, 0xFF1E4234, 0xFF122A21);
            if (!Draw.cheap) Draw.rrect(g, rx + fw / 2, fy + 1, Math.max(1, fw / 2 - 1), 22, br, Draw.rgba(0xFF63E6A6, 0.10f));
            Draw.rrectLine(g, rx, fy, fw, 24, br, Draw.rgba(0xFF63E6A6, 0.26f));
            Draw.spaced(g, "COORDINATE", rx + 9, fy + 9, 0xFF9BC0A8, 0.8f, 1, false);
            String coords = mc.player.getBlockX() + ", " + mc.player.getBlockZ();
            Draw.bold(g, coords, rx + fw - 9 - Draw.widthBold(coords), fy + 8, 0xFFFFFFFF);

            int ly = fy + 31;
            if (flagged == 0) {
                dot(g, rx + 1, ly + 2, 0xFF4A9BE8);
                String msg = finds.isEmpty() ? "Scanning nearby chunks · nothing found yet" : "Scanning nearby chunks · nothing in range";
                Draw.textS(g, Draw.fit(msg, fw - 14), rx + 9, ly, 0xFF8FA0B0, 0.85f);
            } else {
                String flaggedText = flagged + " flagged";
                int limit = rx + fw - Draw.widthBoldS(flaggedText, 0.85f) - 8;
                int lx = rx + 1;
                lx += legend(g, lx, ly, BAND_COLORS[0], "strong");
                if (lx + legendWidth("likely") <= limit) lx += legend(g, lx, ly, BAND_COLORS[1], "likely");
                if (lx + legendWidth("weak") <= limit) lx += legend(g, lx, ly, BAND_COLORS[2], "weak");
                if (!Draw.cheap) Draw.boldS(g, flaggedText, rx + fw - Draw.widthBoldS(flaggedText, 0.85f), ly, 0xFFF2F6FA, 0.85f);
            }
        }
    }

    /** The card's top row: the mark, the name, what it is, and how many finds there are. */
    private void radarHead(GuiGraphics g, int x, int y, int pad, int pw, int accent) {
        int ts = 22;
        int ix = x + pad;
        int iy = y + pad - 1;
        int br = Hud.round(6);
        Draw.rrect(g, ix, iy, ts, ts, br, Draw.rgba(0xFFA9B435, 0.26f), Draw.rgba(0xFF6D751F, 0.40f));
        Draw.rrectLine(g, ix, iy, ts, ts, br, Draw.rgba(0xFFCBD74E, 0.38f));
        float mx = ix + ts / 2f - 0.5f;
        float my = iy + ts / 2f - 0.8f;
        ringSeg(g, mx, my, 4.3f, 1.6f, 0xFFD6E05A);
        Draw.line(g, mx + 3.4f, my + 3.4f, mx + 6.6f, my + 6.6f, 2.2f, 0xFFD6E05A);

        boolean any = !finds.isEmpty();
        String count = finds.size() + (finds.size() == 1 ? " find" : " finds");
        int cw = Draw.widthS(count, 0.85f) + 14;
        int px = x + pw - pad - cw;
        int pr = Hud.round(8);
        int pc = any ? 0xFF63E6A6 : 0xFF9FB0CC;
        Draw.rrect(g, px, y + pad, cw, 15, pr, Draw.rgba(pc, 0.12f));
        Draw.rrectLine(g, px, y + pad, cw, 15, pr, Draw.rgba(pc, any ? 0.48f : 0.18f));
        Draw.textS(g, count, px + 7, y + pad + 5, any ? 0xFF8CF0B4 : 0xFFC4CFD6, 0.85f);

        int tx = ix + ts + 9;
        int have = px - 7 - tx;
        if (have < 26) return;
        // the title steps down a size on a narrow card rather than being cut short
        String title = "Suspicious Chunks";
        float tsc = 1f;
        if (Draw.widthBold(title) > have) tsc = 0.78f;
        if (Draw.widthBoldS(title, tsc) > have) tsc = 0.66f;
        Draw.boldS(g, Draw.fit(title, have), tx, y + pad + 1, 0xFFF2F6FA, tsc);
        String sub = "Base finder · live scan";
        if (Draw.widthS(sub, 0.78f) <= have) Draw.textS(g, sub, tx, y + pad + 14, 0xFF93A5B4, 0.78f);
    }

    /** A thin circle, drawn as short segments so it stays round at any size. */
    private static void ringSeg(GuiGraphics g, float cx, float cy, float rad, int color) {
        ringSeg(g, cx, cy, rad, 1f, color);
    }

    private static void ringSeg(GuiGraphics g, float cx, float cy, float rad, float thick, int color) {
        int seg = Math.max(16, Math.round(rad * 2.4f));
        for (int i = 0; i < seg; i++) {
            double a0 = i * Math.PI * 2 / seg;
            double a1 = (i + 1) * Math.PI * 2 / seg;
            Draw.line(g, cx + (float) Math.cos(a0) * rad, cy + (float) Math.sin(a0) * rad,
                    cx + (float) Math.cos(a1) * rad, cy + (float) Math.sin(a1) * rad, thick, color);
        }
    }

    /** A filled wedge from the centre out to {@code rad}, {@code halfDeg} to either side of the given direction. */
    private static void sector(GuiGraphics g, float cx, float cy, float dirx, float diry, float rad, float halfDeg, int color) {
        float mid = (float) Math.atan2(diry, dirx);
        float half = (float) Math.toRadians(halfDeg);
        int steps = 26;
        float thick = rad * 2 * half / steps + 1.5f;
        for (int i = 0; i <= steps; i++) {
            float a = mid - half + 2 * half * i / steps;
            Draw.line(g, cx, cy, cx + (float) Math.cos(a) * rad, cy + (float) Math.sin(a) * rad, thick, color);
        }
    }

    /** You, in the middle of the map: a small arrow that points the way you face. */
    private static void selfArrow(GuiGraphics g, float cx, float cy, float dirx, float diry, int cell) {
        float len = Math.max(8f, cell * 1.15f);
        float tipx = cx + dirx * len;
        float tipy = cy + diry * len;
        float bx = cx - dirx * len * 0.72f;
        float by = cy - diry * len * 0.72f;
        float px = -diry * len * 0.44f;
        float py = dirx * len * 0.44f;
        // pink body, white edge: readable on the dark map at any size
        for (int k = 0; k <= 12; k++) {
            float t = k / 12f;
            Draw.line(g, tipx, tipy, bx + px * t, by + py * t, 1.2f, 0xFFFF7594);
            Draw.line(g, tipx, tipy, bx - px * t, by - py * t, 1.2f, 0xFFFF7594);
        }
        Draw.line(g, tipx, tipy, bx + px, by + py, 1f, 0xFFFFFFFF);
        Draw.line(g, tipx, tipy, bx - px, by - py, 1f, 0xFFFFFFFF);
        Draw.line(g, bx + px, by + py, bx - px, by - py, 1f, 0xFFFFFFFF);
        Draw.line(g, cx - dirx * len * 0.1f, cy - diry * len * 0.1f, cx - dirx * len * 0.9f, cy - diry * len * 0.9f, 1f, 0xFFFFFFFF);
    }

    /** The little north marker: the letter, and a red needle under it. */
    private static void north(GuiGraphics g, int x, int y) {
        Draw.textS(g, "N", x + 5, y + 8, 0xFFD6E0EC, 1f);
        for (int i = 0; i < 6; i++) {
            float half = (i + 1) * 0.9f;
            Draw.line(g, x + 7f - half, y + 7f - i, x + 7f + half, y + 7f - i, 1f, 0xFFF2607A);
        }
    }

    /** How sure a flagged chunk is: 0 strong, 1 likely, 2 weak. */
    private static int band(Score s) {
        double c = s.verdict == null ? 0 : s.verdict.confidence;
        return c >= 65 ? 0 : c >= 35 ? 1 : 2;
    }

    private static void dot(GuiGraphics g, int x, int y, int color) {
        Draw.rrect(g, x, y, 5, 5, 3, color);
    }

    private static int legendWidth(String label) {
        return 10 + Math.round(Draw.widthS(label, 0.85f)) + 9;
    }

    private static int legend(GuiGraphics g, int x, int y, int color, String label) {
        dot(g, x, y + 2, color);
        Draw.textS(g, label, x + 9, y, 0xFFB9C6D4, 0.85f);
        return legendWidth(label);
    }

    private final Map<Long, ChunkLook.Area> areas = new HashMap<>();
    private final Map<Long, ChunkLook.Area> geodeAreas = new HashMap<>();
    private int markStamp;

    private void drawMarkers(GuiGraphics g, Minecraft mc, int sw, int sh) {
        double mr = markerRange.get() * 16;
        int drawn = 0;
        long now = System.currentTimeMillis();
        markStamp++;
        List<Find> sorted = new ArrayList<>(finds);
        sorted.sort((a, b) -> Double.compare(dist(a, mc), dist(b, mc)));
        boolean first = true;
        for (Find f : sorted) {
            if (drawn >= 8) break;
            double dist = dist(f, mc);
            if (dist > mr + f.chunks * 8) continue;
            drawn++;
            // the top of what was built there, used only to hang the label above the find
            double top = (f.maxY == Integer.MIN_VALUE ? mc.player.getY() : f.maxY) + 2;
            float certainty = f.verdict.confidence / 100f;
            int baseColour = GROUP_COLORS[groupOf(f.groups)];
            int tileColor;
            if (lineColor.index() <= 0) {
                // gradient from yellow (unsure / possible) to orange to AMR red (certain base)
                int yellow = 0xFFF4D84B;
                int orange = 0xFFF08C2E;
                int red = 0xFFDD3B3B;
                tileColor = (int) (Draw.mix(yellow, orange, Math.min(1f, certainty * 2f)));
                tileColor = (int) (Draw.mix(tileColor, red, Math.max(0f, certainty * 2f - 1f)));
            } else {
                tileColor = com.zlorbie788.zeus.render.EspKit.pick(lineColor, baseColour);
            }
            int fill = (tileColor & 0xFFFFFF) | 0x60000000;
            int color = tileColor;
            ChunkLook.Area area = ChunkLook.area(areas, f.cells, fill, now);
            if (area != null) {
                area.stamp = markStamp;
                ChunkLook.draw(g, area, area.scale(now), ChunkLook.PLANE);
                if (tracer.on() && hudTracer() && first) {
                    // one tracer to the middle of every separate area, in the fill colour
                    int tc = 0xFF000000 | (fill & 0xFFFFFF);
                    for (double[] c : area.centres) {
                        com.zlorbie788.zeus.render.EspKit.tracerLine(g, sw, sh, 0, c[0], ChunkLook.PLANE, c[1], tc, area.scale(now));
                    }
                }
            }
            first = false;
            float[] p = new float[2];
            if (Projector.project(f.cx, top + 1.5, f.cz, p)) {
                String status = f.entry == null || "Intact".equals(f.entry.status) ? "" : " · " + f.entry.status;
                boolean bad = f.entry != null && ("Raided".equals(f.entry.status) || "Looted".equals(f.entry.status) || f.entry.status.startsWith("Possibly"));
                int tone = bad ? 0xFFFF7A6B : color;
                String label = f.verdict.type + " " + f.verdict.confidence + " %" + status + "  " + Math.round(dist) + "m";
                int tw = Draw.width(label);
                int tx = Math.round(p[0]) - tw / 2;
                int ty = Math.round(p[1]);
                Draw.rrect(g, tx - 4, ty - 2, tw + 8, 12, 3, 0xD00A0F1C);
                g.fill(tx - 4, ty + 9, tx + tw + 4, ty + 10, tone);
                Draw.text(g, label, tx, ty, tone);
                String why = f.entry != null && !f.entry.detail.isEmpty() ? f.entry.detail : f.verdict.why();
                Draw.textCenter(g, Draw.fit(why, 220), Math.round(p[0]), ty + 13, Draw.rgba(tone, 0.8f));
            }
        }
        // likely mined geodes: one purple tile each
        if (minedGeodeScan.on() && !geodeHints.isEmpty()) {
            for (Map.Entry<Long, GeodeHint> en : geodeHints.entrySet()) {
                int gx = kx(en.getKey());
                int gz = kz(en.getKey());
                if (Math.hypot(gx * 16 + 8 - mc.player.getX(), gz * 16 + 8 - mc.player.getZ()) > mr) continue;
                List<long[]> one = new ArrayList<>();
                one.add(new long[] {gx, gz});
                ChunkLook.Area ga = ChunkLook.area(geodeAreas, one, ChunkLook.FILL_MINED, now);
                if (ga == null) continue;
                ga.stamp = markStamp;
                ChunkLook.draw(g, ga, ga.scale(now), ChunkLook.PLANE);
            }
        }
        // whatever stopped being flagged shrinks away instead of vanishing
        shrinkAway(g, areas, now);
        shrinkAway(g, geodeAreas, now);
    }

    private void shrinkAway(GuiGraphics g, Map<Long, ChunkLook.Area> store, long now) {
        if (store.isEmpty()) return;
        var it = store.values().iterator();
        while (it.hasNext()) {
            ChunkLook.Area a = it.next();
            if (a.stamp != markStamp) a.die(now);
            if (a.gone(now)) {
                it.remove();
                continue;
            }
            if (a.dying()) ChunkLook.draw(g, a, a.scale(now), ChunkLook.PLANE);
        }
    }

    private static String capital(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
