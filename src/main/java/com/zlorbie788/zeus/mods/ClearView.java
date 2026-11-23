package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Setting;

/**
 * Keeps the screen overlays out of your view. The burning animation, the block texture you get
 * when your head is inside a block, and the totem pop flash are all drawn by one renderer, so this
 * cancels that pass while it is switched on.
 */
public final class ClearView extends Module {
    private static volatile boolean hiding;

    private final Setting.Bool overlays = add(new Setting.Bool("Hide screen overlays",
            "Stops the fire animation, the block texture when your head is inside a block, and the totem flash from covering your view.", true));

    public ClearView() {
        super("clearview", "Clear View", "Keeps fire, blocks in your face and the totem flash from covering your screen.", Category.RENDER);
        alias("clear view", "no overlay", "no fire", "clear screen");
        overlays.onChange(this::sync);
        hiding = overlays.on();
    }

    private void sync() {
        hiding = enabled() && overlays.on();
    }

    @Override
    protected void onEnable() {
        sync();
    }

    @Override
    protected void onDisable() {
        hiding = false;
    }

    /** Read from the screen-effect renderer. */
    public static boolean hides() {
        return hiding;
    }
}
