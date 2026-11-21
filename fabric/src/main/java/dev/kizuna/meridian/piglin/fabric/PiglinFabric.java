package dev.kizuna.meridian.piglin.fabric;

import dev.kizuna.meridian.human.server.HumanContent;
import dev.kizuna.meridian.human.server.HumanDamage;
import dev.kizuna.meridian.humanoid.fabric.HumanoidSpeciesHooks;
import dev.kizuna.meridian.humanoid.server.HumanoidTier;
import dev.kizuna.meridian.humanoid.server.SpeciesBodyRules;
import dev.kizuna.meridian.piglin.server.PiglinBody;
import dev.kizuna.meridian.piglin.server.PiglinContent;
import dev.kizuna.meridian.protocol.pb.BodyMessages.BodyTemplate;
import dev.kizuna.meridian.vanilla.fabric.VanillaDamageFabric;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import net.fabricmc.api.ModInitializer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;

/** 猪灵和蛮兵的身体宿主接线，保留原版 Brain 与装备行为。 */
public final class PiglinFabric implements ModInitializer {
    static final Map<EntityType<?>, String> SPECIES = Map.of(
            EntityTypes.PIGLIN, "piglin",
            EntityTypes.PIGLIN_BRUTE, "piglin_brute");
    private static final String ENTRY = """
            [species."minecraft:%1$s"]
            template = "%2$s"
            default_tier = "mortal"
            brain = false
            [species."minecraft:%1$s".profile]
            true_qi_base_probability = 0.0
            opening_count_multiplier = 1.0
            fill_multiplier = 1.0
            wall_fill_multiplier = 1.0
            arm_opening_weight = 1.0
            foot_opening_weight = 1.0
            """;

    @Override
    public void onInitialize() {
        var bundle = PiglinContent.bundle();
        BodyTemplate selectedTemplate = bundle.getTemplates(0);
        var rules = new SpeciesBodyRules(
                request -> PiglinBody.born(selectedTemplate, request.bodyId(), request.now()),
                (wounded, damage) -> PiglinBody.lethal(wounded),
                (spawn, random) -> HumanoidTier.MORTAL,
                UnaryOperator.identity());
        String table = SPECIES.values().stream()
                .sorted()
                .map(id -> ENTRY.formatted(id, PiglinContent.TEMPLATE_ID))
                .reduce((left, right) -> left + "\n" + right)
                .orElseThrow();
        HumanoidSpeciesHooks.registerAll(table, rules, PiglinFabric::accepts, List.of(bundle));
        HumanDamage.registerTemplateDefaults(PiglinContent.TEMPLATE_ID, HumanContent.TEMPLATE_ID);
        VanillaDamageFabric.registerTemplateDefaults(PiglinContent.TEMPLATE_ID, HumanContent.TEMPLATE_ID);
    }

    public static boolean accepts(LivingEntity entity) {
        return !entity.isBaby() && SPECIES.containsKey(entity.getType());
    }
}
