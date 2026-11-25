package dev.kizuna.meridian.piglin.fabric;

import dev.kizuna.meridian.humanoid.server.HumanoidTier;
import dev.kizuna.meridian.protocol.BodyProtobuf;
import dev.kizuna.meridian.protocol.pb.BodyMessages.BodySnapshot;
import dev.kizuna.meridian.protocol.pb.BodyMessages.MeridianOpeningStage;
import dev.kizuna.meridian.protocol.pb.BodyMessages.PartDefinition;
import dev.kizuna.meridian.protocol.pb.BodyMessages.PartState;
import dev.kizuna.meridian.technique.fabric.TechniqueFabric;
import dev.kizuna.meridian.technique.fabric.world.TechniqueStatusBenefits;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.CastRequest;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.CastOutcome;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.TechniqueReference;
import dev.kizuna.meridian.technique.server.TechniqueCastEndReason;
import dev.kizuna.meridian.technique.server.TechniqueCastEnded;
import dev.kizuna.meridian.technique.server.TechniqueState;
import dev.kizuna.meridian.vanilla.fabric.VanillaDamageFabric;
import java.util.List;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.item.Items;

/** 原版 Brain 选好目标后按距离、伤势和金囊余量决定是否起式。 */
public final class GoldForms {
    static final String PREFIX = "meridian_piglin:";
    public static final int GILDED_TINT = 0xffffdd84;
    public static final int STATUE_TINT = 0xffffcc38;
    public static final AttachmentType<Integer> SKIN_TINT = AttachmentRegistry.create(
            Identifier.parse(PREFIX + "skin_tint"), builder ->
                    builder.syncWith(ByteBufCodecs.INT, AttachmentSyncPredicate.all()));
    private static final List<String> MIDDLE = List.of("midas_touch", "gold_cast", "devour");
    private static final int AI_INTERVAL = 10;
    private static final int MEAL_INTERVAL = 20;
    private static final int QUIET_RANGE = 16;
    private static final int DART_MIN_RANGE = 4;
    private static final int DART_RESERVE = 5;
    private static final int CAST_COST = 9;
    private static final int GILDING_COST = 3;
    private static final int PALM_COST = 5;
    private static final double LOW_POUCH = 0.3;
    private static final double LOW_HEALTH = 0.4;
    private static final double HEAVY_HIT = 0.2;
    private static final Set<LivingEntity> HEAVY_HITS = Collections.newSetFromMap(new WeakHashMap<>());
    private static MinecraftServer server;

    private GoldForms() {
    }

    static void register() {
        ServerLifecycleEvents.SERVER_STARTING.register(starting -> server = starting);
        ServerLifecycleEvents.SERVER_STOPPED.register(stopped -> server = null);
        TechniqueFabric.SERVICE_STARTED.register(service -> {
            var statue = TechniqueStatusBenefits.get(PREFIX + "gold_cast").effect().value();
            statue.addAttributeModifier(Attributes.KNOCKBACK_RESISTANCE, Identifier.parse(PREFIX + "gold_cast"),
                    1, AttributeModifier.Operation.ADD_VALUE);
            service.addCastCheck(GoldForms::check);
        });
        TechniqueFabric.CAST_ENDED.register(GoldForms::ended);
        VanillaDamageFabric.registerDamageObserver(GoldForms::hurt);
    }

    private static void hurt(VanillaDamageFabric.DamageEvent event) {
        LivingEntity target = event.target();
        if (!PiglinFabric.accepts(target) || event.input().damageAfterEffects() <= 0) {
            return;
        }
        target.removeEffect(TechniqueStatusBenefits.get(PREFIX + "feeding").effect());
        var result = event.result();
        double lost = health(result.before()) - health(result.body());
        if (lost >= maximumHealth(result.before()) * HEAVY_HIT) {
            HEAVY_HITS.add(target);
        }
    }

    private static double health(BodySnapshot body) {
        return body.getPartStatesList().stream().mapToDouble(PartState::getHp).sum();
    }

    private static double maximumHealth(BodySnapshot body) {
        return body.getStructure().getPartsList().stream().mapToDouble(PartDefinition::getMaxHp).sum();
    }

    static boolean has(LivingEntity entity, String status) {
        return entity.hasEffect(TechniqueStatusBenefits.get(PREFIX + status).effect());
    }

    public static boolean isStatue(LivingEntity entity) {
        return PiglinFabric.accepts(entity) && has(entity, "gold_cast");
    }

    private static Optional<String> check(UUID uuid, CastRequest request) {
        var caster = actor(uuid);
        if (caster == null) {
            return Optional.empty();
        }
        if (has(caster, "gold_cast") || caster instanceof Piglin piglin && PiglinBehaviorFabric.isGoldStruck(piglin)) {
            return Optional.of("此刻无法起式");
        }
        var reference = request.getTechnique();
        String id = reference.getId();
        if (!id.startsWith(PREFIX)) {
            return PiglinWeaponForms.check(caster, reference);
        }
        String form = id.substring(PREFIX.length());
        if (VanillaDamageFabric.inspectBody(caster).filter(body -> qualifies(body, form)).isEmpty()) {
            return Optional.of("施展此式所需的经脉尚未冲开");
        }
        if (id.equals(PREFIX + "swallow_gold") && !PiglinBehaviorFabric.canFeed(caster)) {
            return Optional.of("腹中已满，或身边无金可食");
        }
        if (caster instanceof Piglin && (id.equals(PREFIX + "swallow_gold") || id.equals(PREFIX + "gilding"))
                && PiglinBehaviorFabric.hasSoulFireNearby(caster)) {
            return Optional.of("魂火逼近，金气难聚");
        }
        return Optional.empty();
    }

    public static void tickFeeding(AbstractPiglin caster) {
        syncSkin(caster);
        var effect = TechniqueStatusBenefits.get(PREFIX + "feeding").effect();
        var feeding = caster.getEffect(effect);
        if (feeding == null) {
            return;
        }
        caster.getNavigation().stop();
        if (feeding.getDuration() % MEAL_INTERVAL == 0) {
            FeedingLungs.apply(caster, feeding);
        }
        // 原版即将扣去一刻，最后一口也在效果消失之前结算。
        if ((feeding.getDuration() - 1) % MEAL_INTERVAL == 0 && !PiglinBehaviorFabric.eatOne(caster)
                || !PiglinBehaviorFabric.canFeed(caster)) {
            caster.removeEffect(effect);
        }
    }

    private static void syncSkin(AbstractPiglin caster) {
        Integer tint = null;
        if (has(caster, "gold_cast")) {
            tint = STATUE_TINT;
        } else if (has(caster, "gilded")) {
            tint = GILDED_TINT;
        }
        if (!java.util.Objects.equals(caster.getAttached(SKIN_TINT), tint)) {
            caster.setAttached(SKIN_TINT, tint);
        }
    }

    public static void tick(AbstractPiglin caster, ServerLevel level) {
        if (!caster.isAdult() || !TechniqueFabric.running() || caster.tickCount % AI_INTERVAL != 0
                || has(caster, "feeding") || has(caster, "gold_cast")) {
            return;
        }
        var body = VanillaDamageFabric.inspectBody(caster).orElse(null);
        if (body == null || HumanoidTier.of(body) == HumanoidTier.MORTAL) {
            return;
        }
        var progress = TechniqueState.read(BodyProtobuf.fromMessage(body));
        if (progress.hasActiveCast() || TechniqueFabric.service().staggerRemainingMillis(caster.getUUID()) > 0) {
            return;
        }
        long now = body.getSettledAt().getElapsedMillis();
        Predicate<String> ready = form -> progress.getCooldownsOrDefault(PREFIX + form, 0) <= now
                && qualifies(body, form);
        String form = choose(caster, level, body, ready);
        if (form != null) {
            cast(caster, body, form);
        } else {
            PiglinWeaponForms.tryKnown(caster, body, progress);
        }
    }

    private static String choose(AbstractPiglin caster, ServerLevel level, BodySnapshot body, Predicate<String> ready) {
        long gold = PiglinGoldFabric.gold(caster);
        // 伤害仓先结算重伤，再登记僵直；把这次重击留到恢复后的第一次择式。
        boolean heavy = HEAVY_HITS.remove(caster);
        boolean wounded = health(body) < maximumHealth(body) * LOW_HEALTH;
        if ((wounded || heavy) && gold >= CAST_COST && ready.test("gold_cast")) {
            return "gold_cast";
        }
        LivingEntity target = caster.getTarget();
        if (target == null) {
            if (ready.test("swallow_gold") && level.getNearestPlayer(caster, QUIET_RANGE) == null
                    && PiglinBehaviorFabric.canFeed(caster)) {
                return "swallow_gold";
            }
            return null;
        }
        long capacity = PiglinGoldFabric.capacity(caster);
        double distance = caster.distanceTo(target);
        if (distance <= GoldAttacks.REACH) {
            boolean wantsTreasure = GoldAttacks.holdsGoldTool(target) || gold < capacity * LOW_POUCH;
            if (caster instanceof Piglin && gold < capacity && wantsTreasure && ready.test("devour")) {
                return "devour";
            }
            if (gold >= PALM_COST && MidasTouch.hasMetalArmor(target) && ready.test("midas_touch")) {
                return "midas_touch";
            }
        }
        if (distance >= DART_MIN_RANGE && distance <= GoldAttacks.DART_RANGE && gold > DART_RESERVE
                && !caster.isHolding(Items.CROSSBOW) && caster.hasLineOfSight(target) && ready.test("coin_dart")) {
            return "coin_dart";
        }
        var weapon = target.getMainHandItem();
        if (gold >= GILDING_COST && ready.test("gilding") && !has(caster, "gilded")
                && (weapon.is(ItemTags.SWORDS) || weapon.is(ItemTags.AXES)
                || weapon.is(Items.BOW) || weapon.is(Items.CROSSBOW))) {
            return "gilding";
        }
        return null;
    }

    private static boolean qualifies(BodySnapshot body, String form) {
        HumanoidTier tier = HumanoidTier.of(body);
        if (tier == HumanoidTier.MORTAL || MIDDLE.contains(form) && tier != HumanoidTier.TRUE_QI) {
            return false;
        }
        var opened = body.getMeridianStatesList().stream()
                .filter(channel -> channel.getOpening().getStage() == MeridianOpeningStage.OPENED)
                .map(channel -> channel.getId())
                .collect(Collectors.toSet());
        Set<String> covered = body.getStructure().getMeridiansList().stream()
                .filter(channel -> opened.contains(channel.getId()))
                .flatMap(channel -> channel.getPartIdsList().stream())
                .collect(Collectors.toSet());
        if (!covered.contains("chest") && !covered.contains("abdomen")) {
            return false;
        }
        if (form.equals("swallow_gold")) {
            return true;
        }
        if (!covered.contains("left_upper_arm") || !covered.contains("right_upper_arm")) {
            return false;
        }
        return switch (form) {
            case "gold_cast" -> covered.contains("left_thigh") && covered.contains("right_thigh");
            case "devour" -> covered.contains("head");
            case "gilding", "coin_dart", "midas_touch" -> true;
            default -> false;
        };
    }

    static CastOutcome cast(AbstractPiglin caster, BodySnapshot body, String form) {
        var service = TechniqueFabric.service();
        String id = PREFIX + form;
        UUID uuid = caster.getUUID();
        var known = service.known(uuid).stream().filter(entry -> entry.technique().getId().equals(id)).findFirst();
        var reference = known.orElseGet(() -> service.learn(uuid, id).stream()
                .filter(entry -> entry.technique().getId().equals(id)).findFirst().orElseThrow()).technique();
        return cast(caster, body, reference);
    }

    static CastOutcome cast(AbstractPiglin caster, BodySnapshot body, TechniqueReference reference) {
        var request = CastRequest.newBuilder()
                .setBodyId(body.getId())
                .setTechnique(reference)
                .setCastId(UUID.randomUUID().toString());
        LivingEntity target = caster.getTarget();
        if (target != null) {
            request.setTargetId(target.getStringUUID());
            caster.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
        }
        return TechniqueFabric.service().cast(caster.getUUID(), request.build());
    }

    static AbstractPiglin actor(UUID uuid) {
        if (server != null) {
            for (ServerLevel level : server.getAllLevels()) {
                if (level.getEntity(uuid) instanceof AbstractPiglin piglin && piglin.isAdult()) {
                    return piglin;
                }
            }
        }
        return null;
    }

    private static void ended(TechniqueCastEnded event) {
        if (event.reason() != TechniqueCastEndReason.COMPLETED) {
            return;
        }
        Mob caster = actor(event.entityUuid());
        if (caster == null || !(caster.level() instanceof ServerLevel level)) {
            return;
        }
        var last = VanillaDamageFabric.inspectBody(caster).map(BodyProtobuf::fromMessage)
                .map(TechniqueState::read).map(progress -> progress.getLastCast()).orElseThrow();
        if (last.getCastId().equals(event.castId())) {
            switch (last.getTechnique().getId()) {
                case PREFIX + "coin_dart" -> GoldAttacks.dart(caster, level);
                case PREFIX + "devour" -> GoldAttacks.devour(caster);
            }
        }
    }
}
