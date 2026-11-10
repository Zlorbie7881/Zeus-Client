package com.zlorbie788.zeus.mixin;

import com.zlorbie788.zeus.mods.QuickMods;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Skin Change: the skin a player is drawn with can be swapped for another player's, on your screen only. */
@Mixin(AbstractClientPlayer.class)
public abstract class SkinMixin {
    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true, require = 0)
    private void zeus$skin(CallbackInfoReturnable<PlayerSkin> cir) {
        PlayerSkin replacement = QuickMods.SkinChange.replace((AbstractClientPlayer) (Object) this);
        if (replacement != null) cir.setReturnValue(replacement);
    }
}
