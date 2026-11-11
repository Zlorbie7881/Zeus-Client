package com.zlorbie788.zeus.mixin;

import com.zlorbie788.zeus.mods.MultiUse;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Minecraft.class)
public abstract class MultiUseMixin {

    @Redirect(method = {"handleKeybinds"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isUsingItem()Z", ordinal = 0), require = 0)
    private boolean zeus$keysUsing(LocalPlayer player) {
        return MultiUse.keysUsing(player.isUsingItem(), ((Minecraft) (Object) this).options.keyUse.isDown());
    }

    @Redirect(method = {"continueAttack"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isUsingItem()Z"), require = 0)
    private boolean zeus$mineUsing(LocalPlayer player) {
        return MultiUse.mineUsing(player.isUsingItem());
    }

    @Redirect(method = {"startUseItem"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;isDestroying()Z"), require = 0)
    private boolean zeus$useMining(MultiPlayerGameMode mode) {
        return MultiUse.useMining(mode.isDestroying());
    }
}
