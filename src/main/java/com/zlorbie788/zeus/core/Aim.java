package com.zlorbie788.zeus.core;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class Aim {
    private Aim() {}

    private static float lastYaw = Float.NaN;
    private static float lastPitch = Float.NaN;

    // A client that sends several look packets inside one tick is one of the easiest things a
    // server can notice. Modules aim again every tick anyway, so extra packets in the same tick
    // are dropped unless the new aim is far away from the one already on the wire.
    private static int sentTick = -1;
    private static int sentCount;
    private static float sentYaw;
    private static float sentPitch;

    public static float yawTo(Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        return (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
    }

    public static float pitchTo(Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double flat = Math.sqrt(dx * dx + dz * dz);
        return (float) -Math.toDegrees(Math.atan2(dy, flat));
    }

    public static boolean look(Vec3 point) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null || point == null) return false;
        Vec3 eye = mc.player.getEyePosition();
        float yaw = yawTo(eye, point);
        float pitch = Math.max(-90f, Math.min(90f, pitchTo(eye, point)));
        return sendRotation(yaw, pitch);
    }

    private static boolean sentOnce;

    private static int silentUntil;
    private static float silentYaw;
    private static float silentPitch;
    private static int fadeLeft;
    private static float fadeTargetYaw;
    private static float fadeTargetPitch;

    public static boolean sendRotation(float yaw, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) return false;
        float[] shaped = Hand.shape(yaw, Math.max(-90f, Math.min(90f, pitch)));
        int tick = mc.player.tickCount;
        if (tick != sentTick) {
            sentTick = tick;
            sentCount = 0;
        }
        // Every delta is a whole number of mouse steps measured from the last angle sent, because
        // that is the number a real mouse produces and the number Grim's aim processor takes the GCD
        // of. Quantising the absolute angle instead would leave the offset from the real view behind.
        float prevYaw = sentYaw;
        float prevPitch = sentPitch;
        boolean had = sentOnce;
        float[] tuned = Stealth.shape(mc, shaped[0], shaped[1], prevYaw, prevPitch, had);
        float y = tuned[0];
        float p = tuned[1];
        float[] allowed = com.zlorbie788.zeus.mods.Oracle.limitRotation(mc.player.getYRot(), mc.player.getXRot(), y, p);
        y = allowed[0];
        p = allowed[1];
        if (!Float.isFinite(y) || !Float.isFinite(p)) return false;
        if (sentCount >= 2 && Math.abs(Mth.wrapDegrees(y - sentYaw)) < 12f && Math.abs(p - sentPitch) < 12f) return true;
        // Sending the same look twice is Vulcan's "repeated yaw values"; the game already carries the
        // real view every tick, so an identical packet adds nothing but a flag.
        if (y == sentYaw && p == sentPitch) return true;
        sentOnce = true;
        sentCount++;
        sentYaw = y;
        sentPitch = p;
        lastYaw = y;
        lastPitch = p;
        float viewYaw = mc.player.getYRot();
        float viewPitch = mc.player.getXRot();
        double grid = Stealth.grid(mc);
        boolean away = Math.abs(Mth.wrapDegrees(y - viewYaw)) > Math.max(grid * 2.0, 0.35)
                || Math.abs(p - viewPitch) > Math.max(grid * 2.0, 0.35);
        if (away) {
            // Remember the flick: it is held, then walked home in steps instead of snapping back.
            silentYaw = y;
            silentPitch = p;
            silentUntil = tick + Stealth.HOLD_TICKS;
            fadeLeft = 0;
        } else {
            silentUntil = 0;
        }
        Stealth.sent(y, p, viewYaw, viewPitch, prevYaw, prevPitch, had);
        com.zlorbie788.zeus.mods.Oracle.rotation(viewYaw, viewPitch, y, p);
        mc.getConnection().send(new ServerboundMovePlayerPacket.Rot(y, p, mc.player.onGround(), mc.player.horizontalCollision));
        return true;
    }

    /**
     * Called every client tick. A silent rotation that nobody is refreshing is held for a moment and
     * then walked back to the real view in whole mouse steps, so the outgoing stream looks like a
     * player who looked at something and then looked away instead of a view that teleports home.
     */
    public static void tick(Minecraft mc) {
        if (mc.player == null || mc.getConnection() == null || silentUntil == 0) return;
        int tick = mc.player.tickCount;
        if (sentTick == tick) return;                 // a module aimed this tick, it owns the look
        if (tick <= silentUntil) {
            sendRaw(mc, silentYaw, silentPitch, tick);
            return;
        }
        if (fadeLeft == 0) {
            fadeLeft = Stealth.FADE_TICKS;
            fadeTargetYaw = mc.player.getYRot();
            fadeTargetPitch = mc.player.getXRot();
        }
        double grid = Stealth.grid(mc);
        silentYaw = Stealth.fadeStep(silentYaw, fadeTargetYaw, fadeLeft, grid, false);
        silentPitch = Stealth.fadeStep(silentPitch, fadeTargetPitch, fadeLeft, grid, true);
        sendRaw(mc, silentYaw, silentPitch, tick);
        if (--fadeLeft <= 0) {
            silentUntil = 0;
            silentYaw = fadeTargetYaw;
            silentPitch = fadeTargetPitch;
        }
    }

    private static void sendRaw(Minecraft mc, float yaw, float pitch, int tick) {
        float p = Math.max(-90f, Math.min(90f, pitch));
        if (yaw == sentYaw && p == sentPitch) return;
        float prevYaw = sentYaw;
        float prevPitch = sentPitch;
        boolean had = sentOnce;
        sentOnce = true;
        sentTick = tick;
        sentCount = 1;
        sentYaw = yaw;
        sentPitch = p;
        lastYaw = yaw;
        lastPitch = p;
        Stealth.sent(yaw, p, mc.player.getYRot(), mc.player.getXRot(), prevYaw, prevPitch, had);
        mc.getConnection().send(new ServerboundMovePlayerPacket.Rot(yaw, p, mc.player.onGround(), mc.player.horizontalCollision));
    }

    // Distance from the eyes to the nearest point of the hitbox, which is what a server measures.
    // Feet-to-feet distance says a tall mob is further away than it is and a wide one closer.
    public static double reachTo(Entity e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || e == null) return Double.MAX_VALUE;
        Vec3 eye = mc.player.getEyePosition();
        AABB box = e.getBoundingBox();
        double dx = Math.max(box.minX - eye.x, Math.max(0.0, eye.x - box.maxX));
        double dy = Math.max(box.minY - eye.y, Math.max(0.0, eye.y - box.maxY));
        double dz = Math.max(box.minZ - eye.z, Math.max(0.0, eye.z - box.maxZ));
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** True when the rotation the server currently has would already look at that point. */
    public static boolean isLookingAt(Vec3 point, double degrees) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || point == null) return true;
        Vec3 eye = mc.player.getEyePosition();
        float yaw = yawTo(eye, point);
        float pitch = pitchTo(eye, point);
        float baseYaw = sentOnce ? sentYaw : mc.player.getYRot();
        float basePitch = sentOnce ? sentPitch : mc.player.getXRot();
        return Math.abs(Mth.wrapDegrees(yaw - baseYaw)) <= degrees
                && Math.abs(pitch - basePitch) <= degrees;
    }

    public static void forget() {
        lastYaw = Float.NaN;
        lastPitch = Float.NaN;
        sentTick = -1;
        sentCount = 0;
    }
}
