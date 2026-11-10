package com.zlorbie788.zeus.render;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

/**
 * The state behind Freecam and Free Look. The game's camera is asked (by the camera mixin) where to stand and which way to
 * look; while this is on, the answer comes from here and not from your player. Your player is not touched: it stands where
 * it was, and the game sends exactly what it would send if you were not pressing any key. Nothing here talks to a server.
 */
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
    private static final boolean[] RAW = new boolean[7]; // forward, back, left, right, up, down, sprint

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

    /** Starts a mode with the camera where your eyes are and looking where you look. */
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

    /** True while the world the camera was started in is still the world you are in. A new world or a reconnect ends the mode. */
    public static boolean stillHere() {
        Minecraft mc = Minecraft.getInstance();
        if (mode == OFF) return true;
        if (mc.level == null || mc.level != world) {
            end();
            return false;
        }
        return true;
    }

    /** How far your body has been moved since the camera started (a teleport, knockback, a vehicle). */
    public static double bodyMoved() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0;
        return Math.sqrt(sq(mc.player.getX() - bx) + sq(mc.player.getY() - by) + sq(mc.player.getZ() - bz));
    }

    private static double sq(double v) {
        return v * v;
    }

    /** Forgets which keys were held, for example when a screen opens, so the camera does not keep flying. */
    public static void clearKeys() {
        java.util.Arrays.fill(RAW, false);
    }

    public static void end() {
        mode = OFF;
        freeze = false;
        blockClicks = false;
    }

    /** Remembers where the camera was, so a frame between two ticks can be drawn smoothly. */
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

    /** The mouse turns the camera at once (no smoothing between ticks, so it never lags behind the hand). */
    public static float yaw(float t) {
        return yaw;
    }

    public static float pitch(float t) {
        return pitch;
    }

    /** Mouse movement while the camera is detached (the same scale the game uses). */
    public static void turn(double dYaw, double dPitch) {
        turnHook = true;
        yaw += (float) (dYaw * 0.15);
        pitch = Math.max(-90f, Math.min(90f, pitch + (float) (dPitch * 0.15)));
    }

    // ------------------------------------------------------------ hooks report in

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

    /** True state of the movement keys while they are held back from the player: forward, back, left, right, up, down, sprint. */
    public static boolean[] keys() {
        return RAW;
    }

    /** Called from the key mixin: the player sees these keys as released while the freecam is on, the camera reads them here. */
    public static boolean filterDown(KeyMapping k, boolean real) {
        if (!freezing()) return real;
        Options o = Minecraft.getInstance().options;
        // holding attack or use would keep mining or placing from your body while you look elsewhere
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
