package com.zlorbie788.zeus.mixin;

import com.zlorbie788.zeus.mods.StreamerPrivacy;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class TabListPrivacyMixin {
    @Inject(method = "renderTabList", at = @At("HEAD"), cancellable = true, require = 0)
    private void zeus$hideTabList(GuiGraphics graphics, CallbackInfo ci) {
        if (StreamerPrivacy.hideTabList()) ci.cancel();
    }
}
