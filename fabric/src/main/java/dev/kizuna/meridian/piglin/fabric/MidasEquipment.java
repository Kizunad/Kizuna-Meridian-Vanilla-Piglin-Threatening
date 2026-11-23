package dev.kizuna.meridian.piglin.fabric;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/** 点金所命中的装备，随宿主实体读写。 */
public interface MidasEquipment {
    record Mark(ItemStack stack, int expiresAt) { }

    void meridianPiglin$mark(EquipmentSlot slot);

    boolean meridianPiglin$matches(EquipmentSlot slot);
}
