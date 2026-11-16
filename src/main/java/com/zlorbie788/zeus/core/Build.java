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

public final class Build {
    private Build() {}

    /**
     * Vanilla block interaction range. The server keeps its own copy of this attribute and never sees
     * the Reach module, so Grim's FarPlace measures against 4.5 no matter what the slider says: a
     * placement past this is a flag on every module that places, from scaffold to surround to
     * crystals. Placements therefore use this, not the slider.
     */
    public static final double REACH = 4.5;

    public static double reach() {
        return REACH;
    }

    /** The most a placement may use: the server's own range, whatever the module asked for. */
    private static double placeReach(double asked) {
        return Math.min(REACH, Math.max(asked, REACH));
    }

    /** Nothing solid between the eyes and the face being used, so it is not a placement through a wall. */
    private static boolean sees(Vec3 eye, Vec3 hit) {
        Minecraft mc = mc();
        if (mc.level == null) return true;
        double dist = eye.distanceTo(hit);
        int steps = (int) Math.ceil(dist / 0.15);
        // Stop short of the face itself: the block being clicked is solid and the last samples are
        // inside it. Everything before that must be air, otherwise we are reaching through a wall.
        for (int i = 2; i < steps - 2; i++) {
            Vec3 at = eye.lerp(hit, (double) i / steps);
            BlockPos p = BlockPos.containing(at.x, at.y, at.z);
            if (solid(p)) return false;
        }
        return true;
    }

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

    public static boolean solid(BlockPos p) {
        BlockState s = state(p);
        return !s.isAir() && !s.canBeReplaced() && !s.getCollisionShape(mc().level, p).isEmpty();
    }

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

    public static BlockHitResult top(BlockPos base) {
        return new BlockHitResult(new Vec3(base.getX() + 0.5, base.getY() + 1.0, base.getZ() + 0.5), Direction.UP, base, false);
    }

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

    public static boolean canPlace(BlockPos pos, double reach) {
        BlockHitResult hit = against(pos, placeReach(reach));
        return replaceable(pos) && !blocked(pos) && hit != null && sees(eye(), hit.getLocation());
    }

    private static volatile long useLimitedAt;

    /** True when the last use was refused only because another use already happened this tick. */
    public static boolean rateLimited() {
        return System.currentTimeMillis() - useLimitedAt < 150;
    }

    public static boolean useOn(BlockHitResult hit, Predicate<ItemStack> item, boolean swing) {
        Minecraft mc = mc();
        if (mc.gameMode == null || mc.player == null) return false;
        // One block use per tick: a second one is MultiPlace, and so is the same block twice.
        // A refusal here is not a failure of the calling module, so it is flagged and not reported
        // as one: the crystal macros used it to decide their "no crystals" toast.
        if (!Stealth.allowUse(mc)) {
            useLimitedAt = System.currentTimeMillis();
            return false;
        }
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

    // One placement per tick is what a real player can do; two in the same tick is a red flag for
    // placement checks, so anything after the first is skipped and the caller tries again next tick.
    private static int placedTick = -1;

    public static boolean place(BlockPos pos, Predicate<ItemStack> item, boolean swing, double reach) {
        if (!replaceable(pos) || blocked(pos)) return false;
        Minecraft mc = mc();
        if (mc.player == null) return false;
        if (mc.player.tickCount == placedTick) return false;
        // Using an item while the game is digging is its own packet-order flag, so the placement waits.
        if (mc.gameMode.isDestroying()) return false;
        BlockHitResult hit = against(pos, placeReach(reach));
        if (hit == null) return false;
        if (!sees(eye(), hit.getLocation())) return false;
        // A placement whose face was never in the player's line of sight is a flag of its own, so
        // look at it first. The rotation goes out before the use packet in the same tick.
        if (!Aim.isLookingAt(hit.getLocation(), 8.0)) Aim.look(hit.getLocation());
        if (!useOn(hit, item, swing)) return false;
        placedTick = mc.player.tickCount;
        return true;
    }
}
