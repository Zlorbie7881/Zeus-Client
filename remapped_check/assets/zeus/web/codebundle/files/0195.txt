package com.zlorbie788.zeus.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * The sounds of the Zeus menu.
 *
 * <ul>
 *   <li>a soft low rumble when the pointer reaches a stone plate (a little higher and quieter for the round buttons),</li>
 *   <li>a sharp electric crack with a thump when something is clicked,</li>
 *   <li>low wind and soft rain under the whole menu: two looping sounds that fade in while the menu is on the screen, swell
 *       with the storm, turn to wind alone in the snow, and fade out again when the menu is left.</li>
 * </ul>
 *
 * <p>The wind and the rain are looped by the game itself (the files join their own ends without a seam). They are kept alive
 * only while {@link #ambience} is called every frame by the menu's scene; {@link #tick} (once per game tick) fades them out
 * when it stops being called, and ends them.
 */
public final class MenuAudio {
    private MenuAudio() {}

    private static SoundEvent event(String name) {
        return SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("zeus", name));
    }

    private static final SoundEvent RUMBLE = event("menu_rumble");
    private static final SoundEvent CRACK = event("menu_crack");
    private static final SoundEvent WIND = event("menu_wind");
    private static final SoundEvent RAIN = event("menu_rain");

    /** Set by the backdrop that sits behind the game's other screens: the same sounds, but quieter. */
    static boolean backdrop;

    private static Loop wind;
    private static Loop rain;
    private static long lastPoke;
    private static long lastHover;

    /** Plays the rumble of a plate or a round button being pointed at. */
    static void hover(boolean plate, int index) {
        if (!UiSettings.MENU_SOUNDS.on()) return;
        long now = System.nanoTime();
        if (now - lastHover < 90_000_000L) return;
        lastHover = now;
        float pitch = plate ? 0.82f + 0.07f * (index % 3) : 1.45f;
        float vol = plate ? 0.55f : 0.30f;
        play(RUMBLE, pitch, vol);
    }

    /** Plays the crack of a click. */
    static void click() {
        if (!UiSettings.MENU_SOUNDS.on()) return;
        play(CRACK, 0.95f + 0.1f * (float) Math.random(), 0.75f);
    }

    private static void play(SoundEvent ev, float pitch, float volume) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) mc.getSoundManager().play(SimpleSoundInstance.forUI(ev, pitch, volume));
        } catch (Throwable ignored) {
            // a sound must never stop the picture
        }
    }

    /**
     * Called every frame the menu's sky is drawn. {@code storm} is 0 (clear) to 1, {@code snow} is 0 to 1.
     */
    static void ambience(float storm, float snow) {
        lastPoke = System.nanoTime();
        if (!UiSettings.MENU_AMBIENCE.on()) return;
        float master = (float) (UiSettings.AMBIENCE_VOLUME.get() / 100.0) * (backdrop ? 0.55f : 1f);
        float w = (0.10f + 0.55f * storm + 0.20f * snow) * master;
        float r = (0.07f + 0.60f * storm) * (1f - snow) * master;
        float wp = 0.94f + 0.14f * storm;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) return;
            if (wind == null && w > 0.002f) {
                wind = new Loop(WIND);
                mc.getSoundManager().play(wind);
            }
            if (rain == null && r > 0.004f) {
                rain = new Loop(RAIN);
                mc.getSoundManager().play(rain);
            }
        } catch (Throwable ignored) {
            wind = null;
            rain = null;
        }
        if (wind != null) { wind.target = w; wind.targetPitch = wp; }
        if (rain != null) { rain.target = r; rain.targetPitch = 0.98f + 0.04f * storm; }
    }

    /** Once per game tick: fades the loops out when the menu is no longer drawn, and ends them. */
    public static void tick() {
        if (wind == null && rain == null) return;
        boolean away = System.nanoTime() - lastPoke > 350_000_000L || !UiSettings.MENU_AMBIENCE.on();
        if (away) {
            if (wind != null) wind.target = 0f;
            if (rain != null) rain.target = 0f;
        }
        if (wind != null && wind.finished()) wind = null;
        if (rain != null && rain.finished()) rain = null;
    }

    /** One looping, relative sound whose volume and pitch follow targets, a little every game tick. */
    private static final class Loop extends AbstractTickableSoundInstance {
        float target;
        float targetPitch = 1f;
        private boolean ending;

        Loop(SoundEvent ev) {
            super(ev, SoundSource.AMBIENT, RandomSource.create());
            this.looping = true;
            this.delay = 0;
            this.volume = 0.001f;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
        }

        @Override
        public void tick() {
            this.volume += (target - this.volume) * 0.06f;
            this.pitch += (targetPitch - this.pitch) * 0.04f;
            if (target <= 0f && this.volume < 0.004f) {
                ending = true;
                stop();
            }
        }

        boolean finished() {
            return ending || isStopped();
        }
    }
}
