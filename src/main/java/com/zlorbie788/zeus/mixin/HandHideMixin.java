package com.zlorbie788.zeus.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zlorbie788.zeus.mods.KnifeSwing;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class HandHideMixin {
    @Inject(method = "renderHandsWithItems", at = @At("HEAD"), cancellable = true, require = 0)
    private void zeus$knife(float partialTick, PoseStack poseStack, SubmitNodeCollector collector, LocalPlayer player, int light, CallbackInfo ci) {
        if (KnifeSwing.hidesHand()) ci.cancel();
    }
}
