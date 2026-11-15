package com.zlorbie788.zeus.mixin;

import com.zlorbie788.zeus.mods.Ghost;
import com.zlorbie788.zeus.mods.Oracle;
import com.zlorbie788.zeus.mods.Parity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
public abstract class GameModeAttackMixin {
    @Inject(method = "startDestroyBlock", at = @At("HEAD"), cancellable = true, require = 0)
    private void zeus$oneBreak(net.minecraft.core.BlockPos pos, net.minecraft.core.Direction face,
                              org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        if (!com.zlorbie788.zeus.core.Stealth.allowBreak(Minecraft.getInstance())) cir.setReturnValue(false);
    }

    @Inject(method = "attack(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void zeus$ghostRhythm(Player player, Entity target, CallbackInfo ci) {
        if (Ghost.skipSwing()) {
            ci.cancel();
            return;
        }
        // Grim's SelfInteract and InvalidInteractTarget: an attack packet aimed at yourself, a corpse
        // or an entity the server already removed is a flag with nothing behind it.
        if (target == player || target.isRemoved() || !target.isAlive()) {
            ci.cancel();
            return;
        }
        if (!com.zlorbie788.zeus.core.Stealth.allowAttack(Minecraft.getInstance())) {
            ci.cancel();
            return;
        }
        if (!Oracle.allowAttack(target)) {
            ci.cancel();
            return;
        }
        Parity.WTap.beforeSwing(player);
    }
}
