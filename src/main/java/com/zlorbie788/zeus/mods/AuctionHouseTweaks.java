package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public final class AuctionHouseTweaks extends Module {
    private final AuctionBot seller = new AuctionBot();
    private final MarketHunter market = new MarketHunter();
    private final AhSniper sniper = new AhSniper();

    public AuctionHouseTweaks() {
        super("auction_house_tweaks", "Auction House Tweaks",
            "Auction listing workflow, learned-price bargain alerts, an in-game price graph and the AH Sniper: it reads every listing on the page, pages through the auction house, prices each one against your rule and the learned market value, buys the biggest discount, confirms it and checks the item actually arrived (off until you switch it on, with a spending limit).",
            Category.UTILITY);
        hudCard();
        for (Setting setting : seller.settings()) addSetting(setting);
        for (Setting setting : market.settings()) addSetting(setting);
        for (Setting setting : sniper.settings()) addSetting(setting);
    }

    public void configureMarketFromDashboard(String watchedItems, String priceCeiling, double bargainPercent) {
        market.configureFromDashboard(watchedItems, priceCeiling, bargainPercent);
    }

    @Override protected void onEnable() {
        seller.setEnabled(true);
        market.setEnabled(true);
        sniper.setEnabled(true);
    }

    @Override protected void onDisable() {
        seller.setEnabled(false);
        market.setEnabled(false);
        sniper.setEnabled(false);
    }

    @Override protected void onTick(Minecraft mc) {
        seller.tickFromComposite(mc);
        market.tickFromComposite(mc);
        sniper.tickFromComposite(mc);
    }

    @Override protected void onHud(GuiGraphics g, Minecraft mc, float partial, int w, int h) {
        market.hudFromComposite(g, mc, partial, w, h);
    }

    @Override public String info() {
        return market.enabled() ? market.info() : seller.info();
    }
}
