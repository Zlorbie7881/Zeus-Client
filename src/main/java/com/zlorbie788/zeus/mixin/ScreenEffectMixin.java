package com.zlorbie788.zeus.mixin;

import com.zlorbie788.zeus.mods.ClearView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.resources.model.MaterialSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenEffectRenderer.class)
public abstract class ScreenEffectMixin {
    @Inject(method = "renderScreenEffect", at = @At("HEAD"), cancellable = true, require = 0)
    private void zeus$clearView(Minecraft minecraft, MaterialSet materials, MultiBufferSource buffers, CallbackInfo ci) {
        if (ClearView.hides()) ci.cancel();
    }
}
