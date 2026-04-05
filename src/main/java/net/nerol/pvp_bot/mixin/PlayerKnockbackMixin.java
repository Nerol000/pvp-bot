package net.nerol.pvp_bot.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.nerol.pvp_bot.bot.BotPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public class PlayerKnockbackMixin {

    /**
     * Player.causeExtraKnockback ends with:
     *   connection.send(new ClientboundSetEntityMotionPacket(entity));
     *   entity.setDeltaMovement(oldMovement);   ← resets knockback velocity
     *
     * For a BotPlayer target the packet is swallowed, so only the reset runs,
     * undoing the knockback entirely. We cancel the whole method for BotPlayer
     * targets and apply the knockback directly instead.
     */
    @Inject(method = "causeExtraKnockback", at = @At("HEAD"), cancellable = true)
    private void pvpbot_skipKnockbackResetForBot(Entity entity, float knockbackAmount, Vec3 oldMovement, CallbackInfo ci) {
        if (entity instanceof BotPlayer bot) {
            if (knockbackAmount > 0.0f) {
                Player attacker = (Player)(Object)this;
                bot.knockback(knockbackAmount,
                    net.minecraft.util.Mth.sin(attacker.getYRot() * (float)(Math.PI / 180.0)),
                    -net.minecraft.util.Mth.cos(attacker.getYRot() * (float)(Math.PI / 180.0)));
            }
            ci.cancel();
        }
    }
}
