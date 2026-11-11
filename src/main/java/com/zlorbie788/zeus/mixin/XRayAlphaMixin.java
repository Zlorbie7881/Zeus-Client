package com.zlorbie788.zeus.mixin;

import com.zlorbie788.zeus.mods.XRay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

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
