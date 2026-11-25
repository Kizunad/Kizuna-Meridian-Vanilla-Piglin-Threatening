package dev.kizuna.meridian.piglin.fabric;

import com.mojang.serialization.MapCodec;
import dev.kizuna.meridian.humanoid.server.HumanoidTier;
import dev.kizuna.meridian.vanilla.fabric.VanillaDamageFabric;
import java.util.Set;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.NestedLootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/** 原版箱子与易货沿用自己的奖池，额外的卷轴抽取由剑卷数据表控制。 */
final class PiglinSwordLoot {
    private static final Set<ResourceKey<LootTable>> BASTIONS = Set.of(
            BuiltInLootTables.BASTION_BRIDGE, BuiltInLootTables.BASTION_HOGLIN_STABLE,
            BuiltInLootTables.BASTION_OTHER, BuiltInLootTables.BASTION_TREASURE);

    private PiglinSwordLoot() { }

    static void register() {
        Registry.register(BuiltInRegistries.LOOT_CONDITION_TYPE, Identifier.parse("meridian_piglin:practitioner"),
                Practitioner.CODEC);
        LootTableEvents.MODIFY.register((key, table, source, registries) -> {
            if (!source.isBuiltin()) {
                return;
            }
            if (BASTIONS.contains(key)) {
                table.withPool(scroll("bastion_scroll"));
            } else if (key.equals(BuiltInLootTables.PIGLIN_BARTERING)) {
                table.withPool(scroll("barter_scroll").when(() -> Practitioner.INSTANCE));
            }
        });
    }

    private static LootPool.Builder scroll(String path) {
        var id = Identifier.fromNamespaceAndPath("meridian_piglin_sword", path);
        var key = ResourceKey.create(Registries.LOOT_TABLE, id);
        return LootPool.lootPool().add(NestedLootTable.lootTableReference(key));
    }

    private enum Practitioner implements LootItemCondition {
        INSTANCE;

        private static final MapCodec<Practitioner> CODEC = MapCodec.unit(INSTANCE);

        @Override
        public MapCodec<Practitioner> codec() {
            return CODEC;
        }

        @Override
        public Set<ContextKey<?>> getReferencedContextParams() {
            return Set.of(LootContextParams.THIS_ENTITY);
        }

        @Override
        public boolean test(LootContext context) {
            return context.getOptionalParameter(LootContextParams.THIS_ENTITY) instanceof Piglin piglin
                    && piglin.isAdult() && VanillaDamageFabric.inspectBody(piglin)
                    .map(HumanoidTier::of).filter(tier -> tier != HumanoidTier.MORTAL).isPresent();
        }
    }
}
