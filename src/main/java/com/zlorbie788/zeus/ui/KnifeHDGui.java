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
            Geo geo = geo(m, f);
            return geo != null && submit(g, geo);
        } catch (RuntimeException | Error e) {
            failures++;
            brokenUntil = System.currentTimeMillis() + 5_000L;
            lastError = e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
            com.zlorbie788.zeus.core.Zeus.LOG.warn("HD knife could not be drawn (attempt {}); using the pixel knife", failures, e);
            return false;
        }
    }

    private static final KnifeHD.Frame FIT = new KnifeHD.Frame();

    // ------------------------------------------------------------------ cached still knives
    /** One knife's ready-to-send vertices, split by pipeline, plus which sheet it samples. */
    private static final class Geo {
        final String sheet;
        final String fallback;
        final List<Batch> batches = new java.util.ArrayList<>();

        Geo(String sheet, String fallback) {
            this.sheet = sheet;
            this.fallback = fallback;
        }
    }

    private static Geo geo(KnifeHD.Model m, KnifeHD.Frame f) {
        Geo geo = new Geo(KnifeHD.sheet(m, f.level), f.level != 1 ? KnifeHD.sheet(m, 1) : null);
        List<KnifeHD.Quad> qs = f.quads;
        int start = 0;
        while (start < qs.size()) {
            int grp = qs.get(start).group;
            int end = start;
            while (end < qs.size() && qs.get(end).group == grp) end++;
            boolean light2 = qs.get(start).light;
            RenderPipeline pipe = light2 ? RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA : RenderPipelines.GUI_TEXTURED;
            Batch b = new Batch(pipe, qs, start, end);
            if (b.n > 0) geo.batches.add(b);
            start = end;
        }
        return geo;
    }

    private static boolean submit(GuiGraphics g, Geo geo) {
        Tex.Img img = Tex.sprite(geo.sheet);
        if (img == null && geo.fallback != null) img = Tex.sprite(geo.fallback);
        if (img == null) return false;
        AbstractTexture tex = Minecraft.getInstance().getTextureManager().getTexture(img.id);
        TextureSetup setup = TextureSetup.singleTexture(tex.getTextureView(), tex.getSampler());
        Matrix3x2f pose = new Matrix3x2f(g.pose());
        ScreenRectangle scissor = g.scissorStack.peek();
        for (Batch b : geo.batches) {
            Element e = new Element(b, setup, pose, scissor);
            if (e.bounds != null) g.guiRenderState.submitGuiElement(e);
        }
        return true;
    }

    /** Least recently used still knives, keyed by knife, box size and detail. Small: each is a few hundred quads. */
    private static final java.util.LinkedHashMap<Long, Geo> STILL = new java.util.LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(java.util.Map.Entry<Long, Geo> e) {
            return size() > 220;
        }
    };
    private static final java.util.HashMap<Integer, float[]> REST_FIT = new java.util.HashMap<>();

    /** The knife's on-screen box at unit 1 in the gallery's resting pose (measured once). */
    private static float[] restFit(int knife, KnifeHD.Model m, Rig.T rest) {
        float[] b = REST_FIT.get(knife);
        if (b != null) return b;
        KnifeHD.build(FIT, m, rest, 1f, 1f, 1f, false);
        if (FIT.quads.isEmpty()) return null;
        float x0 = Float.MAX_VALUE, y0 = Float.MAX_VALUE, x1 = -Float.MAX_VALUE, y1 = -Float.MAX_VALUE;
        for (KnifeHD.Quad q : FIT.quads) {
            for (int i = 0; i < 4; i++) {
                x0 = Math.min(x0, q.x[i]);
                y0 = Math.min(y0, q.y[i]);
                x1 = Math.max(x1, q.x[i]);
                y1 = Math.max(y1, q.y[i]);
            }
        }
        b = new float[] {x0, y0, x1, y1};
        REST_FIT.put(knife, b);
        return b;
    }

    /**
     * A knife fitted into a box in the gallery's resting pose, not moving. Built once per knife and
     * box size, then only re-sent each frame: tiles, reel cards and locked previews use this.
     */
    public static boolean still(GuiGraphics g, int knife, int bx, int by, int bw, int bh, boolean big) {
        if (broken() || bw <= 2 || bh <= 2) return false;
        KnifeHD.Model m = KnifeHD.model(knife);
        if (m == null) return false;
        Rig.T k = Rig.T.rz((float) Math.PI - 1.10f).rotY(-0.35f).rotX(0.12f);
        float[] b = restFit(knife, m, k);
        if (b == null) return false;
        float ew = Math.max(1e-3f, (b[2] - b[0]) * 0.80f), eh = Math.max(1e-3f, (b[3] - b[1]) * 0.80f);
        float unit = Math.min(bw / ew, bh / eh) * 0.86f;
        float pixels = (float) Minecraft.getInstance().getWindow().getGuiScale();
        long key = ((long) knife << 40) ^ ((long) Math.round(unit * 32f) << 8) ^ ((long) Math.round(pixels) << 2) ^ (big ? 1 : 0);
        try {
            Geo geo = STILL.get(key);
            if (geo == null) {
                KnifeHD.Frame f = KnifeHD.build(FRAME, m, k, unit, pixels, 1f, true);
                if (f.quads.isEmpty()) return false;
                geo = geo(m, f);
                STILL.put(key, geo);
            }
            g.pose().pushMatrix();
            g.pose().translate(bx + bw / 2f - (b[0] + b[2]) / 2f * unit, by + bh / 2f - (b[1] + b[3]) / 2f * unit);
            boolean ok = submit(g, geo);
            g.pose().popMatrix();
            return ok;
        } catch (RuntimeException | Error e) {
            failures++;
            brokenUntil = System.currentTimeMillis() + 5_000L;
            lastError = e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
            return false;
        }
    }

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
        // fitted from the resting pose, measured once per knife, so the turning knife keeps one size
        float[] fb = restFit(knife, m, Rig.T.rz((float) Math.PI - 1.10f).rotY(-0.35f).rotX(0.12f));
        if (fb == null) return false;
        float x0 = fb[0], y0 = fb[1], x1 = fb[2], y1 = fb[3];
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
        float[] fb = restFit(knife, m, rest);
        if (fb == null) return false;
        float x0 = fb[0], y0 = fb[1], x1 = fb[2], y1 = fb[3];
        float ew = Math.max(1e-3f, (x1 - x0) * 0.80f), eh = Math.max(1e-3f, (y1 - y0) * 0.80f);
        float unit = Math.min(bw / ew, bh / eh) * 0.86f;
        Rig.T k = Rig.T.rz((float) Math.PI - 1.10f + tilt).rotY(yaw).rotX(0.12f);
        g.pose().pushMatrix();
        g.pose().translate(bx + bw / 2f - (x0 + x1) / 2f * unit, by + bh / 2f - (y0 + y1) / 2f * unit);
        boolean ok = draw(g, knife, k, unit, 1f, true);
        g.pose().popMatrix();
        return ok;
    }

    /** Vertices for one pipeline's quads, copied out of the reused frame so they can be kept. */
    private static final class Batch {
        final RenderPipeline pipeline;
        final float[] xy;
        final float[] uv;
        final int[] col;
        final int n;
        final float minX, minY, maxX, maxY;

        Batch(RenderPipeline pipeline, List<KnifeHD.Quad> qs, int from, int to) {
            this.pipeline = pipeline;
            // Every quad is sent twice, once in each winding.  GUI pipelines cull back faces, and which
            // winding survives depends on how the 3D knife is turned; with both copies exactly one passes
            // the cull test, so no face of the knife can disappear and none is drawn twice.
            int count = (to - from) * 8;
            xy = new float[count * 2];
            uv = new float[count * 2];
            col = new int[count];
            float x0 = Float.MAX_VALUE, y0 = Float.MAX_VALUE, x1 = -Float.MAX_VALUE, y1 = -Float.MAX_VALUE;
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
                        x0 = Math.min(x0, x);
                        y0 = Math.min(y0, y);
                        x1 = Math.max(x1, x);
                        y1 = Math.max(y1, y);
                        o++;
                    }
                }
            }
            n = count;
            minX = x0;
            minY = y0;
            maxX = x1;
            maxY = y1;
        }
    }

    /** One batch sent with this frame's pose and clip. */
    private static final class Element implements GuiElementRenderState {
        private final RenderPipeline pipeline;
        private final TextureSetup setup;
        private final Matrix3x2f pose;
        private final ScreenRectangle scissor;
        private final ScreenRectangle bounds;
        private final float[] xy;
        private final float[] uv;
        private final int[] col;

        Element(Batch b, TextureSetup setup, Matrix3x2f pose, ScreenRectangle scissor) {
            this.pipeline = b.pipeline;
            this.setup = setup;
            this.pose = pose;
            this.scissor = scissor;
            this.xy = b.xy;
            this.uv = b.uv;
            this.col = b.col;
            ScreenRectangle r = null;
            if (b.n > 0 && b.maxX >= b.minX) {
                int x0 = (int) Math.floor(b.minX), y0 = (int) Math.floor(b.minY);
                int w = Math.max(1, (int) Math.ceil(b.maxX) - x0), h = Math.max(1, (int) Math.ceil(b.maxY) - y0);
                r = new ScreenRectangle(x0, y0, w, h).transformMaxBounds(pose);
                if (scissor != null) r = scissor.intersection(r);
            }
            bounds = r;
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
