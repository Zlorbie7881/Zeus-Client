package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Names;
import com.zlorbie788.zeus.core.Setting;
import com.zlorbie788.zeus.core.Zeus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AhSniper extends Module {

    private static final Pattern PRICE_KEYWORD = Pattern.compile(
            "(?i)(?:price|cost|buy(?:\\s*it\\s*now)?|sell(?:ing)?(?:\\s*for)?|amount)\\s*[:=]?\\s*[$\\u26C3]?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*([kmbt])?");
    private static final Pattern DOLLAR = Pattern.compile("[$\\u26C3]\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*([kmbt])?");
    private static final Pattern COINS = Pattern.compile("(?i)([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*([kmbt])?\\s*(?:coins?|money|shards?)");

    private final Setting.Bool armed = add(new Setting.Bool("Sniper: auto-buy", "Buys listings that match a rule below by itself. Off: the sniper does nothing. It switches itself off again when a limit is reached.", false));
    private final Setting.Text rules = add(new Setting.Text("Sniper: items and highest price", "One rule per line or comma: item = the most you will pay. For example: elytra=2m, mace=500k, netherite ingot=300k. Write an item with no price (just \"shulker box\") to buy it whenever it is under the discount below. k, m, b and t work.", "", 260));
    private final Setting.Choice per = add(new Setting.Choice("Sniper: the price is", "Whole listing: the price on the listing. Per item: the price divided by the number of items in the stack.", 0, "For the whole listing", "Per item"));
    private final Setting.Bool learned = add(new Setting.Bool("Sniper: use learned prices", "Also compares the price with what the client has seen that item sell for, so a rule without a price still has a ceiling and a \"bargain\" is measured against the market, not only your limit.", true));
    private final Setting.Num below = add(new Setting.Num("Sniper: buy below market", "With learned prices on, a listing is worth buying only when it is at least this far under the learned price for that item.", 25, 5, 90, 5, " %")).showIf(() -> this.learned.on());
    private final Setting.Bool smart = add(new Setting.Bool("Sniper: smart mode", "Buys anything the price book says is underpriced, even when no rule matches it. Rules still apply on top and are never loosened.", true));
    private final Setting.Text minProfit = add(new Setting.Text("Sniper: smallest profit", "Smart mode skips a listing unless reselling it at the learned price would earn at least this much. k, m and b work. 0 accepts anything.", "0", 12));
    private final Setting.Text spendLimit = add(new Setting.Text("Sniper: spending limit", "It stops buying once it has spent this much since you switched it on. 0 means no limit (not recommended).", "1m", 16));
    private final Setting.Num maxBuys = add(new Setting.Num("Sniper: most purchases", "It stops after this many purchases.", 3, 1, 50, 1, ""));
    private final Setting.Num pause = add(new Setting.Num("Sniper: pause after a purchase", "Seconds it waits after a confirmed purchase before it looks again.", 1.5, 0.5, 30, 0.5, " s"));
    private final Setting.Num reaction = add(new Setting.Num("Sniper: reaction time", "How long a matching listing can sit before it is clicked. 0 clicks on the tick it appears; a small value looks more like a person.", 0, 0, 400, 10, " ms"));
    private final Setting.Choice buyClick = add(new Setting.Choice("Sniper: how to buy", "Click listing then confirm: the usual two-step. Shift-click: many servers buy instantly, with no confirm window. Both: tries shift-click, then falls back to clicking the listing.", 2, "Click listing, then confirm", "Shift-click the listing", "Both"));
    private final Setting.Bool reopen = add(new Setting.Bool("Sniper: open the auction house for me", "When no window is open, types the command below to open it again.", true));
    private final Setting.Text openCommand = add(new Setting.Text("Sniper: open command", "The command (without the slash) that opens the auction house.", "ah", 30)).showIf(() -> this.reopen.on());
    private final Setting.Text searchCommand = add(new Setting.Text("Sniper: search command", "The command used to put the auction house on one item. %s is replaced with the search text. Leave it empty to never search.", "ah search %s", 50));
    private final Setting.Text searchTerms = add(new Setting.Text("Sniper: search for", "What the search command gets, for example: elytra, mace. One search is tried every so often when the listings show nothing worth buying.", "", 60));
    private final Setting.Num searchEvery = add(new Setting.Num("Sniper: search again every", "Seconds between two searches, so the auction house is not flooded with commands.", 30, 5, 300, 5, " s"));
    private final Setting.Num pages = add(new Setting.Num("Sniper: pages to turn", "How many pages it pages through per pass when nothing on the page is worth buying. 0 never turns a page.", 3, 0, 20, 1, ""));
    private final Setting.Bool verify = add(new Setting.Bool("Sniper: check the item arrived", "Counts the item in your inventory before and after the purchase, and only counts the money as spent when it is really there. Off: trusts the click.", true));
    private final Setting.Text windowNames = add(new Setting.Text("Sniper: auction window title", "Words that appear in the title of the listing window, separated by commas.", "auction,ah,market", 80));
    private final Setting.Text confirmNames = add(new Setting.Text("Sniper: confirm window title", "Words that appear in the title of the window that asks you to confirm a purchase.", "confirm,purchase,buy", 80));
    private final Setting.Info report = addSetting(new Setting.Info("Sniper: status", this::lines));

    private enum Phase { LOOK, CONFIRM, VERIFY }

    private final Map<String, Long> tried = new HashMap<>();
    private final Map<String, Long> blocked = new HashMap<>();
    private static final java.util.Random RNG = new java.util.Random();
    private Phase phase = Phase.LOOK;
    private String bestVerdict = "";
    private long restUntil;
    private long lastSearch;
    private long lastPage;
    private long lastClick;
    private long idleSince;
    private int confirmTicks;
    private int verifyTicks;
    private int listingContainer = -1;
    private int pageCount;
    private String pageSig = "";
    private int bought;
    private double spent;
    private double pendingPrice;
    private String pendingName = "";
    private Item pendingItem;
    private int baselineCount = -1;
    private int listingsSeen;
    private double bestSeen;
    private double profit;
    private int misses;
    private double pendingFair;
    private int pendingCount = 1;
    private String lastBuy = "";
    private String watching = "";
    private String note = "Off.";

    public AhSniper() {
        super("ah_sniper", "AH Sniper", "Buys auction listings at or under a price you set, with a spending limit, a purchase limit and a check that the item really arrived.", Category.UTILITY);
        alias("auction sniper", "ah buy", "snipe", "auction bot", "ah bot");
    }

    public static String arm(boolean on) {
        AhSniper self = Zeus.get(AhSniper.class);
        if (self == null) return "AH Sniper is missing.";
        if (on) {
            self.setEnabled(true);
            self.armed.set(true);
            self.reset();
            return "AH Sniper armed. Open the auction house or let it open on its own.";
        }
        self.armed.set(false);
        self.phase = Phase.LOOK;
        self.note = "Off. Type /zeus ah on to arm it again.";
        return "AH Sniper disarmed.";
    }

    public static String rules(String text) {
        AhSniper self = Zeus.get(AhSniper.class);
        if (self == null) return "AH Sniper is missing.";
        self.rules.set(text == null ? "" : text);
        return "Rules saved: " + self.rules.get();
    }

    public static String status() {
        AhSniper self = Zeus.get(AhSniper.class);
        if (self == null) return "AH Sniper is missing.";
        return self.note + " | " + self.lines().get(1);
    }

    @Override
    protected void onEnable() {
        reset();
    }

    private void reset() {
        phase = Phase.LOOK;
        restUntil = lastSearch = lastPage = lastClick = 0;
        idleSince = 0;
        confirmTicks = verifyTicks = pageCount = bought = listingsSeen = 0;
        spent = 0;
        pendingPrice = 0;
        pendingName = "";
        pendingItem = null;
        baselineCount = -1;
        pageSig = "";
        bestSeen = 0;
        profit = 0;
        misses = 0;
        pendingFair = 0;
        pendingCount = 1;
        lastBuy = "";
        blocked.clear();
        tried.clear();
        note = armed.on() ? "Armed. Open the auction house." : "Off. Switch \"Sniper: auto-buy\" on to start.";
    }

    @Override
    public String info() {
        return armed.on() ? (bought > 0 ? bought + " bought" : "armed") : null;
    }

    private double limit() {
        double v = MarketHunter.parsePrice(spendLimit.get());
        return v < 0 ? 0 : v;
    }

    private List<String> lines() {
        List<String> out = new ArrayList<>();
        out.add(note);
        out.add("Bought " + bought + " of " + maxBuys.i() + ", spent " + MarketHunter.money(spent) + (limit() > 0 ? " of " + MarketHunter.money(limit()) : ""));
        List<Rule> rs = parseRules();
        out.add(rs.isEmpty() ? "No rules yet. Example: elytra=2m, mace=500k" : rs.size() + " rule" + (rs.size() == 1 ? "" : "s") + ", " + listingsSeen + " listings read");
        if (bestSeen > 0) out.add("Best deal seen: " + Math.round(bestSeen) + " % under its ceiling");
        if (profit > 0) out.add("Estimated resale profit: " + MarketHunter.money(profit));
        if (misses > 0) out.add("Failed buys: " + misses + (blocked.size() > 0 ? ", " + blocked.size() + " item(s) skipped for a while" : ""));
        if (smart.on()) out.add("Smart mode: " + (rs.isEmpty() ? "watching every known item" : "rules plus the price book"));
        if (!lastBuy.isEmpty()) out.add("Last: " + lastBuy);
        if (!bestVerdict.isEmpty()) out.add(bestVerdict);
        out.add("Price book: " + AuctionMind.books() + " items");
        if (!watching.isBlank()) {
            double trend = AuctionMind.momentum(watching);
            if (Math.abs(trend) > 0.05) out.add("Trend on " + watching + ": " + (trend > 0 ? "rising " : "falling ") + Math.round(Math.abs(trend) * 100) + " %");
        }
        out.add("Phase: " + phase.name().toLowerCase(Locale.ROOT) + (pageCount > 0 ? ", page " + (pageCount + 1) : ""));
        return out;
    }

    record Rule(String words, double max) {}

    List<Rule> parseRules() {
        List<Rule> out = new ArrayList<>();
        String all = rules.get() == null ? "" : rules.get();
        for (String part : all.split("[,;\\n]")) {
            String piece = part.trim();
            if (piece.isEmpty()) continue;
            int eq = piece.lastIndexOf('=');
            String words;
            double max = -1;
            if (eq > 0) {
                words = piece.substring(0, eq).trim().toLowerCase(Locale.ROOT);
                max = MarketHunter.parsePrice(piece.substring(eq + 1).trim());
            } else {
                words = piece.toLowerCase(Locale.ROOT);
            }
            if (!words.isEmpty()) out.add(new Rule(words, max));
        }
        return out;
    }

    static double priceOf(ItemStack stack) {
        String text = stack.getHoverName().getString() + " " + MarketHunter.loreText(stack);
        return priceIn(text);
    }

    static double priceIn(String text) {
        if (text == null || text.isEmpty()) return -1;
        String t = text.replace('\u00A0', ' ');
        Matcher m = PRICE_KEYWORD.matcher(t);
        if (m.find()) return value(m.group(1), m.group(2));
        m = DOLLAR.matcher(t);
        if (m.find()) return value(m.group(1), m.group(2));
        m = COINS.matcher(t);
        if (m.find()) return value(m.group(1), m.group(2));
        return -1;
    }

    private static double value(String number, String suffix) {
        try {
            double v = Double.parseDouble(number.replace(",", ""));
            if (suffix != null) {
                v *= switch (suffix.toLowerCase(Locale.ROOT)) {
                    case "k" -> 1e3;
                    case "m" -> 1e6;
                    case "b" -> 1e9;
                    case "t" -> 1e12;
                    default -> 1;
                };
            }
            return v > 0 ? v : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static boolean titled(String title, String words) {
        for (String w : words.toLowerCase(Locale.ROOT).split(",")) {
            String k = w.trim();
            if (!k.isEmpty() && title.contains(k)) return true;
        }
        return false;
    }

    private void stop(String why) {
        armed.set(false);
        phase = Phase.LOOK;
        note = why;
        Zeus.toast("AH Sniper: " + why, true);
    }

    @Override
    protected void onTick(Minecraft mc) {
        if (!armed.on()) {
            if (!note.startsWith("Off")) note = "Off. Switch \"Sniper: auto-buy\" on to start.";
            return;
        }
        if (mc.player == null || mc.gameMode == null) return;
        long now = System.currentTimeMillis();
        if (bought >= maxBuys.i()) {
            stop("reached the limit of " + maxBuys.i() + " purchases");
            return;
        }
        if (limit() > 0 && spent >= limit()) {
            stop("reached the spending limit of " + MarketHunter.money(limit()));
            return;
        }
        List<Rule> rs = parseRules();
        if (rs.isEmpty() && !smart.on()) {
            note = "Add a rule first, for example: elytra=2m, or switch smart mode on";
            return;
        }
        if (now < restUntil) return;

        AbstractContainerMenu menu = mc.player.containerMenu;
        Screen screen = mc.screen;
        String title = screen == null ? "" : screen.getTitle().getString().toLowerCase(Locale.ROOT);

        if (phase == Phase.VERIFY) {
            verifyTicks++;
            if (verify.on() && pendingItem != null) {
                int count = countOf(mc, pendingItem);
                if (count > baselineCount) {
                    settle(mc, now, true);
                    return;
                }
                if (verifyTicks < 60) return;
                settle(mc, now, false);
                return;
            }
            settle(mc, now, true);
            return;
        }

        if (phase == Phase.CONFIRM) {
            confirmTicks++;
            boolean sameWindow = screen != null && menu.containerId == listingContainer && titled(title, windowNames.get());
            boolean confirmWindow = screen != null && !sameWindow;
            if (!confirmWindow || screen == null) {
                if (confirmTicks > 10) {
                    phase = Phase.VERIFY;
                    verifyTicks = 0;
                }
                return;
            }
            if (confirmTicks < 3) return;
            if (confirmTicks > 80) {
                note = "The confirm window never answered. Closing it.";
                mc.setScreen(null);
                phase = Phase.LOOK;
                restUntil = now + 1000;
                return;
            }
            double shown = confirmTotal(menu);
            double left = limit() > 0 ? limit() - spent : Double.MAX_VALUE;
            if (shown > 0 && shown > left) {
                note = "Refused " + pendingName + ": the confirm window wants " + MarketHunter.money(shown) + ", more than the budget left.";
                mc.setScreen(null);
                phase = Phase.LOOK;
                restUntil = now + 1500;
                return;
            }
            int slot = confirmSlot(menu);
            if (slot < 0) return;
            click(mc, menu, slot, false);
            phase = Phase.VERIFY;
            verifyTicks = 0;
            return;
        }

        if (screen == null || menu == null) {
            phase = Phase.LOOK;
            if (idleSince == 0) idleSince = now;
            if (reopen.on() && !openCommand.get().isBlank() && now - idleSince > 1500 && mc.getConnection() != null) {
                idleSince = now;
                run(mc, openCommand.get());
                note = "Opening the auction house.";
            } else {
                note = "Armed. Waiting for the auction house window.";
            }
            return;
        }
        idleSince = 0;

        if (!titled(title, windowNames.get())) {
            note = "Waiting for the auction house window (now: " + screen.getTitle().getString() + ").";
            return;
        }

        int listingSlots = Math.max(0, menu.slots.size() - 36);
        if (listingSlots == 0) {
            note = "The auction window has no listing slots Zeus can see.";
            return;
        }

        String sig = signature(menu, listingSlots);
        if (!sig.equals(pageSig)) {
            pageSig = sig;
            listingsSeen += countFilled(menu, listingSlots);
        }

        double left = limit() > 0 ? limit() - spent : Double.MAX_VALUE;
        int bestSlot = -1;
        double bestDiscount = 0;
        double bestScore = 0;
        double bestPrice = 0;
        double bestFair = 0;
        String verdictLine = "";
        String bestName = "";
        Item bestItem = null;
        int bestCount = 0;

        for (int i = 0; i < listingSlots; i++) {
            ItemStack st = menu.slots.get(i).getItem();
            if (st == null || st.isEmpty()) continue;
            String name = st.getHoverName().getString();
            String lower = (name + " " + Names.item(st).replace('_', ' ') + " " + MarketHunter.loreText(st)).toLowerCase(Locale.ROOT);
            if (lower.contains("sold") || lower.contains("unavailable")) continue;
            Long ban = blocked.get(name);
            if (ban != null) {
                if (now - ban < 600_000L) continue;
                blocked.remove(name);
            }
            double ceiling = -1;
            double ruleMax = -1;
            boolean matched = false;
            for (Rule r : rs) {
                if (!lower.contains(r.words())) continue;
                matched = true;
                if (r.max() > 0) ruleMax = Math.max(ruleMax, r.max());
                else if (ceiling < 0) ceiling = Double.MAX_VALUE;
            }
            if (ruleMax > 0) ceiling = ceiling < 0 ? ruleMax : Math.min(ceiling, ruleMax);
            double market = learned.on() ? MarketHunter.learnedFor(name) : -1;
            if (!matched && !(smart.on() && market > 0)) continue;
            if (ceiling <= 0 && market <= 0 && ruleMax <= 0) continue;
            double price = priceOf(st);
            if (price <= 0) continue;
            double compare = per.index() == 1 ? price / Math.max(1, st.getCount()) : price;
            if (price > left) continue;
            double wanted = MarketHunter.parsePrice(minProfit.get());
            if (wanted > 0 && market > 0 && (market - compare) * Math.max(1, st.getCount()) < wanted) continue;
            AuctionMind.Verdict verdict = AuctionMind.judge(name, compare, st.getCount(), ruleMax, market, below.f());
            AuctionMind.observe(name, compare);
            if (!verdict.buy()) continue;
            String key = menu.containerId + "|" + i + "|" + name + "|" + st.getCount() + "|" + Math.round(price);
            Long seen = tried.get(key);
            if (seen != null && now - seen < 180_000L) continue;
            if (verdict.discount() > bestSeen) bestSeen = verdict.discount();
            if (verdict.score() > bestScore || bestSlot < 0) {
                bestSlot = i;
                bestScore = verdict.score();
                bestDiscount = verdict.discount();
                verdictLine = "Brain: " + (verdict.fair() > 0 ? "fair " + MarketHunter.money(verdict.fair()) : "no fair price") + ", " + Math.round(verdict.confidence() * 100) + " % sure, " + verdict.samples() + " seen";
                bestPrice = price;
                bestFair = verdict.fair();
                bestName = name;
                bestItem = st.getItem();
                bestCount = st.getCount();
            }
        }

        bestVerdict = verdictLine;
        if (!bestName.isBlank()) watching = bestName;
        if (bestSlot >= 0 && now - lastClick >= Math.max(0, Math.round(reaction.f())) + RNG.nextInt(80)) {
            String key = menu.containerId + "|" + bestSlot + "|" + bestName + "|" + bestCount + "|" + Math.round(bestPrice);
            tried.put(key, now);
            if (tried.size() > 600) {
                tried.entrySet().removeIf(e -> now - e.getValue() > 180_000L);
            }
            pendingPrice = bestPrice;
            pendingFair = bestFair;
            pendingCount = Math.max(1, bestCount);
            pendingName = bestName;
            pendingItem = bestItem;
            baselineCount = bestItem == null ? -1 : countOf(mc, bestItem);
            lastClick = now;
            listingContainer = menu.containerId;
            boolean shift = buyClick.index() == 1;
            click(mc, menu, bestSlot, shift);
            phase = shift ? Phase.VERIFY : Phase.CONFIRM;
            confirmTicks = 0;
            verifyTicks = 0;
            note = "Found " + bestName + " for " + MarketHunter.money(bestPrice) + (bestDiscount > 0 ? " (" + Math.round(bestDiscount) + " % under the ceiling)" : "") + ". Buying...";
            return;
        }

        note = "Watching " + listingSlots + " slots, nothing at your prices.";
        stepPage(mc, menu, listingSlots, now, rs);
    }

    private void stepPage(Minecraft mc, AbstractContainerMenu menu, int listingSlots, long now, List<Rule> rs) {
        int next = findNamed(menu, listingSlots, "next page", "next", "\u2192", "\u00BB", "arrow");
        int refresh = findNamed(menu, listingSlots, "refresh", "reload", "new listings");
        if (pages.i() > 0 && pageCount < pages.i() && next >= 0 && now - lastPage > 700) {
            lastPage = now;
            pageCount++;
            click(mc, menu, next, false);
            pageSig = "";
            note = "Turning to page " + (pageCount + 1) + ".";
            return;
        }
        if (pageCount >= pages.i() && pages.i() > 0) {
            pageCount = 0;
            pageSig = "";
            if (refresh >= 0 && now - lastPage > 900) {
                lastPage = now;
                click(mc, menu, refresh, false);
                note = "Refreshing the listings.";
                return;
            }
        }
        if (refresh >= 0 && now - lastPage > 1400) {
            lastPage = now;
            click(mc, menu, refresh, false);
            note = "Refreshing the listings.";
            return;
        }
        maybeSearch(mc, now, rs);
    }

    private void maybeSearch(Minecraft mc, long now, List<Rule> rs) {
        if (searchCommand.get().isBlank() || searchTerms.get().isBlank()) return;
        if (now - lastSearch < Math.max(5000L, Math.round(searchEvery.f() * 1000))) return;
        String words = rs.isEmpty() ? "" : rs.get(0).words();
        String cmd = searchCommand.get().replace("%s", searchTerms.get().isBlank() ? words : searchTerms.get());
        lastSearch = now;
        mc.setScreen(null);
        run(mc, cmd);
        note = "Searching the auction house for " + searchTerms.get() + ".";
    }

    private void settle(Minecraft mc, long now, boolean confirmed) {
        if (confirmed) {
            bought++;
            spent += pendingPrice;
            lastBuy = pendingName + " for " + MarketHunter.money(pendingPrice);
            double gain = Math.max(0, pendingFair - pendingPrice) * pendingCount;
            profit += gain;
            note = "Bought " + lastBuy + (gain > 0 ? " (about " + MarketHunter.money(gain) + " under market)" : "") + ".";
            Zeus.toast("AH Sniper bought " + lastBuy, true);
            restUntil = now + Math.max(1, Math.round(pause.f() * 1000));
        } else {
            misses++;
            blocked.put(pendingName, now);
            note = "Clicked " + pendingName + " but the item never arrived. Skipping it for ten minutes.";
            restUntil = now + 1200;
        }
        phase = Phase.LOOK;
        verifyTicks = 0;
        pendingItem = null;
        baselineCount = -1;
        pageSig = "";
    }

    private static void run(Minecraft mc, String command) {
        if (mc.getConnection() == null) return;
        String cmd = command.trim();
        while (cmd.startsWith("/")) cmd = cmd.substring(1);
        if (!cmd.isEmpty()) mc.getConnection().sendCommand(cmd);
    }

    private static void click(Minecraft mc, AbstractContainerMenu menu, int slot, boolean shift) {
        if (mc.gameMode == null || mc.player == null) return;
        mc.gameMode.handleInventoryMouseClick(menu.containerId, slot, 0, shift ? ClickType.QUICK_MOVE : ClickType.PICKUP, mc.player);
    }

    private static String signature(AbstractContainerMenu menu, int slots) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < slots; i++) {
            ItemStack st = menu.slots.get(i).getItem();
            if (st == null || st.isEmpty()) {
                b.append('_');
            } else {
                b.append(Names.item(st)).append('x').append(st.getCount()).append(';');
            }
        }
        return b.toString();
    }

    private static int countFilled(AbstractContainerMenu menu, int slots) {
        int n = 0;
        for (int i = 0; i < slots; i++) {
            ItemStack st = menu.slots.get(i).getItem();
            if (st != null && !st.isEmpty()) n++;
        }
        return n;
    }

    private static double confirmTotal(AbstractContainerMenu menu) {
        int n = Math.max(0, menu.slots.size() - 36);
        for (int i = 0; i < n; i++) {
            ItemStack st = menu.slots.get(i).getItem();
            if (st == null || st.isEmpty()) continue;
            double p = priceIn(st.getHoverName().getString() + " " + MarketHunter.loreText(st));
            if (p > 0) return p;
        }
        return -1;
    }

    private static int findNamed(AbstractContainerMenu menu, int slots, String... words) {
        for (int i = 0; i < slots; i++) {
            ItemStack st = menu.slots.get(i).getItem();
            if (st == null || st.isEmpty()) continue;
            String text = (st.getHoverName().getString() + " " + MarketHunter.loreText(st)).toLowerCase(Locale.ROOT);
            for (String w : words) if (text.contains(w)) return i;
        }
        for (int i = 0; i < slots; i++) {
            ItemStack st = menu.slots.get(i).getItem();
            if (st == null || st.isEmpty()) continue;
            if (Names.item(st).contains("arrow") || Names.item(st).contains("spectral_arrow")) return i;
        }
        return -1;
    }

    private static int confirmSlot(AbstractContainerMenu menu) {
        int n = Math.max(0, menu.slots.size() - 36);
        for (int i = 0; i < n; i++) {
            ItemStack st = menu.slots.get(i).getItem();
            if (st == null || st.isEmpty()) continue;
            String name = st.getHoverName().getString().toLowerCase(Locale.ROOT);
            if (name.contains("confirm") || name.contains("accept") || name.contains("buy") || name.contains("purchase")) return i;
        }
        for (int i = 0; i < n; i++) {
            ItemStack st = menu.slots.get(i).getItem();
            if (st == null || st.isEmpty()) continue;
            String id = Names.item(st);
            if (id.equals("lime_stained_glass_pane") || id.equals("green_stained_glass_pane") || id.equals("lime_wool") || id.equals("green_wool") || id.equals("lime_concrete")) return i;
        }
        return -1;
    }

    private static int countOf(Minecraft mc, Item item) {
        if (mc.player == null || item == null) return -1;
        int n = 0;
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            ItemStack st = mc.player.getInventory().getItem(i);
            if (st != null && !st.isEmpty() && st.getItem() == item) n += st.getCount();
        }
        return n;
    }
}
