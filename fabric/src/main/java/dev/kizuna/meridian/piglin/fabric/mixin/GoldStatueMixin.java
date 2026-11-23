package dev.kizuna.meridian.piglin.fabric.mixin;

import dev.kizuna.meridian.piglin.fabric.FeedingLungs;
import dev.kizuna.meridian.piglin.fabric.GoldForms;
import java.util.Collection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
abstract class GoldStatueMixin {
    @Inject(method = "onEffectAdded", at = @At("TAIL"), require = 1)
    private void meridianPiglin$startFeeding(MobEffectInstance effect, Entity source, CallbackInfo ci) {
        FeedingLungs.apply((LivingEntity) (Object) this, effect);
    }

    @Inject(method = "onEffectsRemoved", at = @At("TAIL"), require = 1)
    private void meridianPiglin$endFeeding(Collection<MobEffectInstance> effects, CallbackInfo ci) {
        FeedingLungs.remove((LivingEntity) (Object) this, effects);
    }

    @Inject(method = "tickEffects", at = @At("HEAD"), require = 1)
    private void meridianPiglin$feed(CallbackInfo ci) {
        if ((Object) this instanceof AbstractPiglin piglin && piglin.level() instanceof ServerLevel) {
            GoldForms.tickFeeding(piglin);
        }
    }

    @Inject(method = "isImmobile", at = @At("HEAD"), cancellable = true, require = 1)
    private void meridianPiglin$still(CallbackInfoReturnable<Boolean> result) {
        // 原版在此清除移动输入并跳过 Brain 的近战与弩击。
        if (GoldForms.isStatue((LivingEntity) (Object) this)) {
            result.setReturnValue(Boolean.TRUE);
        }
    }
}
