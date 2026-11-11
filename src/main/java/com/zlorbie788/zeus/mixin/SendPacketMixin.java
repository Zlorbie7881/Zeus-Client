package com.zlorbie788.zeus.mixin;

import com.zlorbie788.zeus.mods.Movement;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import com.zlorbie788.zeus.mods.AdvancedFeatures;
import com.zlorbie788.zeus.mods.FakeLag;
import com.zlorbie788.zeus.mods.MediaBaseFinder;
import com.zlorbie788.zeus.mods.Spoof;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class SendPacketMixin {
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void zeus$send(Packet<?> packet, CallbackInfo ci) {
        Packet<?> out = Spoof.intercept(packet);
        if (out == null) {
            ci.cancel();
            return;
        }
        if (out != packet) {
            if (Spoof.canDeliver()) {
                Spoof.deliver(out);
                ci.cancel();
            }
            return;
        }
        boolean mace = com.zlorbie788.zeus.mods.MaceMods.MaceDamage.sending();
        if (!mace && FakeLag.holds(packet)) {
            ci.cancel();
            return;
        }
        Movement.NoFall.ping();
        if (packet instanceof ServerboundChatCommandPacket command) {
            MediaBaseFinder.noteOutboundCommand(command.command());
        }
        if (mace) return;
        if (packet instanceof ServerboundMovePlayerPacket move) {
            Movement.NoFall.rewrite(move);
            Movement.AntiHunger.rewrite(move);
        } else if (Movement.AntiHunger.drops(packet)) {
            ci.cancel();
        }
    }
}
