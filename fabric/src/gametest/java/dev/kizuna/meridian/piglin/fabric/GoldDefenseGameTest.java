package dev.kizuna.meridian.piglin.fabric;

import dev.kizuna.meridian.technique.fabric.world.TechniqueStatusBenefits;
import dev.kizuna.meridian.vanilla.fabric.IncomingDamage;
import dev.kizuna.meridian.vanilla.fabric.VanillaDamageFabric;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class GoldDefenseGameTest {
    // 三倍镐击也须小于最小部位的 HP，使随机落点不会引入过量伤害。
    private static final float DAMAGE = 2;
    private static final double EPSILON = 0.0001;
    private static final int DURATION = 100;
    private static final double GILDED_CUT = 0.6;
    private static final double GILDED_PICK = 1.5;
    private static final double STATUE_PHYSICAL = 0.1;
    private static final double STATUE_PICK = 3;
    private static final double HALF = 0.5;

    @GameTest
    public void gildingSeparatesBladeStickPickAndFire(GameTestHelper test) {
        PiglinTestBodies.announce("gold_defense_game_test_gilding_separates_blade_stick_pick_and_fire");
        String status = "gilded";
        check(test, status, swing(test, Items.IRON_SWORD), GILDED_CUT);
        check(test, status, swing(test, Items.STICK), 1);
        check(test, status, swing(test, Items.IRON_PICKAXE), GILDED_PICK);
        check(test, status, test.getLevel().damageSources().inFire(), HALF);
        test.succeed();
    }

    @GameTest
    public void statueSeparatesPickFireAndQi(GameTestHelper test) {
        PiglinTestBodies.announce("gold_defense_game_test_statue_separates_pick_fire_and_qi");
        String status = "gold_cast";
        check(test, status, swing(test, Items.IRON_SWORD), STATUE_PHYSICAL);
        check(test, status, swing(test, Items.STICK), STATUE_PHYSICAL);
        check(test, status, swing(test, Items.IRON_PICKAXE), STATUE_PICK);
        check(test, status, test.getLevel().damageSources().inFire(), 0);
        var blade = swing(test, Items.IRON_SWORD);
        var qi = new IncomingDamage.QiSource(blade.typeHolder(), blade.getDirectEntity(), blade.getEntity());
        check(test, status, qi, HALF);
        test.succeed();
    }

    private static DamageSource swing(GameTestHelper test, Item item) {
        LivingEntity attacker = PiglinTestBodies.spawn(test, EntityTypes.COW);
        attacker.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(item));
        return attacker.damageSources().mobAttack(attacker);
    }

    private static void check(GameTestHelper test, String status, DamageSource source, double factor) {
        var direct = target(test, status);
        double before = hp(direct);
        var hit = new VanillaDamageFabric.DirectDamage(direct, source, "chest", VanillaDamageFabric.bodyHp(DAMAGE));
        VanillaDamageFabric.damageDirect(hit).orElseThrow();
        assertLoss(test, before - hp(direct), factor, "直接", source);
        var normal = target(test, status);
        double original = hp(normal);
        normal.hurtServer(test.getLevel(), source, DAMAGE);
        assertLoss(test, original - hp(normal), factor, "普通", source);
    }

    private static Piglin target(GameTestHelper test, String status) {
        var piglin = (Piglin) PiglinTestBodies.spawn(test, EntityTypes.PIGLIN);
        var effect = TechniqueStatusBenefits.get(GoldForms.PREFIX + status).effect();
        piglin.addEffect(new MobEffectInstance(effect, DURATION));
        return piglin;
    }

    private static double hp(Piglin target) {
        return PiglinTestBodies.body(target).getPartStatesList().stream().mapToDouble(part -> part.getHp()).sum();
    }

    private static void assertLoss(GameTestHelper test, double actual, double factor, String path,
                                   DamageSource source) {
        double expected = VanillaDamageFabric.bodyHp(DAMAGE) * factor;
        String message = path + source + " 实付伤害 " + actual + " 预期 " + expected;
        test.assertTrue(Math.abs(actual - expected) < EPSILON, message);
    }
}
