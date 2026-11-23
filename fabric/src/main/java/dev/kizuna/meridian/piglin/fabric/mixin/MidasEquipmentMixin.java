package dev.kizuna.meridian.piglin.fabric.mixin;

import dev.kizuna.meridian.piglin.fabric.MidasEquipment;
import dev.kizuna.meridian.piglin.fabric.MidasTouch;
import java.util.Collection;
import java.util.EnumMap;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
abstract class MidasEquipmentMixin implements MidasEquipment {
    @Unique private static final String SAVE_KEY = "meridian_piglin:midased_";
    @Unique private final EnumMap<EquipmentSlot, Mark> meridianPiglin$marks = new EnumMap<>(EquipmentSlot.class);

    @Override
    public void meridianPiglin$mark(EquipmentSlot slot) {
        LivingEntity entity = (LivingEntity) (Object) this;
        int expiresAt = entity.tickCount + MidasTouch.DURATION_TICKS;
        meridianPiglin$marks.put(slot, new Mark(entity.getItemBySlot(slot), expiresAt));
    }

    @Override
    public boolean meridianPiglin$matches(EquipmentSlot slot) {
        LivingEntity entity = (LivingEntity) (Object) this;
        Mark mark = meridianPiglin$marks.get(slot);
        return mark != null && mark.expiresAt() - entity.tickCount > 0 && !mark.stack().isEmpty()
                && mark.stack() == entity.getItemBySlot(slot);
    }

    @Inject(method = "onEquipItem", at = @At("HEAD"), require = 1)
    private void meridianPiglin$unequip(EquipmentSlot slot, ItemStack oldStack, ItemStack newStack, CallbackInfo ci) {
        Mark mark = meridianPiglin$marks.get(slot);
        if (mark != null && mark.stack() == oldStack && oldStack != newStack) {
            meridianPiglin$marks.remove(slot);
        }
    }

    @Inject(method = "onEffectsRemoved", at = @At("HEAD"), require = 1)
    private void meridianPiglin$clear(Collection<MobEffectInstance> removed, CallbackInfo ci) {
        if (!meridianPiglin$marks.isEmpty() && removed.stream().anyMatch(effect -> effect.is(MidasTouch.EFFECT))) {
            meridianPiglin$marks.clear();
        }
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"), require = 1)
    private void meridianPiglin$save(ValueOutput output, CallbackInfo ci) {
        LivingEntity entity = (LivingEntity) (Object) this;
        for (var entry : meridianPiglin$marks.entrySet()) {
            if (meridianPiglin$matches(entry.getKey())) {
                output.putInt(SAVE_KEY + entry.getKey().getName(), entry.getValue().expiresAt() - entity.tickCount);
            }
        }
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"), require = 1)
    private void meridianPiglin$load(ValueInput input, CallbackInfo ci) {
        meridianPiglin$marks.clear();
        LivingEntity entity = (LivingEntity) (Object) this;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            int remaining = input.getIntOr(SAVE_KEY + slot.getName(), 0);
            if (remaining > 0) {
                meridianPiglin$marks.put(slot, new Mark(entity.getItemBySlot(slot), entity.tickCount + remaining));
            }
        }
    }
}
