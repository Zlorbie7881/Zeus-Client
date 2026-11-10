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

/**
 * No Fall and Anti Hunger: the "I am on the ground" flag of the movement packet your game was about to send is changed in
 * that same packet. The method is declared in the game's common packet listener (the one the play listener extends), which is
 * why the older hook on the play listener found nothing: a mixin only sees methods its target class declares itself.
 */
@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class SendPacketMixin {
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void zeus$send(Packet<?> packet, CallbackInfo ci) {
        // Vanilla Spoof first: rewrite the brand, drop the channel list. If it replaced the packet, the replacement is sent
        // here and the original never leaves (this is the one path that can run while a replacement is delivered, so the
        // module's own guard stops it from happening twice).
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
        // Fake Lag after it: the lag queue only ever holds what would really have been sent.
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
