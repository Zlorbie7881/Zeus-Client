package com.zlorbie788.zeus.mixin;

import com.zlorbie788.zeus.render.CameraControl;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** While Freecam is on, your player sees the movement keys as released (the camera reads them instead). */
@Mixin(KeyMapping.class)
public abstract class KeyMappingMixin {
    @Inject(method = "isDown", at = @At("RETURN"), cancellable = true, require = 0)
    private void zeus$down(CallbackInfoReturnable<Boolean> cir) {
        if (CameraControl.freezing()) cir.setReturnValue(CameraControl.filterDown((KeyMapping) (Object) this, cir.getReturnValue()));
    }

    @Inject(method = "consumeClick", at = @At("RETURN"), cancellable = true, require = 0)
    private void zeus$click(CallbackInfoReturnable<Boolean> cir) {
        if (CameraControl.freezing()) cir.setReturnValue(CameraControl.filterClick((KeyMapping) (Object) this, cir.getReturnValue()));
    }
}
