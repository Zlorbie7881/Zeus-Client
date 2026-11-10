package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Env;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Setting;
import com.zlorbie788.zeus.core.Zeus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Counts one thing per chunk across the chunks you have loaded, so you can walk a straight line instead of a spiral.
 *
 * <p>Ore is not spread out evenly: it comes in veins, and the vein you are standing over tells you nothing about the chunk
 * next to it. This keeps a tally for every chunk in range and lists the best ones with a direction and a distance, which is
 * the difference between mining for an hour and mining where the ore actually is. It reads only chunk data your client has
 * already received, on a small time budget, so it never asks the server for anything.</p>
 */
public final class ChunkScanner extends Module {
    private static final String[] WHAT = {"Diamond ore", "Ancient debris", "Emerald ore", "Gold ore", "Iron ore", "Coal ore", "Amethyst cluster", "Spawner", "Chest", "Shulker box"};

    private final Setting.Choice what = add(new Setting.Choice("Look for", "The block every chunk is counted for.", 0, WHAT));
    private final Setting.Num range = add(new Setting.Num("Range", "How many chunks around you are counted.", 12, 3, 20, 1, " chunks"));
    private final Setting.Num richest = add(new Setting.Num("Chunks listed", "How many of the best chunks are shown.", 6, 1, 12, 1, ""));
    private final Setting.Bool deepOnly = add(new Setting.Bool("Below the surface only", "Skip sections near the sky, where a stray ore in a mountain is not worth walking to.", true));
    private final Setting.Info list = add(new Setting.Info("Best chunks", this::lines));
    private final Setting.Action copy = add(new Setting.Action("Copy", "Copies every counted chunk with its coordinates so you can keep the list.", this::copy));

    private final Map<Long, Integer> counts = new HashMap<>();
    private final List<long[]> queue = new ArrayList<>();
    private int qi;
    private int tick;

    public ChunkScanner() {
        super("chunkscanner", "Chunk Scanner", "Counts ore, spawners or containers per chunk and points at the richest chunks.", Category.SCANNER);
        light();
        hudCard();
    }

    @Override
    protected void onEnable() {
        counts.clear();
        queue.clear();
        qi = 0;
    }

    @Override
    protected void onDisable() {
        counts.clear();
        queue.clear();
        qi = 0;
    }

    private static Block target(int index) {
        return switch (index) {
            case 1 -> Blocks.ANCIENT_DEBRIS;
            case 2 -> Blocks.EMERALD_ORE;
            case 3 -> Blocks.GOLD_ORE;
            case 4 -> Blocks.IRON_ORE;
            case 5 -> Blocks.COAL_ORE;
            case 6 -> Blocks.AMETHYST_CLUSTER;
            case 7 -> Blocks.SPAWNER;
            case 8 -> Blocks.CHEST;
            case 9 -> Blocks.SHULKER_BOX;
            default -> Blocks.DIAMOND_ORE;
        };
    }

    private static boolean matches(BlockState state, Block wanted) {
        Block block = state.getBlock();
        if (block == wanted) return true;
        // deepslate variants count for the ore they hold
        String want;
        String have;
        try {
            want = BuiltInRegistries.BLOCK.getKey(wanted).getPath();
            have = BuiltInRegistries.BLOCK.getKey(block).getPath();
        } catch (RuntimeException e) {
            return false;
        }
        return want.equals("diamond_ore") && have.equals("deepslate_diamond_ore")
                || want.equals("gold_ore") && have.equals("deepslate_gold_ore")
                || want.equals("iron_ore") && have.equals("deepslate_iron_ore")
                || want.equals("coal_ore") && have.equals("deepslate_coal_ore")
                || want.equals("emerald_ore") && have.equals("deepslate_emerald_ore")
                || want.equals("chest") && have.equals("trapped_chest");
    }

    @Override
    protected void onTick(Minecraft mc) {
        if (mc.level == null || mc.player == null) return;
        if (tick++ % 40 == 0 && qi >= queue.size()) buildQueue(mc);
        Block wanted = target(what.index());
        long deadline = System.nanoTime() + 1_500_000L;
        while (qi < queue.size() && System.nanoTime() < deadline) {
            long[] c = queue.get(qi++);
            count(mc, (int) c[0], (int) c[1], wanted);
        }
    }

    private void buildQueue(Minecraft mc) {
        queue.clear();
        qi = 0;
        int rc = range.i();
        int pcx = mc.player.getBlockX() >> 4;
        int pcz = mc.player.getBlockZ() >> 4;
        for (int dx = -rc; dx <= rc; dx++) {
            for (int dz = -rc; dz <= rc; dz++) queue.add(new long[] {pcx + dx, pcz + dz});
        }
        queue.sort(Comparator.comparingLong(a -> a[0] * a[0] + a[1] * a[1]));
    }

    private void count(Minecraft mc, int cx, int cz, Block wanted) {
        LevelChunk chunk = Env.chunk(mc, cx, cz);
        long key = key(cx, cz);
        if (chunk == null) {
            counts.remove(key);
            return;
        }
        LevelChunkSection[] sections = chunk.getSections();
        int minY = chunk.getMinSectionY() << 4;
        int surface = Brain.surface(mc, (cx << 4) + 8, (cz << 4) + 8);
        int found = 0;
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection sec = sections[i];
            if (sec == null || sec.hasOnlyAir()) continue;
            int by = minY + i * 16;
            if (deepOnly.on() && surface != Integer.MIN_VALUE && by > surface - 4) continue;
            if (!sec.maybeHas(st -> matches(st, wanted))) continue;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        if (matches(sec.getBlockState(x, y, z), wanted)) found++;
                    }
                }
            }
        }
        counts.put(key, found);
    }

    private List<String> lines() {
        List<String> out = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        if (!enabled()) {
            out.add("Switch it on to count.");
            return out;
        }
        if (mc.player == null || counts.isEmpty()) return out;
        String name = WHAT[what.index()].toLowerCase(Locale.ROOT);
        List<Map.Entry<Long, Integer>> sorted = new ArrayList<>(counts.entrySet());
        sorted.removeIf(e -> e.getValue() <= 0);
        sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        if (sorted.isEmpty()) {
            out.add("No " + name + " in " + counts.size() + " loaded chunks.");
            return out;
        }
        int total = 0;
        for (int v : counts.values()) total += v;
        out.add(total + " " + name + " in " + counts.size() + " chunks; the best are:");
        for (int i = 0; i < Math.min(richest.i(), sorted.size()); i++) {
            long key = sorted.get(i).getKey();
            int cx = (int) (key >> 32);
            int cz = (int) key;
            int x = cx * 16 + 8;
            int z = cz * 16 + 8;
            int d = (int) Math.round(Math.hypot(x - mc.player.getX(), z - mc.player.getZ()));
            String dir = direction(x - mc.player.getX(), z - mc.player.getZ());
            out.add(String.format(Locale.ROOT, "%d x %s   %d, %d   %dm %s", sorted.get(i).getValue(), name, x, z, d, dir));
        }
        return out;
    }

    private void copy() {
        Minecraft mc = Minecraft.getInstance();
        StringBuilder b = new StringBuilder("Zeus chunk scan, ").append(WHAT[what.index()]).append('\n');
        List<Map.Entry<Long, Integer>> sorted = new ArrayList<>(counts.entrySet());
        sorted.removeIf(e -> e.getValue() <= 0);
        sorted.sort((a, b2) -> Integer.compare(b2.getValue(), a.getValue()));
        for (Map.Entry<Long, Integer> e : sorted) {
            b.append(e.getValue()).append(" x ").append((int) (e.getKey() >> 32) * 16 + 8).append(", ")
                    .append((int) (long) e.getKey() * 16 + 8).append('\n');
        }
        if (mc.keyboardHandler != null) {
            mc.keyboardHandler.setClipboard(b.toString());
            Zeus.toast("Chunk scan copied", true);
        }
    }

    @Override
    public String info() {
        return counts.isEmpty() ? null : Integer.toString(counts.size());
    }

    private static long key(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    private static final String[] DIRS = {"E", "SE", "S", "SW", "W", "NW", "N", "NE"};

    private static String direction(double dx, double dz) {
        double a = Math.toDegrees(Math.atan2(dz, dx));
        int i = (int) Math.round(((a % 360) + 360) % 360 / 45.0) % 8;
        return DIRS[i];
    }
}
