package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Setting;
import com.zlorbie788.zeus.render.Projector;
import com.zlorbie788.zeus.ui.AuroraSky;
import com.zlorbie788.zeus.ui.Perf;
import com.zlorbie788.zeus.ui.SkyScape;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;

import java.util.List;

/**
 * A fully procedural night atmosphere. Every element is placed on the celestial sphere and projected through the active
 * camera: a layered aurora, a generated Milky Way, thousands of stars, drifting cloud banks and occasional meteors.
 * Nothing is a screen-space picture, so the sky remains coherent as the player looks around, including straight up.
 */
public final class Atmosphere extends Module {
    private final Setting.Num brightness = add(new Setting.Num("Brightness", "Overall strength of the night atmosphere.", 100, 30, 100, 5, "%"));
    private final Setting.Num motion = add(new Setting.Num("Motion", "Speed of the aurora folds and cloud drift. 0 pauses the movement.", 100, 0, 250, 10, "%"));
    private final Setting.Bool twinkle = add(new Setting.Bool("Twinkling stars", "Stars gently vary in brightness.", true));
    private final Setting.Choice shooting = add(new Setting.Choice("Shooting stars", "Average time between falling stars.", 2, "Off", "Rare", "Sometimes", "Often"));
    private final Setting.Choice detail = add(new Setting.Choice("Detail", "Rendering detail. Automatically reduced when performance drops.", 2, "Low", "Medium", "High"));
    private final Setting.Choice when = add(new Setting.Choice("Show", "When the atmosphere is visible.", 0, "Clear nights", "Nights, also in rain", "Always"));
    private final Setting.Bool hills = add(new Setting.Bool("Hide behind land", "Hide the atmosphere below hills, mountains and trees.", true));
    private final Setting.Info status = add(new Setting.Info("Status", this::status));

    private static final float[] METEOR_GAP = {0f, 30f, 14f, 7f};
    private static final int[] HAZE_COLS = {18, 12, 8};
    private static final int[] HAZE_ROWS = {18, 12, 8};
    private static final int[] STAR_COUNTS = {3000, 5500, 8000};
    private static final int[] CLOUD_DETAIL = {2, 3, 4};

    private final AuroraSky aurora = new AuroraSky();
    private final AuroraSky.Style style = new AuroraSky.Style();
    private final SkyScape scape = new SkyScape(0x5A17C0DE);
    private final com.zlorbie788.zeus.ui.SkyPhoto.Skyline skyline = new com.zlorbie788.zeus.ui.SkyPhoto.SimpleSkyline();
    private final double[] dir = new double[3];

    private float fade;
    private float clock;
    private long last;
    private String why = "";
    private boolean warned;

    public Atmosphere() {
        super("atmosphere", "Atmosphere", "A living night sky with flowing northern lights, a generated Milky Way, stars, clouds and shooting stars.", Category.RENDER);
        style.seed = 0x5A17C0DE;
        style.curtains = 6;
        style.scheme = 5;
        style.height = 22f;
        style.bearing = 0f;
        style.spread = 178f;
        style.activity = 0.88f;
        style.surges = true;
    }

    @Override
    protected void onDisable() {
        fade = 0f;
        last = 0L;
        skyline.reset();
    }

    @Override
    public String info() {
        return "Living night sky";
    }

    private List<String> status() {
        if (!enabled()) return List.of("Switched off");
        return List.of(
                "Showing: " + (fade > 0.02f ? Math.round(fade * 100f) + "%" : "hidden"),
                "Why: " + (why.isEmpty() ? "-" : why),
                "Stars: procedural", "Shooting stars in the sky: " + scape.meteorCount(),
                "Clouds: " + (scape.cloudsReady() ? "ready" : "forming"));
    }

    @Override
    protected void onHudWorld(GuiGraphics g, Minecraft mc, float partial, int w, int h) {
        long now = System.nanoTime();
        float dt = last == 0L ? 0f : Math.min(0.1f, (now - last) / 1e9f);
        last = now;
        if (mc.level == null || mc.player == null) return;

        float target = target(mc, partial);
        fade += (target - fade) * (1f - (float) Math.exp(-dt * 1.05f));
        if (Math.abs(target - fade) < 0.004f) fade = target;
        if (fade <= 0.01f) return;

        float speed = (float) motion.get() / 100f;
        clock += dt * speed;
        if (!Projector.prepare(mc, partial, w, h)) return;

        int quality = detail.index();
        int perf = Perf.level();
        if (perf >= 2) quality = 0;
        else if (perf == 1) quality = Math.max(0, quality - 1);

        boolean land = hills.on();
        if (land) skyline.sweep(mc);
        final boolean useLand = land && skyline.ready();
        class AtmoView implements com.zlorbie788.zeus.ui.SkyPhoto.View, com.zlorbie788.zeus.ui.AuroraSky.View {
            @Override
            public boolean at(float sx, float sy, float[] azel) {
                if (!Projector.ray(sx, sy, dir)) return false;
                azel[0] = (float) Math.toDegrees(Math.atan2(dir[0], -dir[2]));
                azel[1] = (float) Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, dir[1]))));
                return true;
            }

            @Override
            public boolean hasSkyline() {
                return useLand;
            }

            @Override
            public float skyline(float az) {
                return useLand ? skyline.at(az) : -90f;
            }
        }
        com.zlorbie788.zeus.ui.SkyPhoto.View scapeView = new AtmoView();
        com.zlorbie788.zeus.ui.AuroraSky.View auroraView = new AtmoView();

        try {
            float gain = fade * brightness.f() / 100f;
            float auroraGain = gain * 0.92f;
            style.activity = 0.88f;
            aurora.begin(clock, style);

            // Deep blue atmospheric gradient and the directionally generated Milky Way.
            scape.renderHaze(g, w, h, scapeView, gain * 0.88f, HAZE_COLS[quality], HAZE_ROWS[quality], true);

            // Broad, folded aurora curtains are drawn behind the closer cloud banks.
            int auroraCol = Math.max(2, Math.round(w / (float) (quality == 0 ? 90 : quality == 1 ? 150 : 220)));
            int auroraRow = Math.max(4, Math.round(h / (float) (quality == 0 ? 20 : quality == 1 ? 32 : 44)));
            aurora.render(g, 0, 0, w, h, auroraView, auroraGain, auroraCol, auroraRow);

            // Stars sit behind the cloud banks; each has its own subtle shimmer.
            float starGain = gain * (twinkle.on() ? 1f : 0.88f);
            scape.renderStars(g, scapeView, starGain, STAR_COUNTS[quality], clock * (twinkle.on() ? 1f : 0f),
                    Math.max(1, (int) mc.getWindow().getGuiScale()));

            // Clouds are generated from procedural puff geometry and slowly drift across the celestial sphere.
            scape.renderClouds(g, scapeView, gain * 0.72f, CLOUD_DETAIL[quality], clock, speed, aurora.surge() * 0.55f);

            scape.updateMeteors(clock, METEOR_GAP[shooting.index()]);
            scape.renderMeteors(g, scapeView, gain, Math.max(1, (int) mc.getWindow().getGuiScale()));
        } catch (RuntimeException | LinkageError e) {
            if (!warned) com.zlorbie788.zeus.core.Zeus.LOG.warn("The procedural atmosphere could not be drawn", e);
            warned = true;
        }
    }

    /** Visibility follows the real world: dimension, time of day, weather and whether the player can see the sky. */
    private float target(Minecraft mc, float partial) {
        if (mc.level.dimension() != Level.OVERWORLD) {
            why = "only in the overworld";
            return 0f;
        }

        float nightNow = 1f;
        if (when.index() != 2) {
            try {
                net.minecraft.world.phys.Vec3 at = mc.player.position();
                float stars = mc.level.environmentAttributes().getValue(net.minecraft.world.attribute.EnvironmentAttributes.STAR_BRIGHTNESS, at);
                nightNow = Math.max(0f, Math.min(1f, (stars - 0.05f) / 0.28f));
            } catch (RuntimeException | LinkageError e) {
                nightNow = 1f;
            }
        }

        float rain = 0f;
        if (when.index() == 0) {
            try {
                rain = Math.max(mc.level.getRainLevel(partial), mc.level.getThunderLevel(partial));
            } catch (RuntimeException | LinkageError e) {
                rain = 0f;
            }
        }

        int skyLight = 15;
        try {
            BlockPos p = mc.player.blockPosition().above();
            skyLight = mc.level.getBrightness(LightLayer.SKY, p);
        } catch (RuntimeException | LinkageError e) {
            skyLight = 15;
        }
        float open = Math.max(0f, Math.min(1f, (skyLight - 5f) / 8f));

        if (nightNow < 0.01f) why = "it is day";
        else if (rain > 0.5f) why = "rain";
        else if (open < 0.05f) why = "no open sky";
        else why = when.index() == 2 ? "always on" : "clear night";
        return nightNow * (1f - rain) * open;
    }
}
