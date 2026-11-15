package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Setting;
import com.zlorbie788.zeus.core.Zeus;
import com.zlorbie788.zeus.mixin.ScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class HoverTotem extends Module {
    private final Setting.Choice target = add(new Setting.Choice("Where it goes", "Offhand is the PvP choice. Hotbar slot puts it where you are looking, so a spear or sword stays in your hand.", 0, "Offhand", "Selected hotbar slot"));
    private final Setting.Bool onlyWhenEmpty = add(new Setting.Bool("Only when that slot has no totem", "Off: hovering a totem swaps it in even if you already hold one, which is how you stack totems across containers.", true));
    private final Setting.Num delay = add(new Setting.Num("Delay", "Ticks to wait after a swap before another one, so holding the mouse still cannot flood the server with clicks.", 4, 1, 40, 1, " ticks"));
    private final Setting.Bool notify = add(new Setting.Bool("Tell me", "A toast when a totem is moved.", true));
    private int cooldown;

    public HoverTotem() {
        super("hovertotem", "Hover Totem", "Hover a totem in any open container and it moves to your offhand or hotbar.", Category.COMBAT);
        light();
    }

    @Override
    protected void onEnable() {
        cooldown = 0;
    }

    @Override
    protected void onTick(Minecraft mc) {
        if (mc.player == null || mc.gameMode == null) return;
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        if (!(mc.screen instanceof AbstractContainerScreen<?> screen)) return;
        Slot slot = ((ScreenAccessor) screen).zeus$hoveredSlot();
        if (slot == null || !slot.hasItem()) return;
        ItemStack hovered = slot.getItem();
        if (!hovered.is(Items.TOTEM_OF_UNDYING)) return;
        boolean offhand = target.index() == 0;
        ItemStack destination = offhand ? mc.player.getOffhandItem() : mc.player.getMainHandItem();
        if (onlyWhenEmpty.on() && destination.is(Items.TOTEM_OF_UNDYING)) return;
        var menu = mc.player.containerMenu;
        int menuSlot = menu.slots.indexOf(slot);
        if (menuSlot < 0) return;
        int button = offhand ? 40 : mc.player.getInventory().getSelectedSlot();
        mc.gameMode.handleInventoryMouseClick(menu.containerId, menuSlot, button, ClickType.SWAP, mc.player);
        cooldown = delay.i();
        if (notify.on()) Zeus.toast("Totem moved to your " + (offhand ? "offhand" : "hotbar"), true);
    }
}
