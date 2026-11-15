package com.zlorbie788.zeus.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zlorbie788.zeus.mods.CameraMods;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class NoHurtCamMixin {
    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true, require = 0)
    private void zeus$noHurtCam(PoseStack pose, float partial, CallbackInfo ci) {
        try {
            if (CameraMods.NoHurtCam.active()) ci.cancel();
        } catch (RuntimeException ignored) {
        }
    }
}
