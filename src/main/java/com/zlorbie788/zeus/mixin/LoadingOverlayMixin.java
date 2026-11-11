package com.zlorbie788.zeus.mixin;

import com.zlorbie788.zeus.ui.ZeusLoading;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private ReloadInstance reload;
    @Shadow @Final private boolean fadeIn;
    @Shadow private float currentProgress;
    @Shadow private long fadeOutStart;
    @Shadow private long fadeInStart;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
    private void zeus$render(GuiGraphics g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        try {
            int w = g.guiWidth();
            int h = g.guiHeight();
            long now = Util.getMillis();
            if (this.fadeIn && this.fadeInStart == -1L) this.fadeInStart = now;
            float out = this.fadeOutStart > -1L ? (now - this.fadeOutStart) / 1000.0F : -1.0F;
            float in = this.fadeInStart > -1L ? (now - this.fadeInStart) / 500.0F : -1.0F;
            float alpha;
            boolean under = false;
            if (out >= 1.0F) {
                under = true;
                alpha = 1.0F - Mth.clamp(out - 1.0F, 0.0F, 1.0F);
            } else if (this.fadeIn) {
                under = in < 1.0F;
                alpha = Mth.clamp(in, 0.15F, 1.0F);
            } else {
                alpha = 1.0F;
            }
            float actual = this.reload.getActualProgress();
            boolean done = this.reload.isDone();
            if (under) {
                if (this.minecraft.screen != null) this.minecraft.screen.renderWithTooltipAndSubtitles(g, mouseX, mouseY, partialTick);
                else this.minecraft.gui.renderDeferredSubtitles();
                g.nextStratum();
            }
            if (!ZeusLoading.startup(this, g, w, h, actual, done, alpha)) return;
            this.currentProgress = Mth.clamp(this.currentProgress * 0.95F + actual * 0.050000012F, 0.0F, 1.0F);
            if (out >= 2.0F) this.minecraft.setOverlay(null);
            ci.cancel();
        } catch (RuntimeException | LinkageError e) {
        }
    }
}
