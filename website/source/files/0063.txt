package com.zlorbie788.zeus.mixin;

import com.zlorbie788.zeus.mods.AntiVanish;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Captures tab-list removals so Anti Vanish can correlate them with entities still visible client-side. */
@Mixin(ClientPacketListener.class)
public abstract class VanishTabRemoveMixin {
    @Inject(method = "handlePlayerInfoRemove", at = @At("HEAD"), require = 0)
    private void zeus$tabRemove(ClientboundPlayerInfoRemovePacket packet, CallbackInfo ci) {
        AntiVanish.noteTabRemoval(packet);
    }
}
