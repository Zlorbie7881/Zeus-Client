package com.zlorbie788.zeus.mods;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class Clearance {
    private Clearance() {}

    record Result(int owed, int kinds, int seen) {}

    private static final Result NONE = new Result(0, 0, 0);

    private static final int CAP = 40;

    private static final int KIND_MIN = 2;

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
            if ((bits & (1 << i)) != 0) kinds++;
        }
        if (kinds == 1 && owed < KIND_MIN) return new Result(owed, 1, seen);
        return new Result(Math.min(CAP, owed), kinds, seen);
    }
}
