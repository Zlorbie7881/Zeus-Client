package com.zlorbie788.zeus.mods;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;

import java.util.concurrent.ConcurrentHashMap;

public final class Activity {
    private Activity() {}

    private static final ConcurrentHashMap<Long, long[]> HEAT = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<net.minecraft.world.level.block.Block, Boolean> NOISE = new ConcurrentHashMap<>();
    private static volatile boolean counting;
    private static volatile long halfLifeMs = 30_000L;

    public static void setMemorySeconds(int seconds) {
        halfLifeMs = Math.max(5_000L, Math.min(120_000L, seconds * 1_000L));
    }

    public static void setCounting(boolean on) {
        counting = on;
        if (!on) HEAT.clear();
    }

    private static long key(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xffffffffL);
    }

    private static int batchLeft = Integer.MAX_VALUE;

    public static void beginBatch() {
        batchLeft = 4;
    }

    public static void endBatch() {
        batchLeft = Integer.MAX_VALUE;
    }

    public static void note(BlockPos pos, BlockState state) {
        if (!counting || pos == null || state == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        double dx = pos.getX() + 0.5 - mc.player.getX();
        double dz = pos.getZ() + 0.5 - mc.player.getZ();
        if (dx * dx + dz * dz < 12 * 12) return;
        if (!playerLike(mc.level.getBlockState(pos), state)) return;
        if (batchLeft != Integer.MAX_VALUE) {
            if (batchLeft <= 0) return;
            batchLeft--;
        }
        long now = System.currentTimeMillis();
        HEAT.compute(key(pos.getX() >> 4, pos.getZ() >> 4), (k, v) -> {
            if (v == null) return new long[] {100, now};
            v[0] = decayed(v[0], v[1], now) + 100;
            v[1] = now;
            return v;
        });
    }

    static boolean playerLike(BlockState old, BlockState now) {
        if (old == null || now == null) return false;
        if (old.getBlock() == now.getBlock()) return false;
        boolean oldFluid = !old.getFluidState().isEmpty();
        boolean newFluid = !now.getFluidState().isEmpty();
        boolean oldGone = old.isAir() || oldFluid || old.canBeReplaced();
        boolean nowGone = now.isAir() || newFluid || now.canBeReplaced();
        if (oldGone == nowGone) return false;
        if (!oldGone) {
            if (noise(old) || old.getBlock() instanceof net.minecraft.world.level.block.FallingBlock) return false;
            return true;
        }
        if (noise(now) || now.getBlock() instanceof net.minecraft.world.level.block.FallingBlock) return false;
        if (oldFluid && stoneFromFluid(now)) return false;
        return true;
    }

    private static boolean stoneFromFluid(BlockState s) {
        try {
            String id = BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
            return id.equals("cobblestone") || id.equals("stone") || id.equals("obsidian") || id.equals("basalt") || id.equals("blackstone");
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static long decayed(long value, long at, long now) {
        double f = Math.pow(0.5, (now - at) / (double) halfLifeMs);
        return Math.round(value * f);
    }

    public static double heat(int cx, int cz) {
        long[] v = HEAT.get(key(cx, cz));
        if (v == null) return 0;
        return decayed(v[0], v[1], System.currentTimeMillis()) / 100.0;
    }

    public static java.util.List<long[]> hot(double min) {
        java.util.List<long[]> out = new java.util.ArrayList<>();
        long now = System.currentTimeMillis();
        for (var e : HEAT.entrySet()) {
            long[] v = e.getValue();
            double h = decayed(v[0], v[1], now) / 100.0;
            if (h < 0.2) {
                HEAT.remove(e.getKey(), v);
                continue;
            }
            if (h >= min) out.add(new long[] {(int) (e.getKey() >> 32), (int) (long) e.getKey(), Math.round(h * 10)});
        }
        out.sort((a, b) -> Long.compare(b[2], a[2]));
        return out;
    }

    private static boolean noise(BlockState state) {
        net.minecraft.world.level.block.Block b = state.getBlock();
        return NOISE.computeIfAbsent(b, k -> {
            String p;
            try {
                String id = BuiltInRegistries.BLOCK.getKey(k).toString();
                p = id.substring(id.indexOf(':') + 1);
            } catch (RuntimeException e) {
                return true;
            }
            return p.equals("water") || p.equals("lava") || p.endsWith("leaves") || p.equals("fire") || p.equals("soul_fire")
                    || p.equals("short_grass") || p.equals("tall_grass") || p.equals("fern") || p.equals("snow") || p.endsWith("sapling")
                    || p.equals("wheat") || p.equals("carrots") || p.equals("potatoes") || p.equals("beetroots") || p.equals("sugar_cane")
                    || p.equals("kelp") || p.equals("kelp_plant") || p.equals("bamboo") || p.equals("cactus") || p.equals("vine")
                    || p.equals("pumpkin_stem") || p.equals("melon_stem") || p.equals("sweet_berry_bush") || p.equals("nether_wart")
                    || p.equals("cave_vines") || p.equals("cave_vines_plant") || p.equals("glow_lichen") || p.equals("moss_carpet")
                    || p.equals("bubble_column") || p.equals("piston_head") || p.equals("moving_piston")
                    || p.contains("ice") || p.equals("snow_block") || p.equals("powder_snow") || p.contains("sculk") || p.contains("dripstone")
                    || p.contains("amethyst") || p.contains("azalea") || p.contains("dripleaf") || p.contains("hanging_roots") || p.equals("spore_blossom")
                    || p.contains("vines") || p.contains("moss") || p.contains("turtle_egg") || p.contains("frogspawn") || p.equals("torchflower")
                    || p.equals("pitcher_crop") || p.equals("cocoa") || p.equals("chorus_flower") || p.equals("chorus_plant") || p.equals("cobweb")
                    || p.equals("glow_berries") || p.equals("big_dripleaf_stem") || p.equals("lava_cauldron") || p.equals("water_cauldron")
                    || p.equals("powder_snow_cauldron") || p.contains("coral") || p.equals("seagrass") || p.equals("tall_seagrass") || p.equals("sea_pickle")
                    || p.equals("lily_pad") || p.equals("dead_bush") || p.endsWith("_mushroom") || p.endsWith("_fungus") || p.endsWith("_roots") || p.equals("crimson_roots")
                    || p.equals("warped_wart_block") || p.equals("shroomlight") || p.equals("nether_sprouts") || p.equals("tripwire") || p.equals("tripwire_hook");
        });
    }
}
