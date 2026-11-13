package com.zlorbie788.zeus.core;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
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

    public static boolean sendRotation(float yaw, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) return false;
        float[] shaped = Hand.shape(yaw, Math.max(-90f, Math.min(90f, pitch)));
        float y = shaped[0];
        float p = Math.max(-90f, Math.min(90f, shaped[1]));
        int tick = mc.player.tickCount;
        if (tick != sentTick) {
            sentTick = tick;
            sentCount = 0;
        }
        if (sentCount >= 2 && Math.abs(y - sentYaw) < 12f && Math.abs(p - sentPitch) < 12f) return true;
        sentCount++;
        sentYaw = y;
        sentPitch = p;
        lastYaw = y;
        lastPitch = p;
        com.zlorbie788.zeus.mods.Exposure.rotation(mc.player.getYRot(), mc.player.getXRot(), y, p);
        mc.getConnection().send(new ServerboundMovePlayerPacket.Rot(y, p, mc.player.onGround(), mc.player.horizontalCollision));
        return true;
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

    public static void forget() {
        lastYaw = Float.NaN;
        lastPitch = Float.NaN;
        sentTick = -1;
        sentCount = 0;
    }
}
