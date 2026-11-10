package com.zlorbie788.zeus.mixin;

import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets the freecam draw your own body (the camera treats itself as "detached", like the third-person view). */
@Mixin(Camera.class)
public interface CameraAccessor {
    @Accessor("detached")
    void zeus$detached(boolean value);
}
