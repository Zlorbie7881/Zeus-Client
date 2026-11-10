package com.zlorbie788.zeus.ui;

import com.zlorbie788.zeus.core.Zeus;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.function.Supplier;

/**
 * Plays a Zeus intro film (the start-up one or the one of the Z key): a short film where the logo's ring draws itself 0 to 100 percent, then a thunderstorm breaks in the dark around the logo and it settles into a
 * steady glow. The game has no video player, so the film ships as a row of
 * pictures (one per frame, 40 a second) and a sound file. One picture is on the graphics card at a time; the next frame is
 * decoded when it is due. Any key or click skips it. Nothing starts until the game's own loading screen is gone.
 */
public final class VideoIntroScreen extends Screen {
    /** One film: its pictures, speed, sound and the extra thunder placed on its strikes (seconds, pitch, volume, 0 = thunder / 1 = impact). */
    public record Film(String path, int frames, float fps, String sound, float[][] cues) {}

    /** The start-up film: the ring draws itself, then a thunderstorm. Its sound (tools/intro_sound.py) has the thunder in it, so the game adds none. */
    public static final Film STARTUP = new Film("intro/f%03d.jpg", 200, 40f, "intro", new float[0][]);
    /** The film of the Z key: five seconds, its own sound only. Made by tools/panel_intro.py. */
    public static final Film PANEL = new Film("intro_panel/p%03d.jpg", 150, 30f, "panel_intro", new float[0][]);
    private static final int AHEAD = 8;

    private final Film film;
    private final Supplier<Screen> next;
    private final boolean fromTitle;
    private long t0;
    private long endAt;
    private int shown = -1;
    private volatile int vw = 1024;
    private volatile int vh = 576;
    private int cue;
    private final java.util.concurrent.ConcurrentHashMap<Integer, int[]> ready = new java.util.concurrent.ConcurrentHashMap<>();
    private volatile int want;
    private volatile boolean stop;
    private Thread worker;
    private Tex.Img frame;
    private SoundInstance sound;
    private boolean left;
    private final long born = System.nanoTime();

    public VideoIntroScreen(Supplier<Screen> next, boolean fromTitle) {
        this(STARTUP, next, fromTitle);
    }

    public VideoIntroScreen(Film film, Supplier<Screen> next, boolean fromTitle) {
        super(Component.literal("Zeus"));
        this.film = film;
        this.next = next;
        this.fromTitle = fromTitle;
        startWorker();
    }

    /** Decodes the next few frames ahead of the clock on a side thread, so the game thread only has to upload a picture. */
    private void startWorker() {
        worker = new Thread(() -> {
            while (!stop) {
                int from = want;
                boolean did = false;
                for (int i = from; i < Math.min(film.frames(), from + AHEAD) && !stop; i++) {
                    if (ready.containsKey(i)) continue;
                    try {
                        byte[] bytes = Tex.bytes(String.format(film.path(), i + 1));
                        if (bytes == null) continue;
                        BufferedImage img = javax.imageio.ImageIO.read(new ByteArrayInputStream(bytes));
                        if (img == null) continue;
                        vw = img.getWidth();
                        vh = img.getHeight();
                        ready.put(i, img.getRGB(0, 0, vw, vh, null, 0, vw));
                        did = true;
                    } catch (java.io.IOException | RuntimeException e) {
                        Zeus.LOG.warn("Intro frame {} could not be decoded", i, e);
                        ready.put(i, new int[0]);
                    }
                }
                ready.keySet().removeIf(k -> k < from - 1);
                if (!did) {
                    try {
                        Thread.sleep(4);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            }
        }, "zeus-intro-decoder");
        worker.setDaemon(true);
        worker.start();
    }

    public static boolean available() {
        return available(STARTUP);
    }

    public static boolean available(Film f) {
        return Tex.bytes(String.format(f.path(), 1)) != null;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // the picture is drawn in render
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xFF000000);
        if (left) return;
        try {
            if (minecraft.getOverlay() != null) return; // the game's loading screen is still up
        } catch (RuntimeException ignored) {
            // treat as loaded
        }
        long now = System.nanoTime();
        if (t0 == 0) {
            t0 = now;
            startSound();
        }
        float t = (now - t0) / 1e9f;
        while (cue < film.cues().length && t >= film.cues()[cue][0]) {
            thunder(film.cues()[cue]);
            cue++;
        }
        int idx = (int) (t * film.fps());
        if (idx >= film.frames()) {
            idx = film.frames() - 1;
            if (endAt == 0) endAt = now;
            if ((now - endAt) / 1e9f > 0.25f) {
                leave();
                return;
            }
        }
        want = idx;
        if (idx != shown) load(idx);
        if (frame == null) return;
        drawFrame(g, t);
        // a short fade in from black
        float in = Math.max(0f, 1f - t / 0.25f);
        if (in > 0.01f) g.fill(0, 0, width, height, Draw.rgba(0xFF000000, in));
    }

    /**
     * Draws the picture in the chosen way. The film is 16 : 9, the shape of most screens. Fit shows all of it and
     * carries the lightning on across the empty sides: the outermost pixels of the picture are smeared outwards and fade
     * to black, so the whole screen is alive and nothing is cut. Fill crops, Stretch widens.
     */
    private void drawFrame(GuiGraphics g, float t) {
        int mode = UiSettings.INTRO_FIT.index();
        if (mode == 2) {
            frame.draw(g, 0, 0, width, height, 0xFFFFFFFF);
            return;
        }
        if (mode == 1) {
            float sc = Math.max(width / (float) vw, height / (float) vh);
            int dw = Math.round(vw * sc);
            int dh = Math.round(vh * sc);
            frame.draw(g, (width - dw) / 2, (height - dh) / 2, dw, dh, 0xFFFFFFFF);
            return;
        }
        float sc = Math.min(width / (float) vw, height / (float) vh);
        int dw = Math.round(vw * sc);
        int dh = Math.round(vh * sc);
        int x0 = (width - dw) / 2;
        int y0 = (height - dh) / 2;
        float flare = 0.72f + 0.28f * (float) Math.sin(t * 9.0);
        if (x0 > 1) {
            // left and right: the edge column of the picture, stretched, then faded out
            frame.drawPart(g, 0, y0, x0, dh, 0, 0, 2, vh, Draw.rgba(0xFFFFFFFF, 0.9f * flare));
            frame.drawPart(g, x0 + dw, y0, width - x0 - dw, dh, vw - 2, 0, 2, vh, Draw.rgba(0xFFFFFFFF, 0.9f * flare));
            int step = Math.max(2, x0 / 90);
            for (int i = 0; i < x0; i += step) {
                float k = i / (float) x0; // 0 at the picture, 1 at the screen edge
                int a = Draw.rgba(0xFF000000, (float) Math.min(1.0, Math.pow(k, 0.7) * 0.97 + 0.03));
                g.fill(x0 - i - step, y0, x0 - i, y0 + dh, a);
                g.fill(x0 + dw + i, y0, x0 + dw + i + step, y0 + dh, a);
            }
        }
        if (y0 > 1) {
            frame.drawPart(g, x0, 0, dw, y0, 0, 0, vw, 2, Draw.rgba(0xFFFFFFFF, 0.9f * flare));
            frame.drawPart(g, x0, y0 + dh, dw, height - y0 - dh, 0, vh - 2, vw, 2, Draw.rgba(0xFFFFFFFF, 0.9f * flare));
            int step = Math.max(2, y0 / 90);
            for (int i = 0; i < y0; i += step) {
                float k = i / (float) y0;
                int a = Draw.rgba(0xFF000000, (float) Math.min(1.0, Math.pow(k, 0.7) * 0.97 + 0.03));
                g.fill(x0, y0 - i - step, x0 + dw, y0 - i, a);
                g.fill(x0, y0 + dh + i, x0 + dw, y0 + dh + i + step, a);
            }
        }
        frame.draw(g, x0, y0, dw, dh, 0xFFFFFFFF);
    }

    private void load(int idx) {
        // the newest decoded frame that is not past the one that is due (a late frame is skipped, never waited for)
        int use = -1;
        for (int k = idx; k >= Math.max(0, idx - 6) && use < 0; k--) if (ready.containsKey(k)) use = k;
        if (use < 0 || use == shown) return;
        int[] px = ready.remove(use);
        if (px == null) return;
        shown = use;
        try {
            int w = vw;
            int h = vh;
            if (frame == null || frame.w != w || frame.h != h) {
                if (frame != null) frame.release();
                frame = Tex.blank(w, h);
            }
            frame.put(px);
        } catch (RuntimeException e) {
            Zeus.LOG.warn("Intro frame {} could not be shown", use, e);
        }
        final int keep = use;
        ready.keySet().removeIf(k -> k < keep);
    }

    private void startSound() {
        if (!UiSettings.INTRO_SOUND.on()) return;
        try {
            SoundEvent ev = SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("zeus", film.sound()));
            sound = SimpleSoundInstance.forUI(ev, 1f, 1f);
            minecraft.getSoundManager().play(sound);
        } catch (Throwable ignored) {
            // no sound must never stop the picture
        }
    }

    private void thunder(float[] c) {
        if (!UiSettings.THUNDER.on()) return;
        try {
            var ev = c[3] == 0 ? net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_THUNDER : net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_IMPACT;
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(ev, c[1], c[2]));
        } catch (Throwable ignored) {
            // the film's own sound is enough
        }
    }

    private void leave() {
        if (left) return;
        left = true;
        stop = true;
        try {
            if (sound != null) minecraft.getSoundManager().stop(sound);
        } catch (Throwable ignored) {
            // already finished
        }
        Screen n = null;
        try {
            n = next.get();
        } catch (RuntimeException e) {
            Zeus.LOG.warn("Screen after the intro could not open", e);
        }
        minecraft.setScreen(n);
    }

    private boolean canSkip() {
        return (System.nanoTime() - born) / 1e9f > 0.3f;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (canSkip()) leave();
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (canSkip()) leave();
        return true;
    }

    @Override
    public void removed() {
        stop = true;
        try {
            if (sound != null && !left) minecraft.getSoundManager().stop(sound);
        } catch (Throwable ignored) {
            // nothing to stop
        }
        if (frame != null) {
            frame.release();
            frame = null;
        }
    }

    public boolean fromTitle() {
        return fromTitle;
    }
}
