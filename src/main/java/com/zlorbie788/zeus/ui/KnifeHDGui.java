package com.zlorbie788.zeus.ui;

import com.zlorbie788.zeus.ui.KnifeHD.Frame;
import com.zlorbie788.zeus.ui.KnifeHD.Model;
import com.zlorbie788.zeus.ui.KnifeHD.Quad;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import org.joml.Matrix3x2f;

import java.util.HashMap;
import java.util.Map;

/** Draws the quads {@link KnifeHD} builds, straight into the GUI, with no depth buffer needed. */
public final class KnifeHDGui {
    private KnifeHDGui() {
    }

    private static final Frame FRAME = new Frame();
    private static final Map<String, Tex.Img> SHEETS = new HashMap<>();

    private static Tex.Img sheet(Model m, int level) {
        String path = KnifeHD.sheet(m, level);
        Tex.Img img = SHEETS.get(path);
        if (img == null) {
            img = Tex.sprite(path);
            if (img != null) SHEETS.put(path, img);
        }
        return img;
    }

    /** Draw one HD knife. Returns false when there is nothing to draw. */
    public static boolean draw(GuiGraphics g, int index, Rig.T k, float unit, float alpha, boolean light) {
        return draw(g, KnifeHD.model(index), k, unit, alpha, light);
    }

    public static boolean draw(GuiGraphics g, Model m, Rig.T k, float unit, float alpha, boolean light) {
        if (m == null || k == null || alpha <= 0.004f) return false;
        float pixels = Math.max(1f, (float) Minecraft.getInstance().getWindow().getGuiScale());
        Frame f = KnifeHD.build(FRAME, m, k, unit, pixels, alpha, light);
        if (f.quads.isEmpty()) return false;
        Tex.Img img = sheet(m, f.level);
        if (img == null) return false;
        float tw = img.w, th = img.h;
        var pose = g.pose();
        boolean drew = false;
        for (Quad q : f.quads) {
            float ex = q.x[1] - q.x[0], ey = q.y[1] - q.y[0];
            float fx = q.x[3] - q.x[0], fy = q.y[3] - q.y[0];
            if (Math.abs(ex * fy - ey * fx) < 2e-4f) continue;
            if (!finite(q, ex, ey, fx, fy)) continue;
            int col = tint(q, alpha);
            if ((col >>> 24) < 2) continue;
            float u0 = Math.min(q.u[0], q.u[1]), u1 = Math.max(q.u[0], q.u[1]);
            float v0 = Math.min(q.v[0], q.v[1]), v1 = Math.max(q.v[0], q.v[1]);
            pose.pushMatrix();
            pose.mul(new Matrix3x2f(ex, ey, fx, fy, q.x[0], q.y[0]));
            g.blit(RenderPipelines.GUI_TEXTURED, img.id, 0, 0, u0 * tw, v0 * th, 1, 1,
                    Math.max(1, Math.round((u1 - u0) * tw)), Math.max(1, Math.round((v1 - v0) * th)),
                    img.w, img.h, col);
            pose.popMatrix();
            drew = true;
        }
        return drew;
    }

    private static boolean finite(Quad q, float ex, float ey, float fx, float fy) {
        for (int i = 0; i < 4; i++) {
            if (!Float.isFinite(q.x[i]) || !Float.isFinite(q.y[i])) return false;
            if (!Float.isFinite(q.u[i]) || !Float.isFinite(q.v[i])) return false;
        }
        return Float.isFinite(ex + ey + fx + fy);
    }

    /** One colour for the whole quad: the light pass is white, the solid passes keep their light. */
    private static int tint(Quad q, float alpha) {
        int r = 0, gg = 0, b = 0, a = 0;
        for (int i = 0; i < 4; i++) {
            int c = q.c[i];
            a += (c >>> 24) & 255;
            r += (c >> 16) & 255;
            gg += (c >> 8) & 255;
            b += c & 255;
        }
        a = Math.round(a / 4f * alpha);
        if (q.light) {
            int lv = Math.round((r + gg + b) / 12f);
            int la = Math.min(255, Math.round(lv * alpha * 0.92f));
            return (la << 24) | 0x00FFFFFF;
        }
        return (Math.min(255, a) << 24) | ((r / 4) << 16) | ((gg / 4) << 8) | (b / 4);
    }

    /** Drop the cached sheets, for a resource reload. */
    public static void clear() {
        SHEETS.clear();
    }
}
