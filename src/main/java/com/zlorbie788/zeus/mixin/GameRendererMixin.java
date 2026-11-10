package com.zlorbie788.zeus.mixin;

import com.zlorbie788.zeus.render.Projector;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hands the exact field of view of this frame to the overlay projector. The renderer multiplies the FOV option by the
 * sprint/fly modifier and by the FOV-effect scale, so reading it here is what keeps boxes, tracers and labels lined up
 * while sprinting, while zoomed and with a zoomed-out third-person view. Only the world FOV (the one that uses the
 * setting) is taken; the separate pass for the hand is ignored.
 */
@Mixin(value = GameRenderer.class, priority = 1500)
public abstract class GameRendererMixin {
    @Inject(method = "getFov", at = @At("RETURN"), require = 0)
    private void zeus$fov(Camera camera, float partial, boolean useFovSetting, CallbackInfoReturnable<Float> cir) {
        try {
            if (!useFovSetting) return;
            float fov = cir.getReturnValueF();
            // The zoom is applied here, to the number the renderer uses, instead of by changing the FOV option every frame
            // (which makes the game redo its chunk culling each time). The overlay reads the same number, so it stays lined up.
            float z = com.zlorbie788.zeus.mods.Extras.Zoom.factorNow();
            if (z > 1.001f) {
                fov = (float) Math.toDegrees(2.0 * Math.atan(Math.tan(Math.toRadians(fov) / 2.0) / z));
                cir.setReturnValue(fov);
            }
            Projector.offerFov(fov);
        } catch (RuntimeException ignored) {
            // the option value is still used as a fallback
        }
    }
}
