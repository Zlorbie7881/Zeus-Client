package com.zlorbie788.zeus.mixin;

import com.zlorbie788.zeus.mods.MultiUse;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Lets two buttons work at the same time. The game normally ignores the attack button while you use an item (eat, draw a
 * bow, hold a shield) and ignores the use button while you break a block. With Multi Use on, those checks answer "no" while
 * the button that should be allowed is held, so the game goes on to handle both buttons in the same tick, exactly like it
 * does for either one alone. Nothing new is sent to the server: it is the same attack and use packets the buttons
 * always produce.
 */
@Mixin(Minecraft.class)
public abstract class MultiUseMixin {
    /** The key handling: "are you using an item" decides whether the attack clicks are thrown away. */
    @Redirect(method = {"handleKeybinds"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isUsingItem()Z", ordinal = 0), require = 0)
    private boolean zeus$keysUsing(LocalPlayer player) {
        return MultiUse.keysUsing(player.isUsingItem(), ((Minecraft) (Object) this).options.keyUse.isDown());
    }

    /** Holding the attack button breaks blocks only when you are not using an item. */
    @Redirect(method = {"continueAttack"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isUsingItem()Z"), require = 0)
    private boolean zeus$mineUsing(LocalPlayer player) {
        return MultiUse.mineUsing(player.isUsingItem());
    }

    /** Using an item is refused while a block is being broken. */
    @Redirect(method = {"startUseItem"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;isDestroying()Z"), require = 0)
    private boolean zeus$useMining(MultiPlayerGameMode mode) {
        return MultiUse.useMining(mode.isDestroying());
    }
}
