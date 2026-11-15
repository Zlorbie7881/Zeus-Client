package com.zlorbie788.zeus.mixin;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.zlorbie788.zeus.mods.XRay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(BufferBuilder.class)
public abstract class XRayColorMixin {
    @ModifyVariable(method = "setColor(IIII)Lcom/mojang/blaze3d/vertex/VertexConsumer;", at = @At("HEAD"), argsOnly = true, ordinal = 3, require = 0)
    private int zeus$alpha(int alpha) {
        return XRay.capAlpha(alpha);
    }

    @ModifyVariable(method = "setColor(I)Lcom/mojang/blaze3d/vertex/VertexConsumer;", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private int zeus$argb(int argb) {
        return XRay.capArgb(argb);
    }
}
