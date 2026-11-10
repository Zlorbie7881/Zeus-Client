package com.zlorbie788.zeus.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes the slot the mouse is over, which the game keeps protected, so Hover Totem can read it. */
@Mixin(AbstractContainerScreen.class)
public interface ScreenAccessor {
    @Accessor("hoveredSlot")
    Slot zeus$hoveredSlot();
}
