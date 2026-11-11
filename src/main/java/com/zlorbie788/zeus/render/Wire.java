package com.zlorbie788.zeus.render;

import com.zlorbie788.zeus.ui.Draw;
import net.minecraft.client.gui.GuiGraphics;

public final class Wire {
    private Wire() {}

    private static final int[][] EDGES = {
            {0, 1}, {1, 3}, {3, 2}, {2, 0}, {4, 5}, {5, 7}, {7, 6}, {6, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};

    private static final float[] LINE = new float[4];

    public static void line(GuiGraphics g, double x1, double y1, double z1, double x2, double y2, double z2, float thick, int color, boolean halo) {
        if (!Projector.projectLine(x1, y1, z1, x2, y2, z2, LINE)) return;
        if (halo && !com.zlorbie788.zeus.ui.Perf.gameFast()) Stroke.keyline(g, LINE[0], LINE[1], LINE[2], LINE[3], thick, 0.45f * (color >>> 24) / 255f);
        Stroke.soft(g, LINE[0], LINE[1], LINE[2], LINE[3], thick, color);
    }

    private static final double[] CX = new double[8];
    private static final double[] CY = new double[8];
    private static final double[] CZ = new double[8];

    public static void box(GuiGraphics g, double x1, double y1, double z1, double x2, double y2, double z2, float thick, int color, boolean halo) {
        int i = 0;
        for (int xi = 0; xi < 2; xi++) {
            for (int yi = 0; yi < 2; yi++) {
                for (int zi = 0; zi < 2; zi++) {
                    CX[i] = xi == 0 ? x1 : x2;
                    CY[i] = yi == 0 ? y1 : y2;
                    CZ[i] = zi == 0 ? z1 : z2;
                    i++;
                }
            }
        }
        boolean keys = halo && !com.zlorbie788.zeus.ui.Perf.gameFast();
        float ka = 0.45f * (color >>> 24) / 255f;
        for (int[] e : EDGES) {
            if (Projector.projectLine(CX[e[0]], CY[e[0]], CZ[e[0]], CX[e[1]], CY[e[1]], CZ[e[1]], LINE)) {
                if (keys) Stroke.keyline(g, LINE[0], LINE[1], LINE[2], LINE[3], thick, ka);
                Stroke.soft(g, LINE[0], LINE[1], LINE[2], LINE[3], thick, color);
            }
        }
    }

    public static void floor(GuiGraphics g, double x, double y, double z, double size, float thick, int color) {
        line(g, x, y, z, x + size, y, z, thick, color, true);
        line(g, x + size, y, z, x + size, y, z + size, thick, color, true);
        line(g, x + size, y, z + size, x, y, z + size, thick, color, true);
        line(g, x, y, z + size, x, y, z, thick, color, true);
    }

    public static float[] bounds(double x1, double y1, double z1, double x2, double y2, double z2) {
        float[] t = new float[2];
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (int xi = 0; xi < 2; xi++) {
            for (int yi = 0; yi < 2; yi++) {
                for (int zi = 0; zi < 2; zi++) {
                    if (!Projector.project(xi == 0 ? x1 : x2, yi == 0 ? y1 : y2, zi == 0 ? z1 : z2, t)) return null;
                    minX = Math.min(minX, t[0]);
                    maxX = Math.max(maxX, t[0]);
                    minY = Math.min(minY, t[1]);
                    maxY = Math.max(maxY, t[1]);
                }
            }
        }
        return new float[] {minX, minY, maxX, maxY};
    }
}
