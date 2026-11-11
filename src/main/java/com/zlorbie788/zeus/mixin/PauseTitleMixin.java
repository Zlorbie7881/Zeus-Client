package com.zlorbie788.zeus.mixin;

import com.zlorbie788.zeus.ui.ZeusSkins;
import net.minecraft.client.gui.screens.PauseScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PauseScreen.class)
public abstract class PauseTitleMixin {
    @Inject(method = "init", at = @At("TAIL"), require = 0)
    private void zeus$init(CallbackInfo ci) {
        ZeusSkins.pauseInit((PauseScreen) (Object) this);
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;drawCenteredString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"), require = 0)
    private void zeus$title(GuiGraphics g, Font font, Component text, int x, int y, int color) {
        if (!ZeusSkins.hidesTitle((PauseScreen) (Object) this)) g.drawCenteredString(font, text, x, y, color);
    }
}
