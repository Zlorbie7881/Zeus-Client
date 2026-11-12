package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Setting;
import com.zlorbie788.zeus.core.Zeus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MarketHunter extends Module {
    private static final Pattern CURRENCY_PRICE = Pattern.compile(
        "(?i)(?:\\$\\s*|\\b)([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*([kmb])?\\s*(?:coins?|coin|dollars?|usd|\\$)");
    private static final Pattern NUMBER = Pattern.compile(
        "(?i)([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*([kmb])?");

    private final Setting.Text watch = add(new Setting.Text(
        "Watched items", "Comma-separated item-name keywords.",
        "elytra,mace,netherite,diamond", 180));
    private final Setting.Text windowNames = add(new Setting.Text(
        "Auction window title", "Comma-separated title keywords used to recognize your Auction House.",
        "auction,market,bazaar,ah", 100));
    private final Setting.Text maxPrice = add(new Setting.Text(
        "Maximum price", "Optional absolute price ceiling. 0 disables this filter.",
        "0", 16));
    private final Setting.Num scanInterval = add(new Setting.Num(
        "Scan interval", "Seconds between scans of the currently open listing window.",
        1.0, 0.25, 10.0, 0.25, " s"));
    private final Setting.Num discount = add(new Setting.Num(
        "Bargain threshold", "Alert when a listing is this percentage below its learned average.",
        20, 1, 90, 1, "%"));
    private final Setting.Num minSamples = add(new Setting.Num(
        "Minimum price samples", "How many unique observed prices are required before bargain alerts.",
        3, 2, 12, 1, ""));
    private final Setting.Num historySize = add(new Setting.Num(
        "History per item", "Maximum unique prices retained for each item during this session.",
        30, 5, 100, 5, ""));
    private final Setting.Bool alertUnpriced = add(new Setting.Bool(
        "Alert on unpriced items", "Notify for watched items whose listing price cannot be read.", false));
    private final Setting.Bool notifyAllMatches = add(new Setting.Bool(
        "Notify on every match", "Notify for every new watched listing, not only learned bargains.", false));
    private final Setting.Bool priceGraph = add(new Setting.Bool(
        "In-game price graph", "Draw the most-observed item's learned prices and a simple trend estimate on the HUD.", true));
    private final Setting.Info dashboard = addSetting(new Setting.Info("Market Intelligence", this::dashboardLines));
    private final Setting.Action openBestDeal = addSetting(new Setting.Action(
        "Open best bargain listing", "Opens the strongest bargain currently visible in the Auction House. This is a single user-triggered click; Zeus never confirms or completes a purchase for you.", this::openBestListing));
    private final Setting.Action clearHistory = addSetting(new Setting.Action(
        "Clear price history", "Clear the session's learned price samples and alert de-duplication.", this::clearData));

    private final Map<String, Deque<Double>> prices = new LinkedHashMap<>();
    private final Set<String> sampledPrices = new LinkedHashSet<>();
    private final Set<String> alertedListings = new HashSet<>();
    private final Deque<String> recentDeals = new ArrayDeque<>();
    private int ticks;
    private int totalScans;
    private int listingsSeen;
    private int matches;
    private int bargains;
    private int alerts;
    private String last = "Open the server Auction House to begin scanning.";
    private int bestSlot = -1;
    private int bestContainerId = -1;
    private double bestPrice = -1;
    private double bestDiscount;
    private Object bestItem;
    private int bestCount;
    private String bestName = "";

    public MarketHunter() {
        super("market_hunter", "Market Hunter",
            "Read-only market scanner with session price history, bargain detection and a live dashboard.",
            Category.UTILITY);
    }

    public void configureFromDashboard(String watchedItems, String priceCeiling, double bargainPercent) {
        watch.set(watchedItems == null ? "" : watchedItems);
        maxPrice.set(priceCeiling == null ? "0" : priceCeiling);
        discount.set(bargainPercent);
        last = "Auction watch settings updated from the local dashboard.";
    }

    private List<String> dashboardLines() {
        List<String> lines = new ArrayList<>();
        lines.add(last);
        lines.add("Scans: " + totalScans + " · listings: " + listingsSeen + " · matches: " + matches);
        lines.add("Bargains: " + bargains + " · alerts: " + alerts + " · tracked items: " + prices.size());
        int shown = 0;
        for (String deal : recentDeals) {
            if (shown++ == 4) break;
            lines.add(deal);
        }
        if (recentDeals.isEmpty()) lines.add("No bargain signals yet. More price observations improve accuracy.");
        Map.Entry<String, Deque<Double>> trend = mostObserved();
        if (trend != null && trend.getValue().size() >= 2) {
            double first = trend.getValue().getFirst();
            double latest = trend.getValue().getLast();
            double change = first == 0 ? 0 : (latest - first) / first * 100.0;
            lines.add("Trend estimate: " + (change > 2 ? "up" : change < -2 ? "down" : "flat")
                + " · " + String.format(Locale.ROOT, "%.1f", change) + "% across observed samples");
            lines.add("Reason: latest observed price compared with oldest sample · low confidence");
        }
        if (bestSlot >= 0) {
            lines.add("Best visible bargain: " + bestName + " · " + money(bestPrice)
                + " · " + String.format(Locale.ROOT, "%.0f", bestDiscount) + "% below average");
            lines.add("Use Open best bargain listing to inspect it; purchase confirmation stays manual.");
        } else {
            lines.add("No current bargain candidate · purchases are always manual.");
        }
        return lines;
    }

    @Override
    protected void onEnable() {
        ticks = totalScans = listingsSeen = matches = bargains = alerts = 0;
        last = "Market Hunter enabled. Waiting for an Auction House.";
    }

    @Override
    protected void onDisable() {
        last = "Paused · " + alerts + " alerts this session.";
    }

    @Override
    public String info() {
        return enabled() ? Integer.toString(bargains) + " deals" : null;
    }

    private void clearData() {
        prices.clear();
        sampledPrices.clear();
        alertedListings.clear();
        recentDeals.clear();
        matches = bargains = alerts = 0;
        clearBestListing();
        last = "Price history cleared.";
        Zeus.toast("Market Hunter: price history cleared", true);
    }

    @Override
    protected void onTick(Minecraft mc) {
        if (mc.player == null || mc.screen == null || mc.player.containerMenu == null) {
            clearBestListing();
            last = "Open the server Auction House to scan visible listings.";
            return;
        }

        String title = mc.screen.getTitle().getString().toLowerCase(Locale.ROOT);
        if (!matchesWindow(title)) {
            clearBestListing();
            last = "Waiting for Auction House window · current: " + mc.screen.getTitle().getString();
            return;
        }

        int every = Math.max(5, (int)Math.round(scanInterval.get() * 20.0));
        if (++ticks < every) return;
        ticks = 0;
        totalScans++;

        AbstractContainerMenu menu = mc.player.containerMenu;
        int listingSlots = Math.max(0, menu.slots.size() - 36);
        if (listingSlots == 0) {
            last = "Auction window found, but no listing slots were detected.";
            return;
        }

        int currentMatches = 0;
        int currentDeals = 0;
        clearBestListing();
        for (int i = 0; i < listingSlots; i++) {
            ItemStack stack = menu.slots.get(i).getItem();
            if (stack == null || stack.isEmpty()) continue;
            listingsSeen++;

            String itemName = stack.getHoverName().getString();
            String fullText = itemName + " " + loreText(stack);
            String keyword = matchingKeyword(fullText.toLowerCase(Locale.ROOT));
            if (keyword == null) continue;
            currentMatches++;
            matches++;

            double price = findPrice(fullText);
            if (price < 0) {
                if (alertUnpriced.on()) alertOnce(menu, i, stack, itemName, -1, "price unreadable");
                continue;
            }

            double ceiling = parsePrice(maxPrice.get());
            if (ceiling > 0 && price > ceiling) continue;

            String itemKey = normalize(itemName);
            Deque<Double> history = prices.computeIfAbsent(itemKey, k -> new ArrayDeque<>());
            double averageBefore = average(history);
            int samplesBefore = history.size();

            String sampleKey = itemKey + "|" + Math.round(price * 100.0);
            if (sampledPrices.add(sampleKey)) {
                history.addLast(price);
                while (history.size() > (int)historySize.get()) history.removeFirst();
                if (sampledPrices.size() > 2500) sampledPrices.clear();
                learn(itemKey, price, (int) historySize.get());
            }

            boolean enoughHistory = samplesBefore >= (int)minSamples.get();
            double drop = averageBefore > 0 ? (averageBefore - price) / averageBefore * 100.0 : 0;
            boolean bargain = enoughHistory && drop >= discount.get();
            if (bargain) {
                currentDeals++;
                bargains++;
                if (bestSlot < 0 || drop > bestDiscount) {
                    bestSlot = i;
                    bestContainerId = menu.containerId;
                    bestPrice = price;
                    bestDiscount = drop;
                    bestItem = stack.getItem();
                    bestCount = stack.getCount();
                    bestName = itemName;
                }
                String summary = itemName + " · " + money(price) + " (" +
                    String.format(Locale.ROOT, "%.0f", drop) + "% below avg)";
                rememberDeal(summary);
                alertOnce(menu, i, stack, itemName, price, summary);
            } else if (notifyAllMatches.on()) {
                alertOnce(menu, i, stack, itemName, price, itemName + " · " + money(price));
            }
        }

        last = "Scanning " + listingSlots + " slots · " + currentMatches +
            " watched matches · " + currentDeals + " bargain signals.";
    }

    private void clearBestListing() {
        bestSlot = -1;
        bestContainerId = -1;
        bestPrice = -1;
        bestDiscount = 0;
        bestItem = null;
        bestCount = 0;
        bestName = "";
    }

    private void openBestListing() {
        Minecraft mc = Minecraft.getInstance();
        if (bestSlot < 0 || mc.player == null || mc.gameMode == null || mc.screen == null) {
            Zeus.toast("No current bargain listing to open", false);
            return;
        }
        if (!matchesWindow(mc.screen.getTitle().getString().toLowerCase(Locale.ROOT))) {
            Zeus.toast("Open the Auction House listing window first", false);
            return;
        }
        AbstractContainerMenu menu = mc.player.containerMenu;
        if (menu == null || menu.containerId != bestContainerId || bestSlot >= menu.slots.size()) {
            Zeus.toast("Auction listing changed · scan the page again", false);
            clearBestListing();
            return;
        }
        ItemStack current = menu.slots.get(bestSlot).getItem();
        if (current.isEmpty() || current.getItem() != bestItem || current.getCount() != bestCount
                || !current.getHoverName().getString().equals(bestName)) {
            Zeus.toast("Bargain listing changed · scan the page again", false);
            clearBestListing();
            return;
        }
        mc.gameMode.handleInventoryMouseClick(menu.containerId, bestSlot, 0, ClickType.PICKUP, mc.player);
        last = "Opened " + bestName + " for manual review at " + money(bestPrice) + ". Confirm any purchase yourself.";
        Zeus.toast("Opened bargain listing · review before buying", true);
    }

    private Map.Entry<String, Deque<Double>> mostObserved() {
        Map.Entry<String, Deque<Double>> best = null;
        for (Map.Entry<String, Deque<Double>> entry : prices.entrySet()) {
            if (best == null || entry.getValue().size() > best.getValue().size()) best = entry;
        }
        return best;
    }

    @Override
    protected void onHud(GuiGraphics g, Minecraft mc, float partial, int w, int h) {
        if (!priceGraph.on()) return;
        Map.Entry<String, Deque<Double>> entry = mostObserved();
        if (entry == null || entry.getValue().size() < 2) return;
        List<Double> values = new ArrayList<>(entry.getValue());
        int width = 154, height = 62, x = 8, y = h - height - 8;
        Hud.record(x, y, width, height);
        g.fill(x, y, x + width, y + height, 0xB0101420);
        g.fill(x, y, x + 2, y + height, 0xFF4D9EFF);
        g.drawString(mc.font, "MARKET · " + entry.getKey(), x + 7, y + 5, 0xFFE8F1FF, false);
        double min = values.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        double max = values.stream().mapToDouble(Double::doubleValue).max().orElse(min + 1);
        if (max - min < 0.0001) max = min + 1;
        int chartX = x + 7, chartY = y + 20, chartW = width - 14, chartH = 30;
        for (int i = 1; i < values.size(); i++) {
            int x1 = chartX + (i - 1) * chartW / Math.max(1, values.size() - 1);
            int x2 = chartX + i * chartW / Math.max(1, values.size() - 1);
            int y1 = chartY + chartH - (int)Math.round((values.get(i - 1) - min) / (max - min) * chartH);
            int y2 = chartY + chartH - (int)Math.round((values.get(i) - min) / (max - min) * chartH);
            int top = Math.min(y1, y2), bottom = Math.max(y1, y2);
            g.fill(x1, top, Math.max(x1 + 1, x2 + 1), bottom + 2, 0xFF55D6A2);
        }
        double first = values.get(0), last = values.get(values.size() - 1);
        double pct = first == 0 ? 0 : (last - first) / first * 100.0;
        String trend = pct > 2 ? "↑" : pct < -2 ? "↓" : "→";
        g.drawString(mc.font, "Now " + money(last) + "  " + trend + String.format(Locale.ROOT, "%.1f%%", pct), x + 7, y + 51, 0xFFD5E2F5, false);
    }

    private boolean matchesWindow(String title) {
        for (String candidate : windowNames.get().toLowerCase(Locale.ROOT).split(",")) {
            String keyword = candidate.trim();
            if (!keyword.isEmpty() && title.contains(keyword)) return true;
        }
        return false;
    }

    private void alertOnce(AbstractContainerMenu menu, int slot, ItemStack stack,
                           String itemName, double price, String message) {
        String key = menu.containerId + "|" + slot + "|" + stack.getItem() + "|" +
            stack.getCount() + "|" + normalize(itemName) + "|" + price;
        if (!alertedListings.add(key)) return;
        alerts++;
        last = message;
        Zeus.toast("Market Hunter: " + message, true);
        if (alertedListings.size() > 1200) {
                alertedListings.clear();
        }
    }

    private void rememberDeal(String deal) {
        recentDeals.remove(deal);
        recentDeals.addFirst(deal);
        while (recentDeals.size() > 8) recentDeals.removeLast();
    }

    private String matchingKeyword(String text) {
        for (String word : watch.get().toLowerCase(Locale.ROOT).split(",")) {
            String keyword = word.trim();
            if (!keyword.isEmpty() && text.contains(keyword)) return keyword;
        }
        return null;
    }

    private static final java.util.regex.Pattern CLEAN = java.util.regex.Pattern.compile("[^a-z0-9]+");

    private static String normalize(String value) {
        return CLEAN.matcher(value.toLowerCase(Locale.ROOT)).replaceAll(" ").trim();
    }

    private static final Map<String, Deque<Double>> LEARNED = new java.util.concurrent.ConcurrentHashMap<>();
    private static final int LEARNED_MAX = 4000;

    private static String norm(String name) {
        return name == null ? "" : CLEAN.matcher(name.toLowerCase(Locale.ROOT)).replaceAll(" ").trim();
    }

    static void learn(String name, double price, int keep) {
        String key = norm(name);
        if (key.isEmpty() || price <= 0) return;
        AuctionMind.observe(name, price);
        Deque<Double> list = LEARNED.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (list) {
            list.addLast(price);
            while (list.size() > Math.max(5, keep)) list.removeFirst();
        }
        if (LEARNED.size() > LEARNED_MAX) {
            LEARNED.entrySet().removeIf(e -> {
                synchronized (e.getValue()) {
                    return e.getValue().isEmpty();
                }
            });
            if (LEARNED.size() > LEARNED_MAX) LEARNED.clear();
        }
    }

    static double learnedFor(String name) {
        Deque<Double> list = LEARNED.get(norm(name));
        if (list == null) return -1;
        synchronized (list) {
            if (list.size() < 3) return -1;
            double sum = 0;
            for (double v : list) sum += v;
            return sum / list.size();
        }
    }

    static String loreText(ItemStack stack) {
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) return "";
        StringBuilder out = new StringBuilder();
        for (Component line : lore.lines()) out.append(' ').append(line.getString());
        return out.toString();
    }

    private static double average(Deque<Double> values) {
        if (values.isEmpty()) return -1;
        double sum = 0;
        for (double value : values) sum += value;
        return sum / values.size();
    }

    private static double findPrice(String text) {
        Matcher explicit = CURRENCY_PRICE.matcher(text.replace(",", ""));
        double found = -1;
        while (explicit.find()) {
            double value = numberValue(explicit.group(1), explicit.group(2));
            if (value >= 0) found = value;
        }
        if (found >= 0) return found;

        String[] lines = text.split("\\\\R");
        for (String line : lines) {
            String lower = line.toLowerCase(Locale.ROOT);
            if (!(lower.contains("price") || lower.contains("cost") || lower.contains("buy"))) continue;
            Matcher m = NUMBER.matcher(line.replace(",", ""));
            while (m.find()) {
                double value = numberValue(m.group(1), m.group(2));
                if (value >= 0) found = value;
            }
            if (found >= 0) return found;
        }
        return -1;
    }

    static double parsePrice(String input) {
        if (input == null) return -1;
        Matcher m = NUMBER.matcher(input.trim().toLowerCase(Locale.ROOT).replace(",", ""));
        if (!m.matches()) return -1;
        return numberValue(m.group(1), m.group(2));
    }

    private static double numberValue(String amount, String suffix) {
        try {
            double value = Double.parseDouble(amount.replace(",", ""));
            if (suffix != null) {
                value *= switch (suffix.toLowerCase(Locale.ROOT)) {
                    case "k" -> 1_000d;
                    case "m" -> 1_000_000d;
                    case "b" -> 1_000_000_000d;
                    default -> 1d;
                };
            }
            return value;
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    static String money(double value) {
        if (value >= 1_000_000_000) return String.format(Locale.ROOT, "%.2fB", value / 1_000_000_000);
        if (value >= 1_000_000) return String.format(Locale.ROOT, "%.2fM", value / 1_000_000);
        if (value >= 1_000) return String.format(Locale.ROOT, "%.1fK", value / 1_000);
        return String.format(Locale.ROOT, "%.0f", value);
    }
}
