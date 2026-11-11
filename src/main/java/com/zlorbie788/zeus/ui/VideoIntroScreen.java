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

public final class VideoIntroScreen extends Screen {

    public record Film(String path, int frames, float fps, String sound, float[][] cues) {}

    public static final Film STARTUP = new Film("intro/f%03d.jpg", 200, 40f, "intro", new float[0][]);

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
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xFF000000);
        if (left) return;
        try {
            if (minecraft.getOverlay() != null) return;
        } catch (RuntimeException ignored) {
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
        float in = Math.max(0f, 1f - t / 0.25f);
        if (in > 0.01f) g.fill(0, 0, width, height, Draw.rgba(0xFF000000, in));
    }

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
            frame.drawPart(g, 0, y0, x0, dh, 0, 0, 2, vh, Draw.rgba(0xFFFFFFFF, 0.9f * flare));
            frame.drawPart(g, x0 + dw, y0, width - x0 - dw, dh, vw - 2, 0, 2, vh, Draw.rgba(0xFFFFFFFF, 0.9f * flare));
            int step = Math.max(2, x0 / 90);
            for (int i = 0; i < x0; i += step) {
                float k = i / (float) x0;
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
        }
    }

    private void thunder(float[] c) {
        if (!UiSettings.THUNDER.on()) return;
        try {
            var ev = c[3] == 0 ? net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_THUNDER : net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_IMPACT;
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(ev, c[1], c[2]));
        } catch (Throwable ignored) {
        }
    }

    private void leave() {
        if (left) return;
        left = true;
        stop = true;
        try {
            if (sound != null) minecraft.getSoundManager().stop(sound);
        } catch (Throwable ignored) {
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
