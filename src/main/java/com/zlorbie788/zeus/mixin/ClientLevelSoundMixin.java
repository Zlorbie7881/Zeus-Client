package com.zlorbie788.zeus.mixin;

import com.zlorbie788.zeus.mods.SoundEsp;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hands every sound the client is about to play to Sound ESP: the id, the category, the position and who caused it.
 * Both entry points are hooked, because sounds the server sends and sounds played locally (records, for example) take
 * different paths. Nothing is changed or cancelled here, the sound plays exactly as it normally would.
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelSoundMixin {

    @Inject(method = "playSeededSound(Lnet/minecraft/world/entity/Entity;DDDLnet/minecraft/core/Holder;"
            + "Lnet/minecraft/sounds/SoundSource;FFJ)V", at = @At("HEAD"), require = 0)
    private void zeus$seededSound(Entity except, double x, double y, double z, Holder<SoundEvent> sound,
                                  SoundSource source, float volume, float pitch, long seed, CallbackInfo ci) {
        SoundEvent event = sound.value();
        if (event != null) SoundEsp.capture(except, x, y, z, event.location(), source, volume, pitch);
    }

    @Inject(method = "playSeededSound(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;"
            + "Lnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V", at = @At("HEAD"), require = 0)
    private void zeus$entitySound(Entity except, Entity source, Holder<SoundEvent> sound, SoundSource category,
                                  float volume, float pitch, long seed, CallbackInfo ci) {
        SoundEvent event = sound.value();
        if (event != null && source != null) {
            SoundEsp.capture(except, source.getX(), source.getY(), source.getZ(), event.location(), category,
                    volume, pitch);
        }
    }

    @Inject(method = "playLocalSound(DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFZ)V",
            at = @At("HEAD"), require = 0)
    private void zeus$localSound(double x, double y, double z, SoundEvent sound, SoundSource source,
                                 float volume, float pitch, boolean distanceDelay, CallbackInfo ci) {
        if (sound != null) SoundEsp.capture(null, x, y, z, sound.location(), source, volume, pitch);
    }

    @Inject(method = "playLocalSound(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/sounds/SoundEvent;"
            + "Lnet/minecraft/sounds/SoundSource;FF)V", at = @At("HEAD"), require = 0)
    private void zeus$entityLocalSound(Entity entity, SoundEvent sound, SoundSource source, float volume,
                                       float pitch, CallbackInfo ci) {
        if (sound != null && entity != null) {
            SoundEsp.capture(entity, entity.getX(), entity.getY(), entity.getZ(), sound.location(), source,
                    volume, pitch);
        }
    }
}
