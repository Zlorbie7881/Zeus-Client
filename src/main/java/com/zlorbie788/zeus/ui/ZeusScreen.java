package com.zlorbie788.zeus.ui;

import com.mojang.blaze3d.platform.InputConstants;
import com.zlorbie788.zeus.client.ZeusClient;
import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Env;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Setting;
import com.zlorbie788.zeus.core.Usage;
import com.zlorbie788.zeus.core.Zeus;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ZeusScreen extends Screen {
    private static final int HEAD = 48;
    private static final int FOOT = 22;
    private static final int SIDE = 128;
    private static final int PAGE_ACTIVE = Category.values().length;
    private static final int PAGE_PANEL = PAGE_ACTIVE + 1;
    private static final int PAGE_FAV = PAGE_PANEL + 1;

    private static final int WIN_W = 98;

    private static final int WIN_ROWS = 5;
    private static final int WIN_ROWS_SHORT = 4;
    private static final int WIN_HEAD = 15;
    private static final int ROW_H = 13;

    private static final int WIN_GAP = 5;

    private static int lastPage = 0;
    private static final Set<String> OPEN = new HashSet<>();
    private static final List<String> ORDER = new ArrayList<>();
    static {
        ORDER.add(UiState.FAVOURITES);
        for (Category c : Category.values()) ORDER.add(c.name());
    }

    private int px;
    private int py;
    private int pw;
    private int ph;
    private int page;

    private float scroll;
    private float scrollShown;
    private int contentH;
    private int listX;
    private int listY;
    private int listW;
    private int listH;

    private final Map<String, Float> expand = new HashMap<>();
    private final Map<String, Float> toggle = new HashMap<>();
    private final Map<String, Float> hover = new HashMap<>();
    private float pillY = -1;
    private EditBox search;

    private EditBox field;
    private Setting.Text editing;
    private boolean fieldDrawn;

    private final long bornNanos = System.nanoTime();
    private final long startNanos = bornNanos;
    private long lastNanos;
    private float offset;
    private final float speed;
    private final boolean intro;
    private int soundIdx;

    private Module listening;
    private boolean listeningOpen;
    private Setting.Num dragging;
    private int dragX;
    private int dragW;

    private int mx;
    private int my;
    private String tip;
    private boolean columns;

    private boolean menuPanel;

    private String dragWin;
    private boolean dragPopup;
    private float dragOffX;
    private float dragOffY;
    private float pressX;
    private float pressY;
    private boolean dragMoved;
    private Module popup;
    private boolean popupPanel;
    private float popupX = -1;
    private float popupY = -1;
    private float popupScroll;
    private float popupReveal;
    private int popupContent;
    private int popupViewTop;
    private int popupViewH;
    private final Map<String, int[]> winRect = new HashMap<>();

    private final Map<String, float[]> winPos = new HashMap<>();

    private final Map<String, Float> windowOpen = new HashMap<>();

    private interface Click {
        void on(int button);
    }

    private record Hit(int x, int y, int w, int h, Click run) {}

    private final List<Hit> hits = new ArrayList<>();

    private final Screen parent;
    private static boolean videoSeen;

    public ZeusScreen() {
        this(null);
    }

    public ZeusScreen(Screen parent) {
        super(Component.literal("Zeus"));
        this.parent = parent;
        this.menuPanel = parent instanceof ZeusTitleScreen;
        int mode = UiSettings.INTRO.index();
        intro = mode == 2 || mode == 3;
        speed = mode == 3 ? 2.1f : 1f;
        page = Math.max(0, Math.min(PAGE_FAV, lastPage));
    }

    private static String pendingSearch;

    public static Screen at(String query) {
        pendingSearch = query;
        return new ZeusScreen(null);
    }

    public static Screen make(Screen parent) {
        int mode = UiSettings.INTRO.index();
        boolean video = (mode == 0 || (mode == 1 && !videoSeen)) && !UiSettings.CALM.on();
        if (video && VideoIntroScreen.available(VideoIntroScreen.PANEL)) {
            videoSeen = true;
            return new VideoIntroScreen(VideoIntroScreen.PANEL, () -> new ZeusScreen(parent), false);
        }
        if (video && VideoIntroScreen.available()) {
            videoSeen = true;
            return new VideoIntroScreen(() -> new ZeusScreen(parent), false);
        }
        return new ZeusScreen(parent);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    private boolean windowsLayout() {
        return UiSettings.LAYOUT.index() == 1 && !menuPanel;
    }

    private void setLayout(int i) {
        menuPanel = false;
        UiSettings.LAYOUT.set(i);
    }

    @Override
    protected void init() {
        if (!UiSettings.LAYOUT_WINDOWS_DEFAULT.on()) {
            UiSettings.LAYOUT_WINDOWS_DEFAULT.set(true);
            UiSettings.LAYOUT.set(1);
        }
        if (!windowsLayout()) {
            try {
                layoutPanel();
                if (!artFailed) backdrop();
            } catch (Throwable t) {
                artFailed = true;
                Zeus.LOG.warn("Panel backdrop could not be prepared", t);
            }
        }
        pw = Math.min(width - 16, 680);
        ph = Math.min(height - 16, 380);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        if (search == null) {
            search = new EditBox(minecraft.font, 0, 0, 100, 12, Component.empty());
            search.setMaxLength(32);
            search.setBordered(false);
            search.setHint(Component.literal("Search modules"));
            search.setResponder(s -> scroll = 0);
        }
        addRenderableWidget(search);
        if (pendingSearch != null) {
            search.setValue(pendingSearch);
            search.setCursorPosition(pendingSearch.length());
            pendingSearch = null;
        }
        search.setFocused(true);
        setFocused(search);
        setInitialFocus(search);
        if (field == null) {
            field = new EditBox(minecraft.font, 0, 0, 100, 12, Component.empty());
            field.setBordered(false);
            field.setResponder(v -> {
                if (editing != null) editing.set(v);
            });
        }
        editing = null;
        field.setFocused(false);
        field.visible = false;
        addRenderableWidget(field);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        Ty.release();
        Zeus.save();
    }

    private float time() {
        if (!intro) return Intro.LENGTH + 1f;
        return (System.nanoTime() - startNanos) / 1e9f * speed + offset;
    }

    private float anim() {
        return (System.nanoTime() - bornNanos) / 1e9f;
    }

    private void skipIntro() {
        float t = time();
        if (t < Intro.LEAVE) {
            offset += Intro.LEAVE - t;
            soundIdx = 4;
        }
    }

    private static final java.util.Map<String, String> CARD_KEYS = new java.util.HashMap<>();

    private static String cardKey(String id) {
        return CARD_KEYS.computeIfAbsent(id, k -> "card" + k);
    }

    private float ease(String key, boolean on, float dt) {
        float v = hover.getOrDefault(key, 0f);
        v += ((on ? 1f : 0f) - v) * Math.min(1f, dt * 16f);
        if (Math.abs((on ? 1f : 0f) - v) < 0.01f) v = on ? 1f : 0f;
        hover.put(key, v);
        return v;
    }

    private float dtNow;

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (bgDrawn) return;
        bgDrawn = true;
        if (windowsLayout()) g.fillGradient(0, 0, width, height, 0x90040610, 0xB8070B16);
        else g.fillGradient(0, 0, width, height, 0xC0040610, 0xE0070B16);
    }

    private boolean bgDrawn;
    private long pageAt = System.nanoTime();
    private static final java.util.Map<String, Long> BURSTS = new java.util.HashMap<>();

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        mx = mouseX;
        my = mouseY;
        Accent.contrast(UiSettings.CONTRAST.on());
        fieldDrawn = false;
        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 0.016f : Math.min(0.1f, (now - lastNanos) / 1e9f);
        lastNanos = now;
        dtNow = dt;
        float t = time();
        boolean playing = t < Intro.LENGTH;
        int accent = Accent.main();
        int second = Accent.second();
        columns = windowsLayout();

        renderBackground(g, mouseX, mouseY, partialTick);

        if (playing && UiSettings.THUNDER.on()) {
            float[] at = {Intro.STRIKE1, Intro.STRIKE2, Intro.STRIKE3, Intro.FORMED};
            while (soundIdx < 4 && t >= at[soundIdx]) {
                play(soundIdx == 3);
                soundIdx++;
            }
        }

        float shake = playing && !UiSettings.CALM.on() ? Intro.shake(t) : 0f;
        g.pose().pushMatrix();
        if (shake > 0.01f) {
            int f = (int) (t * 40f);
            g.pose().translate((float) Math.sin(f * 12.9898) * 2.4f * shake, (float) Math.cos(f * 78.233) * 2.4f * shake);
        }

        float open = playing ? Math.max(0f, Math.min(1f, (t - Intro.LEAVE + 0.1f) / 0.7f)) : 1f;
        if (!columns) layoutPanel();
        float logoX = columns ? 24 : (bx + K(24) + K(33)) / (float) gs;
        float logoY = columns ? 22 : (by + K(44)) / (float) gs;
        float logoSize = columns ? 24f : Math.max(24f, K(66) * 0.8f / gs);
        if (open > 0f) {
            float e = open * open * (3f - 2f * open);
            float sc = 0.94f + 0.06f * e;
            g.pose().pushMatrix();
            g.pose().translate(width / 2f, height / 2f);
            g.pose().scale(sc, sc);
            g.pose().translate(-width / 2f, -height / 2f);
            if (columns) drawWindows(g, dt, playing ? 0 : 1, accent, second);
            else drawPanel(g, dt, playing ? 0 : 1, accent, second);
            if (open < 1f) {
                if (columns) g.fill(0, 0, width, height, Draw.rgba(0xFF05070F, 1f - e));
                else g.fill(px - 14, py - 14, px + pw + 14, py + ph + 14, Draw.rgba(0xFF05070F, 1f - e));
            }
            g.pose().popMatrix();
        }
        if (playing) {
            g.nextStratum();
            float fade = 1f - Math.max(0f, Math.min(1f, (t - Intro.LEAVE) / 0.5f));
            Intro.backdrop(g, width, height, t, accent, fade);
            float leave = Math.max(0f, Math.min(1f, (t - Intro.LEAVE) / (Intro.LENGTH - Intro.LEAVE)));
            Intro.front(g, width, height, t, accent, second, leave, logoX, logoY, logoSize);
            float fl = Intro.flash(t);
            if (fl > 0.01f) g.fill(0, 0, width, height, Draw.rgba(0xFFDCEBFF, fl * (UiSettings.CALM.on() ? 0.12f : 0.55f)));
        }
        g.pose().popMatrix();
        bgDrawn = false;
        if (editing != null && !fieldDrawn) stopEditing();
    }

    private void play(boolean thunder) {
        try {
            var sound = thunder ? SoundEvents.LIGHTNING_BOLT_THUNDER : SoundEvents.LIGHTNING_BOLT_IMPACT;
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound, thunder ? 0.85f : 1.2f, thunder ? 0.5f : 0.3f));
        } catch (Throwable ignored) {
        }
    }

    private int gs = 2;
    private float k = 1f;

    private int bx;
    private int by;
    private int bw;
    private int bh;
    private int listTop;
    private int listBottom;
    private int listLeft;
    private int listWide;

    private int sbRight;
    private int footY;
    private boolean artFailed;
    private int filter;
    private final float[] fpsHist = new float[30];
    private long fpsAt;
    private final java.util.IdentityHashMap<Object, Float> sAnim = new java.util.IdentityHashMap<>();
    private int dragMul = 1;
    private static final String[] SIDE_KEYS = new String[32];

    static {
        for (int i = 0; i < SIDE_KEYS.length; i++) SIDE_KEYS[i] = "side" + i;
    }

    private static final int TXT = 0xFFEAF2FF;

    private int K(double d) {
        return Math.round((float) (d * k));
    }

    private float tp(double px) {
        return Math.max(9f, (float) (px * k));
    }

    private int cDim() {
        return UiSettings.CONTRAST.on() ? 0xFFDCE6FA : 0xFF9FB2D9;
    }

    private int cSub() {
        return UiSettings.CONTRAST.on() ? 0xFFC3D2F2 : 0xFF7F93BD;
    }

    private int cMut() {
        return UiSettings.CONTRAST.on() ? 0xFFAFC0E6 : 0xFF5D7099;
    }

    private int cLine() {
        return UiSettings.CONTRAST.on() ? 0x806096FF : 0x296096FF;
    }

    private int blue(int accent) {
        return UiSettings.ACCENT.index() == 0 ? 0xFF2F86FF : accent;
    }

    private static int lite(int bl) {
        return Draw.mix(bl, 0xFFFFFFFF, 0.38f);
    }

    private void tx(GuiGraphics g, String s, int x, int cy, double px, int weight, int color) {
        Ty.text(g, s, x, cy, tp(px), weight, color);
    }

    private void txR(GuiGraphics g, String s, int xr, int cy, double px, int weight, int color) {
        Ty.textR(g, s, xr, cy, tp(px), weight, color);
    }

    private void txC(GuiGraphics g, String s, int cx, int cy, double px, int weight, int color) {
        Ty.textC(g, s, cx, cy, tp(px), weight, color);
    }

    private int tw(String s, double px, int weight) {
        return Ty.w(s, tp(px), weight);
    }

    private void spaced(GuiGraphics g, String s, int x, int cy, double px, int weight, int color, double gap) {
        float p = tp(px);
        float xx = x;
        float sp = (float) (gap * k);
        for (int i = 0; i < s.length(); i++) {
            String ch = String.valueOf(s.charAt(i));
            Ty.text(g, ch, Math.round(xx), cy, p, weight, color);
            xx += Ty.w(ch, p, weight) + sp;
        }
    }

    private int spacedW(String s, double px, int weight, double gap) {
        float p = tp(px);
        float w = 0;
        for (int i = 0; i < s.length(); i++) w += Ty.w(String.valueOf(s.charAt(i)), p, weight) + (float) (gap * k);
        return Math.round(w - (float) (gap * k));
    }

    private void layoutPanel() {
        gs = Tex.guiScale();
        k = Math.max(0.5f, Math.min(height * gs / 941f, width * gs / 1672f));
        int m = Math.max(2, K(8));
        bx = m;
        by = m;
        bw = width * gs - 2 * m;
        bh = height * gs - 2 * m;
        px = Math.round(bx / (float) gs);
        py = Math.round(by / (float) gs);
        pw = Math.round(bw / (float) gs);
        ph = Math.round(bh / (float) gs);
    }

    private Tex.Img backdrop() {
        float kk = Math.min(1f, 1920f / Math.max(1, bw));
        int rw = Math.max(64, Math.round(bw * kk));
        int rh = Math.max(64, Math.round(bh * kk));
        Tex.Img have = Tex.cached("panelbg2");
        if (have != null && have.w == rw && have.h == rh) return have;
        return Tex.made("panelbg2", PanelArt.render(rw, rh, Math.round(K(18) * kk)), rw, rh);
    }

    private static Tex.Img ramp() {
        Tex.Img have = Tex.cached("pz_ramp");
        if (have != null) return have;
        int[] px = new int[256];
        for (int i = 0; i < 256; i++) {
            float t = i / 255f;
            float a = t >= 0.66f ? 0f : (float) Math.pow(1f - t / 0.66f, 1.35);
            px[i] = (Math.round(a * 255f) << 24) | 0xFFFFFF;
        }
        return Tex.made("pz_ramp", px, 256, 1);
    }

    private float animS(Object key, float target, float dt) {
        float v = sAnim.getOrDefault(key, target);
        v += (target - v) * Math.min(1f, dt * 18f);
        if (Math.abs(target - v) < 0.01f) v = target;
        sAnim.put(key, v);
        return v;
    }

    private boolean inViewD() {
        return my >= listTop && my < listBottom;
    }

    private void drawPanel(GuiGraphics g, float dt, int live, int accent, int second) {
        hits.clear();
        tip = null;
        boolean interactive = live == 1;
        int rmx = mx;
        int rmy = my;
        layoutPanel();
        mx = interactive ? rmx * gs : -100000;
        my = interactive ? rmy * gs : -100000;
        int bl = blue(accent);
        int bl2 = lite(bl);
        Ty.begin();
        g.pose().pushMatrix();
        g.pose().scale(1f / gs, 1f / gs);

        int r = K(18);
        Tex.Img bg = null;
        if (!artFailed) {
            try {
                bg = backdrop();
            } catch (Throwable t) {
                artFailed = true;
                Zeus.LOG.warn("Panel backdrop could not be drawn", t);
            }
        }
        if (bg != null) bg.draw(g, bx, by, bw, bh, 0xFFFFFFFF);
        else Ty.fill(g, bx, by, bw, bh, r, 0xF2060A16);
        Ty.glow(g, bx, by, bw, bh, r, Math.max(6, K(26)), Draw.rgba(bl, 0.22f));
        Ty.ring(g, bx, by, bw, bh, r, Math.max(1, Math.round(k)), Draw.rgba(bl, 0.62f));

        int hh = K(88);
        drawHeader(g, bl, bl2, hh);

        int bodyTop = by + hh + K(16);
        int bodyBottom = by + bh - K(74);
        int railX = bx + K(16);
        int railW = K(284);
        int mainX = railX + railW + K(16);
        int mainR = bx + bw - K(16);
        drawRail(g, railX, bodyTop, railW, bodyBottom - bodyTop, bl, bl2);

        sbRight = Math.round(mainX / (float) gs);
        listLeft = mainX;
        listWide = mainR - mainX - K(16);
        int headBottom = bodyTop + K(62);
        listTop = bodyTop + K(98);
        listTop -= listTop % gs;
        listBottom = bodyBottom - (bodyBottom % gs);
        drawList(g, dt, bl, bl2, bodyTop, mainX, mainR);

        footY = by + bh - K(58);
        drawPanelFooter(g, bl, bl2, footY);

        if (tip != null && interactive) drawTipD(g, bl);
        g.pose().popMatrix();
        Ty.flush();
        mx = rmx;
        my = rmy;
    }

    private void headerButton(GuiGraphics g, int x, int y, int s, String glyph, int tint, int hotTint, int bl, Click run, String tipText) {
        boolean hot = hov(x, y, s, s);
        Ty.fill(g, x, y, s, s, K(13), hot ? 0xC00E1C40 : 0x85081026);
        Ty.ring(g, x, y, s, s, K(13), Math.max(1, Math.round(k)), hot ? Draw.rgba(bl, 0.8f) : 0x4D6096FF);
        if (hot && !UiSettings.CALM.on() && !Perf.fast()) {
            Anim.ring(g, x, y, s, anim(), bl, 0xFFDCEBFF, 1f);
        }
        Glyphs.drawPx(g, glyph, x + s / 2f, y + s / 2f, K(22), hot ? hotTint : tint);
        hit(x, y, s, s, run, tipText);
    }

    private void drawHeader(GuiGraphics g, int bl, int bl2, int hh) {
        int cy = by + hh / 2;
        int line = Math.max(1, Math.round(k));
        int ls = K(66);
        int lx = bx + K(24);
        Tex.Img logo = Tex.fitted("pz_logo", "textures/gui/logo_z.png", ls, ls, false, 0.5f, 0.5f);
        if (logo != null) logo.draw(g, lx, cy - ls / 2, ls, ls, 0xFFFFFFFF);
        int wh = K(40);
        int ww = Math.round(wh * 850f / 120f);
        int wx = lx + ls + K(18);
        Tex.Img word = Tex.fitted("pz_word", "textures/gui/panel_lettering.png", ww, wh, false, 0.5f, 0.5f);
        int wordEnd = wx + ww;
        if (word != null) {
            word.draw(g, wx, cy - wh / 2, ww, wh, 0xFFFFFFFF);
        } else {
            spaced(g, "ZEUS CLIENT", wx, cy, 27, Ty.XBOLD, 0xFFF3F8FF, 9);
            wordEnd = wx + spacedW("ZEUS CLIENT", 27, Ty.XBOLD, 9);
        }
        String ver = "v" + Zeus.VERSION;
        int vpw = tw(ver, 12, Ty.SEMI) + K(22);
        int vph = K(26);
        int vpx = wordEnd + K(18);
        Ty.fill(g, vpx, cy - vph / 2, vpw, vph, vph / 2, Draw.rgba(bl, 0.16f));
        Ty.ring(g, vpx, cy - vph / 2, vpw, vph, vph / 2, line, Draw.rgba(bl, 0.42f));
        txC(g, ver, vpx + vpw / 2, cy, 12, Ty.SEMI, 0xFFA8D0FF);
        int verEnd = vpx + vpw;

        int s = K(46);
        int gap = K(10);
        int cur = bx + bw - K(22) - s;
        headerButton(g, cur, cy - s / 2, s, "x", 0xFFFFB3BB, 0xFFFFD0D5, 0xFFFF6C7C, b -> onClose(), "Close (Esc)");
        cur -= s + gap;
        headerButton(g, cur, cy - s / 2, s, "tiles", 0xFFD7E5FF, 0xFFFFFFFF, bl, b -> setLayout(1), "Switch to the Windows layout");
        cur -= s + gap;
        headerButton(g, cur, cy - s / 2, s, "move", 0xFFD7E5FF, 0xFFFFFFFF, bl, b -> minecraft.setScreen(new HudEditorScreen(this)), "Open the global HUD editor");
        cur -= gap + K(2);

        int minSearch = K(210);
        int room = cur - (verEnd + K(30));
        String prof = "Profiles";
        int saved = com.zlorbie788.zeus.mods.Profiles.savedCount();
        String sav = saved + " saved";
        int pw0 = K(14) + K(8) + K(9) + tw(prof, 13.5, Ty.SEMI) + K(10) + tw(sav, 11.5, Ty.MED) + K(14);
        if (room - pw0 - K(12) >= minSearch) {
            int pxx = cur - pw0;
            boolean hot = hov(pxx, cy - s / 2, pw0, s);
            Ty.fill(g, pxx, cy - s / 2, pw0, s, K(13), hot ? 0xC00E1C40 : 0x85081026);
            Ty.ring(g, pxx, cy - s / 2, pw0, s, K(13), line, hot ? Draw.rgba(bl, 0.8f) : 0x4D6096FF);
            Ty.disc(g, pxx + K(14) + K(4), cy, Math.max(2, K(4)), 0xFF3DE07A);
            Ty.glow(g, pxx + K(14), cy - K(4), K(8), K(8), K(4), K(8), 0x553DE07A);
            int tx0 = pxx + K(14) + K(8) + K(9);
            tx(g, prof, tx0, cy, 13.5, Ty.SEMI, 0xFFCFE0FF);
            tx(g, sav, tx0 + tw(prof, 13.5, Ty.SEMI) + K(10), cy, 11.5, Ty.MED, cMut());
            hit(pxx, cy - s / 2, pw0, s, b -> openPage(Category.PROFILES.ordinal()), "Your saved profiles");
            cur = pxx - K(12);
            room = cur - (verEnd + K(30));
        } else {
            cur -= 0;
        }
        int sw = Math.max(K(120), Math.min(K(470), room));
        int sx = cur - sw;
        drawSearch(g, sx, cy - s / 2, sw, s, bl);
    }

    private void drawSearch(GuiGraphics g, int x, int y, int w, int h, int bl) {
        boolean focused = search.isFocused();
        int line = Math.max(1, Math.round(k));
        Ty.fill(g, x, y, w, h, K(13), 0xA3050A18);
        if (focused) Ty.glow(g, x, y, w, h, K(13), K(7), Draw.rgba(bl, 0.25f));
        Ty.ring(g, x, y, w, h, K(13), line, focused ? Draw.rgba(bl, 0.95f) : 0x576096FF);
        int cy = y + h / 2;
        Glyphs.drawPx(g, "magnifier", x + K(14) + K(9.5), cy, K(19), focused ? 0xFFCFE0FF : cSub());
        boolean has = !search.getValue().isEmpty();
        String chip = "Ctrl F";
        int chW = tw(chip, 11, Ty.SEMI) + K(16);
        int chH = K(22);
        int chX = x + w - K(12) - chW;
        boolean showChip = w > K(230) && !has;
        int cb = K(20);
        int cbX = x + w - K(9) - cb;
        int textX = x + K(46);
        int room = (showChip ? chX - K(10) : has ? cbX - K(8) : x + w - K(14)) - textX;
        if (!has) {
            tx(g, Ty.fit("Search modules, settings…", room, tp(14.5), Ty.MED), textX, cy, 14.5, Ty.MED, cSub());
            if (focused && ((System.nanoTime() / 530_000_000L) & 1L) == 0L) g.fill(textX, cy - K(9), textX + Math.max(1, K(1.5)), cy + K(9), TXT);
        } else {
            drawField(g, search, textX, cy, room, 14.5, TXT, bl, focused);
        }
        if (showChip) {
            Ty.fill(g, chX, cy - chH / 2, chW, chH, K(6), Draw.rgba(bl, 0.14f));
            Ty.ring(g, chX, cy - chH / 2, chW, chH, K(6), line, Draw.rgba(bl, 0.34f));
            txC(g, chip, chX + chW / 2, cy, 11, Ty.SEMI, 0xFFA9C3F0);
        }
        hit(x, y, w, h, b -> {
            search.setFocused(true);
            setFocused(search);
            search.setCursorPosition(search.getValue().length());
        }, null);
        if (has) {
            boolean hotC = hov(cbX, cy - cb / 2, cb, cb);
            if (hotC) Ty.fill(g, cbX, cy - cb / 2, cb, cb, K(6), Draw.rgba(bl, 0.20f));
            Glyphs.drawPx(g, "x", cbX + cb / 2f, cy, K(15), hotC ? 0xFFFFC9CF : focused ? 0xFFCFE0FF : cSub());
            hit(cbX, cy - cb / 2, cb, cb, b -> {
                search.setValue("");
                search.setFocused(true);
                setFocused(search);
            }, "Clear the search");
        }
    }

    private void drawField(GuiGraphics g, EditBox box, int x, int cy, int room, double px, int color, int bl, boolean focused) {
        String v = box.getValue();
        float p = tp(px);
        int cur = Math.max(0, Math.min(v.length(), box.getCursorPosition()));
        int start = 0;
        while (start < cur && Ty.w(v.substring(start, cur), p, Ty.MED) > room - K(3)) start++;
        String vis = v.substring(start);
        while (vis.length() > 0 && Ty.w(vis, p, Ty.MED) > room) vis = vis.substring(0, vis.length() - 1);
        String hl = box.getHighlighted();
        if (!hl.isEmpty()) {
            int a = v.regionMatches(Math.max(0, cur - hl.length()), hl, 0, hl.length()) && cur >= hl.length() ? cur - hl.length() : cur;
            int b = a + hl.length();
            a = Math.max(a, start);
            b = Math.min(b, start + vis.length());
            if (b > a) {
                int xa = x + Ty.w(v.substring(start, a), p, Ty.MED);
                int xb = x + Ty.w(v.substring(start, b), p, Ty.MED);
                g.fill(xa, cy - K(10), xb, cy + K(10), Draw.rgba(bl, 0.5f));
            }
        }
        Ty.text(g, vis, x, cy, p, Ty.MED, color);
        if (focused && ((System.nanoTime() / 530_000_000L) & 1L) == 0L) {
            int cx = x + Ty.w(v.substring(start, cur), p, Ty.MED);
            g.fill(cx, cy - K(9), cx + Math.max(1, K(1.5)), cy + K(9), TXT);
        }
    }

    private void openPage(int target) {
        page = target;
        pageAt = System.nanoTime();
        lastPage = target;
        scroll = 0;
        scrollShown = 0;
        search.setValue("");
        search.setFocused(false);
        setFocused(null);
    }

    private record RailItem(int id, String section) {}

    private static List<RailItem> railItems() {
        Category[] cats = Category.values();
        List<RailItem> o = new ArrayList<>();
        o.add(new RailItem(PAGE_ACTIVE, "LIBRARY"));
        o.add(new RailItem(PAGE_FAV, null));
        Category[] first = {Category.COMBAT, Category.MOVEMENT, Category.PLAYER, Category.WORLD, Category.RENDER, Category.SCANNER, Category.UTILITY};
        Category[] second = {Category.INTERFACE, Category.FRIENDS, Category.PROFILES, Category.PANELS};
        boolean[] used = new boolean[cats.length];
        boolean head = true;
        for (Category c : first) {
            o.add(new RailItem(c.ordinal(), head ? "MODULES" : null));
            head = false;
            used[c.ordinal()] = true;
        }
        for (int i = 0; i < cats.length; i++) {
            boolean in = false;
            for (Category c : second) if (c.ordinal() == i) in = true;
            if (!used[i] && !in) o.add(new RailItem(i, null));
        }
        head = true;
        for (Category c : second) {
            o.add(new RailItem(c.ordinal(), head ? "CLIENT" : null));
            head = false;
            used[c.ordinal()] = true;
        }
        o.add(new RailItem(PAGE_PANEL, null));
        return o;
    }

    private static String pageName(int id, Category[] cats) {
        return id == PAGE_ACTIVE ? "Active" : id == PAGE_PANEL ? "Panel" : id == PAGE_FAV ? "Favourites" : cats[id].label;
    }

    private void drawRail(GuiGraphics g, int x, int y, int w, int h, int bl, int bl2) {
        int line = Math.max(1, Math.round(k));
        Ty.fill(g, x, y, w, h, K(16), 0xA80D162E);
        Ty.ring(g, x, y, w, h, K(16), line, cLine());
        Category[] cats = Category.values();
        List<RailItem> items = railItems();
        int n = items.size();
        int sections = 0;
        for (RailItem it : items) if (it.section() != null) sections++;
        int padX = K(10);
        int padT = K(10);
        int secH = K(30);
        int perfH = K(140);
        boolean perf = true;
        int avail = h - padT - K(10);
        int itemH = Math.min(K(35), (avail - sections * secH - perfH - K(8)) / n);
        if (itemH < K(26)) {
            perf = false;
            itemH = Math.min(K(35), (avail - sections * secH) / n);
        }
        if (itemH < K(20)) {
            secH = K(8);
            itemH = Math.max(K(14), Math.min(K(35), (avail - sections * secH) / n));
        }
        boolean searching = !query().isEmpty();
        int sel = searching ? -1 : page;
        int ix = x + padX;
        int iw = w - 2 * padX;
        int yy = y + padT;
        int[] ys = new int[n];
        int selRow = -1;
        for (int i = 0; i < n; i++) {
            RailItem it = items.get(i);
            if (it.section() != null) {
                if (secH >= K(20)) spaced(g, it.section(), ix + K(12), yy + secH / 2 + K(2), 11, Ty.BOLD, cMut(), 2.4);
                yy += secH;
            }
            ys[i] = yy;
            if (it.id() == sel) selRow = i;
            yy += itemH;
        }
        int rr = K(10);
        if (selRow >= 0) {
            float target = ys[selRow];
            pillY = pillY < 0 || Math.abs(pillY - target) > K(400) ? target : pillY + (target - pillY) * Math.min(1f, dtNow * 18f);
            int py2 = Math.round(pillY);
            Ty.glow(g, ix, py2, iw, itemH, rr, K(12), Draw.rgba(bl, 0.30f));
            Ty.fill(g, ix, py2, iw, itemH, rr, Draw.rgba(bl, 0.30f));
            Ty.ring(g, ix, py2, iw, itemH, rr, line, Draw.rgba(0xFF60A0FF, 0.58f));
            int bar = Math.max(2, K(3));
            Ty.glow(g, x + K(3), py2 + K(8), bar, itemH - K(16), bar / 2, K(8), Draw.rgba(bl, 0.5f));
            Ty.fill(g, x + K(3), py2 + K(8), bar, itemH - K(16), bar / 2, bl2);
        }
        double fs = 14.5;
        for (int i = 0; i < n; i++) {
            int id = items.get(i).id();
            boolean isSel = sel == id;
            boolean hot = hov(ix, ys[i], iw, itemH);
            float hv = ease(SIDE_KEYS[Math.min(id, SIDE_KEYS.length - 1)], hot && !isSel, dtNow);
            if (hv > 0.01f) Ty.fill(g, ix, ys[i], iw, itemH, rr, Draw.rgba(0xFF3C92FF, 0.10f * hv));
            int cy = ys[i] + itemH / 2;
            boolean fav = id == PAGE_FAV;
            String icon = id < cats.length ? Glyphs.forCategory(cats[id]) : fav ? (isSel ? "starfill" : "star") : id == PAGE_ACTIVE ? "bolt" : "sliders";
            int ic = isSel ? 0xFFD4E6FF : hot ? 0xFFDCE8FF : 0xFF7F97C9;
            if (id < cats.length && cats[id] == Category.SCANNER && !isSel) ic = hot ? 0xFF8CF5E8 : 0xFF63E6D4;
            Glyphs.drawPx(g, icon, ix + K(12) + K(9), cy, Math.max(12, K(19)), ic);
            int count = sideCount(id, id == PAGE_ACTIVE, id == PAGE_PANEL, fav, cats);
            int right = ix + iw - K(12);
            if (count > 0) {
                String s = Integer.toString(count);
                int bwid = Math.max(K(24), tw(s, 11.5, Ty.BOLD) + K(14));
                int bhei = Math.min(K(20), itemH - K(4));
                Ty.fill(g, right - bwid, cy - bhei / 2, bwid, bhei, K(7), Draw.rgba(bl, isSel ? 0.30f : 0.16f));
                Ty.ring(g, right - bwid, cy - bhei / 2, bwid, bhei, K(7), line, Draw.rgba(0xFF6096FF, 0.34f));
                txC(g, s, right - bwid / 2, cy, 11.5, Ty.BOLD, bl2);
                right -= bwid + K(8);
            }
            String label = pageName(id, cats);
            int room = right - (ix + K(42));
            double use = fs;
            while (use > 10 && tw(label, use, isSel ? Ty.SEMI : Ty.MED) > room) use -= 0.5;
            tx(g, label, ix + K(42), cy, use, isSel ? Ty.SEMI : Ty.MED, isSel ? 0xFFFFFFFF : hot ? TXT : 0xFFB9C9EA);
            final int target = id;
            hit(ix, ys[i], iw, itemH, b -> openPage(target),
                    id == PAGE_ACTIVE ? "Every module that is switched on" : id == PAGE_PANEL ? "The panel's own settings and keys" : fav ? "Modules you starred, and the ones you used lately" : cats[id].label + " modules");
        }
        if (perf) drawPerf(g, ix, y + h - K(10) - perfH, iw, perfH, bl, bl2);
    }

    private int sideCount(int id, boolean active, boolean panel, boolean fav, Category[] cats) {
        if (active) return activeCount();
        if (panel) return 0;
        if (fav) return Usage.favouriteCount();
        if (cats[id] == Category.FRIENDS) return com.zlorbie788.zeus.mods.Friends.friendCount();
        if (cats[id] == Category.PROFILES) return com.zlorbie788.zeus.mods.Profiles.savedCount();
        return Zeus.enabledCount(cats[id]);
    }

    private void drawPerf(GuiGraphics g, int x, int y, int w, int h, int bl, int bl2) {
        int line = Math.max(1, Math.round(k));
        long now = System.currentTimeMillis();
        int fps = Math.max(0, com.zlorbie788.zeus.mods.Brain.ctx().fps);
        if (now - fpsAt >= 250) {
            fpsAt = now;
            System.arraycopy(fpsHist, 1, fpsHist, 0, fpsHist.length - 1);
            fpsHist[fpsHist.length - 1] = fps;
            if (fpsHist[0] == 0f) for (int i = 0; i < fpsHist.length - 1; i++) if (fpsHist[i] == 0f) fpsHist[i] = fps;
        }
        Ty.fill(g, x, y, w, h, K(13), 0xB3050A18);
        Ty.ring(g, x, y, w, h, K(13), line, cLine());
        int pad = K(14);
        spaced(g, "PERFORMANCE", x + pad, y + K(18), 10.5, Ty.BOLD, cMut(), 2.0);
        int good = fps >= 60 ? 0xFF3DE07A : fps >= 30 ? Accent.WARN : Accent.BAD;
        String st = fps >= 60 ? "Smooth" : fps >= 30 ? "Fine" : "Low";
        int sw = tw(st, 11, Ty.BOLD);
        txR(g, st, x + w - pad, y + K(18), 11, Ty.BOLD, good);
        Ty.disc(g, x + w - pad - sw - K(10), y + K(18), Math.max(2, K(3)), good);
        String num = Integer.toString(fps);
        tx(g, num, x + pad, y + K(46), 26, Ty.XBOLD, 0xFFFFFFFF);
        tx(g, "FPS", x + pad + tw(num, 26, Ty.XBOLD) + K(8), y + K(48), 12, Ty.MED, cDim());
        int gx = x + pad;
        int gw = w - 2 * pad;
        int gy = y + K(64);
        int gh = K(34);
        float lo = Float.MAX_VALUE;
        float hi = 0;
        for (float f : fpsHist) {
            lo = Math.min(lo, f);
            hi = Math.max(hi, f);
        }
        float span = Math.max(20f, hi - lo);
        float mid = (hi + lo) / 2f;
        lo = mid - span / 2f;
        int step = Math.max(2, K(2));
        int cnt = Math.max(1, gw / step);
        int prevTop = -1;
        for (int c = 0; c < cnt; c++) {
            float pos = c * (fpsHist.length - 1f) / Math.max(1, cnt - 1);
            int i0 = Math.min(fpsHist.length - 2, (int) pos);
            float v = fpsHist[i0] + (fpsHist[i0 + 1] - fpsHist[i0]) * (pos - i0);
            int top = gy + gh - Math.round((v - lo) / span * (gh - K(4))) - K(3);
            top = Math.max(gy, Math.min(gy + gh - 1, top));
            int cx = gx + c * step;
            g.fill(cx, top, cx + step, gy + gh, Draw.rgba(bl, 0.16f));
            int a = prevTop < 0 ? top : Math.min(top, prevTop);
            int b = prevTop < 0 ? top : Math.max(top, prevTop);
            g.fill(cx, a, cx + step, Math.max(b, a) + Math.max(1, K(1.6)), Draw.rgba(bl2, 0.95f));
            prevTop = top;
        }
        int fy = y + h - K(16);
        if (com.zlorbie788.zeus.core.Prof.on) {
            double pct = com.zlorbie788.zeus.core.Prof.total();
            double ms = pct / 100.0 * 1000.0 / Math.max(1, fps);
            String a = "Zeus costs ";
            String b = String.format(Locale.ROOT, "%.2f ms", ms);
            tx(g, a, x + pad, fy, 11.5, Ty.MED, cDim());
            tx(g, b, x + pad + tw(a, 11.5, Ty.MED), fy, 11.5, Ty.SEMI, bl2);
            tx(g, " / frame", x + pad + tw(a, 11.5, Ty.MED) + tw(b, 11.5, Ty.SEMI), fy, 11.5, Ty.MED, cDim());
            txR(g, String.format(Locale.ROOT, "%.1f%%", pct), x + w - pad, fy, 11.5, Ty.MED, cDim());
        } else {
            tx(g, "Cost tracking is off (Panel page)", x + pad, fy, 11.5, Ty.MED, cSub());
        }
    }

    private void drawBrand(GuiGraphics g, int x, int y, int accent, int second) {
        String brand = "ZEUS";
        int bx = x;
        for (int i = 0; i < brand.length(); i++) {
            String c = String.valueOf(brand.charAt(i));
            Draw.bold(g, c, bx, y, Draw.mix(0xFFFFFFFF, Draw.mix(accent, second, i / 3f), 0.35f));
            bx += Draw.widthBold(c) + 1;
        }
        String v = "v" + Zeus.VERSION;
        int cw = Draw.width(v) + 10;
        Draw.rrect(g, bx + 4, y - 1, cw, 11, 5, Draw.rgba(accent, 0.2f));
        Draw.text(g, v, bx + 9, y + 1, Draw.mix(Accent.TEXT, accent, 0.4f));
        Draw.text(g, "Thunder client", x, y + 12, Accent.DIM);
    }

    private void drawHeaderButtons(GuiGraphics g, int right, int y, int accent, boolean withSearch) {
        int cx = right - 16;
        boolean hotC = hov(cx, y, 16, 16);
        Draw.rrect(g, cx, y, 16, 16, 5, hotC ? Draw.rgba(Accent.BAD, 0.25f) : Accent.CARD);
        Draw.line(g, cx + 5, y + 5, cx + 11, y + 11, 1.2f, hotC ? 0xFFFF8C98 : Accent.DIM);
        Draw.line(g, cx + 11, y + 5, cx + 5, y + 11, 1.2f, hotC ? 0xFFFF8C98 : Accent.DIM);
        hit(cx, y, 16, 16, b -> onClose(), "Close (Esc)");

        int lx = cx - 20;
        boolean hotL = hov(lx, y, 16, 16);
        Draw.rrect(g, lx, y, 16, 16, 5, hotL ? Draw.rgba(accent, 0.28f) : Accent.CARD);
        Icons.draw(g, columns ? Icons.PANEL : Icons.WINDOWS, lx + 8, y + 8, hotL ? 0xFFFFFFFF : Accent.DIM);
        hit(lx, y, 16, 16, b -> setLayout(columns ? 0 : 1), columns ? "Switch to the Panel layout" : "Switch to the Windows layout");

        int hx = cx - 40;
        boolean hotH = hov(hx, y, 16, 16);
        Draw.rrect(g, hx, y, 16, 16, 5, hotH ? Draw.rgba(accent, 0.28f) : Accent.CARD);
        Icons.draw(g, Category.PANELS.ordinal(), hx + 8, y + 8, hotH ? 0xFFFFFFFF : Accent.DIM);
        hit(hx, y, 16, 16, b -> minecraft.setScreen(new HudEditorScreen(this)), "Open the global HUD editor");

        if (!withSearch) return;
        int sw = Math.min(190, pw - SIDE - 110);
        int sx = lx - 8 - sw;
        boolean focused = search.isFocused();
        Draw.rrect(g, sx, y - 1, sw, 18, 7, Accent.CARD);
        Draw.rrectLine(g, sx, y - 1, sw, 18, 7, focused ? Draw.rgba(accent, 0.85f) : Accent.LINE);
        Draw.ellipse(g, sx + 9, y + 7, 3, 3, Accent.FAINT);
        Draw.ellipse(g, sx + 9, y + 7, 2, 2, Accent.CARD);
        Draw.line(g, sx + 11, y + 9, sx + 14, y + 12, 1.2f, Accent.FAINT);
        search.setX(sx + 20);
        search.setY(y + 2);
        search.setWidth(sw - 26);
        hit(sx, y - 1, sw, 18, b -> {
            search.setFocused(true);
            setFocused(search);
        }, null);
        if (editing == null && search.isFocused()) setFocused(search);
        search.render(g, mx, my, 0f);
    }

    private int activeCount() {
        int n = 0;
        for (Module m : Zeus.all()) if (m.toggleable() && m.enabled()) n++;
        return n;
    }

    private String query() {
        return search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
    }

    private static final class Index {
        final String name, id, category, desc;
        final String[] aliasText;
        final String[] settingNames, settingDescs;
        final String[][] words = new String[3][];

        Index(Module m) {
            name = normalizeSearch(m.name);
            id = normalizeSearch(m.id);
            category = normalizeSearch(m.category.label);
            desc = normalizeSearch(m.desc);
            aliasText = new String[m.aliases().size()];
            for (int i = 0; i < aliasText.length; i++) aliasText[i] = normalizeSearch(m.aliases().get(i));
            List<Setting> list = m.settings();
            settingNames = new String[list.size()];
            settingDescs = new String[list.size()];
            for (int i = 0; i < settingNames.length; i++) {
                settingNames[i] = normalizeSearch(list.get(i).name);
                settingDescs[i] = normalizeSearch(list.get(i).desc);
            }
        }
    }

    private static final java.util.Map<Module, Index> INDEX = new java.util.IdentityHashMap<>();
    private final java.util.Map<Module, Integer> scoreCache = new java.util.IdentityHashMap<>();
    private final java.util.Map<Object, List<Module>> resultCache = new java.util.HashMap<>();
    private String cachedQuery = "";

    private void syncSearchCache(String q) {
        if (!q.equals(cachedQuery)) {
            cachedQuery = q;
            scoreCache.clear();
            resultCache.clear();
        }
    }

    private boolean matches(Module m, String q) {
        return matchScore(m, q) >= 0;
    }

    private int matchScore(Module m, String q) {
        if (q == null || q.isBlank()) return 0;
        syncSearchCache(q);
        Integer cached = scoreCache.get(m);
        if (cached != null) return cached;
        Index ix = INDEX.computeIfAbsent(m, Index::new);
        String needle = normalizeSearch(q);
        int best = Math.max(scoreText(ix.name, m.name, needle), scoreText(ix.id, m.id, needle));
        for (int i = 0; i < ix.aliasText.length; i++) best = Math.max(best, scoreText(ix.aliasText[i], m.aliases().get(i), needle) - 10);
        best = Math.max(best, scoreText(ix.category, null, needle) - 20);
        best = Math.max(best, scoreText(ix.desc, null, needle) - 140);
        List<Setting> list = m.settings();
        for (int i = 0; i < ix.settingNames.length && i < list.size(); i++) {
            best = Math.max(best, scoreText(ix.settingNames[i], list.get(i).name, needle) - 90);
            best = Math.max(best, scoreText(ix.settingDescs[i], null, needle) - 170);
        }
        scoreCache.put(m, best);
        return best;
    }

    private static String normalizeSearch(String value) {
        if (value == null) return "";
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = Character.toLowerCase(value.charAt(i));
            if (c >= 'a' && c <= 'z' || c >= '0' && c <= '9') out.append(c);
        }
        return out.toString();
    }

    private static int scoreText(String hay, String raw, String needle) {
        if (needle.isEmpty() || hay.isEmpty()) return -1;
        if (hay.equals(needle)) return 1200;
        if (hay.startsWith(needle)) return 1050 - Math.min(100, hay.length() - needle.length());
        int at = hay.indexOf(needle);
        if (at >= 0) return 930 - Math.min(100, at);
        if (isSubsequence(needle, hay)) return 700 - Math.min(120, hay.length() - needle.length());
        int distance = editDistance(needle, hay);
        int limit = needle.length() <= 4 ? 1 : needle.length() <= 8 ? 2 : 3;
        if (distance <= limit) return 650 - distance * 90 - Math.abs(hay.length() - needle.length()) * 3;
        if (raw != null) {
            for (String word : raw.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
                if (word.isEmpty()) continue;
                int d = editDistance(needle, word);
                if (d <= limit) return 620 - d * 90;
            }
        }
        return -1;
    }

    private static boolean isSubsequence(String needle, String hay) {
        int at = 0;
        for (int i = 0; i < hay.length() && at < needle.length(); i++) {
            if (hay.charAt(i) == needle.charAt(at)) at++;
        }
        return at == needle.length();
    }

    private static int editDistance(String a, String b) {
        if (Math.abs(a.length() - b.length()) > 3) return 99;
        int[] prev = new int[b.length() + 1];
        int[] next = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            next[0] = i;
            int rowMin = next[0];
            for (int j = 1; j <= b.length(); j++) {
                next[j] = Math.min(Math.min(next[j - 1] + 1, prev[j] + 1), prev[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1));
                rowMin = Math.min(rowMin, next[j]);
            }
            if (rowMin > 3) return 99;
            int[] swap = prev; prev = next; next = swap;
        }
        return prev[b.length()];
    }

    private List<Module> searchResults(String q, Category only) {
        syncSearchCache(q);
        Object key = only == null ? "*" : only;
        List<Module> hit = resultCache.get(key);
        if (hit != null) return hit;
        List<Module> out = new ArrayList<>();
        for (Module m : only == null ? Zeus.all() : Zeus.in(only)) if (matches(m, q)) out.add(m);
        out.sort((a, b) -> {
            int score = Integer.compare(matchScore(b, q), matchScore(a, q));
            return score != 0 ? score : a.name.compareToIgnoreCase(b.name);
        });
        resultCache.put(key, out);
        return out;
    }

    private void drawPanelFooter(GuiGraphics g, int bl, int bl2, int fy) {
        int line = Math.max(1, Math.round(k));
        boolean inWorld = Env.inWorld();
        int dot = inWorld ? 0xFF3DE07A : Accent.WARN;
        int fh = K(46);
        int panicW = Math.max(K(140), tw("Panic", 14, Ty.BOLD) + K(76));
        int panicX = bx + bw - K(16) - panicW;
        int destrW = Math.max(K(168), tw("Self destruct", 13, Ty.BOLD) + K(52));
        int destrX = panicX - K(9) - destrW;
        int capX = bx + K(16);
        int capW = destrX - K(12) - capX;
        int cy = fy + fh / 2;
        Ty.fill(g, capX, fy, capW, fh, K(13), 0xA80D162E);
        Ty.ring(g, capX, fy, capW, fh, K(13), line, cLine());
        float pulse = 0.5f + 0.5f * (float) Math.sin(anim() * 3.0);
        int dx = capX + K(18) + K(4.5);
        if (inWorld) Ty.glow(g, dx - K(4.5), cy - K(4.5), K(9), K(9), K(4.5), K(9), Draw.rgba(dot, 0.25f + 0.25f * pulse));
        Ty.disc(g, dx, cy, Math.max(2, K(4.5)), dot);
        int cur = capX + K(18) + K(9) + K(14);
        String status = inWorld ? "Client side" : "No world";
        tx(g, status, cur, cy, 13, Ty.MED, 0xFFCFE0FF);
        cur += tw(status, 13, Ty.MED) + K(16);
        String where = !inWorld ? "" : Env.local() ? "singleplayer" : "server";
        if (!where.isEmpty()) {
            g.fill(cur, cy - K(8), cur + line, cy + K(8), 0x476096FF);
            cur += K(16);
            tx(g, where, cur, cy, 13, Ty.MED, cDim());
            cur += tw(where, 13, Ty.MED) + K(16);
        }
        int active = activeCount();
        String act = active + " active";
        int actW = tw(act, 13, Ty.BOLD) + K(24);
        int actX = capX + capW - K(18) - actW;
        Glyphs.drawPx(g, "bolt", actX + K(7), cy, K(15), active > 0 ? bl2 : cMut());
        tx(g, act, actX + K(22), cy, 13, Ty.BOLD, active > 0 ? bl2 : cMut());
        String[][] hints = columns
                ? new String[][] {{"Ctrl K", "palette"}, {"Click", "switch on or off"}, {"Right-click", "settings"}, {"Esc", "close"}}
                : new String[][] {{"Ctrl K", "palette"}, {"Click", "settings"}, {"Right-click", "switch on or off"}, {"Esc", "close"}};
        for (String[] hnt : hints) {
            int cw = tw(hnt[0], 11.5, Ty.SEMI) + K(16);
            int total = cw + K(7) + tw(hnt[1], 12.5, Ty.MED);
            if (cur + total > actX - K(16)) break;
            int chH = K(22);
            Ty.fill(g, cur, cy - chH / 2, cw, chH, K(6), Draw.rgba(bl, 0.14f));
            Ty.ring(g, cur, cy - chH / 2, cw, chH, K(6), line, Draw.rgba(bl, 0.34f));
            txC(g, hnt[0], cur + cw / 2, cy, 11.5, Ty.SEMI, 0xFFA9C3F0);
            tx(g, hnt[1], cur + cw + K(7), cy, 12.5, Ty.MED, cMut());
            cur += total + K(14);
        }

        boolean hot = hov(panicX, fy, panicW, fh);
        Ty.fill(g, panicX, fy, panicW, fh, K(13), hot ? 0xA0601426 : 0x80500E1C);
        if (hot) Ty.glow(g, panicX, fy, panicW, fh, K(13), K(10), 0x40FF3B4D);
        Ty.ring(g, panicX, fy, panicW, fh, K(13), line, hot ? 0xFFFF7080 : 0xBFFF5C6C);
        int pw2 = K(18) + K(9) + tw("Panic", 14, Ty.BOLD);
        int sx0 = panicX + (panicW - pw2) / 2;
        Glyphs.drawPx(g, "warn", sx0 + K(9), cy, K(18), 0xFFFF8A98);
        tx(g, "Panic", sx0 + K(27), cy, 14, Ty.BOLD, 0xFFFF8A98);
        hit(panicX, fy, panicW, fh, b -> Zeus.panic(true), "Switches every module off at once");

        boolean dh = hov(destrX, fy, destrW, fh);
        Ty.fill(g, destrX, fy, destrW, fh, K(13), dh ? 0xA04A2E0A : 0x80301D06);
        if (dh) Ty.glow(g, destrX, fy, destrW, fh, K(13), K(10), 0x40FFB020);
        Ty.ring(g, destrX, fy, destrW, fh, K(13), line, dh ? 0xFFFFC060 : 0xBFFFB45C);
        int dw2 = K(18) + K(9) + tw("Self destruct", 13, Ty.BOLD);
        int dsx = destrX + (destrW - dw2) / 2;
        Glyphs.drawPx(g, "warn", dsx + K(9), cy, K(18), Accent.WARN);
        tx(g, "Self destruct", dsx + K(27), cy, 13, Ty.BOLD, Accent.WARN);
        hit(destrX, fy, destrW, fh, b -> Zeus.destroy(), "Switches every Zeus feature off. Type /zeus in chat to bring it back");
    }

    private static String blurb(int id, Category[] cats) {
        if (id == PAGE_ACTIVE) return "Every module that is switched on.";
        if (id == PAGE_FAV) return "Modules you starred, and the ones you used lately.";
        if (id == PAGE_PANEL) return "How the panel looks, its keys and its performance options.";
        return switch (cats[id]) {
            case COMBAT -> "Modules for fighting.";
            case MOVEMENT -> "Fly, run faster and move through the world.";
            case PLAYER -> "Modules that act on you.";
            case WORLD -> "Modules that work with blocks and the world.";
            case RENDER -> "Change what you see.";
            case SCANNER -> "Find bases and things that are hard to spot.";
            case UTILITY -> "Everyday helpers.";
            case FRIENDS -> "The people you keep track of.";
            case PROFILES -> "Saved sets of module settings.";
            case PANELS -> "The heads-up display and info boxes.";
            case INTERFACE -> "How the client looks and feels.";
        };
    }

    private void pill(GuiGraphics g, int x, int y, int w, int h, boolean hot, int bl) {
        Ty.fill(g, x, y, w, h, K(11), hot ? 0xC00E1A3A : 0xB3050A18);
        Ty.ring(g, x, y, w, h, K(11), Math.max(1, Math.round(k)), hot ? Draw.rgba(bl, 0.7f) : cLine());
    }

    private void drawList(GuiGraphics g, float dt, int bl, int bl2, int bodyTop, int mainX, int mainR) {
        Category[] cats = Category.values();
        List<Module> mods;
        String title;
        String q = query();
        boolean panelPage = q.isEmpty() && page == PAGE_PANEL;
        boolean favPage = q.isEmpty() && page == PAGE_FAV;
        boolean activePage = q.isEmpty() && page == PAGE_ACTIVE;
        if (!q.isEmpty()) {
            mods = searchResults(q, null);
            title = "Search results";
        } else if (panelPage) {
            mods = new ArrayList<>();
            title = "Panel";
        } else if (activePage) {
            List<Module> list = new ArrayList<>();
            for (Module m : Zeus.all()) if (m.toggleable() && m.enabled()) list.add(m);
            mods = Usage.sorted(list, UiSettings.SORT.index());
            title = "Active";
        } else if (favPage) {
            mods = Usage.favourites();
            title = "Favourites";
        } else {
            Category c = cats[page];
            mods = Usage.sorted(Zeus.in(c), UiSettings.SORT.index());
            title = c.label;
        }
        int total = mods.size();
        int onN = 0;
        for (Module m : mods) if (m.toggleable() && m.enabled()) onN++;
        List<Module> shown = mods;
        if (filter != 0 && !panelPage) {
            shown = new ArrayList<>();
            for (Module m : mods) if ((filter == 1) == (m.toggleable() && m.enabled())) shown.add(m);
        }
        int line = Math.max(1, Math.round(k));

        int tcy = bodyTop + K(44);
        int tx0 = mainX + K(6);
        tx(g, title, tx0, tcy, 34, Ty.XBOLD, 0xFFFFFFFF);
        int mx0 = tx0 + tw(title, 34, Ty.XBOLD) + K(16);
        int mcy = tcy + K(5);
        int metaEnd = mx0;
        if (panelPage) {
            tx(g, "look and keys", mx0, mcy, 14, Ty.MED, cDim());
            metaEnd += tw("look and keys", 14, Ty.MED);
        } else {
            String a = Integer.toString(total);
            String word = !q.isEmpty() ? (total == 1 ? " result" : " results") : favPage ? " starred" : total == 1 ? " module" : " modules";
            tx(g, a, metaEnd, mcy, 14, Ty.BOLD, bl2);
            metaEnd += tw(a, 14, Ty.BOLD);
            tx(g, word, metaEnd, mcy, 14, Ty.MED, cDim());
            metaEnd += tw(word, 14, Ty.MED);
            if (!favPage) {
                String dotText = " · ";
                tx(g, dotText, metaEnd, mcy, 14, Ty.MED, cDim());
                metaEnd += tw(dotText, 14, Ty.MED);
                String b = Integer.toString(onN);
                tx(g, b, metaEnd, mcy, 14, Ty.BOLD, bl2);
                metaEnd += tw(b, 14, Ty.BOLD);
                tx(g, " on", metaEnd, mcy, 14, Ty.MED, cDim());
                metaEnd += tw(" on", 14, Ty.MED);
            }
        }
        String sub = !q.isEmpty() ? "Matches in names, descriptions and settings." : blurb(page, cats);
        tx(g, sub, tx0, bodyTop + K(62) + K(15), 14, Ty.REG, cSub());

        if (!panelPage) {
            int th = K(38);
            int tcy2 = bodyTop + K(43);
            int ty = tcy2 - th / 2;
            int limit = metaEnd + K(24);
            int cur = mainR;
            int vtW = K(78);
            if (cur - vtW > limit) {
                int vx = cur - vtW;
                pill(g, vx, ty, vtW, th, false, bl);
                boolean one = UiSettings.CARDS.index() == 1;
                int cell = K(34);
                int cellH = th - K(6);
                for (int i = 0; i < 2; i++) {
                    boolean sel = (i == 0) == one;
                    int cx = vx + K(3) + i * (cell + K(2));
                    boolean hot = hov(cx, ty + K(3), cell, cellH);
                    if (sel) Ty.fill(g, cx, ty + K(3), cell, cellH, K(8), Draw.rgba(bl, 0.30f));
                    else if (hot) Ty.fill(g, cx, ty + K(3), cell, cellH, K(8), 0x1AFFFFFF);
                    Glyphs.drawPx(g, i == 0 ? "rows" : "tiles", cx + cell / 2f, tcy2, K(17), sel ? 0xFFFFFFFF : 0xFF7F97C9);
                    final int idx = i;
                    hit(cx, ty + K(3), cell, cellH, b -> {
                        UiSettings.CARDS.set(idx == 0 ? 1 : 0);
                        pageAt = System.nanoTime();
                    }, idx == 0 ? "One wide card per row" : "Two cards side by side");
                }
                cur = vx - K(10);
            }
            String sortLabel = sortName();
            int spW = K(14) + K(16) + K(8) + tw(sortLabel, 13, Ty.SEMI) + K(8) + K(15) + K(12);
            if (cur - spW > limit) {
                int sx = cur - spW;
                boolean hot = hov(sx, ty, spW, th);
                pill(g, sx, ty, spW, th, hot, bl);
                Glyphs.drawPx(g, "sortud", sx + K(14) + K(8), tcy2, K(16), 0xFF7F97C9);
                tx(g, sortLabel, sx + K(14) + K(24), tcy2, 13, Ty.SEMI, 0xFFCFE0FF);
                Glyphs.drawPx(g, "chevdown", sx + spW - K(12) - K(7.5), tcy2, K(15), 0xFF7F97C9);
                hit(sx, ty, spW, th, b -> {
                    int n = UiSettings.SORT.index();
                    UiSettings.SORT.set((n + (b == 1 ? 2 : 1)) % 3);
                    scroll = 0;
                    pageAt = System.nanoTime();
                }, "Sort the modules: category order, the ones you use most first, or A to Z. Click to change (right click goes back).");
                cur = sx - K(10);
            }
            if (!activePage) {
                String[] segs = {"All", "On", "Off"};
                int[] sw = new int[3];
                int tot = K(6);
                for (int i = 0; i < 3; i++) {
                    sw[i] = tw(segs[i], 13, Ty.SEMI) + K(28);
                    tot += sw[i];
                }
                if (cur - tot > limit) {
                    int sx = cur - tot;
                    pill(g, sx, ty, tot, th, false, bl);
                    int cx = sx + K(3);
                    for (int i = 0; i < 3; i++) {
                        boolean sel = filter == i;
                        boolean hot = hov(cx, ty + K(3), sw[i], th - K(6));
                        if (sel) {
                            Ty.glow(g, cx, ty + K(3), sw[i], th - K(6), K(8), K(7), Draw.rgba(bl, 0.28f));
                            Ty.fill(g, cx, ty + K(3), sw[i], th - K(6), K(8), Draw.rgba(bl, 0.32f));
                        } else if (hot) {
                            Ty.fill(g, cx, ty + K(3), sw[i], th - K(6), K(8), 0x1AFFFFFF);
                        }
                        txC(g, segs[i], cx + sw[i] / 2, tcy2, 13, Ty.SEMI, sel ? 0xFFFFFFFF : cDim());
                        final int idx = i;
                        hit(cx, ty + K(3), sw[i], th - K(6), b -> {
                            filter = idx;
                            scroll = 0;
                            pageAt = System.nanoTime();
                        }, idx == 0 ? "Show every module" : idx == 1 ? "Only the modules that are on" : "Only the modules that are off");
                        cx += sw[i];
                    }
                }
            }
        }

        int top = listTop;
        int viewH = listBottom - listTop;
        int maxScroll = Math.max(0, contentH - viewH);
        scroll = Math.max(0, Math.min(maxScroll, scroll));
        scrollShown += (scroll - scrollShown) * Math.min(1f, dt * 16f);
        if (Math.abs(scroll - scrollShown) < 0.5f) scrollShown = scroll;
        int pad = K(10);
        g.enableScissor(listLeft - pad, top, listLeft + listWide + pad, listBottom);
        clipTop = top;
        clipBottom = listBottom;
        int y = top - Math.round(scrollShown);
        int start = y;
        if (panelPage) {
            y = drawPanelCard(g, y, bl, bl2);
        } else if (shown.isEmpty() && !(favPage && !Usage.recent().isEmpty())) {
            drawEmpty(g, top, viewH, bl, q, activePage, favPage);
        } else {
            float since = (System.nanoTime() - pageAt) / 1e9f;
            y = drawModules(g, shown, y, dt, bl, bl2, since);
            if (favPage) {
                List<Module> recent = Usage.recent();
                if (shown.isEmpty()) {
                    tx(g, "No favourites yet. Click the star on a module to pin it here.", listLeft + K(6), y + K(14), 14, Ty.MED, cMut());
                    y += K(44);
                }
                if (!recent.isEmpty()) {
                    y += K(10);
                    tx(g, "Recently used", listLeft + K(6), y + K(10), 15, Ty.SEMI, 0xFF9DC4FF);
                    y += K(32);
                    y = drawModules(g, recent, y, dt, bl, bl2, since);
                }
            }
        }
        g.disableScissor();
        clipTop = -1;
        clipBottom = -1;
        contentH = y - start + K(24);

        int fade = K(34);
        g.fillGradient(listLeft - pad, listBottom - fade, listLeft + listWide + pad, listBottom, 0x00050914, 0xF2050914);
        if (maxScroll > 0) {
            int tbh = Math.max(K(30), Math.round(viewH * (viewH / (float) contentH)));
            int tby = top + Math.round((viewH - tbh) * (scrollShown / maxScroll));
            int sbx = mainR - K(6);
            int sbw = Math.max(2, K(5));
            Ty.fill(g, sbx, tby, sbw, tbh, sbw / 2, Draw.rgba(0xFF6096FF, 0.42f));
        }
    }

    private void drawEmpty(GuiGraphics g, int top, int viewH, int bl, String q, boolean activePage, boolean favPage) {
        int line = Math.max(1, Math.round(k));
        int cxm = listLeft + listWide / 2;
        int cy = top + Math.max(K(88), viewH / 2 - K(34));
        int tile = K(52);
        int tx0 = cxm - tile / 2;
        int ty0 = cy - tile;
        Ty.glow(g, tx0, ty0, tile, tile, K(15), K(16), Draw.rgba(bl, 0.16f));
        Ty.fill(g, tx0, ty0, tile, tile, K(15), 0x99101A33);
        Ty.ring(g, tx0, ty0, tile, tile, K(15), line, Draw.rgba(0xFF6096FF, 0.30f));
        String glyph = !q.isEmpty() ? "magnifier" : activePage ? "bolt" : favPage ? "star" : "sliders";
        Glyphs.drawPx(g, glyph, cxm, ty0 + tile / 2f, K(24), 0xFF90B4E8);
        String head = !q.isEmpty() ? "No module matches \"" + q + "\""
                : activePage ? "Nothing is switched on"
                : favPage ? "No favourites yet"
                : "Nothing here";
        txC(g, Ty.fit(head, listWide - K(24), tp(16), Ty.SEMI), cxm, cy + K(14), 16, Ty.SEMI, 0xFFE6EEFF);
        String sub = !q.isEmpty() ? "Press Ctrl K to search every module and setting from anywhere."
                : activePage ? "Switch a module on and it appears here."
                : favPage ? "Click the star on a module to pin it here."
                : "The filter in the header is hiding the rest.";
        txC(g, Ty.fit(sub, listWide - K(24), tp(13), Ty.MED), cxm, cy + K(36), 13, Ty.MED, cMut());
        if (q.isEmpty()) return;
        String label = "Clear the search";
        int bwid = tw(label, 13, Ty.SEMI) + K(28);
        int bh = K(28);
        int bx0 = cxm - bwid / 2;
        int by0 = cy + K(50);
        boolean hot = hov(bx0, by0, bwid, bh);
        Ty.fill(g, bx0, by0, bwid, bh, K(9), hot ? Draw.rgba(bl, 0.34f) : Draw.rgba(bl, 0.18f));
        Ty.ring(g, bx0, by0, bwid, bh, K(9), line, Draw.rgba(0xFF6096FF, hot ? 0.75f : 0.42f));
        txC(g, label, cxm, by0 + bh / 2, 13, Ty.SEMI, hot ? 0xFFFFFFFF : 0xFFCFE0FF);
        hit(bx0, by0, bwid, bh, b -> {
            search.setValue("");
            search.setFocused(true);
            setFocused(search);
        }, null);
    }

    private int drawModules(GuiGraphics g, List<Module> list, int y, float dt, int bl, int bl2, float since) {
        int rowH = K(86);
        int gap = K(12);
        int cols = UiSettings.CARDS.index() == 0 && listWide >= K(760) ? 2 : 1;
        int colW = (listWide - gap * (cols - 1)) / cols;
        int col = 0;
        int idx = 0;
        for (Module m : list) {
            boolean wide = cols == 1 || OPEN.contains(m.id) || expand.getOrDefault(m.id, 0f) > 0.002f;
            if (wide && col != 0) {
                y += rowH + gap;
                col = 0;
            }
            float e = UiSettings.CALM.on() || Perf.fast() ? 1f : Math.max(0f, Math.min(1f, (since - Math.min(idx, 12) * 0.035f) / 0.30f));
            float slide = (1f - e) * (1f - e) * (1f - e);
            if (wide) {
                int h = drawCard(g, m, listLeft, y, listWide, dt, bl, bl2, slide);
                y += h + gap;
            } else {
                drawCard(g, m, listLeft + col * (colW + gap), y, colW, dt, bl, bl2, slide);
                if (col == cols - 1) {
                    y += rowH + gap;
                    col = 0;
                } else {
                    col++;
                }
            }
            idx++;
        }
        if (col != 0) y += rowH + gap;
        return y;
    }

    private boolean inView(int top, int viewH) {
        return my >= top && my < top + viewH;
    }

    private int keyChipW(String text) {
        return Math.max(K(56), tw(text, 11.5, Ty.SEMI) + K(22));
    }

    private void keyChip(GuiGraphics g, String text, boolean wait, boolean unbound, int x, int cy, int w, int bl, Click run) {
        int h = K(28);
        boolean hot = hov(x, cy - h / 2, w, h) && inViewD();
        Ty.fill(g, x, cy - h / 2, w, h, K(8), wait ? Draw.rgba(bl, 0.35f) : hot ? 0xC00E1A3A : 0xB3050A18);
        Ty.ring(g, x, cy - h / 2, w, h, K(8), Math.max(1, Math.round(k)), wait ? bl : hot ? Draw.rgba(bl, 0.7f) : cLine());
        txC(g, text, x + w / 2, cy, 11.5, Ty.SEMI, wait || !unbound ? 0xFFFFFFFF : 0xFF8FA3CC);
        hit(x, cy - h / 2, w, h, run, "Click, then press a key. Right click removes the key.");
    }

    private int drawCard(GuiGraphics g, Module m, int x, int y, int w, float dt, int bl, int bl2, float slide) {
        int rowH = K(86);
        int top = listTop;
        int viewH = listBottom - listTop;
        int margin = K(8);
        boolean open = OPEN.contains(m.id);
        if (!open) {
            Float settledEx = expand.get(m.id);
            Float settledOn = toggle.get(m.id);
            if ((settledEx == null || settledEx == 0f) && (settledOn == null || settledOn == (m.enabled() ? 1f : 0f))
                    && (y + rowH < top - margin || y > top + viewH + margin)) {
                return rowH;
            }
        }
        float ex = expand.getOrDefault(m.id, open ? 1f : 0f);
        ex += ((open ? 1f : 0f) - ex) * Math.min(1f, dt * 14f);
        if (Math.abs((open ? 1f : 0f) - ex) < 0.01f) ex = open ? 1f : 0f;
        expand.put(m.id, ex);
        float on = toggle.getOrDefault(m.id, m.enabled() ? 1f : 0f);
        float want = m.enabled() ? 1f : 0f;
        on += (want - on) * Math.min(1f, dt * 18f);
        if (Math.abs(want - on) < 0.01f) on = want;
        toggle.put(m.id, on);

        List<Setting> sets = m.settings();
        int full = rowH;
        int[] ax = null;
        int[] ay = null;
        int[] aw = null;
        int[] ah = null;
        int setH = 0;
        int sidePad = K(22);
        if ((open || ex > 0f) && !sets.isEmpty()) {
            ax = new int[sets.size()];
            ay = new int[sets.size()];
            aw = new int[sets.size()];
            ah = new int[sets.size()];
            setH = arrange(sets, w - 2 * sidePad, ax, ay, aw, ah);
            full = rowH + K(14) + setH + K(18);
        }
        float ee = ex * ex * (3f - 2f * ex);
        int h = rowH + Math.round((full - rowH) * ee);
        if (y + h < top - margin || y > top + viewH + margin) return h;

        int line = Math.max(1, Math.round(k));
        int rr = K(15);
        boolean hot = hov(x, y, w, rowH) && inViewD();
        float hv = ease(cardKey(m.id), hot, dt);
        g.pose().pushMatrix();
        if (slide > 0f) g.pose().translate(0f, slide * K(26));
        if (on > 0.02f) Ty.glow(g, x, y, w, h, rr, K(14), Draw.rgba(bl, 0.20f * on));
        Ty.fill(g, x, y, w, h, rr, Draw.mix(0xA30F1934, 0xB60D1630, on));
        if (on > 0.02f) {
            int wx0 = x + rr;
            int wx1 = x + Math.round(w * 0.66f);
            Ty.fillLeftEnd(g, x, y, h, rr, Draw.rgba(bl, 0.27f * on));
            ramp().draw(g, wx0, y + line, Math.max(1, wx1 - wx0), Math.max(1, h - 2 * line), Draw.rgba(bl, 0.27f * on));
        }
        Ty.ring(g, x, y, w, h, rr, line, Draw.mix(Draw.mix(cLine(), 0x6B6096FF, hv), Draw.rgba(0xFF60A0FF, 0.62f), on));
        if (hv > 0.05f && !UiSettings.CALM.on() && !Perf.fast()) Thunder.outline(g, x, y, w, rowH, anim(), hv * 0.6f, Math.max(1f, k), 0.35f);

        hit(x, y, w, rowH, b -> {
            if (b == 1 && m.toggleable() && !m.locked()) m.toggle();
            else toggleOpen(m);
        }, m.locked() ? m.note() : m.desc);

        int tile = Math.min(K(48), rowH - K(16));
        int tx0 = x + K(14);
        int tty = y + (rowH - tile) / 2;
        int tr = K(13);
        if (on > 0.02f) Ty.glow(g, tx0, tty, tile, tile, tr, K(10), Draw.rgba(bl, 0.5f * on));
        Ty.fill(g, tx0, tty, tile, tile, tr, Draw.mix(0xB3050A18, Draw.mix(bl, 0xFF0B1E4A, 0.12f), on));
        Ty.ring(g, tx0, tty, tile, tile, tr, line, Draw.mix(cLine(), Draw.rgba(0xFFA0CDFF, 0.6f), on));
        Glyphs.drawPx(g, Glyphs.forModule(m.id, m.category), tx0 + tile / 2f, tty + tile / 2f, Math.max(14, Math.round(tile * 0.5f)), Draw.mix(0xFF86A2D6, 0xFFFFFFFF, on));

        int cy = y + rowH / 2;
        int cur = x + w - K(16);
        boolean hasSets = !sets.isEmpty();
        if (hasSets) {
            int chev = Math.max(12, K(20));
            int chx = cur - chev / 2;
            int ccol = hot || ee > 0.5f ? 0xFF9CC7FF : 0xFF4A6AA8;
            Glyphs.drawPx(g, ee > 0.5f ? "chevup" : "chevdown", chx, cy, chev, ccol);
            hit(cur - K(30), y, K(34), rowH, b -> toggleOpen(m), "Settings");
            cur -= K(30);
        }
        if (m.toggleable() && m.locked()) {
            String soon = m.note() == null ? "In development" : m.note();
            int ww = tw(soon, 11, Ty.BOLD) + K(16);
            int wh2 = K(22);
            Ty.fill(g, cur - ww, cy - wh2 / 2, ww, wh2, K(7), Draw.rgba(Accent.WARN, 0.12f));
            Ty.ring(g, cur - ww, cy - wh2 / 2, ww, wh2, K(7), line, Draw.rgba(Accent.WARN, 0.34f));
            txC(g, soon, cur - ww / 2, cy, 11, Ty.BOLD, Accent.WARN);
            cur -= ww + K(14);
        }
        if (m.toggleable() && !m.locked()) {
            int sw = K(46);
            int sh = K(26);
            int sx = cur - sw;
            drawSwitchD(g, sx, cy - sh / 2, sw, sh, on, bl);
            Long burstAt = BURSTS.get(m.id);
            if (burstAt != null) {
                float bp = (System.nanoTime() - burstAt) / 0.6e9f;
                if (bp >= 1f) BURSTS.remove(m.id);
                else Thunder.burst(g, sx + sw * 0.75f, cy, Math.max(36f, K(80)), bp, Math.max(1f, k));
            }
            hit(sx - K(4), y + K(2), sw + K(8), rowH - K(4), b -> {
                if (!m.enabled()) BURSTS.put(m.id, System.nanoTime());
                m.toggle();
            }, "Switch " + m.name + (m.enabled() ? " off" : " on"));
            cur = sx - K(14);
            boolean fav = Usage.favourite(m);
            int ss = K(26);
            boolean starHot = hov(cur - ss, y + K(2), ss, rowH - K(4)) && inViewD();
            int starCol = fav ? Accent.WARN : starHot ? 0xFFFFE08A : hot ? 0xFF7F93BD : 0xFF4A5D88;
            if (fav) Ty.glow(g, cur - ss / 2 - K(7), cy - K(7), K(14), K(14), K(7), K(9), 0x40FFC857);
            Glyphs.drawPx(g, fav ? "starfill" : "star", cur - ss / 2f, cy, K(19), starCol);
            hit(cur - ss - K(2), y + K(2), ss + K(4), rowH - K(4), b -> Usage.toggleFavourite(m),
                    fav ? "Remove " + m.name + " from your favourites" : "Add " + m.name + " to your favourites");
            cur -= ss + K(14);
            if (m.key != null) {
                boolean wait = listening == m;
                KeyMapping km = m.key;
                String text = wait ? "Press a key" : km.isUnbound() ? "None" : km.getTranslatedKeyMessage().getString();
                int kw = keyChipW(text);
                keyChip(g, text, wait, km.isUnbound(), cur - kw, cy, kw, bl, b -> {
                    if (b == 1) {
                        setKey(km, InputConstants.UNKNOWN);
                        listening = null;
                        listeningOpen = false;
                        return;
                    }
                    listening = m;
                    listeningOpen = false;
                });
                cur -= kw + K(14);
            }
            float cost = m.enabled() && UiSettings.SHOW_COST.on() ? m.costPct : 0f;
            if (cost >= 0.05f) {
                int fpsNow = Math.max(1, com.zlorbie788.zeus.mods.Brain.ctx().fps);
                String ct = String.format(Locale.ROOT, "%.2f ms", cost / 100.0 * 1000.0 / fpsNow);
                int cc = cost < 1f ? 0xFF7FD7A4 : cost < 3f ? Accent.WARN : 0xFFFF7C8A;
                int cw2 = tw(ct, 11.5, Ty.SEMI) + K(18);
                int ch = K(24);
                if (cur - cw2 > x + K(260)) {
                    Ty.fill(g, cur - cw2, cy - ch / 2, cw2, ch, K(7), Draw.rgba(cc, 0.10f));
                    Ty.ring(g, cur - cw2, cy - ch / 2, cw2, ch, K(7), line, Draw.rgba(cc, 0.30f));
                    txC(g, ct, cur - cw2 / 2, cy, 11.5, Ty.SEMI, cc);
                    cur -= cw2 + K(14);
                }
            }
        }

        int textLeft = tx0 + tile + K(14);
        int textRight = cur - K(6);
        int nameCy = cy - K(11);
        tx(g, Ty.fit(m.name, textRight - textLeft, tp(16.5), Ty.SEMI), textLeft, nameCy, 16.5, Ty.SEMI, 0xFFFFFFFF);
        String info = m.enabled() ? m.info() : null;
        if (info != null && !info.isEmpty()) {
            int nameW = tw(m.name, 16.5, Ty.SEMI);
            int iw = tw(info, 11, Ty.BOLD) + K(14);
            int ix = textLeft + nameW + K(9);
            if (ix + iw < textRight) {
                int ih = K(20);
                Ty.fill(g, ix, nameCy - ih / 2, iw, ih, K(6), Draw.rgba(bl, 0.18f));
                Ty.ring(g, ix, nameCy - ih / 2, iw, ih, K(6), line, Draw.rgba(0xFF6096FF, 0.35f));
                txC(g, info, ix + iw / 2, nameCy, 11, Ty.BOLD, 0xFFBFD8FF);
            }
        }
        int descCy = cy + K(13);
        if (m.error != null) tx(g, Ty.fit("Switched off: " + m.error, textRight - textLeft, tp(13.5), Ty.REG), textLeft, descCy, 13.5, Ty.REG, 0xFFFF7B7B);
        else tx(g, Ty.fit(m.desc, textRight - textLeft, tp(13.5), Ty.REG), textLeft, descCy, 13.5, Ty.REG, cDim());

        if (ee > 0.02f && ax != null) {
            g.enableScissor(x, y + rowH, x + w, y + h);
            g.fill(x + line, y + rowH, x + w - line, y + rowH + line, cLine());
            int sy0 = y + rowH + K(14);
            int sx0 = x + sidePad;
            for (int i = 0; i < sets.size(); i++) {
                if (aw[i] > 0) drawSettingD(g, sets.get(i), sx0 + ax[i], sy0 + ay[i], aw[i], ah[i], bl, bl2, dt);
            }
            g.disableScissor();
        }
        g.pose().popMatrix();
        return h;
    }

    private void toggleOpen(Module m) {
        if (OPEN.contains(m.id)) OPEN.remove(m.id);
        else OPEN.add(m.id);
    }

    private void drawSwitchD(GuiGraphics g, int x, int y, int w, int h, float on, int bl) {
        int line = Math.max(1, Math.round(k));
        if (on > 0.4f) Ty.glow(g, x, y, w, h, h / 2, K(9), Draw.rgba(bl, 0.45f * on));
        Ty.fill(g, x, y, w, h, h / 2, Draw.mix(0xFF18233F, bl, on));
        Ty.ring(g, x, y, w, h, h / 2, line, Draw.mix(0x386096FF, Draw.rgba(0xFFAAD2FF, 0.6f), on));
        int pad = K(3);
        int d = h - 2 * pad;
        int kx = x + pad + Math.round((w - h) * on);
        Ty.disc(g, kx + d / 2, y + h / 2, d / 2, Draw.mix(0xFF7385AD, 0xFFFFFFFF, on));
    }

    private int lab() {
        return UiSettings.CONTRAST.on() ? 0xFFFFFFFF : 0xFFC4D4F2;
    }

    private boolean chipsFit(Setting.Choice c, int cw) {
        String[] o = c.options;
        if (o.length > 5) return false;
        int total = (o.length - 1) * K(6);
        for (String s : o) total += tw(s, 12.5, Ty.SEMI) + K(24);
        return total <= cw;
    }

    private int pickRows(Setting.Pick p, int cw) {
        int cx = 0;
        int rows = 1;
        for (String l : p.labels) {
            int w = tw(l, 12, Ty.SEMI) + K(22);
            if (cx + w > cw && cx > 0) {
                cx = 0;
                rows++;
            }
            cx += w + K(6);
        }
        return rows;
    }

    private int settingH(Setting s, int cw) {
        if (s instanceof Setting.Bool) return K(40);
        if (s instanceof Setting.Num) return K(56);
        if (s instanceof Setting.Choice c) return chipsFit(c, cw) ? K(68) : K(44);
        if (s instanceof Setting.Pick p) return K(32) + pickRows(p, cw) * K(36);
        if (s instanceof Setting.Text) return K(48);
        if (s instanceof Setting.Action) return K(48);
        if (s instanceof Setting.Info info) {
            int n = Math.min(info.lines().size(), 10);
            return K(34) + Math.max(1, n) * K(19) + (info.lines().size() > 10 ? K(19) : 0) + K(8);
        }
        return 0;
    }

    private int arrange(List<Setting> list, int w, int[] ox, int[] oy, int[] ow, int[] oh) {
        int cols = w >= K(880) ? 3 : w >= K(540) ? 2 : 1;
        int gap = K(36);
        int cw = (w - gap * (cols - 1)) / cols;
        int n = list.size();
        int sum = 0;
        for (int i = 0; i < n; i++) {
            Setting s = list.get(i);
            ow[i] = -1;
            if (!s.visible() || s instanceof Setting.Info || s instanceof Setting.Secret) continue;
            oh[i] = settingH(s, cw);
            sum += oh[i];
        }
        int target = (sum + cols - 1) / cols;
        int[] colY = new int[cols];
        int c = 0;
        for (int i = 0; i < n; i++) {
            if (oh[i] <= 0 || !list.get(i).visible() || list.get(i) instanceof Setting.Info || list.get(i) instanceof Setting.Secret) continue;
            if (c < cols - 1 && colY[c] > 0 && colY[c] + oh[i] / 2 > target) c++;
            ox[i] = c * (cw + gap);
            oy[i] = colY[c];
            ow[i] = cw;
            colY[c] += oh[i];
        }
        int end = 0;
        for (int v : colY) end = Math.max(end, v);
        for (int i = 0; i < n; i++) {
            Setting s = list.get(i);
            if (!(s instanceof Setting.Info) || !s.visible()) continue;
            ox[i] = 0;
            oy[i] = end;
            ow[i] = w;
            oh[i] = settingH(s, w);
            end += oh[i];
        }
        return end;
    }

    private void drawSettingD(GuiGraphics g, Setting s, int x, int y, int w, int h, int bl, int bl2, float dt) {
        int line = Math.max(1, Math.round(k));
        boolean hot = hov(x - K(8), y, w + K(16), h) && inViewD();
        if (hot && s.desc != null && !s.desc.isEmpty()) tip = s.desc;
        int lc = hot ? TXT : lab();
        if (s instanceof Setting.Bool b) {
            float v = animS(b, b.on() ? 1f : 0f, dt);
            int cy = y + h / 2;
            tx(g, Ty.fit(s.name, w - K(60), tp(13), Ty.MED), x, cy, 13, Ty.MED, lc);
            int sw = K(40);
            int sh = K(22);
            drawSwitchD(g, x + w - sw, cy - sh / 2, sw, sh, v, bl);
            hit(x - K(8), y, w + K(16), h, k2 -> b.toggle(), null);
        } else if (s instanceof Setting.Num n) {
            int lcy = y + K(13);
            tx(g, Ty.fit(s.name, w - K(70), tp(13), Ty.MED), x, lcy, 13, Ty.MED, lc);
            txR(g, n.text(), x + w, lcy, 13, Ty.BOLD, bl2);
            int th = K(6);
            int ty = y + K(34);
            Ty.fill(g, x, ty, w, th, th / 2, 0xFF17233F);
            int fw = Math.max(0, Math.round(w * (float) n.fraction()));
            boolean drag = dragging == n;
            if (fw > 0) {
                Ty.glow(g, x, ty, Math.max(th, fw), th, th / 2, K(7), Draw.rgba(bl, 0.35f));
                Ty.fill(g, x, ty, Math.max(th, fw), th, th / 2, bl);
            }
            int kr = K(8);
            int kx = x + fw;
            Ty.disc(g, kx, ty + th / 2, kr + K(drag ? 6 : 4), Draw.rgba(bl, drag ? 0.40f : 0.28f));
            Ty.disc(g, kx, ty + th / 2, kr, 0xFFFFFFFF);
            final int fx = x;
            final int fwid = w;
            hit(x - K(10), y + K(22), w + K(20), K(30), k2 -> {
                dragging = n;
                dragX = fx;
                dragW = fwid;
                dragMul = gs;
                applyDragDev(mx);
            }, null);
        } else if (s instanceof Setting.Choice c) {
            String[] o = c.options;
            if (chipsFit(c, w)) {
                tx(g, Ty.fit(s.name, w, tp(13), Ty.MED), x, y + K(12), 13, Ty.MED, lc);
                int gap = K(6);
                int ch = K(32);
                int cyy = y + K(26);
                int minTotal = (o.length - 1) * gap;
                for (String os : o) minTotal += tw(os, 12.5, Ty.SEMI) + K(24);
                int extra = Math.max(0, (w - minTotal) / o.length);
                int cx = x;
                for (int i = 0; i < o.length; i++) {
                    int cw = tw(o[i], 12.5, Ty.SEMI) + K(24) + extra;
                    if (i == o.length - 1) cw = x + w - cx;
                    boolean sel = c.index() == i;
                    boolean hc = hov(cx, cyy, cw, ch) && inViewD();
                    if (sel) {
                        Ty.glow(g, cx, cyy, cw, ch, K(9), K(8), Draw.rgba(bl, 0.28f));
                        Ty.fill(g, cx, cyy, cw, ch, K(9), Draw.rgba(bl, 0.32f));
                    } else {
                        Ty.fill(g, cx, cyy, cw, ch, K(9), hc ? 0xC00E1A3A : 0xB3050A18);
                    }
                    Ty.ring(g, cx, cyy, cw, ch, K(9), line, sel ? Draw.rgba(0xFF78AFFF, 0.65f) : hc ? Draw.rgba(bl, 0.6f) : cLine());
                    txC(g, o[i], cx + cw / 2, cyy + ch / 2, 12.5, Ty.SEMI, sel ? 0xFFFFFFFF : hc ? TXT : cDim());
                    final int idx = i;
                    hit(cx, cyy, cw, ch, k2 -> c.set(idx), null);
                    cx += cw + gap;
                }
            } else {
                int cy = y + h / 2;
                String v = c.value();
                int vw = Math.max(K(120), tw(v, 12.5, Ty.SEMI) + K(66));
                vw = Math.min(vw, w - K(20));
                tx(g, Ty.fit(s.name, w - vw - K(12), tp(13), Ty.MED), x, cy, 13, Ty.MED, lc);
                int vx = x + w - vw;
                int ch = K(32);
                boolean hc = hov(vx, cy - ch / 2, vw, ch) && inViewD();
                Ty.fill(g, vx, cy - ch / 2, vw, ch, K(9), hc ? 0xC00E1A3A : 0xB3050A18);
                Ty.ring(g, vx, cy - ch / 2, vw, ch, K(9), line, hc ? Draw.rgba(bl, 0.7f) : cLine());
                Glyphs.drawPx(g, "chevleft", vx + K(16), cy, K(15), 0xFF7F97C9);
                Glyphs.drawPx(g, "chevright", vx + vw - K(16), cy, K(15), 0xFF7F97C9);
                txC(g, Ty.fit(v, vw - K(56), tp(12.5), Ty.SEMI), vx + vw / 2, cy, 12.5, Ty.SEMI, TXT);
                hit(vx, cy - ch / 2, vw, ch, k2 -> c.set(k2 == 1 ? (c.index() + o.length - 1) % o.length : (c.index() + 1) % o.length), "Left click: next, right click: previous");
            }
        } else if (s instanceof Setting.Pick p) {
            tx(g, Ty.fit(s.name, w - K(80), tp(13), Ty.MED), x, y + K(12), 13, Ty.MED, lc);
            txR(g, p.label(), x + w, y + K(12), 12.5, Ty.BOLD, bl2);
            int cx = 0;
            int cyy = y + K(28);
            int rows = 1;
            int ch = K(30);
            for (int i = 0; i < p.labels.length; i++) {
                int cw = tw(p.labels[i], 12, Ty.SEMI) + K(22);
                if (cx + cw > w && cx > 0) {
                    cx = 0;
                    cyy += K(36);
                    rows++;
                }
                boolean sel = p.index() == i;
                boolean hc = hov(x + cx, cyy, cw, ch) && inViewD();
                if (sel) Ty.fill(g, x + cx, cyy, cw, ch, K(9), Draw.rgba(bl, 0.32f));
                else Ty.fill(g, x + cx, cyy, cw, ch, K(9), hc ? 0xC00E1A3A : 0xB3050A18);
                Ty.ring(g, x + cx, cyy, cw, ch, K(9), line, sel ? Draw.rgba(0xFF78AFFF, 0.65f) : hc ? Draw.rgba(bl, 0.6f) : cLine());
                txC(g, p.labels[i], x + cx + cw / 2, cyy + ch / 2, 12, Ty.SEMI, sel ? 0xFFFFFFFF : hc ? TXT : cDim());
                final int idx = i;
                hit(x + cx, cyy, cw, ch, k2 -> p.set(idx), null);
                cx += cw + K(6);
            }
            p.rows = rows;
        } else if (s instanceof Setting.Text t) {
            int cy = y + h / 2;
            int bwid = Math.max(K(120), Math.min(w - tw(s.name, 13, Ty.MED) - K(16), K(240)));
            int bxx = x + w - bwid;
            tx(g, Ty.fit(s.name, bxx - x - K(10), tp(13), Ty.MED), x, cy, 13, Ty.MED, lc);
            int ch = K(34);
            boolean mine = editing == t;
            boolean hb = hov(bxx, cy - ch / 2, bwid, ch) && inViewD();
            Ty.fill(g, bxx, cy - ch / 2, bwid, ch, K(9), mine ? 0xE0040810 : hb ? 0xC00E1A3A : 0xB3050A18);
            if (mine) Ty.glow(g, bxx, cy - ch / 2, bwid, ch, K(9), K(6), Draw.rgba(bl, 0.22f));
            Ty.ring(g, bxx, cy - ch / 2, bwid, ch, K(9), line, Draw.rgba(bl, mine ? 0.95f : hb ? 0.7f : 0.35f));
            if (mine) {
                field.setX(bxx + K(12));
                field.setY(cy - K(10));
                field.setWidth(bwid - K(24));
                field.visible = true;
                field.setFocused(true);
                drawField(g, field, bxx + K(12), cy, bwid - K(24), 12.5, TXT, bl, true);
                fieldDrawn = true;
            } else {
                String v = t.get();
                tx(g, Ty.fit(v.isEmpty() ? "(empty)" : v, bwid - K(24), tp(12.5), Ty.MED), bxx + K(12), cy, 12.5, Ty.MED, v.isEmpty() ? cMut() : TXT);
            }
            hit(bxx, cy - ch / 2, bwid, ch, k2 -> {
                editing = t;
                field.setMaxLength(t.max);
                field.setValue(t.get());
                focusField();
            }, null);
        } else if (s instanceof Setting.Action a) {
            int ch = K(34);
            int cy = y + h / 2;
            int bwid = tw(a.name, 12.5, Ty.SEMI) + K(30);
            boolean hb = hov(x, cy - ch / 2, bwid, ch) && inViewD();
            Ty.fill(g, x, cy - ch / 2, bwid, ch, K(9), hb ? Draw.rgba(bl, 0.42f) : Draw.rgba(bl, 0.24f));
            Ty.ring(g, x, cy - ch / 2, bwid, ch, K(9), line, Draw.rgba(bl, hb ? 0.95f : 0.55f));
            txC(g, a.name, x + bwid / 2, cy, 12.5, Ty.SEMI, 0xFFFFFFFF);
            if (a.desc != null && !a.desc.isEmpty() && w - bwid > K(120)) {
                tx(g, Ty.fit(a.desc, w - bwid - K(14), tp(12), Ty.REG), x + bwid + K(12), cy, 12, Ty.REG, cMut());
            }
            hit(x, cy - ch / 2, bwid, ch, k2 -> a.fire(), a.desc);
        } else if (s instanceof Setting.Info info) {
            spaced(g, s.name.toUpperCase(Locale.ROOT), x, y + K(10), 10.5, Ty.BOLD, cMut(), 1.6);
            List<String> lines = info.lines();
            int boxY = y + K(24);
            int boxH = h - K(30);
            Ty.fill(g, x, boxY, w, boxH, K(10), 0xB3050A18);
            Ty.ring(g, x, boxY, w, boxH, K(10), line, cLine());
            int ly = boxY + K(18);
            if (lines.isEmpty()) {
                tx(g, "Nothing yet", x + K(14), ly, 12.5, Ty.MED, cMut());
            } else {
                int shown = Math.min(lines.size(), 10);
                for (int i = 0; i < shown; i++) {
                    tx(g, Ty.fit(lines.get(i), w - K(28), tp(12.5), Ty.MED), x + K(14), ly, 12.5, Ty.MED, i == 0 ? TXT : cDim());
                    ly += K(19);
                }
                if (lines.size() > shown) tx(g, "+ " + (lines.size() - shown) + " more", x + K(14), ly, 12.5, Ty.MED, cMut());
            }
        }
    }

    private int drawPanelCard(GuiGraphics g, int y, int bl, int bl2) {
        int x = listLeft;
        int w = listWide;
        int line = Math.max(1, Math.round(k));
        List<Setting> rows = Zeus.UI.settings();
        int sidePad = K(22);
        int[] ax = new int[rows.size()];
        int[] ay = new int[rows.size()];
        int[] aw = new int[rows.size()];
        int[] ah = new int[rows.size()];
        int setH = arrange(rows, w - 2 * sidePad, ax, ay, aw, ah);
        int head = K(64);
        int foot = K(58);
        int total = head + K(6) + setH + foot;
        int rr = K(15);
        Ty.fill(g, x, y, w, total, rr, 0xA30F1934);
        Ty.ring(g, x, y, w, total, rr, line, cLine());
        int cy = y + head / 2;
        tx(g, "Open key", x + sidePad, cy, 15, Ty.SEMI, 0xFFFFFFFF);
        tx(g, "The key that opens and closes this panel", x + sidePad + tw("Open key", 15, Ty.SEMI) + K(14), cy, 12.5, Ty.REG, cMut());
        boolean wait = listeningOpen;
        KeyMapping ok = ZeusClient.OPEN;
        String text = wait ? "Press a key" : ok.isUnbound() ? "None" : ok.getTranslatedKeyMessage().getString();
        int kw = keyChipW(text);
        keyChip(g, text, wait, ok.isUnbound(), x + w - sidePad - kw, cy, kw, bl, b -> {
            if (b == 1) {
                setKey(ok, InputConstants.UNKNOWN);
                listening = null;
                listeningOpen = false;
                return;
            }
            listening = null;
            listeningOpen = true;
        });
        g.fill(x + line, y + head, x + w - line, y + head + line, cLine());
        int sy0 = y + head + K(6);
        for (int i = 0; i < rows.size(); i++) {
            if (aw[i] > 0) drawSettingD(g, rows.get(i), x + sidePad + ax[i], sy0 + ay[i], aw[i], ah[i], bl, bl2, dtNow);
        }
        int fy = y + total - foot + K(16);
        tx(g, Ty.fit("Press the open key in a world. Every module has its own key on its card.", w - 2 * sidePad, tp(12.5), Ty.REG), x + sidePad, fy, 12.5, Ty.REG, cMut());
        tx(g, "Keys can also be set in Options > Controls.", x + sidePad, fy + K(20), 12.5, Ty.REG, cMut());
        return y + total + K(12);
    }

    private void drawTipD(GuiGraphics g, int bl) {
        g.nextStratum();
        float p = tp(12.5);
        int maxW = K(320);
        List<String> lines = wrapD(tip, maxW, p);
        int w = 0;
        for (String l : lines) w = Math.max(w, Ty.w(l, p, Ty.MED));
        w += K(24);
        int lh = Math.round(p * 1.45f);
        int h = lines.size() * lh + K(18);
        int x = Math.max(K(4), Math.min(bx + bw - w - K(4), mx + K(16)));
        int y = Math.max(K(4), Math.min(by + bh - h - K(4), my + K(20)));
        Ty.glow(g, x, y, w, h, K(10), K(10), 0x70000000);
        Ty.fill(g, x, y, w, h, K(10), 0xF5070D1E);
        Ty.ring(g, x, y, w, h, K(10), Math.max(1, Math.round(k)), Draw.rgba(bl, 0.6f));
        int ly = y + K(9) + lh / 2;
        for (String l : lines) {
            Ty.text(g, l, x + K(12), ly, p, Ty.MED, TXT);
            ly += lh;
        }
    }

    private static List<String> wrapD(String s, int maxW, float p) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : s.split(" ")) {
            String test = cur.length() == 0 ? word : cur + " " + word;
            if (Ty.w(test, p, Ty.MED) > maxW && cur.length() > 0) {
                out.add(cur.toString());
                cur = new StringBuilder(word);
            } else {
                cur = new StringBuilder(test);
            }
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    private void drawSwitch(GuiGraphics g, int x, int y, int w, int h, float on, int col, boolean locked) {
        int track = Draw.mix(0xFF2A3150, col, on);
        if (locked) track = Draw.mix(track, 0xFF1A1F30, 0.6f);
        Draw.rrect(g, x, y, w, h, h / 2, track);
        int kx = x + 1 + Math.round((w - h) * on);
        Draw.rrect(g, kx, y + 1, h - 2, h - 2, (h - 2) / 2, locked ? 0xFF8791AE : 0xFFFFFFFF);
        if (on > 0.5f && !locked) Draw.glow(g, x, y, w, h, h / 2, col, 3, 0.18f);
    }

    private int rowH(Setting s) {
        if (s instanceof Setting.Num) return 26;
        if (s instanceof Setting.Action) return 24;
        if (s instanceof Setting.Pick p) return 20 + Math.max(1, p.rows) * 17 + 2;
        if (s instanceof Setting.Info info) {
            int n = Math.min(info.lines().size(), 10);
            return 16 + Math.max(1, n) * 10 + (info.lines().size() > 10 ? 10 : 0);
        }
        return 20;
    }

    private void drawRow(GuiGraphics g, Setting s, int x, int y, int w, int col, int top, int viewH) {
        boolean hot = hov(x - 4, y, w + 8, rowH(s)) && inView(top, viewH);
        if (hot && s.desc != null && !s.desc.isEmpty()) tip = s.desc;
        if (s instanceof Setting.Bool b) {
            Draw.text(g, s.name, x, y + 6, Accent.TEXT);
            float v = b.on() ? 1f : 0f;
            drawSwitch(g, x + w - 22, y + 4, 22, 11, v, col, false);
            hit(x - 4, y, w + 8, 20, k -> b.toggle(), null);
        } else if (s instanceof Setting.Num n) {
            Draw.text(g, s.name, x, y + 2, Accent.TEXT);
            Draw.textRight(g, n.text(), x + w, y + 2, Draw.mix(Accent.TEXT, col, 0.5f));
            int ty = y + 16;
            Draw.rrect(g, x, ty, w, 4, 2, 0xFF222942);
            int fw = Math.max(0, Math.round(w * (float) n.fraction()));
            if (fw > 0) Draw.rrect(g, x, ty, Math.max(4, fw), 4, 2, Draw.mix(col, 0xFFFFFFFF, 0.1f), col);
            int kx = x + fw;
            boolean drag = dragging == n;
            Draw.rrect(g, kx - 4, ty - 2, 8, 8, 4, drag || hot ? 0xFFFFFFFF : 0xFFDCE4FA);
            if (drag) Draw.glow(g, kx - 4, ty - 2, 8, 8, 4, col, 4, 0.3f);
            final int fx = x;
            final int fwid = w;
            hit(x - 4, y + 10, w + 8, 14, k -> {
                dragging = n;
                dragX = fx;
                dragW = fwid;
                dragMul = 1;
                applyDrag(mx);
            }, null);
        } else if (s instanceof Setting.Choice c) {
            Draw.text(g, s.name, x, y + 6, Accent.TEXT);
            drawChoice(g, c, x, y, w, col);
        } else if (s instanceof Setting.Pick p) {
            Draw.text(g, s.name, x, y + 3, Accent.TEXT);
            Draw.textRight(g, p.label(), x + w, y + 3, Draw.mix(Accent.TEXT, col, 0.6f));
            int cx = x;
            int cy = y + 17;
            int rows = 1;
            for (int i = 0; i < p.labels.length; i++) {
                int cw = Draw.width(p.labels[i]) + 12;
                if (cx + cw > x + w && cx > x) {
                    cx = x;
                    cy += 17;
                    rows++;
                }
                if (cy + 15 > top && cy < top + viewH) {
                    boolean sel = p.index() == i;
                    boolean hb = hov(cx, cy, cw, 15) && inView(top, viewH);
                    Draw.rrect(g, cx, cy, cw, 15, 5, sel ? Draw.rgba(col, 0.34f) : hb ? Draw.rgba(0xFFFFFFFF, 0.09f) : 0xFF1B2136);
                    if (sel) Draw.rrectLineFast(g, cx, cy, cw, 15, 5, Draw.rgba(col, 0.9f));
                    Draw.textCenter(g, p.labels[i], cx + cw / 2, cy + 4, sel ? 0xFFFFFFFF : hb ? Accent.TEXT : Accent.DIM);
                    final int idx = i;
                    hit(cx, cy, cw, 15, k -> p.set(idx), null);
                }
                cx += cw + 3;
            }
            p.rows = rows;
        } else if (s instanceof Setting.Text t) {
            Draw.text(g, s.name, x, y + 6, Accent.TEXT);
            int bw = Math.max(70, Math.min(w - Draw.width(s.name) - 14, 150));
            int bx = x + w - bw;
            boolean mine = editing == t;
            boolean hb = hov(bx, y + 1, bw, 18) && inView(top, viewH);
            Draw.rrect(g, bx, y + 1, bw, 18, 6, mine ? 0xFF0C1019 : hb ? Draw.rgba(col, 0.2f) : 0xFF1B2136);
            Draw.rrectLine(g, bx, y + 1, bw, 18, 6, Draw.rgba(col, mine ? 0.95f : hb ? 0.7f : 0.35f));
            if (mine) {
                field.setX(bx + 6);
                field.setY(y + 6);
                field.setWidth(bw - 12);
                field.visible = true;
                field.render(g, mx, my, 0f);
                fieldDrawn = true;
            } else {
                Draw.text(g, Draw.fit(t.get().isEmpty() ? "(empty)" : t.get(), bw - 12), bx + 6, y + 6, t.get().isEmpty() ? Accent.FAINT : Accent.TEXT);
            }
            hit(bx, y + 1, bw, 18, k -> {
                editing = t;
                field.setMaxLength(t.max);
                field.setValue(t.get());
                focusField();
            }, null);
        } else if (s instanceof Setting.Action a) {
            int bw = Draw.width(a.name) + 20;
            boolean hb = hov(x, y + 2, bw, 18) && inView(top, viewH);
            Draw.rrect(g, x, y + 2, bw, 18, 6, hb ? Draw.rgba(col, 0.38f) : Draw.rgba(col, 0.2f));
            Draw.rrectLine(g, x, y + 2, bw, 18, 6, Draw.rgba(col, hb ? 0.9f : 0.5f));
            Draw.textCenter(g, a.name, x + bw / 2, y + 7, 0xFFFFFFFF);
            if (a.desc != null && !a.desc.isEmpty()) Draw.text(g, Draw.fit(a.desc, w - bw - 10), x + bw + 8, y + 7, Accent.FAINT);
            hit(x, y + 2, bw, 18, k -> a.fire(), a.desc);
        } else if (s instanceof Setting.Info info) {
            Draw.text(g, s.name.toUpperCase(Locale.ROOT), x, y + 3, Accent.FAINT);
            List<String> lines = info.lines();
            Draw.rrect(g, x, y + 14, w, rowH(s) - 18, 5, 0xFF0C1019);
            int ly = y + 18;
            if (lines.isEmpty()) {
                Draw.text(g, "Nothing yet", x + 8, ly, Accent.FAINT);
            } else {
                int shown = Math.min(lines.size(), 10);
                for (int i = 0; i < shown; i++) {
                    Draw.text(g, Draw.fit(lines.get(i), w - 16), x + 8, ly, i == 0 ? Accent.TEXT : Accent.DIM);
                    ly += 10;
                }
                if (lines.size() > shown) Draw.text(g, "+ " + (lines.size() - shown) + " more", x + 8, ly, Accent.FAINT);
            }
        }
    }

    private void drawChoice(GuiGraphics g, Setting.Choice c, int x, int y, int w, int col) {
        String[] o = c.options;
        int total = 0;
        for (String s : o) total += Draw.width(s) + 14;
        int labelW = Draw.width(c.name) + 16;
        if (o.length <= 5 && total + o.length * 2 <= w - labelW) {
            int cx = x + w;
            for (int i = o.length - 1; i >= 0; i--) {
                int cw = Draw.width(o[i]) + 14;
                cx -= cw;
                boolean sel = c.index() == i;
                boolean hot = hov(cx, y + 1, cw, 18);
                Draw.rrect(g, cx, y + 1, cw, 18, 6, sel ? Draw.rgba(col, 0.30f) : hot ? Draw.rgba(0xFFFFFFFF, 0.07f) : 0xFF1B2136);
                if (sel) Draw.rrectLine(g, cx, y + 1, cw, 18, 6, Draw.rgba(col, 0.85f));
                Draw.textCenter(g, o[i], cx + cw / 2, y + 6, sel ? 0xFFFFFFFF : Accent.DIM);
                final int idx = i;
                hit(cx, y + 1, cw, 18, k -> c.set(idx), null);
                cx -= 2;
            }
        } else {
            String v = c.value();
            int vw = Math.max(70, Draw.width(v) + 28);
            int cx = x + w - vw;
            boolean hot = hov(cx, y + 1, vw, 18);
            Draw.rrect(g, cx, y + 1, vw, 18, 6, hot ? Draw.rgba(col, 0.25f) : 0xFF1B2136);
            Draw.rrectLine(g, cx, y + 1, vw, 18, 6, Draw.rgba(col, hot ? 0.8f : 0.35f));
            Draw.textCenter(g, v, cx + vw / 2, y + 6, Accent.TEXT);
            Draw.text(g, "<", cx + 6, y + 6, Accent.FAINT);
            Draw.text(g, ">", cx + vw - 11, y + 6, Accent.FAINT);
            hit(cx, y + 1, vw, 18, k -> c.set(k == 1 ? (c.index() + o.length - 1) % o.length : (c.index() + 1) % o.length), "Left click: next, right click: previous");
        }
    }

    private int bindWidth(KeyMapping k, Module m) {
        return Math.max(28, Draw.width(keyText(k, m)) + 14);
    }

    private String keyText(KeyMapping k, Module m) {
        if (listening == m && (m != null || listeningOpen)) return "press a key";
        if (k.isUnbound()) return "none";
        return k.getTranslatedKeyMessage().getString();
    }

    private void drawBind(GuiGraphics g, KeyMapping k, Module m, int right, int y, boolean openKey, int top, int viewH) {
        boolean wait = openKey ? listeningOpen : listening == m;
        String text = wait ? "press a key" : (k.isUnbound() ? "none" : k.getTranslatedKeyMessage().getString());
        int bw = Math.max(28, Draw.width(text) + 14);
        int x = right - bw;
        boolean hot = hov(x, y, bw, 14) && inView(top, viewH);
        int col = Accent.main();
        Draw.rrect(g, x, y, bw, 14, 5, wait ? Draw.rgba(col, 0.3f) : hot ? 0xFF222A44 : 0xFF181E33);
        Draw.rrectLine(g, x, y, bw, 14, 5, wait ? col : Accent.LINE);
        Draw.textCenter(g, text, x + bw / 2, y + 3, wait ? 0xFFFFFFFF : k.isUnbound() ? Accent.FAINT : Accent.TEXT);
        hit(x, y, bw, 14, b -> {
            if (b == 1) {
                setKey(k, InputConstants.UNKNOWN);
                listening = null;
                listeningOpen = false;
                return;
            }
            listening = openKey ? null : m;
            listeningOpen = openKey;
        }, "Click, then press a key. Right click removes the key.");
    }

    private void setKey(KeyMapping k, InputConstants.Key key) {
        k.setKey(key);
        KeyMapping.resetMapping();
        minecraft.options.save();
    }

    private void drawFooter(GuiGraphics g, int left, int right, int y, int accent) {
        boolean inWorld = Env.inWorld();
        int dot = inWorld ? Accent.OK : Accent.WARN;
        float pulse = 0.5f + 0.5f * (float) Math.sin(anim() * 3.0);
        if (inWorld) Draw.glow(g, left, y + 1, 6, 6, 3, dot, 2, 0.10f + 0.12f * pulse);
        Draw.rrect(g, left, y + 1, 6, 6, 3, dot);
        String status = !inWorld ? "No world" : Env.local() ? "Client side  -  singleplayer" : "Client side  -  server";
        Draw.text(g, status, left + 12, y, Accent.DIM);
        int active = activeCount();
        String a = active + " active";
        int pw2 = Draw.width("Panic") + 16;
        int bx = right - pw2;
        boolean hot = hov(bx, y - 3, pw2, 16);
        Draw.rrect(g, bx, y - 3, pw2, 16, 6, hot ? Draw.rgba(Accent.BAD, 0.38f) : Draw.rgba(Accent.BAD, 0.16f));
        Draw.rrectLine(g, bx, y - 3, pw2, 16, 6, Draw.rgba(Accent.BAD, hot ? 0.9f : 0.4f));
        Draw.textCenter(g, "Panic", bx + pw2 / 2, y, 0xFFFFA0A8);
        hit(bx, y - 3, pw2, 16, b -> Zeus.panic(true), "Switches every module off at once");

        String destruct = "Self destruct";
        int dw = Draw.width(destruct) + 16;
        int dx = bx - 8 - dw;
        boolean dhot = hov(dx, y - 3, dw, 16);
        Draw.rrect(g, dx, y - 3, dw, 16, 6, dhot ? Draw.rgba(0xFFFFB45C, 0.36f) : Draw.rgba(0xFFFFB45C, 0.14f));
        Draw.rrectLine(g, dx, y - 3, dw, 16, 6, Draw.rgba(0xFFFFB45C, dhot ? 0.9f : 0.4f));
        Draw.textCenter(g, destruct, dx + dw / 2, y, 0xFFFFD08A);
        hit(dx, y - 3, dw, 16, b -> Zeus.destroy(), "Switches every Zeus feature off. Type /zeus in chat to bring it back");
        Draw.textRight(g, a, dx - 10, y, active > 0 ? Draw.mix(Accent.TEXT, accent, 0.5f) : Accent.FAINT);
    }

    private int wW;
    private int wHead;
    private int wRow;
    private int wGap;
    private int wTop;
    private int wBottom;
    private int wScrW;
    private int wScrH;
    private int wMinY;
    private static final int WIN_ROWS_VISIBLE = 5;
    private static final int WIN_ROWS_SHORT_VISIBLE = 4;

    private void layoutWin() {
        gs = Tex.guiScale();
        k = Math.max(0.5f, Math.min(height * gs / 941f, width * gs / 1672f));
        wScrW = width * gs;
        wScrH = height * gs;
        bx = 0;
        by = 0;
        bw = wScrW;
        bh = wScrH;
        wW = K(238);
        wHead = K(48);
        wRow = K(40);
        wGap = K(14);
        wTop = K(84);
        wBottom = wScrH - K(80);
        wMinY = K(70);
        listTop = 0;
        listBottom = wScrH;
        sbRight = 0;
    }

    private void drawWindows(GuiGraphics g, float dt, int live, int accent, int second) {
        hits.clear();
        tip = null;
        boolean interactive = live == 1;
        int rmx = mx;
        int rmy = my;
        layoutWin();
        mx = interactive ? rmx * gs : -100000;
        my = interactive ? rmy * gs : -100000;
        winRect.clear();
        clipTop = -1;
        clipBottom = -1;
        int bl = blue(accent);
        int bl2 = lite(bl);
        Ty.begin();
        g.pose().pushMatrix();
        g.pose().scale(1f / gs, 1f / gs);
        String q = query();

        drawWinHeader(g, bl, bl2);

        int sortMode = UiSettings.SORT.index();
        long nowNs = System.nanoTime();
        String defsKey = q + "|" + sortMode;
        if (defsCache == null || !defsKey.equals(defsCacheKey) || nowNs - defsCacheAt > 120_000_000L) {
            List<WinDef> built = new ArrayList<>();
            if (q.isEmpty()) built.add(new WinDef(UiState.FAVOURITES, "Favourites", Accent.WARN, null, "starfill", Usage.favourites()));
            for (Category c : Category.values()) {
                if (c == Category.PROFILES && q.isEmpty()) continue;
                List<Module> mods = q.isEmpty() ? Usage.sorted(Zeus.in(c), sortMode) : searchResults(q, c);
                if (!mods.isEmpty()) built.add(new WinDef(c.name(), c.label, c.color(), c, null, mods));
            }
            if (defsCache != null) for (WinDef nd : built) for (WinDef od : defsCache) if (od.key.equals(nd.key)) nd.open = od.open;
            defsCache = built;
            defsCacheKey = defsKey;
            defsCacheAt = nowNs;
        }
        List<WinDef> defs = defsCache;

        int w = wW;
        winW = w;
        int avail = Math.max(wHead + wRow * 3, wBottom - wTop);
        int cols = Math.max(1, (wScrW - K(16)) / (w + wGap));
        int gridW = cols * (w + wGap) - wGap;
        int left = Math.max(K(8), (wScrW - gridW) / 2);
        int rowsVis = Math.max(WIN_ROWS_VISIBLE, Math.min(9, (avail - 2 * wHead - wGap - K(16)) / (2 * wRow)));
        int capFull = wRow * rowsVis + K(8);
        int capShort = wRow * Math.max(WIN_ROWS_SHORT_VISIBLE, rowsVis - 1) + K(8);
        Map<String, int[]> plan = new HashMap<>();
        List<WinDef> tiled = new ArrayList<>();
        Map<String, WinDef> byKey = new HashMap<>();
        for (WinDef d : defs) {
            UiState.Win st = UiState.win(d.key);
            d.open = openOf(d.key, st.collapsed && q.isEmpty(), dt);
            d.content = Math.max(1, d.mods.size()) * wRow + K(8);
            byKey.put(d.key, d);
            if (!st.placed) tiled.add(d);
        }
        List<int[]> taken = new ArrayList<>();
        List<String> priority = new ArrayList<>();
        if (dragWin != null && byKey.containsKey(dragWin) && UiState.win(dragWin).placed) priority.add(dragWin);
        for (int i = ORDER.size() - 1; i >= 0; i--) {
            String key = ORDER.get(i);
            if (!priority.contains(key) && byKey.containsKey(key) && UiState.win(key).placed) priority.add(key);
        }
        int maxY = Math.max(wMinY, wScrH - wHead - K(80));
        for (String key : priority) {
            WinDef d = byKey.get(key);
            UiState.Win st = UiState.win(key);
            int px2 = Math.round(Math.max(0, Math.min(wScrW - w, st.x * gs)));
            int py2 = Math.round(Math.max(wMinY, Math.min(maxY, st.y * gs)));
            int bodyMax = Math.max(wRow * 3, wScrH - py2 - wHead - K(80));
            int hh = wHead + Math.round(Math.min(d.content, bodyMax) * d.open);
            int[] at = pushClear(taken, px2, py2, w, hh, maxY);
            if (at == null) at = new int[] {px2, py2};
            plan.put(key, new int[] {at[0], at[1], Math.max(wRow * 3, wScrH - at[1] - wHead - K(80))});
            taken.add(new int[] {at[0], at[1], w, hh});
        }
        for (WinDef d : defs) if (!plan.containsKey(d.key) && !tiled.contains(d)) tiled.add(d);
        int[] colH = new int[cols];
        List<List<WinDef>> stacks = new ArrayList<>();
        for (int i = 0; i < cols; i++) stacks.add(new ArrayList<>());
        for (WinDef d : tiled) {
            int best = 0;
            for (int i = 1; i < cols; i++) if (colH[i] < colH[best]) best = i;
            stacks.get(best).add(d);
            colH[best] += wHead + Math.min(d.content, fixedSize(d) ? capShort : capFull) + wGap;
        }
        for (int ci = 0; ci < cols; ci++) {
            List<WinDef> stack = stacks.get(ci);
            if (stack.isEmpty()) continue;
            int heads = stack.size() * wHead + (stack.size() - 1) * wGap;
            float natural = 0;
            float fixedBody = 0;
            for (WinDef d : stack) {
                if (fixedSize(d)) fixedBody += Math.min(d.content, capShort) * d.open;
                else natural += Math.min(d.content, capFull) * d.open;
            }
            float room = avail - heads - fixedBody;
            float factor = natural <= room || natural <= 0 ? 1f : Math.max(0.2f, room / natural);
            int yy = wTop;
            int xx = left + ci * (w + wGap);
            for (WinDef d : stack) {
                int cap = Math.min(d.content, capFull);
                int body = fixedSize(d) ? Math.min(d.content, capShort) : Math.max(Math.min(d.content, wRow * 3), Math.round(cap * factor));
                int shown = Math.round(Math.min(d.content, body) * d.open);
                for (int it = 0; it < 8; it++) {
                    boolean moved = false;
                    for (int[] r : taken) {
                        if (xx < r[0] + r[2] + wGap && xx + w + wGap > r[0] && yy < r[1] + r[3] + wGap && yy + wHead + shown + wGap > r[1]) {
                            yy = r[1] + r[3] + wGap;
                            moved = true;
                        }
                    }
                    if (!moved) break;
                }
                plan.put(d.key, new int[] {xx, yy, body});
                yy += wHead + shown + wGap;
            }
        }
        boolean still = UiSettings.CALM.on() || Perf.fast();
        float glide = Math.min(1f, dt * 14f);
        winPos.keySet().retainAll(plan.keySet());
        for (Map.Entry<String, int[]> e : plan.entrySet()) {
            int[] at = e.getValue();
            float[] cur = winPos.get(e.getKey());
            if (cur == null || still || e.getKey().equals(dragWin)) {
                winPos.put(e.getKey(), new float[] {at[0], at[1]});
                continue;
            }
            cur[0] += (at[0] - cur[0]) * glide;
            cur[1] += (at[1] - cur[1]) * glide;
            if (Math.abs(at[0] - cur[0]) < 0.6f * gs) cur[0] = at[0];
            if (Math.abs(at[1] - cur[1]) < 0.6f * gs) cur[1] = at[1];
            at[0] = Math.round(cur[0]);
            at[1] = Math.round(cur[1]);
        }
        for (String key : ORDER) {
            int[] at = plan.get(key);
            if (at == null) continue;
            for (WinDef d : defs) {
                if (d.key.equals(key)) {
                    drawWin(g, d, at[0], at[1], at[2], bl, bl2, second, dt, !q.isEmpty());
                    break;
                }
            }
        }
        clipTop = -1;
        clipBottom = -1;
        listTop = 0;
        listBottom = wScrH;

        drawPanelFooter(g, bl, bl2, wScrH - K(64));
        drawPopupWin(g, bl, bl2, dt);
        if (tip != null && interactive) drawTipD(g, bl);
        g.pose().popMatrix();
        Ty.flush();
        mx = rmx;
        my = rmy;
    }

    private void drawWinHeader(GuiGraphics g, int bl, int bl2) {
        int line = Math.max(1, Math.round(k));
        g.fillGradient(0, 0, wScrW, K(96), 0xD0040813, 0x00040813);
        int cy = K(40);
        int ls = K(58);
        int lx = K(22);
        float breathe = 0.5f + 0.5f * (float) Math.sin(anim() * 2.2);
        Ty.glow(g, lx, cy - ls / 2, ls, ls, ls / 2, K(16), Draw.rgba(bl, 0.18f + 0.12f * breathe));
        Tex.Img logo = Tex.fitted("pw_logo", "textures/gui/logo_z.png", ls, ls, false, 0.5f, 0.5f);
        if (logo != null) logo.draw(g, lx, cy - ls / 2, ls, ls, 0xFFFFFFFF);
        int wh = K(34);
        int ww = Math.round(wh * 850f / 120f);
        int wx = lx + ls + K(16);
        Tex.Img word = Tex.fitted("pw_word", "textures/gui/panel_lettering.png", ww, wh, false, 0.5f, 0.5f);
        int wordEnd = wx + ww;
        if (word != null) {
            word.draw(g, wx, cy - wh / 2, ww, wh, 0xFFFFFFFF);
        } else {
            spaced(g, "ZEUS CLIENT", wx, cy, 24, Ty.XBOLD, 0xFFF3F8FF, 8);
            wordEnd = wx + spacedW("ZEUS CLIENT", 24, Ty.XBOLD, 8);
        }
        String ver = "v" + Zeus.VERSION;
        int vpw = tw(ver, 12, Ty.SEMI) + K(22);
        int vph = K(26);
        int vpx = wordEnd + K(16);
        Ty.fill(g, vpx, cy - vph / 2, vpw, vph, vph / 2, Draw.rgba(bl, 0.16f));
        Ty.ring(g, vpx, cy - vph / 2, vpw, vph, vph / 2, line, Draw.rgba(bl, 0.42f));
        txC(g, ver, vpx + vpw / 2, cy, 12, Ty.SEMI, 0xFFA8D0FF);
        int verEnd = vpx + vpw;

        int s = K(44);
        int gap = K(10);
        int cur = wScrW - K(22) - s;
        headerButton(g, cur, cy - s / 2, s, "x", 0xFFFFB3BB, 0xFFFFD0D5, 0xFFFF6C7C, b -> onClose(), "Close (Esc)");
        cur -= s + gap;
        headerButton(g, cur, cy - s / 2, s, "rows", 0xFFD7E5FF, 0xFFFFFFFF, bl, b -> setLayout(0), "Switch to the Panel layout");
        cur -= s + gap;
        headerButton(g, cur, cy - s / 2, s, "move", 0xFFD7E5FF, 0xFFFFFFFF, bl, b -> minecraft.setScreen(new HudEditorScreen(this)), "Open the global HUD editor");
        cur -= s + gap;
        headerButton(g, cur, cy - s / 2, s, "sliders", 0xFFD7E5FF, 0xFFFFFFFF, bl, b -> {
            popup = null;
            popupPanel = true;
            popupReveal = 0f;
            popupScroll = 0;
            popupX = Math.max(K(8), wScrW - K(440));
            popupY = K(92);
        }, "The look of Zeus: colour, layout, intro and keys");
        cur -= s + gap;
        headerButton(g, cur, cy - s / 2, s, "tiles", 0xFFD7E5FF, 0xFFFFFFFF, bl, b -> {
            UiState.tidy();
            ORDER.clear();
            ORDER.add(UiState.FAVOURITES);
            for (Category c : Category.values()) ORDER.add(c.name());
            pageAt = System.nanoTime();
        }, "Tidy up: put every window back into the neat grid");
        cur -= gap;

        Module profMod = Zeus.get("profiles");
        String sortText = "Sort: " + sortName();
        int chipW = tw(sortText, 13, Ty.SEMI) + K(46);
        int room = cur - (verEnd + K(30));
        if (profMod != null && room >= K(200) + chipW + K(12) + K(130)) {
            String prof = "Profiles";
            String sav = com.zlorbie788.zeus.mods.Profiles.savedCount() + " saved";
            int pw0 = K(18) + K(8) + K(9) + tw(prof, 13, Ty.SEMI) + K(10) + tw(sav, 11.5, Ty.MED) + K(16);
            int pxx = cur - pw0;
            boolean hot = hov(pxx, cy - s / 2, pw0, s);
            boolean openNow = popup == profMod;
            Ty.fill(g, pxx, cy - s / 2, pw0, s, K(13), hot || openNow ? 0xC00E1C40 : 0x85081026);
            Ty.ring(g, pxx, cy - s / 2, pw0, s, K(13), line, hot || openNow ? Draw.rgba(bl, 0.8f) : 0x4D6096FF);
            Ty.disc(g, pxx + K(18) + K(4), cy, Math.max(2, K(4)), 0xFF3DE07A);
            Ty.glow(g, pxx + K(18), cy - K(4), K(8), K(8), K(4), K(8), 0x553DE07A);
            int tx0 = pxx + K(18) + K(8) + K(9);
            tx(g, prof, tx0, cy, 13, Ty.SEMI, hot || openNow ? 0xFFFFFFFF : 0xFFCFE0FF);
            tx(g, sav, tx0 + tw(prof, 13, Ty.SEMI) + K(10), cy, 11.5, Ty.MED, hot || openNow ? 0xFFB9CCF0 : 0xFF8FA6D0);
            hit(pxx, cy - s / 2, pw0, s, b -> {
                if (popup == profMod) {
                    closePopup();
                    return;
                }
                openPopup(profMod);
                popupX = Math.max(K(8), Math.min(wScrW - K(428), pxx + pw0 - K(420)));
                popupY = K(92);
            }, "Your saved profiles: save, load and share sets of settings");
            cur = pxx - K(12);
            room = cur - (verEnd + K(30));
        }
        boolean chip = room >= K(200) + chipW + K(12);
        if (chip) {
            int sx = cur - chipW;
            boolean hot = hov(sx, cy - s / 2, chipW, s);
            boolean on = UiSettings.SORT.index() != 0;
            Ty.fill(g, sx, cy - s / 2, chipW, s, K(13), hot ? 0xC00E1C40 : on ? Draw.rgba(bl, 0.18f) : 0x85081026);
            Ty.ring(g, sx, cy - s / 2, chipW, s, K(13), line, hot || on ? Draw.rgba(bl, 0.8f) : 0x4D6096FF);
            Glyphs.drawPx(g, "sortud", sx + K(20), cy, K(18), hot || on ? 0xFFFFFFFF : 0xFFB9CCF0);
            tx(g, sortText, sx + K(36), cy, 13, Ty.SEMI, hot || on ? 0xFFFFFFFF : 0xFFCFE0FF);
            hit(sx, cy - s / 2, chipW, s, b -> {
                int n = UiSettings.SORT.index();
                UiSettings.SORT.set((n + (b == 1 ? 2 : 1)) % 3);
                scroll = 0;
                pageAt = System.nanoTime();
            }, "Sort the modules: category order, the ones you use most first, or A to Z. Click to change.");
            cur = sx - K(12);
            room = cur - (verEnd + K(30));
        }
        int sw = Math.max(K(150), Math.min(K(440), room));
        drawSearch(g, cur - sw, cy - s / 2, sw, s, bl);
    }

    private void drawWin(GuiGraphics g, WinDef def, int x, int y, int bodyMax, int bl, int bl2, int second, float dt, boolean searching) {
        String key = def.key;
        List<Module> mods = def.mods;
        int ww = wW;
        UiState.Win win = UiState.win(key);
        boolean collapsed = win.collapsed && !searching;
        int contentH = def.content;
        float open = def.open;
        int bodyH = Math.round(Math.min(contentH, bodyMax) * open);
        int total = wHead + bodyH;
        int maxScroll = Math.max(0, contentH - bodyH);
        win.scroll = Math.max(0, Math.min(maxScroll, win.scroll));
        winRect.put(key, new int[] {x, y, ww, total});
        int line = Math.max(1, Math.round(k));
        int rr = K(15);
        boolean lifted = key.equals(dragWin) && dragMoved;
        int tint = Draw.mix(def.color, bl, 0.35f);

        Ty.glow(g, x, y + K(4), ww, total, rr, lifted ? K(26) : K(16), lifted ? 0x99000000 : 0x66000000);
        Ty.fill(g, x, y, ww, total, rr, 0xF00A1122);
        g.enableScissor(x, y, x + ww, y + wHead);
        Ty.fill(g, x, y, ww, wHead + rr * 2, rr, Draw.rgba(tint, 0.20f));
        g.fillGradient(x, y, x + ww, y + wHead, Draw.rgba(0xFFFFFFFF, 0.05f), 0x00FFFFFF);
        g.disableScissor();
        if (open > 0.1f) g.fill(x + line, y + wHead - line, x + ww - line, y + wHead, Draw.rgba(tint, 0.45f));
        Ty.ring(g, x, y, ww, total, rr, line, Draw.mix(cLine(), Draw.rgba(tint, 0.9f), lifted ? 0.7f : 0.25f));

        int hc = y + wHead / 2;
        int tile = K(30);
        Ty.fill(g, x + K(10), hc - tile / 2, tile, tile, K(9), Draw.rgba(tint, 0.26f));
        String glyph = def.cat != null ? Glyphs.forCategory(def.cat) : def.glyph;
        Glyphs.drawPx(g, glyph, x + K(10) + tile / 2f, hc, K(18), 0xFFFFFFFF);
        tx(g, def.label, x + K(48), hc, 15.5, Ty.BOLD, 0xFFFFFFFF);
        int on = 0;
        for (Module m : mods) if (m.enabled() && m.toggleable()) on++;
        int labW = tw(def.label, 15.5, Ty.BOLD);
        if (on > 0) {
            String s = Integer.toString(on);
            int bw2 = tw(s, 11.5, Ty.BOLD) + K(14);
            int bh2 = K(20);
            int bx2 = x + K(48) + labW + K(8);
            if (bx2 + bw2 < x + ww - K(34)) {
                Ty.fill(g, bx2, hc - bh2 / 2, bw2, bh2, bh2 / 2, Draw.rgba(bl, 0.30f));
                txC(g, s, bx2 + bw2 / 2, hc, 11.5, Ty.BOLD, 0xFFDCEBFF);
            }
        }
        Glyphs.drawPx(g, collapsed ? "chevright" : "chevdown", x + ww - K(18), hc, K(18), 0xFFD7E5FF);

        hit(x, y, ww, total, b -> { }, null);
        hit(x, y, ww, wHead, b -> {
            if (b != 0) return;
            dragWin = key;
            dragPopup = false;
            dragMoved = false;
            pressX = mx;
            pressY = my;
            dragOffX = mx - x;
            dragOffY = my - y;
            ORDER.remove(key);
            ORDER.add(key);
        }, "Drag to move, click to fold");
        if (bodyH <= 0) return;

        int top = y + wHead;
        g.enableScissor(x, top, x + ww, top + bodyH);
        clipTop = top;
        clipBottom = top + bodyH;
        listTop = top;
        listBottom = top + bodyH;
        int ry = top + K(4) - Math.round(win.scroll);
        float entered = (System.nanoTime() - pageAt) / 1e9f;
        int rowIndex = 0;
        boolean winHot = hov(x, top, ww, bodyH);
        if (mods.isEmpty()) {
            tx(g, "Nothing starred yet.", x + K(16), top + K(18), 13, Ty.MED, cSub());
            tx(g, Ty.fit("Click the star on a module.", ww - K(30), tp(12.5), Ty.REG), x + K(16), top + K(38), 12.5, Ty.REG, cMut());
        }
        for (Module m : mods) {
            if (ry + wRow >= top && ry <= top + bodyH) {
                float stagger = UiSettings.CALM.on() || Perf.fast() ? 1f
                        : Math.max(0f, Math.min(1f, (entered - Math.min(rowIndex, 10) * 0.025f) / 0.22f));
                float slide = 1f - (1f - stagger) * (1f - stagger);
                drawWinRow(g, m, x, ry + Math.round((1f - slide) * K(10)), ww, bl, bl2, dt, winHot);
            }
            ry += wRow;
            rowIndex++;
        }
        g.disableScissor();
        clipTop = -1;
        clipBottom = -1;
        listTop = 0;
        listBottom = wScrH;
        if (maxScroll > 0) {
            int sbh = Math.max(K(18), Math.round(bodyH * (bodyH / (float) contentH)));
            int sby = top + Math.round((bodyH - sbh) * (win.scroll / maxScroll));
            Ty.fill(g, x + ww - K(7), sby, Math.max(2, K(3)), sbh, Math.max(1, K(1.5)), Draw.rgba(bl, 0.6f));
        }
    }

    private final Map<Module, String> rowKeys = new HashMap<>();

    private void drawWinRow(GuiGraphics g, Module m, int x, int y, int w, int bl, int bl2, float dt, boolean winHot) {
        int rh = wRow;
        boolean hot = hov(x, y, w, rh) && my >= clipTop && my < clipBottom;
        String key = rowKeys.computeIfAbsent(m, kk -> "row" + kk.id);
        float hv = ease(key, hot, dt);
        float on = toggle.getOrDefault(m.id, m.enabled() ? 1f : 0f);
        float want = m.enabled() ? 1f : 0f;
        on += (want - on) * Math.min(1f, dt * 16f);
        if (Math.abs(want - on) < 0.01f) on = want;
        toggle.put(m.id, on);
        int ix = x + K(7);
        int iw = w - K(14);
        int iy = y + K(2);
        int ih = rh - K(4);
        int rr = K(10);
        if (on > 0.01f) {
            Ty.fill(g, ix, iy, iw, ih, rr, Draw.rgba(bl, 0.13f * on));
            Ty.fillLeftEnd(g, ix, iy, ih, rr, Draw.rgba(bl, 0.18f * on));
            ramp().draw(g, ix + rr, iy + 1, Math.max(1, Math.round(iw * 0.62f) - rr), Math.max(1, ih - 2), Draw.rgba(bl, 0.20f * on));
            Ty.fill(g, ix + K(3), iy + ih / 4, Math.max(2, K(3)), ih / 2, Math.max(1, K(1.5)), Draw.rgba(bl2, on));
        } else if (hv > 0.01f) {
            Ty.fill(g, ix, iy, iw, ih, rr, Draw.rgba(0xFFFFFFFF, 0.06f * hv));
        }
        int cy = y + rh / 2;
        boolean fav = Usage.favourite(m);
        int nameX = ix + K(14);
        if (fav || winHot) {
            boolean starHot = hov(ix, y, K(24), rh);
            int starCol = fav ? Accent.WARN : starHot ? 0xFFFFE08A : Draw.rgba(0xFFFFFFFF, 0.22f);
            Glyphs.drawPx(g, fav ? "starfill" : "star", ix + K(16), cy, K(15), starCol);
            nameX = ix + K(32);
        }
        int right = ix + iw - K(10);
        int sw = K(34);
        int sh = K(19);
        int sx = right - sw;
        if (m.toggleable()) {
            drawSwitchD(g, sx, cy - sh / 2, sw, sh, on, bl);
            Long burstAt = BURSTS.get(m.id);
            if (burstAt != null) {
                float bp = (System.nanoTime() - burstAt) / 0.6e9f;
                if (bp >= 1f) BURSTS.remove(m.id);
                else Thunder.burst(g, sx + sw * 0.75f, cy, K(40), bp, 1f);
            }
            right = sx - K(8);
        } else {
            Glyphs.drawPx(g, "chevright", right - K(8), cy, K(16), hot ? 0xFF9CC7FF : 0xFF4A6AA8);
            right -= K(24);
        }
        if (!m.settings().isEmpty() && m.toggleable() && (hot || on > 0.5f)) {
            Glyphs.drawPx(g, "sliders", right - K(8), cy, K(15), hot ? 0xFFBFD8FF : Draw.rgba(0xFFBFD8FF, 0.45f));
            right -= K(24);
        }
        float cost = m.enabled() && UiSettings.SHOW_COST.on() ? m.costPct : 0f;
        if (cost >= 0.05f && right - nameX > K(110)) {
            int fpsNow = Math.max(1, com.zlorbie788.zeus.mods.Brain.ctx().fps);
            String ct = String.format(Locale.ROOT, "%.2f ms", cost / 100.0 * 1000.0 / fpsNow);
            int cc = cost < 1f ? 0xFF7FD7A4 : cost < 3f ? Accent.WARN : 0xFFFF7C8A;
            txR(g, ct, right, cy, 11, Ty.SEMI, Draw.rgba(cc, 0.9f));
            right -= tw(ct, 11, Ty.SEMI) + K(8);
        }
        int tc = Draw.mix(Draw.mix(cDim(), 0xFFFFFFFF, hv * 0.6f), 0xFFFFFFFF, on);
        tx(g, Ty.fit(m.name, right - nameX, tp(14.5), on > 0.5f ? Ty.SEMI : Ty.MED), nameX, cy, 14.5, on > 0.5f ? Ty.SEMI : Ty.MED, tc);
        final String hint = m.desc + (m.toggleable() ? "  (Right click: settings)" : "");
        hit(ix, iy, iw, ih, bt -> {
            if (bt == 0 && m.toggleable()) m.toggle();
            else openPopup(m);
        }, hint);
        if (m.toggleable()) {
            hit(sx - K(4), iy, sw + K(8), ih, bt -> {
                if (bt != 0) {
                    openPopup(m);
                    return;
                }
                if (!m.enabled()) BURSTS.put(m.id, System.nanoTime());
                m.toggle();
            }, "Switch " + m.name + (m.enabled() ? " off" : " on"));
            if (!m.settings().isEmpty()) hit(sx - K(32), iy, K(26), ih, bt -> openPopup(m), "Settings of " + m.name);
        }
        hit(ix, iy, K(26), ih, bt -> Usage.toggleFavourite(m), fav ? "Remove " + m.name + " from your favourites" : "Add " + m.name + " to your favourites");
    }

    private void openPopup(Module m) {
        popup = m;
        popupPanel = false;
        popupReveal = 0f;
        popupScroll = 0;
        popupX = Math.min(wScrW - K(420), mx + K(24));
        popupY = Math.max(K(80), my - K(40));
    }

    private void closePopup() {
        popup = null;
        popupPanel = false;
        listening = null;
        listeningOpen = false;
    }

    private void drawPopupWin(GuiGraphics g, int bl, int bl2, float dt) {
        if (popup == null && !popupPanel) return;
        List<Setting> rows = popupPanel ? Zeus.UI.settings() : popup.settings();
        int w = K(420);
        int line = Math.max(1, Math.round(k));
        int rr = K(16);
        int sidePad = K(22);
        int head = K(104);
        int[] ax = new int[rows.size()];
        int[] ay = new int[rows.size()];
        int[] aw = new int[rows.size()];
        int[] ah = new int[rows.size()];
        int setH = arrange(rows, w - 2 * sidePad, ax, ay, aw, ah);
        int extra = popupPanel ? K(56) : 0;
        boolean empty = !popupPanel && setH == 0;
        int inner = K(10) + extra + (empty ? K(52) : setH) + K(16);
        int maxH = wScrH - K(170);
        int bodyH = Math.min(inner, Math.max(K(110), maxH - head));
        int h = head + bodyH;
        popupX = Math.max(K(8), Math.min(wScrW - w - K(8), popupX));
        popupY = Math.max(K(8), Math.min(wScrH - h - K(76), popupY));
        popupReveal += (1f - popupReveal) * Math.min(1f, dt * 18f);
        if (popupReveal > 0.995f) popupReveal = 1f;
        int x = Math.round(popupX);
        int y = Math.round(popupY + (1f - popupReveal) * K(16));
        int col = popupPanel ? bl : Draw.mix(popup.category.color(), bl, 0.3f);
        popupContent = inner;
        popupViewTop = y + head;
        popupViewH = bodyH;
        int maxScroll = Math.max(0, inner - bodyH);
        popupScroll = Math.max(0, Math.min(maxScroll, popupScroll));

        g.nextStratum();
        winRect.put(null, new int[] {x, y, w, h});
        Ty.glow(g, x, y + K(6), w, h, rr, K(30), 0xAA000000);
        Ty.fill(g, x, y, w, h, rr, 0xFF080E20);
        g.enableScissor(x, y, x + w, y + head);
        Ty.fill(g, x, y, w, head + rr * 2, rr, Draw.rgba(col, 0.17f));
        g.fillGradient(x, y, x + w, y + head, Draw.rgba(0xFFFFFFFF, 0.05f), 0x00FFFFFF);
        g.disableScissor();
        g.fill(x + line, y + head - line, x + w - line, y + head, Draw.rgba(col, 0.40f));
        Ty.ring(g, x, y, w, h, rr, line, Draw.rgba(col, 0.55f));
        hit(x, y, w, h, b -> { }, null);
        hit(x, y, w, head, b -> {
            if (b != 0) return;
            dragPopup = true;
            dragWin = null;
            dragMoved = false;
            pressX = mx;
            pressY = my;
            dragOffX = mx - popupX;
            dragOffY = my - popupY;
        }, "Drag to move");

        int hc = y + K(38);
        int tile = K(46);
        int tx0 = x + sidePad;
        String glyph = popupPanel ? "sliders" : Glyphs.forModule(popup.id, popup.category);
        float onP = popupPanel ? 1f : toggle.getOrDefault(popup.id, popup.enabled() ? 1f : 0f);
        Ty.glow(g, tx0, hc - tile / 2, tile, tile, K(13), K(10), Draw.rgba(col, 0.40f * onP));
        Ty.fill(g, tx0, hc - tile / 2, tile, tile, K(13), Draw.mix(0xB3050A18, Draw.mix(col, 0xFF0B1E4A, 0.12f), onP));
        Ty.ring(g, tx0, hc - tile / 2, tile, tile, K(13), line, Draw.mix(cLine(), Draw.rgba(0xFFA0CDFF, 0.6f), onP));
        Glyphs.drawPx(g, glyph, tx0 + tile / 2f, hc, K(24), Draw.mix(0xFF86A2D6, 0xFFFFFFFF, onP));

        int cs = K(34);
        int cx = x + w - sidePad + K(4) - cs;
        boolean hotC = hov(cx, hc - cs / 2, cs, cs);
        Ty.fill(g, cx, hc - cs / 2, cs, cs, K(10), hotC ? Draw.rgba(Accent.BAD, 0.28f) : 0x85081026);
        Ty.ring(g, cx, hc - cs / 2, cs, cs, K(10), line, hotC ? 0xFFFF7080 : 0x4D6096FF);
        Glyphs.drawPx(g, "x", cx + cs / 2f, hc, K(17), hotC ? 0xFFFFD0D5 : 0xFFB9CCF0);
        hit(cx, hc - cs / 2, cs, cs, b -> closePopup(), "Close (Esc)");
        int cur = cx - K(12);
        if (!popupPanel && popup.toggleable()) {
            final Module pm = popup;
            int sw = K(46);
            int sh = K(26);
            drawSwitchD(g, cur - sw, hc - sh / 2, sw, sh, onP, bl);
            hit(cur - sw - K(4), hc - K(20), sw + K(8), K(40), b -> pm.toggle(), "Switch " + pm.name + (pm.enabled() ? " off" : " on"));
            cur -= sw + K(12);
            boolean fav = Usage.favourite(pm);
            int ss = K(26);
            Glyphs.drawPx(g, fav ? "starfill" : "star", cur - ss / 2f, hc, K(19), fav ? Accent.WARN : hov(cur - ss, hc - K(18), ss, K(36)) ? 0xFFFFE08A : 0xFF7F93BD);
            hit(cur - ss - K(2), hc - K(18), ss + K(4), K(36), b -> Usage.toggleFavourite(pm), fav ? "Remove from your favourites" : "Add to your favourites");
            cur -= ss + K(10);
            if (pm.key != null) {
                boolean wait = listening == pm;
                KeyMapping km = pm.key;
                String text = wait ? "Press a key" : km.isUnbound() ? "None" : km.getTranslatedKeyMessage().getString();
                int kw = keyChipW(text);
                keyChip(g, text, wait, km.isUnbound(), cur - kw, hc, kw, bl, b -> {
                    if (b == 1) {
                        setKey(km, InputConstants.UNKNOWN);
                        listening = null;
                        listeningOpen = false;
                        return;
                    }
                    listening = pm;
                    listeningOpen = false;
                });
                cur -= kw + K(12);
            }
        }
        int textLeft = tx0 + tile + K(16);
        int textRight = cur - K(8);
        String title = popupPanel ? "Zeus" : popup.name;
        String sub = popupPanel ? "Colour, layout, intro and keys" : popup.desc;
        tx(g, Ty.fit(title, textRight - textLeft, tp(19), Ty.BOLD), textLeft, hc, 19, Ty.BOLD, 0xFFFFFFFF);
        int subW = w - 2 * sidePad;
        List<String> subLines = wrapD(sub, subW, tp(12.5));
        for (int li = 0; li < Math.min(2, subLines.size()); li++) {
            String ln = li == 1 && subLines.size() > 2 ? Ty.fit(subLines.get(li) + " …", subW, tp(12.5), Ty.REG) : subLines.get(li);
            tx(g, ln, tx0, y + K(78) + li * K(17) - (subLines.size() > 1 ? K(6) : 0), 12.5, Ty.REG, cDim());
        }

        int top = y + head;
        g.enableScissor(x + line, top, x + w - line, top + bodyH);
        clipTop = top;
        clipBottom = top + bodyH;
        listTop = top;
        listBottom = top + bodyH;
        int sy0 = top + K(10) - Math.round(popupScroll);
        if (popupPanel) {
            int rcy = sy0 + K(24);
            tx(g, "Open key", x + sidePad, rcy, 14, Ty.SEMI, 0xFFFFFFFF);
            tx(g, "Opens and closes this screen", x + sidePad + tw("Open key", 14, Ty.SEMI) + K(12), rcy, 12, Ty.REG, cMut());
            KeyMapping ok = ZeusClient.OPEN;
            boolean wait = listeningOpen;
            String text = wait ? "Press a key" : ok.isUnbound() ? "None" : ok.getTranslatedKeyMessage().getString();
            int kw = keyChipW(text);
            keyChip(g, text, wait, ok.isUnbound(), x + w - sidePad - kw, rcy, kw, bl, b -> {
                if (b == 1) {
                    setKey(ok, InputConstants.UNKNOWN);
                    listening = null;
                    listeningOpen = false;
                    return;
                }
                listening = null;
                listeningOpen = true;
            });
            sy0 += extra;
        }
        if (empty) {
            tx(g, "This module has no settings.", x + sidePad, sy0 + K(20), 13.5, Ty.MED, cSub());
        }
        for (int i = 0; i < rows.size(); i++) {
            if (aw[i] > 0) drawSettingD(g, rows.get(i), x + sidePad + ax[i], sy0 + ay[i], aw[i], ah[i], bl, bl2, dt);
        }
        g.disableScissor();
        clipTop = -1;
        clipBottom = -1;
        listTop = 0;
        listBottom = wScrH;
        if (maxScroll > 0) {
            int sbh = Math.max(K(24), Math.round(bodyH * (bodyH / (float) inner)));
            int sby = top + Math.round((bodyH - sbh) * (popupScroll / maxScroll));
            Ty.fill(g, x + w - K(9), sby, Math.max(2, K(3)), sbh, Math.max(1, K(1.5)), Draw.rgba(col, 0.7f));
        }
    }

    private int[] pushClear(List<int[]> others, int x, int y, int w, int h, int maxY) {
        int minY = wMinY;
        int maxX = Math.max(0, wScrW - w);
        x = Math.max(0, Math.min(maxX, x));
        y = Math.max(minY, Math.min(maxY, y));
        for (int it = 0; it < 10; it++) {
            int[] hit = null;
            for (int[] r : others) {
                if (x < r[0] + r[2] + wGap && x + w + wGap > r[0] && y < r[1] + r[3] + wGap && y + h + wGap > r[1]) {
                    hit = r;
                    break;
                }
            }
            if (hit == null) return new int[] {x, y};
            int[][] options = {
                    {hit[0] - wGap - w, y}, {hit[0] + hit[2] + wGap, y},
                    {x, hit[1] - wGap - h}, {x, hit[1] + hit[3] + wGap}};
            int bestI = -1;
            int bestD = Integer.MAX_VALUE;
            for (int i = 0; i < options.length; i++) {
                int ox = options[i][0];
                int oy = options[i][1];
                if (ox < 0 || ox > maxX || oy < minY || oy > maxY) continue;
                int d = Math.abs(ox - x) + Math.abs(oy - y);
                if (d < bestD) {
                    bestD = d;
                    bestI = i;
                }
            }
            if (bestI < 0) return null;
            x = options[bestI][0];
            y = options[bestI][1];
        }
        return null;
    }

    private int[] snapWindow(List<int[]> others, int x, int y, int w, int h) {
        final int reach = K(6);
        int[] xs = {K(8), wScrW - w - K(8)};
        for (int v : xs) if (Math.abs(x - v) <= reach) x = v;
        if (Math.abs(y - wTop) <= reach) y = wTop;
        for (int[] r : others) {
            boolean sideBySide = y < r[1] + r[3] + wGap + reach && y + h + wGap + reach > r[1];
            boolean stacked = x < r[0] + r[2] + wGap + reach && x + w + wGap + reach > r[0];
            if (Math.abs(x - r[0]) <= reach && stacked) x = r[0];
            if (Math.abs(y - r[1]) <= reach && sideBySide) y = r[1];
            if (sideBySide) {
                if (Math.abs(x - (r[0] + r[2] + wGap)) <= reach) x = r[0] + r[2] + wGap;
                if (Math.abs(x + w + wGap - r[0]) <= reach) x = r[0] - wGap - w;
            }
            if (stacked) {
                if (Math.abs(y - (r[1] + r[3] + wGap)) <= reach) y = r[1] + r[3] + wGap;
                if (Math.abs(y + h + wGap - r[1]) <= reach) y = r[1] - wGap - h;
            }
        }
        return new int[] {x, y};
    }

    private static boolean fixedSize(WinDef d) {
        return d.cat == null;
    }

    private static final class WinDef {
        final String key;
        final String label;
        final int color;
        final Category cat;
        final String glyph;
        final List<Module> mods;
        float open;
        int content;

        WinDef(String key, String label, int color, Category cat, String glyph, List<Module> mods) {
            this.key = key;
            this.label = label;
            this.color = color;
            this.cat = cat;
            this.glyph = glyph;
            this.mods = mods;
        }
    }

    private List<WinDef> defsCache;
    private String defsCacheKey = "";
    private long defsCacheAt;
    private int winW = WIN_W;

    private int windowWidth() {
        if (cachedWinW > 0) return cachedWinW;
        List<Integer> widths = new ArrayList<>();
        for (Module m : Zeus.all()) if (m.toggleable()) widths.add(Draw.width(m.name));
        java.util.Collections.sort(widths);
        int p = widths.isEmpty() ? 80 : widths.get(Math.min(widths.size() - 1, (int) (widths.size() * 0.97)));
        cachedWinW = Math.max(96, Math.min(114, p + 22));
        return cachedWinW;
    }

    private int cachedWinW;

    private float openOf(String key, boolean collapsed, float dt) {
        float target = collapsed ? 0f : 1f;
        float open = windowOpen.getOrDefault(key, 0f);
        open += (target - open) * Math.min(1f, dt * 17f);
        if (Math.abs(target - open) < 0.008f) open = target;
        windowOpen.put(key, open);
        return open;
    }

    private String sortName() {
        return switch (UiSettings.SORT.index()) {
            case 1 -> "Most used";
            case 2 -> "A to Z";
            default -> "Category";
        };
    }

    private void drawTip(GuiGraphics g) {
        g.nextStratum();
        String t = tip;
        int maxW = 220;
        List<String> lines = wrap(t, maxW);
        int w = 0;
        for (String l : lines) w = Math.max(w, Draw.width(l));
        w += 14;
        int h = lines.size() * 10 + 8;
        int x = Math.min(width - w - 4, mx + 12);
        int y = Math.min(height - h - 4, my + 14);
        Draw.shadow(g, x, y, w, h, 4, 4, 0.8f);
        Draw.rrect(g, x, y, w, h, 4, 0xFA0B0E18);
        Draw.rrectLine(g, x, y, w, h, 4, Draw.rgba(Accent.main(), 0.5f));
        int ly = y + 4;
        for (String l : lines) {
            Draw.text(g, l, x + 7, ly, Accent.TEXT);
            ly += 10;
        }
    }

    private static List<String> wrap(String s, int maxW) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : s.split(" ")) {
            String test = cur.length() == 0 ? word : cur + " " + word;
            if (Draw.width(test) > maxW && cur.length() > 0) {
                out.add(cur.toString());
                cur = new StringBuilder(word);
            } else {
                cur = new StringBuilder(test);
            }
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    private boolean hov(int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private int clipTop = -1;
    private int clipBottom = -1;

    private void hit(int x, int y, int w, int h, Click run, String tipText) {
        if (clipTop >= 0) {
            int y2 = Math.min(y + h, clipBottom);
            y = Math.max(y, clipTop);
            h = y2 - y;
            if (h <= 0) return;
        }
        hits.add(new Hit(x, y, w, h, run));
        if (tipText != null && hov(x, y, w, h)) tip = tipText;
    }

    private void applyDrag(double mouseX) {
        applyDragDev(mouseX * dragMul);
    }

    private void applyDragDev(double devX) {
        if (dragging == null) return;
        double f = (devX - dragX) / Math.max(1.0, dragW);
        f = Math.max(0, Math.min(1, f));
        dragging.set(dragging.min + f * (dragging.max - dragging.min));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (time() < Intro.LEAVE) {
            skipIntro();
            return true;
        }
        double x = event.x() * gs;
        double y = event.y() * gs;
        if (editing != null) stopEditing();
        if (listening != null || listeningOpen) {
            boolean onChip = false;
            for (Hit h : hits) if (x >= h.x && x < h.x + h.w && y >= h.y && y < h.y + h.h) onChip = true;
            if (!onChip) {
                listening = null;
                listeningOpen = false;
            }
        }
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (x >= h.x && x < h.x + h.w && y >= h.y && y < h.y + h.h) {
                if (event.button() <= 1) {
                    h.run.on(event.button());
                    return true;
                }
            }
        }
        if (columns && (popup != null || popupPanel)) {
            closePopup();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (dragging != null) {
            applyDrag(event.x());
            return true;
        }
        if (dragWin != null || dragPopup) {
            double ex = event.x() * gs;
            double ey = event.y() * gs;
            if (Math.abs(ex - pressX) + Math.abs(ey - pressY) > 3 * gs || dragMoved) dragMoved = true;
            if (dragMoved) {
                if (dragWin != null) {
                    UiState.Win w = UiState.win(dragWin);
                    int[] mine = winRect.get(dragWin);
                    int hh = mine != null ? mine[3] : wHead;
                    int maxY = Math.max(wMinY, wScrH - wHead - K(80));
                    int wantX = (int) Math.round(Math.max(0, Math.min(wScrW - wW, ex - dragOffX)));
                    int wantY = (int) Math.round(Math.max(wMinY, Math.min(maxY, ey - dragOffY)));
                    w.x = wantX / (float) gs;
                    w.y = wantY / (float) gs;
                    w.placed = true;
                    Zeus.markDirty();
                } else {
                    popupX = (float) (ex - dragOffX);
                    popupY = (float) (ey - dragOffY);
                }
            }
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        dragging = null;
        if (dragWin != null && !dragMoved) {
            UiState.Win w = UiState.win(dragWin);
            w.collapsed = !w.collapsed;
            Zeus.markDirty();
        }
        dragWin = null;
        dragPopup = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (scrollY == 0) return super.mouseScrolled(x, y, scrollX, scrollY);
        if (columns) {
            double dx = x * gs;
            double dy = y * gs;
            int[] pr = winRect.get(null);
            if ((popup != null || popupPanel) && pr != null && dx >= pr[0] && dx < pr[0] + pr[2] && dy >= pr[1] && dy < pr[1] + pr[3]) {
                popupScroll -= (float) scrollY * K(46);
                return true;
            }
            for (int i = ORDER.size() - 1; i >= 0; i--) {
                String c = ORDER.get(i);
                int[] r = winRect.get(c);
                if (r != null && dx >= r[0] && dx < r[0] + r[2] && dy >= r[1] && dy < r[1] + r[3]) {
                    UiState.win(c).scroll -= (float) scrollY * wRow * 0.9f;
                    return true;
                }
            }
            return true;
        }
        if (x >= sbRight) {
            scroll -= (float) scrollY * 28f * gs;
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    private void stopEditing() {
        editing = null;
        if (field != null) {
            field.setFocused(false);
            field.visible = false;
        }
        setFocused(null);
    }

    private void focusField() {
        if (field == null) return;
        if (search != null && search.isFocused()) search.setFocused(false);
        field.visible = true;
        field.setFocused(true);
        setFocused(field);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (editing != null && field != null) {
            focusField();
            if (field.charTyped(event)) return true;
        }
        return super.charTyped(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (time() < Intro.LEAVE) {
            skipIntro();
            return true;
        }
        if (listening != null || listeningOpen) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                listening = null;
                listeningOpen = false;
                return true;
            }
            KeyMapping target = listeningOpen ? ZeusClient.OPEN : listening.key;
            if (key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE) setKey(target, InputConstants.UNKNOWN);
            else setKey(target, InputConstants.Type.KEYSYM.getOrCreate(key));
            listening = null;
            listeningOpen = false;
            return true;
        }
        if (editing != null && field != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                stopEditing();
                return true;
            }
            focusField();
            return super.keyPressed(event);
        }
        if (key == GLFW.GLFW_KEY_K && minecraft != null
                && (event.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0) {
            minecraft.setScreen(new PaletteScreen(this));
            return true;
        }
        if (search != null && search.isFocused()) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                if (search.getValue().isEmpty()) {
                    search.setFocused(false);
                    setFocused(null);
                } else {
                    search.setValue("");
                }
                return true;
            }
            return super.keyPressed(event);
        }
        if (search != null
                && (key == GLFW.GLFW_KEY_SLASH || key == GLFW.GLFW_KEY_F && (event.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0)
                && (key != GLFW.GLFW_KEY_SLASH || (event.modifiers() & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_ALT)) == 0)) {
            search.setFocused(true);
            setFocused(search);
            search.setCursorPosition(search.getValue().length());
            return true;
        }
        if (key == GLFW.GLFW_KEY_HOME && !columns) {
            scroll = 0;
            return true;
        }
        if (key == GLFW.GLFW_KEY_END && !columns) {
            scroll = 1e7f;
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE && columns && (popup != null || popupPanel)) {
            closePopup();
            return true;
        }
        if (key == GLFW.GLFW_KEY_PAGE_DOWN) {
            scroll += 120 * gs;
            return true;
        }
        if (key == GLFW.GLFW_KEY_PAGE_UP) {
            scroll -= 120 * gs;
            return true;
        }
        if (ZeusClient.OPEN != null && !ZeusClient.OPEN.isUnbound() && InputConstants.Type.KEYSYM.getOrCreate(key).getName().equals(ZeusClient.OPEN.saveString())) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }
}
