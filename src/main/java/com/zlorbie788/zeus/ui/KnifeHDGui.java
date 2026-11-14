package com.zlorbie788.zeus.ui;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.joml.Matrix3x2f;

/**
 * Submits the quads {@link KnifeHD} builds as real GUI geometry: four free vertices per quad,
 * each with its own uv and colour, so faces are smoothly lit and the outline is the texture's.
 *
 * <p>Quads of one group share a pipeline and go into one element; the light groups use the
 * premultiplied pipeline, whose texels carry almost no alpha, so they add light.  Groups are
 * submitted in paint order and their bounds overlap, so the GUI keeps that order.
 */
public final class KnifeHDGui {
    private KnifeHDGui() {
    }

    private static final KnifeHD.Frame FRAME = new KnifeHD.Frame();
    private static volatile long brokenUntil;
    private static int failures;
    private static String lastError = "";

    /** One failure (a texture still uploading, a resource reload) should not cost the HD knife for the whole session. */
    private static boolean broken() {
        return failures >= 5 || System.currentTimeMillis() < brokenUntil;
    }

    public static String problem() {
        return failures == 0 ? "" : lastError;
    }

    public static boolean ready(int knife) {
        if (broken()) return false;
        KnifeHD.Model m = KnifeHD.model(knife);
        return m != null && Tex.sprite(KnifeHD.sheet(m, 1)) != null;
    }

    /** Draws a knife at the rig transform k.  Returns false if it could not be drawn. */
    public static boolean draw(GuiGraphics g, int knife, Rig.T k, float unit, float alpha, boolean light) {
        if (broken()) return false;
        try {
            KnifeHD.Model m = KnifeHD.model(knife);
            if (m == null) return false;
            float pixels = (float) Minecraft.getInstance().getWindow().getGuiScale();
            KnifeHD.Frame f = KnifeHD.build(FRAME, m, k, unit, pixels, alpha, light);
            if (f.quads.isEmpty()) return false;
            Tex.Img img = Tex.sprite(KnifeHD.sheet(m, f.level));
            if (img == null && f.level != 1) img = Tex.sprite(KnifeHD.sheet(m, 1));
            if (img == null) return false;
            AbstractTexture tex = Minecraft.getInstance().getTextureManager().getTexture(img.id);
            TextureSetup setup = TextureSetup.singleTexture(tex.getTextureView(), tex.getSampler());
            Matrix3x2f pose = new Matrix3x2f(g.pose());
            ScreenRectangle scissor = g.scissorStack.peek();
            List<KnifeHD.Quad> qs = f.quads;
            int start = 0;
            while (start < qs.size()) {
                int grp = qs.get(start).group;
                int end = start;
                while (end < qs.size() && qs.get(end).group == grp) end++;
                boolean light2 = qs.get(start).light;
                RenderPipeline pipe = light2 ? RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA : RenderPipelines.GUI_TEXTURED;
                Element e = new Element(pipe, setup, pose, scissor, qs, start, end);
                if (e.bounds != null) g.guiRenderState.submitGuiElement(e);
                start = end;
            }
            return true;
        } catch (RuntimeException | Error e) {
            failures++;
            brokenUntil = System.currentTimeMillis() + 5_000L;
            lastError = e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
            com.zlorbie788.zeus.core.Zeus.LOG.warn("HD knife could not be drawn (attempt {}); using the pixel knife", failures, e);
            return false;
        }
    }

    private static final KnifeHD.Frame FIT = new KnifeHD.Frame();

    /**
     * The gallery view: the knife on a slow turn, fitted to the box.  The turn is gentle enough
     * to keep the face towards the viewer and shows the highlights running along the blade.
     */
    public static boolean showcase(GuiGraphics g, int knife, int bx, int by, int bw, int bh, float time, boolean big) {
        if (broken()) return false;
        KnifeHD.Model m = KnifeHD.model(knife);
        if (m == null) return false;
        float sway = (float) Math.sin(time * (big ? 0.9 : 0.7) + knife * 0.8);
        Rig.T k = Rig.T.rz((float) Math.PI - 1.10f).rotY(-0.35f + 0.32f * sway).rotX(0.12f + 0.06f * sway);
        // measure at unit 1, then scale to the box (projection is linear in unit)
        KnifeHD.build(FIT, m, k, 1f, 1f, 1f, false);
        if (FIT.quads.isEmpty()) return false;
        float x0 = Float.MAX_VALUE, y0 = Float.MAX_VALUE, x1 = -Float.MAX_VALUE, y1 = -Float.MAX_VALUE;
        for (KnifeHD.Quad q : FIT.quads) {
            for (int i = 0; i < 4; i++) {
                x0 = Math.min(x0, q.x[i]);
                y0 = Math.min(y0, q.y[i]);
                x1 = Math.max(x1, q.x[i]);
                y1 = Math.max(y1, q.y[i]);
            }
        }
        // the sheet has empty margins around the knife; 0.80 of the quads' box is the knife
        float ew = Math.max(1e-3f, (x1 - x0) * 0.80f), eh = Math.max(1e-3f, (y1 - y0) * 0.80f);
        float unit = Math.min(bw / ew, bh / eh) * 0.86f;
        g.pose().pushMatrix();
        g.pose().translate(bx + bw / 2f - (x0 + x1) / 2f * unit, by + bh / 2f - (y0 + y1) / 2f * unit);
        boolean ok = draw(g, knife, k, unit, 1f, true);
        g.pose().popMatrix();
        return ok;
    }

    /**
     * Draws an HD knife fitted into a box with an explicit turn (yaw round its length) and tilt,
     * for the crate reveal. The fit is measured once at rest so the knife does not pump in size
     * as it turns.
     */
    public static boolean spin(GuiGraphics g, int knife, int bx, int by, int bw, int bh, float yaw, float tilt) {
        if (broken()) return false;
        KnifeHD.Model m = KnifeHD.model(knife);
        if (m == null) return false;
        Rig.T rest = Rig.T.rz((float) Math.PI - 1.10f).rotY(-0.35f).rotX(0.12f);
        KnifeHD.build(FIT, m, rest, 1f, 1f, 1f, false);
        if (FIT.quads.isEmpty()) return false;
        float x0 = Float.MAX_VALUE, y0 = Float.MAX_VALUE, x1 = -Float.MAX_VALUE, y1 = -Float.MAX_VALUE;
        for (KnifeHD.Quad q : FIT.quads) {
            for (int i = 0; i < 4; i++) {
                x0 = Math.min(x0, q.x[i]);
                y0 = Math.min(y0, q.y[i]);
                x1 = Math.max(x1, q.x[i]);
                y1 = Math.max(y1, q.y[i]);
            }
        }
        float ew = Math.max(1e-3f, (x1 - x0) * 0.80f), eh = Math.max(1e-3f, (y1 - y0) * 0.80f);
        float unit = Math.min(bw / ew, bh / eh) * 0.86f;
        Rig.T k = Rig.T.rz((float) Math.PI - 1.10f + tilt).rotY(yaw).rotX(0.12f);
        g.pose().pushMatrix();
        g.pose().translate(bx + bw / 2f - (x0 + x1) / 2f * unit, by + bh / 2f - (y0 + y1) / 2f * unit);
        boolean ok = draw(g, knife, k, unit, 1f, true);
        g.pose().popMatrix();
        return ok;
    }

    /** A batch of quads with their own vertices; a copy, because the frame is reused. */
    private static final class Element implements GuiElementRenderState {
        private final RenderPipeline pipeline;
        private final TextureSetup setup;
        private final Matrix3x2f pose;
        private final ScreenRectangle scissor;
        private final ScreenRectangle bounds;
        private final float[] xy;
        private final float[] uv;
        private final int[] col;

        Element(RenderPipeline pipeline, TextureSetup setup, Matrix3x2f pose, ScreenRectangle scissor,
                List<KnifeHD.Quad> qs, int from, int to) {
            this.pipeline = pipeline;
            this.setup = setup;
            this.pose = pose;
            this.scissor = scissor;
            // Every quad is sent twice, once in each winding.  GUI pipelines cull back faces, and which
            // winding survives depends on how the 3D knife is turned; with both copies exactly one passes
            // the cull test, so no face of the knife can disappear and none is drawn twice.
            int n = (to - from) * 8;
            xy = new float[n * 2];
            uv = new float[n * 2];
            col = new int[n];
            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
            int o = 0;
            for (int i = from; i < to; i++) {
                KnifeHD.Quad q = qs.get(i);
                for (int pass = 0; pass < 2; pass++) {
                    for (int k = 0; k < 4; k++) {
                        int c = pass == 0 ? k : (4 - k) & 3;
                        float x = q.x[c], y = q.y[c];
                        if (!Float.isFinite(x) || !Float.isFinite(y)) {
                            x = 0f;
                            y = 0f;
                        }
                        xy[o * 2] = x;
                        xy[o * 2 + 1] = y;
                        uv[o * 2] = q.u[c];
                        uv[o * 2 + 1] = q.v[c];
                        col[o] = q.c[c];
                        minX = Math.min(minX, x);
                        minY = Math.min(minY, y);
                        maxX = Math.max(maxX, x);
                        maxY = Math.max(maxY, y);
                        o++;
                    }
                }
            }
            ScreenRectangle b = null;
            if (n > 0 && maxX >= minX) {
                int x0 = (int) Math.floor(minX), y0 = (int) Math.floor(minY);
                int w = Math.max(1, (int) Math.ceil(maxX) - x0), h = Math.max(1, (int) Math.ceil(maxY) - y0);
                b = new ScreenRectangle(x0, y0, w, h).transformMaxBounds(pose);
                if (scissor != null) b = scissor.intersection(b);
            }
            bounds = b;
        }

        @Override
        public void buildVertices(VertexConsumer vc) {
            for (int i = 0; i < col.length; i++) {
                vc.addVertexWith2DPose(pose, xy[i * 2], xy[i * 2 + 1]).setUv(uv[i * 2], uv[i * 2 + 1]).setColor(col[i]);
            }
        }

        @Override
        public RenderPipeline pipeline() {
            return pipeline;
        }

        @Override
        public TextureSetup textureSetup() {
            return setup;
        }

        @Override
        public ScreenRectangle scissorArea() {
            return scissor;
        }

        @Override
        public ScreenRectangle bounds() {
            return bounds;
        }
    }
}
