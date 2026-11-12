package com.zlorbie788.zeus.mixin;

import com.zlorbie788.zeus.mods.MouseTweaks;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public abstract class MouseTweaksMixin {
    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true, require = 0)
    private void zeus$scroll(double mouseX, double mouseY, double scrollX, double scrollY, CallbackInfoReturnable<Boolean> cir) {
        try {
            if (MouseTweaks.wheel((AbstractContainerScreen<?>) (Object) this, scrollY)) cir.setReturnValue(true);
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true, require = 0)
    private void zeus$drag(MouseButtonEvent event, double dragX, double dragY, CallbackInfoReturnable<Boolean> cir) {
        try {
            if (MouseTweaks.drag((AbstractContainerScreen<?>) (Object) this, event.button())) cir.setReturnValue(true);
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), require = 0)
    private void zeus$release(MouseButtonEvent event, CallbackInfoReturnable<Boolean> cir) {
        MouseTweaks.release();
    }
}
