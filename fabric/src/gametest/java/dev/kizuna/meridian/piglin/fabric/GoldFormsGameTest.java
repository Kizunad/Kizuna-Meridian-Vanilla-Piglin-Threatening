package dev.kizuna.meridian.piglin.fabric;

import dev.kizuna.meridian.humanoid.fabric.HumanoidBirthPlan;
import dev.kizuna.meridian.humanoid.server.HumanoidTier;
import dev.kizuna.meridian.piglin.server.PiglinBody;
import dev.kizuna.meridian.piglin.server.PiglinContent;
import dev.kizuna.meridian.piglin.server.PiglinGold;
import dev.kizuna.meridian.protocol.pb.BodyMessages.BodySnapshot;
import dev.kizuna.meridian.core.Anatomy;
import dev.kizuna.meridian.core.OrganCapabilities;
import dev.kizuna.meridian.protocol.BodyProtobuf;
import dev.kizuna.meridian.technique.fabric.TechniqueFabric;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.CastRequest;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.TechniqueFailure;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.TechniqueReference;
import dev.kizuna.meridian.technique.server.TechniqueService;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.GameType;

public final class GoldFormsGameTest {
    private static final int START_GOLD = 18;
    private static final int ATTACK_TICKS = 22;
    private static final int CAST_TIMEOUT = 80;
    private static final int GILDING_COST = 3;
    private static final int PALM_COST = 5;
    private static final int STATUE_COST = 9;
    private static final int SEPARATION = 2;
    private static final int SEARCH_RADIUS = 14;
    private static final int SHIELD_READY_TICKS = 7;
    private static final double DART_TOTAL_DAMAGE = 3;
    private static final double DAMAGE_TOLERANCE = 0.0001;
    private static final double SOUL_FIRE_FACTOR = 2;
    private static final int WARMUP_TICKS = 30;
    private static final int MEAL_TICKS = 20;
    private static final int MEAL_STACK = 3;
    private static final int FIRST_POCKET = 9;
    private static final int LAST_POCKET = 17;
    private static final int BACKPACK = 18;
    private static final int POCKET_STACK = 7;
    private static final long MISS_RECOVERY = 1000;
    private static final int ASSERT_DELAY = 2;
    private static final int FEEDING_INTAKE = 3;
    private static final int RANGED_DISTANCE = 6;
    private static final double WOUNDED_FRACTION = 0.35;
    private static final float HEAVY_SWING = 85;
    private static final double HEAVY_FRACTION = 0.2;
    private static final String PALM_CHEST_TARGET = "meridian_piglin_test.palm_chest";

    static {
        // 固定该测试靶的普通受击抽签。前臂与颈部允许没有盔甲覆盖。
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((target, source, amount) -> {
            if (target.entityTags().contains(PALM_CHEST_TARGET)) {
                long seed = MidasGameTest.chestSeed(target, source);
                target.getRandom().setSeed(seed);
            }
            return true;
        });
    }

    @GameTest
    public void soulFireDoublesOnlyQiForPiglins(GameTestHelper test) {
        PiglinTestBodies.announce("gold_forms_game_test_soul_fire_doubles_only_qi_for_piglins");
        var level = test.getLevel();
        Piglin normal = practitioner(test);
        double normalCost = spendOnDart(test, normal);
        Piglin afraid = practitioner(test);
        var torch = afraid.blockPosition().above();
        level.setBlockAndUpdate(torch, Blocks.SOUL_TORCH.defaultBlockState());
        double increased = spendOnDart(test, afraid);
        test.assertTrue(Math.abs(increased - normalCost * SOUL_FIRE_FACTOR) < DAMAGE_TOLERANCE,
                "魂火旁应扣双倍真元，原价 " + normalCost + " 实付 " + increased);
        var brute = EntityTypes.PIGLIN_BRUTE.create(level, EntitySpawnReason.COMMAND);
        var difficulty = level.getCurrentDifficultyAt(torch);
        HumanoidBirthPlan.captureSpawner(brute, difficulty, HumanoidTier.TRUE_QI);
        PiglinTestBodies.add(test, brute);
        var before = PiglinTestBodies.body(brute);
        var outcome = GoldForms.cast(brute, before, "coin_dart");
        test.assertTrue(outcome.getFailure() == TechniqueFailure.TECHNIQUE_FAILURE_UNSPECIFIED, "蛮兵须能出镖");
        double paid = lumen(before) - lumen(PiglinTestBodies.body(brute));
        test.assertTrue(Math.abs(paid - normalCost) < DAMAGE_TOLERANCE, "蛮兵魂火旁应付原价");
        level.setBlockAndUpdate(torch, Blocks.AIR.defaultBlockState());
        test.succeed();
    }

    @GameTest(maxTicks = WARMUP_TICKS + MEAL_TICKS * 3)
    public void swallowEatsOncePerSecondUntilHit(GameTestHelper test) {
        PiglinTestBodies.announce("gold_forms_game_test_swallow_eats_once_per_second_until_hit");
        Piglin caster = practitioner(test);
        var stack = new ItemStack(Items.GOLD_NUGGET, MEAL_STACK);
        caster.getInventory().setItem(0, stack);
        accepted(test, caster, "swallow_gold");
        test.runAfterDelay(WARMUP_TICKS + ASSERT_DELAY, () -> {
            test.assertTrue(GoldForms.has(caster, "feeding"), "起手后应进入服金");
            assertLungs(test, caster, FEEDING_INTAKE);
            test.assertTrue(stack.getCount() == MEAL_STACK, "第一秒未满应保留三粒金");
        });
        test.runAfterDelay(WARMUP_TICKS + MEAL_TICKS + ASSERT_DELAY, () -> {
            test.assertTrue(stack.getCount() == MEAL_STACK - 1, "满一秒应吃一件");
            test.assertTrue(PiglinGoldFabric.gold(caster) == START_GOLD + 1, "吃下的一粒应进金囊");
            caster.hurtServer(test.getLevel(), caster.damageSources().generic(), 1);
            test.assertTrue(!GoldForms.has(caster, "feeding"), "普通命中应立刻中止服金");
            assertLungs(test, caster, 1);
        });
        test.runAfterDelay(WARMUP_TICKS + MEAL_TICKS * 2 + ASSERT_DELAY, () -> {
            test.assertTrue(stack.getCount() == MEAL_STACK - 1, "受击后下一秒应保留库存");
            test.assertTrue(cast(caster, "swallow_gold") == TechniqueFailure.COOLING_DOWN, "受击后仍在冷却");
            test.succeed();
        });
    }

    private static void assertLungs(GameTestHelper test, Piglin caster, double expected) {
        var snapshot = PiglinTestBodies.body(caster);
        var body = BodyProtobuf.fromMessage(snapshot);
        var lungs = snapshot.getStructure().getOrgansList().stream()
                .filter(organ -> organ.getKind().equals("human:lung")).toList();
        test.assertTrue(!lungs.isEmpty(), "猪灵身体须有肺");
        for (var lung : lungs) {
            var id = new Anatomy.OrganId(lung.getId());
            double multiplier = OrganCapabilities.intakeMultiplier(body, id);
            test.assertTrue(Math.abs(expected - multiplier) < DAMAGE_TOLERANCE, "肺摄入倍率 " + multiplier);
        }
    }

    @GameTest
    public void aiSelectsSixFormsFromTargetAndWounds(GameTestHelper test) {
        PiglinTestBodies.announce("gold_forms_game_test_ai_selects_six_forms_from_target_and_wounds");
        Piglin quiet = practitioner(test);
        quiet.getInventory().setItem(0, new ItemStack(Items.GOLD_NUGGET));
        expectChoice(test, quiet, "swallow_gold");
        Piglin far = practitioner(test);
        var distant = inFront(test, far);
        // 把射线抬到测试结构的围墙之上。
        far.setPos(far.position().add(0, RANGED_DISTANCE, 0));
        distant.setPos(far.position().add(0, 0, RANGED_DISTANCE));
        test.assertTrue(far.getTarget() == distant, "Brain 的攻击目标须是远处测试靶");
        test.assertTrue(far.hasLineOfSight(distant), "两实体间须能直视");
        far.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        expectChoice(test, far, "coin_dart");
        Piglin defense = practitioner(test);
        inFront(test, defense).setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        expectChoice(test, defense, "gilding");
        Piglin palm = practitioner(test);
        inFront(test, palm).setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        expectChoice(test, palm, "midas_touch");
        Piglin thief = practitioner(test);
        inFront(test, thief).setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.GOLDEN_AXE));
        expectChoice(test, thief, "devour");
        Piglin wounded = practitioner(test, WOUNDED_FRACTION);
        expectChoice(test, wounded, "gold_cast");
        test.succeed();
    }

    private static void expectChoice(GameTestHelper test, Piglin caster, String expected) {
        GoldForms.tick(caster, test.getLevel());
        var active = TechniqueFabric.service().observe(caster.getUUID()).orElseThrow().cast();
        String actual = active.map(cast -> cast.active().getRequest().getTechnique().getId()).orElse("");
        test.assertTrue(actual.equals(GoldForms.PREFIX + expected), expected + " 择式结果 " + actual);
    }

    @GameTest(maxTicks = CAST_TIMEOUT)
    public void heavyBlowTriggersStatueAboveLowHealth(GameTestHelper test) {
        PiglinTestBodies.announce("gold_forms_game_test_heavy_blow_triggers_statue_above_low_health");
        Piglin caster = practitioner(test);
        var attacker = PiglinTestBodies.spawn(test, EntityTypes.COW);
        var source = attacker.damageSources().mobAttack(attacker);
        long seed = MidasGameTest.seedFor(caster, source, "pelvis");
        double before = bodyHealth(caster);
        caster.getRandom().setSeed(seed);
        caster.hurtServer(test.getLevel(), source, HEAVY_SWING);
        double after = bodyHealth(caster);
        test.assertTrue(PiglinGoldFabric.gold(caster) == START_GOLD, "重击应避开金囊");
        test.assertTrue(after <= before * (1 - HEAVY_FRACTION) && after > before / 2,
                "一击须实扣两成以上且过半血，受击前 " + before + " 受击后 " + after);
        test.onEachTick(() -> GoldForms.tick(caster, test.getLevel()));
        test.succeedWhen(() -> test.assertTrue(GoldForms.isStatue(caster), "受击僵直解除后应铸金"));
    }

    @GameTest
    public void devourRespectsPlayerPocketAndGoldLimit(GameTestHelper test) {
        PiglinTestBodies.announce("gold_forms_game_test_devour_respects_player_pocket_and_gold_limit");
        Piglin caster = practitioner(test);
        var target = test.makeMockPlayer(GameType.SURVIVAL);
        var inventory = target.getInventory();
        inventory.setItem(FIRST_POCKET, new ItemStack(Items.GOLD_NUGGET, POCKET_STACK));
        inventory.setItem(LAST_POCKET, new ItemStack(Items.GOLD_NUGGET, POCKET_STACK));
        inventory.setItem(BACKPACK, new ItemStack(Items.GOLD_INGOT));
        target.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.GOLDEN_CHESTPLATE));
        target.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.GOLDEN_AXE));
        test.assertTrue(GoldAttacks.takeGold(caster, target), "有副手金器应抢金器");
        test.assertTrue(target.getOffhandItem().isEmpty() && inventory.getItem(FIRST_POCKET).getCount() == POCKET_STACK,
                "副手金器应先于口袋里的金粒");
        test.assertTrue(GoldAttacks.takeGold(caster, target), "应摸到口袋中的金粒");
        long remaining = POCKET_STACK * 2 - PiglinGold.INGOT_VALUE;
        test.assertTrue(inventory.getItem(FIRST_POCKET).isEmpty()
                && inventory.getItem(LAST_POCKET).getCount() == remaining, "两格合计最多取九粒");
        test.assertTrue(PiglinGoldFabric.gold(caster) == START_GOLD + PiglinGold.INGOT_VALUE * 2, "两次金量应守恒");
        inventory.setItem(LAST_POCKET, ItemStack.EMPTY);
        test.assertTrue(!GoldAttacks.takeGold(caster, target), "身上只余背包和穿戴金甲时须落空");
        test.assertTrue(inventory.getItem(BACKPACK).is(Items.GOLD_INGOT)
                && target.getItemBySlot(EquipmentSlot.CHEST).is(Items.GOLDEN_CHESTPLATE), "背包和金甲应保留");
        test.succeed();
    }

    @GameTest(maxTicks = CAST_TIMEOUT)
    public void devourMissHoldsCasterForOneSecond(GameTestHelper test) {
        PiglinTestBodies.announce("gold_forms_game_test_devour_miss_holds_caster_for_one_second");
        Piglin caster = practitioner(test);
        inFront(test, caster);
        accepted(test, caster, "devour");
        var service = TechniqueFabric.service();
        var kind = TechniqueService.StaggerKind.HOLD;
        test.runAfterDelay(ATTACK_TICKS, () -> {
            long left = service.staggerRemainingMillis(caster.getUUID(), kind);
            test.assertTrue(left > 0 && left <= MISS_RECOVERY, "落空应多僵一秒，余 " + left);
        });
        test.runAfterDelay(ATTACK_TICKS + MEAL_TICKS, () -> {
            test.assertTrue(service.staggerRemainingMillis(caster.getUUID(), kind) == 0, "一秒后应能行动");
            test.assertTrue(PiglinGoldFabric.gold(caster) == START_GOLD, "落空不增减金");
            test.succeed();
        });
    }

    private static double spendOnDart(GameTestHelper test, Piglin piglin) {
        double before = lumen(PiglinTestBodies.body(piglin));
        accepted(test, piglin, "coin_dart");
        test.assertTrue(PiglinGoldFabric.gold(piglin) == START_GOLD - 1, "金钱镖只扣一粒金");
        double spent = before - lumen(PiglinTestBodies.body(piglin));
        test.assertTrue(spent > 0, "起手应扣真元");
        return spent;
    }

    private static double lumen(BodySnapshot body) {
        return body.getMeridianStatesList().stream()
                .flatMap(channel -> channel.getLumenQi().getEntriesList().stream())
                .mapToDouble(qi -> qi.getAmount()).sum();
    }

    @GameTest
    public void soulFireRejectsSwallowWithoutPaying(GameTestHelper test) {
        PiglinTestBodies.announce("gold_forms_game_test_soul_fire_rejects_swallow_without_paying");
        Piglin caster = practitioner(test);
        caster.getInventory().setItem(0, new ItemStack(Items.GOLD_INGOT));
        var torch = caster.blockPosition().above();
        var level = test.getLevel();
        level.setBlockAndUpdate(torch, Blocks.SOUL_TORCH.defaultBlockState());
        var before = PiglinTestBodies.body(caster).getMeridianStatesList();
        var failed = cast(caster, "swallow_gold");
        test.assertTrue(failed != TechniqueFailure.TECHNIQUE_FAILURE_UNSPECIFIED, "魂火旁服金应拒绝起式");
        test.assertTrue(PiglinTestBodies.body(caster).getMeridianStatesList().equals(before), "拒绝后经脉真元有变");
        level.setBlockAndUpdate(torch, Blocks.AIR.defaultBlockState());
        accepted(test, caster, "swallow_gold");
        test.succeed();
    }

    @GameTest(maxTicks = CAST_TIMEOUT)
    public void foreignGoldInterruptsFormalSwallowWarmup(GameTestHelper test) {
        PiglinTestBodies.announce("gold_forms_game_test_foreign_gold_interrupts_formal_swallow_warmup");
        Piglin caster = practitioner(test);
        caster.getInventory().setItem(0, new ItemStack(Items.GOLD_NUGGET));
        accepted(test, caster, "swallow_gold");
        var level = test.getLevel();
        var bait = new ItemEntity(level, caster.getX(), caster.getY(), caster.getZ(), new ItemStack(Items.GOLD_INGOT));
        level.addFreshEntity(bait);
        PiglinBehaviorFabric.tick(caster);
        test.assertTrue(PiglinBehaviorFabric.isGoldStruck(caster), "起手时见到外来的金锭应触发见金");
        test.assertTrue(TechniqueFabric.service().observe(caster.getUUID()).orElseThrow().cast().isEmpty(),
                "服金应在起手阶段被打断");
        test.assertTrue(PiglinGoldFabric.gold(caster) == START_GOLD, "被打断时库存的金应留在原处");
        test.succeed();
    }

    @GameTest(maxTicks = CAST_TIMEOUT)
    public void gildingAndStatuePayTheirOwnGold(GameTestHelper test) {
        PiglinTestBodies.announce("gold_forms_game_test_gilding_and_statue_pay_their_own_gold");
        Piglin gilded = practitioner(test);
        Piglin statue = practitioner(test);
        accepted(test, gilded, "gilding");
        accepted(test, statue, "gold_cast");
        test.runAfterDelay(ATTACK_TICKS, () -> {
            test.assertTrue(GoldForms.has(gilded, "gilded"), "鎏金起手后应有金皮");
            test.assertTrue(GoldForms.has(statue, "gold_cast"), "铸金起手后应成像");
            test.assertTrue(statue.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) >= 1, "金像应抗击退");
            test.assertTrue(PiglinGoldFabric.gold(gilded) == START_GOLD - GILDING_COST, "鎏金应扣三粒");
            test.assertTrue(PiglinGoldFabric.gold(statue) == START_GOLD - STATUE_COST, "铸金应扣九粒");
            test.succeed();
        });
    }

    @GameTest(maxTicks = CAST_TIMEOUT)
    public void thrownDartLeavesOneCollectibleNugget(GameTestHelper test) {
        PiglinTestBodies.announce("gold_forms_game_test_thrown_dart_leaves_one_collectible_nugget");
        Piglin caster = practitioner(test);
        accepted(test, caster, "coin_dart");
        test.runAfterDelay(ATTACK_TICKS, () -> {
            var drops = test.getLevel().getEntitiesOfClass(ItemEntity.class,
                    caster.getBoundingBox().inflate(SEARCH_RADIUS), item -> item.getItem().is(Items.GOLD_NUGGET)
                            && item.getOwner() == caster);
            test.assertTrue(drops.stream().mapToInt(item -> item.getItem().getCount()).sum() == 1,
                    "这次金钱镖应落下一粒可拾取的金");
            test.assertTrue(PiglinGoldFabric.gold(caster) == START_GOLD - 1, "金钱镖应扣一粒");
            test.assertTrue(cast(caster, "coin_dart") == TechniqueFailure.COOLING_DOWN, "金钱镖应进入冷却");
            test.succeed();
        });
    }

    @GameTest(maxTicks = CAST_TIMEOUT)
    public void dartAddsQiToPiercingHit(GameTestHelper test) {
        PiglinTestBodies.announce("gold_forms_game_test_dart_adds_qi_to_piercing_hit");
        Piglin caster = practitioner(test);
        Piglin target = inFront(test, caster);
        double before = bodyHealth(target);
        accepted(test, caster, "coin_dart");
        test.runAfterDelay(ATTACK_TICKS, () -> {
            double loss = before - bodyHealth(target);
            test.assertTrue(Math.abs(loss - DART_TOTAL_DAMAGE) < DAMAGE_TOLERANCE,
                    "未持盾应吃到穿刺和真元两段，实际 " + loss);
            test.succeed();
        });
    }

    @GameTest(maxTicks = CAST_TIMEOUT)
    public void shieldStopsDartAndQi(GameTestHelper test) {
        PiglinTestBodies.announce("gold_forms_game_test_shield_stops_dart_and_qi");
        Piglin caster = practitioner(test);
        Piglin target = inFront(test, caster);
        target.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, caster.getEyePosition());
        target.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
        target.startUsingItem(InteractionHand.OFF_HAND);
        double before = bodyHealth(target);
        test.runAfterDelay(SHIELD_READY_TICKS, () -> accepted(test, caster, "coin_dart"));
        test.runAfterDelay(SHIELD_READY_TICKS + ATTACK_TICKS, () -> {
            test.assertTrue(bodyHealth(target) == before, "迎面举盾应挡下金钱镖及其附伤");
            test.assertTrue(PiglinGoldFabric.gold(caster) == START_GOLD - 1, "盾挡下后仍须耗金");
            test.succeed();
        });
    }

    private static double bodyHealth(Piglin target) {
        return PiglinTestBodies.body(target).getPartStatesList().stream().mapToDouble(part -> part.getHp()).sum();
    }

    @GameTest(maxTicks = CAST_TIMEOUT)
    public void devourTakesHeldGoldIntoPouch(GameTestHelper test) {
        PiglinTestBodies.announce("gold_forms_game_test_devour_takes_held_gold_into_pouch");
        Piglin caster = practitioner(test);
        Piglin target = inFront(test, caster);
        target.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.GOLDEN_SWORD));
        accepted(test, caster, "devour");
        test.runAfterDelay(ATTACK_TICKS, () -> {
            test.assertTrue(target.getMainHandItem().isEmpty(), "吞宝命中应取走目标手中的金剑");
            test.assertTrue(PiglinGoldFabric.gold(caster) == START_GOLD + PiglinGold.INGOT_VALUE, "金剑应入金囊");
            test.succeed();
        });
    }

    @GameTest(maxTicks = CAST_TIMEOUT)
    public void castPalmMarksTargetsMetalEquipment(GameTestHelper test) {
        PiglinTestBodies.announce("gold_forms_game_test_cast_palm_marks_targets_metal_equipment");
        Piglin caster = practitioner(test);
        Piglin target = inFront(test, caster);
        target.addTag(PALM_CHEST_TARGET);
        target.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        accepted(test, caster, "midas_touch");
        test.runAfterDelay(ATTACK_TICKS, () -> {
            test.assertTrue(target.hasEffect(MidasTouch.EFFECT), "正前方披甲目标应被点金掌命中");
            test.assertTrue(PiglinGoldFabric.gold(caster) == START_GOLD - PALM_COST, "点金掌应扣五粒");
            test.succeed();
        });
    }

    private static Piglin inFront(GameTestHelper test, Piglin caster) {
        Piglin target = (Piglin) PiglinTestBodies.spawn(test, EntityTypes.PIGLIN);
        target.setNoAi(true /* noAi */);
        target.setNoGravity(true /* noGravity */);
        target.setPos(caster.position().add(0, 0, SEPARATION));
        caster.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
        caster.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
        return target;
    }

    private static Piglin practitioner(GameTestHelper test) {
        return practitioner(test, 1);
    }

    private static Piglin practitioner(GameTestHelper test, double healthFraction) {
        var original = (Piglin) PiglinTestBodies.spawn(test, EntityTypes.PIGLIN);
        var body = PiglinTestBodies.body(original);
        var template = PiglinContent.bundle().getTemplates(0);
        var born = PiglinBody.born(template, body.getId(), body.getSettledAt(), HumanoidTier.TRUE_QI,
                new java.util.Random(0));
        var funded = PiglinGold.withGold(born, START_GOLD).toBuilder();
        for (var part : funded.getPartStatesBuilderList()) {
            part.setHp(part.getHp() * healthFraction);
        }
        var save = PiglinTestBodies.save(original);
        var stored = save.getCompound(PiglinTestBodies.SAVE_KEY).orElseThrow();
        stored.putByteArray("body", funded.build().toByteArray());
        original.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        var caster = EntityTypes.PIGLIN.create(test.getLevel(), EntitySpawnReason.LOAD);
        PiglinTestBodies.load(caster, save);
        caster.setNoAi(true /* noAi */);
        caster.setNoGravity(true /* noGravity */);
        PiglinTestBodies.add(test, caster);
        TechniqueFabric.service().learn(caster.getUUID(), GoldForms.PREFIX + "gold_eating");
        return caster;
    }

    private static void accepted(GameTestHelper test, Piglin entity, String form) {
        var failure = cast(entity, form);
        test.assertTrue(failure == TechniqueFailure.TECHNIQUE_FAILURE_UNSPECIFIED, form + " 起手被拒 " + failure);
    }

    private static TechniqueFailure cast(Piglin caster, String form) {
        var technique = TechniqueReference.newBuilder()
                .setId(GoldForms.PREFIX + form)
                .setVersion(1)
                .build();
        var request = CastRequest.newBuilder()
                .setTechnique(technique)
                .setBodyId(PiglinTestBodies.body(caster).getId())
                .setCastId(UUID.randomUUID().toString());
        var target = caster.getTarget();
        if (target != null) {
            request.setTargetId(target.getStringUUID());
        }
        return TechniqueFabric.service().cast(caster.getUUID(), request.build()).getFailure();
    }
}
