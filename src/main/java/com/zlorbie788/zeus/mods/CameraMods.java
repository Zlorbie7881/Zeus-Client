package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Setting;
import com.zlorbie788.zeus.core.Zeus;
import com.zlorbie788.zeus.render.CameraControl;
import com.zlorbie788.zeus.ui.Accent;
import com.zlorbie788.zeus.ui.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.AABB;

public final class CameraMods {
    private CameraMods() {}

    public static final class Freecam extends Module {
        private final Setting.Num speed = add(new Setting.Num("Speed", "How fast the camera flies.", 1.0, 0.1, 5.0, 0.1, " b/t"));
        private final Setting.Num boost = add(new Setting.Num("Sprint boost", "How much faster you fly while holding sprint.", 2.5, 1.0, 6.0, 0.25, "x"));
        private final Setting.Num smooth = add(new Setting.Num("Smoothness", "How softly the camera starts and stops. Higher is smoother.", 0.6, 0.0, 0.9, 0.05, ""));
        private final Setting.Bool clip = add(new Setting.Bool("Fly through blocks", "Off: the camera is stopped by solid blocks like a spectator would not be.", true));
        private final Setting.Bool body = add(new Setting.Bool("Show your body", "Draws your own player where it stands.", true));
        private final Setting.Bool hurt = add(new Setting.Bool("Stop when you take damage", "Freecam switches itself off if your player is hurt, so you are never caught looking elsewhere.", true));
        private final Setting.Bool clicks = add(new Setting.Bool("Block attacks and use", "Left and right click do nothing while you fly around.", true));
        private final Setting.Num leash = add(new Setting.Num("Stop if my body is moved", "Freecam switches itself off if your player is pushed or teleported more than this, so the picture never shows somewhere your player is not. 0 turns it off.", 6, 0, 30, 1, " blocks"));
        private final Setting.Bool label = add(new Setting.Bool("On-screen label", "A small label with the camera position.", true));
        private final Setting.Info help = add(new Setting.Info("How it works", () -> java.util.List.of(
                "The camera flies, your player stays where it is.",
                "W A S D fly, Space up, Sneak down, Sprint faster, mouse looks.",
                "Your game sends the same as when you stand still: the keys are held back from your player, and so are attack and use.",
                "It switches itself off if you die, take damage, change world or your player is moved.",
                "Switching it off puts the camera back on your player.")));
        private double vx, vy, vz;
        private int ticks;
        private boolean failed;

        public Freecam() {
            super("freecam", "Freecam", "Fly the camera around while your player stays where it is.", Category.RENDER);
        }

        @Override
        protected void onEnable() {
            Minecraft mc = Minecraft.getInstance();
            var p = mc.player;
            if (p == null) return;
            var eye = p.getEyePosition(1f);
            CameraControl.begin(CameraControl.FREECAM, eye.x, eye.y, eye.z, p.getViewYRot(1f), p.getViewXRot(1f), true, clicks.on());
            vx = vy = vz = 0;
            ticks = 0;
            failed = false;
            FreeLook fl = Zeus.get(FreeLook.class);
            if (fl != null && fl.enabled()) fl.setEnabled(false);
        }

        @Override
        protected void onDisable() {
            CameraControl.end();
        }

        private void abort(String why) {
            failed = true;
            Zeus.toast("Freecam: " + why, false);
            setEnabled(false);
        }

        @Override
        protected void onTick(Minecraft mc) {
            var p = mc.player;
            if (p == null || mc.level == null || failed) return;
            ticks++;
            if (!CameraControl.stillHere()) {
                setEnabled(false);
                return;
            }
            if (p.isDeadOrDying()) {
                Zeus.toast("Freecam off: you died", false);
                setEnabled(false);
                return;
            }
            if (leash.i() > 0 && CameraControl.bodyMoved() > leash.i()) {
                Zeus.toast("Freecam off: your player was moved", false);
                setEnabled(false);
                return;
            }
            if (mc.screen != null) CameraControl.clearKeys();
            if (ticks == 12) {
                if (!CameraControl.cameraHooked()) {
                    abort("this game version's camera could not be reached, so it was switched off");
                    return;
                }
                if (!CameraControl.keysHooked()) {
                    abort("the movement keys could not be held back from your player, so it was switched off");
                    return;
                }
            }
            if (hurt.on() && p.hurtTime > 0) {
                Zeus.toast("Freecam off: you took damage", false);
                setEnabled(false);
                return;
            }
            CameraControl.snapshot();
            boolean[] k = mc.screen != null ? new boolean[7] : CameraControl.keys();
            double fwd = (k[0] ? 1 : 0) - (k[1] ? 1 : 0);
            double side = (k[3] ? 1 : 0) - (k[2] ? 1 : 0);
            double up = (k[4] ? 1 : 0) - (k[5] ? 1 : 0);
            double yaw = Math.toRadians(CameraControl.yaw());
            double pitch = Math.toRadians(CameraControl.pitch());
            double cp = Math.cos(pitch);
            double fx = -Math.sin(yaw) * cp;
            double fy = -Math.sin(pitch);
            double fz = Math.cos(yaw) * cp;
            double rx = -Math.cos(yaw);
            double rz = -Math.sin(yaw);
            double sp = speed.get() * (k[6] ? boost.get() : 1.0);
            double tx = (fx * fwd + rx * side) * sp;
            double ty = (fy * fwd + up) * sp;
            double tz = (fz * fwd + rz * side) * sp;
            double s = smooth.get();
            vx += (tx - vx) * (1 - s);
            vy += (ty - vy) * (1 - s);
            vz += (tz - vz) * (1 - s);
            if (Math.abs(vx) < 1e-4) vx = 0;
            if (Math.abs(vy) < 1e-4) vy = 0;
            if (Math.abs(vz) < 1e-4) vz = 0;
            if (!clip.on()) {
                AABB box = new AABB(CameraControl.x() - 0.2 + vx, CameraControl.y() - 0.2 + vy, CameraControl.z() - 0.2 + vz, CameraControl.x() + 0.2 + vx, CameraControl.y() + 0.2 + vy, CameraControl.z() + 0.2 + vz);
                if (!mc.level.noCollision(box)) {
                    vx = vy = vz = 0;
                }
            }
            CameraControl.move(vx, vy, vz);
        }

        @Override
        protected void onHud(GuiGraphics g, Minecraft mc, float partial, int w, int h) {
            if (!label.on() || mc.screen != null) return;
            String text = "FREECAM  " + Math.round(CameraControl.x()) + ", " + Math.round(CameraControl.y()) + ", " + Math.round(CameraControl.z());
            int tw = Draw.width(text);
            int x = w / 2 - tw / 2 - 8;
            int y = h - 62;
            Draw.rrect(g, x, y, tw + 16, 16, 7, 0xC0101522);
            Draw.rrectLine(g, x, y, tw + 16, 16, 7, Draw.rgba(Accent.main(), 0.7f));
            Draw.text(g, text, x + 8, y + 4, Accent.TEXT);
        }

        @Override
        public String info() {
            return speed.text();
        }
    }

    public static final class FreeLook extends Module {
        private final Setting.Bool snap = add(new Setting.Bool("Look back when switched off", "Off: the view jumps back to where your player faces. On: your player turns to where you were looking.", false));

        public FreeLook() {
            super("freelook", "Free Look", "Look around freely while your player keeps walking the same way.", Category.RENDER);
        }

        @Override
        protected void onEnable() {
            Minecraft mc = Minecraft.getInstance();
            var p = mc.player;
            if (p == null) return;
            Freecam fc = Zeus.get(Freecam.class);
            if (fc != null && fc.enabled()) {
                setEnabled(false);
                return;
            }
            CameraControl.begin(CameraControl.LOOK, 0, 0, 0, p.getViewYRot(1f), p.getViewXRot(1f), false, false);
        }

        @Override
        protected void onDisable() {
            Minecraft mc = Minecraft.getInstance();
            if (snap.on() && mc.player != null && CameraControl.mode() == CameraControl.LOOK) {
                mc.player.setYRot(CameraControl.yaw());
                mc.player.setXRot(CameraControl.pitch());
            }
            if (CameraControl.mode() == CameraControl.LOOK) CameraControl.end();
        }

        @Override
        protected void onTick(Minecraft mc) {
            CameraControl.snapshot();
            if (!CameraControl.turnHooked() && mc.player != null) {
            }
        }
    }
}
