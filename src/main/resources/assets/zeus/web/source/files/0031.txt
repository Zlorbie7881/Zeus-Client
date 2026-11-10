package com.zlorbie788.zeus.mixin;

import com.zlorbie788.zeus.render.CameraControl;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Freecam and Free Look: after the game has placed its camera on your player, the camera is moved to where the module
 * wants it. Only the picture changes. Both method names are listed because the game's camera class has been reworked
 * between versions; if neither is found the module says so and switches itself off.
 */
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
                // without this your own body is not drawn; the freecam still works
            }
        }
    }
}
