package com.zlorbie788.zeus.mixin;

import com.zlorbie788.zeus.ui.ListPlates;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The world list and the server list as rows of stone plates (a test: switch "Stone plate lists" off, or delete this class and
 * its line in zeus.mixins.json, to get the game's own lists back).
 */
@Mixin(AbstractSelectionList.class)
public abstract class ListPlateMixin {
    @Inject(method = "renderListBackground", at = @At("HEAD"), cancellable = true, require = 0)
    private void zeus$noPanel(GuiGraphics g, CallbackInfo ci) {
        if (ListPlates.active(this)) ci.cancel();
    }

    @Inject(method = "renderListSeparators", at = @At("HEAD"), cancellable = true, require = 0)
    private void zeus$noSeparators(GuiGraphics g, CallbackInfo ci) {
        if (ListPlates.active(this)) ci.cancel();
    }

    @Inject(method = "renderSelection", at = @At("HEAD"), cancellable = true, require = 0)
    private void zeus$noSelectionBox(GuiGraphics g, @Coerce Object entry, int color, CallbackInfo ci) {
        if (ListPlates.active(this)) ci.cancel();
    }

    @Inject(method = "renderItem", at = @At("HEAD"), require = 0)
    private void zeus$plate(GuiGraphics g, int mouseX, int mouseY, float partial, @Coerce Object entry, CallbackInfo ci) {
        if (ListPlates.active(this)) ListPlates.row((AbstractSelectionList<?>) (Object) this, entry, g, mouseX, mouseY);
    }
}
