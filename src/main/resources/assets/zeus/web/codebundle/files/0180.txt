package com.zlorbie788.zeus.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zlorbie788.zeus.core.Zeus;
import net.minecraft.client.gui.GuiGraphics;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The Zeus lettering as a font: a row of silver, carved-metal capitals (made by {@code tools/greek_font.py}) drawn letter by
 * letter at any size. Lower case is drawn as capitals. Text that has a letter the font does not have (other alphabets) is not
 * drawn by this class: {@link #supports} says so and the caller uses the game's font instead.
 */
public final class GreekFont {
    private GreekFont() {}

    private static final String PICTURE = "textures/gui/menu/greek.png";
    private static boolean tried;
    private static boolean ok;
    private static int cap = 128;
    private static int row = 190;
    private static int base = 158;
    private static int pad = 14;
    private static int space = 59;
    private static int gap = 27;
    private static int total;
    private static final Map<Character, int[]> GLYPHS = new HashMap<>();

    private static void load() {
        if (tried) return;
        tried = true;
        try (InputStream in = GreekFont.class.getResourceAsStream("/assets/zeus/textures/gui/menu/greek.json")) {
            if (in == null) return;
            JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            cap = o.get("cap").getAsInt();
            row = o.get("row").getAsInt();
            base = o.get("base").getAsInt();
            pad = o.get("pad").getAsInt();
            space = o.get("space").getAsInt();
            gap = o.get("gap").getAsInt();
            JsonObject g = o.getAsJsonObject("g");
            for (Map.Entry<String, com.google.gson.JsonElement> e : g.entrySet()) {
                JsonArray a = e.getValue().getAsJsonArray();
                GLYPHS.put(e.getKey().charAt(0), new int[] {a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt()});
                total = Math.max(total, a.get(0).getAsInt() + a.get(1).getAsInt());
            }
            ok = !GLYPHS.isEmpty();
        } catch (Exception | LinkageError e) {
            Zeus.LOG.warn("The Zeus lettering could not be read; the game's font is used instead", e);
        }
    }

    public static boolean available() {
        load();
        return ok;
    }

    /** True when every character of the text can be drawn with this font (spaces and lower case included). */
    public static boolean supports(String text) {
        if (!available()) return false;
        for (int i = 0; i < text.length(); i++) {
            char c = Character.toUpperCase(text.charAt(i));
            if (c != ' ' && !GLYPHS.containsKey(c)) return false;
        }
        return true;
    }

    /** The width of the text, in interface pixels, for letters {@code capHeight} high. */
    public static int width(String text, int capHeight) {
        if (!available()) return Draw.width(text);
        float s = capHeight / (float) cap;
        float x = 0f;
        String t = text.toUpperCase(Locale.ROOT);
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c == ' ') {
                x += space;
                continue;
            }
            int[] g = GLYPHS.get(c);
            if (g == null) continue;
            x += g[2] + (i < t.length() - 1 ? gap : 0);
        }
        return Math.round(x * s);
    }

    /** Draws the text with its capitals {@code capHeight} interface pixels high, the top of the capitals at y. */
    public static void draw(GuiGraphics g, String text, int x, int y, int capHeight, int tint, boolean shadow) {
        if (!available()) {
            Draw.text(g, text, x, y, tint);
            return;
        }
        int gs = Tex.guiScale();
        float s = capHeight / (float) cap;
        float px = s * gs;                       // texture pixels per atlas pixel
        int tw = Math.max(1, Math.round(total * px));
        int th = Math.max(1, Math.round(row * px));
        Tex.Img img = Tex.fitted("greek_" + th, PICTURE, tw, th, false, 0, 0);
        if (img == null) {
            Draw.text(g, text, x, y, tint);
            return;
        }
        String t = text.toUpperCase(Locale.ROOT);
        float cx = x;
        int topPad = base - cap;                 // the capitals start this far down in the picture
        int dy = y - Math.round(topPad * s);
        int dh = Math.round(row * s);
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c == ' ') {
                cx += space * s;
                continue;
            }
            int[] gl = GLYPHS.get(c);
            if (gl == null) continue;
            int dx = Math.round(cx - pad * s);
            int dw = Math.round(gl[1] * s);
            int sx = Math.round(gl[0] * px);
            int sw = Math.min(img.w - sx, Math.round(gl[1] * px));
            if (sw > 0 && dw > 0) {
                if (shadow) img.drawPart(g, dx + 1, dy + 1, dw, dh, sx, 0, sw, th, Draw.rgba(0xFF000000, Math.min(1f, Draw.a(tint) / 255f * 0.75f)));
                img.drawPart(g, dx, dy, dw, dh, sx, 0, sw, th, tint);
            }
            cx += (gl[2] + gap) * s;
        }
    }

    public static void drawCentered(GuiGraphics g, String text, int cx, int y, int capHeight, int tint, boolean shadow) {
        draw(g, text, cx - width(text, capHeight) / 2, y, capHeight, tint, shadow);
    }
}
