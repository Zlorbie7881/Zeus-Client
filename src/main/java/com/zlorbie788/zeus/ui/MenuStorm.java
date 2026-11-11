package com.zlorbie788.zeus.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

import java.util.Random;

final class MenuStorm {
    private MenuStorm() {}

    static final float LENGTH = 120f;
    private static final float RAMP_UP = 24f;
    private static final float RAMP_DOWN = 20f;
    private static final float SLANT_DEG = 11f;

    private static final Random RNG = new Random();
    private static long last;
    private static float visit;
    private static float nextStart = pickFirst();
    private static boolean storming;
    private static float clock;

    static float level;

    static boolean rising = true;

    static float cloudClock;
    private static float rainClock;

    static float flash;
    static float flashX = 0.5f;
    static float flashY = 0.3f;
    private static float nextStrike;
    private static float strikeAt = -10f;
    private static int strikeSeed;
    private static boolean strikeBolt;
    private static float boltX;
    private static float boltDrift;
    private static float boltDepth;
    private static float thunderAt = -1f;
    private static float thunderPitch = 0.8f;
    private static float thunderVolume = 0.8f;

    private static float pickFirst() {
        return 60f + 40f * RNG.nextFloat();
    }

    static void reset() {
        visit = 0f;
        nextStart = pickFirst();
        storming = false;
        clock = 0f;
        level = 0f;
        flash = 0f;
        strikeAt = -10f;
        nextStrike = 0f;
        thunderAt = -1f;
        last = 0;
    }

    static float update() {
        long now = System.nanoTime();
        float dt = last == 0 ? 0f : (now - last) / 1e9f;
        last = now;
        if (dt > 2.5f) {
            float keep = cloudClock;
            reset();
            last = now;
            cloudClock = keep;
            dt = 0f;
        }
        float anim = Math.min(dt, 0.05f);
        boolean allowed = UiSettings.MENU_STORM.on();
        if (!storming) {
            visit += dt;
            if (allowed && visit >= nextStart) {
                storming = true;
                clock = 0f;
                nextStrike = RAMP_UP * 0.5f + RNG.nextFloat() * 3f;
                strikeAt = -10f;
            }
        } else {
            clock += dt;
            if (!allowed) clock = Math.max(clock, LENGTH - RAMP_DOWN);
            if (clock >= LENGTH) {
                storming = false;
                visit = 0f;
                nextStart = 90f + 120f * RNG.nextFloat();
            }
        }
        if (storming) {
            rising = clock < LENGTH * 0.5f && allowed;
            float up = Math.min(1f, clock / RAMP_UP);
            float down = Math.min(1f, (LENGTH - clock) / RAMP_DOWN);
            float v = Math.max(0f, Math.min(up, down));
            level = v * v * (3f - 2f * v);
        } else {
            level = 0f;
        }
        cloudClock += anim * (1f + 1.4f * level);
        rainClock += anim;
        lightning();
        sounds();
        return cloudClock;
    }

    private static void lightning() {
        flash = 0f;
        if (!storming || level < 0.3f) return;
        if (UiSettings.CALM.on() || Perf.fast()) {
            if (clock >= nextStrike) {
                nextStrike = clock + 6f + RNG.nextFloat() * 10f;
                scheduleThunder(0.4f + RNG.nextFloat());
            }
            return;
        }
        if (clock >= nextStrike) {
            strikeAt = clock;
            strikeSeed++;
            strikeBolt = RNG.nextFloat() < 0.68f;
            boltX = 0.12f + 0.76f * RNG.nextFloat();
            boltDrift = (RNG.nextFloat() - 0.5f) * 0.24f;
            boltDepth = 0.45f + 0.30f * RNG.nextFloat();
            flashX = strikeBolt ? boltX : 0.15f + 0.7f * RNG.nextFloat();
            flashY = 0.22f + 0.2f * RNG.nextFloat();
            nextStrike = clock + (2.0f + 6.5f * RNG.nextFloat()) * (1.5f - 0.55f * level);
            scheduleThunder(0.25f + 1.4f * RNG.nextFloat());
        }
        float e = clock - strikeAt;
        if (e < 0f || e > 1.1f) return;
        float v = e < 0.07f ? e / 0.07f : e < 0.15f ? 0.30f : e < 0.24f ? 1f : Math.max(0f, 1f - (e - 0.24f) / 0.8f);
        flash = v * (0.55f + 0.45f * level);
    }

    private static void scheduleThunder(float delay) {
        thunderAt = clock + delay;
        thunderPitch = 0.62f + 0.4f * RNG.nextFloat();
        thunderVolume = 0.45f + 0.45f * RNG.nextFloat();
    }

    private static void sounds() {
        if (!storming || !UiSettings.THUNDER.on()) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) return;
            if (thunderAt >= 0f && clock >= thunderAt) {
                thunderAt = -1f;
                var ev = RNG.nextFloat() < 0.7f ? SoundEvents.LIGHTNING_BOLT_THUNDER : SoundEvents.LIGHTNING_BOLT_IMPACT;
                mc.getSoundManager().play(SimpleSoundInstance.forUI(ev, thunderPitch, thunderVolume * (0.4f + 0.6f * level)));
            }
        } catch (Throwable ignored) {
        }
    }

    static float sinceStrike() {
        return storming && strikeAt > -5f ? clock - strikeAt : -1f;
    }

    static int strikeId() {
        return strikeSeed;
    }

    static boolean on() {
        return level > 0.004f;
    }

    static void bolt(GuiGraphics g, int width, int height) {
        if (!strikeBolt || level < 0.3f || UiSettings.CALM.on() || Perf.fast()) return;
        float e = clock - strikeAt;
        if (e < 0f || e > 0.9f) return;
        float fl = e < 0.08f ? e / 0.08f : e < 0.18f ? 0.35f : e < 0.3f ? 1f : Math.max(0f, 1f - (e - 0.3f) / 0.6f);
        int seed = strikeSeed * 7 + (e > 0.18f && e < 0.3f ? 1 : 0);
        float x0 = width * boltX;
        float x1 = x0 + width * boltDrift;
        float y1 = height * (0.50f + 0.32f * boltDepth);
        float grow = Math.min(1f, e / 0.06f + 0.15f);
        Lightning.bolt(g, x0, -4, x1, y1, grow, seed, Math.min(1f, fl * 1.1f), 0xFF8EC8FF, 0xFFD6E6FF, Math.max(1.3f, height / 170f), Perf.fast() ? 1 : 2, 0.7f, 0.3f, 3);
        if (grow >= 1f) Lightning.bloom(g, x1, y1, height * 0.10f, 0xFFB4D2FF, 0.55f * fl);
    }

    private static final int RAIN_TEX = 512;

    static void rain(GuiGraphics g, int width, int height) {
        if (!on() || UiSettings.CALM.on() || Perf.fast() || MenuSnow.level() > 0.5f) return;
        String[] names = {"mid", "near"};
        float[] side = {1.0f, 1.45f};
        float[] speed = {1.7f, 2.6f};
        float[] alpha = {0.95f, 0.90f};
        double ang = Math.toRadians(SLANT_DEG);
        float dx = (float) -Math.sin(ang);
        float dy = (float) Math.cos(ang);
        float a = (float) Math.pow(level, 0.85) * 0.85f;
        int gs = Tex.guiScale();
        for (int i = 0; i < 2; i++) {
            int ts = Math.max(64, Math.round(height * side[i]));
            int px = Math.min(ts * gs, RAIN_TEX);
            Tex.Img img = Tex.fitted("storm_rain_" + names[i], "textures/gui/menu/rain_" + names[i] + ".png", px, px, false, 0, 0);
            if (img == null) continue;
            float dist = rainClock * speed[i] * height;
            float ox = ((dist * dx) % ts + ts) % ts;
            float oy = ((dist * dy) % ts + ts) % ts;
            int tint = Draw.rgba(0xFFFFFFFF, a * alpha[i]);
            int x0 = Math.round(ox) - ts;
            int y0 = Math.round(oy) - ts;
            for (int x = x0; x < width; x += ts) {
                for (int y = y0; y < height; y += ts) img.draw(g, x, y, ts, ts, tint);
            }
        }
    }

    static void warm(int width, int height, float cs, int gs, int cloudPx) {
        try {
            int kh = Math.round(cloudPx * 700f / 2560f);
            Tex.fitted("menu_storm_a", "textures/gui/menu/storm_a.png", cloudPx, kh, false, 0, 0);
            Tex.fitted("menu_storm_b", "textures/gui/menu/storm_b.png", cloudPx, kh, false, 0, 0);
            int fh = Math.round(cloudPx * 1000f / 2560f);
            Tex.fitted("menu_storm_full_a", "textures/gui/menu/storm_full_a.png", cloudPx, fh, false, 0, 0);
            Tex.fitted("menu_storm_full_b", "textures/gui/menu/storm_full_b.png", cloudPx, fh, false, 0, 0);
            Tex.fitted("menu_storm_scud", "textures/gui/menu/storm_scud.png", cloudPx, Math.round(cloudPx * 520f / 2560f), false, 0, 0);
            String[] names = {"mid", "near"};
            for (int i = 0; i < 2; i++) {
                int px = Math.min(Math.round(height * (i == 0 ? 1.0f : 1.45f)) * gs, RAIN_TEX);
                Tex.fitted("storm_rain_" + names[i], "textures/gui/menu/rain_" + names[i] + ".png", px, px, false, 0, 0);
            }
        } catch (RuntimeException | LinkageError e) {
            com.zlorbie788.zeus.core.Zeus.LOG.warn("Storm pictures could not be prepared", e);
        }
    }
}
