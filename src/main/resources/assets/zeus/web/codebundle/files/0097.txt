package com.zlorbie788.zeus.mods;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Setting;
import com.zlorbie788.zeus.core.Zeus;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Zeus Brain: what Zeus knows about where you are and how the game is running, shared by every module.
 *
 * <ul>
 * <li><b>Where you are:</b> which server (or "singleplayer"), which dimension, above or below the ground, near the world's centre.</li>
 * <li><b>How the game runs:</b> frames per second are measured, and every scanner gets a time budget that shrinks when the
 * game lags and grows again when it does not.</li>
 * <li><b>What this server is like:</b> how crowded with builds it is (kept per server in {@code zeus-brain.json}), so a
 * server full of builds is judged more strictly than an empty world.</li>
 * </ul>
 */
public final class Brain extends Module {
    /** A snapshot of what Zeus understands right now. */
    public static final class Ctx {
        public String server = "singleplayer";
        public boolean multi;
        public String dim = "overworld";
        public boolean underground;
        public boolean deep;
        public boolean water;
        public int fps = 60;
        public double centerDist;
        public String area = "Surface";
        public float budget = 1f;
        public int surfaceY = Integer.MIN_VALUE;
        public boolean hasSurface = true;
    }

    private static volatile Ctx ctx = new Ctx();
    private static final Map<String, BaseBrain.Baseline> BASELINES = new HashMap<>();
    private static boolean loaded;
    private static Brain self;

    private final Setting.Bool adapt = add(new Setting.Bool("Slow scanners down when the game lags", "Every scanner gets less time per tick when your frame rate is low, and more when it is high.", true));
    private final Setting.Bool remember = add(new Setting.Bool("Learn what each server is like", "Remembers how crowded with builds each server is, so base detection is stricter on busy servers.", true));
    private final Setting.Bool learnPlaystyle = add(new Setting.Bool("Learn from my gameplay", "Privately counts broad gameplay patterns (combat, mining, building, movement and exploration) to build a local playstyle profile. No chat or personal text is read.", true));
    private final Setting.Num floor = add(new Setting.Num("Never go below", "The smallest share of the normal scan time a scanner may get.", 25, 10, 100, 5, " %")).showIf(() -> this.adapt.on());
    private final Setting.Info know = add(new Setting.Info("What Zeus understands", this::lines));
    private final Setting.Info playstyle = add(new Setting.Info("Learned playstyle", this::playstyleLines));
    private final long[] playCounts = new long[5];
    private boolean playstyleLoaded;

    private long frameAt;
    private int frames;
    private long second;
    private int ticks;
    private boolean saveNeeded;
    private long saveAt;

    public Brain() {
        super("brain", "Zeus Brain", "Understands where you are and how the game runs, and tunes every scanner to it.", Category.UTILITY);
        anywhere().onByDefault();
        self = this;
    }

    public static Ctx ctx() {
        return ctx;
    }

    /** A scan time (in nanoseconds) scaled by how well the game is running. */
    public static long budget(long base) {
        return (long) (base * ctx.budget);
    }

    public static BaseBrain.Baseline baseline() {
        load();
        String k = ctx.server + "|" + ctx.dim;
        return BASELINES.computeIfAbsent(k, x -> new BaseBrain.Baseline());
    }

    public static void learned() {
        if (self != null) {
            self.saveNeeded = true;
            self.saveAt = System.currentTimeMillis();
        }
    }

    /** True when the Brain is allowed to learn per server. */
    public static boolean learning() {
        return self == null || self.remember.on();
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("zeus-brain.json");
    }

    private static Path playstyleFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("zeus-playstyle.json");
    }

    private void loadPlaystyle() {
        if (playstyleLoaded) return;
        playstyleLoaded = true;
        try {
            if (!Files.isRegularFile(playstyleFile())) return;
            JsonObject o = JsonParser.parseString(Files.readString(playstyleFile(), StandardCharsets.UTF_8)).getAsJsonObject();
            String[] keys = {"combat", "mining", "building", "movement", "exploration"};
            for (int i = 0; i < keys.length; i++) if (o.has(keys[i])) playCounts[i] = Math.max(0, o.get(keys[i]).getAsLong());
        } catch (IOException | RuntimeException e) { Zeus.LOG.warn("Could not read the learned playstyle", e); }
    }

    private void savePlaystyle() {
        try {
            JsonObject o = new JsonObject();
            String[] keys = {"combat", "mining", "building", "movement", "exploration"};
            for (int i = 0; i < keys.length; i++) o.addProperty(keys[i], playCounts[i]);
            Files.writeString(playstyleFile(), o.toString(), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) { Zeus.LOG.warn("Could not save the learned playstyle", e); }
    }

    private List<String> playstyleLines() {
        loadPlaystyle();
        String[] names = {"Combat", "Mining", "Building", "Movement", "Exploration"};
        long total = 0; int best = 0;
        for (int i = 0; i < playCounts.length; i++) { total += playCounts[i]; if (playCounts[i] > playCounts[best]) best = i; }
        if (!learnPlaystyle.on()) return List.of("Learning is paused. Your saved profile is kept.");
        if (total < 20) return List.of("Learning your playstyle…", "Keep playing; this profile stays on this computer.");
        return List.of("Main tendency: " + names[best], "Combat " + playCounts[0] + " · Mining " + playCounts[1] + " · Building " + playCounts[2], "Movement " + playCounts[3] + " · Exploration " + playCounts[4]);
    }

    private static void load() {
        if (loaded) return;
        loaded = true;
        try {
            if (!Files.isRegularFile(file())) return;
            JsonObject o = JsonParser.parseString(Files.readString(file(), StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, com.google.gson.JsonElement> e : o.entrySet()) {
                JsonObject b = e.getValue().getAsJsonObject();
                BaseBrain.Baseline bl = new BaseBrain.Baseline();
                bl.crowding = b.get("crowding").getAsDouble();
                bl.samples = b.get("samples").getAsLong();
                BASELINES.put(e.getKey(), bl);
            }
        } catch (IOException | RuntimeException e) {
            Zeus.LOG.warn("Could not read what Zeus learned about servers", e);
        }
    }

    private static void save() {
        try {
            JsonObject o = new JsonObject();
            for (Map.Entry<String, BaseBrain.Baseline> e : BASELINES.entrySet()) {
                JsonObject b = new JsonObject();
                b.addProperty("crowding", e.getValue().crowding);
                b.addProperty("samples", e.getValue().samples);
                o.add(e.getKey(), b);
            }
            Files.createDirectories(file().getParent());
            Files.writeString(file(), o.toString(), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            Zeus.LOG.warn("Could not save what Zeus learned about servers", e);
        }
    }

    static String serverKey(Minecraft mc) {
        try {
            if (mc.getSingleplayerServer() != null) return "singleplayer";
            if (mc.getCurrentServer() != null && mc.getCurrentServer().ip != null) return mc.getCurrentServer().ip.toLowerCase(Locale.ROOT);
        } catch (RuntimeException ignored) {
            // fall through
        }
        return "unknown";
    }

    /** The y of the ground at a column, or {@code Integer.MIN_VALUE} when this dimension has no open ground (the Nether). */
    public static int surface(Minecraft mc, int x, int z) {
        if (mc.level == null || !ctx.hasSurface) return Integer.MIN_VALUE;
        try {
            return mc.level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        } catch (RuntimeException e) {
            return Integer.MIN_VALUE;
        }
    }

    // ------------------------------------------------------------------ updating

    @Override
    protected void onTick(Minecraft mc) {
        if (mc.level == null || mc.player == null) return;
        if (++ticks % 10 != 0) return;
        if (learnPlaystyle.on()) {
            loadPlaystyle();
            boolean attack = mc.options.keyAttack.isDown();
            boolean use = mc.options.keyUse.isDown();
            boolean moving = mc.player.getDeltaMovement().horizontalDistanceSqr() > 0.0125 || mc.player.isSprinting() || mc.player.isFallFlying();
            if (attack && mc.hitResult instanceof EntityHitResult) playCounts[0]++;
            else if (attack && mc.hitResult instanceof BlockHitResult) playCounts[1]++;
            else if (use && mc.player.getMainHandItem().getItem() instanceof BlockItem) playCounts[2]++;
            else if (moving) { playCounts[3]++; if (!mc.player.isSprinting() && !mc.player.isFallFlying()) playCounts[4]++; }
            if (System.currentTimeMillis() - saveAt > 60000) { savePlaystyle(); saveAt = System.currentTimeMillis(); }
        }
        Ctx c = new Ctx();
        c.server = serverKey(mc);
        c.multi = mc.getSingleplayerServer() == null;
        String d = mc.level.dimension().toString();
        c.dim = d.contains("nether") ? "nether" : d.contains("the_end") ? "end" : "overworld";
        c.hasSurface = c.dim.equals("overworld");
        c.fps = ctx.fps;
        c.water = mc.player.isInWater();
        c.centerDist = Math.sqrt(mc.player.getX() * mc.player.getX() + mc.player.getZ() * mc.player.getZ());
        if (c.hasSurface) {
            c.surfaceY = surface(mc, mc.player.getBlockX(), mc.player.getBlockZ());
            int depth = c.surfaceY == Integer.MIN_VALUE ? 0 : c.surfaceY - mc.player.getBlockY();
            c.underground = depth > 6 && !mc.level.canSeeSky(mc.player.blockPosition());
            c.deep = mc.player.getBlockY() < 0;
        } else {
            c.underground = true;
            c.deep = false;
        }
        c.area = c.dim.equals("nether") ? "The Nether" : c.dim.equals("end") ? "The End" : c.deep ? "Deep underground" : c.underground ? "Caves" : c.centerDist < 300 ? "Near the world centre" : "Surface";
        float b = 1f;
        if (adapt.on()) {
            if (c.fps < 15) b = 0.25f;
            else if (c.fps < 30) b = 0.45f;
            else if (c.fps < 50) b = 0.75f;
            else if (c.fps > 120) b = 1.3f;
            b = Math.max(b, (float) (floor.get() / 100.0));
        }
        c.budget = b;
        ctx = c;
        if (saveNeeded && System.currentTimeMillis() - saveAt > 15000) {
            saveNeeded = false;
            save();
        }
        Ledger.saveIfDue();
    }

    @Override
    protected void onHud(GuiGraphics g, Minecraft mc, float partial, int w, int h) {
        long now = System.nanoTime();
        frames++;
        if (now - second > 1_000_000_000L) {
            ctx.fps = frames;
            frames = 0;
            second = now;
        }
    }

    @Override
    protected void onDisable() {
        if (playstyleLoaded) savePlaystyle();
        ctx = new Ctx();
    }

    @Override
    public String info() {
        return ctx.fps + " fps";
    }

    private List<String> lines() {
        Ctx c = ctx;
        List<String> out = new ArrayList<>();
        if (!enabled()) {
            out.add("Switch the module on.");
            return out;
        }
        out.add("Server: " + c.server + (c.multi ? "" : " (your own world)"));
        out.add("Dimension: " + c.dim + "  ·  you are: " + c.area);
        out.add("Frame rate " + c.fps + "  ·  scanners run at " + Math.round(c.budget * 100) + " % of their normal time");
        BaseBrain.Baseline b = BASELINES.get(c.server + "|" + c.dim);
        if (b != null && b.samples > 3) out.add("This place is " + (b.crowding > 0.2 ? "crowded with builds" : b.crowding > 0.08 ? "fairly built up" : "quiet") + " (" + Math.round(b.crowding * 100) + " % of chunks look built)");
        return out;
    }
}
