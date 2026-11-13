package com.zlorbie788.zeus.mods;
import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Setting;
import com.zlorbie788.zeus.core.Zeus;
import com.zlorbie788.zeus.ui.ArmArt;
import com.zlorbie788.zeus.ui.Draw;
import com.zlorbie788.zeus.ui.KnifeArt;
import com.zlorbie788.zeus.ui.KnifeMesh;
import com.zlorbie788.zeus.ui.KnifePickerScreen;
import com.zlorbie788.zeus.ui.Lightning;
import com.zlorbie788.zeus.ui.Perf;
import com.zlorbie788.zeus.ui.Rig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class KnifeSwing extends Module {
    private static final int OPEN = 0;
    private static final int FAN = 1;
    private static final int SHOW = 2;
    private static final int WHIRL = 3;
    private static final int ROLL = 4;
    private static final int ROLL_BACK = 5;
    private static final int TWIRL = 6;
    private static final int GUN = 7;
    private static final int AERIAL = 8;
    private static final int CLOSE = 9;
    private static final int SLASHING = 10;
    private static final int RSPIN = 11;
    private static final int RREV = 12;
    private static final int RFLURRY = 13;
    private static final int RPIN = 14;
    private static final int KFLIP = 15;
    private static final int KAERIAL = 16;
    private static final int KSHOW = 17;
    private static final int KSLASH = 18;
    private static final int KTRIPLE = 19;
    private static final int KCATCH = 20;
    private static final int KEIGHT = 21;
    private static final int KFLIPY = 22;
    private static final int KSLASH3 = 23;
    private static final int KSNAP = 24;
    private static final int KHYPER = 25;
    private static final int KJUGGLE = 26;
    private static final int SFAN = 27;
    private static final int HWHIRL = 28;
    private static final int FRENZY = 29;
    private static final int KORBIT = 30;
    private static final int KCROSS = 31;
    private static final int KDOUBLE = 32;
    private static final int KSTORM = 33;
    private static final int HELI = 34;
    private static final int BDOUBLE = 35;
    private static final int LATCH = 36;
    private static final int CASCADE = 37;
    private static final int INSPECT = 38;
    private static final int SB_DRAW = 39;
    private static final int SB_ROLL = 40;
    private static final int SB_FAN = 41;
    private static final int SB_ZEN = 42;
    private static final int SB_AERIAL = 43;
    private static final int SB_INSPECT = 44;
    private static final int SK_DRAW = 45;
    private static final int SK_INSPECT = 46;
    private static final int SK_FLIP = 47;
    private static final int SK_SPIN = 48;
    private static final int SK_PIN = 49;
    private static final int SK_TOSS = 50;
    private static final String[] CLIP_NAME = {"draw", "inspect2", "fan", "zen", "aerial", "inspect", "draw", "inspect", "inspect2", "spin", "pinwheel", "toss"};
    private static final float[] CLIP_D = {1.35f, 3.4f, 3.2f, 3.0f, 2.6f, 3.9f, 1.3f, 3.5f, 3.0f, 2.2f, 3.0f, 2.8f};

    private static final float BASE = 1.3f;
    private static final float[] MOVE_TIME = {0.55f, 1.05f, 1.7f, 1.15f, 1.0f, 1.0f, 1.2f, 1.1f, 1.6f, 0.5f, 0.72f,
        0.9f, 0.9f, 1.6f, 1.15f, 0.95f, 1.35f, 1.35f, 0.7f,
        1.2f, 0.85f, 1.6f, 0.95f, 1.15f, 1.35f, 1.7f, 1.9f,
        0.95f, 1.1f, 1.3f,
        1.5f, 1.4f, 1.9f, 1.5f, 1.5f, 2.0f, 1.1f, 3.0f, 3.2f,
        1.35f * 1.3f, 3.4f * 1.3f, 3.2f * 1.3f, 3.0f * 1.3f, 2.6f * 1.3f, 3.9f * 1.3f, 1.3f * 1.3f, 3.5f * 1.3f, 3.0f * 1.3f, 2.2f * 1.3f, 3.0f * 1.3f, 2.8f * 1.3f};
    public static final String[] TRICKS = {"Chaos combo", "Flick open", "Fan", "Showcase", "Handle whirl", "Rollover", "Reverse rollover", "Twirl", "Gunslinger", "Aerial toss", "Snap shut", "Speed fan", "Hyper whirl", "Flip frenzy", "Helicopter", "Double toss", "Latch flip", "Cascade", "Inspect", "Studio draw", "Studio rollover", "Studio fan", "Studio zen", "Studio aerial toss", "Studio inspect", "Random"};
    private static final int[][] SCRIPT = {
        {FAN, SFAN, SHOW, HWHIRL, ROLL, HELI, TWIRL, FRENZY, GUN, BDOUBLE, ROLL_BACK, CASCADE, CLOSE},
        {OPEN}, {FAN}, {SHOW}, {WHIRL}, {ROLL}, {ROLL_BACK}, {TWIRL}, {GUN}, {AERIAL}, {CLOSE}, {SFAN}, {HWHIRL}, {FRENZY},
        {HELI}, {BDOUBLE}, {LATCH}, {CASCADE}, {INSPECT},
        {SB_DRAW}, {SB_ROLL}, {SB_FAN}, {SB_ZEN}, {SB_AERIAL}, {SB_INSPECT}
    };
    public static final String[] KTRICKS = {"Chaos combo", "Ring spin", "Reverse spin", "Ring flurry", "Pinwheel", "Backflip", "Toss and catch", "Showcase", "Slash",
        "Triple spin", "Catch flip", "Figure eight", "Side flip", "Slash combo", "Spin stop", "Hyper spin", "Juggle", "Wrist orbit", "Cross spin", "Double toss", "Ring storm", "Inspect", "Studio draw", "Studio ring spin", "Studio back flip", "Studio pinwheel", "Studio toss", "Studio inspect", "Random"};
    private static final int[][] KSCRIPT = {
        {KSHOW, RSPIN, KORBIT, KTRIPLE, KEIGHT, KCROSS, RFLURRY, KFLIP, KDOUBLE, KSNAP, KHYPER},
        {RSPIN}, {RREV}, {RFLURRY}, {RPIN}, {KFLIP}, {KAERIAL}, {KSHOW}, {KSLASH},
        {KTRIPLE}, {KCATCH}, {KEIGHT}, {KFLIPY}, {KSLASH3}, {KSNAP}, {KHYPER}, {KJUGGLE}, {KORBIT}, {KCROSS}, {KDOUBLE}, {KSTORM}, {INSPECT},
        {SK_DRAW}, {SK_SPIN}, {SK_FLIP}, {SK_PIN}, {SK_TOSS}, {SK_INSPECT}
    };
    private static KnifeSwing self;
    private long drewAt;
    private long frames;
    private long enabledAt;
    private boolean warned;
    private static final String[] TYPES = types();
    private static String[] types() {
        String[] st = KnifeArt.straightNames();
        String[] all = new String[2 + st.length];
        all[0] = "Butterfly knife";
        all[1] = "Karambit";
        System.arraycopy(st, 0, all, 2, st.length);
        return all;
    }
    private final Setting.Choice type = add(new Setting.Choice("Knife type", "Butterfly knife: the balisong with its flips. Karambit: the curved claw knife whose ring sits on your finger and which spins around it. The others are the straight and curved knives of CS2 (Bayonet, M9 Bayonet, Huntsman, Flip, Gut, Falchion, Bowie, Navaja, Stiletto, Talon, Ursus, Classic, Paracord, Survival, Nomad, Skeleton, Kukri and the Shadow Daggers) plus the Tantō, Kris, Cleaver, Kunai, Jambiya, Khopesh, Machete and Dagger. They all use the karambit's tricks.", 0, TYPES));
    private final Setting.Choice sfinish = add(new Setting.Choice("Knife finish", "The paint of the straight knives: steel, Fade, Slaughter, Doppler, Case hardened and so on.", 0, KnifeArt.SFINISHES)).showIf(() -> self != null && self.type.index() >= 2);
    private final Setting.Choice when = add(new Setting.Choice("When it shows", "Choose when the knife overlay replaces the first-person hand: empty hand, both hands empty, always, or whenever you hold any sword.", 0, "Empty hand", "Both hands empty", "Always", "Replace any sword"));
    private final Setting.Bool hideHand = add(new Setting.Bool("Hide the game's own hand", "Hides the game's arm and held item while the knife is in your hand, so the two do not overlap.", true));
        private final Setting.Choice trick = add(new Setting.Choice("Tsuki / Butterfly (Butterfly)", "Plays the Tsuki / Butterfly animation: the trick key or right click plays it, and the knife changes to Butterfly to match. The repeat count is shown in its display.", 0, TRICKS)).showIf(() -> type.index() == 0);
        private final Setting.Choice ktrick = add(new Setting.Choice("Tsuki / Karambit & others (Karambit & other knives)", "Plays the Tsuki animation on a karambit and other knives. The knife changes to a karambit-style version to match the animation. The repeat count is shown in its display.", 0, KTRICKS)).showIf(() -> type.index() >= 1);
        private final Setting.Bool rightClick = add(new Setting.Bool("Keyboard & mouse trick", "The keyboard makes this Tsuki / Butterfly. Pick the Tsuki key in Controls and choose the animation. The move that is already in there decides which animation happens.", true));
    private final Setting.Bool slash = add(new Setting.Bool("Slash when I attack", "The knife cuts across the screen when you attack.", true));
    private final Setting.Bool hold = add(new Setting.Bool("Always in hand", "The knife stays in your hand between tricks. Off: it only shows while it moves.", true));
    private final Setting.Bool auto = add(new Setting.Bool("Keep doing tricks", "Plays a trick by itself every few seconds.", false));
    private final Setting.Choice preset = add(new Setting.Choice("Butterfly colours", "The colours of the butterfly knife's handles and blade.", 0, KnifeArt.PRESETS)).showIf(() -> type.index() == 0);
    private final Setting.Choice kcolor = add(new Setting.Choice("Karambit colours", "The colours of the karambit. Voidbloom is the dark purple one with the glowing magenta blade.", 0, KnifeArt.KPRESETS)).showIf(() -> type.index() == 1);
    private final Setting.Choice kcurve = add(new Setting.Choice("Karambit blade", "How the karambit's blade curves: the claw of the video, a wide hook or a long talon.", 0, KnifeArt.KCURVES)).showIf(() -> type.index() == 1);
    private final Setting.Choice shape = add(new Setting.Choice("Blade shape", "The shape of the butterfly knife's blade.", 0, KnifeArt.SHAPES)).showIf(() -> type.index() == 0);
    private final Setting.Choice finish = add(new Setting.Choice("Blade finish", "Overrides the blade colours of the chosen butterfly knife.", 0, KnifeArt.FINISHES)).showIf(() -> type.index() == 0);
    private final Setting.Choice design = add(new Setting.Choice("Handle design", "Slotted has the long slot and slanted teeth of the knife in the video.", 0, KnifeArt.DESIGNS)).showIf(() -> type.index() == 0);
    private final Setting.Choice hand = add(new Setting.Choice("Hand", "My skin: the arm wears your own skin, like the game's own hand. The others are painted.", 0, ArmArt.LOOKS));
    private final Setting.Choice place = add(new Setting.Choice("Place", "Where on the screen. Bottom left is the left hand.", 0, "Bottom right", "Bottom left", "Centre"));
    private final Setting.Bool showFingers = add(new Setting.Bool("Show fingers", "Draws the blocky fingers and thumb on the handle. Off: only the arm and the knife.", false));
    private final Setting.Num size = add(new Setting.Num("Size", "How big the hand and knife are.", 1.0, 0.5, 2.5, 0.1, "x"));
    private final Setting.Num speed = add(new Setting.Num("Speed", "How fast the tricks play.", 1.0, 0.5, 2.0, 0.1, "x"));
    private final Setting.Num spins = add(new Setting.Num("Rollovers", "How many times the knife turns in a rollover.", 2, 1, 4, 1, ""));
    private final Setting.Bool sway = add(new Setting.Bool("Sway with the camera", "The hand lags behind when you turn, like the game's own.", true));
    private final Setting.Bool handLight = add(new Setting.Bool("Light on the hand", "The knife throws a little of its colour on the hand when it flashes and spins.", true));
    private final Setting.Bool safeDraw = add(new Setting.Bool("Safe draw", "Always uses the flat pixel knives, which draw on every setup. Turn it off to try the 3D models.", true));
    private final Setting.Info status = add(new Setting.Info("Knife", this::lines));
    private final Setting.Bool model3d = add(new Setting.Bool("3D knife models", "Draws the knives as real 3D models (a few hundred triangles each, flat shaded, cheap). Off: the classic pixel knives. Falls back to the pixel knives by itself if a model cannot be loaded.", false));
    private final Setting.Bool trail = add(new Setting.Bool("Light trail", "A fading trail behind the blade.", true));
    private final Setting.Bool blur = add(new Setting.Bool("Motion blur", "Ghost images of the knife while it moves fast.", true));
    private final Setting.Bool sparks = add(new Setting.Bool("Sparks and flash", "A flash and a few sparks when it locks open and when it cuts.", true));
    private final Setting.Num trailStrength = add(new Setting.Num("Trail strength", "Opacity of the blade trail. Set to 0% to keep trails enabled but invisible, or lower it for a cleaner look.", 100, 0, 150, 5, "%")).showIf(() -> trail.on());
    private final Setting.Num ghostStrength = add(new Setting.Num("Ghost opacity", "Opacity of the motion-blur ghost knives. Lower values make fast tricks less busy.", 100, 0, 150, 5, "%")).showIf(() -> blur.on());
    private final Setting.Num flashStrength = add(new Setting.Num("Flash intensity", "Strength of the impact bloom and sparks. Lower it for a subtler knife effect.", 100, 0, 150, 5, "%")).showIf(() -> sparks.on());
    private final Setting.Action gallery = add(new Setting.Action("Open the knife gallery", "A menu showing every knife and every finish, drawn the way the game draws them. Click one to use it.", this::openGallery));
    private final Setting.Action showNow = add(new Setting.Action("Show it now", "Draws the knife for four seconds whatever your hand is doing, so you can check the knife and its trick without emptying your hand.", () -> previewUntil = System.currentTimeMillis() + 4000L));
    private final Setting.Action[] play = new Setting.Action[TRICKS.length];
    private final Setting.Action[] kplay = new Setting.Action[KTRICKS.length];
    private int lastType;
    private final ArrayDeque<Integer> queue = new ArrayDeque<>();
    private final List<int[]> line = new ArrayList<>();
    private float[] lineStart = new float[0];
    private float lineTotal;
    private long start;
    private long enter;
    private boolean isOpen;
    private boolean wasDown;

    private long previewUntil;

    private boolean meshBroken;
    private boolean wasUse;
    private long autoAt;
    private final Random rnd = new Random();
    private int lastRandom = -1;
    private boolean want;
    private float vis;
    private long lastFrame;
    private float swayX;
    private float swayY;
    private float lastYaw = Float.NaN;
    private float lastPitch;
    private final Rig rig = new Rig();
    private final Rig ghost = new Rig();

    private final ArrayDeque<float[]> tipTrail = new ArrayDeque<>();

    private final List<Rig.T> ghostAt = new ArrayList<>();
    private final List<float[]> ghostAngle = new ArrayList<>();
    private final List<Long> ghostWhen = new ArrayList<>();
    public KnifeSwing() {
        super("knife", "Knives", "A butterfly knife, karambit or CS2 straight knife in your own hand, built in 3D and animated like a real flipper. Shows when your hand is empty by default, and right click or the trick key plays a trick.", Category.RENDER);
        self = this;
        for (int i = 0; i < TRICKS.length; i++) {
            final int id = i;
            play[i] = add(new Setting.Action("Play (butterfly): " + TRICKS[i], "Switches to the butterfly knife and plays this trick now.", () -> {
                type.set(0);
                perform(id);
            }));
        }
        for (int i = 0; i < KTRICKS.length; i++) {
            final int id = i;
            kplay[i] = add(new Setting.Action("Play (karambit): " + KTRICKS[i], "Switches to the karambit and plays this trick now.", () -> {
                type.set(1);
                perform(id);
            }));
        }
    }

    private Setting.Choice finishFor(int t) {
        return t == 0 ? preset : t == 1 ? kcolor : sfinish;
    }
    private void openGallery() {
        Minecraft mc = Minecraft.getInstance();
        int n = TYPES.length;
        int[] fins = new int[n];
        for (int i = 0; i < n; i++) fins[i] = finishFor(i).index();
        mc.setScreen(new KnifePickerScreen(mc.screen, type.index(), fins, (k, f) -> {
            type.set(k);
            finishFor(k).set(f);
        }));
    }
    private static final String[] INSPECT_STYLES = {
        "a slow turn with a tilt, so the flat of the blade and then the spine face you",
        "a flip end over end to show the front, then a sweep round to show the edge",
        "a short toss, a catch with a recoil and one turn to show it",
        "a fast turn about the handle, then the blade rocked from side to side",
        "a turn into a reverse grip, a small stab and a turn back",
        "a loose wrist roll that ends in a full turn"};

    public static String inspectText(int id) {
        String st = INSPECT_STYLES[id % 6];
        String out = st.substring(0, 1).toUpperCase() + st.substring(1) + ".";
        if (id >= 12) out += " Done with two turns instead of one.";
        if ((id / 6) % 2 == 1) out += " Turns the other way round.";
        return out;
    }

    public static void pressTrick() {
        if (self != null && self.enabled()) self.perform(self.curTrick());
    }

    public static void nextTrick() {
        if (self != null && self.enabled()) {
            if (self.karambit()) {
                int n = (self.ktrick.index() + 1) % KTRICKS.length;
                self.ktrick.set(n);
                Zeus.toast("Trick: " + KTRICKS[n], true);
            } else {
                int n = (self.trick.index() + 1) % TRICKS.length;
                self.trick.set(n);
                Zeus.toast("Trick: " + TRICKS[n], true);
            }
        }
    }

    private boolean karambit() {
        return type.index() >= 1;
    }

    private java.util.List<String> lines() {
        int idx = Math.max(0, Math.min(TYPES.length - 1, type.index()));
        String name = TYPES[idx];
        String mesh = karambit() ? KnifeArt.nameOf(key()) : "Butterfly knife";
        boolean loaded = KnifeMesh.has(mesh);
        String render = !model3d.on() ? "pixel knife (3D off)"
                : loaded ? (safeDraw.on() ? "3D model ready (safe draw on)" : "3D model in use") : "3D mesh missing, using pixels";
        String state = playing() ? "playing a trick" : (isOpen ? "open in hand" : "closed in hand");
        return java.util.List.of(name, render, state, "Tricks: " + (karambit() ? KTRICKS.length : TRICKS.length) + " loaded");
    }

    private boolean ring() {
        return type.index() == 1;
    }

    private int key() {
        return ring() ? kcurve.index() : type.index() >= 2 ? 3 + (type.index() - 2) : 0;
    }
    private float grip() {
        return KnifeArt.gripOf(key());
    }
    private int curTrick() {
        return karambit() ? ktrick.index() : trick.index();
    }

    public static boolean hidesHand() {
        if (self == null || !self.enabled() || !self.hideHand.on() || self.vis <= 0.02f) return false;
        return self.frames > 0 && System.nanoTime() - self.drewAt < 250_000_000L;
    }
    private void perform(int id) {
        int n = karambit() ? KTRICKS.length : TRICKS.length;
        if (id == n - 1) {
            int pick;
            do {
                pick = 1 + rnd.nextInt(n - 2);
            } while (pick == lastRandom);
            lastRandom = pick;
            id = pick;
            if (rnd.nextInt(5) == 0) id = 0;
        }
        if (playing()) {
            if (queue.size() < 3) queue.add(id);
            return;
        }
        launch(id);
    }

    private int studioOr(int m) {
        if (m < SB_DRAW) return m;
        String mesh = !model3d.on() ? null : karambit() ? KnifeArt.nameOf(key()) : "Butterfly knife";
        if (mesh != null && KnifeMesh.has(mesh) && KnifeMesh.hasClip(mesh, CLIP_NAME[m - SB_DRAW])) return m;
        return switch (m) {
            case SB_DRAW -> OPEN;
            case SB_ROLL -> ROLL;
            case SB_FAN -> FAN;
            case SB_ZEN -> TWIRL;
            case SB_AERIAL -> AERIAL;
            case SB_INSPECT -> SHOW;
            case SK_DRAW, SK_SPIN -> RSPIN;
            case SK_FLIP -> KFLIP;
            case SK_PIN -> RPIN;
            case SK_TOSS -> KAERIAL;
            default -> KSHOW;
        };
    }
    private boolean playing() {
        return !line.isEmpty() && secs() < lineTotal;
    }

    private void launch(int id) {
        line.clear();
        if (karambit()) {
            for (int m : KSCRIPT[Math.max(0, Math.min(KSCRIPT.length - 1, id))]) line.add(new int[] {studioOr(m)});
            begin();
            return;
        }
        boolean open = isOpen;
        for (int m : SCRIPT[id]) {
            m = studioOr(m);
            if (m == OPEN && open) continue;
            if (m == CLOSE && !open) continue;
            if ((m == LATCH || m == SB_DRAW) && open) {
                line.add(new int[] {CLOSE});
                open = false;
            }
            if (m != OPEN && m != CLOSE && !selfOpens(m) && !open) {
                line.add(new int[] {OPEN});
                open = true;
            }
            line.add(new int[] {m});
            if (m == OPEN || m == LATCH || m == SB_DRAW) open = true;
            if (m == CLOSE) open = false;
        }
        if (line.isEmpty()) line.add(new int[] {isOpen ? CLOSE : OPEN});
        begin();
    }
    private void launchSlash() {
        line.clear();
        queue.clear();
        line.add(new int[] {karambit() ? KSLASH : SLASHING});
        begin();
    }
    private void begin() {
        lineStart = new float[line.size()];
        float t = 0;
        for (int i = 0; i < line.size(); i++) {
            lineStart[i] = t;
            t += MOVE_TIME[line.get(i)[0]];
        }
        lineTotal = t;
        start = System.nanoTime();
        carry = lastShown;
        carryStart = null;
        carryAt = System.nanoTime();
    }
    @Override
    protected void onEnable() {
        enter = System.nanoTime();
        queue.clear();
        line.clear();
        isOpen = false;
        vis = 0f;
        frames = 0;
        drewAt = 0L;
        enabledAt = System.nanoTime();
        warned = false;
        lastFrame = 0;
        lastYaw = Float.NaN;
        lastType = type.index();
        meshBroken = false;
        launch(1);
    }
    @Override
    protected void onTick(Minecraft mc) {
        if (type.index() != lastType) {
            lastType = type.index();
            queue.clear();
            line.clear();
            isOpen = false;
            launch(1);
        }
        want = false;
        if (mc.player != null && mc.screen == null && !mc.player.isSpectator()) {
            boolean main = mc.player.getMainHandItem().isEmpty();
            boolean off = mc.player.getOffhandItem().isEmpty();
            want = switch (when.index()) {
                case 3 -> net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(mc.player.getMainHandItem().getItem()).getPath().endsWith("_sword");
                case 2 -> true;
                case 1 -> main && off;
                default -> main;
            };
        }
        if (System.currentTimeMillis() < previewUntil) want = true;
        boolean active = want && vis > 0.5f;
        boolean down = mc.screen == null && mc.options.keyAttack.isDown();
        if (down && !wasDown && active && slash.on() && !(playing() && secs() < 0.3f)) launchSlash();
        wasDown = down;
        boolean use = mc.screen == null && mc.options.keyUse.isDown();
        if (use && !wasUse && active && rightClick.on()) perform(curTrick());
        wasUse = use;
        if (!playing() && !line.isEmpty()) {
            int last = line.get(line.size() - 1)[0];
            isOpen = last != CLOSE;
            line.clear();
            if (!queue.isEmpty()) launch(queue.poll());
        }
        if (auto.on() && active && !playing() && System.currentTimeMillis() - autoAt > 2500) {
            autoAt = System.currentTimeMillis();
            perform((karambit() ? KTRICKS.length : TRICKS.length) - 1);
        }
    }

    private float secs() {
        if (start == 0) return 99f;
        return (System.nanoTime() - start) / 1e9f * speed.f() * BASE;
    }
    private static float c01(float x) {
        return Math.max(0f, Math.min(1f, x));
    }
    private static float ease(float x) {
        x = c01(x);
        return x * x * (3f - 2f * x);
    }
    private static float easeIn(float x) {
        x = c01(x);
        return x * x * x;
    }

    private static float easeSmoother(float x) {
        x = c01(x);
        return x * x * x * (x * (x * 6f - 15f) + 10f);
    }

    private static float softStart(float x) {
        x = c01(x);
        return 0.5f * ease(x) + 0.5f * x;
    }
    private static float easeOutBack(float x) {
        x = c01(x);
        float c = 1.9f;
        float y = x - 1f;
        return 1f + (c + 1f) * y * y * y + c * y * y;
    }
    private static float bump(float x) {
        return (float) Math.sin(c01(x) * Math.PI);
    }
    private static final float PI = (float) Math.PI;
    private static final float TAU = (float) (Math.PI * 2.0);
    private static final float GRIP = 8.6f;
    private static final float ARM_L = 38f;
    private static final float ARM_Z = 2.62f;
    private static final float ARM_X = 0.35f;

    private static final class Pose {
        float flickZ, flickX, flickY;
        float gx, gy, gz;
        float kx, ky, kz;
        float rx, ry, rz;
        float gripY = GRIP;
        float a, b, c;
        float release;
        float flash, rush;
        float shake;
        float armRoll = 0.38f;
        String clip;
        float clipT;
    }
    private float slideNow() {
        return vis;
    }
    private static float lerp(float a, float b, float k) {
        return a + (b - a) * k;
    }
    private static float wrapPi(float a) {
        a = (a + PI) % TAU;
        if (a < 0f) a += TAU;
        return a - PI;
    }
    private static float lerpAng(float a, float b, float k) {
        return a + wrapPi(b - a) * k;
    }

    private static float spin(float x, float turns, float flick) {
        x = c01(x);
        float sg = Math.signum(turns);
        float tot = turns * TAU;
        float wind = 0.11f;
        float settle = 0.15f;
        float back = 0.30f * sg;
        float over = 0.12f * sg;
        if (x < wind) {
            float u = x / wind;
            return -back * (float) Math.sin(u * PI / 2f);
        }
        if (x < 1f - settle) {
            float u = (x - wind) / (1f - wind - settle);
            float even = easeSmoother(u);
            float hard = 1f - (1f - u) * (1f - u) * (1f - u);
            float f = lerp(even, hard, flick * easeSmoother(u / 0.18f));
            return -back + (tot + over + back) * f;
        }
        float u = (x - (1f - settle)) / settle;
        float d = 1f - u;
        return tot + over * d * d * (float) Math.cos(u * PI * 1.15f);
    }

    private static float kick(float x, float at, float amp) {
        if (x < at) return 0f;
        float t = x - at;
        return amp * (float) (Math.exp(-9.0 * t) * Math.sin(t * 40.0));
    }

    private static float let(float u) {
        if (u <= 0f || u >= 1f) return 0f;
        return easeSmoother(u / 0.13f) * (1f - easeSmoother((u - 0.87f) / 0.13f));
    }

    private static float arc(float u) {
        u = c01(u);
        return 4f * u * (1f - u);
    }

    private static Pose carryOver(Pose cur, Pose from, Pose startOf, float w) {
        Pose p = mix(cur, cur, 0f);
        if (w <= 0f) return p;
        p.flickZ += w * (from.flickZ - startOf.flickZ);
        p.flickX += w * (from.flickX - startOf.flickX);
        p.flickY += w * (from.flickY - startOf.flickY);
        p.gx += w * (from.gx - startOf.gx);
        p.gy += w * (from.gy - startOf.gy);
        p.gz += w * (from.gz - startOf.gz);
        p.kx += w * (from.kx - startOf.kx);
        p.ky += w * (from.ky - startOf.ky);
        p.kz += w * (from.kz - startOf.kz);
        p.rx += w * wrapPi(from.rx - startOf.rx);
        p.ry += w * wrapPi(from.ry - startOf.ry);
        p.rz += w * wrapPi(from.rz - startOf.rz);
        p.gripY += w * (from.gripY - startOf.gripY);
        p.a += w * wrapPi(from.a - startOf.a);
        p.b += w * wrapPi(from.b - startOf.b);
        p.c += w * (from.c - startOf.c);
        p.release += w * (from.release - startOf.release);
        return p;
    }

    private static Pose mix(Pose a, Pose b, float k) {
        Pose p = new Pose();
        p.flickZ = lerp(a.flickZ, b.flickZ, k);
        p.flickX = lerp(a.flickX, b.flickX, k);
        p.flickY = lerp(a.flickY, b.flickY, k);
        p.gx = lerp(a.gx, b.gx, k);
        p.gy = lerp(a.gy, b.gy, k);
        p.gz = lerp(a.gz, b.gz, k);
        p.kx = lerp(a.kx, b.kx, k);
        p.ky = lerp(a.ky, b.ky, k);
        p.kz = lerp(a.kz, b.kz, k);
        p.rx = lerpAng(a.rx, b.rx, k);
        p.ry = lerpAng(a.ry, b.ry, k);
        p.rz = lerpAng(a.rz, b.rz, k);
        p.gripY = lerp(a.gripY, b.gripY, k);
        p.a = lerpAng(a.a, b.a, k);
        p.b = lerpAng(a.b, b.b, k);
        p.c = lerp(a.c, b.c, k);
        p.release = lerp(a.release, b.release, k);
        p.flash = lerp(a.flash, b.flash, k);
        p.rush = lerp(a.rush, b.rush, k);
        p.shake = lerp(a.shake, b.shake, k);
        p.armRoll = lerp(a.armRoll, b.armRoll, k);
        p.clip = k < 0.5f || b.clip == null ? a.clip : b.clip;
        p.clipT = k < 0.5f || b.clip == null ? a.clipT : b.clipT;
        return p;
    }

    private Pose idle(boolean open) {
        Pose p = new Pose();
        float idleT = (System.nanoTime() % 20_000_000_000L) / 1e9f;
        p.c = open ? PI : 0f;
        if (karambit()) {
            p.gripY = grip();
            p.c = 0f;
            p.rz += 0.05f * (float) Math.sin(idleT * 1.4);
        }
        p.flickZ += (float) Math.sin(idleT * 1.7) * 0.012f;
        p.flickX += (float) Math.sin(idleT * 1.1) * 0.010f;
        p.gy += (float) Math.sin(idleT * 1.3) * 0.28f;
        p.gx += (float) Math.cos(idleT * 0.9) * 0.20f;
        return p;
    }

    private static boolean selfOpens(int move) {
        return move == OPEN || move == SLASHING || move == LATCH || move == SB_DRAW;
    }

    private Pose shown(float s, boolean running) {
        Pose p = pose(s, running);
        if (carry != null) {
            float age = (System.nanoTime() - carryAt) / 1e9f;
            if (age < CARRY) {
                if (carryStart == null) carryStart = p;
                p = carryOver(p, carry, carryStart, 1f - easeSmoother(age / CARRY));
            }
            else carry = null;
        }
        lastShown = p;
        return p;
    }
    private static final float CARRY = 0.14f;
    private static final float BLEND = 0.10f;
    private Pose carry;
    private Pose carryStart;
    private long carryAt;
    private Pose lastShown;

    private Pose pose(float s, boolean running) {
        if (!running || line.isEmpty()) return idle(isOpen);
        int idx = 0;
        for (int i = 0; i < lineStart.length; i++) if (s >= lineStart[i]) idx = i;
        int move = line.get(idx)[0];
        float t = s - lineStart[idx];
        float x = c01(t / MOVE_TIME[move]);
        Pose p = movePose(move, x, t, startsOpen(idx));
        if (t < BLEND) {
            Pose before;
            if (idx > 0) {
                int pm = line.get(idx - 1)[0];
                before = movePose(pm, 1f, MOVE_TIME[pm], startsOpen(idx - 1));
            } else {
                before = idle(selfOpens(move) ? false : startsOpen(0));
            }
            Pose first = movePose(move, 0f, 0f, startsOpen(idx));
            p = carryOver(p, before, first, 1f - easeSmoother(t / BLEND));
        }
        if (idx == line.size() - 1) {
            float left = MOVE_TIME[move] - t;
            if (left < BLEND) {
                Pose last = movePose(move, 1f, MOVE_TIME[move], startsOpen(idx));
                p = carryOver(p, idle(move != CLOSE), last, easeSmoother(1f - left / BLEND));
            }
        }
        return p;
    }

    private boolean startsOpen(int idx) {
        int move = line.get(idx)[0];
        if (selfOpens(move)) return false;
        return idx == 0 ? isOpen : line.get(idx - 1)[0] != CLOSE;
    }

    private Pose movePose(int move, float x, float t, boolean startsOpen) {
        Pose p = new Pose();
        boolean kb = karambit();
        p.c = kb ? 0f : startsOpen ? PI : 0f;
        if (kb) p.gripY = grip();
        if (move >= SB_DRAW) {
            int ci = move - SB_DRAW;
            p.clip = CLIP_NAME[ci];
            p.clipT = x * CLIP_D[ci];
            p.release = Math.min(ease(x / 0.06f), 1f - ease((x - 0.94f) / 0.06f));
            if (!kb) p.c = PI;
            return p;
        }
        switch (move) {
            case OPEN -> {
                float o = x >= 0.62f ? 1f : easeOutBack(softStart(x / 0.62f));
                setOpen(p, Math.min(1.05f, o));
                p.flickZ = 0.10f * bump(x / 0.30f) - 0.16f * bump((x - 0.26f) / 0.46f) + kick(x, 0.62f, 0.05f);
                p.flickX = -0.10f * bump((x - 0.26f) / 0.46f);
                p.gy = -1.6f * bump((x - 0.28f) / 0.44f) + kick(x, 0.62f, 0.5f);
                p.release = 0.2f * bump((x - 0.3f) / 0.4f);
                p.rush = x > 0.18f && x < 0.62f ? 1f : 0f;
                if (x > 0.58f && x < 0.92f) p.flash = 1f - (x - 0.58f) / 0.34f;
            }
            case FAN, SFAN -> {
                float kf = move == SFAN ? 2.5f : 1f;
                float ph = x * 2f * kf;
                float f = ph % 1f;
                float v = f < 0.42f ? 0.5f + 0.5f * (float) Math.cos(PI * f / 0.42f) : 0.5f - 0.5f * (float) Math.cos(PI * (f - 0.42f) / 0.58f);
                setOpen(p, 0.05f + 0.95f * v);
                p.flickZ = 0.10f * kf * (float) Math.sin(x * PI * 4.0 * kf);
                p.flickX = 0.05f * (float) Math.sin(x * PI * 4.0 * kf + 1.2);
                p.gx = 1.4f * kf * (float) Math.sin(x * PI * 4.0 * kf);
                if (move == SFAN) p.shake = 0.35f;
                p.gy = -0.8f * bump(x);
                p.release = 0.12f;
                p.rush = 0.85f;
                if (x > 0.44f && x < 0.58f || x > 0.95f) p.flash = 0.65f;
            }
            case SHOW, KSHOW -> {
                p.ry = 0.95f * (float) Math.sin(x * TAU);
                p.rz = 0.12f * (float) Math.sin(x * TAU + 1.0);
                p.rx = -0.10f * bump(x);
                p.gy = -1.8f * bump(x);
                p.flickX = -0.05f * bump(x);
                p.flash = 0.35f * bump((x - 0.45f) / 0.2f);
                if (kb) {
                    p.rz += TAU * easeSmoother(c01((x - 0.30f) / 0.50f));
                    p.flash = Math.max(p.flash, 0.55f * bump((x - 0.62f) / 0.2f));
                }
            }
            case WHIRL, HWHIRL -> {
                float in = ease(x / 0.14f);
                float out = 1f - ease((x - 0.86f) / 0.14f);
                p.gripY = GRIP - (GRIP - 1.4f) * Math.min(in, out);
                float sp = spin(c01((x - 0.08f) / 0.80f), move == HWHIRL ? 5f : 2f, 0.45f);
                if (move == HWHIRL) p.shake = 0.4f;
                p.a = -sp;
                p.b = sp;
                p.release = 0.25f * Math.min(in, out);
                p.rush = x > 0.12f && x < 0.9f ? 1f : 0f;
                p.flickZ = 0.04f * (float) Math.sin(x * TAU * 2) + 0.04f * (float) Math.sin(sp);
                p.gx = 0.7f * (float) Math.sin(sp * 0.5f) * Math.min(in, out);
                p.gy = 0.5f * (float) Math.cos(sp * 0.5f) * Math.min(in, out) - 0.5f * bump(x);
                if (x > 0.93f) p.flash = 0.5f;
            }
            case ROLL, ROLL_BACK -> {
                float dir = move == ROLL ? 1f : -1f;
                p.ry = dir * spin(x, spins.i(), 0.30f);
                p.release = 0.35f * bump(x);
                p.gy = -1.2f * bump(x) + kick(x, 0.86f, 0.5f);
                p.rz = 0.08f * dir * bump(x) + 0.05f * (float) Math.sin(p.ry * 2f) * bump(x);
                p.rush = x > 0.04f && x < 0.96f ? 1f : 0f;
                if (x > 0.9f) p.flash = 0.5f;
            }
            case FRENZY -> {
                p.rx = spin(x, 3f, 0.55f);
                p.ry = spin(c01((x - 0.12f) / 0.78f), 1f, 0.2f);
                p.rz = -0.4f * (float) Math.sin(x * TAU);
                p.gy = -3.2f * bump(x) + kick(x, 0.9f, 0.7f);
                p.gx = 2f * (float) Math.sin(x * TAU);
                p.flickX = -0.14f * bump(x);
                p.release = 0.8f * bump(x);
                p.rush = x > 0.03f && x < 0.97f ? 1f : 0f;
                p.shake = 0.3f * bump(x);
                if (x > 0.92f) p.flash = 0.8f;
            }
            case TWIRL -> {
                p.rz = -spin(x, 2f, 0.4f);
                p.release = 0.75f * bump(x);
                p.gx = 1.0f * bump(x) * (float) Math.cos(p.rz);
                p.gy = -0.7f * bump(x) * (float) Math.sin(p.rz);
                p.flickZ = 0.05f * bump(x);
                p.rush = x > 0.04f && x < 0.96f ? 1f : 0f;
                if (x > 0.93f) p.flash = 0.4f;
            }
            case GUN -> {
                p.rx = spin(x, 2f, 0.5f);
                p.rz = 0.30f * bump(x);
                p.gy = -2.4f * bump(x) + 0.7f * bump(x / 0.16f) + kick(x, 0.88f, 0.8f);
                p.flickX = -0.10f * bump(x);
                p.release = 0.65f * bump(x);
                p.rush = x > 0.04f && x < 0.96f ? 1f : 0f;
                if (x > 0.93f) p.flash = 0.6f;
            }
            case AERIAL, KAERIAL -> {
                float u = c01((x - 0.12f) / 0.72f);
                float up = arc(u);
                float wind = bump(x / 0.20f);
                p.ky = (kb ? -26f : -34f) * up;
                p.kx = -5f * up;
                p.kz = -2f * up;
                p.rz = -(kb ? 3f : 2f) * TAU * (0.12f * easeSmoother(u) + 0.88f * u);
                p.rx = 0.9f * (float) Math.sin(u * TAU) * up;
                p.gy = 2.2f * wind - 2.0f * bump((x - 0.80f) / 0.18f) + kick(x, 0.86f, 0.9f);
                p.flickZ = -0.12f * wind + 0.10f * bump((x - 0.78f) / 0.2f);
                p.flickX = 0.08f * wind;
                p.release = Math.max(let(u), 0.2f * wind);
                p.rush = up > 0.1f ? 1f : 0f;
                if (x > 0.86f && x < 1f) p.flash = 1f - (x - 0.86f) / 0.14f;
                if (x > 0.86f) p.shake = 0.35f * bump((x - 0.86f) / 0.14f);
            }
            case CLOSE -> {
                setOpen(p, 1f - (0.5f * ease(x) + 0.5f * easeIn(x)));
                p.flickZ = 0.12f * bump(x) + kick(x, 0.9f, 0.05f);
                p.flickX = 0.06f * bump(x);
                p.rush = x > 0f && x < 0.9f ? 1f : 0f;
                if (x > 0.9f) p.flash = 0.4f * (1f - (x - 0.9f) / 0.1f);
            }
            case RSPIN, RREV -> {
                float dir = move == RSPIN ? -1f : 1f;
                float ang = dir * spin(x, 2f, 0.45f);
                p.rz = ang;
                p.ry = 0.16f * (float) Math.sin(ang * 2f) * bump(x);
                spinHand(p, x);
                p.gx = 0.8f * (float) Math.cos(ang) * bump(x);
                p.gy = -0.6f * bump(x) - 0.7f * (float) Math.sin(ang) * bump(x) + kick(x, 0.88f, 0.6f);
                p.flickZ = 0.05f * (float) Math.sin(x * PI * 2.0);
                if (x > 0.9f) p.flash = 0.55f;
            }
            case RFLURRY -> {
                float ph = x * 3f;
                int seg = (int) Math.min(2f, ph);
                float f = ph - seg;
                float turns = seg == 0 ? -1f : seg == 1 ? 1f : -2f;
                p.rz = (seg == 0 ? 0f : seg == 1 ? -TAU : 0f) + spin(f, turns, seg == 2 ? 0.2f : 0.7f);
                spinHand(p, x);
                p.gx = 0.9f * (float) Math.sin(x * TAU * 1.5);
                p.gy = -0.6f * bump(f) + kick(f, 0.0f, 0.5f);
                p.flickZ = 0.06f * (float) Math.sin(x * TAU * 3.0);
                p.shake = seg == 2 ? 0.25f * bump(f) : 0f;
                if (f > 0.9f) p.flash = 0.5f;
            }
            case RPIN -> {
                float ang = -spin(x, 3f, 0.2f);
                p.rz = ang;
                p.ry = 0.2f * (float) Math.sin(ang);
                spinHand(p, x);
                p.gx = 2.6f * (float) Math.sin(x * TAU) * bump(x);
                p.gy = -2.4f * (1f - (float) Math.cos(x * TAU)) * 0.5f + kick(x, 0.9f, 0.5f);
                p.flickZ = 0.10f * (float) Math.sin(x * TAU * 2.0);
                if (x > 0.92f) p.flash = 0.6f;
            }
            case KFLIP -> {
                p.rx = spin(x, 2f, 0.5f);
                p.rz = 0.30f * bump(x);
                spinHand(p, x);
                p.gy = -1.8f * bump(x) + 0.6f * bump(x / 0.16f) + kick(x, 0.88f, 0.7f);
                p.flickX = -0.08f * bump(x);
                if (x > 0.93f) p.flash = 0.6f;
            }
            case KTRIPLE -> {
                float a = x < 0.5f ? -spin(x / 0.5f, 2f, 0.7f) : -2f * TAU - spin((x - 0.5f) / 0.5f, 1f, 0.15f);
                p.rz = a;
                p.ry = 0.14f * (float) Math.sin(a * 2f);
                spinHand(p, x);
                p.gy = -1.2f * bump(x) + kick(x, 0.5f, 0.5f);
                p.flickZ = -0.12f * bump(x / 0.5f) + 0.1f * bump((x - 0.5f) / 0.5f);
                p.shake = 0.35f * bump((x - 0.7f) / 0.3f);
                if (x > 0.9f) p.flash = 0.8f;
            }
            case KCATCH -> {
                float u = c01((x - 0.08f) / 0.76f);
                float up = arc(u);
                p.ky = -13f * up;
                p.kx = -2f * up;
                p.rz = -TAU * (0.15f * easeSmoother(u) + 0.85f * u);
                p.rx = 0.7f * (float) Math.sin(u * TAU) * up;
                p.release = Math.max(let(u), 0.3f);
                p.gy = 1.4f * bump(x / 0.2f) - 1.6f * bump((x - 0.8f) / 0.2f) + kick(x, 0.84f, 0.8f);
                p.rush = u > 0.05f && u < 0.95f ? 1f : 0f;
                p.shake = 0.5f * bump((x - 0.84f) / 0.16f);
                if (x > 0.84f) p.flash = 0.9f * (1f - (x - 0.84f) / 0.16f);
            }
            case KEIGHT -> {
                float th = x * TAU * 1.5f;
                float env = bump(x);
                p.rz = 1.2f * TAU * (float) Math.sin(x * TAU);
                spinHand(p, x);
                p.gx = 5f * (float) Math.sin(th * 2f) * env;
                p.gy = -3.5f * (float) Math.sin(th) * env;
                p.ry = 0.2f * (float) Math.cos(th) * env;
                p.flickZ = 0.12f * (float) Math.sin(th);
                p.shake = 0.2f;
                if (x > 0.92f) p.flash = 0.5f;
            }
            case KFLIPY -> {
                p.ry = spin(x, 2f, 0.35f);
                p.rz = 0.35f * (float) Math.sin(x * TAU);
                p.release = 0.7f * bump(x);
                p.rush = x > 0.04f && x < 0.96f ? 1f : 0f;
                p.gy = -2f * bump(x) + kick(x, 0.88f, 0.6f);
                p.flickX = -0.1f * bump(x);
                if (x > 0.92f) p.flash = 0.7f;
            }
            case KSLASH3 -> {
                float[] ax = {-17f, 11f, -15f};
                float[] ay = {7f, -8f, -2f};
                float[] az = {-1.5f, 1.7f, -0.3f};
                float[] aq = {0f, 0f, 9f};
                int sg = (int) Math.min(2f, x * 3f);
                float f = x * 3f - sg;
                float a = f < 0.3f ? -0.35f * ease(f / 0.3f) : f < 0.55f ? -0.35f + 1.35f * (0.55f * ease((f - 0.3f) / 0.25f) + 0.45f * easeIn((f - 0.3f) / 0.25f)) : 1f - ease((f - 0.55f) / 0.45f);
                p.gx = ax[sg] * a;
                p.gy = ay[sg] * a;
                p.gz = aq[sg] * a;
                p.rz = az[sg] * a;
                p.flickZ = 0.22f * a * (sg == 1 ? -1f : 1f);
                p.release = 0.5f * c01(a);
                p.rush = f > 0.28f && f < 0.7f ? 1f : 0f;
                p.shake = f > 0.5f && f < 0.7f ? 0.7f : 0f;
                if (f > 0.5f && f < 0.66f) p.flash = 0.9f;
            }
            case KSNAP -> {
                float a = -spin(x * 1.0f, 3f, 0.9f);
                p.rz = a;
                p.ry = 0.14f * (float) Math.sin(a * 2f) * bump(x);
                spinHand(p, x);
                p.gx = -1.2f * bump(x);
                p.gy = -1.0f * bump(x) + kick(x, 0.84f, 1.0f);
                p.shake = 0.7f * bump((x - 0.78f) / 0.22f);
                p.flickZ = 0.1f * bump(x) - 0.12f * bump((x - 0.8f) / 0.2f);
                if (x > 0.84f && x < 0.99f) p.flash = 1f - (x - 0.84f) / 0.15f;
            }
            case KHYPER -> {
                float a = -spin(x, 6f, 0.15f);
                p.rz = a;
                p.ry = 0.12f * (float) Math.sin(a * 3f) * bump(x);
                spinHand(p, x);
                p.gy = -1.6f * bump(x) + 0.5f * (float) Math.sin(x * TAU * 6.0) * bump(x);
                p.gx = 1.2f * (float) Math.sin(x * TAU * 5.0) * bump(x);
                p.shake = 0.5f * bump(x);
                p.flickZ = 0.1f * (float) Math.sin(x * TAU * 4.0);
                if (x > 0.9f) p.flash = 1f;
            }
            case KJUGGLE -> {
                float u1 = c01((x - 0.04f) / 0.38f);
                float u2 = c01((x - 0.46f) / 0.5f);
                float up1 = arc(u1);
                float up2 = arc(u2);
                p.ky = -11f * up1 - 21f * up2;
                p.kx = -2f * up1 - 4f * up2;
                p.rz = -TAU * (0.15f * easeSmoother(u1) + 0.85f * u1) - 2f * TAU * (0.15f * easeSmoother(u2) + 0.85f * u2);
                p.rx = 0.5f * (float) Math.sin(x * TAU * 3.0) * (up1 + up2);
                p.release = Math.max(Math.max(let(u1), let(u2)), 0.2f);
                p.gy = 1.2f * bump(x / 0.1f) - 1.6f * bump((x - 0.43f) / 0.1f) - 1.8f * bump((x - 0.92f) / 0.08f) + kick(x, 0.43f, 0.5f) + kick(x, 0.94f, 0.7f);
                p.rush = (up1 + up2) > 0.1f ? 1f : 0f;
                if ((x > 0.4f && x < 0.48f) || x > 0.94f) p.flash = 0.8f;
                if (x > 0.94f) p.shake = 0.6f;
            }
            case KORBIT -> {
                float env = ease(x / 0.15f) * (1f - ease((x - 0.85f) / 0.15f));
                float ang = -spin(x, 3f, 0.25f);
                p.rz = ang;
                p.ry = 0.30f * (float) Math.sin(ang);
                p.rx = 0.22f * (float) Math.cos(ang);
                spinHand(p, x);
                p.gx = 4.2f * (float) Math.cos(ang - 0.6f) * env;
                p.gy = -3.0f * (float) Math.sin(ang - 0.6f) * env - 1.0f * env;
                p.flickZ = 0.10f * (float) Math.sin(ang);
                p.shake = 0.15f * env;
                if (x > 0.92f) p.flash = 0.6f;
            }
            case KCROSS -> {
                float ang = -spin(x, 2f, 0.4f);
                p.rz = ang;
                p.rx = 0.95f * (float) Math.sin(ang * 0.5f) * bump(x);
                p.ry = 0.55f * (float) Math.cos(ang) * bump(x);
                spinHand(p, x);
                p.gy = -1.4f * bump(x) + kick(x, 0.88f, 0.6f);
                p.gx = 1.3f * (float) Math.sin(ang) * bump(x);
                p.flickZ = 0.08f * (float) Math.sin(ang * 0.5f);
                if (x > 0.92f) p.flash = 0.6f;
            }
            case KDOUBLE -> {
                float u1 = c01((x - 0.05f) / 0.36f);
                float u2 = c01((x - 0.50f) / 0.44f);
                float up1 = arc(u1);
                float up2 = arc(u2);
                p.ky = -10f * up1 - 25f * up2;
                p.kx = -2f * up1 - 4f * up2;
                p.rz = -TAU * (0.2f * easeSmoother(u1) + 0.8f * u1) - 3f * TAU * (0.1f * easeSmoother(u2) + 0.9f * u2);
                p.rx = TAU * easeSmoother(u1) + 0.6f * (float) Math.sin(u2 * TAU) * up2;
                p.release = Math.max(Math.max(let(u1), let(u2)), 0.25f);
                p.gy = 1.3f * bump(x / 0.1f) - 1.5f * bump((x - 0.42f) / 0.1f) + 1.0f * bump((x - 0.5f) / 0.08f) - 1.8f * bump((x - 0.94f) / 0.06f)
                        + kick(x, 0.42f, 0.5f) + kick(x, 0.95f, 0.8f);
                p.rush = (up1 + up2) > 0.1f ? 1f : 0f;
                if ((x > 0.41f && x < 0.49f) || x > 0.94f) p.flash = 0.8f;
                if (x > 0.95f) p.shake = 0.6f;
            }
            case KSTORM -> {
                float a = -spin(x, 5f, 0.6f);
                p.rz = a;
                p.rx = 0.5f * (float) Math.sin(x * TAU * 2.0) * bump(x);
                p.ry = 0.25f * (float) Math.sin(a * 2f) * bump(x);
                spinHand(p, x);
                p.gx = 1.8f * (float) Math.sin(x * TAU * 7.0) * bump(x);
                p.gy = -2.0f * bump(x) + 0.8f * (float) Math.sin(x * TAU * 9.0) * bump(x);
                p.shake = 0.65f * bump(x);
                p.flickZ = 0.14f * (float) Math.sin(x * TAU * 5.0);
                if ((x > 0.45f && x < 0.52f) || x > 0.92f) p.flash = 1f;
            }
            case INSPECT -> {
                int id = type.index();
                int style = id % 6;
                float dir = (id / 6) % 2 == 0 ? 1f : -1f;
                float turns = id >= 12 ? 2f : 1f;
                float env = bump(x);
                p.gy = -1.8f * env;
                p.flickX = -0.05f * env;
                p.release = 0.3f * env;
                p.rush = x > 0.06f && x < 0.94f ? 1f : 0f;
                if (style == 0) {
                    p.ry = dir * spin(c01((x - 0.10f) / 0.80f), turns, 0.2f);
                    p.rx = -0.35f * env;
                    p.rz = 0.15f * (float) Math.sin(x * TAU) * env;
                } else if (style == 1) {
                    p.rx = dir * spin(c01((x - 0.08f) / 0.42f), 1f, 0.5f);
                    p.rz = dir * spin(c01((x - 0.56f) / 0.38f), turns, 0.3f);
                } else if (style == 2) {
                    float u = c01((x - 0.10f) / 0.55f);
                    float up = arc(u);
                    p.ky = -14f * up;
                    p.rz = -dir * TAU * turns * easeSmoother(u);
                    p.release = Math.max(let(u), 0.2f * env);
                    p.gy += kick(x, 0.66f, 0.6f);
                    p.ry = dir * spin(c01((x - 0.74f) / 0.22f), 1f, 0.2f);
                } else if (style == 3) {
                    p.rz = -dir * spin(c01((x - 0.10f) / 0.50f), turns, 0.4f);
                    p.ry = x > 0.62f ? dir * 0.9f * (float) Math.sin((x - 0.62f) / 0.34f * TAU) : 0f;
                } else if (style == 4) {
                    p.rz = dir * PI * (easeSmoother(c01((x - 0.10f) / 0.25f)) - easeSmoother(c01((x - 0.60f) / 0.25f)));
                    p.rx = 0.5f * dir * (float) Math.sin(c01((x - 0.35f) / 0.30f) * PI);
                    p.gy += -2.0f * bump((x - 0.42f) / 0.16f);
                } else {
                    p.rz = dir * 0.6f * (float) Math.sin(x * TAU * 2f) * env;
                    p.ry = dir * spin(c01((x - 0.40f) / 0.55f), turns, 0.1f);
                }
                p.flash = Math.max(0.5f * bump((x - 0.46f) / 0.12f), x > 0.92f ? 0.6f : 0f);
            }
            case HELI -> {
                float ang = spin(x, 3f, 0.2f);
                float env = bump(x);
                p.ry = ang;
                p.rz = 0.35f * env;
                p.rx = 0.18f * (float) Math.sin(ang) * env;
                float hs = spin(x, 2f, 0.2f);
                p.a = -hs;
                p.b = hs;
                p.release = 0.6f * env;
                p.gx = 1.8f * (float) Math.cos(ang) * env;
                p.gy = -1.4f * env + 0.8f * (float) Math.sin(ang) * env + kick(x, 0.88f, 0.6f);
                p.rush = x > 0.05f && x < 0.95f ? 1f : 0f;
                p.shake = 0.25f * env;
                if (x > 0.9f) p.flash = 0.7f;
            }
            case BDOUBLE -> {
                float u1 = c01((x - 0.05f) / 0.36f);
                float u2 = c01((x - 0.50f) / 0.44f);
                float up1 = arc(u1);
                float up2 = arc(u2);
                p.ky = -16f * up1 - 32f * up2;
                p.kx = -3f * up1 - 5f * up2;
                p.rz = -TAU * (0.2f * easeSmoother(u1) + 0.8f * u1) - 2f * TAU * (0.1f * easeSmoother(u2) + 0.9f * u2);
                p.rx = TAU * easeSmoother(u1) + 0.7f * (float) Math.sin(u2 * TAU) * up2;
                p.a = -TAU * 2f * easeSmoother(u2);
                p.b = TAU * 2f * easeSmoother(u2);
                p.release = Math.max(Math.max(let(u1), let(u2)), 0.25f);
                p.gy = 1.3f * bump(x / 0.1f) - 1.5f * bump((x - 0.42f) / 0.1f) + 1.0f * bump((x - 0.5f) / 0.08f) - 1.8f * bump((x - 0.94f) / 0.06f)
                        + kick(x, 0.42f, 0.5f) + kick(x, 0.95f, 0.8f);
                p.rush = (up1 + up2) > 0.1f ? 1f : 0f;
                if ((x > 0.41f && x < 0.49f) || x > 0.94f) p.flash = 0.8f;
                if (x > 0.95f) p.shake = 0.6f;
            }
            case LATCH -> {
                float o = x >= 0.45f ? 1f : easeOutBack(softStart(x / 0.45f));
                setOpen(p, Math.min(1.05f, o));
                p.rz = -spin(x, 1f, 0.7f);
                p.flickZ = 0.12f * bump(x / 0.35f) + kick(x, 0.45f, 0.06f);
                p.gy = -1.8f * bump(x / 0.6f) + kick(x, 0.5f, 0.7f);
                p.release = 0.6f * bump(x);
                p.rush = x > 0.06f && x < 0.9f ? 1f : 0f;
                if (x > 0.42f && x < 0.8f) p.flash = 1f - (x - 0.42f) / 0.38f;
            }
            case CASCADE -> {
                if (x < 0.30f) return movePose(FAN, x / 0.30f, t, true);
                if (x < 0.62f) return movePose(WHIRL, (x - 0.30f) / 0.32f, t, true);
                return movePose(ROLL, (x - 0.62f) / 0.38f, t, true);
            }
            case KSLASH -> {
                slashPose(p, t);
                p.b = 0f;
                p.c = 0f;
                p.rz *= 2.2f;
            }
            default -> slashPose(p, t);
        }
        return p;
    }

    private static void spinHand(Pose p, float x) {
        p.release = Math.min(ease(x / 0.10f), 1f - ease((x - 0.90f) / 0.10f));
        p.rush = x > 0.03f && x < 0.97f ? 1f : 0f;
    }
    private static void setOpen(Pose p, float o) {
        p.c = PI * o;
        p.b = TAU * o;
    }
    private void slashPose(Pose p, float s) {
        float flick = easeOutBack(softStart(s / 0.17f));
        setOpen(p, Math.min(1.04f, flick));
        if (s > 0.17f) setOpen(p, 1f);
        float cock = ease((s - 0.04f) / 0.14f);
        float cut = 0.6f * ease((s - 0.17f) / 0.15f) + 0.4f * easeIn((s - 0.17f) / 0.15f);
        float back = ease((s - 0.34f) / 0.34f);
        float sweep = cock * (1f - cut);
        float swing = cut * (1f - back);
        p.flickZ = 0.32f * sweep - 0.55f * swing;
        p.flickY = 0.15f * swing;
        p.gx = 3f * sweep - 18f * swing;
        p.gy = 4f * sweep - 8f * swing;
        p.rz = -0.5f * swing;
        p.release = 0.2f * swing;
        p.flash = s > 0.27f && s < 0.42f ? 1f - (s - 0.27f) / 0.15f : 0f;
        p.rush = (cut > 0.05f && cut < 0.99f && back < 0.2f ? 1f : 0f) + (s < 0.17f ? 0.6f : 0f);
    }
    private static final class Frame {
        Rig.T knife;
        Rig.T arm;
        Rig.T fist;
    }
    private Frame frame(Pose p) {
        boolean kb = karambit();
        Rig.T r0 = ring() ? Rig.T.rz(0.40f).rotY(-0.45f).rotX(0.10f) : kb ? Rig.T.rz(PI - 0.50f).rotY(-0.45f).rotX(0.10f) : Rig.T.rz(-0.20f).rotY(-0.55f).rotX(0.12f);
        Rig.T base = new Rig.T(r0);
        float[] g = base.point(0f, p.gripY, 0f);
        float[] pv = ring() ? new float[] {0f, 0f, 0f} : g;
        Rig.T rt = Rig.T.rz(p.rz).then(Rig.T.rx(p.rx)).then(Rig.T.ry(p.ry));
        Rig.T k = base.worldRot(rt, pv[0], pv[1], pv[2]);
        k.x += g[0] + p.kx + p.gx;
        k.y += g[1] + p.ky + p.gy;
        k.z += g[2] + p.kz + p.gz;
        Rig.T arm = Rig.T.rz(ARM_Z).rotX(ARM_X).rotY(p.armRoll);
        arm.x = g[0] + p.gx;
        arm.y = g[1] + p.gy;
        arm.z = g[2] + p.gz;
        Rig.T fist = new Rig.T(r0);
        fist.x = arm.x;
        fist.y = arm.y;
        fist.z = arm.z;
        float[] sh = arm.point(0f, -ARM_L, 0f);
        Rig.T rf = Rig.T.rz(p.flickZ).then(Rig.T.rx(p.flickX)).then(Rig.T.ry(p.flickY));
        Frame f = new Frame();
        f.knife = k.worldRot(rf, sh[0], sh[1], sh[2]);
        f.arm = arm.worldRot(rf, sh[0], sh[1], sh[2]);
        f.fist = fist.worldRot(rf, sh[0], sh[1], sh[2]);
        return f;
    }

    @Override
    protected void onHud(GuiGraphics g, Minecraft mc, float partial, int w, int h) {
        if (mc.screen != null || !enabled() || mc.player == null) return;
        long now = System.nanoTime();
        float dt = lastFrame == 0L ? 0.016f : Math.min(0.08f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        vis += ((want ? 1f : 0f) - vis) * Math.min(1f, dt * 9f);
        if (vis < 0.02f) {
            tipTrail.clear();
            ghostAt.clear();
            ghostAngle.clear();
            ghostWhen.clear();
            return;
        }
        float sc = Math.max(0.5f, (float) size.f());
        float unit = Math.max(0.6f, h / 540f * 4.6f * sc) * (0.62f + 0.38f * vis);
        if (!Float.isFinite(sc) || !Float.isFinite(unit) || unit <= 0f || w <= 0 || h <= 0) return;
        float yaw = mc.player.getYRot(), pitch = mc.player.getXRot();
        if (!Float.isNaN(lastYaw)) {
            float dy = wrap(yaw - lastYaw);
            swayX += dy * 0.55f;
            swayY += (pitch - lastPitch) * 0.42f;
        }
        lastYaw = yaw;
        lastPitch = pitch;
        swayX *= 0.86f;
        swayY *= 0.86f;
        if (!sway.on()) {
            swayX = 0f;
            swayY = 0f;
        }
        float lim = 7f * unit;
        swayX = Math.max(-lim, Math.min(lim, swayX));
        swayY = Math.max(-lim, Math.min(lim, swayY));

        boolean left = place.index() == 1;
        float x0 = place.index() == 2 ? w / 2f : left ? 9f * unit : w - 9f * unit;
        float y0 = h + 4.6f * unit;
        float u = left ? -unit : unit;

        Pose p = shown(secs(), playing());
        Frame f = frame(p);
        KnifeArt.Look look = lookFor();
        look.fine = !Perf.gameFast();
        look.mirror = left;
        float fade = Math.min(1f, vis);
        boolean mesh = meshReady();
        int colour = Draw.rgba(look.glow, 0.85f * fade);

        g.pose().pushMatrix();
        g.pose().translate(x0 + swayX, y0 + swayY);

        float[] tip = tipOf(f, p);
        float[] tp = onScreen(tip, u);
        if (trail.on() && !still() && trailStrength.f() > 1 && p.rush > 0.05f) {
            tipTrail.addLast(new float[] {tp[0], tp[1], now / 1e9f});
        } else {
            tipTrail.clear();
        }
        while (!tipTrail.isEmpty() && now / 1e9f - tipTrail.peekFirst()[2] > 0.20f) tipTrail.removeFirst();
        if (tipTrail.size() > 1) {
            float[] prev = null;
            float total = Math.max(0.05f, trailStrength.f() / 100f);
            for (float[] pt : tipTrail) {
                if (prev != null) {
                    float age = (float) (now / 1e9f - pt[2]);
                    float k = Math.max(0f, 1f - age / 0.20f) * total;
                    if (k > 0.02f) {
                        Draw.line(g, prev[0], prev[1], pt[0], pt[1], Math.max(1f, u * 0.55f), Draw.rgba(look.glow, k * 0.85f + 0.05f));
                    }
                }
                prev = pt;
            }
        }

        if (blur.on() && !still() && ghostStrength.f() > 1 && p.rush > 0.05f) {
            ghostAt.add(new Rig.T(f.knife));
            ghostAngle.add(new float[] {p.a, p.b, p.c, p.clip == null ? -1f : 0f, p.clipT});
            ghostWhen.add(now);
        }
        while (!ghostWhen.isEmpty() && (now - ghostWhen.get(0)) / 1e9f > 0.16f) {
            ghostAt.remove(0);
            ghostAngle.remove(0);
            ghostWhen.remove(0);
        }
        while (ghostAt.size() > 5) {
            ghostAt.remove(0);
            ghostAngle.remove(0);
            ghostWhen.remove(0);
        }
        float ghostFade = Math.min(1f, ghostStrength.f() / 100f);
        for (int i = 0; i < ghostAt.size(); i++) {
            float[] a = ghostAngle.get(i);
            float al = 0.20f * ghostFade * (1f - i / (float) Math.max(1, ghostAt.size())) * fade;
            if (al < 0.02f) continue;
            if (!mesh || !drawMesh(g, ghostAt.get(i), a[0], a[1], a[2], look, u, al)) pixelKnife(g, ghostAt.get(i), a[0], a[1], a[2], look, u, al);
        }

        rig.clear();
        rig.alpha(fade);
        ArmArt.Skin skin = ArmArt.skin(mc.player, hand.index(), handLight.on() ? glowOf(look, p) : 0);
        float aw = ArmArt.armWidth(skin);
        rig.box(f.arm, 0f, -ARM_L / 2f, 0f, aw, ARM_L * 1.06f, 8f, ArmArt.arm(skin), false, 40f);
        if (showFingers.on()) {
            if (karambit()) kFingers(f, p, skin);
            else fingers(f, p, skin);
        }
        rig.flush(g, u);

        boolean knifeDrawn;
        if (mesh && p.clip != null && KnifeMesh.hasClip(meshName(), p.clip)) {
            try {
                knifeDrawn = KnifeMesh.drawClip(g, meshName(), p.clip, p.clipT, f.knife, look, u, fade, Perf.gameFast());
            } catch (Throwable t) {
                meshBroken = true;
                knifeDrawn = false;
            }
        } else if (mesh) {
            knifeDrawn = drawMesh(g, f.knife, p.a, p.b, p.c, look, u, fade);
        } else {
            knifeDrawn = pixelKnife(g, f.knife, p.a, p.b, p.c, look, u, fade);
        }
        if (!knifeDrawn) knifeDrawn = pixelKnife(g, f.knife, p.a, p.b, p.c, look, u, fade);
        if (!knifeDrawn) {
            lastResort(g, f.knife, p.c, look, u, fade);
            knifeDrawn = true;
        }
        if (knifeDrawn) {
            drewAt = System.nanoTime();
            frames++;
        } else if (!warned && System.nanoTime() - enabledAt > 3_000_000_000L) {
            warned = true;
            com.zlorbie788.zeus.core.Zeus.toast("The knife is not drawing. Try Safe draw in Knife Swing.", false);
        }

        if (sparks.on() && !still() && p.flash > 0.01f) {
            float s = Math.max(0.01f, Math.min(1f, p.flash) * Math.min(1f, flashStrength.f() / 100f));
            int r = Math.round(26f * sc * (0.4f + s));
            Draw.glow(g, Math.round(tp[0]) - r, Math.round(tp[1]) - r, r * 2, r * 2, r, Draw.rgba(look.glow, 0.55f * s * fade), r, 0.9f);
            for (int i = 0; i < 3; i++) {
                double ang = (now / 1e9 + i * 2.1) * 7.0;
                float len = r * (0.55f + 0.45f * s);
                Draw.line(g, tp[0], tp[1], tp[0] + (float) Math.cos(ang) * len, tp[1] + (float) Math.sin(ang) * len, Math.max(1f, u * 0.4f), colour);
            }
        }

        g.pose().popMatrix();
    }

    private KnifeArt.Look lookFor() {
        float time = (System.nanoTime() % 60_000_000_000L) / 1e9f;
        if (type.index() == 0) return KnifeArt.look(preset.index(), shape.index(), finish.index(), design.index(), time);
        if (type.index() == 1) return KnifeArt.kLook(kcolor.index(), kcurve.index(), time);
        return KnifeArt.sLook(sfinish.index(), type.index() - 2, time);
    }

    private String meshName() {
        if (!model3d.on()) return null;
        return karambit() ? KnifeArt.nameOf(key()) : "Butterfly knife";
    }

    private boolean meshReady() {
        if (safeDraw.on() || meshBroken) return false;
        String name = meshName();
        return name != null && KnifeMesh.has(name);
    }

    private boolean drawMesh(GuiGraphics g, Rig.T at, float a, float b, float c, KnifeArt.Look look, float unit, float alpha) {
        try {
            String name = meshName();
            if (name == null) return false;
            return KnifeMesh.draw(g, name, at, a, b, c, look, unit, alpha, Perf.gameFast());
        } catch (Throwable t) {
            meshBroken = true;
            Zeus.LOG.warn("The 3D knife could not be drawn; using the pixel knife instead", t);
            return false;
        }
    }

    private boolean pixelKnife(GuiGraphics g, Rig.T at, float a, float b, float c, KnifeArt.Look look, float unit, float alpha) {
        ghost.clear();
        ghost.alpha(alpha);
        if (karambit()) KnifeArt.buildKarambit(ghost, at, look);
        else KnifeArt.build(ghost, at, a, b, c, look);
        boolean any = ghost.size() > 0;
        ghost.flush(g, unit);
        return any;
    }

    private void lastResort(GuiGraphics g, Rig.T at, float c, KnifeArt.Look look, float unit, float alpha) {
        float[] a = onScreen(at.point(0f, 0f, 0f), unit);
        float[] b = onScreen(at.rotZ(c).point(0f, KnifeArt.BL, 0f), unit);
        Draw.line(g, a[0], a[1], b[0], b[1], Math.max(2f, unit * 1.1f), Draw.rgba(look.blade[0], alpha));
        Draw.line(g, a[0], a[1], a[0] + (b[0] - a[0]) * 0.45f, a[1] + (b[1] - a[1]) * 0.45f, Math.max(2f, unit * 1.1f), Draw.rgba(look.handle[0], alpha));
    }

    private static int glowOf(KnifeArt.Look look, Pose p) {
        float k = 0.18f + 0.55f * Math.max(0f, Math.min(1f, p.flash)) + 0.25f * Math.max(0f, Math.min(1f, p.rush));
        return (Math.round(255f * Math.min(1f, k)) << 24) | (look.glow & 0xFFFFFF);
    }

    private static float[] onScreen(float[] p, float unit) {
        float k = unit * 90f / (90f + p[2]);
        return new float[] {p[0] * k, p[1] * k};
    }

    private static float wrap(float deg) {
        while (deg > 180f) deg -= 360f;
        while (deg < -180f) deg += 360f;
        return deg;
    }

    private float[] tipOf(Frame f, Pose p) {
        return karambit() ? KnifeArt.kTip(f.knife, key()) : KnifeArt.tip(f.knife, p.c);
    }

    private void kFingers(Frame f, Pose p, ArmArt.Skin skin) {
        Rig.Painter paint = ArmArt.finger(skin);
        float rel = Math.max(0f, Math.min(1f, p.release));
        float z = -2.3f;
        for (int i = 0; i < 3; i++) {
            float y = (i - 1) * 2.7f;
            Rig.T hinge = f.fist.move(4.0f, y, z).rotY(rel * (1.15f + 0.1f * i));
            rig.box(hinge, -2.8f, 0f, 0f, 5.6f, 2.6f, 3.2f, paint, false);
        }
        boolean inRing = ring() && Math.abs(p.kx) + Math.abs(p.ky) + Math.abs(p.kz) < 0.6f;
        if (inRing) {
            Rig.T ring = f.fist.move(0.2f, -KnifeArt.KGRIP, -1.3f);
            rig.box(ring, 0f, 1.4f, 0f, 2.4f, 4.6f, 2.8f, paint, false);
        }
        Rig.T thumb = f.fist.move(-2.3f, -4.3f - 1.5f * rel, z - 0.2f).rotZ(0.25f + 0.7f * rel);
        rig.box(thumb, 0f, 0f, 0f, 2.6f, 4.4f, 2.6f, paint, false);
    }

    private void fingers(Frame f, Pose p, ArmArt.Skin skin) {
        Rig.Painter paint = ArmArt.finger(skin);
        float rel = Math.max(0f, Math.min(1f, p.release));
        float z = -(KnifeArt.PIN_Z + KnifeArt.HT / 2f + 1.5f);
        for (int i = 0; i < 4; i++) {
            float y = (i - 1.5f) * 2.4f;
            float hx = 3.6f;
            Rig.T hinge = f.fist.move(hx, y, z).rotY(rel * (1.15f + 0.1f * i));
            rig.box(hinge, -2.5f, 0f, 0f, 5.0f, 2.3f, 3.0f, paint, false);
        }
        Rig.T thumb = f.fist.move(-2.3f, -5.6f - 2f * rel, z - 0.2f).rotZ(0.25f + 0.7f * rel);
        rig.box(thumb, 0f, 0f, 0f, 2.6f, 4.6f, 2.6f, paint, false);
    }
    private static boolean still() {
        return com.zlorbie788.zeus.ui.UiSettings.REDUCE_MOTION.on();
    }

    public String info() {
        String name = karambit() ? KTRICKS[ktrick.index()] : TRICKS[trick.index()];
        if (!enabled()) return name;
        if (frames == 0) return name + " · not drawn yet";
        if (model3d.on() && !meshReady()) return name + " · 3D model fell back to pixels";
        return name;
    }
}
