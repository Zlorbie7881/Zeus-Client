package com.zlorbie788.zeus.core;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.function.Predicate;

/** Placing blocks and using items on blocks, the way a player does with a right click. */
public final class Build {
    private Build() {}

    public static final double REACH = 4.6;

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    public static BlockState state(BlockPos p) {
        return mc().level.getBlockState(p);
    }

    public static boolean replaceable(BlockPos p) {
        return state(p).canBeReplaced();
    }

    public static boolean air(BlockPos p) {
        return state(p).isAir();
    }

    /** Something solid you could stand on or place against. */
    public static boolean solid(BlockPos p) {
        BlockState s = state(p);
        return !s.isAir() && !s.canBeReplaced() && !s.getCollisionShape(mc().level, p).isEmpty();
    }

    /** A living thing or a placed object is in the way (dropped items do not count). */
    public static boolean blocked(BlockPos p) {
        for (Entity e : mc().level.getEntities((Entity) null, new AABB(p))) {
            if (e instanceof ItemEntity || !e.isAlive() || e.isSpectator()) continue;
            return true;
        }
        return false;
    }

    public static Vec3 eye() {
        return mc().player.getEyePosition();
    }

    public static double distTo(BlockPos p) {
        return eye().distanceTo(Vec3.atCenterOf(p));
    }

    /** A click on the top face of a block. */
    public static BlockHitResult top(BlockPos base) {
        return new BlockHitResult(new Vec3(base.getX() + 0.5, base.getY() + 1.0, base.getZ() + 0.5), Direction.UP, base, false);
    }

    /** Finds a neighbour of {@code pos} to click on so that the block lands on {@code pos}. Null when there is none in reach. */
    public static BlockHitResult against(BlockPos pos, double reach) {
        BlockHitResult best = null;
        double bestD = Double.MAX_VALUE;
        for (Direction d : Direction.values()) {
            BlockPos nb = pos.relative(d);
            if (!solid(nb)) continue;
            Direction face = d.getOpposite();
            Vec3 hit = new Vec3(nb.getX() + 0.5 + face.getStepX() * 0.5, nb.getY() + 0.5 + face.getStepY() * 0.5, nb.getZ() + 0.5 + face.getStepZ() * 0.5);
            double dist = eye().distanceTo(hit);
            if (dist > reach || dist >= bestD) continue;
            best = new BlockHitResult(hit, face, nb, false);
            bestD = dist;
        }
        return best;
    }

    /** True when something could be placed at pos right now (free, in reach, with something to click on). */
    public static boolean canPlace(BlockPos pos, double reach) {
        return replaceable(pos) && !blocked(pos) && against(pos, reach) != null;
    }

    /** Uses the item on a block: the hand is the offhand when it holds the item, otherwise the hotbar slot is selected for a moment. */
    public static boolean useOn(BlockHitResult hit, Predicate<ItemStack> item, boolean swing) {
        Minecraft mc = mc();
        if (mc.gameMode == null || mc.player == null) return false;
        if (Inv.offhand(item)) {
            mc.gameMode.useItemOn(mc.player, InteractionHand.OFF_HAND, hit);
            if (swing) mc.player.swing(InteractionHand.OFF_HAND);
            return true;
        }
        int slot = Inv.hotbar(item);
        if (slot < 0) return false;
        return Inv.with(slot, () -> {
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
            if (swing) mc.player.swing(InteractionHand.MAIN_HAND);
        });
    }

    /** Places the held item on pos. */
    public static boolean place(BlockPos pos, Predicate<ItemStack> item, boolean swing, double reach) {
        if (!replaceable(pos) || blocked(pos)) return false;
        BlockHitResult hit = against(pos, reach);
        return hit != null && useOn(hit, item, swing);
    }
}
