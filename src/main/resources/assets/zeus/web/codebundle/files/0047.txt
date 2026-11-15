package com.zlorbie788.zeus.mixin;

import com.zlorbie788.zeus.render.CameraControl;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    protected abstract void setPosition(double x, double y, double z);

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Inject(method = {"setup", "update"}, at = @At("RETURN"), require = 0)
    private void zeus$camera(CallbackInfo ci) {
        if (!CameraControl.overridingLook() || !CameraControl.stillHere()) return;
        CameraControl.sawCamera();
        float t = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        setRotation(CameraControl.yaw(t), CameraControl.pitch(t));
        if (CameraControl.overridingPosition()) {
            double[] e = CameraControl.eye(t);
            setPosition(e[0], e[1], e[2]);
            try {
                ((CameraAccessor) (Object) this).zeus$detached(true);
            } catch (ClassCastException | LinkageError ignored) {
            }
        }
    }
}
