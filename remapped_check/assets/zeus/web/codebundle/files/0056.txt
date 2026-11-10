package com.zlorbie788.zeus.mixin;

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerboundMovePlayerPacket.class)
public interface MovePacketAccessor {
    @Mutable
    @Accessor("onGround")
    void zeus$onGround(boolean value);
}
