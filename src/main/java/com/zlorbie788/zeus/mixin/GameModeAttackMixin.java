package com.zlorbie788.zeus.mixin;

import com.zlorbie788.zeus.mods.Ghost;
import com.zlorbie788.zeus.mods.Parity;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
public abstract class GameModeAttackMixin {
    @Inject(method = "attack(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void zeus$ghostRhythm(Player player, Entity target, CallbackInfo ci) {
        if (Ghost.skipSwing()) {
            ci.cancel();
            return;
        }
        Parity.WTap.beforeSwing(player);
    }
}
