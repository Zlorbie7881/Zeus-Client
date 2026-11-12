package com.zlorbie788.zeus.mixin;

import com.zlorbie788.zeus.mods.QuickMods;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public abstract class GameModeHooksMixin {
    @Inject(method = "attack", at = @At("HEAD"), require = 0)
    private void zeus$beforeAttack(Player player, Entity target, CallbackInfo ci) {
        com.zlorbie788.zeus.mods.MaceMods.MaceDamage.beforeAttack(player, target);
        com.zlorbie788.zeus.mods.Combat.BreachSwap.beforeAttack();
        com.zlorbie788.zeus.mods.Combat.TotemHit.beforeAttack();
    }

    @Inject(method = "attack", at = @At("HEAD"), cancellable = true, require = 0)
    private void zeus$antiMiss(Player player, Entity target, CallbackInfo ci) {
        if (com.zlorbie788.zeus.mods.Combat.AntiMiss.blocks(target)) ci.cancel();
    }

    @Inject(method = "attack", at = @At("TAIL"), require = 0)
    private void zeus$attacked(Player player, Entity target, CallbackInfo ci) {
        QuickMods.CrystalOptimizer.hit(player, target);
        com.zlorbie788.zeus.mods.Combat.HitFx.burst(target);
    }

    @Inject(method = "destroyBlock", at = @At("HEAD"), cancellable = true, require = 0)
    private void zeus$destroy(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (QuickMods.AntiGhostBlock.holds()) cir.setReturnValue(false);
    }
}
