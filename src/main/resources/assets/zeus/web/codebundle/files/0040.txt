package com.zlorbie788.zeus.mixin;

import com.zlorbie788.zeus.render.CameraControl;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** While the camera is detached the mouse turns the camera and not your player. */
@Mixin(Entity.class)
public abstract class EntityTurnMixin {
    @Inject(method = "turn", at = @At("HEAD"), cancellable = true, require = 0)
    private void zeus$turn(double yRot, double xRot, CallbackInfo ci) {
        if (CameraControl.overridingLook() && (Object) this == Minecraft.getInstance().player) {
            CameraControl.turn(yRot, xRot);
            ci.cancel();
        }
    }
}
