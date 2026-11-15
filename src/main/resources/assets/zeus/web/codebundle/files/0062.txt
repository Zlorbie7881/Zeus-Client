package com.zlorbie788.zeus.mixin;

import com.zlorbie788.zeus.render.Projector;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = GameRenderer.class, priority = 1500)
public abstract class GameRendererMixin {
    @Inject(method = "getFov", at = @At("RETURN"), require = 0)
    private void zeus$fov(Camera camera, float partial, boolean useFovSetting, CallbackInfoReturnable<Float> cir) {
        try {
            if (!useFovSetting) return;
            float fov = cir.getReturnValueF();
            float z = com.zlorbie788.zeus.mods.Extras.Zoom.factorNow();
            if (z > 1.001f) {
                fov = (float) Math.toDegrees(2.0 * Math.atan(Math.tan(Math.toRadians(fov) / 2.0) / z));
                cir.setReturnValue(fov);
            }
            Projector.offerFov(fov);
        } catch (RuntimeException ignored) {
        }
    }
}
