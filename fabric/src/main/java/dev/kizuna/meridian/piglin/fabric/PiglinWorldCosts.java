package dev.kizuna.meridian.piglin.fabric;

import dev.kizuna.meridian.core.BodySnapshot;
import dev.kizuna.meridian.piglin.server.PiglinContent;
import dev.kizuna.meridian.piglin.server.PiglinTechniqueCosts;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.TechniqueDefinition;
import dev.kizuna.meridian.technique.server.TechniqueAddonCostEntrypoint;
import dev.kizuna.meridian.technique.server.TechniqueAddonCostPayers;
import java.util.UUID;
import net.minecraft.world.entity.monster.piglin.Piglin;

/** 由服务端已绑定的身体查施术者所在处的魂火。 */
public final class PiglinWorldCosts extends PiglinTechniqueCosts.GoldPayer implements TechniqueAddonCostEntrypoint {
    private static final String BODY_PREFIX = "meridian_humanoid:entity/";
    private static final double SOUL_FIRE_COST = 2;

    @Override
    public void registerPayers(TechniqueAddonCostPayers payers) {
        payers.register(this);
    }

    @Override
    public double qiMultiplier(BodySnapshot body, TechniqueDefinition technique) {
        if (!body.template().id().value().equals(PiglinContent.TEMPLATE_ID)) {
            return 1;
        }
        String id = body.id().value();
        if (id.startsWith(BODY_PREFIX)) {
            var caster = GoldForms.actor(UUID.fromString(id.substring(BODY_PREFIX.length())));
            if (caster instanceof Piglin && PiglinBehaviorFabric.hasSoulFireNearby(caster)) {
                return SOUL_FIRE_COST;
            }
        }
        return 1;
    }
}
