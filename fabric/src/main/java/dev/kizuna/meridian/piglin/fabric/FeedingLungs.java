package dev.kizuna.meridian.piglin.fabric;

import dev.kizuna.meridian.protocol.pb.ActivityMessages.BodyActivity;
import dev.kizuna.meridian.protocol.pb.BodyMessages.CapabilityModifier;
import dev.kizuna.meridian.protocol.pb.BodyMessages.CurvePoint;
import dev.kizuna.meridian.technique.fabric.world.TechniqueStatusBenefits;
import dev.kizuna.meridian.vanilla.fabric.VanillaDamageFabric;
import java.util.Collection;
import java.util.stream.Collectors;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/** 服金效果占用的肺摄入曲线，随身体保存，只撤销本效果的修正。 */
public final class FeedingLungs {
    private static final String PREFIX = "meridian_piglin:feeding/";
    private static final long TICK_MILLIS = 50;
    private static final double INTAKE = 3;

    private FeedingLungs() { }

    public static void apply(LivingEntity caster, MobEffectInstance effect) {
        if (!(caster.level() instanceof ServerLevel) || !PiglinFabric.accepts(caster) || !feeding(effect)) {
            return;
        }
        var body = VanillaDamageFabric.inspectBody(caster).orElseThrow();
        var ids = body.getModifiersList().stream().map(CapabilityModifier::getId).collect(Collectors.toSet());
        var activity = BodyActivity.newBuilder();
        long start = body.getSettledAt().getElapsedMillis();
        long duration = Math.max(1, effect.getDuration()) * TICK_MILLIS;
        for (var organ : body.getStructure().getOrgansList()) {
            if (organ.getKind().equals("human:lung") && !ids.contains(PREFIX + organ.getId())) {
                activity.addAddModifiers(intake(organ.getId(), start, duration));
            }
        }
        if (activity.getAddModifiersCount() > 0) {
            PiglinGoldFabric.submit(caster, body, activity.build()).orElseThrow();
        }
    }

    public static void remove(LivingEntity caster, Collection<MobEffectInstance> effects) {
        if (!(caster.level() instanceof ServerLevel) || !PiglinFabric.accepts(caster)
                || effects.stream().noneMatch(FeedingLungs::feeding)) {
            return;
        }
        var current = VanillaDamageFabric.inspectBody(caster);
        if (current.isEmpty()) {
            return;
        }
        var body = current.get();
        var ids = body.getModifiersList().stream()
                .filter(modifier -> modifier.getSource().equals(PiglinGoldFabric.CALLER))
                .map(CapabilityModifier::getId).filter(id -> id.startsWith(PREFIX)).toList();
        if (!ids.isEmpty()) {
            var activity = BodyActivity.newBuilder().addAllRemoveModifiers(ids).build();
            PiglinGoldFabric.submit(caster, body, activity);
        }
    }

    private static boolean feeding(MobEffectInstance effect) {
        return effect.is(TechniqueStatusBenefits.get(GoldForms.PREFIX + "feeding").effect());
    }

    private static CapabilityModifier intake(String organ, long start, long duration) {
        var first = CurvePoint.newBuilder().setValue(1).build();
        var last = first.toBuilder().setOffsetMillis(duration).build();
        return CapabilityModifier.newBuilder()
                .setSource(PiglinGoldFabric.CALLER)
                .setId(PREFIX + organ)
                .setOrganId(organ)
                .setStartMillis(start)
                .setStrengthPeak(1)
                .setBurstPeak(1)
                .setEndurancePeak(1)
                .setRecoveryPeak(1)
                .setIntakePeak(INTAKE)
                .addCurve(first)
                .addCurve(last)
                .build();
    }
}
