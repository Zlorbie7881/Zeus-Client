package com.zlorbie788.zeus.mixin;

import com.zlorbie788.zeus.mods.ScreenshotTweaks;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** The same as {@link ShotChatMixin} for the longer form of addMessage, in case the screenshot line arrives that way. A message that already has its buttons is left alone. */
@Mixin(ChatComponent.class)
public abstract class ShotChatFullMixin {
    @ModifyVariable(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V", at = @At("HEAD"), argsOnly = true, require = 0)
    private Component zeus$shotFull(Component message) {
        return ScreenshotTweaks.decorate(message);
    }
}
