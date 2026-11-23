package dev.kizuna.meridian.piglin.fabric;

import dev.kizuna.meridian.vanilla.fabric.IncomingDamage;
import dev.kizuna.meridian.vanilla.fabric.VanillaDamageTypeTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;

/** 金属皮肤与金像的承伤倍率。 */
final class GoldDefense {
    private static final double GILDED_EDGE = 0.6;
    private static final double GILDED_FIRE = 0.5;
    private static final double GILDED_PICK = 1.5;
    private static final double STATUE_PHYSICAL = 0.1;
    private static final double STATUE_QI = 0.5;
    private static final double STATUE_PICK = 3;

    private GoldDefense() {
    }

    static double multiplier(IncomingDamage hit) {
        var target = hit.target();
        if (!PiglinFabric.accepts(target)) {
            return 1;
        }
        boolean statue = GoldForms.has(target, "gold_cast");
        if (!statue && !GoldForms.has(target, "gilded")) {
            return 1;
        }
        var provider = hit.provider();
        var source = provider.source();
        if (source instanceof IncomingDamage.QiSource) {
            return statue ? STATUE_QI : 1;
        }
        if (source.is(DamageTypeTags.IS_FIRE)) {
            return statue ? 0 : GILDED_FIRE;
        }
        if (source.is(VanillaDamageTypeTags.MELEE) && provider.weapon().is(ItemTags.PICKAXES)
                && source.getDirectEntity() == source.getEntity() && !source.is(VanillaDamageTypeTags.LEG)) {
            return statue ? STATUE_PICK : GILDED_PICK;
        }
        return switch (hit.kind()) {
            case CUTTING, PIERCING -> statue ? STATUE_PHYSICAL : GILDED_EDGE;
            case BLUNT -> statue ? STATUE_PHYSICAL : 1;
            case HEAT -> statue ? 0 : GILDED_FIRE;
            default -> 1;
        };
    }
}
