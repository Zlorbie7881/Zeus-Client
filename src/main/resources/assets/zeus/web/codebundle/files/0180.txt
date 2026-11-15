package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Setting;
import com.zlorbie788.zeus.core.Zeus;
import com.zlorbie788.zeus.ui.Accent;
import com.zlorbie788.zeus.ui.Draw;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class PriceTags extends Module {
    private final Setting.Bool dealsOnly = add(new Setting.Bool("Deals only", "Hides the tags on items that are not under market.", false));
    private final Setting.Num dealAt = add(new Setting.Num("Deal from", "How far under the learned price an item has to be before it counts as a deal.", 20, 5, 90, 5, " %"));
    private final Setting.Bool prices = add(new Setting.Bool("Listing prices", "Shows the price written on a listing when no market price is known for it.", true));
    private final Setting.Bool values = add(new Setting.Bool("Market values", "Shows the learned market value when the listing has no readable price.", true));
    private final Setting.Info summary = addSetting(new Setting.Info("Price Tags: status", this::lines));

    private int deals;
    private double best;

    public PriceTags() {
        super("price_tags", "Price Tags", "Puts the learned market value, the profit and the discount on every item in an open container, so a good listing is obvious without reading a single line of lore.", Category.UTILITY);
        alias("price tags", "market tags", "auction values");
    }

    public static void render(GuiGraphics g, AbstractContainerMenu menu, int left, int top) {
        PriceTags self = Zeus.get(PriceTags.class);
        if (self != null) self.draw(g, menu, left, top);
    }

    private void draw(GuiGraphics g, AbstractContainerMenu menu, int left, int top) {
        deals = 0;
        best = 0;
        if (!enabled()) return;
        for (Slot slot : menu.slots) {
            ItemStack stack = slot.getItem();
            if (stack == null || stack.isEmpty()) continue;
            String name = stack.getHoverName().getString();
            double fair = AuctionMind.fair(name);
            double price = AhSniper.priceOf(stack);
            if (fair <= 0 && price <= 0) continue;
            String label;
            int colour;
            if (price > 0 && fair > 0) {
                double off = (1.0 - price / fair) * 100.0;
                boolean deal = off >= dealAt.f();
                if (deal) {
                    deals++;
                    if (off > best) best = off;
                }
                if (dealsOnly.on() && !deal) continue;
                label = (off >= 0 ? "+" : "-") + shortMoney(Math.abs(fair - price));
                colour = off >= 0 ? Accent.OK : Accent.BAD;
            } else if (price > 0) {
                if (dealsOnly.on() || !prices.on()) continue;
                label = shortMoney(price);
                colour = Accent.DIM;
            } else {
                if (dealsOnly.on() || !values.on()) continue;
                label = shortMoney(fair);
                colour = Accent.INFO;
            }
            paint(g, left + slot.x, top + slot.y, label, colour);
        }
    }

    private static void paint(GuiGraphics g, int x, int y, String label, int colour) {
        float scale = 0.62f;
        int w = Math.round(Draw.widthS(label, scale));
        g.fill(x + 1, y + 9, x + 16, y + 16, 0x70000000);
        Draw.textS(g, label, x + 15 - Math.min(14, w), y + 9, colour, scale);
    }

    private static String shortMoney(double value) {
        if (value >= 1e12) return trim(value / 1e12) + "t";
        if (value >= 1e9) return trim(value / 1e9) + "b";
        if (value >= 1e6) return trim(value / 1e6) + "m";
        if (value >= 1e3) return trim(value / 1e3) + "k";
        return Long.toString(Math.round(value));
    }

    private static String trim(double value) {
        return value >= 100 ? Long.toString(Math.round(value)) : String.format(Locale.ROOT, "%.1f", value);
    }

    private List<String> lines() {
        List<String> out = new ArrayList<>();
        out.add(deals > 0 ? deals + " deal(s) in view, best " + Math.round(best) + " % under market" : "No deals in the open window");
        out.add("Price book: " + AuctionMind.books() + " items, counted from listings and purchases");
        out.add("Deal from " + Math.round(dealAt.f()) + " % under market");
        return out;
    }

    @Override
    public String info() {
        return deals > 0 ? deals + " deals" : null;
    }
}
