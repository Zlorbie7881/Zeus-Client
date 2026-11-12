package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Setting;
import com.zlorbie788.zeus.ui.Accent;
import com.zlorbie788.zeus.ui.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public final class Reticle extends Module {
    private final Setting.Choice style = add(new Setting.Choice("Style", "Ring draws four bars around the crosshair. Brackets draws four corner marks.", 0, "Ring", "Brackets"));
    private final Setting.Num size = add(new Setting.Num("Size", "How far the marks sit from the middle of the screen.", 8, 4, 26, 1, " px"));
    private final Setting.Num thickness = add(new Setting.Num("Thickness", "How thick each mark is.", 1, 1, 3, 1, " px"));
    private final Setting.Num gap = add(new Setting.Num("Gap", "The empty space kept clear in the middle so the vanilla crosshair stays readable.", 3, 1, 10, 1, " px"));
    private final Setting.Num opacity = add(new Setting.Num("Opacity", "How strong the reticle is drawn.", 85, 20, 100, 5, "%"));
    private final Setting.Bool dot = add(new Setting.Bool("Centre dot", "A single accent pixel at the exact aim point.", true));
    private final Setting.Bool expand = add(new Setting.Bool("Expand when swinging", "Pushes the marks out while your hand swings.", true));
    private final Setting.Bool glow = add(new Setting.Bool("Glow", "A soft halo behind the marks.", true));

    public Reticle() {
        super("reticle", "Reticle", "Marks the exact aim point with accent bars, brackets and a centre dot so the crosshair reads instantly in any light.", Category.RENDER);
    }

    @Override
    protected void onHud(GuiGraphics g, Minecraft mc, float partial, int w, int h) {
        if (mc.player == null || mc.screen != null || mc.options == null) return;
        if (!mc.options.getCameraType().isFirstPerson()) return;
        float strength = (float) opacity.get() / 100f;
        int accent = Accent.main();
        int cx = w / 2;
        int cy = h / 2;
        int r = (int) Math.round(size.get()) + (expand.on() && mc.player.swinging ? 2 : 0);
        int t = Math.max(1, thickness.i());
        int clear = Math.max(1, gap.i());
        int bar = Draw.rgba(accent, 0.85f * strength);
        int bright = Draw.rgba(Draw.mix(accent, 0xFFFFFFFF, 0.55f), 0.95f * strength);
        if (glow.on() && !Draw.cheap) {
            Draw.glow(g, cx - r - 2, cy - t / 2 - 2, (r + 2) * 2, t + 4, 2, accent, 4, 0.22f * strength);
        }
        if (style.index() == 0) {
            g.fill(cx + clear, cy - t / 2, cx + r, cy + (t + 1) / 2, bar);
            g.fill(cx - r, cy - t / 2, cx - clear, cy + (t + 1) / 2, bar);
            g.fill(cx - t / 2, cy + clear, cx + (t + 1) / 2, cy + r, bar);
            g.fill(cx - t / 2, cy - r, cx + (t + 1) / 2, cy - clear, bar);
        } else {
            int arm = Math.max(3, r - clear);
            bracket(g, cx + clear, cy + clear, 1, 1, arm, t, bar);
            bracket(g, cx - clear, cy + clear, -1, 1, arm, t, bar);
            bracket(g, cx + clear, cy - clear, 1, -1, arm, t, bar);
            bracket(g, cx - clear, cy - clear, -1, -1, arm, t, bar);
        }
        if (dot.on()) {
            g.fill(cx - 1, cy - 1, cx + 1, cy + 1, Draw.rgba(0xFFFFFFFF, 0.55f * strength));
            g.fill(cx, cy, cx + 1, cy + 1, bright);
        }
    }

    private static void bracket(GuiGraphics g, int x, int y, int dx, int dy, int arm, int t, int colour) {
        int hx = dx > 0 ? x : x - arm;
        int vy = dy > 0 ? y : y - arm;
        g.fill(hx, dy > 0 ? y : y - t, hx + arm, dy > 0 ? y + t : y, colour);
        g.fill(dx > 0 ? x : x - t, vy, dx > 0 ? x + t : x, vy + arm, colour);
    }
}
