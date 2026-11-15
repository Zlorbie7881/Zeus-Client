package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.ui.Accent;
import com.zlorbie788.zeus.core.Build;
import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Env;
import com.zlorbie788.zeus.core.Inv;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Setting;
import com.zlorbie788.zeus.core.Zeus;
import com.zlorbie788.zeus.mixin.MinecraftAccessor;
import com.zlorbie788.zeus.mixin.MultiPlayerGameModeAccessor;
import com.zlorbie788.zeus.render.Projector;
import com.zlorbie788.zeus.render.Wire;
import com.zlorbie788.zeus.ui.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class WorldMods {
    private WorldMods() {}

    public static final class FastBreak extends Module {
        private final Setting.Num speed = add(new Setting.Num("Mining speed", "How many times faster you break blocks on your screen. Servers put the block back if it breaks more than about 1.4x early, so that is the default.", 1.4, 1.1, 20, 0.1, "x"));
        private final Setting.Bool delay = add(new Setting.Bool("No pause between blocks", "Removes the short wait before the next block starts breaking.", true));

        public FastBreak() {
            super("fastbreak", "Fast Break", "Break blocks much faster.", Category.WORLD);
            speed.onChange(this::apply);
        }

        private void apply() {
            if (enabled()) Env.attribute("block_break_speed", "fastbreak", speed.get() - 1.0, "add_multiplied_total");
        }

        @Override
        protected void onEnable() {
            apply();
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (delay.on() && mc.gameMode != null) ((MultiPlayerGameModeAccessor) mc.gameMode).zeus$setDestroyDelay(0);
        }

        @Override
        protected void onDisable() {
            Env.attributeOff("block_break_speed", "fastbreak");
        }

        @Override
        protected void cleanSlate() {
            onDisable();
        }

        @Override
        public String info() {
            return speed.text();
        }
    }

    public static final class FastPlace extends Module {
        private final Setting.Num ticks = add(new Setting.Num("Delay", "Ticks between two placements. 4 is normal, 0 is as fast as the game can go.", 0, 0, 3, 1, " ticks"));

        public FastPlace() {
            super("fastplace", "Fast Place", "Place blocks and use items without the wait.", Category.WORLD);
        }

        @Override
        protected void onTick(Minecraft mc) {
            MinecraftAccessor acc = (MinecraftAccessor) mc;
            if (acc.zeus$getRightClickDelay() > ticks.i()) acc.zeus$setRightClickDelay(ticks.i());
        }
    }

    public static final class Nuker extends Module {
        private static final int PICK_A = 0;
        private static final int CONFIRM_A = 1;
        private static final int PICK_B = 2;
        private static final int CONFIRM_B = 3;
        private static final int SCAN = 4;
        private static final int CLEAR = 5;
        private static final int IDLE = 6;

        private static final int AMBER = Accent.WARN;
        private static final int GREEN = 0xFF52F28A;
        private static final int CYAN = 0xFF5FD4FF;
        private static final long CONFIRM_MS = 3000L;
        private static final long SCAN_MS = 1300L;

        private static final int MAX_BLOCKS = 12000;

        private final Setting.Num reach = add(new Setting.Num("Reach", "How far from your eyes a block can be and still be broken. The game's own limit is about 4.5 blocks; a little more is accepted by most servers.", 4.5, 3, 6, 0.5, " blocks"));
        private final Setting.Bool tools = add(new Setting.Bool("Use the best tool", "Switches to the fastest tool in your hotbar for each block, and back to the slot you had when the area is done.", true));
        private final Setting.Bool liquids = add(new Setting.Bool("Leave water and lava", "Never breaks into a block that holds a liquid.", true));
        private final Setting.Bool swing = add(new Setting.Bool("Swing hand", "Show the hand swing.", true));
        private final Setting.Action restart = add(new Setting.Action("Pick a new area", "Forgets the two corners and starts the selection over.", this::begin));

        private int phase = PICK_A;
        private long phaseAt;
        private BlockPos a;
        private BlockPos b;
        private BlockPos min;
        private BlockPos max;
        private final List<BlockPos> todo = new ArrayList<>();
        private BlockPos breaking;

        private int cursor;

        private int sortTicks;

        private int waiting;
        private boolean wasAttack;
        private boolean wasEnter;
        private int previousSlot = -1;

        public Nuker() {
            super("nuker", "Area Clearer", "Left click one corner, press enter to confirm it, do the same with a second corner, then it scans the box and breaks every block inside it with your best tool.", Category.WORLD);
        }

        private void begin() {
            phase = PICK_A;
            phaseAt = now();
            a = null;
            b = null;
            min = null;
            max = null;
            todo.clear();
            cursor = 0;
            sortTicks = 0;
            waiting = 0;
            breaking = null;
            previousSlot = -1;
        }

        @Override
        protected void onEnable() {
            begin();
            Zeus.toast("Area Clearer: left click the first corner", true);
        }

        @Override
        protected void onDisable() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gameMode != null) mc.gameMode.stopDestroyBlock();
            if (previousSlot >= 0 && tools.on()) Inv.select(previousSlot);
            breaking = null;
            todo.clear();
            min = null;
            max = null;
            phase = PICK_A;
        }

        private static long now() {
            return System.currentTimeMillis();
        }

        private static boolean key(int code) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getWindow() == null) return false;
            return GLFW.glfwGetKey(mc.getWindow().handle(), code) == GLFW.GLFW_PRESS;
        }

        private static BlockPos lookedAt(Minecraft mc) {
            if (mc.hitResult instanceof BlockHitResult bh && bh.getType() == HitResult.Type.BLOCK) return bh.getBlockPos();
            return null;
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (mc.player == null || mc.level == null || mc.gameMode == null) return;
            if (phase == IDLE || mc.screen != null) return;
            boolean attack = mc.options.keyAttack.isDown();
            boolean click = attack && !wasAttack;
            wasAttack = attack;
            boolean enter = key(GLFW.GLFW_KEY_ENTER) || key(GLFW.GLFW_KEY_KP_ENTER);
            boolean confirm = enter && !wasEnter;
            wasEnter = enter;
            long age = now() - phaseAt;
            BlockPos look = lookedAt(mc);
            switch (phase) {
                case PICK_A -> {
                    if (click && look != null) {
                        a = look;
                        phase = CONFIRM_A;
                        phaseAt = now();
                        mc.gameMode.stopDestroyBlock();
                    }
                }
                case CONFIRM_A -> {
                    if (confirm) {
                        phase = PICK_B;
                        phaseAt = now();
                        Zeus.toast("Corner 1 set — left click the second corner", true);
                    } else if (age > CONFIRM_MS) {
                        phase = PICK_A;
                        a = null;
                        phaseAt = now();
                    }
                }
                case PICK_B -> {
                    if (click && look != null && !look.equals(a)) {
                        b = look;
                        phase = CONFIRM_B;
                        phaseAt = now();
                        mc.gameMode.stopDestroyBlock();
                    }
                }
                case CONFIRM_B -> {
                    if (confirm) {
                        if (measure(mc)) {
                            phase = SCAN;
                            phaseAt = now();
                        } else {
                            phase = PICK_A;
                            phaseAt = now();
                            a = null;
                            b = null;
                        }
                    } else if (age > CONFIRM_MS) {
                        phase = PICK_B;
                        b = null;
                        phaseAt = now();
                    }
                }
                case SCAN -> {
                    if (age > SCAN_MS) {
                        phase = CLEAR;
                        phaseAt = now();
                        Zeus.toast("Clearing " + todo.size() + " blocks", true);
                    }
                }
                case CLEAR -> clear(mc);
                default -> { }
            }
        }

        private boolean measure(Minecraft mc) {
            if (a == null || b == null || mc.level == null) return false;
            min = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
            max = new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
            long count = (long) (max.getX() - min.getX() + 1) * (max.getY() - min.getY() + 1) * (max.getZ() - min.getZ() + 1);
            if (count > MAX_BLOCKS) {
                Zeus.toast("That area is too big (" + count + " blocks, limit " + MAX_BLOCKS + ")", false);
                return false;
            }
            todo.clear();
            for (BlockPos p : BlockPos.betweenClosed(min, max)) {
                BlockState st = mc.level.getBlockState(p);
                if (st.isAir()) continue;
                if (st.getDestroySpeed(mc.level, p) < 0f) continue;
                if (liquids.on() && !st.getFluidState().isEmpty()) continue;
                todo.add(p.immutable());
            }
            BlockPos eye = mc.player == null ? BlockPos.ZERO : mc.player.blockPosition();
            todo.sort(Comparator.comparingDouble(p -> p.distSqr(eye)));
            cursor = 0;
            return true;
        }

        private void clear(Minecraft mc) {
            if (mc.player == null || mc.level == null || mc.gameMode == null) return;
            if (++sortTicks >= 20 && cursor < todo.size()) {
                sortTicks = 0;
                BlockPos eye = mc.player.blockPosition();
                List<BlockPos> rest = new ArrayList<>(todo.subList(cursor, todo.size()));
                rest.sort(Comparator.comparingDouble(p -> p.distSqr(eye)));
                for (int i = 0; i < rest.size(); i++) todo.set(cursor + i, rest.get(i));
            }
            BlockPos target = null;
            while (cursor < todo.size()) {
                BlockPos p = todo.get(cursor);
                BlockState st = mc.level.getBlockState(p);
                if (st.isAir() || st.getDestroySpeed(mc.level, p) < 0f
                        || mc.player.getEyePosition().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(p)) > reach.f()) {
                    cursor++;
                    continue;
                }
                target = p;
                break;
            }
            if (target == null) {
                if (cursor < todo.size()) {
                    if (mc.gameMode != null) mc.gameMode.stopDestroyBlock();
                    breaking = null;
                    waiting = todo.size() - cursor;
                    return;
                }
                if (mc.gameMode != null) mc.gameMode.stopDestroyBlock();
                breaking = null;
                if (previousSlot >= 0 && tools.on()) Inv.select(previousSlot);
                previousSlot = -1;
                begin();
                Zeus.toast("Area cleared", true);
                return;
            }
            waiting = 0;
            if (tools.on()) chooseTool(mc, target);
            Direction face = facing(mc, target);
            if (!target.equals(breaking)) {
                mc.gameMode.startDestroyBlock(target, face);
                breaking = target;
            } else {
                mc.gameMode.continueDestroyBlock(target, face);
            }
            if (swing.on()) mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        }

        private static Direction facing(Minecraft mc, BlockPos p) {
            double dx = mc.player.getX() - (p.getX() + 0.5);
            double dy = mc.player.getEyeY() - (p.getY() + 0.5);
            double dz = mc.player.getZ() - (p.getZ() + 0.5);
            double ax = Math.abs(dx), ay = Math.abs(dy), az = Math.abs(dz);
            if (ay >= ax && ay >= az) return dy > 0 ? Direction.UP : Direction.DOWN;
            if (ax >= az) return dx > 0 ? Direction.EAST : Direction.WEST;
            return dz > 0 ? Direction.SOUTH : Direction.NORTH;
        }

        private void chooseTool(Minecraft mc, BlockPos p) {
            if (mc.player == null || mc.level == null) return;
            BlockState st = mc.level.getBlockState(p);
            int best = Inv.selected();
            float speed = Inv.get(best).getDestroySpeed(st);
            for (int i = 0; i < 9; i++) {
                float s = Inv.get(i).getDestroySpeed(st);
                if (s > speed + 0.01f) {
                    speed = s;
                    best = i;
                }
            }
            if (best != Inv.selected()) {
                if (previousSlot < 0) previousSlot = Inv.selected();
                Inv.select(best);
            }
        }

        @Override
        public String info() {
            return switch (phase) {
                case CONFIRM_A, CONFIRM_B -> "confirm";
                case SCAN -> "scanning";
                case CLEAR -> Math.max(0, todo.size() - cursor) + " left";
                default -> null;
            };
        }

        private static void corner(GuiGraphics g, BlockPos p, int colour, float thick) {
            double x = p.getX(), y = p.getY(), z = p.getZ();
            Wire.box(g, x + 0.02, y + 0.02, z + 0.02, x + 0.98, y + 0.98, z + 0.98, thick, colour, true);
        }

        private static void filled(GuiGraphics g, BlockPos p, int colour, float thick) {
            double x = p.getX(), y = p.getY(), z = p.getZ();
            corner(g, p, colour, thick);
            int inner = Draw.rgba(colour, 0.55f);
            for (int i = 1; i < 3; i++) {
                double k = i / 3.0;
                Wire.line(g, x + 0.04, y + k, z + 0.04, x + 0.96, y + k, z + 0.04, thick * 0.8f, inner, false);
                Wire.line(g, x + 0.04, y + k, z + 0.96, x + 0.96, y + k, z + 0.96, thick * 0.8f, inner, false);
                Wire.line(g, x + 0.04, y + k, z + 0.04, x + 0.04, y + k, z + 0.96, thick * 0.8f, inner, false);
                Wire.line(g, x + 0.96, y + k, z + 0.04, x + 0.96, y + k, z + 0.96, thick * 0.8f, inner, false);
            }
        }

        @Override
        protected void onHud(GuiGraphics g, Minecraft mc, float partial, int w, int h) {
            if (mc.player == null || mc.level == null || mc.screen != null) return;
            long age = now() - phaseAt;
            float thick = 1.7f;
            if (!Projector.prepare(mc, partial, w, h)) return;
            boolean picking = phase == PICK_A || phase == PICK_B;
            if (picking) {
                BlockPos look = lookedAt(mc);
                if (look != null && !look.equals(a)) {
                    bracket(g, look, AMBER, thick * 1.1f);
                    if (a != null) {
                        BlockPos lo = new BlockPos(Math.min(a.getX(), look.getX()), Math.min(a.getY(), look.getY()), Math.min(a.getZ(), look.getZ()));
                        BlockPos hi = new BlockPos(Math.max(a.getX(), look.getX()), Math.max(a.getY(), look.getY()), Math.max(a.getZ(), look.getZ()));
                        Wire.box(g, lo.getX(), lo.getY(), lo.getZ(), hi.getX() + 1, hi.getY() + 1, hi.getZ() + 1, thick * 0.8f, Draw.rgba(CYAN, 0.35f), false);
                    }
                }
            }
            if (phase == CLEAR && breaking != null) corner(g, breaking, 0xFFFFFFFF, thick * 1.1f);
            if (a != null) filled(g, a, phase == CONFIRM_A ? AMBER : GREEN, thick);
            if (b != null) filled(g, b, phase == CONFIRM_B ? AMBER : GREEN, thick);
            if (min != null && max != null && phase >= SCAN) {
                Wire.box(g, min.getX(), min.getY(), min.getZ(), max.getX() + 1, max.getY() + 1, max.getZ() + 1, thick, Draw.rgba(GREEN, 0.55f), true);
            }
            if (phase == SCAN && min != null && max != null) {
                float t = Math.min(1f, age / (float) SCAN_MS);
                double[] p = scanPlane(t);
                Wire.box(g, p[0], p[1], p[2], p[3], p[4], p[5], 2.2f, CYAN, true);
                for (int i = 1; i <= 4; i++) {
                    double[] q = scanPlane(Math.max(0f, t - i * 0.055f));
                    Wire.box(g, q[0], q[1], q[2], q[3], q[4], q[5], 1.1f, Draw.rgba(CYAN, 0.30f - i * 0.06f), false);
                }
            }
            drawPrompt(g, mc, w, h, age);
        }

        private double[] scanPlane(float t) {
            double x1 = min.getX(), y1 = min.getY(), z1 = min.getZ();
            double x2 = max.getX() + 1, y2 = max.getY() + 1, z2 = max.getZ() + 1;
            double sx = x2 - x1, sy = y2 - y1, sz = z2 - z1;
            if (sx >= sy && sx >= sz) {
                double x = x1 + sx * t;
                return new double[] {x, y1, z1, x + 0.03, y2, z2};
            }
            if (sy >= sz) {
                double y = y1 + sy * t;
                return new double[] {x1, y, z1, x2, y + 0.03, z2};
            }
            double z = z1 + sz * t;
            return new double[] {x1, y1, z, x2, y2, z + 0.03};
        }

        private void drawPrompt(GuiGraphics g, Minecraft mc, int w, int h, long age) {
            String title;
            String hint;
            int colour;
            boolean cap = false;
            float bar = -1f;
            if (phase == CONFIRM_A || phase == CONFIRM_B) {
                cap = true;
                title = "Confirm corner " + (phase == CONFIRM_A ? "1" : "2");
                BlockPos c = phase == CONFIRM_A ? a : b;
                hint = c == null ? "" : "at " + c.getX() + " " + c.getY() + " " + c.getZ();
                colour = AMBER;
                bar = Math.max(0f, 1f - age / (float) CONFIRM_MS);
            } else if (phase == PICK_A) {
                title = "Left click a corner";
                hint = "of the area to clear";
                colour = AMBER;
            } else if (phase == PICK_B) {
                title = "Left click the second corner";
                hint = "corner 1 is set";
                colour = GREEN;
            } else if (phase == SCAN) {
                title = "Scanning the area";
                hint = Math.round(Math.min(1f, age / (float) SCAN_MS) * 100) + " %";
                colour = CYAN;
                bar = Math.min(1f, age / (float) SCAN_MS);
            } else if (phase == CLEAR) {
                if (waiting > 0) {
                    title = "Walk closer";
                    hint = waiting + " block" + (waiting == 1 ? "" : "s") + " out of reach";
                    colour = AMBER;
                } else {
                    title = "Clearing";
                    hint = Math.max(0, todo.size() - cursor) + " blocks left";
                    colour = GREEN;
                }
                if (!todo.isEmpty()) bar = Math.min(1f, cursor / (float) Math.max(1, todo.size()));
            } else {
                return;
            }
            int padX = 13;
            int capW = cap ? Draw.widthBold("ENTER") + 16 : 0;
            int textW = Draw.widthBold(title) + (cap ? capW + 9 : 0);
            int bw = Math.max(Math.max(textW, Draw.width(hint)) + padX * 2, 178);
            int bh = bar >= 0f ? 45 : 39;
            int x = (w - bw) / 2;
            int y = h - 104;
            Draw.shadow(g, x, y, bw, bh, 9, 14, 0.6f);
            Draw.rrect(g, x, y, bw, bh, 9, 0xE00A0E17, 0xF20C1322);
            Draw.rrect(g, x + 1, y + 1, bw - 2, 1, 1, Draw.rgba(0xFFFFFFFF, 0.07f));
            Draw.rrectLine(g, x, y, bw, bh, 9, Draw.rgba(colour, 0.45f));
            Draw.rrect(g, x + 2, y + 9, 2, bh - 18, 1, Draw.rgba(colour, 0.9f));
            int ty = y + 9;
            if (cap) {
                keycap(g, x + padX, ty, "ENTER", colour);
                Draw.bold(g, title, x + padX + capW + 9, ty + 2, 0xFFF3F7FF);
            } else {
                Draw.boldCenter(g, title, x + bw / 2, ty, 0xFFF3F7FF);
            }
            Draw.textCenter(g, hint, x + bw / 2, ty + 15, Draw.rgba(colour, 0.92f));
            if (bar >= 0f) {
                int bx = x + padX;
                int bwd = bw - padX * 2;
                Draw.rrect(g, bx, y + bh - 8, bwd, 3, 2, Draw.rgba(0xFFFFFFFF, 0.10f));
                Draw.rrect(g, bx, y + bh - 8, Math.max(2, Math.round(bwd * bar)), 3, 2, Draw.rgba(colour, 0.9f));
            }
        }

        private static int keycap(GuiGraphics g, int x, int y, String text, int colour) {
            int cw = Draw.widthBold(text) + 16;
            int ch = 17;
            Draw.rrect(g, x, y + 2, cw, ch, 4, Draw.rgba(colour, 0.35f));
            Draw.rrect(g, x, y, cw, ch - 1, 4, 0xF01A2130, 0xE6162030);
            Draw.rrectLine(g, x, y, cw, ch - 1, 4, Draw.rgba(colour, 0.65f));
            Draw.boldCenter(g, text, x + cw / 2, y + 5, 0xFFF3F7FF);
            return cw;
        }

        private static void bracket(GuiGraphics g, BlockPos p, int colour, float thick) {
            double x = p.getX(), y = p.getY(), z = p.getZ();
            double e = 0.98, l = 0.30;
            for (int xi = 0; xi < 2; xi++) {
                for (int yi = 0; yi < 2; yi++) {
                    for (int zi = 0; zi < 2; zi++) {
                        double cx = x + (xi == 0 ? 0.02 : e);
                        double cy = y + (yi == 0 ? 0.02 : e);
                        double cz = z + (zi == 0 ? 0.02 : e);
                        double dx = xi == 0 ? l : -l;
                        double dy = yi == 0 ? l : -l;
                        double dz = zi == 0 ? l : -l;
                        Wire.line(g, cx, cy, cz, cx + dx, cy, cz, thick, colour, true);
                        Wire.line(g, cx, cy, cz, cx, cy + dy, cz, thick, colour, true);
                        Wire.line(g, cx, cy, cz, cx, cy, cz + dz, thick, colour, true);
                    }
                }
            }
        }

        private static String coordinates(BlockPos p) {
            return p == null ? "" : p.getX() + " " + p.getY() + " " + p.getZ();
        }
    }
}
