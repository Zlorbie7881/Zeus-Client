package com.zlorbie788.zeus.mods;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Inv;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Setting;
import com.zlorbie788.zeus.core.Zeus;
import com.zlorbie788.zeus.ui.ItemPickerScreen;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Auction Bot: the idea of the DonutSMP Automation bot (list an item on the auction house over and over, check that the
 * listing went through, retry when it did not, and keep count) built into the client, so no separate program, no console
 * client and no second account session is needed.
 *
 * <p>You pick the item from a menu with a search bar, say how many go into each listing and for what price. Every round it
 * looks in your inventory for that item, puts exactly that many into your hand (splitting a bigger stack the way you would
 * with the mouse), types {@code /ah sell <price>}, and waits for the server to confirm: either a chat line that matches the
 * confirmation pattern, or the items leaving your hand. If nothing confirms it in time it tries again, up to the number of
 * tries you set, and counts a failure. Sales, items, income and failures are kept between sessions, and the last log lines
 * are shown in the panel.</p>
 *
 * <p>It does only what you could do by hand, with a pause between steps. Check that the server allows automation: that is your
 * decision and your account.</p>
 */
public final class AuctionBot extends Module {
    private static final Pattern PRICE = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)\\s*([kmbt])?");
    private static final String DEFAULT_CONFIRM = "(?i)(?:listed|auctioned|sold).*?(\\d+)";
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int STEP = 3;

    private final Setting.Secret item = addSetting(new Setting.Secret("item"));
    private final Setting.Action pick = addSetting(new Setting.Action("Choose the item to sell", "Opens a menu with a search bar. Click the item you want to list.", this::openPicker));
    private final Setting.Info chosen = addSetting(new Setting.Info("Item", () -> List.of(displayName(itemId()) + "  (" + itemId() + ")", "In your inventory: " + have(itemId()))));
    private final Setting.Num amount = add(new Setting.Num("Items per listing", "How many items go into one listing. A bigger stack is split for you.", 1, 1, 64, 1, ""));
    private final Setting.Text price = add(new Setting.Text("Price of one listing", "What one listing costs. Plain numbers or short forms: 500, 1.5k, 2m, 3b.", "500", 12));
    private final Setting.Choice format = add(new Setting.Choice("Price is typed as", "Short: 1.5m, which Donut SMP understands. Full: 1500000, for servers that only take plain numbers.", 0, "Short (1.5m)", "Full number"));
    private final Setting.Text command = add(new Setting.Text("Command", "The command without the slash and without the price. The price is added after it.", "ah sell", 30));
    private final Setting.Num delay = add(new Setting.Num("Pause between listings", "How long it waits after one listing before it makes the next.", 3, 0.25, 60, 0.25, " s"));
    private final Setting.Num timeout = add(new Setting.Num("Wait for the confirmation", "How long it waits for the server to confirm a listing before it tries again.", 15, 2, 120, 1, " s"));
    private final Setting.Num tries = add(new Setting.Num("Tries per listing", "How often it types the command again when nothing confirmed it. After that the listing counts as failed.", 3, 1, 10, 1, ""));
    private final Setting.Text confirmText = add(new Setting.Text("Confirmation pattern", "A chat line that matches this means the listing worked (a regular expression, case does not matter). Leave it as it is if you are not sure: items leaving your hand count too.", DEFAULT_CONFIRM, 120));
    private final Setting.Bool byHand = add(new Setting.Bool("Items leaving my hand count", "Counts a listing as done when the items are gone from your hand, even if no chat line matched. Safer than waiting for a chat line alone.", true));
    private final Setting.Bool pressConfirm = add(new Setting.Bool("Press the confirm button", "If the server opens a window asking you to confirm the listing, it clicks the confirm button.", true));
    private final Setting.Num limit = add(new Setting.Num("Stop after", "It switches itself off after this many listings. 0 means it keeps going while the module is on.", 0, 0, 500, 1, ""));
    private final Setting.Info status = addSetting(new Setting.Info("Status", this::statusLines));
    private final Setting.Info totals = addSetting(new Setting.Info("Statistics", this::totalLines));
    private final Setting.Info logLines = addSetting(new Setting.Info("Log", this::logTail));
    private final Setting.Action resetStats = addSetting(new Setting.Action("Reset statistics", "Sets sales, items, income and failures back to zero.", () -> {
        stats = new Stats();
        saveStats();
        log("Statistics reset.");
        Zeus.toast("Auction Bot statistics reset", true);
    }));
    private final Setting.Action clearLog = addSetting(new Setting.Action("Clear the log", "Empties the log lines.", this::wipeLog));

    private enum P { CHECK, PREP, SEND, WAIT, GAP }

    /** What is kept between sessions. */
    static final class Stats {
        long sales;
        long items;
        double income;
        long failures;
    }

    private final Queue<String> incoming = new ConcurrentLinkedQueue<>();
    private final Deque<String> log = new ArrayDeque<>();
    private final Deque<int[]> plan = new ArrayDeque<>();
    private Stats stats = new Stats();
    private P phase = P.CHECK;
    private int cd;
    private int dst = -1;
    private int attempt;
    private int waited;
    private int prepTries;
    private int session;
    private int sessionFailed;
    private int lastWindow = -1;
    private long startedAt;
    private String note = "Off.";

    public AuctionBot() {
        super("auction", "Auction Bot", "Lists an item on the auction house over and over: pick the item, set the price, and it sells, checks the confirmation, retries and keeps count.", Category.UTILITY);
        loadStats();
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> hear(message));
        ClientReceiveMessageEvents.CHAT.register((message, signed, sender, params, time) -> hear(message));
    }

    private void hear(Component message) {
        if (!enabled() || message == null) return;
        incoming.add(message.getString());
        while (incoming.size() > 60) incoming.poll();
    }

    // ------------------------------------------------------------------ settings helpers

    String itemId() {
        String s = item.get().trim().toLowerCase(Locale.ROOT);
        return s.isEmpty() ? "experience_bottle" : s;
    }

    private static String displayName(String id) {
        for (ItemPickerScreen.Entry e : ItemPickerScreen.everything()) if (e.id().equals(id)) return e.name();
        StringBuilder b = new StringBuilder();
        for (String w : id.split("_")) {
            if (w.isEmpty()) continue;
            if (b.length() > 0) b.append(' ');
            b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return b.toString();
    }

    private void openPicker() {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new ItemPickerScreen(mc.screen, "Auction Bot: pick the item", new LinkedHashSet<>(List.of(itemId())), 1, set -> {
            if (!set.isEmpty()) item.set(set.iterator().next());
        }));
    }

    static String idOf(ItemStack st) {
        try {
            String s = BuiltInRegistries.ITEM.getKey(st.getItem()).toString();
            return s.substring(s.indexOf(':') + 1);
        } catch (RuntimeException e) {
            return "";
        }
    }

    private static int have(String id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0;
        int n = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack st = Inv.get(i);
            if (!st.isEmpty() && idOf(st).equals(id)) n += st.getCount();
        }
        return n;
    }

    /** The price as a number, or -1 when it is not written like one. Understands 500, 1.5k, 2m, 3b, 1t and commas. */
    static double parsePrice(String text) {
        if (text == null) return -1;
        Matcher m = PRICE.matcher(text.trim().toLowerCase(Locale.ROOT).replace(",", "").replace("$", ""));
        if (!m.matches()) return -1;
        double v;
        try {
            v = Double.parseDouble(m.group(1));
        } catch (NumberFormatException e) {
            return -1;
        }
        if (m.group(2) != null) {
            v *= switch (m.group(2).charAt(0)) {
                case 'k' -> 1e3;
                case 'm' -> 1e6;
                case 'b' -> 1e9;
                default -> 1e12;
            };
        }
        return v;
    }

    /** The price as the command wants it. */
    String priceArg() {
        double v = parsePrice(price.get());
        if (v <= 0) return "";
        if (format.index() == 1) return BigDecimal.valueOf(v).setScale(0, RoundingMode.HALF_UP).toPlainString();
        return price.get().trim().toLowerCase(Locale.ROOT).replace(",", "").replace("$", "").replace(" ", "");
    }

    String typed() {
        String c = command.get().trim();
        while (c.startsWith("/")) c = c.substring(1);
        return c + " " + priceArg();
    }

    /**
     * The clicks that leave exactly {@code want} items in the hotbar slot {@code to}, from a stack of {@code have} in the
     * menu slot {@code from}: nothing when it is already there, one swap for an exact stack, otherwise the way a player splits
     * a stack (pick it up, right-click one item at a time into the empty slot, put the rest back). Each entry is
     * {menu slot, button, click type: 0 pickup, 1 swap}.
     */
    static List<int[]> clicks(int from, int have, int to, int toHotbar, int want) {
        List<int[]> out = new ArrayList<>();
        if (from == to && have == want) return out;
        if (have == want) {
            out.add(new int[] {from, toHotbar, 1});
            return out;
        }
        out.add(new int[] {from, 0, 0});
        for (int i = 0; i < want; i++) out.add(new int[] {to, 1, 0});
        out.add(new int[] {from, 0, 0});
        return out;
    }

    // ------------------------------------------------------------------ status

    private void wipeLog() {
        log.clear();
    }

    private void log(String text) {
        log.addLast(LocalTime.now().format(CLOCK) + "  " + text);
        while (log.size() > 120) log.removeFirst();
    }

    private List<String> logTail() {
        if (log.isEmpty()) return List.of("Nothing yet.");
        List<String> all = new ArrayList<>(log);
        return all.subList(Math.max(0, all.size() - 6), all.size());
    }

    private List<String> statusLines() {
        List<String> out = new ArrayList<>();
        out.add(note);
        if (enabled()) out.add("This session: " + session + " listed" + (sessionFailed > 0 ? ", " + sessionFailed + " failed" : "") + " · will type /" + typed());
        return out;
    }

    /** For tests. */
    String totalsText() {
        return totalLines().get(0);
    }

    private List<String> totalLines() {
        return List.of(stats.sales + " sales · " + stats.items + " items · " + money(stats.income) + " listed value · " + stats.failures + " failed");
    }

    static String money(double v) {
        if (v >= 1e12) return String.format(Locale.ROOT, "$%.2fT", v / 1e12);
        if (v >= 1e9) return String.format(Locale.ROOT, "$%.2fB", v / 1e9);
        if (v >= 1e6) return String.format(Locale.ROOT, "$%.2fM", v / 1e6);
        if (v >= 1e3) return String.format(Locale.ROOT, "$%.1fK", v / 1e3);
        return String.format(Locale.ROOT, "$%.0f", v);
    }

    @Override
    public String info() {
        return enabled() && session > 0 ? Integer.toString(session) : null;
    }

    // ------------------------------------------------------------------ statistics file

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("zeus-auctionbot.json");
    }

    private void loadStats() {
        try {
            if (!Files.isRegularFile(file())) return;
            JsonObject o = JsonParser.parseString(Files.readString(file(), StandardCharsets.UTF_8)).getAsJsonObject();
            Stats s = new Stats();
            s.sales = o.has("sales") ? o.get("sales").getAsLong() : 0;
            s.items = o.has("items") ? o.get("items").getAsLong() : 0;
            s.income = o.has("income") ? o.get("income").getAsDouble() : 0;
            s.failures = o.has("failures") ? o.get("failures").getAsLong() : 0;
            stats = s;
        } catch (IOException | RuntimeException e) {
            Zeus.LOG.warn("Could not read the auction bot statistics", e);
        }
    }

    private void saveStats() {
        try {
            JsonObject o = new JsonObject();
            o.addProperty("sales", stats.sales);
            o.addProperty("items", stats.items);
            o.addProperty("income", stats.income);
            o.addProperty("failures", stats.failures);
            Files.createDirectories(file().getParent());
            Files.writeString(file(), GSON.toJson(o), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            Zeus.LOG.warn("Could not save the auction bot statistics", e);
        }
    }

    // ------------------------------------------------------------------ the loop

    @Override
    protected void onEnable() {
        phase = P.CHECK;
        cd = 5;
        plan.clear();
        incoming.clear();
        session = 0;
        sessionFailed = 0;
        attempt = 0;
        prepTries = 0;
        lastWindow = -1;
        dst = -1;
        startedAt = System.currentTimeMillis();
        if (parsePrice(price.get()) <= 0) {
            note = "Set a price first (for example 500 or 1.5k).";
            Zeus.toast("Auction Bot: set a price first", false);
            phase = P.GAP;
            cd = 1_000_000;
            return;
        }
        note = "Starting.";
        log("Started: " + amount.i() + "x " + displayName(itemId()) + " for " + price.get() + " each listing.");
    }

    @Override
    protected void onDisable() {
        plan.clear();
        saveStats();
        if (!note.startsWith("Set a price") && !note.startsWith("Done")) note = "Off.";
        log("Stopped. " + session + " listed this session.");
    }

    private void stop(String why, boolean good) {
        note = why;
        log(why);
        Zeus.toast("Auction Bot: " + why, good);
        setEnabled(false);
        note = why;
    }

    private boolean regexMatches(String line) {
        String p = confirmText.get().trim();
        if (p.isEmpty()) return false;
        try {
            return Pattern.compile(p, Pattern.CASE_INSENSITIVE).matcher(line).find();
        } catch (PatternSyntaxException e) {
            return false;
        }
    }

    private static int confirmSlot(AbstractContainerMenu menu) {
        int n = Math.max(0, menu.slots.size() - 36);
        for (int i = 0; i < n; i++) {
            ItemStack st = menu.slots.get(i).getItem();
            if (st == null || st.isEmpty()) continue;
            String name = st.getHoverName().getString().toLowerCase(Locale.ROOT);
            if (name.contains("confirm") || name.contains("accept") || name.equals("yes") || name.contains("list item")) return i;
        }
        return -1;
    }

    private boolean inHand(String id, int count) {
        if (dst < 0) return false;
        ItemStack st = Inv.get(dst);
        return !st.isEmpty() && idOf(st).equals(id) && st.getCount() == count;
    }

    @Override
    protected void onTick(Minecraft mc) {
        if (mc.player == null || mc.getConnection() == null || mc.gameMode == null) return;
        if (cd > 0) {
            cd--;
            return;
        }
        String id = itemId();
        int want = amount.i();
        switch (phase) {
            case CHECK -> {
                if (mc.screen != null) {
                    note = "Close this window and I will go on.";
                    cd = 10;
                    return;
                }
                if (!mc.player.containerMenu.getCarried().isEmpty()) {
                    note = "Put down the item on your cursor first.";
                    cd = 20;
                    return;
                }
                if (limit.i() > 0 && session >= limit.i()) {
                    stop("Done: " + session + " listed.", true);
                    return;
                }
                // find the stack to sell: an exact one in the hotbar first, then any exact one, then the smallest big enough
                int src = -1;
                int best = Integer.MAX_VALUE;
                for (int i = 0; i < 36; i++) {
                    ItemStack st = Inv.get(i);
                    if (st.isEmpty() || !idOf(st).equals(id) || st.getCount() < want) continue;
                    int score = (st.getCount() - want) * 2 + (i < 9 ? 0 : 1);
                    if (score < best) {
                        best = score;
                        src = i;
                    }
                }
                if (src < 0) {
                    int total = have(id);
                    note = total > 0 ? "Waiting for a stack of " + want + " " + displayName(id) + " (you have " + total + " in smaller stacks)." : "Waiting for " + want + " " + displayName(id) + ".";
                    cd = Math.max(20, (int) (delay.get() * 20));
                    return;
                }
                int have = Inv.get(src).getCount();
                // where it goes: the slot it is in if it fits exactly, else the selected hotbar slot or any empty hotbar slot
                int to = -1;
                if (src < 9 && have == want) {
                    to = src;
                } else {
                    int sel = Inv.selected();
                    if (Inv.get(sel).isEmpty() && sel != src) to = sel;
                    for (int i = 0; i < 9 && to < 0; i++) if (Inv.get(i).isEmpty() && i != src) to = i;
                    if (to < 0 && have == want && src >= 9) to = sel;
                }
                if (to < 0) {
                    stop("there is no free hotbar slot to put the items in", false);
                    return;
                }
                dst = to;
                plan.clear();
                for (int[] c : clicks(src < 9 ? Inv.menuSlot(src) : src, have, Inv.menuSlot(to), to, want)) plan.add(c);
                phase = P.PREP;
                cd = 0;
                note = plan.isEmpty() ? "The items are ready." : "Getting " + want + " " + displayName(id) + " ready.";
            }
            case PREP -> {
                if (mc.screen != null) {
                    note = "Close this window and I will go on.";
                    cd = 10;
                    return;
                }
                int[] c = plan.poll();
                if (c != null) {
                    Inv.click(c[0], c[1], c[2] == 1 ? ClickType.SWAP : ClickType.PICKUP);
                    cd = STEP;
                    return;
                }
                if (!inHand(id, want)) {
                    if (++prepTries >= 3) {
                        stop("I could not get exactly " + want + " " + displayName(id) + " into the hotbar", false);
                        return;
                    }
                    log("The items did not end up where expected, trying again.");
                    phase = P.CHECK;
                    cd = 10;
                    return;
                }
                prepTries = 0;
                Inv.select(dst);
                attempt = 1;
                phase = P.SEND;
                cd = 3; // the game tells the server about the new slot on its next tick
            }
            case SEND -> {
                if (Inv.selected() != dst || !inHand(id, want)) {
                    phase = P.CHECK;
                    cd = 5;
                    return;
                }
                String cmd = typed();
                incoming.clear();
                lastWindow = -1;
                mc.getConnection().sendCommand(cmd);
                log("Typed /" + cmd + " (try " + attempt + " of " + tries.i() + ").");
                note = "Listing " + want + "x " + displayName(id) + " for " + price.get() + " (try " + attempt + ").";
                waited = 0;
                phase = P.WAIT;
                cd = 2;
            }
            case WAIT -> {
                waited++;
                boolean ok = false;
                String line;
                while ((line = incoming.poll()) != null) {
                    if (regexMatches(line)) {
                        ok = true;
                        log("Confirmed by chat: " + (line.length() > 70 ? line.substring(0, 70) + "..." : line));
                    }
                }
                if (!ok && mc.screen instanceof AbstractContainerScreen<?> sc && pressConfirm.on()) {
                    AbstractContainerMenu menu = sc.getMenu();
                    if (menu.containerId != lastWindow) {
                        int c = confirmSlot(menu);
                        if (c >= 0) {
                            lastWindow = menu.containerId;
                            mc.gameMode.handleInventoryMouseClick(menu.containerId, c, 0, ClickType.PICKUP, mc.player);
                            log("Pressed the confirm button.");
                            cd = 6;
                            return;
                        }
                    }
                }
                if (!ok && byHand.on()) {
                    ItemStack now = Inv.get(dst);
                    boolean gone = now.isEmpty() || !idOf(now).equals(id) || now.getCount() < want;
                    if (gone && waited > 4) {
                        ok = true;
                        log("Confirmed: the items left your hand.");
                    }
                }
                if (ok) {
                    double value = Math.max(0, parsePrice(price.get()));
                    stats.sales++;
                    stats.items += want;
                    stats.income += value;
                    session++;
                    saveStats();
                    if (mc.screen instanceof AbstractContainerScreen<?>) mc.setScreen(null);
                    note = "Listed " + want + "x " + displayName(id) + " for " + price.get() + ".";
                    phase = P.GAP;
                    cd = Math.max(2, (int) (delay.get() * 20));
                    return;
                }
                if (waited >= timeout.get() * 20) {
                    log("No confirmation after " + (int) timeout.get() + " s.");
                    if (mc.screen instanceof AbstractContainerScreen<?>) mc.setScreen(null);
                    if (attempt < tries.i()) {
                        attempt++;
                        phase = P.SEND;
                        cd = Math.min(Math.max(2, (int) (delay.get() * 20)), 100);
                        note = "No confirmation, trying again.";
                    } else {
                        stats.failures++;
                        sessionFailed++;
                        saveStats();
                        log("The listing failed after " + tries.i() + " tries.");
                        note = "A listing failed after " + tries.i() + " tries.";
                        phase = P.GAP;
                        cd = Math.max(20, (int) (delay.get() * 20));
                    }
                }
            }
            case GAP -> {
                phase = P.CHECK;
                cd = 1;
            }
            default -> { }
        }
    }
}
