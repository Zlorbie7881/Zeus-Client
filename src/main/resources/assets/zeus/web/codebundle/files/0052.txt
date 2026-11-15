package com.zlorbie788.zeus.mixin;

import com.zlorbie788.zeus.mods.Parity;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class CrosshairMixin {
    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true, require = 0)
    private void zeus$hideCrosshair(GuiGraphics graphics, DeltaTracker tracker, CallbackInfo ci) {
        if (Parity.hideCrosshair()) ci.cancel();
    }
}
