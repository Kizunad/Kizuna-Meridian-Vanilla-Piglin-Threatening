package dev.kizuna.meridian.piglin.fabric;

import dev.kizuna.meridian.vanilla.fabric.VanillaDamageFabric;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class MidasGameTest {
    private static final float SWING = 8;
    private static final double GOLD_SWORD_DAMAGE = 4;
    private static final double PALM = 0.01;
    private static final int EXPIRY = 300;
    private static final int EXPIRY_TIMEOUT = 320;
    private static final double EPSILON = 0.0001;
    private static final int SEED_LIMIT = 10000;
    private static final ResourceKey<DamageType> PALM_TYPE = ResourceKey.create(Registries.DAMAGE_TYPE,
            Identifier.parse("meridian_piglin:midas_touch"));

    @GameTest
    public void thirdPartySwingUsesGoldChestplate(GameTestHelper test) {
        PiglinTestBodies.announce("midas_game_test_third_party_swing_uses_gold_chestplate");
        LivingEntity caster = brute(test);
        LivingEntity attacker = PiglinTestBodies.spawn(test, EntityTypes.COW);
        LivingEntity target = armored(test, Items.DIAMOND_CHESTPLATE);
        var source = attacker.damageSources().mobAttack(attacker);
        long seed = chestSeed(target, source);
        double diamond = swing(armored(test, Items.DIAMOND_CHESTPLATE), source, seed);
        double gold = swing(armored(test, Items.GOLDEN_CHESTPLATE), source, seed);
        test.assertTrue(gold > diamond + EPSILON, "对照的普通攻击应打中胸甲覆盖处");
        palm(target, caster, "chest");
        double actual = swing(target, source, seed);
        close(test, actual, gold, "第三方打在被点中的钻石胸甲上应按金胸甲结算");
        test.succeed();
    }

    @GameTest
    public void normalSwingWithTransmutedSwordUsesGoldCap(GameTestHelper test) {
        PiglinTestBodies.announce("midas_game_test_normal_swing_with_transmuted_sword_uses_gold_cap");
        LivingEntity caster = brute(test);
        LivingEntity attacker = brute(test);
        attacker.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
        palm(attacker, caster, mainHandPart(attacker));
        var source = attacker.damageSources().mobAttack(attacker);
        LivingEntity target = brute(test);
        long seed = chestSeed(target, source);
        double capped = swing(target, source, seed);
        close(test, capped, VanillaDamageFabric.bodyHp(GOLD_SWORD_DAMAGE), "普通攻击应封顶在金剑伤害");
        double direct = direct(brute(test), source);
        close(test, direct, VanillaDamageFabric.bodyHp(GOLD_SWORD_DAMAGE), "直接伤害应使用同一把金剑的封顶值");
        attacker.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
        double regular = swing(brute(test), source, seed);
        test.assertTrue(regular > capped + EPSILON, "更换同材质兵器后普通攻击应解除点金封顶");
        test.succeed();
    }

    @GameTest
    public void directStrikeUsesMarkedArmor(GameTestHelper test) {
        PiglinTestBodies.announce("midas_game_test_direct_strike_uses_marked_armor");
        LivingEntity target = armored(test, Items.DIAMOND_CHESTPLATE);
        palm(target, brute(test), "chest");
        LivingEntity attacker = PiglinTestBodies.spawn(test, EntityTypes.COW);
        var source = attacker.damageSources().mobAttack(attacker);
        double gold = direct(armored(test, Items.GOLDEN_CHESTPLATE), source);
        close(test, direct(target, source), gold, "第三方直接伤害打在被点中的胸甲上应按金胸甲结算");
        test.succeed();
    }

    private static double direct(LivingEntity target, DamageSource source) {
        double before = chestHp(target);
        var damage = new VanillaDamageFabric.DirectDamage(target, source, "chest", VanillaDamageFabric.bodyHp(SWING));
        VanillaDamageFabric.damageDirect(damage).orElseThrow();
        return before - chestHp(target);
    }

    @GameTest(maxTicks = EXPIRY_TIMEOUT)
    public void armorReplacementAndExpiryReleaseOverride(GameTestHelper test) {
        PiglinTestBodies.announce("midas_game_test_armor_replacement_and_expiry_release_override");
        LivingEntity caster = brute(test);
        LivingEntity target = armored(test, Items.DIAMOND_CHESTPLATE);
        LivingEntity swapped = armored(test, Items.DIAMOND_CHESTPLATE);
        palm(target, caster, "chest");
        palm(swapped, caster, "chest");
        swapped.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
        LivingEntity attacker = PiglinTestBodies.spawn(test, EntityTypes.COW);
        var source = attacker.damageSources().mobAttack(attacker);
        long seed = chestSeed(target, source);
        double regular = swing(armored(test, Items.DIAMOND_CHESTPLATE), source, seed);
        close(test, swing(swapped, source, seed), regular, "同材质的新胸甲应使用自身护甲");
        test.runAfterDelay(EXPIRY + 1, () -> {
            test.assertTrue(!target.hasEffect(MidasTouch.EFFECT), "点金应在十五秒后消失");
            close(test, swing(target, source, seed), regular, "原装备到期后应恢复自己的护甲");
            test.succeed();
        });
    }

    private static LivingEntity brute(GameTestHelper test) {
        var entity = (Mob) PiglinTestBodies.spawn(test, EntityTypes.PIGLIN_BRUTE);
        entity.setNoAi(true /* noAi */);
        entity.setNoGravity(true /* noGravity */);
        return entity;
    }

    private static LivingEntity armored(GameTestHelper test, Item armor) {
        LivingEntity entity = brute(test);
        entity.setItemSlot(EquipmentSlot.CHEST, new ItemStack(armor));
        return entity;
    }

    private static String mainHandPart(LivingEntity entity) {
        return switch (entity.getMainArm()) {
            case LEFT -> "left_hand";
            case RIGHT -> "right_hand";
        };
    }

    private static void palm(LivingEntity target, LivingEntity caster, String part) {
        var type = target.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(PALM_TYPE);
        var source = new DamageSource(type, caster);
        var damage = new VanillaDamageFabric.DirectDamage(target, source, part, PALM);
        VanillaDamageFabric.damageDirect(damage).orElseThrow();
        if (!target.hasEffect(MidasTouch.EFFECT)) {
            throw new AssertionError("点金打中金属装备后应上效果");
        }
    }

    static long chestSeed(LivingEntity target, DamageSource source) {
        return seedFor(target, source, "chest");
    }

    static long seedFor(LivingEntity target, DamageSource source, String part) {
        for (int i = 0; i < SEED_LIMIT; i++) {
            target.getRandom().setSeed(i);
            if (VanillaDamageFabric.aimedPart(target, source).orElseThrow().equals(part)) {
                return i;
            }
        }
        throw new AssertionError("普通攻击无法选中 " + part);
    }

    private static double swing(LivingEntity target, DamageSource source, long seed) {
        double before = chestHp(target);
        target.getRandom().setSeed(seed);
        var level = (net.minecraft.server.level.ServerLevel) target.level();
        target.hurtServer(level, source, SWING);
        return before - chestHp(target);
    }

    private static double chestHp(LivingEntity entity) {
        return PiglinTestBodies.body(entity).getPartStatesList().stream()
                .filter(part -> part.getId().equals("chest")).findFirst().orElseThrow().getHp();
    }

    private static void close(GameTestHelper test, double actual, double expected, String reason) {
        test.assertTrue(Math.abs(actual - expected) < EPSILON, reason + "，实际 " + actual + "，预期 " + expected);
    }
}
