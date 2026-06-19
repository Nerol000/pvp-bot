package net.nerol.pvp_bot.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.nerol.pvp_bot.bot.BotPlayer;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Courtesy of HeRoBot
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends Entity {

    @Shadow
    protected abstract float getFlyingSpeed();

    protected LivingEntityMixin(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Inject(
            method = "getKnockback(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;)F",
            at = @At("HEAD"),
            cancellable = true
    )
    private void modifyKnockback(Entity entity, DamageSource damageSource, CallbackInfoReturnable<Float> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (entity instanceof LivingEntity target && target.invulnerableTime < 20) {
            cir.setReturnValue(0.0F);
            return;
        }

        float baseKnockback = (float) self.getAttributeValue(Attributes.ATTACK_KNOCKBACK);
        Level level = self.level();

        if (level instanceof ServerLevel serverLevel) {
            float modifiedKnockback = EnchantmentHelper.modifyKnockback(
                    serverLevel,
                    self.getMainHandItem(),
                    entity,
                    damageSource,
                    baseKnockback
            );
            cir.setReturnValue(modifiedKnockback / 2.0F);
        } else {
            cir.setReturnValue(baseKnockback / 2.0F);
        }
    }

    @Inject(method = "knockback", at = @At("HEAD"), cancellable = true)
    private void pvpbot_noKnockbackWhileBlocking(double strength, double x, double z, DamageSource source, float damage, boolean flag, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof BotPlayer && self.isBlocking()) {
            ci.cancel();
        }
    }

    /**
     * Shield Stunning and fixing the shield
     */
    @Unique
    private boolean blockedHit = false;

    // Detect when a hit was blocked by a shield
    @WrapOperation(method = "hurtServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;applyItemBlocking(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)F"))
    private float trackBlockedHit(LivingEntity instance, ServerLevel serverLevel, DamageSource damageSource, float damageAmount, Operation<Float> original) {
        float blockedAmount = original.call(instance, serverLevel, damageSource, damageAmount);
        blockedHit = blockedAmount > 0.0F && instance instanceof Player;
        return blockedAmount;
    }

    @ModifyReturnValue(method = "hurtServer", at = @At("RETURN"))
    private boolean handleBlockedHit(boolean original) {
        // Still only works on non-botPlayer players
        if (blockedHit) {
            blockedHit = false;
            // Shield Stunning: Skip the damage tick completely, no invul frames - matches with servers now
            this.invulnerableTime = 0;
            return false;
        }
        return original;
    }
}

