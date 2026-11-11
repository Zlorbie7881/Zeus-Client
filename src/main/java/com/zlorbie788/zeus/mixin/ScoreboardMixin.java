package com.zlorbie788.zeus.mixin;

import com.zlorbie788.zeus.mods.Scoreboards;
import com.zlorbie788.zeus.mods.StreamerPrivacy;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Scoreboard.class)
public abstract class ScoreboardMixin {
    @Inject(method = "getDisplayObjective", at = @At("RETURN"), cancellable = true, require = 0)
    private void zeus$hideSidebar(DisplaySlot slot, CallbackInfoReturnable<Objective> cir) {
        if (slot != null && cir.getReturnValue() != null && (Scoreboards.hiding() || StreamerPrivacy.hideSidebar()) && slot.getSerializedName().startsWith("sidebar")) {
            cir.setReturnValue(null);
        }
    }
}
