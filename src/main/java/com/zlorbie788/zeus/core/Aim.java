package com.zlorbie788.zeus.core;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.phys.Vec3;

/**
 * Looks at something without turning your view: a small "I am looking this way" packet goes to the server, your own screen
 * stays where it is, and the next normal movement packet puts the server back to your real view. Servers that check that
 * you face what you hit (and some that do not) then see a player who looks at the crystal, block or target.
 */
public final class Aim {
    private Aim() {}

    private static float lastYaw = Float.NaN;
    private static float lastPitch = Float.NaN;

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

    /** Tells the server you look at the point. Returns false when there is no connection. */
    public static boolean look(Vec3 point) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null || point == null) return false;
        Vec3 eye = mc.player.getEyePosition();
        float yaw = yawTo(eye, point);
        float pitch = Math.max(-90f, Math.min(90f, pitchTo(eye, point)));
        return sendRotation(yaw, pitch);
    }

    /**
     * Tells the server you look at these angles while your own view stays put. This is what a silent aim uses: the
     * server's copy of you faces the target, so the hit is accepted, and your screen is untouched.
     */
    public static boolean sendRotation(float yaw, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) return false;
        // the hand shapes the angles before they go out, and what it changed is exactly what a server can notice
        float[] shaped = Hand.shape(yaw, Math.max(-90f, Math.min(90f, pitch)));
        float y = shaped[0];
        float p = Math.max(-90f, Math.min(90f, shaped[1]));
        lastYaw = y;
        lastPitch = p;
        com.zlorbie788.zeus.mods.Exposure.rotation(mc.player.getYRot(), mc.player.getXRot(), y, p);
        mc.getConnection().send(new ServerboundMovePlayerPacket.Rot(y, p, mc.player.onGround(), mc.player.horizontalCollision));
        return true;
    }

    public static void forget() {
        lastYaw = Float.NaN;
        lastPitch = Float.NaN;
    }
}
