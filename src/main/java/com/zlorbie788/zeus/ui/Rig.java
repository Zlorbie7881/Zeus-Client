package com.zlorbie788.zeus.ui;

import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix3x2f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class Rig {

    public static final int K = 4;
    private static final float D = 90f;
    private static final float LX = -0.40f, LY = -0.55f, LZ = -0.73f;

    public static final class T {
        public final float[] r = {1, 0, 0, 0, 1, 0, 0, 0, 1};
        public float x, y, z;

        public T() {
        }

        public T(T o) {
            System.arraycopy(o.r, 0, r, 0, 9);
            x = o.x;
            y = o.y;
            z = o.z;
        }

        public static T at(float x, float y, float z) {
            T t = new T();
            t.x = x;
            t.y = y;
            t.z = z;
            return t;
        }

        public T then(T c) {
            T o = new T();
            float[] a = r;
            float[] b = c.r;
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) o.r[i * 3 + j] = a[i * 3] * b[j] + a[i * 3 + 1] * b[3 + j] + a[i * 3 + 2] * b[6 + j];
            }
            o.x = a[0] * c.x + a[1] * c.y + a[2] * c.z + x;
            o.y = a[3] * c.x + a[4] * c.y + a[5] * c.z + y;
            o.z = a[6] * c.x + a[7] * c.y + a[8] * c.z + z;
            return o;
        }

        public T worldRot(T rotation, float px, float py, float pz) {
            T o = new T();
            float[] a = rotation.r;
            float[] b = r;
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) o.r[i * 3 + j] = a[i * 3] * b[j] + a[i * 3 + 1] * b[3 + j] + a[i * 3 + 2] * b[6 + j];
            }
            float dx = x - px, dy = y - py, dz = z - pz;
            o.x = a[0] * dx + a[1] * dy + a[2] * dz + px;
            o.y = a[3] * dx + a[4] * dy + a[5] * dz + py;
            o.z = a[6] * dx + a[7] * dy + a[8] * dz + pz;
            return o;
        }

        public T move(float dx, float dy, float dz) {
            return then(at(dx, dy, dz));
        }

        public T rotX(float a) {
            return then(rx(a));
        }

        public T rotY(float a) {
            return then(ry(a));
        }

        public T rotZ(float a) {
            return then(rz(a));
        }

        public float[] point(float lx, float ly, float lz) {
            return new float[] {r[0] * lx + r[1] * ly + r[2] * lz + x, r[3] * lx + r[4] * ly + r[5] * lz + y, r[6] * lx + r[7] * ly + r[8] * lz + z};
        }

        public static T rx(float a) {
            T t = new T();
            float c = (float) Math.cos(a), s = (float) Math.sin(a);
            t.r[4] = c;
            t.r[5] = -s;
            t.r[7] = s;
            t.r[8] = c;
            return t;
        }

        public static T ry(float a) {
            T t = new T();
            float c = (float) Math.cos(a), s = (float) Math.sin(a);
            t.r[0] = c;
            t.r[2] = s;
            t.r[6] = -s;
            t.r[8] = c;
            return t;
        }

        public static T rz(float a) {
            T t = new T();
            float c = (float) Math.cos(a), s = (float) Math.sin(a);
            t.r[0] = c;
            t.r[1] = -s;
            t.r[3] = s;
            t.r[4] = c;
            return t;
        }
    }

    public static final class Ctx {
        public float alpha = 1f;
        public float shade = 1f;

        public float nx, ny, nz;

        public float glint;

        public int col(int argb) {
            int a = Math.round(((argb >>> 24) & 255) * alpha);
            if (a <= 0) return 0;
            int r = Math.min(255, Math.round(((argb >> 16) & 255) * shade));
            int g = Math.min(255, Math.round(((argb >> 8) & 255) * shade));
            int b = Math.min(255, Math.round((argb & 255) * shade));
            return (a << 24) | (r << 16) | (g << 8) | b;
        }

        public int raw(int argb, float k) {
            int a = Math.round(((argb >>> 24) & 255) * alpha * k);
            return a <= 0 ? 0 : (Math.min(255, a) << 24) | (argb & 0xFFFFFF);
        }
    }

    public interface Painter {

        void paint(GuiGraphics g, Ctx c, int face, int w, int h);
    }

    private static final class Face {
        float cx, cy, cz, ux, uy, vx, vy, nx, ny, nz, alphaScale, bias;
        int w, h, id;
        Painter p;
    }

    private final List<Face> faces = new ArrayList<>();
    private final Ctx ctx = new Ctx();
    private float alpha = 1f;

    public void clear() {
        faces.clear();
    }

    public void alpha(float a) {
        alpha = a;
    }

    public int size() {
        return faces.size();
    }

    public void box(T t, float cx, float cy, float cz, float sx, float sy, float sz, Painter p, boolean twoSided) {
        box(t, cx, cy, cz, sx, sy, sz, p, twoSided, 0f);
    }

    public void box(T t, float cx, float cy, float cz, float sx, float sy, float sz, Painter p, boolean twoSided, float bias) {
        float[] c = t.point(cx, cy, cz);
        float[] r = t.r;
        float[] ex = {r[0], r[3], r[6]};
        float[] ey = {r[1], r[4], r[7]};
        float[] ez = {r[2], r[5], r[8]};
        for (int f = 0; f < 6; f++) {
            float[] n;
            float[] u;
            float[] v;
            float half;
            int w;
            int h;
            switch (f) {
                case 0 -> {
                    n = ex;
                    u = ez;
                    v = ey;
                    half = sx / 2;
                    w = px(sz);
                    h = px(sy);
                }
                case 1 -> {
                    n = neg(ex);
                    u = neg(ez);
                    v = ey;
                    half = sx / 2;
                    w = px(sz);
                    h = px(sy);
                }
                case 2 -> {
                    n = ey;
                    u = ex;
                    v = ez;
                    half = sy / 2;
                    w = px(sx);
                    h = px(sz);
                }
                case 3 -> {
                    n = neg(ey);
                    u = ex;
                    v = neg(ez);
                    half = sy / 2;
                    w = px(sx);
                    h = px(sz);
                }
                case 4 -> {
                    n = ez;
                    u = neg(ex);
                    v = ey;
                    half = sz / 2;
                    w = px(sx);
                    h = px(sy);
                }
                default -> {
                    n = neg(ez);
                    u = ex;
                    v = ey;
                    half = sz / 2;
                    w = px(sx);
                    h = px(sy);
                }
            }
            float fx = c[0] + n[0] * half, fy = c[1] + n[1] * half, fz = c[2] + n[2] * half;
            float toEye = n[0] * (0f - fx) + n[1] * (0f - fy) + n[2] * (-D - fz);
            if (toEye <= 0f && !twoSided) continue;
            Face fc = new Face();
            fc.cx = fx;
            fc.cy = fy;
            fc.cz = fz;
            boolean back = toEye <= 0f;
            fc.ux = u[0];
            fc.uy = u[1];
            fc.vx = v[0];
            fc.vy = v[1];
            fc.nx = back ? -n[0] : n[0];
            fc.ny = back ? -n[1] : n[1];
            fc.nz = back ? -n[2] : n[2];
            fc.w = w;
            fc.h = h;
            fc.id = f;
            fc.p = p;
            fc.alphaScale = alpha;
            fc.bias = bias;
            faces.add(fc);
        }
    }

    private static float[] neg(float[] a) {
        return new float[] {-a[0], -a[1], -a[2]};
    }

    private int px(float units) {
        return Math.max(1, Math.round(units * K));
    }

    public void flush(GuiGraphics g, float unit) {
        faces.sort(Comparator.comparingDouble((Face f) -> -(f.cz + f.bias)));
        for (Face f : faces) {
            float s = unit * D / (D + f.cz);
            float per = s / K;
            float ux = f.ux * per, uy = f.uy * per, vx = f.vx * per, vy = f.vy * per;
            float cx = f.cx * unit * D / (D + f.cz);
            float cy = f.cy * unit * D / (D + f.cz);
            float ox = cx - ux * f.w / 2f - vx * f.h / 2f;
            float oy = cy - uy * f.w / 2f - vy * f.h / 2f;
            float lam = Math.max(0f, f.nx * LX + f.ny * LY + f.nz * LZ);
            ctx.shade = 0.70f + 0.42f * lam;
            ctx.alpha = f.alphaScale;
            ctx.nx = f.nx;
            ctx.ny = f.ny;
            ctx.nz = f.nz;
            ctx.glint = frac(0.5f + f.nx * 0.9f - f.ny * 0.6f);
            g.pose().pushMatrix();
            g.pose().translate(ox, oy);
            g.pose().mul(new Matrix3x2f(ux, uy, vx, vy, 0f, 0f));
            f.p.paint(g, ctx, f.id, f.w, f.h);
            g.pose().popMatrix();
        }
        faces.clear();
    }

    public float[] extent() {
        float x0 = 1e9f, y0 = 1e9f, x1 = -1e9f, y1 = -1e9f;
        for (Face f : faces) {
            float k = D / (D + f.cz);
            float wu = f.w / (float) K, hu = f.h / (float) K;
            for (int i = 0; i < 4; i++) {
                float su = (i & 1) == 0 ? -0.5f : 0.5f, sv = (i & 2) == 0 ? -0.5f : 0.5f;
                float x = (f.cx + f.ux * wu * su + f.vx * hu * sv) * k;
                float y = (f.cy + f.uy * wu * su + f.vy * hu * sv) * k;
                x0 = Math.min(x0, x);
                x1 = Math.max(x1, x);
                y0 = Math.min(y0, y);
                y1 = Math.max(y1, y);
            }
        }
        return faces.isEmpty() ? new float[4] : new float[] {x0, y0, x1, y1};
    }

    public static float[] project(float x, float y, float z, float unit) {
        float k = unit * D / (D + z);
        return new float[] {x * k, y * k};
    }

    private static float frac(float x) {
        return x - (float) Math.floor(x);
    }
}
