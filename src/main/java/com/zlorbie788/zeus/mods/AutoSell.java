package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Inv;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Setting;
import com.zlorbie788.zeus.core.Zeus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

public final class AutoSell extends Module {
    private final Setting.Text command = add(new Setting.Text("Command", "The command without the slash and without the price. The price is added after it.", "ah sell", 30));
    private final Setting.Num price = add(new Setting.Num("Price", "What each item is listed for, in the unit below.", 1, 0, 999, 0.01, ""));
    private final Setting.Choice unit = add(new Setting.Choice("Price unit", "$: the number as it is. K: thousand, M: million, B: billion, T: trillion.", 2, "$", "K (thousand)", "M (million)", "B (billion)", "T (trillion)"));
    private final Setting.Choice format = add(new Setting.Choice("Price is typed as", "Short: 1.5m, which Donut SMP understands. Full: 1500000, for servers that only take plain numbers.", 0, "Short (1.5m)", "Full number"));
    private final Setting.Choice what = add(new Setting.Choice("What to sell", "The slot you are holding now, the slots you tick below, or the whole hotbar.", 2, "Held slot", "Chosen slots", "Whole hotbar"));
    private final Setting.Bool[] slots = new Setting.Bool[9];
    private final Setting.Text only = add(new Setting.Text("Only items named", "Only sell items whose name contains this text. Empty: any item.", "", 30));
    private final Setting.Num delay = add(new Setting.Num("Delay between commands", "How long it waits after one item before it does the next. 20 ticks are one second.", 30, 4, 400, 1, " ticks"));
    private final Setting.Num jitter = add(new Setting.Num("Random extra delay", "Up to this many ticks are added to the delay at random, so it does not tick like a clock.", 6, 0, 100, 1, " ticks"));
    private final Setting.Bool confirm = add(new Setting.Bool("Press the confirm button", "If the server opens a window asking you to confirm the listing, it clicks the confirm button.", true));
    private final Setting.Bool repeat = add(new Setting.Bool("Keep going", "When the slots are empty it waits and looks again, for new items. Off: it switches itself off when it is done.", false));
    private final Setting.Num again = add(new Setting.Num("Look again every", "", 10, 2, 120, 1, " s")).showIf(() -> repeat.on());
    private final Setting.Info status = add(new Setting.Info("Status", this::statusLines));

    private enum P { PICK, SENT, CONFIRM, CHECK, WAIT }

    private final Random rng = new Random();
    private P phase = P.PICK;
    private int cd;
    private int slot = -1;
    private int count;
    private int selectedBefore = -1;
    private int sold;
    private int kept;
    private ItemStack before;
    private final Set<Integer> skip = new HashSet<>();
    private String note = "Off.";

    public AutoSell() {
        super("autosell", "Auto Sell", "Puts the items in your hotbar up on the auction house with /ah sell, one after another.", Category.UTILITY);
        for (int i = 0; i < 9; i++) slots[i] = add(new Setting.Bool("Slot " + (i + 1), "", false)).showIf(() -> what.index() == 1);
    }

    private java.util.List<String> statusLines() {
        return java.util.List.of(note, "Sold " + sold + (kept > 0 ? ", " + kept + " could not be listed" : ""), "Will type: /" + typed());
    }

    @Override
    public String info() {
        return enabled() && sold > 0 ? Integer.toString(sold) : null;
    }

    private String priceText() {
        BigDecimal v = BigDecimal.valueOf(price.get()).setScale(2, java.math.RoundingMode.HALF_UP);
        double mult = switch (unit.index()) {
            case 1 -> 1e3;
            case 2 -> 1e6;
            case 3 -> 1e9;
            case 4 -> 1e12;
            default -> 1;
        };
        if (format.index() == 1) return v.multiply(BigDecimal.valueOf(mult)).setScale(0, java.math.RoundingMode.HALF_UP).toPlainString();
        String n = v.stripTrailingZeros().toPlainString();
        String suffix = switch (unit.index()) {
            case 1 -> "k";
            case 2 -> "m";
            case 3 -> "b";
            case 4 -> "t";
            default -> "";
        };
        return n + suffix;
    }

    private String typed() {
        String c = command.get().trim();
        while (c.startsWith("/")) c = c.substring(1);
        return c + " " + priceText();
    }

    private boolean wanted(int i) {
        return switch (what.index()) {
            case 0 -> Inv.selected() == i || i == selectedBefore;
            case 1 -> slots[i].on();
            default -> true;
        };
    }

    private boolean nameOk(ItemStack st) {
        String o = only.get().trim().toLowerCase(Locale.ROOT);
        return o.isEmpty() || st.getHoverName().getString().toLowerCase(Locale.ROOT).contains(o);
    }

    private int nextSlot() {
        for (int i = 0; i < 9; i++) {
            if (skip.contains(i) || !wanted(i)) continue;
            ItemStack st = Inv.get(i);
            if (st.isEmpty() || !nameOk(st)) continue;
            return i;
        }
        return -1;
    }

    @Override
    protected void onEnable() {
        if (price.get() <= 0) {
            note = "Set a price first.";
            Zeus.toast("Auto Sell: set a price first", false);
            phase = P.WAIT;
            cd = 1_000_000;
            return;
        }
        phase = P.PICK;
        cd = 5;
        sold = 0;
        kept = 0;
        slot = -1;
        skip.clear();
        Minecraft mc = Minecraft.getInstance();
        selectedBefore = mc.player == null ? -1 : Inv.selected();
        note = "Starting.";
    }

    @Override
    protected void onDisable() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && selectedBefore >= 0) Inv.select(selectedBefore);
        selectedBefore = -1;
        note = "Off.";
    }

    private int pause() {
        return delay.i() + (jitter.i() > 0 ? rng.nextInt(jitter.i() + 1) : 0);
    }

    @Override
    protected void onTick(Minecraft mc) {
        if (mc.player == null || mc.getConnection() == null || mc.gameMode == null) return;
        if (cd > 0) {
            cd--;
            return;
        }
        if (price.get() <= 0 && phase == P.WAIT) {
            cd = 40;
            return;
        }
        switch (phase) {
            case PICK -> {
                if (mc.screen != null) {
                    note = "Close this window and I will go on.";
                    cd = 10;
                    return;
                }
                int s = nextSlot();
                if (s < 0) {
                    if (repeat.on()) {
                        skip.clear();
                        phase = P.WAIT;
                        cd = (int) (again.get() * 20);
                        note = "Nothing to sell. Looking again in " + again.i() + " s.";
                    } else {
                        Zeus.toast("Auto Sell done: " + sold + " listed", true);
                        note = "Done. " + sold + " listed.";
                        setEnabled(false);
                    }
                    return;
                }
                slot = s;
                Inv.select(s);
                before = Inv.get(s).copy();
                count = before.getCount();
                phase = P.SENT;
                cd = 2;
                note = "Selected slot " + (s + 1) + ".";
            }
            case SENT -> {
                if (Inv.selected() != slot || Inv.get(slot).isEmpty()) {
                    phase = P.PICK;
                    cd = 2;
                    return;
                }
                String cmd = typed();
                mc.getConnection().sendCommand(cmd);
                note = "Typed /" + cmd + " for " + Inv.get(slot).getHoverName().getString() + ".";
                phase = confirm.on() ? P.CONFIRM : P.CHECK;
                cd = 8;
            }
            case CONFIRM -> {
                if (mc.screen instanceof AbstractContainerScreen<?> sc) {
                    AbstractContainerMenu menu = sc.getMenu();
                    int c = confirmSlot(menu);
                    if (c >= 0) {
                        mc.gameMode.handleInventoryMouseClick(menu.containerId, c, 0, ClickType.PICKUP, mc.player);
                        note = "Pressed confirm.";
                        phase = P.CHECK;
                        cd = 8;
                        return;
                    }
                    mc.setScreen(null);
                }
                phase = P.CHECK;
                cd = 2;
            }
            case CHECK -> {
                if (mc.screen instanceof AbstractContainerScreen<?> && mc.player.containerMenu != mc.player.inventoryMenu) mc.setScreen(null);
                ItemStack now = Inv.get(slot);
                boolean gone = now.isEmpty() || now.getCount() < count || !ItemStack.isSameItemSameComponents(now, before);
                if (gone) {
                    sold++;
                    note = "Listed slot " + (slot + 1) + ".";
                } else {
                    kept++;
                    skip.add(slot);
                    note = "Slot " + (slot + 1) + " could not be listed (still in your hotbar), skipping it.";
                }
                phase = P.PICK;
                cd = pause();
            }
            case WAIT -> {
                phase = P.PICK;
                cd = 1;
            }
            default -> { }
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
}
