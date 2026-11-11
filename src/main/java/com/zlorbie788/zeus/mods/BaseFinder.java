package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.core.Setting;

public final class BaseFinder extends SuspiciousChunks {
    private final Setting.Num bigSize;

    public BaseFinder() {
        super("basefinder", "Advanced Base Finder", "Scores player-built bases using storage, redstone, workstations, activity and crop patterns; also lists likely mined geodes separately. Natural structures are filtered.", 2, 1);
        this.bigSize = addSetting(new Setting.Num("Big base from", "A find this many chunks wide counts as a big base (for the Discord message).", 4, 2, 12, 1, " chunks"));
    }

    @Override
    public boolean isHudWidget() {
        return false;
    }

    @Override
    protected void onHud(net.minecraft.client.gui.GuiGraphics g, net.minecraft.client.Minecraft mc, float partial, int w, int h) { }

    @Override
    protected boolean hudTracer() {
        return false;
    }

    @Override
    protected void found(net.minecraft.client.Minecraft mc, double x, double z, int chunks, int minY, int maxY, String kind, String structure) {
        if (chunks < bigSize.i()) return;
        DiscordWebhook.bigBase(mc, x, z, chunks, minY, maxY, kind);
    }
}
