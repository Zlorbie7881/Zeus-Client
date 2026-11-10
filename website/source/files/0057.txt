package com.zlorbie788.zeus.mixin;

import com.zlorbie788.zeus.mods.ScreenshotTweaks;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Every line that goes into the chat passes here; the "Saved screenshot as ..." line gets its buttons (see {@link ScreenshotTweaks}). */
@Mixin(ChatComponent.class)
public abstract class ShotChatMixin {
    @ModifyVariable(method = "addMessage(Lnet/minecraft/network/chat/Component;)V", at = @At("HEAD"), argsOnly = true, require = 0)
    private Component zeus$shotOne(Component message) {
        return ScreenshotTweaks.decorate(message);
    }
}
