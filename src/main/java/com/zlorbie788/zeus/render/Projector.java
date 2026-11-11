package com.zlorbie788.zeus.render;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

public final class Projector {
    private Projector() {}

    private static double ex, ey, ez;
    private static double fx, fy, fz, rx, ry, rz, ux, uy, uz;
    private static double tanY;
    private static double aspect;
    private static int sw;
    private static int sh;
    private static boolean ready;

    private static volatile float liveFov;
    private static volatile long liveFovAt;
    private static volatile boolean hookSeen;

    public static boolean hookSeen() {
        return hookSeen;
    }

    public static void offerFov(float fov) {
        if (fov >= 1f && fov <= 179f) {
            liveFov = fov;
            liveFovAt = System.currentTimeMillis();
            hookSeen = true;
        }
    }

    public static double fov(Minecraft mc) {
        long now = System.currentTimeMillis();
        float live = liveFov;
        if (live > 0f && now - liveFovAt < 500L) return live;
        return mc.options.fov().get();
    }

    public static boolean prepare(Minecraft mc, float partial, int screenW, int screenH) {
        ready = false;
        Stroke.frame(mc.getFrameTimeNs());
        if (mc.player == null || screenW <= 0 || screenH <= 0) return false;
        boolean camPos = CameraControl.overridingPosition();
        boolean camLook = CameraControl.overridingLook();
        if (camPos || camLook) {
            Camera cam = mc.gameRenderer.getMainCamera();
            if (cam != null && cam.isInitialized()) {
                Vec3 p = cam.position();
                read(p.x, p.y, p.z, cam.forwardVector(), cam.upVector(), cam.leftVector());
            } else {
                double[] e = camPos ? CameraControl.eye(partial) : new double[] {
                        mc.player.getEyePosition(partial).x, mc.player.getEyePosition(partial).y, mc.player.getEyePosition(partial).z};
                double yawDeg = camLook ? CameraControl.yaw(partial) : mc.player.getViewYRot(partial);
                double pitchDeg = camLook ? CameraControl.pitch(partial) : mc.player.getViewXRot(partial);
                readAngles(e[0], e[1], e[2], yawDeg, pitchDeg);
            }
        } else {
            Camera cam = mc.gameRenderer.getMainCamera();
            if (cam != null && cam.isInitialized()) {
                Vec3 p = cam.position();
                read(p.x, p.y, p.z, cam.forwardVector(), cam.upVector(), cam.leftVector());
            } else {
                Vec3 eye = mc.player.getEyePosition(partial);
                readAngles(eye.x, eye.y, eye.z, mc.player.getViewYRot(partial), mc.player.getViewXRot(partial));
            }
        }
        double fov = fov(mc);
        tanY = Math.tan(Math.toRadians(fov) / 2.0);
        if (tanY < 1e-4) tanY = 1e-4;
        sw = screenW;
        sh = screenH;
        aspect = screenW / (double) screenH;
        ready = true;
        return true;
    }

    private static void read(double px, double py, double pz, Vector3fc f, Vector3fc up, Vector3fc left) {
        ex = px;
        ey = py;
        ez = pz;
        fx = f.x();
        fy = f.y();
        fz = f.z();
        ux = up.x();
        uy = up.y();
        uz = up.z();
        rx = -left.x();
        ry = -left.y();
        rz = -left.z();
        norm();
    }

    private static void readAngles(double px, double py, double pz, double yawDeg, double pitchDeg) {
        ex = px;
        ey = py;
        ez = pz;
        double yaw = Math.toRadians(yawDeg);
        double pitch = Math.toRadians(pitchDeg);
        double cp = Math.cos(pitch);
        fx = -Math.sin(yaw) * cp;
        fy = -Math.sin(pitch);
        fz = Math.cos(yaw) * cp;
        rx = -Math.cos(yaw);
        ry = 0;
        rz = -Math.sin(yaw);
        ux = ry * fz - rz * fy;
        uy = rz * fx - rx * fz;
        uz = rx * fy - ry * fx;
        norm();
    }

    private static void norm() {
        double fl = Math.sqrt(fx * fx + fy * fy + fz * fz);
        if (fl < 1e-6) {
            fx = 0;
            fy = 0;
            fz = 1;
        } else {
            fx /= fl;
            fy /= fl;
            fz /= fl;
        }
        double rl = Math.sqrt(rx * rx + ry * ry + rz * rz);
        if (rl < 1e-6) {
            rx = 1;
            ry = 0;
            rz = 0;
        } else {
            rx /= rl;
            ry /= rl;
            rz /= rl;
        }
        double ul = Math.sqrt(ux * ux + uy * uy + uz * uz);
        if (ul < 1e-6) {
            ux = 0;
            uy = 1;
            uz = 0;
        } else {
            ux /= ul;
            uy /= ul;
            uz /= ul;
        }
    }

    public static double eyeX() {
        return ex;
    }

    public static double eyeY() {
        return ey;
    }

    public static double eyeZ() {
        return ez;
    }

    public static boolean ray(float sx, float sy, double[] out) {
        if (!ready) return false;
        double cx = (sx - sw / 2.0) / (sw / 2.0) * tanY * aspect;
        double cy = (sh / 2.0 - sy) / (sh / 2.0) * tanY;
        double dx = fx + rx * cx + ux * cy;
        double dy = fy + ry * cx + uy * cy;
        double dz = fz + rz * cx + uz * cy;
        double l = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (l < 1e-9) return false;
        out[0] = dx / l;
        out[1] = dy / l;
        out[2] = dz / l;
        return true;
    }

    public static double depth(double x, double y, double z) {
        return (x - ex) * fx + (y - ey) * fy + (z - ez) * fz;
    }

    public static boolean project(double x, double y, double z, float[] out) {
        if (!ready) return false;
        double dx = x - ex;
        double dy = y - ey;
        double dz = z - ez;
        double cz = dx * fx + dy * fy + dz * fz;
        if (cz < 0.05) return false;
        double cx = dx * rx + dy * ry + dz * rz;
        double cy = dx * ux + dy * uy + dz * uz;
        out[0] = (float) (sw / 2.0 + (cx / cz) / (tanY * aspect) * sw / 2.0);
        out[1] = (float) (sh / 2.0 - (cy / cz) / tanY * sh / 2.0);
        return true;
    }

    public static boolean projectLine(double x1, double y1, double z1, double x2, double y2, double z2, float[] out) {
        double d1 = depth(x1, y1, z1);
        double d2 = depth(x2, y2, z2);
        double near = 0.1;
        if (d1 < near && d2 < near) return false;
        if (d1 < near) {
            double t = (near - d1) / (d2 - d1);
            x1 += (x2 - x1) * t;
            y1 += (y2 - y1) * t;
            z1 += (z2 - z1) * t;
        } else if (d2 < near) {
            double t = (near - d2) / (d1 - d2);
            x2 += (x1 - x2) * t;
            y2 += (y1 - y2) * t;
            z2 += (z1 - z2) * t;
        }
        float[] a = new float[2];
        float[] b = new float[2];
        if (!project(x1, y1, z1, a) || !project(x2, y2, z2, b)) return false;
        out[0] = a[0];
        out[1] = a[1];
        out[2] = b[0];
        out[3] = b[1];
        return true;
    }

    public static int screenPoint(double x, double y, double z, float margin, float[] out) {
        lastBehind = false;
        if (!ready) return -1;
        double dx = x - ex;
        double dy = y - ey;
        double dz = z - ez;
        double cz = dx * fx + dy * fy + dz * fz;
        double cx = dx * rx + dy * ry + dz * rz;
        double cy = dx * ux + dy * uy + dz * uz;
        double px;
        double py;
        if (cz > 0.05) {
            px = sw / 2.0 + (cx / cz) / (tanY * aspect) * sw / 2.0;
            py = sh / 2.0 - (cy / cz) / tanY * sh / 2.0;
            if (px >= margin && px <= sw - margin && py >= margin && py <= sh - margin) {
                out[0] = (float) px;
                out[1] = (float) py;
                return 0;
            }
        } else {
            double m = Math.hypot(cx, cy);
            double len3 = Math.sqrt(cx * cx + cy * cy + cz * cz);
            double q = len3 < 1e-9 ? 0.0 : Math.min(1.0, (m / len3) / 0.30);
            double s = q * q * (3.0 - 2.0 * q);
            double dirX = m > 1e-9 ? cx / m : 0.0;
            double dirY = m > 1e-9 ? -cy / m : 0.0;
            px = sw / 2.0 + (dirX * s + (1.0 - s)) * 1000.0;
            py = sh / 2.0 + (dirY * s) * 1000.0;
            lastBehind = true;
        }
        double vx = px - sw / 2.0;
        double vy = py - sh / 2.0;
        if (Math.abs(vx) < 1e-6 && Math.abs(vy) < 1e-6) vy = 1;
        double hx = sw / 2.0 - margin;
        double hy = sh / 2.0 - margin;
        double k = Math.min(hx / Math.max(1e-6, Math.abs(vx)), hy / Math.max(1e-6, Math.abs(vy)));
        out[0] = (float) (sw / 2.0 + vx * k);
        out[1] = (float) (sh / 2.0 + vy * k);
        return 1;
    }

    private static boolean lastBehind;

    public static boolean lastBehind() {
        return lastBehind;
    }

    public static int width() {
        return sw;
    }

    public static int height() {
        return sh;
    }
}
