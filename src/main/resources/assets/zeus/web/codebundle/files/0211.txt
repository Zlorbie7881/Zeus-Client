package com.zlorbie788.zeus.render;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

public final class CameraControl {
    private CameraControl() {}

    public static final int OFF = 0;
    public static final int FREECAM = 1;
    public static final int LOOK = 2;

    private static volatile int mode;
    private static volatile boolean freeze;
    private static volatile boolean blockClicks;
    private static Object world;
    private static double bx, by, bz;
    private static double x, y, z, px, py, pz;
    private static float yaw, pitch, pyaw, ppitch;
    private static volatile boolean cameraHook;
    private static volatile boolean keyHook;
    private static volatile boolean turnHook;
    private static final boolean[] RAW = new boolean[7];

    public static int mode() {
        return mode;
    }

    public static boolean overridingPosition() {
        return mode == FREECAM;
    }

    public static boolean overridingLook() {
        return mode != OFF;
    }

    public static boolean freezing() {
        return freeze && mode == FREECAM;
    }

    public static void begin(int newMode, double ex, double ey, double ez, float yRot, float xRot, boolean freezeInput, boolean noClicks) {
        x = px = ex;
        y = py = ey;
        z = pz = ez;
        yaw = pyaw = yRot;
        pitch = ppitch = xRot;
        cameraHook = false;
        keyHook = false;
        turnHook = false;
        java.util.Arrays.fill(RAW, false);
        freeze = freezeInput;
        blockClicks = noClicks;
        Minecraft mc = Minecraft.getInstance();
        world = mc.level;
        if (mc.player != null) {
            bx = mc.player.getX();
            by = mc.player.getY();
            bz = mc.player.getZ();
        }
        mode = newMode;
    }

    public static boolean stillHere() {
        Minecraft mc = Minecraft.getInstance();
        if (mode == OFF) return true;
        if (mc.level == null || mc.level != world) {
            end();
            return false;
        }
        return true;
    }

    public static double bodyMoved() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0;
        return Math.sqrt(sq(mc.player.getX() - bx) + sq(mc.player.getY() - by) + sq(mc.player.getZ() - bz));
    }

    private static double sq(double v) {
        return v * v;
    }

    public static void clearKeys() {
        java.util.Arrays.fill(RAW, false);
    }

    public static void end() {
        mode = OFF;
        freeze = false;
        blockClicks = false;
    }

    public static void snapshot() {
        px = x;
        py = y;
        pz = z;
        pyaw = yaw;
        ppitch = pitch;
    }

    public static void move(double dx, double dy, double dz) {
        x += dx;
        y += dy;
        z += dz;
    }

    public static double x() {
        return x;
    }

    public static double y() {
        return y;
    }

    public static double z() {
        return z;
    }

    public static float yaw() {
        return yaw;
    }

    public static float pitch() {
        return pitch;
    }

    public static double[] eye(float t) {
        return new double[] {px + (x - px) * t, py + (y - py) * t, pz + (z - pz) * t};
    }

    public static float yaw(float t) {
        return yaw;
    }

    public static float pitch(float t) {
        return pitch;
    }

    public static void turn(double dYaw, double dPitch) {
        turnHook = true;
        yaw += (float) (dYaw * 0.15);
        pitch = Math.max(-90f, Math.min(90f, pitch + (float) (dPitch * 0.15)));
    }

    public static void sawCamera() {
        cameraHook = true;
    }

    public static boolean cameraHooked() {
        return cameraHook;
    }

    public static boolean keysHooked() {
        return keyHook;
    }

    public static boolean turnHooked() {
        return turnHook;
    }

    public static boolean[] keys() {
        return RAW;
    }

    public static boolean filterDown(KeyMapping k, boolean real) {
        if (!freezing()) return real;
        Options o = Minecraft.getInstance().options;
        if (blockClicks && (k == o.keyAttack || k == o.keyUse)) return false;
        int i = k == o.keyUp ? 0 : k == o.keyDown ? 1 : k == o.keyLeft ? 2 : k == o.keyRight ? 3 : k == o.keyJump ? 4 : k == o.keyShift ? 5 : k == o.keySprint ? 6 : -1;
        if (i < 0) return real;
        RAW[i] = real;
        keyHook = true;
        return false;
    }

    public static boolean filterClick(KeyMapping k, boolean real) {
        if (!real || !blockClicks || !freezing()) return real;
        Options o = Minecraft.getInstance().options;
        return !(k == o.keyAttack || k == o.keyUse || k == o.keyPickItem);
    }
}
