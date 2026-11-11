package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Setting;
import com.zlorbie788.zeus.ui.Draw;
import com.zlorbie788.zeus.ui.Logo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;

public final class Zeuscope extends Module {
    private static Zeuscope self;

    private final Setting.Choice corner = Hud.corner(this, 1);

    public Zeuscope() {
        super("zeuscope", "Zeuscope", "A small Zeus logo with your coordinates, shown above the health bar.", Category.PANELS);
        self = this;
        anywhere();
        hudCard();
        onByDefault();
    }

    @Override
    protected void onHud(GuiGraphics g, Minecraft mc, float partial, int w, int h) {
        if (mc.player == null) return;

        Player p = mc.player;
        int[] pos = Hud.place(corner.index(), 130, 16, w, h);
        if (pos == null) return;

        int x = pos[0], y = pos[1];
        int logoSize = 10;
        int gap = 4;

        Logo.bolt(g, x, y + 3, logoSize, Hud.accent());

        int cx = x + logoSize + gap;
        int cy = y + 11;
        String coords = String.format("%d, %d", Math.round(p.getX()), Math.round(p.getZ()));
        Draw.text(g, coords, cx, cy, 0xFFE6EDFA);

        Hud.record(x, y, logoSize + gap + Draw.width(coords), 14);
    }
}
