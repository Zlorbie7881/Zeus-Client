package com.zlorbie788.zeus.mods;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Lifecycle;
import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Setting;
import com.zlorbie788.zeus.core.Zeus;
import com.zlorbie788.zeus.render.EspKit;
import com.zlorbie788.zeus.render.Projector;
import com.zlorbie788.zeus.ui.Draw;
import com.zlorbie788.zeus.ui.SeedWorldsScreen;

import net.fabricmc.fabric.api.resource.v1.DataResourceStore;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SystemReport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gizmos.GizmoCollector;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.Services;
import net.minecraft.server.WorldLoader;
import net.minecraft.server.WorldStem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.LoggingLevelLoadListener;
import net.minecraft.server.notifications.EmptyNotificationService;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.PlayerList;
import net.minecraft.util.Util;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.debugchart.LocalSampleLogger;
import net.minecraft.util.debugchart.SampleLogger;
import net.minecraft.world.Difficulty;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.minecraft.world.phys.AABB;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

public final class SeedEsp extends Module {

    private record Ore(String label, int color, Block... blocks) {}

    private static final Ore[] ORES = {
            new Ore("Diamond", 0xFF4DF3FF, Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE),
            new Ore("Iron", 0xFFD8C0A8, Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE),
            new Ore("Gold", 0xFFFFD84D, Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE, Blocks.NETHER_GOLD_ORE),
            new Ore("Coal", 0xFF9AA0A6, Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE),
            new Ore("Copper", 0xFFFF8F4D, Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE),
            new Ore("Redstone", 0xFFFF4D5E, Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE),
            new Ore("Lapis", 0xFF4D7CFF, Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE),
            new Ore("Emerald", 0xFF35E066, Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE),
            new Ore("Quartz", 0xFFE8E2D0, Blocks.NETHER_QUARTZ_ORE),
            new Ore("Debris", 0xFFB06BFF, Blocks.ANCIENT_DEBRIS)
    };

    private static final int ALL_ORES = (1 << ORES.length) - 1;

    private static final Map<Block, Integer> KIND = new IdentityHashMap<>();

    static {
        for (int i = 0; i < ORES.length; i++) for (Block b : ORES[i].blocks()) KIND.put(b, i);
    }

    private record Hit(int x, int y, int z, int kind, boolean open) {}

    private interface Air {
        Boolean at(int x, int y, int z);
    }

    private record ChunkHits(Hit[] hits) {}

    private record Marker(Hit hit, double dist2, boolean exact, int rank) {}

    private record Vein(int kind, int count, double dist2, boolean exact, AABB box) {}

    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    private static final String[] BOX_MODES = {"Every block", "Whole vein", "Blocks and veins"};

    private final Setting.Text seed = add(new Setting.Text("Seed",
            "The world seed, as a number (or any text, the game turns it into the same number it used). "
                    + "Leave it empty to only show the chunks you have loaded.", "", 64));

    private final Setting.Num range = add(new Setting.Num("Range",
            "How far around you the boxes are drawn, in blocks.", 128, 48, 512, 16, " blocks"));

    private final Setting.Num scanChunks = add(new Setting.Num("Scan range",
            "At least this many chunks around you are generated from the seed. The display Range is always covered "
                    + "as well. Bigger sees further but takes longer the first time; what it finds is cached, so it is "
                    + "only slow once per area.", 10, 2, 32, 2, " chunks"));

    private final Setting.Bool predict = add(new Setting.Bool("Seed prediction",
            "Work out the ore in chunks you have not loaded from the seed, with a throwaway copy of the world generator. "
                    + "Leave it off to only ever show real, seen blocks.", true));

    private final Setting.Bool follow = add(new Setting.Bool("Follow me",
            "Start the seed scan again around you when you walk out of the area it covers.", true)
            .showIf(() -> predict.on()));

    private final Setting.Num minY = add(new Setting.Num("Min Y",
            "Hide ores below this height. -64 shows everything.", -64, -64, 320, 4, ""));

    private final Setting.Num maxY = add(new Setting.Num("Max Y",
            "Hide ores above this height. 320 shows everything.", 320, -64, 320, 4, ""));

    private final Setting.Num perOre = add(new Setting.Num("Nearest per ore",
            "How many of each ore to draw, nearest first. Every ore family gets this many, so coal can never hide "
                    + "the diamonds.", 40, 5, 400, 5, ""));

    private final Setting.Choice boxMode = add(new Setting.Choice("Boxes",
            "Every block: one box per ore block. Whole vein: one box around a connected run of one ore, labelled with "
                    + "how many blocks it has, which is what tells you a vein is worth digging. Blocks and veins: both.",
            1, BOX_MODES));

    private final Setting.Num minVein = add(new Setting.Num("Smallest vein",
            "Hide veins with fewer blocks than this, so only runs worth digging to get a box. 1 keeps every vein.",
            1, 1, 64, 1, "").showIf(() -> boxMode.index() != 0));

    private final Setting.Bool exposed = add(new Setting.Bool("Touching air",
            "Only mark ore with at least one air side, so you see what you can walk up to instead of every buried "
                    + "block. Ore beside a chunk nothing has looked at yet counts as open, so nothing real is hidden.",
            false));

    private final Setting.Bool labels = add(new Setting.Bool("Labels",
            "Name and distance on the nearest finds of each ore.", true));

    private final Setting.Num showDistance = add(new Setting.Num("Label distance",
            "Only finds this close get a name tag, which keeps the screen readable.", 48, 8, 192, 8, " blocks"));

    private final Setting.Bool[] ores = new Setting.Bool[ORES.length];

    private final EspKit kit = new EspKit(this, 1, false);

    private static final String[] CORNERS = {"Top left", "Top right", "Bottom left", "Bottom right"};

    private final Setting.Bool panel = add(new Setting.Bool("Ore counter",
            "A small panel with how many of each ore are on screen.", true));

    private final Setting.Choice corner = add(new Setting.Choice("Counter corner",
            "Which corner the ore counter sits in.", 1, CORNERS).showIf(() -> panel.on()));

    private final Setting.Bool remember = add(new Setting.Bool("Remember per world",
            "Keep one seed for every server and save, and put it back by itself when you return to it. "
                    + "Saved in config/zeus-seeds.json.", true));

    private final Setting.Action worlds = add(new Setting.Action("Seed worlds",
            "Every server and save with a remembered seed. Pick one to use it, or forget it from the list.",
            this::openSeedWorlds));

    private final Setting.Action rescan = add(new Setting.Action("Scan from seed now",
            "Throws away what was cached for this world and scans the seed around you again from scratch.",
            this::restartScan));

    private final Setting.Action forget = add(new Setting.Action("Forget this world's seed",
            "Removes the seed remembered for the world you are in now.", this::forgetCurrentWorld));

    private final Setting.Action clear = add(new Setting.Action("Clear results",
            "Throws away everything found so far.", this::clearResults));

    private final Setting.Info status = add(new Setting.Info("Status", this::statusLines));

    private final Map<Long, ChunkHits> exact = new ConcurrentHashMap<>();

    private List<ChunkPos> ring = List.of();
    private int ringCursor;
    private long ringCentre = Long.MIN_VALUE;
    private int ringRadius = -1;

    private volatile SeedJob job;
    private final Map<String, String> remembered = new LinkedHashMap<>();
    private boolean seedsLoaded;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private String lastSeed = "";
    private String lastWorld = "";
    private long seedSettledAt;
    private long lastStartAt;
    private String failedSeed = "";
    private boolean dropCacheOnce;

    private List<Marker> shown = List.of();
    private long shownAt;
    private double shownX, shownY, shownZ;
    private int shownMask = -1;
    private SeedJob shownJob;
    private long shownVersion;
    private int shownSeedChunks = -1;
    private volatile long dataVersion;
    private final int[] panelCounts = new int[ORES.length];
    private final long[] nearest = new long[ORES.length];
    private List<Vein> shownVeins = List.of();
    private String panelState = "";

    public SeedEsp() {
        super("seedesp", "Seed ESP",
                "Every ore around you: real blocks from the chunks you have loaded, and the rest worked out from the "
                        + "world seed with a throwaway copy of the world generator. Each ore keeps its own share of the "
                        + "boxes, so the valuable ones are never hidden behind a wall of coal.",
                Category.RENDER);
        for (int i = 0; i < ORES.length; i++) {
            Setting.Bool on = new Setting.Bool(ORES[i].label(),
                    "Mark " + ORES[i].label().toLowerCase(Locale.ROOT) + ".", true);
            on.onChange(this::invalidate);
            ores[i] = add(on);
            nearest[i] = Long.MIN_VALUE;
        }
    }

    @Override
    protected void onEnable() {
        clearResults();
        seedSettledAt = 0;
        applyRememberedSeed();
        lastWorld = worldKey(Minecraft.getInstance());
        Zeus.toast("Seed ESP: reading the chunks around you", true);
    }

    @Override
    protected void onDisable() {
        cancelJob();
        exact.clear();
        ring = List.of();
        ringCentre = Long.MIN_VALUE;
        invalidate();
    }

    @Override
    protected void cleanSlate() {
        cancelJob();
        exact.clear();
        ring = List.of();
        ringCentre = Long.MIN_VALUE;
        invalidate();
        applyRememberedSeed();
        lastWorld = worldKey(Minecraft.getInstance());
    }

    @Override
    protected void onTick(Minecraft mc) {
        if (mc.level == null || mc.player == null) return;

        scanLoadedChunks(mc);
        seedTick(mc);
    }

    @Override
    public String info() {
        SeedJob j = job;
        String state;
        if (j == null) state = seed.get().isBlank() ? "no seed" : "idle";
        else if (j.failed) state = "seed scan failed";
        else if (!j.finished) state = j.total <= 0 ? "starting worldgen" : j.done + "/" + j.total + " chunks";
        else state = "seed ready";
        int found = 0;
        for (int c : panelCounts) found += c;
        return found + " shown · " + state;
    }

    private List<String> statusLines() {
        List<String> out = new ArrayList<>();
        SeedJob j = job;
        out.add("Real blocks: " + countHits(exact) + " from " + exact.size() + " loaded chunk(s)");
        if (j == null) {
            out.add("Seed scan: not started");
        } else if (j.failed) {
            out.add("Seed scan: failed");
            if (!j.message.isBlank()) out.add(j.message);
        } else if (!j.finished) {
            out.add("Seed scan: " + j.done + "/" + (j.total <= 0 ? "?" : j.total) + " chunks · " + j.dimension);
        } else {
            out.add("Seed scan: " + countHits(j.hits) + " ore(s) from " + j.hits.size() + " chunk(s) · " + j.dimension);
            if (j.fromCache) out.add("Loaded from the cache, no generation needed");
        }
        if (!lastSeed.isBlank()) out.add("Seed: " + lastSeed);
        int shownTotal = 0;
        for (int c : panelCounts) shownTotal += c;
        out.add("On screen: " + shownTotal + " box(es)");
        return out;
    }

    private static int countHits(Map<Long, ChunkHits> map) {
        int n = 0;
        for (ChunkHits c : map.values()) n += c.hits().length;
        return n;
    }

    private void invalidate() {
        shownAt = 0;
    }

    private void clearResults() {
        cancelJob();
        exact.clear();
        invalidate();
        for (int i = 0; i < panelCounts.length; i++) panelCounts[i] = 0;
        panelState = "";
    }

    private void restartScan() {
        dropCacheOnce = true;
        cancelJob();
        failedSeed = "";
        seedSettledAt = 0;
        lastStartAt = 0;
        exact.clear();
        invalidate();
        Zeus.toast("Seed ESP: rescanning this world from the seed", true);
    }

    private void scanLoadedChunks(Minecraft mc) {
        int radius = Math.max(1, (int) Math.round(range.get() / 16.0));
        int cx = mc.player.getBlockX() >> 4;
        int cz = mc.player.getBlockZ() >> 4;
        long centre = ChunkPos.asLong(cx, cz);
        if (centre != ringCentre || radius != ringRadius) {
            ringCentre = centre;
            ringRadius = radius;
            ringCursor = 0;
            ring = buildRing(cx, cz, radius);
            Set<Long> keep = new HashSet<>();
            for (ChunkPos p : ring) keep.add(p.toLong());
            if (exact.keySet().retainAll(keep)) invalidate();
        }

        int budget = 3;
        for (int n = 0; n < budget && !ring.isEmpty(); n++) {
            ChunkPos pos = ring.get(ringCursor);
            ringCursor = (ringCursor + 1) % ring.size();
            scanLoadedChunk(mc, pos);
        }
    }

    private static List<ChunkPos> buildRing(int cx, int cz, int radius) {
        List<ChunkPos> out = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius + radius) continue;
                out.add(new ChunkPos(cx + dx, cz + dz));
            }
        }
        out.sort(Comparator.comparingInt(p -> (p.x - cx) * (p.x - cx) + (p.z - cz) * (p.z - cz)));
        return out;
    }

    private void scanLoadedChunk(Minecraft mc, ChunkPos pos) {
        long key = pos.toLong();
        LevelChunk chunk;
        try {
            chunk = mc.level.getChunkSource().getChunk(pos.x, pos.z, ChunkStatus.FULL, false);
        } catch (RuntimeException ignored) {
            chunk = null;
        }
        if (chunk == null) {
            if (exact.remove(key) != null) dataVersion++;
            return;
        }
        List<Hit> hits = new ArrayList<>();
        readChunk(chunk, ALL_ORES, hits, (x, y, z) -> {
            var level = mc.level;
            if (level == null || !level.hasChunk(x >> 4, z >> 4)) return null;
            return level.getBlockState(new BlockPos(x, y, z)).isAir();
        });
        ChunkHits before = exact.put(key, new ChunkHits(hits.toArray(new Hit[0])));
        if (before == null || before.hits().length != hits.size()) dataVersion++;
    }

    private static void readChunk(LevelChunk chunk, int mask, List<Hit> out, Air air) {
        LevelChunkSection[] sections = chunk.getSections();
        int minY = chunk.getMinY();
        ChunkPos pos = chunk.getPos();
        int minX = pos.getMinBlockX();
        int minZ = pos.getMinBlockZ();
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            if (section == null || section.hasOnlyAir()) continue;
            if (!section.maybeHas(state -> KIND.containsKey(state.getBlock()))) continue;
            int baseY = minY + (i << 4);
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        Integer kind = KIND.get(section.getBlockState(x, y, z).getBlock());
                        if (kind == null || (mask & (1 << kind)) == 0) continue;
                        int wx = minX + x, wy = baseY + y, wz = minZ + z;
                        out.add(new Hit(wx, wy, wz, kind, touchesAir(air, wx, wy, wz)));
                    }
                }
            }
        }
    }

    private static boolean touchesAir(Air air, int x, int y, int z) {
        Boolean side = air.at(x + 1, y, z);
        if (side == null || side) return true;
        side = air.at(x - 1, y, z);
        if (side == null || side) return true;
        side = air.at(x, y + 1, z);
        if (side == null || side) return true;
        side = air.at(x, y - 1, z);
        if (side == null || side) return true;
        side = air.at(x, y, z + 1);
        if (side == null || side) return true;
        side = air.at(x, y, z - 1);
        return side == null || side;
    }

    private int oreMask() {
        int mask = 0;
        for (int i = 0; i < ores.length; i++) if (ores[i].on()) mask |= 1 << i;
        return mask;
    }

    private static String worldKey(Minecraft mc) {
        ServerData server = mc.getCurrentServer();
        if (server != null && server.ip != null && !server.ip.isBlank()) {
            return "server:" + server.ip.trim().toLowerCase(Locale.ROOT);
        }
        IntegratedServer single = mc.getSingleplayerServer();
        if (single != null) {
            try {
                String name = single.getWorldData().getLevelName();
                if (name != null && !name.isBlank()) return "save:" + name;
            } catch (Throwable ignored) {
            }
        }
        return "";
    }

    private static Path seedFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("zeus-seeds.json");
    }

    private void applyRememberedSeed() {
        if (!remember.on() || !seed.get().isBlank()) return;
        try {
            String key = worldKey(Minecraft.getInstance());
            if (key.isEmpty()) return;
            loadSeeds();
            String saved = remembered.get(key);
            if (saved != null && !saved.isBlank()) {
                seed.set(saved);
                Zeus.LOG.info("Seed ESP: using the seed remembered for {}", key);
            }
        } catch (Throwable t) {
            Zeus.LOG.warn("Seed ESP: could not restore the seed for this world", t);
        }
    }

    private void rememberSeed(String text) {
        if (!remember.on()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        try {
            String key = worldKey(mc);
            if (key.isEmpty()) return;
            loadSeeds();
            if (text.isBlank()) {
                if (remembered.remove(key) != null) saveSeeds();
                return;
            }
            if (text.equals(remembered.get(key))) return;
            remembered.put(key, text);
            saveSeeds();
        } catch (Throwable t) {
            Zeus.LOG.warn("Seed ESP: could not remember the seed for this world", t);
        }
    }

    private void forgetCurrentWorld() {
        try {
            String key = worldKey(Minecraft.getInstance());
            if (key.isEmpty()) return;
            forgetRemembered(key);
        } catch (Throwable t) {
            Zeus.LOG.warn("Seed ESP: could not forget this world's seed", t);
        }
    }

    private void openSeedWorlds() {
        Minecraft mc = Minecraft.getInstance();
        loadSeeds();
        mc.setScreen(new SeedWorldsScreen(mc.screen, this::seedWorldRows, this::useRemembered, this::forgetRemembered));
    }

    private List<SeedWorldsScreen.Row> seedWorldRows() {
        String here = worldKey(Minecraft.getInstance());
        List<SeedWorldsScreen.Row> out = new ArrayList<>();
        for (Map.Entry<String, String> entry : remembered.entrySet()) {
            out.add(new SeedWorldsScreen.Row(entry.getKey(), worldLabel(entry.getKey()), entry.getValue(),
                    entry.getKey().equals(here)));
        }
        out.sort(Comparator.comparing((SeedWorldsScreen.Row row) -> !row.here())
                .thenComparing(SeedWorldsScreen.Row::label, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    private static String worldLabel(String key) {
        int split = key.indexOf(':');
        String name = split >= 0 ? key.substring(split + 1) : key;
        return name.isBlank() ? key : name;
    }

    private void useRemembered(String key) {
        String saved = remembered.get(key);
        if (saved == null || saved.isBlank()) return;
        seed.set(saved);
        failedSeed = "";
        lastSeed = "";
        seedSettledAt = 0;
        Zeus.toast("Seed ESP: now using the seed saved for " + worldLabel(key), true);
    }

    private void forgetRemembered(String key) {
        if (remembered.remove(key) == null) return;
        saveSeeds();
        if (key.equals(worldKey(Minecraft.getInstance()))) {
            seed.set("");
            failedSeed = "";
            lastSeed = "";
            seedSettledAt = 0;
            cancelJob();
        }
        Zeus.toast("Seed ESP: forgot the seed for " + worldLabel(key), true);
    }

    private void restoreSeedFor(String key) {
        loadSeeds();
        String saved = remembered.get(key);
        String current = seed.get().trim();
        if (saved != null && !saved.isBlank()) {
            if (saved.equals(current)) return;
            seed.set(saved);
            failedSeed = "";
            lastSeed = "";
            seedSettledAt = 0;
            Zeus.LOG.info("Seed ESP: restored the seed saved for {}", key);
        } else if (!current.isEmpty()) {
            seed.set("");
            failedSeed = "";
            lastSeed = "";
            seedSettledAt = 0;
        }
    }

    private void loadSeeds() {
        if (seedsLoaded) return;
        seedsLoaded = true;
        try {
            Path file = seedFile();
            if (!Files.exists(file)) return;
            JsonElement root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            if (!root.isJsonObject()) return;
            JsonObject worlds = root.getAsJsonObject().getAsJsonObject("worlds");
            if (worlds == null) return;
            for (Map.Entry<String, JsonElement> entry : worlds.entrySet()) {
                if (entry.getValue().isJsonPrimitive()) remembered.put(entry.getKey(), entry.getValue().getAsString());
            }
        } catch (Exception e) {
            Zeus.LOG.warn("Seed ESP: could not read the remembered seeds", e);
        }
    }

    private void saveSeeds() {
        try {
            Path file = seedFile();
            JsonObject worlds = new JsonObject();
            for (Map.Entry<String, String> entry : remembered.entrySet()) {
                worlds.addProperty(entry.getKey(), entry.getValue());
            }
            JsonObject root = new JsonObject();
            root.add("worlds", worlds);
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Zeus.LOG.warn("Seed ESP: could not save the remembered seeds", e);
        }
    }

    private void seedTick(Minecraft mc) {
        long now = System.currentTimeMillis();
        if (remember.on()) {
            String world = worldKey(mc);
            if (!world.isEmpty() && !world.equals(lastWorld)) {
                lastWorld = world;
                restoreSeedFor(world);
            }
        }
        String text = seed.get().trim();
        if (!text.equals(lastSeed)) {
            lastSeed = text;
            seedSettledAt = now;
            failedSeed = "";
        }
        if (now - seedSettledAt > 900) rememberSeed(text);

        ResourceKey<Level> dim = mc.level.dimension();
        boolean supported = dim.equals(Level.OVERWORLD) || dim.equals(Level.NETHER);
        SeedJob j = job;

        if (j != null) {
            boolean stale = !predict.on() || !supported || !j.dimension.equals(dim) || !j.seedText.equals(text)
                    || j.radiusChunks != scanRadius(dim) || (j.follow && follow.on() && walkedAway(mc, j));
            if (stale) {
                cancelJob();
                j = null;
            }
        }

        if (j != null && j.failed) {
            failedSeed = j.seedText + "|" + j.dimension.identifier();
            cancelJob();
            j = null;
        }

        if (j != null && j.finished) {
            if (!j.follow || !follow.on() || !walkedAway(mc, j)) return;
            cancelJob();
            j = null;
        }

        if (j != null) return;
        if (!predict.on() || !supported) return;
        if (text.isBlank() || text.equals(failedSeed)) return;
        if (!failedSeed.isEmpty() && failedSeed.equals(text + "|" + dim.identifier())) return;
        if (now - seedSettledAt < 700 || now - lastStartAt < 4000) return;

        OptionalLong parsed;
        try {
            parsed = WorldOptions.parseSeed(text);
        } catch (RuntimeException e) {
            return;
        }
        if (parsed.isEmpty()) return;

        startJob(parsed.getAsLong(), text, dim, mc);
    }

    private int scanRadius(ResourceKey<Level> dim) {
        int wanted = Math.max(2, (int) Math.round(scanChunks.get()));
        wanted = Math.max(wanted, (int) Math.ceil(range.get() / 16.0));
        if (dim.equals(Level.NETHER)) wanted = Math.min(wanted, 12);
        return Math.min(32, wanted);
    }

    private static boolean walkedAway(Minecraft mc, SeedJob j) {
        double dx = mc.player.getX() - j.originX;
        double dz = mc.player.getZ() - j.originZ;
        double limit = Math.max(48.0, j.radiusChunks * 16.0 * 0.6);
        return dx * dx + dz * dz > limit * limit;
    }

    private void startJob(long seedValue, String text, ResourceKey<Level> dim, Minecraft mc) {
        cancelJob();
        SeedJob j = new SeedJob(seedValue, text, dim, mc.player.getBlockX(), mc.player.getBlockZ(),
                scanRadius(dim), follow.on(), dropCacheOnce, mc);
        dropCacheOnce = false;
        job = j;
        lastStartAt = System.currentTimeMillis();
        invalidate();
        Zeus.LOG.info("Seed ESP: {} chunk(s) around {}, {} in {} with seed {}",
                j.radiusChunks, j.originX, j.originZ, dim.identifier(), text);
        j.start();
    }

    private void cancelJob() {
        SeedJob j = job;
        job = null;
        if (j != null) j.cancel();
        invalidate();
    }

    private static final class OreCache {
        static final int MAGIC = 0x5A455553;
        static final int VERSION = 2;
        static final int MAX_CHUNKS = 24000;

        final Map<Long, Hit[]> chunks = new ConcurrentHashMap<>();

        static Path dir() {
            return FabricLoader.getInstance().getConfigDir().resolve("zeus").resolve("ores");
        }

        static Path file(String world, String seedText, ResourceKey<Level> dim) {
            String base = (world + "|" + seedText + "|" + dim.identifier()).toLowerCase(Locale.ROOT);
            String safe = world.replaceAll("[^A-Za-z0-9._-]", "_");
            if (safe.length() > 32) safe = safe.substring(0, 32);
            if (safe.isEmpty()) safe = "world";
            return dir().resolve(safe + "-" + Integer.toHexString(base.hashCode()) + "-"
                    + dim.identifier().getPath().replace('/', '_') + ".bin");
        }

        static OreCache read(Path file) {
            OreCache cache = new OreCache();
            if (file == null || !Files.exists(file)) return cache;
            try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
                if (in.readInt() != MAGIC) return cache;
                int version = in.readInt();
                if (version != 1 && version != VERSION) return cache;
                int count = in.readInt();
                if (count < 0 || count > MAX_CHUNKS * 2) return cache;
                for (int c = 0; c < count; c++) {
                    long key = in.readLong();
                    int n = in.readShort() & 0xFFFF;
                    if (n > 4096) return new OreCache();
                    Hit[] hits = new Hit[n];
                    for (int i = 0; i < n; i++) {
                        int raw = in.readByte();
                        int x = in.readInt();
                        int y = in.readShort();
                        int z = in.readInt();
                        boolean open = version < 2 || (raw & 0x80) != 0;
                        int kind = raw & 0x7F;
                        if (kind >= ORES.length) {
                            hits[i] = new Hit(0, -9999, 0, 0, true);
                            continue;
                        }
                        hits[i] = new Hit(x, y, z, kind, open);
                    }
                    cache.chunks.put(key, hits);
                }
            } catch (Exception e) {
                Zeus.LOG.warn("Seed ESP: could not read the ore cache, scanning the seed again", e);
                cache.chunks.clear();
            }
            return cache;
        }

        void write(Path file, int nearX, int nearZ) {
            if (file == null) return;
            Path temp = null;
            try {
                List<Map.Entry<Long, Hit[]>> entries = new ArrayList<>(chunks.entrySet());
                if (entries.size() > MAX_CHUNKS) {
                    entries.sort(Comparator.comparingLong(e -> {
                        int cx = ChunkPos.getX(e.getKey()) * 16 + 8;
                        int cz = ChunkPos.getZ(e.getKey()) * 16 + 8;
                        long dx = cx - nearX;
                        long dz = cz - nearZ;
                        return dx * dx + dz * dz;
                    }));
                    entries = entries.subList(0, MAX_CHUNKS);
                }
                if (file.getParent() != null) Files.createDirectories(file.getParent());
                temp = file.resolveSibling(file.getFileName() + ".tmp");
                try (DataOutputStream out = new DataOutputStream(
                        new BufferedOutputStream(Files.newOutputStream(temp)))) {
                    out.writeInt(MAGIC);
                    out.writeInt(VERSION);
                    out.writeInt(entries.size());
                    for (Map.Entry<Long, Hit[]> entry : entries) {
                        Hit[] hits = entry.getValue();
                        out.writeLong(entry.getKey());
                        out.writeShort(hits.length);
                        for (Hit hit : hits) {
                            out.writeByte(hit.kind() | (hit.open() ? 0x80 : 0));
                            out.writeInt(hit.x());
                            out.writeShort(hit.y());
                            out.writeInt(hit.z());
                        }
                    }
                }
                try {
                    Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    Files.copy(temp, file, StandardCopyOption.REPLACE_EXISTING);
                    Files.deleteIfExists(temp);
                }
            } catch (Exception e) {
                Zeus.LOG.warn("Seed ESP: could not save the ore cache", e);
                if (temp != null) {
                    try {
                        Files.deleteIfExists(temp);
                    } catch (IOException ignored) {
                    }
                }
            }
        }
    }

    private static final class SeedJob {
        final long seed;
        final String seedText;
        final ResourceKey<Level> dimension;
        final int originX;
        final int originZ;
        final int radiusChunks;
        final boolean follow;
        final boolean dropCache;
        final Path cacheFile;
        final long startedAt = System.currentTimeMillis();

        final Map<Long, ChunkHits> hits = new ConcurrentHashMap<>();

        volatile boolean cancelled;
        volatile boolean finished;
        volatile boolean failed;
        volatile boolean fromCache;
        volatile String message = "";
        volatile int done;
        volatile int baseDone;
        volatile int total;
        volatile int generated;

        SeedJob(long seed, String seedText, ResourceKey<Level> dimension, int originX, int originZ, int radiusChunks,
                boolean follow, boolean dropCache, Minecraft mc) {
            this.seed = seed;
            this.seedText = seedText;
            this.dimension = dimension;
            this.originX = originX;
            this.originZ = originZ;
            this.radiusChunks = radiusChunks;
            this.follow = follow;
            this.dropCache = dropCache;
            String world;
            try {
                world = worldKey(mc);
            } catch (Throwable t) {
                world = "";
            }
            this.cacheFile = OreCache.file(world, seedText, dimension);
        }

        void start() {
            Thread thread = new Thread(this::run, "Zeus seed scanner");
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY);
            thread.start();
        }

        void cancel() {
            cancelled = true;
        }

        void publish(ChunkPos pos, Hit[] found) {
            if (cancelled) return;
            hits.put(pos.toLong(), new ChunkHits(found));
        }

        private void run() {
            OreCache cache = null;
            Path dir = null;
            try {
                cache = dropCache ? new OreCache() : OreCache.read(cacheFile);
                if (dropCache) deleteTree(cacheFile);

                List<ChunkPos> missing = new ArrayList<>();
                for (ChunkPos pos : circle(originX >> 4, originZ >> 4, radiusChunks)) {
                    Hit[] known = cache.chunks.get(pos.toLong());
                    if (known != null) {
                        hits.put(pos.toLong(), new ChunkHits(known));
                        done++;
                    } else {
                        missing.add(pos);
                    }
                }
                total = done + missing.size();
                baseDone = done;
                fromCache = missing.isEmpty();
                if (fromCache) {
                    Zeus.LOG.info("Seed ESP: {} chunk(s) came from the cache, nothing to generate", hits.size());
                    return;
                }

                dir = Files.createTempDirectory("zeus-seed-");
                final Path workDir = dir;
                LevelStorageSource storage = LevelStorageSource.createDefault(dir);
                try (LevelStorageSource.LevelStorageAccess access = storage.createAccess("zeus-search")) {
                    PackRepository packs = ServerPacksSource.createPackRepository(access);
                    packs.reload();
                    WorldStem stem = openStem(packs, seed);
                    try {
                        ScanServer server = MinecraftServer.spin(thread ->
                                new ScanServer(thread, access, packs, stem, this, missing, workDir));
                        server.getRunningThread().join(300_000L);
                        if (server.getRunningThread().isAlive()) {
                            failed = true;
                            message = "The seed scan took too long and was stopped";
                            server.halt(false);
                            server.getRunningThread().join(8_000L);
                        } else if (server.failure != null) {
                            failed = true;
                            message = server.failure.getMessage();
                        }
                    } finally {
                        stem.close();
                    }
                }
            } catch (Throwable t) {
                failed = true;
                message = t.getClass().getSimpleName() + (t.getMessage() == null ? "" : ": " + t.getMessage());
                Zeus.LOG.warn("Seed ESP: the local seed scan failed", t);
            } finally {
                if (cache != null) {
                    for (Map.Entry<Long, ChunkHits> entry : hits.entrySet()) {
                        cache.chunks.put(entry.getKey(), entry.getValue().hits());
                    }
                    if (generated > 0 && !cache.chunks.isEmpty()) {
                        cache.write(cacheFile, originX, originZ);
                    }
                }
                finished = true;
                deleteTree(dir);
            }
        }

        static List<ChunkPos> circle(int cx, int cz, int radius) {
            List<ChunkPos> out = new ArrayList<>();
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dz * dz > radius * radius + radius) continue;
                    out.add(new ChunkPos(cx + dx, cz + dz));
                }
            }
            out.sort(Comparator.comparingInt(p -> (p.x - cx) * (p.x - cx) + (p.z - cz) * (p.z - cz)));
            return out;
        }

        private static WorldStem openStem(PackRepository packs, long seed) throws Exception {
            WorldDataConfiguration config = new WorldDataConfiguration(
                    new DataPackConfig(List.of("vanilla"), List.of()), FeatureFlags.DEFAULT_FLAGS);
            LevelSettings settings = new LevelSettings("Zeus seed scan", GameType.CREATIVE, false, Difficulty.NORMAL,
                    false, new GameRules(FeatureFlags.DEFAULT_FLAGS), config);
            WorldLoader.PackConfig packConfig = new WorldLoader.PackConfig(packs, config, false, true);
            WorldLoader.InitConfig init = new WorldLoader.InitConfig(packConfig, Commands.CommandSelection.DEDICATED,
                    LevelBasedPermissionSet.OWNER);
            return Util.blockUntilDone(executor -> WorldLoader.load(init,
                    context -> dataOutput(context, settings, seed), WorldStem::new,
                    Util.backgroundExecutor(), executor)).get();
        }

        private static WorldLoader.DataLoadOutput<PrimaryLevelData> dataOutput(WorldLoader.DataLoadContext context,
                                                                              LevelSettings settings, long seed) {
            Registry<LevelStem> stems = new MappedRegistry<>(Registries.LEVEL_STEM, Lifecycle.stable()).freeze();
            HolderLookup.RegistryLookup<WorldPreset> presets =
                    context.datapackWorldgen().lookupOrThrow(Registries.WORLD_PRESET);
            WorldDimensions.Complete dimensions = presets.getOrThrow(WorldPresets.NORMAL).value()
                    .createWorldDimensions().bake(stems);
            WorldOptions options = new WorldOptions(seed, true, false);
            PrimaryLevelData data = new PrimaryLevelData(settings, options,
                    dimensions.specialWorldProperty(), dimensions.lifecycle());
            return new WorldLoader.DataLoadOutput<>(data, dimensions.dimensionsRegistryAccess());
        }

        private static void deleteTree(Path dir) {
            if (dir == null) return;
            for (int attempt = 0; attempt < 3; attempt++) {
                try {
                    if (!Files.exists(dir)) return;
                    try (var walk = Files.walk(dir)) {
                        walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (IOException ignored) {
                            }
                        });
                    }
                    if (!Files.exists(dir)) return;
                } catch (IOException ignored) {
                }
                try {
                    Thread.sleep(250L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private static final class ScanServer extends MinecraftServer {
        private static final int CHUNKS_PER_TICK = 3;
        private static final AtomicBoolean GIZMOS = new AtomicBoolean(false);

        private final LocalSampleLogger tickLogger = new LocalSampleLogger(4);
        private final IdentityHashMap<DataResourceStore.Key<?>, Object> resources = new IdentityHashMap<>();
        private final SeedJob job;
        private final List<ChunkPos> queue;
        private int cursor;
        private volatile RuntimeException failure;

        ScanServer(Thread thread, LevelStorageSource.LevelStorageAccess access, PackRepository packs, WorldStem stem,
                   SeedJob job, List<ChunkPos> queue, Path dir) {
            super(thread, access, packs, stem, java.net.Proxy.NO_PROXY, DataFixers.getDataFixer(),
                    makeServices(dir), LoggingLevelLoadListener.forDedicatedServer());
            this.job = job;
            this.queue = queue;
        }

        private static Services makeServices(Path dir) {
            try {
                return Services.create(
                        new com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService(java.net.Proxy.NO_PROXY),
                        dir.toFile());
            } catch (Throwable t) {
                return null;
            }
        }

        @Override
        public boolean initServer() {
            this.setPlayerList(new PlayerList(this, this.registries(), this.playerDataStorage,
                    new EmptyNotificationService()) {});
            if (GIZMOS.compareAndSet(false, true)) {
                try {
                    Gizmos.withCollector(GizmoCollector.NOOP);
                } catch (Throwable ignored) {
                }
            }
            this.loadLevel();
            for (ServerLevel level : this.getAllLevels()) level.noSave = true;
            return true;
        }

        @Override
        public void tickServer(BooleanSupplier keepGoing) {
            super.tickServer(keepGoing);
            if (job.cancelled) {
                stop();
                return;
            }
            ServerLevel level = this.getLevel(job.dimension);
            if (level == null) {
                job.failed = true;
                job.message = "That dimension could not be created";
                stop();
                return;
            }
            for (int n = 0; n < CHUNKS_PER_TICK && cursor < queue.size(); n++) {
                ChunkPos pos = queue.get(cursor++);
                try {
                    scan(level, pos);
                } catch (RuntimeException e) {
                    Zeus.LOG.warn("Seed ESP: could not read chunk {},{} of the seed", pos.x, pos.z, e);
                }
                job.done = job.baseDone + cursor;
                if (job.cancelled) break;
            }
            if (cursor >= queue.size()) stop();
        }

        private void stop() {
            this.halt(false);
        }

        private void scan(ServerLevel level, ChunkPos pos) {
            ChunkAccess access = level.getChunkSource().getChunk(pos.x, pos.z, ChunkStatus.FULL, true);
            if (!(access instanceof LevelChunk chunk)) return;
            List<Hit> hits = new ArrayList<>();
            readChunk(chunk, ALL_ORES, hits, (x, y, z) -> {
                LevelChunk neighbour = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
                if (neighbour == null) return null;
                return neighbour.getBlockState(new BlockPos(x, y, z)).isAir();
            });
            job.publish(pos, hits.toArray(new Hit[0]));
            job.generated++;
        }

        @Override
        public void onServerCrash(net.minecraft.CrashReport report) {
            this.failure = new IllegalStateException(report.getTitle());
            Zeus.LOG.warn("Seed ESP: the throwaway world generator crashed", report.getException());
            stop();
        }

        @Override
        protected void waitUntilNextTick() {
            this.runAllTasks();
        }

        @Override
        protected SampleLogger getTickTimeLogger() {
            return this.tickLogger;
        }

        @Override
        public boolean isTickTimeLoggingEnabled() {
            return false;
        }

        @Override
        public SystemReport fillServerSystemReport(SystemReport report) {
            report.setDetail("Type", "Zeus seed scanner");
            return report;
        }

        @Override
        public LevelBasedPermissionSet operatorUserPermissions() {
            return LevelBasedPermissionSet.OWNER;
        }

        @Override
        public PermissionSet getFunctionCompilationPermissions() {
            return LevelBasedPermissionSet.OWNER;
        }

        @Override
        public boolean shouldRconBroadcast() {
            return false;
        }

        @Override
        public boolean isDedicatedServer() {
            return false;
        }

        @Override
        public int getRateLimitPacketsPerSecond() {
            return 0;
        }

        @Override
        public boolean useNativeTransport() {
            return false;
        }

        @Override
        public boolean isPublished() {
            return false;
        }

        @Override
        public boolean shouldInformAdmins() {
            return false;
        }

        @Override
        public boolean isSingleplayerOwner(NameAndId profile) {
            return false;
        }

        @Override
        public int getMaxPlayers() {
            return 1;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getOrThrow(DataResourceStore.Key<T> key) {
            return (T) this.resources.get(key);
        }
    }

    @Override
    protected void onHudWorld(GuiGraphics g, Minecraft mc, float partial, int w, int h) {
        if (mc.level == null || mc.player == null) return;
        markers(mc);
        int mode = boxMode.index();
        boolean blocks = mode == 0 || mode == 2;
        boolean veins = mode == 1 || mode == 2;
        if ((!blocks || shown.isEmpty()) && (!veins || shownVeins.isEmpty())) return;
        if (!Projector.prepare(mc, partial, w, h)) return;
        if (veins) drawVeins(g, mc, w, h);
        if (blocks) drawBlocks(g, mc, w, h);
    }

    private void drawBlocks(GuiGraphics g, Minecraft mc, int w, int h) {
        float labelRange = showDistance.f() * showDistance.f();
        int drawn = 0;
        for (int i = shown.size() - 1; i >= 0; i--) {
            Marker marker = shown.get(i);
            Hit hit = marker.hit();
            int color = ORES[hit.kind()].color();
            float alpha = marker.exact() ? 1f : 0.5f;
            AABB box = EspKit.shape(mc, hit.x(), hit.y(), hit.z());
            kit.box(g, box, color, alpha, drawn < 60);
            if (labels.on() && marker.rank() < 4 && marker.dist2() < labelRange) {
                float[] onScreen = new float[2];
                if (Projector.project(hit.x() + 0.5, box.maxY, hit.z() + 0.5, onScreen)) {
                    String name = (marker.exact() ? "" : "~") + ORES[hit.kind()].label();
                    kit.tag(g, name, Math.round(Math.sqrt(marker.dist2())) + "m",
                            Math.round(onScreen[0]), Math.round(onScreen[1]), color, alpha, w, h);
                }
            }
            kit.tracer(g, w, h, hit.x() + 0.5, hit.y() + 0.5, hit.z() + 0.5, color, alpha);
            drawn++;
        }
    }

    private void drawVeins(GuiGraphics g, Minecraft mc, int w, int h) {
        float labelRange = showDistance.f() * showDistance.f();
        int drawn = 0;
        for (int i = shownVeins.size() - 1; i >= 0; i--) {
            Vein vein = shownVeins.get(i);
            int color = ORES[vein.kind()].color();
            float alpha = vein.exact() ? 1f : 0.5f;
            AABB box = vein.box();
            kit.box(g, box, color, alpha, drawn < 45);
            double cx = (box.minX + box.maxX) / 2.0;
            double cz = (box.minZ + box.maxZ) / 2.0;
            if (labels.on() && vein.dist2() < labelRange) {
                float[] onScreen = new float[2];
                if (Projector.project(cx, box.maxY, cz, onScreen)) {
                    String name = (vein.exact() ? "" : "~") + ORES[vein.kind()].label() + " ×" + vein.count();
                    kit.tag(g, name, Math.round(Math.sqrt(vein.dist2())) + "m",
                            Math.round(onScreen[0]), Math.round(onScreen[1]), color, alpha, w, h);
                }
            }
            kit.tracer(g, w, h, cx, (box.minY + box.maxY) / 2.0, cz, color, alpha);
            drawn++;
        }
    }

    @Override
    protected void onHud(GuiGraphics g, Minecraft mc, float partial, int w, int h) {
        if (!panel.on() || mc.player == null) return;
        int rows = 0;
        for (int count : panelCounts) if (count > 0) rows++;
        if (rows == 0 && panelState.isEmpty()) return;

        int lineH = 11;
        int boxW = 158;
        int boxH = lineH * (rows + 1) + 10;
        int ci = corner.index();
        int x = (ci == 1 || ci == 3) ? w - boxW - 6 : 6;
        int y = (ci >= 2) ? h - boxH - 6 : 6;

        Draw.rrect(g, x, y, boxW, boxH, 4, 0xB80B0B10);
        Draw.bold(g, "Ores", x + 7, y + 6, 0xFFFFFFFF);
        if (!panelState.isEmpty()) Draw.textRight(g, panelState, x + boxW - 7, y + 6, 0xFF9AA0A6);

        int yy = y + 6 + lineH;
        for (int i = 0; i < ORES.length; i++) {
            int count = panelCounts[i];
            if (count <= 0) continue;
            Draw.text(g, ORES[i].label(), x + 16, yy, ORES[i].color());
            Draw.rrect(g, x + 7, yy + 2, 4, 4, 1, ORES[i].color());
            Draw.textRight(g, count + "  " + nearestText(mc, nearest[i]), x + boxW - 7, yy, 0xFFE6E6E6);
            yy += lineH;
        }
    }

    private List<Marker> markers(Minecraft mc) {
        SeedJob j = job;
        int mask = oreMask();
        long now = System.currentTimeMillis();
        double px = mc.player.getX(), py = mc.player.getY(), pz = mc.player.getZ();
        double moved = (px - shownX) * (px - shownX) + (py - shownY) * (py - shownY) + (pz - shownZ) * (pz - shownZ);
        int seedChunks = j == null ? -1 : j.hits.size();
        if (now - shownAt < 200 && j == shownJob && mask == shownMask && moved < 9.0
                && dataVersion == shownVersion && seedChunks == shownSeedChunks) {
            return shown;
        }

        double r = range.get();
        double r2 = r * r;
        double lowY = Math.min(minY.f(), maxY.f());
        double highY = Math.max(minY.f(), maxY.f());
        boolean openOnly = exposed.on();
        List<Marker> raw = new ArrayList<>();
        Set<Long> realPositions = new HashSet<>();

        for (Map.Entry<Long, ChunkHits> entry : exact.entrySet()) {
            if (chunkOutOfRange(entry.getKey(), px, pz, r2)) continue;
            for (Hit hit : entry.getValue().hits()) {
                if ((mask & (1 << hit.kind())) == 0) continue;
                if (hit.y() < lowY || hit.y() > highY) continue;
                if (openOnly && !hit.open()) continue;
                double d2 = dist2(hit, px, py, pz);
                if (d2 > r2) continue;
                realPositions.add(BlockPos.asLong(hit.x(), hit.y(), hit.z()));
                raw.add(new Marker(hit, d2, true, 0));
            }
        }

        if (j != null && predict.on() && !j.cancelled && mc.level.dimension().equals(j.dimension)) {
            for (Map.Entry<Long, ChunkHits> entry : j.hits.entrySet()) {
                if (exact.containsKey(entry.getKey())) continue;
                if (chunkOutOfRange(entry.getKey(), px, pz, r2)) continue;
                if (mc.level.hasChunk(ChunkPos.getX(entry.getKey()), ChunkPos.getZ(entry.getKey()))) continue;
                for (Hit hit : entry.getValue().hits()) {
                    if ((mask & (1 << hit.kind())) == 0) continue;
                    if (hit.y() < lowY || hit.y() > highY) continue;
                    if (openOnly && !hit.open()) continue;
                    if (realPositions.contains(BlockPos.asLong(hit.x(), hit.y(), hit.z()))) continue;
                    double d2 = dist2(hit, px, py, pz);
                    if (d2 > r2) continue;
                    raw.add(new Marker(hit, d2, false, 0));
                }
            }
        }

        raw.sort(Comparator.comparingDouble(Marker::dist2));

        int[] counted = new int[ORES.length];
        for (int i = 0; i < nearest.length; i++) nearest[i] = Long.MIN_VALUE;
        for (Marker marker : raw) {
            if (openOnly && !marker.hit().open()) continue;
            int kind = marker.hit().kind();
            counted[kind]++;
            if (nearest[kind] == Long.MIN_VALUE) {
                nearest[kind] = BlockPos.asLong(marker.hit().x(), marker.hit().y(), marker.hit().z());
            }
        }
        System.arraycopy(counted, 0, panelCounts, 0, counted.length);
        panelState = stateText(j, mc);

        int cap = perOre.i();
        int[] used = new int[ORES.length];
        List<Marker> kept = new ArrayList<>();
        for (Marker marker : raw) {
            int kind = marker.hit().kind();
            if (used[kind] >= cap) continue;
            kept.add(new Marker(marker.hit(), marker.dist2(), marker.exact(), used[kind]));
            used[kind]++;
        }

        List<Vein> veins = cluster(raw);
        veins.sort(Comparator.comparingDouble(Vein::dist2));
        int[] veinUsed = new int[ORES.length];
        List<Vein> keptVeins = new ArrayList<>();
        for (Vein vein : veins) {
            if (vein.count() < minVein.i()) continue;
            if (veinUsed[vein.kind()] >= cap) continue;
            veinUsed[vein.kind()]++;
            keptVeins.add(vein);
        }

        shown = kept;
        shownVeins = keptVeins;
        shownAt = now;
        shownX = px;
        shownY = py;
        shownZ = pz;
        shownMask = mask;
        shownJob = j;
        shownVersion = dataVersion;
        shownSeedChunks = seedChunks;
        return shown;
    }

    private static List<Vein> cluster(List<Marker> hits) {
        int n = Math.min(hits.size(), 30000);
        if (n == 0) return List.of();
        Map<Long, Integer> index = new HashMap<>(Math.max(16, n * 2));
        for (int i = 0; i < n; i++) {
            Hit h = hits.get(i).hit();
            index.put(BlockPos.asLong(h.x(), h.y(), h.z()), i);
        }
        boolean[] seen = new boolean[n];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        List<Vein> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (seen[i]) continue;
            int kind = hits.get(i).hit().kind();
            int count = 0;
            double best = Double.MAX_VALUE;
            boolean exact = true;
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            seen[i] = true;
            queue.add(i);
            while (!queue.isEmpty()) {
                Marker m = hits.get(queue.poll());
                Hit h = m.hit();
                count++;
                if (m.dist2() < best) best = m.dist2();
                exact &= m.exact();
                if (h.x() < minX) minX = h.x();
                if (h.y() < minY) minY = h.y();
                if (h.z() < minZ) minZ = h.z();
                if (h.x() > maxX) maxX = h.x();
                if (h.y() > maxY) maxY = h.y();
                if (h.z() > maxZ) maxZ = h.z();
                for (int side = 0; side < 6; side++) {
                    int nx = h.x() + (side == 0 ? 1 : side == 1 ? -1 : 0);
                    int ny = h.y() + (side == 2 ? 1 : side == 3 ? -1 : 0);
                    int nz = h.z() + (side == 4 ? 1 : side == 5 ? -1 : 0);
                    Integer nb = index.get(BlockPos.asLong(nx, ny, nz));
                    if (nb == null || seen[nb]) continue;
                    if (hits.get(nb).hit().kind() != kind) continue;
                    seen[nb] = true;
                    queue.add(nb);
                }
            }
            out.add(new Vein(kind, count, best, exact,
                    new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1)));
        }
        return out;
    }

    private static String nearestText(Minecraft mc, long at) {
        if (at == Long.MIN_VALUE || mc.player == null) return "";
        double dx = BlockPos.getX(at) + 0.5 - mc.player.getX();
        double dy = BlockPos.getY(at) + 0.5 - mc.player.getY();
        double dz = BlockPos.getZ(at) + 0.5 - mc.player.getZ();
        int dist = (int) Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz));
        double rel = Math.toDegrees(Math.atan2(-dx, dz)) - mc.player.getYRot();
        rel = ((rel % 360) + 540) % 360 - 180;
        int arrow = Math.floorMod((int) Math.round(rel / 45.0), 8);
        String height = dy < -6 ? "▼" : dy > 6 ? "▲" : "";
        return dist + "m " + ARROWS[arrow] + height;
    }

    private String stateText(SeedJob j, Minecraft mc) {
        if (j == null) return seed.get().isBlank() ? "no seed" : "";
        if (j.failed) return "scan failed";
        if (!j.finished) {
            if (j.total <= 0) return "starting";
            return j.done + "/" + j.total + (j.fromCache ? "" : " chunks");
        }
        return j.fromCache ? "cached" : "ready";
    }

    private static boolean chunkOutOfRange(long key, double px, double pz, double r2) {
        double dx = (ChunkPos.getX(key) << 4) + 8 - px;
        double dz = (ChunkPos.getZ(key) << 4) + 8 - pz;
        return dx * dx + dz * dz > r2 + 4096.0;
    }

    private static double dist2(Hit hit, double x, double y, double z) {
        double dx = hit.x() + 0.5 - x;
        double dy = hit.y() + 0.5 - y;
        double dz = hit.z() + 0.5 - z;
        return dx * dx + dy * dy + dz * dz;
    }
}
