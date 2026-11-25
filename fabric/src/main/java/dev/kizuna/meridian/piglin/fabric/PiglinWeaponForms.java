package dev.kizuna.meridian.piglin.fabric;

import dev.kizuna.meridian.protocol.pb.BodyMessages.BodySnapshot;
import dev.kizuna.meridian.technique.fabric.TechniqueFabric;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.CastPhase;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.ProjectileWeaponCategory;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.TechniqueBook;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.TechniqueDefinition;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.TechniqueProgress;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.TechniqueReference;
import dev.kizuna.meridian.technique.server.TechniqueRegistry;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.item.Item;

/** 只对原版 Brain 已经选定的对手尝试已学兵器招式。 */
final class PiglinWeaponForms {
    private static final Map<String, Weapon> BOOKS = Map.of(
            "meridian_door_breaker:door_breaker", new Weapon(tag("meridian_door_breaker:axes"),
                    ProjectileWeaponCategory.PROJECTILE_WEAPON_CATEGORY_UNSPECIFIED, "cast.meridian_piglin.axe"),
            "meridian_wayfarer:wayfarer", new Weapon(tag("meridian_wayfarer:crossbows"),
                    ProjectileWeaponCategory.CROSSBOW, "cast.meridian_piglin.crossbow"));

    private PiglinWeaponForms() { }

    private static TagKey<Item> tag(String id) {
        return TagKey.create(Registries.ITEM, Identifier.parse(id));
    }

    static Optional<String> check(AbstractPiglin caster, TechniqueReference reference) {
        var registry = TechniqueFabric.service().registry();
        return weapon(registry, reference)
                .filter(rule -> !rule.permits(caster, registry.find(reference).orElseThrow()))
                .map(rule -> Component.translatable(rule.reasonKey()).getString());
    }

    static void tryKnown(AbstractPiglin caster, BodySnapshot body, TechniqueProgress progress) {
        var target = caster.getTarget();
        if (target == null || !target.isAlive() || !caster.hasLineOfSight(target)) {
            return;
        }
        var registry = TechniqueFabric.service().registry();
        long now = body.getSettledAt().getElapsedMillis();
        double distance = caster.distanceTo(target);
        for (var known : progress.getKnownList()) {
            var reference = known.getTechnique();
            var rule = weapon(registry, reference);
            if (rule.isEmpty() || progress.getCooldownsOrDefault(reference.getId(), 0) > now) {
                continue;
            }
            var definition = registry.find(reference).orElseThrow();
            var hints = definition.getAiHints();
            if (distance < hints.getMinimumDistance().getValue() || distance > hints.getMaximumDistance().getValue()
                    || !rule.get().permits(caster, definition)) {
                continue;
            }
            if (GoldForms.cast(caster, body, reference).getPhase() == CastPhase.WARMUP) {
                return;
            }
        }
    }

    private static Optional<Weapon> weapon(TechniqueRegistry registry, TechniqueReference reference) {
        return registry.bookOf(reference).map(TechniqueBook::getId).map(BOOKS::get);
    }

    private record Weapon(TagKey<Item> tag, ProjectileWeaponCategory projectile, String reasonKey) {
        boolean permits(AbstractPiglin caster, TechniqueDefinition definition) {
            return definition.getProjectileWeapon() == projectile && caster.isHolding(stack -> stack.is(tag));
        }
    }
}
