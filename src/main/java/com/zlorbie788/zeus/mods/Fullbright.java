package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Env;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Setting;
import net.minecraft.client.Minecraft;

import java.util.List;

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

    public static boolean blockLight() {
        return light;
    }

    public static void sawLight() {
        sawLight = true;
    }

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
