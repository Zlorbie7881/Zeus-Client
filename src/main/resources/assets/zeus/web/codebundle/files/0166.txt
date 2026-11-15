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

/**
 * Mouse Tweaks: scrolling and dragging inside any container, the way it should always have worked.
 */
public final class MouseTweaks extends Module {
    private final Setting.Bool wheel = add(new Setting.Bool("Wheel over a slot",
            "Scroll on a slot to shift the whole stack across, or to place and take single items with something on the cursor.", true));
    private final Setting.Bool dragLeft = add(new Setting.Bool("Left drag",
            "Hold left and sweep over slots to place one item in each, or pick one from each when your cursor is empty.", true));
    private final Setting.Bool dragRight = add(new Setting.Bool("Right drag",
            "Hold right and sweep to place one item per slot, or pick one item per slot.", true));

    private static int lastSlot = -1;

    public MouseTweaks() {
        super("mousetweaks", "Mouse Tweaks",
                "Scroll wheel and drag gestures in every container: move stacks, place one item per slot, pick one up per slot.",
                Category.UTILITY);
        alias("mouse", "drag", "scroll");
    }

    private static MouseTweaks self() {
        MouseTweaks m = Zeus.get(MouseTweaks.class);
        return m != null && m.enabled() ? m : null;
    }

    private static Slot hovered(AbstractContainerScreen<?> screen) {
        try {
            return ((ScreenAccessor) screen).zeus$hoveredSlot();
        } catch (Throwable t) {
            return null;
        }
    }

    private static void slotClick(Minecraft mc, AbstractContainerScreen<?> screen, Slot slot, int button, ClickType type) {
        if (mc.gameMode == null || mc.player == null) return;
        mc.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, slot.index, button, type, mc.player);
    }

    /** Scroll on a slot: whole stack across, or one item in / out with a stack on the cursor. */
    public static boolean wheel(AbstractContainerScreen<?> screen, double dir) {
        MouseTweaks m = self();
        if (m == null || !m.wheel.on() || dir == 0) return false;
        Slot slot = hovered(screen);
        if (slot == null || !slot.hasItem()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) return false;
        ItemStack carried = screen.getMenu().getCarried();
        if (carried.isEmpty()) {
            if (dir > 0) {
                slotClick(mc, screen, slot, 0, ClickType.QUICK_MOVE);
                return true;
            }
            slotClick(mc, screen, slot, 1, ClickType.PICKUP);
            return true;
        }
        slotClick(mc, screen, slot, 1, ClickType.PICKUP);
        return true;
    }

    /** Drag across slots: one item placed or picked per slot, once each. */
    public static boolean drag(AbstractContainerScreen<?> screen, int button) {
        MouseTweaks m = self();
        if (m == null) return false;
        if (button == 0 && !m.dragLeft.on()) return false;
        if (button == 1 && !m.dragRight.on()) return false;
        if (button != 0 && button != 1) return false;
        Slot slot = hovered(screen);
        if (slot == null) return false;
        if (lastSlot == slot.index) return false;
        lastSlot = slot.index;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) return false;
        ItemStack carried = screen.getMenu().getCarried();
        if (!carried.isEmpty()) {
            if (!slot.mayPlace(carried)) return false;
            slotClick(mc, screen, slot, 1, ClickType.PICKUP);
            return true;
        }
        if (!slot.hasItem()) return false;
        slotClick(mc, screen, slot, 1, ClickType.PICKUP);
        return true;
    }

    public static void release() {
        lastSlot = -1;
    }
}
