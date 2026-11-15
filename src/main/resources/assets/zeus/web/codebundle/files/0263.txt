package com.zlorbie788.zeus.ui;

import com.zlorbie788.zeus.render.Projector;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import org.joml.Matrix3x2f;

public final class SkyPhoto {

    static final int W = 1672;

    static final int H = 941;

    private static final String DIR = "textures/atmosphere/";

    private static final float F = (H / 2f) / (float) Math.tan(Math.toRadians(85.0 / 2.0));

    private static final int STRIP = 5;

    private Tex.Img base;

    private Tex.Img starsA;

    private Tex.Img starsB;

    private Tex.Img glow;

    private boolean loaded;

    private boolean failed;

    private int[] mAur;

    private int[] mCloud;

    private static final int MW = W / 8;

    private static final int MH = H / 8;

    private float ox;

    private float oy;

    private float ex;

    private float ey;

    private float dx;

    private float dy;

    private final float[] p0 = new float[2];

    private final float[] p1 = new float[2];

    private final float[] p2 = new float[2];

    private final double[] dirv = new double[3];

    private final float[] tmp = new float[2];

    private int[] cut = new int[512];

    private int strips;

    private boolean anyLand;

    public boolean ready() {

        return loaded;

    }

    public boolean failed() {

        return failed;

    }

    public boolean load() {

        if (loaded) return true;

        if (failed) return false;

        base = Tex.sprite(DIR + "base.png");

        starsA = Tex.sprite(DIR + "stars_a.png");

        starsB = Tex.sprite(DIR + "stars_b.png");

        glow = Tex.sprite(DIR + "glow.png");

        java.awt.image.BufferedImage mk = Tex.image(DIR + "masks.png");

        if (base == null || starsA == null || starsB == null || glow == null || mk == null) {

            failed = true;

            return false;

        }

        mAur = new int[MW * MH];

        mCloud = new int[MW * MH];

        for (int y = 0; y < MH && y < mk.getHeight(); y++) {

            for (int x = 0; x < MW && x < mk.getWidth(); x++) {

                int c = mk.getRGB(x, y);

                mAur[y * MW + x] = (c >> 16) & 255;

                mCloud[y * MW + x] = (c >> 8) & 255;

            }

        }

        mAur = blur(mAur);

        mCloud = blur(mCloud);

        loaded = true;

        return true;

    }

    private static int[] blur(int[] src) {

        int r = 9;

        int[] tmp = new int[src.length];

        int[] out = new int[src.length];

        for (int y = 0; y < MH; y++) {

            for (int x = 0; x < MW; x++) {

                int s = 0;

                for (int k = -r; k <= r; k++) s += src[y * MW + Math.max(0, Math.min(MW - 1, x + k))];

                tmp[y * MW + x] = s / (2 * r + 1);

            }

        }

        for (int y = 0; y < MH; y++) {

            for (int x = 0; x < MW; x++) {

                int s = 0;

                for (int k = -r; k <= r; k++) s += tmp[Math.max(0, Math.min(MH - 1, y + k)) * MW + x];

                out[y * MW + x] = s / (2 * r + 1);

            }

        }

        return out;

    }

    private static float radius(float theta) {

        float q = (float) Math.PI / 4f;

        if (theta <= q) return F * (float) Math.tan(theta);

        return F * (1f + 2f * (theta - q));

    }

    private static float angle(float r) {

        float k = r / F;

        if (k <= 1f) return (float) Math.atan(k);

        return (float) Math.PI / 4f + (k - 1f) * 0.5f;

    }

    private void texToDir(float u, float v, double[] out) {

        float r = (float) Math.hypot(u, v);

        if (r < 1e-3f) {

            out[0] = 0;

            out[1] = 1;

            out[2] = 0;

            return;

        }

        float th = angle(r);

        float s = (float) Math.sin(th);

        out[0] = s * u / r;

        out[1] = Math.cos(th);

        out[2] = -s * v / r;

    }

    private boolean fit(int w, int h) {

        if (!Projector.ray(w / 2f, h / 2f, dirv)) return false;

        double dxw = dirv[0];

        double dyw = Math.max(-1.0, Math.min(1.0, dirv[1]));

        double dzw = dirv[2];

        float theta = (float) Math.acos(dyw);

        float u0 = 0f;

        float v0 = 0f;

        double sn = Math.sqrt(dxw * dxw + dzw * dzw);

        if (sn > 1e-4) {

            float r = radius(theta);

            u0 = (float) (dxw / sn) * r;

            v0 = -(float) (dzw / sn) * r;

        }

        double[] d = new double[3];

        double far = 160.0;

        double eX = Projector.eyeX();

        double eY = Projector.eyeY();

        double eZ = Projector.eyeZ();

        float step = 60f;

        texToDir(u0, v0, d);

        if (!Projector.project(eX + d[0] * far, eY + d[1] * far, eZ + d[2] * far, p0)) return false;

        texToDir(u0 + step, v0, d);

        if (!Projector.project(eX + d[0] * far, eY + d[1] * far, eZ + d[2] * far, p1)) return false;

        texToDir(u0, v0 + step, d);

        if (!Projector.project(eX + d[0] * far, eY + d[1] * far, eZ + d[2] * far, p2)) return false;

        ex = (p1[0] - p0[0]) / step;

        ey = (p1[1] - p0[1]) / step;

        dx = (p2[0] - p0[0]) / step;

        dy = (p2[1] - p0[1]) / step;

        ox = p0[0] - u0 * ex - v0 * dx;

        oy = p0[1] - u0 * ey - v0 * dy;

        return true;

    }

    private void toScreen(float u, float v, float[] out) {

        out[0] = ox + u * ex + v * dx;

        out[1] = oy + u * ey + v * dy;

    }

    private final float[] azel = new float[2];

    private float gap(SkyPhoto.View view, float sx, float sy) {

        if (!view.at(sx, sy, azel)) return -1f;

        float line = view.hasSkyline() ? Math.max(view.skyline(azel[0]), 1.5f) : 1.5f;

        return azel[1] - line;

    }

    private void computeCut(SkyPhoto.View view, int w, int h) {

        strips = (w + STRIP - 1) / STRIP;

        if (cut.length < strips) cut = new int[strips];

        anyLand = false;

        for (int i = 0; i < strips; i++) {

            float sx = Math.min(w, i * STRIP + STRIP * 0.5f);

            int yEnd = h;

            if (gap(view, sx, h) < 0f) {

                if (gap(view, sx, 0) < 0f) {

                    yEnd = 0;

                } else {

                    float lo = 0f;

                    float hi = h;

                    for (int it = 0; it < 8; it++) {

                        float mid = (lo + hi) * 0.5f;

                        if (gap(view, sx, mid) >= 0f) lo = mid;

                        else hi = mid;

                    }

                    yEnd = Math.round(lo);

                }

            }

            cut[i] = yEnd;

            if (yEnd < h) anyLand = true;

        }

    }

    private int minCut(int x0, int x1) {

        int a = Math.max(0, x0 / STRIP);

        int b = Math.min(strips - 1, x1 / STRIP);

        int m = Integer.MAX_VALUE;

        for (int i = a; i <= b; i++) m = Math.min(m, cut[i]);

        return m == Integer.MAX_VALUE ? 0 : m;

    }

    private int maxCut(int x0, int x1) {

        int a = Math.max(0, x0 / STRIP);

        int b = Math.min(strips - 1, x1 / STRIP);

        int m = 0;

        for (int i = a; i <= b; i++) m = Math.max(m, cut[i]);

        return m;

    }

    private interface Op {

        void draw(GuiGraphics g);

    }

    private void place(GuiGraphics g, float tx, float ty, float sx, float sy) {

        g.pose().pushMatrix();

        g.pose().translate(ox, oy);

        g.pose().mul(new Matrix3x2f(ex, ey, dx, dy, 0f, 0f));

        g.pose().translate(tx, ty);

        if (sx != 1f || sy != 1f) g.pose().scale(sx, sy);

    }

    private static void done(GuiGraphics g) {

        g.pose().popMatrix();

    }

    private void bbox(float u0, float v0, float u1, float v1, float[] out) {

        float minX = Float.MAX_VALUE;

        float minY = Float.MAX_VALUE;

        float maxX = -Float.MAX_VALUE;

        float maxY = -Float.MAX_VALUE;

        for (int c = 0; c < 4; c++) {

            toScreen((c & 1) == 0 ? u0 : u1, (c & 2) == 0 ? v0 : v1, tmp);

            minX = Math.min(minX, tmp[0]);

            maxX = Math.max(maxX, tmp[0]);

            minY = Math.min(minY, tmp[1]);

            maxY = Math.max(maxY, tmp[1]);

        }

        out[0] = minX;

        out[1] = minY;

        out[2] = maxX;

        out[3] = maxY;

    }

    private final float[] bb = new float[4];

    private void clipped(GuiGraphics g, int w, int h, int strip, Op op) {

        int x0 = Math.max(0, (int) Math.floor(bb[0]));

        int x1 = Math.min(w, (int) Math.ceil(bb[2]));

        int y0 = Math.max(0, (int) Math.floor(bb[1]));

        int y1 = Math.min(h, (int) Math.ceil(bb[3]));

        if (x1 <= x0 || y1 <= y0) return;

        if (!anyLand) {

            op.draw(g);

            return;

        }

        int lo = minCut(x0, x1);

        if (lo >= y1) {

            op.draw(g);

            return;

        }

        if (maxCut(x0, x1) <= y0) return;

        for (int x = x0; x < x1; ) {

            int xe = Math.min(x1, x + strip);

            int c = minCut(x, xe - 1);

            int ye = Math.min(y1, c);

            if (ye > y0) {

                g.enableScissor(x, y0, xe, ye);

                op.draw(g);

                g.disableScissor();

            }

            x = xe;

        }

    }

    private static float smooth(float a, float b, float x) {

        float t = Math.max(0f, Math.min(1f, (x - a) / (b - a)));

        return t * t * (3f - 2f * t);

    }

    private static int tint(float alpha, float r, float g, float b) {

        int a = Math.round(Math.max(0f, Math.min(1f, alpha)) * 255f);

        int ir = Math.round(Math.max(0f, Math.min(1f, r)) * 255f);

        int ig = Math.round(Math.max(0f, Math.min(1f, g)) * 255f);

        int ib = Math.round(Math.max(0f, Math.min(1f, b)) * 255f);

        return a << 24 | ir << 16 | ig << 8 | ib;

    }

    public static final class Look {

        public float gain = 1f;

        public float motion = 1f;

        public float twinkle = 1f;

        public int cellsX = 19;

        public int cellsY = 10;

        public boolean cutLand = true;

    }

    public interface View {

        boolean at(float sx, float sy, float[] azel);

        default boolean hasSkyline() {
            return false;
        }

        default float skyline(float az) {
            return -90f;
        }
    }

public interface Skyline {

        void reset();

        void sweep(net.minecraft.client.Minecraft mc);

        float at(float az);

        default boolean ready() {
            return true;
        }
    }

    public static final class SimpleSkyline implements Skyline {

        private float[] table = new float[360];

        private int dirty = 1;

        public void reset() {

            dirty = 1;

        }

        public void sweep(net.minecraft.client.Minecraft mc) {

            if (mc.level == null || mc.player == null) return;

            net.minecraft.world.phys.Vec3 eye = mc.player.getEyePosition(1f);

            double r = 120.0;

            double x0 = eye.x - r, z0 = eye.z - r;

            double step = r * 2.0 / (table.length - 1);

            net.minecraft.world.level.ClipContext ct = new net.minecraft.world.level.ClipContext(

                    net.minecraft.world.phys.Vec3.ZERO, net.minecraft.world.phys.Vec3.ZERO,

                    net.minecraft.world.level.ClipContext.Block.VISUAL, net.minecraft.world.level.ClipContext.Fluid.ANY, mc.player);

            for (int i = 0; i < table.length; i++) {

                double a = Math.toRadians(i * 1.0 - 90.0);

                double dx = Math.cos(a) * r, dz = Math.sin(a) * r;

                ct = new net.minecraft.world.level.ClipContext(

                        eye.add(dx, -60.0, dz), eye.add(dx, 200.0, dz),

                        net.minecraft.world.level.ClipContext.Block.VISUAL, net.minecraft.world.level.ClipContext.Fluid.NONE, mc.player);

                HitResult cr = mc.level.clip(ct);

                if (cr != null && (cr.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK)) {

                                    BlockPos bhrPos = ((BlockHitResult) cr).getBlockPos();
                                        double ddx = bhrPos.getX() - eye.x;
                    double ddy = bhrPos.getY() - eye.y;

                    double ddz = bhrPos.getZ() - eye.z;

                    double dSq = ddx * ddx + ddy * ddy + ddz * ddz;

                    table[i] = (float) Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0,

                            (bhrPos.getY() - eye.y) / Math.sqrt(dSq)))));

                } else {

                    table[i] = -90f;

                }

            }

            dirty = 0;

        }

                public boolean ready() {
            return dirty == 0;
        }

        public float at(float az) {

            az = ((az + 90f) % 360f + 360f) % 360f;

            int i = Math.round(az);

            int a = Math.max(0, i);

            int b = Math.min(table.length - 1, i);

            if (a >= b) return table[a];

            float f = az - i;

            return table[a] + (table[b] - table[a]) * f;

        }

    }

    public final Skyline skyline = new SimpleSkyline();

    public void render(GuiGraphics g, int w, int h, SkyPhoto.View view, Look look, float time) {

        if (!load() || look.gain <= 0.01f) return;

        if (!fit(w, h)) return;

        if (look.cutLand) {

            computeCut(view, w, h);

        } else {

            strips = (w + STRIP - 1) / STRIP;

            if (cut.length < strips) cut = new int[strips];

            for (int i = 0; i < strips; i++) cut[i] = h;

            anyLand = false;

        }

        int gainTint = tint(look.gain, 1f, 1f, 1f);

        float hw = W / 2f;

        float hh = H / 2f;

        for (int j = -1; j <= 1; j++) {

            for (int i = -1; i <= 1; i++) {

                if (i == 0 && j == 0) continue;

                float u0 = -hw + i * W;

                float v0 = -hh + j * H;

                bbox(u0, v0, u0 + W, v0 + H, bb);

                if (bb[2] < 0 || bb[0] > w || bb[3] < 0 || bb[1] > h) continue;

                final float sx = (i & 1) != 0 ? -1f : 1f;

                final float sy = (j & 1) != 0 ? -1f : 1f;

                final float tx = sx < 0 ? -hw + (i + 1) * W : -hw + i * W;

                final float ty = sy < 0 ? -hh + (j + 1) * H : -hh + j * H;

                final int c = tint(look.gain, 0.78f, 0.80f, 0.86f);

                clipped(g, w, h, STRIP * 2, gg -> {

                    place(gg, tx, ty, sx, sy);

                    base.drawPart(gg, 0, 0, W, H, 0, 0, W, H, c);

                    done(gg);

                });

            }

        }

        int nx = Math.max(4, look.cellsX);

        int ny = Math.max(3, look.cellsY);

        float m = look.motion;

        final int margin = 22;

        for (int cj = 0; cj < ny; cj++) {

            int y0 = cj * H / ny;

            int y1 = (cj + 1) * H / ny;

            for (int ci = 0; ci < nx; ci++) {

                int x0 = ci * W / nx;

                int x1 = (ci + 1) * W / nx;

                int mx = Math.min(MW - 1, ((x0 + x1) / 2) / 8);

                int my = Math.min(MH - 1, ((y0 + y1) / 2) / 8);

                float wa = mAur[my * MW + mx] / 255f;

                float wc = mCloud[my * MW + mx] / 255f * (1f - wa);

                float cx = (x0 + x1) * 0.5f;

                float cy = (y0 + y1) * 0.5f;

                float ddx = 0f;

                float ddy = 0f;

                if (m > 0f) {

                    float a1 = (float) Math.sin(cy * 0.0046f + time * 0.75f + 1.3f * (float) Math.sin(cx * 0.0021f + time * 0.17f));

                    float a2 = (float) Math.sin(cx * 0.0034f - time * 0.41f + cy * 0.0018f);

                    float a3 = (float) Math.sin(cx * 0.0052f + cy * 0.0028f + time * 0.83f);

                    ddx += wa * (15f * a1 + 5f * a3);

                    ddy += wa * (9f * a2);

                    float c1 = (float) Math.sin(time * 0.21f + cx * 0.0039f + cy * 0.0027f);

                    float c2 = (float) Math.sin(time * 0.17f + cy * 0.0049f - cx * 0.0023f);

                    float away = cx < hw ? -1f : 1f;

                    ddx += wc * (9f * c1 + away * 6f * (0.5f + 0.5f * c2));

                    ddy += wc * (6f * c2);

                    ddx *= m;

                    ddy *= m;

                }

                float u0 = -hw + x0 + ddx;

                float v0 = -hh + y0 + ddy;

                bbox(u0 - margin, v0 - margin, u0 + (x1 - x0) + margin, v0 + (y1 - y0) + margin, bb);

                if (bb[2] < 0 || bb[0] > w || bb[3] < 0 || bb[1] > h) continue;

                final int sx0 = Math.max(0, x0 - margin);

                final int sy0 = Math.max(0, y0 - margin);

                final int sx1 = Math.min(W, x1 + margin);

                final int sy1 = Math.min(H, y1 + margin);

                final float tx = -hw + sx0 + ddx;

                final float ty = -hh + sy0 + ddy;

                clipped(g, w, h, STRIP, gg -> {

                    place(gg, tx, ty, 1f, 1f);

                    base.drawPart(gg, 0, 0, sx1 - sx0, sy1 - sy0, sx0, sy0, sx1 - sx0, sy1 - sy0, gainTint);

                    done(gg);

                });

            }

        }

        bbox(-hw, -hh, hw, hh, bb);

        float tw = look.twinkle;

        if (m > 0f) {

            float g1 = 0.10f + 0.09f * (float) Math.sin(time * 0.62f);

            float g2 = 0.08f + 0.07f * (float) Math.sin(time * 0.37f + 2.1f);

            final int c1 = tint(Math.max(0f, g1) * look.gain * Math.min(1f, m), 1f, 1f, 1f);

            final int c2 = tint(Math.max(0f, g2) * look.gain * Math.min(1f, m), 1f, 1f, 1f);

            final float sh1 = 10f * (float) Math.sin(time * 0.31f);

            final float sh2 = -9f * (float) Math.sin(time * 0.27f + 1f);

            final float sv2 = 6f * (float) Math.sin(time * 0.23f + 0.4f);

            clipped(g, w, h, STRIP * 2, gg -> {

                place(gg, -hw + sh1, -hh, 4f, 4f);

                glow.drawPart(gg, 0, 0, W / 4, H / 4, 0, 0, W / 4, H / 4, c1);

                done(gg);

                place(gg, -hw + sh2, -hh + sv2, 4f, 4f);

                glow.drawPart(gg, 0, 0, W / 4, H / 4, 0, 0, W / 4, H / 4, c2);

                done(gg);

            });

        }

        float ta = 1f - tw * (0.42f + 0.42f * (float) Math.sin(time * 1.9f));

        float tb = 1f - tw * (0.42f + 0.42f * (float) Math.sin(time * 2.7f + 2.3f));

        final int ca = tint(Math.max(0.15f, ta) * look.gain, 1f, 1f, 1f);

        final int cb = tint(Math.max(0.15f, tb) * look.gain, 1f, 1f, 1f);

        clipped(g, w, h, STRIP * 2, gg -> {

            place(gg, -hw, -hh, 1f, 1f);

            starsA.drawPart(gg, 0, 0, W, H, 0, 0, W, H, ca);

            starsB.drawPart(gg, 0, 0, W, H, 0, 0, W, H, cb);

            done(gg);

        });

    }

}
