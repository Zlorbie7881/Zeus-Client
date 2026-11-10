package com.zlorbie788.zeus.mixin;

import com.zlorbie788.zeus.mods.XRay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * X-Ray opacity, step three: how see-through. When the game's own block renderer writes the corners of a face it passes an
 * alpha value (always fully solid). For the blocks X-Ray draws faintly that value becomes the opacity you chose. Which block is
 * being written is known from the render-shape check that always comes just before, on the same thread.
 *
 * <p>Two shapes of the call are listed because the game changed it between versions; whichever exists is used.</p>
 */
@Mixin(net.minecraft.client.renderer.block.ModelBlockRenderer.class)
public abstract class XRayAlphaMixin {
    @ModifyArg(method = {"putQuadData", "putQuadWithTint", "renderQuadList"}, index = 6, require = 0, at = @At(value = "INVOKE",
        target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;putBulkData(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lnet/minecraft/client/renderer/block/model/BakedQuad;[FFFFF[II)V"))
    private float zeus$alpha(float alpha) {
        return XRay.alphaFor(alpha);
    }

    @ModifyArg(method = {"putQuadData", "putQuadWithTint", "renderQuadList"}, index = 6, require = 0, at = @At(value = "INVOKE",
        target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;putBulkData(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lnet/minecraft/client/renderer/block/model/BakedQuad;[FFFFF[IIZ)V"))
    private float zeus$alphaOld(float alpha) {
        return XRay.alphaFor(alpha);
    }
}
