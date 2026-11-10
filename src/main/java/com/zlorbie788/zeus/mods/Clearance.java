package com.zlorbie788.zeus.mods;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The other half of the evidence: what a chunk should have grown and did not.
 *
 * <p>A sapling grows, kelp reaches the surface, bamboo climbs, berries and wart ripen, cane and cactus add a block, crops
 * mature, vines lengthen. None of that needs a player. If a chunk was loaded and ticked, its plants are grown. So plants
 * that are still short after hours of being loaded prove the chunk was never ticked with anybody inside it, which is the
 * only thing that keeps a base from being found by walking past it. This measures that, per chunk, and nothing else: it
 * never looks at what a player placed, so it cannot be fooled by silk-touch farms or by a base built to look natural.</p>
 */
final class Clearance {
    private Clearance() {}

    /** Number of under-grown plants, how many kinds of them, and how many plants were looked at in total. */
    record Result(int owed, int kinds, int seen) {}

    private static final Result NONE = new Result(0, 0, 0);

    /** Keeps one chunk from dominating the ring test when it is a whole jungle. */
    private static final int CAP = 40;

    /** How many cells of each kind before it counts as a kind. */
    private static final int KIND_MIN = 2;

    /** Registry path -> {kind, max age}. Anything not in here is not growth we can time. */
    private static final Map<String, int[]> RULES = Map.ofEntries(
            Map.entry("kelp", new int[] {0, 25}),
            Map.entry("bamboo", new int[] {1, 15}),
            Map.entry("sugar_cane", new int[] {2, 15}),
            Map.entry("cactus", new int[] {3, 15}),
            Map.entry("cocoa", new int[] {4, 2}),
            Map.entry("sweet_berry_bush", new int[] {5, 3}),
            Map.entry("nether_wart", new int[] {6, 3}),
            Map.entry("wheat", new int[] {7, 7}),
            Map.entry("carrots", new int[] {7, 7}),
            Map.entry("potatoes", new int[] {7, 7}),
            Map.entry("beetroots", new int[] {7, 3}),
            Map.entry("pumpkin_stem", new int[] {8, 7}),
            Map.entry("melon_stem", new int[] {8, 7}),
            Map.entry("weeping_vines", new int[] {9, 25}),
            Map.entry("weeping_vines_plant", new int[] {9, 25}),
            Map.entry("twisting_vines", new int[] {9, 25}),
            Map.entry("twisting_vines_plant", new int[] {9, 25}),
            Map.entry("cave_vines", new int[] {10, 1}),
            Map.entry("cave_vines_plant", new int[] {10, 1})
    );

    private static final Map<Block, int[]> CACHE = new ConcurrentHashMap<>();
    /** Ages seen per block, so the age property is read once per block state and not per cell. */
    private static final Map<BlockState, Integer> STAGE = new ConcurrentHashMap<>();

    private static int[] rule(Block block) {
        return CACHE.computeIfAbsent(block, b -> {
            String path;
            try {
                path = BuiltInRegistries.BLOCK.getKey(b).getPath();
            } catch (RuntimeException e) {
                return new int[] {-1, 0};
            }
            int[] r = RULES.get(path);
            return r == null ? new int[] {-1, 0} : r;
        });
    }

    static boolean tracked(Block block) {
        return rule(block)[0] >= 0;
    }

    /** True while the plant is shorter than a plant that has been left alone gets. */
    static boolean owed(BlockState state, Block block) {
        int[] r = rule(block);
        if (r[0] < 0) return false;
        int stage = STAGE.computeIfAbsent(state, st -> {
            for (Map.Entry<net.minecraft.world.level.block.state.properties.Property<?>, Comparable<?>> e : st.getValues().entrySet()) {
                String name = e.getKey().getName();
                if (!name.equals("age") && !name.equals("stage") && !name.equals("berries")) continue;
                if (e.getValue() instanceof Boolean b) return b ? 1 : 0;
                try {
                    return Integer.parseInt(e.getValue().toString());
                } catch (NumberFormatException ignored) {
                    return -1;
                }
            }
            return -1;
        });
        if (stage < 0) return false;
        return stage < r[1];
    }

    static Result of(LevelChunk chunk) {
        LevelChunkSection[] sections = chunk.getSections();
        int owed = 0;
        int seen = 0;
        int bits = 0;
        for (LevelChunkSection sec : sections) {
            if (sec == null || sec.hasOnlyAir()) continue;
            if (!sec.maybeHas(st -> tracked(st.getBlock()))) continue;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = sec.getBlockState(x, y, z);
                        Block block = state.getBlock();
                        if (!tracked(block)) continue;
                        seen++;
                        if (!owed(state, block)) continue;
                        owed++;
                        bits |= 1 << rule(block)[0];
                    }
                }
            }
        }
        if (owed == 0 && seen == 0) return NONE;
        int kinds = 0;
        for (int i = 0; i < 11; i++) {
            // a single stray sapling is not proof of anything; a kind needs a few cells
            if ((bits & (1 << i)) != 0) kinds++;
        }
        if (kinds == 1 && owed < KIND_MIN) return new Result(owed, 1, seen);
        return new Result(Math.min(CAP, owed), kinds, seen);
    }
}
