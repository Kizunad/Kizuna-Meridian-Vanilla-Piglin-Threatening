package dev.kizuna.meridian.piglin.fabric;

import dev.kizuna.meridian.humanoid.fabric.HumanoidBrain;
import dev.kizuna.meridian.humanoid.fabric.HumanoidSpeciesBirths;
import dev.kizuna.meridian.humanoid.server.HumanoidTier;
import dev.kizuna.meridian.protocol.pb.BodyMessages.BodySnapshot;
import dev.kizuna.meridian.technique.fabric.TechniqueFabric;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.CastOutcome;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.CastPhase;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.CastRequest;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.TechniqueFailure;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.TechniqueReference;
import dev.kizuna.meridian.vanilla.fabric.VanillaDamageFabric;
import dev.kizuna.meridian.vanilla.fabric.VanillaDamageFabric.DamageEvent;
import dev.kizuna.meridian.vanilla.fabric.VanillaDamageFabric.GuardTool;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;

public final class PiglinCommonFormsGameTest {
    private static final String GUARD = "meridian_basic_techniques:guard";
    private static final String KNOCK = "meridian_door_breaker:knock";
    private static final String REPEATER = "meridian_wayfarer:repeater";
    private static final String AXE_VOLUME = "meridian_door_breaker:upper";
    private static final String BOLT_VOLUME = "meridian_wayfarer:crossbow";
    private static final int GUARD_READY = 10;
    private static final int RANGE = 9;
    private static final int TEST_TICKS = 140;
    private static final double MELEE_RANGE = 2;
    private static final double CHEST_HIT = 4;
    private static final double ARM_DAMAGE = 2.8;
    private static final double WEAPON_SHOCK = 0.28;
    private static final int WEAPON_WEAR = 3;
    private static final double HP_TOLERANCE = 1e-6;
    private static final List<EntityType<? extends AbstractPiglin>> TYPES =
            List.of(EntityTypes.PIGLIN, EntityTypes.PIGLIN_BRUTE);
    private static final Map<DamageSource, List<DamageEvent>> OBSERVED_HITS = new HashMap<>();

    static {
        VanillaDamageFabric.registerDamageObserver(event -> {
            var events = OBSERVED_HITS.get(event.source());
            if (events != null) {
                events.add(event);
            }
        });
    }

    @GameTest
    public void bothSpeciesGuardChestWithForearm(GameTestHelper test) {
        PiglinTestBodies.announce("piglin_common_forms_game_test_both_species_guard_chest_with_forearm");
        checkGuard(test, TYPES, ItemStack.EMPTY, GuardTool.UNARMED);
    }

    @GameTest
    public void piglinGuardsWithGoldenSword(GameTestHelper test) {
        PiglinTestBodies.announce("piglin_common_forms_game_test_piglin_guards_with_golden_sword");
        checkGuard(test, List.of(EntityTypes.PIGLIN), new ItemStack(Items.GOLDEN_SWORD), GuardTool.WEAPON);
    }

    @GameTest
    public void bruteGuardsWithGoldenAxe(GameTestHelper test) {
        PiglinTestBodies.announce("piglin_common_forms_game_test_brute_guards_with_golden_axe");
        checkGuard(test, List.of(EntityTypes.PIGLIN_BRUTE), new ItemStack(Items.GOLDEN_AXE), GuardTool.WEAPON);
    }

    @GameTest
    public void crossbowLeavesBothSpeciesGuardingWithForearm(GameTestHelper test) {
        PiglinTestBodies.announce("piglin_common_forms_game_test_crossbow_leaves_both_species_guarding_with_forearm");
        checkGuard(test, TYPES, new ItemStack(Items.CROSSBOW), GuardTool.UNARMED);
    }

    private static void checkGuard(GameTestHelper test, List<EntityType<? extends AbstractPiglin>> types,
                                   ItemStack mainHand, GuardTool expectedTool) {
        var defenders = types.stream().map(type -> practitioner(test, type)).toList();
        for (var defender : defenders) {
            defender.setItemSlot(EquipmentSlot.MAINHAND, mainHand.copy());
            defender.setNoAi(true /* noAi */);
            var outcome = cast(defender, GUARD);
            test.assertTrue(outcome.getPhase() == CastPhase.WARMUP, "天生格挡起手失败 " + outcome.getFailure());
        }
        test.runAfterDelay(GUARD_READY, () -> {
            var expectedItem = mainHand.getItem();
            for (var defender : defenders) {
                guardHit(test, defender, expectedItem, expectedTool);
            }
            test.succeed();
        });
    }

    private static void guardHit(GameTestHelper test, AbstractPiglin defender, Item expectedItem,
                                 GuardTool expectedTool) {
        var attacker = PiglinTestBodies.spawn(test, EntityTypes.COW);
        defender.setYRot(0);
        attacker.setPos(defender.position().add(0, 0, MELEE_RANGE));
        var source = attacker.damageSources().mobAttack(attacker);
        var held = defender.getMainHandItem();
        test.assertTrue(held.getItem() == expectedItem, "守方在受击前换了主手物品");
        int initialWear = held.getDamageValue();
        var hit = new VanillaDamageFabric.DirectDamage(defender, source, "chest", CHEST_HIT);
        var event = observeHit(test, hit);
        var contact = event.input().contact();
        test.assertTrue(event.target() == defender && contact.state().isPresent(), "未命中当前守方的格挡");
        var actualTool = contact.tool();
        test.assertTrue(actualTool == expectedTool, "格挡预期 " + expectedTool + "，实际 " + actualTool);
        int expectedWear = expectedTool == GuardTool.WEAPON ? WEAPON_WEAR : 0;
        double expectedArmDamage = expectedTool == GuardTool.WEAPON ? WEAPON_SHOCK : ARM_DAMAGE;
        var afterHit = defender.getMainHandItem();
        test.assertTrue(afterHit.getItem() == expectedItem, "格挡后守方失去了主手物品");
        int wear = afterHit.getDamageValue() - initialWear;
        test.assertTrue(wear == expectedWear, "主手磨损预期 " + expectedWear + "，实际 " + wear);
        assertForearmHit(test, event, expectedArmDamage);
    }

    private static DamageEvent observeHit(GameTestHelper test, VanillaDamageFabric.DirectDamage hit) {
        var events = new ArrayList<DamageEvent>();
        var source = hit.source();
        OBSERVED_HITS.put(source, events);
        try {
            test.assertTrue(VanillaDamageFabric.damageDirect(hit).isPresent(), "直接胸击未结算");
        } finally {
            OBSERVED_HITS.remove(source);
        }
        test.assertTrue(events.size() == 1, "一次胸击应收到一个伤害回执，实际 " + events.size());
        return events.getFirst();
    }

    private static void assertForearmHit(GameTestHelper test, DamageEvent event, double expectedArmDamage) {
        var part = event.input().hit().orElseThrow().getPartId();
        boolean left = part.equals("left_forearm");
        test.assertTrue(left || part.equals("right_forearm"), "格挡改点落在 " + part);
        var result = event.result();
        var before = result.before();
        var after = result.body();
        test.assertTrue(hp(before, "chest") == hp(after, "chest"), "正面胸击仍伤到了胸");
        String otherArm = left ? "right_forearm" : "left_forearm";
        test.assertTrue(hp(before, otherArm) == hp(after, otherArm), "格挡伤到了另一条前臂");
        double damage = hp(before, part) - hp(after, part);
        test.assertTrue(Math.abs(damage - expectedArmDamage) < HP_TOLERANCE,
                "前臂承伤预期 " + expectedArmDamage + "，实际 " + damage);
    }

    private static double hp(BodySnapshot body, String id) {
        return body.getPartStatesList().stream().filter(part -> part.getId().equals(id))
                .findFirst().orElseThrow().getHp();
    }

    @GameTest
    public void bothSpeciesRequireLearnedVolumeAndWeapon(GameTestHelper test) {
        PiglinTestBodies.announce("piglin_common_forms_game_test_both_species_require_learned_volume_and_weapon");
        for (var type : TYPES) {
            verifyWeapon(test, practitioner(test, type), AXE_VOLUME, KNOCK, Items.GOLDEN_AXE);
            verifyWeapon(test, practitioner(test, type), BOLT_VOLUME, REPEATER, Items.CROSSBOW);
        }
        test.succeed();
    }

    private static void verifyWeapon(GameTestHelper test, AbstractPiglin caster, String volume, String form,
                                     Item weapon) {
        caster.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(weapon));
        var failure = cast(caster, form).getFailure();
        test.assertTrue(failure == TechniqueFailure.NOT_KNOWN,
                "修士出生持兵器应因未学卷被拒 " + form + "，实际 " + failure);
        TechniqueFabric.service().learn(caster.getUUID(), volume);
        caster.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        caster.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.GOLDEN_SWORD));
        var before = PiglinTestBodies.body(caster);
        test.assertTrue(cast(caster, form).getFailure() == TechniqueFailure.EFFECT_REJECTED,
                "只拿金剑也能用 " + form);
        test.assertTrue(before.equals(PiglinTestBodies.body(caster)), "持物拒绝仍改变了身体");
        caster.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        caster.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(weapon));
        var outcome = cast(caster, form);
        test.assertTrue(outcome.getPhase() == CastPhase.WARMUP, "副手持兵器未能起手 " + outcome.getReason());
    }

    private static CastOutcome cast(AbstractPiglin caster, String id) {
        var reference = TechniqueReference.newBuilder()
                .setId(id)
                .setVersion(1)
                .build();
        var request = CastRequest.newBuilder()
                .setBodyId(PiglinTestBodies.body(caster).getId())
                .setTechnique(reference)
                .setCastId(UUID.randomUUID().toString())
                .build();
        return TechniqueFabric.service().cast(caster.getUUID(), request);
    }

    @GameTest(maxTicks = TEST_TICKS)
    public void bruteUsesLearnedAxeOnBrainTarget(GameTestHelper test) {
        PiglinTestBodies.announce("piglin_common_forms_game_test_brute_uses_learned_axe_on_brain_target");
        var brute = practitioner(test, EntityTypes.PIGLIN_BRUTE);
        brute.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.GOLDEN_AXE));
        var target = opponent(test, brute, MELEE_RANGE);
        TechniqueFabric.service().learn(brute.getUUID(), KNOCK);
        test.succeedWhen(() -> {
            assertBrainCast(test, brute, target, KNOCK);
            var hit = target.getLastDamageSource();
            test.assertTrue(hit != null && hit.getEntity() == brute && hit.typeHolder().unwrapKey().orElseThrow()
                    .identifier().getNamespace().equals("meridian_door_breaker"), "破门斩尚未命中原版目标");
        });
    }

    @GameTest(maxTicks = TEST_TICKS, structure = "meridian_piglin_test:archery")
    public void piglinFiresLearnedCrossbowAtBrainTarget(GameTestHelper test) {
        PiglinTestBodies.announce("piglin_common_forms_game_test_piglin_fires_learned_crossbow_at_brain_target");
        var piglin = practitioner(test, EntityTypes.PIGLIN);
        piglin.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
        var target = opponent(test, piglin, RANGE);
        TechniqueFabric.service().learn(piglin.getUUID(), BOLT_VOLUME);
        test.succeedWhen(() -> {
            assertBrainCast(test, piglin, target, REPEATER);
            var arrows = test.getLevel().getEntitiesOfClass(AbstractArrow.class,
                    piglin.getBoundingBox().inflate(RANGE), arrow -> arrow.getOwner() == piglin && arrow.isCritArrow());
            test.assertTrue(!arrows.isEmpty(), "连弩尚未射出实体箭");
        });
    }

    @GameTest
    public void loadedVanillaCrossbowWaitsForCast(GameTestHelper test) {
        PiglinTestBodies.announce("piglin_common_forms_game_test_loaded_vanilla_crossbow_waits_for_cast");
        var piglin = practitioner(test, EntityTypes.PIGLIN);
        var target = opponent(test, piglin, MELEE_RANGE);
        piglin.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
        var crossbow = new ItemStack(Items.CROSSBOW);
        var loaded = ChargedProjectiles.ofNonEmpty(List.of(new ItemStack(Items.SPECTRAL_ARROW)));
        crossbow.set(DataComponents.CHARGED_PROJECTILES, loaded);
        piglin.setItemSlot(EquipmentSlot.MAINHAND, crossbow);
        var service = TechniqueFabric.service();
        var uuid = piglin.getUUID();
        service.learn(uuid, BOLT_VOLUME);
        var outcome = cast(piglin, REPEATER);
        test.assertTrue(outcome.getPhase() == CastPhase.WARMUP, "连弩起手失败 " + outcome.getReason());
        piglin.performRangedAttack(target, 1);
        test.assertTrue(loaded.equals(crossbow.get(DataComponents.CHARGED_PROJECTILES)), "行矢起手时原版弩抢先射出");
        test.assertTrue(service.interruptWarmup(uuid, outcome.getCastId()), "连弩起手未结束");
        piglin.performRangedAttack(target, 1);
        var arrows = test.getLevel().getEntitiesOfClass(AbstractArrow.class, piglin.getBoundingBox().inflate(RANGE),
                arrow -> arrow.getType() == EntityTypes.SPECTRAL_ARROW && arrow.getOwner() == piglin);
        test.assertTrue(arrows.size() == 1, "中断后原版弩应射出先前装填的一支光灵箭");
        test.succeed();
    }

    private static <T extends AbstractPiglin> T practitioner(GameTestHelper test, EntityType<T> type) {
        var caster = type.create(test.getLevel(), EntitySpawnReason.COMMAND);
        HumanoidSpeciesBirths.birth(caster, HumanoidTier.TRUE_QI);
        caster.setNoGravity(true /* noGravity */);
        caster.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        PiglinTestBodies.add(test, caster);
        test.assertTrue(!HumanoidBrain.managed(caster), "猪灵不应接管到类人大脑");
        return caster;
    }

    private static LivingEntity opponent(GameTestHelper test, AbstractPiglin caster, double distance) {
        var target = EntityTypes.COW.create(test.getLevel(), EntitySpawnReason.COMMAND);
        target.setNoAi(true /* noAi */);
        target.setNoGravity(true /* noGravity */);
        target.setPos(caster.position().add(0, 0, distance));
        test.getLevel().addFreshEntity(target);
        // 只给原版愤怒记忆，由 Brain 自己写入攻击目标。
        caster.getBrain().setMemory(MemoryModuleType.ANGRY_AT, target.getUUID());
        return target;
    }

    private static void assertBrainCast(GameTestHelper test, AbstractPiglin caster, LivingEntity target, String id) {
        test.assertTrue(caster.getTarget() == target, "原版 Brain 尚未选中愤怒目标");
        test.assertTrue(caster.hasLineOfSight(target), "所选目标在场地外或视线受阻");
        var cast = TechniqueFabric.service().observe(caster.getUUID()).orElseThrow().cast();
        test.assertTrue(cast.isPresent(), "尚未对原版目标施放 " + id);
        var request = cast.orElseThrow().active().getRequest();
        boolean expectedTarget = request.getTargetId().equals(target.getStringUUID());
        test.assertTrue(request.getTechnique().getId().equals(id) && expectedTarget,
                "当前施放的式或目标不符 " + request.getTechnique().getId());
    }
}
