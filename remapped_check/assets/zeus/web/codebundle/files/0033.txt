package com.zlorbie788.zeus.mixin;

import com.zlorbie788.zeus.mods.Activity;
import com.zlorbie788.zeus.mods.Combat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import com.zlorbie788.zeus.mods.AdvancedFeatures;
import com.zlorbie788.zeus.mods.MediaBaseFinder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Anti Knockback: the push the game sends when you are hit is dropped on arrival. Also lets Activity count block changes. */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Inject(method = "handleSetEntityMotion", at = @At("HEAD"), cancellable = true, require = 0)
    private void zeus$motion(ClientboundSetEntityMotionPacket packet, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (Combat.AntiKnockback.blocks() && mc.player != null && packet.getId() == mc.player.getId()) ci.cancel();
    }

    @Inject(method = "handleBlockUpdate", at = @At("HEAD"), require = 0)
    private void zeus$blockUpdate(ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
        Activity.note(packet.getPos(), packet.getBlockState());
    }

    @Inject(method = "handleChunkBlocksUpdate", at = @At("HEAD"), require = 0)
    private void zeus$sectionUpdate(ClientboundSectionBlocksUpdatePacket packet, CallbackInfo ci) {
        Activity.beginBatch();
        try {
            packet.runUpdates(Activity::note);
        } finally {
            Activity.endBatch();
        }
    }


    @Inject(method = "handlePlayerInfoUpdate", at = @At("HEAD"), require = 0)
    private void zeus$playerInfoUpdate(ClientboundPlayerInfoUpdatePacket packet, CallbackInfo ci) {
        MediaBaseFinder.notePlayerInfoUpdate(packet);
        com.zlorbie788.zeus.mods.StaffHud.notePlayerInfoUpdate(packet);
    }

    /** Entity event 35 is "a totem of undying was used", 2 is the hurt flash every damaged living entity broadcasts. */
    @Inject(method = "handleEntityEvent", at = @At("HEAD"), require = 0)
    private void zeus$event(ClientboundEntityEventPacket packet, CallbackInfo ci) {
        int event = packet.getEventId();
        if (event != 2 && event != 35) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        net.minecraft.world.entity.Entity who = packet.getEntity(mc.level);
        if (who == null) return;
        // event 2 is the hurt flash, 35 is a totem pop: both mean our last swing at that entity landed
        com.zlorbie788.zeus.core.Hits.confirm(who.getId());
        if (event == 35) com.zlorbie788.zeus.mods.NetPanels.totemPopped(who);
    }

    /** The server says who damaged what: the cleanest hit confirmation there is. */
    @Inject(method = "handleDamageEvent", at = @At("HEAD"), require = 0)
    private void zeus$damageEvent(ClientboundDamageEventPacket packet, CallbackInfo ci) {
        com.zlorbie788.zeus.core.Hits.confirm(packet.entityId());
    }

    /**
     * Removed entities are the only answer an end crystal gives (it is not a living entity, so it never sends a hurt event),
     * and the same packet tells the tracker that a mob we hit is gone.
     */
    @Inject(method = "handleRemoveEntities", at = @At("HEAD"), require = 0)
    private void zeus$removed(ClientboundRemoveEntitiesPacket packet, CallbackInfo ci) {
        for (int id : packet.getEntityIds()) com.zlorbie788.zeus.core.Hits.confirm(id);
    }

    /** Boat NoClip: the server's "move the boat back here" correction is dropped on arrival (when the module's setting is on). */
    @Inject(method = "handleMoveVehicle", at = @At("HEAD"), cancellable = true, require = 0)
    private void zeus$moveVehicle(ClientboundMoveVehiclePacket packet, CallbackInfo ci) {
        if (com.zlorbie788.zeus.mods.AutomationPlus.BoatNoClip.ignoresCorrections()) ci.cancel();
    }
}
