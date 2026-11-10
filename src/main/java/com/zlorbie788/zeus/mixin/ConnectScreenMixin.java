package com.zlorbie788.zeus.mixin;

import com.zlorbie788.zeus.ui.ZeusLoading;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The game's "connecting to server" screen: the menu sky and the Zeus logo, and only the Cancel button on top (see {@link ZeusLoading}). */
@Mixin(ConnectScreen.class)
public abstract class ConnectScreenMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
    private void zeus$render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (ZeusLoading.connect((Screen) (Object) this, graphics, mouseX, mouseY, partialTick)) ci.cancel();
    }
}
