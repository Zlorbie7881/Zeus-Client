package com.zlorbie788.zeus.mixin;

import com.zlorbie788.zeus.mods.PriceTags;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class ContainerPriceMixin {
    @Shadow
    protected int leftPos;

    @Shadow
    protected int topPos;

    @Inject(method = "render", at = @At("TAIL"), require = 0)
    private void zeus$priceTags(GuiGraphics g, int mouseX, int mouseY, float partial, CallbackInfo ci) {
        try {
            AbstractContainerScreen<?> self = (AbstractContainerScreen<?>) (Object) this;
            PriceTags.render(g, self.getMenu(), leftPos, topPos);
        } catch (RuntimeException ignored) {
        }
    }
}
