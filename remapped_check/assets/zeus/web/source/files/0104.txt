package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Env;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Setting;
import net.minecraft.client.Minecraft;

import java.util.List;

/**
 * Everything is lit like day, in caves, in the Nether and at night. Two methods work together:
 * <ul>
 * <li><b>Full light</b>: the one function that tells the chunk builder how bright a block face is (used by the game and by
 * Sodium alike) answers "full light", so the world itself is bright, and the dark inside caves, behind trees and in
 * corners is gone. Nothing is drawn differently, only the light value.</li>
 * <li><b>Night vision</b>: a hidden night vision effect on your own player, which also brightens mobs, items and the
 * sky, and works in every renderer even when the first method cannot be reached.</li>
 * </ul>
 */
public final class Fullbright extends Module {
    private static volatile boolean light;
    private static volatile boolean sawLight;

    private final Setting.Choice method = add(new Setting.Choice("Method", "Full light brightens the world itself, night vision brightens everything else. Both together is the brightest.", 0, "Both", "Full light only", "Night vision only"));
    private final Setting.Info status = add(new Setting.Info("Status", this::status));
    private int ticks;
    private boolean nightVision;

    public Fullbright() {
        super("fullbright", "Fullbright", "See in the dark: caves, nights and the Nether look like day.", Category.RENDER);
        method.onChange(() -> {
            if (enabled()) apply();
        });
    }

    private static volatile boolean sawEngine;

    /**
     * True when the light of a block position should read 15 on the calling thread: Fullbright is on in a light mode, or
     * X-Ray's "Light everything up" is on and this is a chunk-building thread. Never on the integrated server's thread.
     */
    public static boolean engineLight() {
        boolean fb = light;
        if (!fb && !XRay.fullbright()) return false;
        String n = Thread.currentThread().getName();
        if (n.startsWith("Server") || n.startsWith("server")) return false;
        return fb || XRay.onMeshThread();
    }

    public static void sawEngine() {
        sawEngine = true;
    }

    /** True while the block light hook should answer "full light". */
    public static boolean blockLight() {
        return light;
    }

    public static void sawLight() {
        sawLight = true;
    }

    /** Did any of the light hooks run? X-Ray asks, to know whether ores will be lit. */
    public static boolean lightHookRan() {
        return sawLight || sawEngine;
    }

    private List<String> status() {
        if (!enabled()) return List.of("Switched off");
        return List.of(
                "Full light, block hook: " + (!light ? "not used" : sawLight ? "working" : "waiting for the first chunk"),
                "Full light, light engine hook: " + (!light ? "not used" : sawEngine ? "working" : "waiting"),
                "Night vision: " + (nightVision ? "on" : "off"));
    }

    private void apply() {
        light = method.index() != 2;
        nightVision = method.index() != 1 || (light && !sawLight && !sawEngine && ticks > 60);
        if (nightVision) Env.effect("night_vision", 0);
        else if (!otherNightVision()) Env.effectOff("night_vision");
        rebuild();
    }

    private static boolean otherNightVision() {
        XRay x = com.zlorbie788.zeus.core.Zeus.get(XRay.class);
        return x != null && x.enabled() && XRay.fullbright();
    }

    private static void rebuild() {
        Minecraft mc = Minecraft.getInstance();
        try {
            if (mc.levelRenderer != null && mc.level != null) mc.levelRenderer.allChanged();
        } catch (RuntimeException | LinkageError ignored) {
            // the chunks pick the change up the next time they reload
        }
    }

    @Override
    protected void onEnable() {
        ticks = 0;
        sawLight = false;
        sawEngine = false;
        apply();
    }

    @Override
    protected void onTick(Minecraft mc) {
        ticks++;
        if (ticks % 200 == 0 && nightVision) Env.effect("night_vision", 0);
        // the light hook never ran (a renderer that does not ask the game for light): night vision takes over
        if (ticks == 80 && light && !sawLight && !sawEngine && !nightVision) apply();
    }

    @Override
    protected void onDisable() {
        light = false;
        nightVision = false;
        if (!otherNightVision()) Env.effectOff("night_vision");
        rebuild();
    }

    @Override
    public String info() {
        return method.value();
    }
}
