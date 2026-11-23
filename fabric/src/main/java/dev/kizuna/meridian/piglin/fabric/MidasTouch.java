package dev.kizuna.meridian.piglin.fabric;

import dev.kizuna.meridian.technique.fabric.world.TechniqueQuietEffect;
import dev.kizuna.meridian.vanilla.fabric.DamageEquipmentOverrides;
import dev.kizuna.meridian.vanilla.fabric.VanillaDamageFabric;
import dev.kizuna.meridian.vanilla.fabric.VanillaDamageTypeTags;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/** 点金只在效果尚存且命中时的那件装备仍在原槽位时生效。 */
public final class MidasTouch implements DamageEquipmentOverrides.Provider {
    public static final int DURATION_TICKS = 300;
    private static final int COLOR = 0xD6A642;
    private static final Identifier HIT = Identifier.parse("meridian_piglin:midas_touch");
    private static final Set<String> METALS = Set.of("iron", "chainmail", "copper", "diamond", "netherite");
    private static final Map<String, Item> GOLD = Map.ofEntries(
            Map.entry("helmet", Items.GOLDEN_HELMET), Map.entry("chestplate", Items.GOLDEN_CHESTPLATE),
            Map.entry("leggings", Items.GOLDEN_LEGGINGS), Map.entry("boots", Items.GOLDEN_BOOTS),
            Map.entry("sword", Items.GOLDEN_SWORD), Map.entry("axe", Items.GOLDEN_AXE),
            Map.entry("pickaxe", Items.GOLDEN_PICKAXE), Map.entry("shovel", Items.GOLDEN_SHOVEL),
            Map.entry("hoe", Items.GOLDEN_HOE), Map.entry("spear", Items.GOLDEN_SPEAR));
    public static final Holder<MobEffect> EFFECT = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,
            Identifier.parse("meridian_piglin:midased"), new TechniqueQuietEffect(MobEffectCategory.HARMFUL, COLOR));

    static void register() {
        DamageEquipmentOverrides.register(new MidasTouch());
        VanillaDamageFabric.registerDamageObserver(event -> {
            if (event.input().guarded().isPresent() || event.bodyDead()
                    || !event.source().typeHolder().unwrapKey().orElseThrow().identifier().equals(HIT)) {
                return;
            }
            event.input().hit().ifPresent(hit -> apply(event.target(), hit.getPartId()));
        });
    }

    static void apply(LivingEntity target, String part) {
        var armor = VanillaDamageFabric.inspectBody(target)
                .flatMap(body -> VanillaDamageFabric.coveredEquipmentSlot(body, part));
        boolean markedArmor = armor.filter(slot -> mark(target, slot)).isPresent();
        boolean markedWeapon = hand(target, part).filter(slot -> mark(target, slot)).isPresent();
        if (markedArmor || markedWeapon) {
            target.addEffect(new MobEffectInstance(EFFECT, DURATION_TICKS));
        }
    }

    static boolean hasMetalArmor(LivingEntity target) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR
                    && equivalent(target.getItemBySlot(slot), slot).isPresent()) {
                return true;
            }
        }
        return false;
    }

    private static Optional<EquipmentSlot> hand(LivingEntity target, String part) {
        if (!part.endsWith("_hand")) {
            return Optional.empty();
        }
        HumanoidArm side = HumanoidArm.RIGHT;
        if (part.startsWith("left_")) {
            side = HumanoidArm.LEFT;
        }
        if (side == target.getMainArm()) {
            return Optional.of(EquipmentSlot.MAINHAND);
        }
        return Optional.of(EquipmentSlot.OFFHAND);
    }

    private static boolean mark(LivingEntity target, EquipmentSlot slot) {
        if (equivalent(target.getItemBySlot(slot), slot).isEmpty()) {
            return false;
        }
        ((MidasEquipment) target).meridianPiglin$mark(slot);
        return true;
    }

    private static Optional<Item> equivalent(ItemStack stack, EquipmentSlot slot) {
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!id.getNamespace().equals("minecraft")) {
            return Optional.empty();
        }
        String path = id.getPath();
        int separator = path.indexOf('_');
        if (separator < 0 || !METALS.contains(path.substring(0, separator))) {
            return Optional.empty();
        }
        Item gold = GOLD.get(path.substring(separator + 1));
        if (gold == null) {
            return Optional.empty();
        }
        var equipment = gold.components().get(DataComponents.EQUIPPABLE);
        if (equipment != null && equipment.slot() == slot) {
            return Optional.of(gold);
        }
        if (equipment == null && slot.getType() == EquipmentSlot.Type.HAND) {
            return Optional.of(gold);
        }
        return Optional.empty();
    }

    @Override
    public Optional<VanillaDamageFabric.LocalArmor> armor(LivingEntity target, String part,
                                                           Optional<EquipmentSlot> slot) {
        return slot.flatMap(value -> active(target, value).map(item -> {
            var attributes = item.components().getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS,
                    ItemAttributeModifiers.EMPTY);
            return new VanillaDamageFabric.LocalArmor(attributes.compute(Attributes.ARMOR, 0, value),
                    attributes.compute(Attributes.ARMOR_TOUGHNESS, 0, value));
        }));
    }

    @Override
    public Optional<VanillaDamageFabric.WeaponDamageCap> weapon(VanillaDamageFabric.DamageProvider source) {
        if (!source.source().is(VanillaDamageTypeTags.MELEE)
                || source.source().is(VanillaDamageTypeTags.LEG)
                || source.directEntity().orElse(null) != source.attacker().orElse(null)) {
            return Optional.empty();
        }
        EquipmentSlot hand = EquipmentSlot.MAINHAND;
        if (source.source().is(VanillaDamageTypeTags.OFF_HAND)) {
            hand = EquipmentSlot.OFFHAND;
        }
        var attacker = source.attacker();
        if (attacker.isEmpty()) {
            return Optional.empty();
        }
        return active(attacker.get(), hand).map(item -> {
            var attributes = item.components().getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS,
                    ItemAttributeModifiers.EMPTY);
            double cap = attributes.compute(Attributes.ATTACK_DAMAGE, 1, EquipmentSlot.MAINHAND);
            return new VanillaDamageFabric.WeaponDamageCap(source.weapon(), cap);
        });
    }

    private static Optional<Item> active(LivingEntity target, EquipmentSlot slot) {
        if (target.hasEffect(EFFECT) && ((MidasEquipment) target).meridianPiglin$matches(slot)) {
            return equivalent(target.getItemBySlot(slot), slot);
        }
        return Optional.empty();
    }
}
