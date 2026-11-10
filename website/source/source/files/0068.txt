package com.zlorbie788.zeus.mixin;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.zlorbie788.zeus.mods.XRay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * X-Ray opacity, step three: how see-through. Every way of drawing a block (the game's own, Fabric's default renderer) ends by
 * writing a colour for each corner into the chunk's mesh, and this is that last step. While the chunk builder writes a block
 * that X-Ray draws faintly, the alpha of the colour is capped at the opacity chosen. It only does anything on the helper
 * threads that build chunk meshes, and only while X-Ray's see-through mode is on, so nothing else in the game is touched.
 *
 * <p>The cap is a minimum, not a multiplication, so it does not matter if one of these two methods calls the other.</p>
 */
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
