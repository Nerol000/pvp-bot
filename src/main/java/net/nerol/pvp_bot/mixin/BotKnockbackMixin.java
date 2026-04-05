package net.nerol.pvp_bot.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.nerol.pvp_bot.bot.BotPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Player.class)
public class BotKnockbackMixin {

    /**
     * Player.causeExtraKnockback ends with:
     *   connection.send(new ClientboundSetEntityMotionPacket(entity));
     *   entity.setDeltaMovement(oldMovement);   ← resets knockback velocity
     *
     * For a BotPlayer target the packet is swallowed, so only the reset runs,
     * undoing the knockback entirely. We cancel the whole method for BotPlayer
     * targets and apply the knockback directly instead.
     */
    @Redirect(
            method = "causeExtraKnockback",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/entity/Entity;hurtMarked:Z",
                    ordinal = 0
            )
    )
    private boolean pvpbot_skipResetForBot(Entity target) {
        return target.hurtMarked && !(target instanceof BotPlayer);
    }
}
