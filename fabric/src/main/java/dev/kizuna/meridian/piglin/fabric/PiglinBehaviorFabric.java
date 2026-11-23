package dev.kizuna.meridian.piglin.fabric;

import dev.kizuna.meridian.piglin.server.PiglinGold;
import dev.kizuna.meridian.technique.fabric.TechniqueFabric;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.CastPhase;
import dev.kizuna.meridian.technique.server.TechniqueService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;

/** 猪灵原版 Brain 外的服金、魂火和见金状态接线。 */
public final class PiglinBehaviorFabric {
    private static final int REPELLENT_HORIZONTAL_RANGE = 8;
    private static final int REPELLENT_VERTICAL_RANGE = 4;
    private static final int GOLD_NOTICE_RANGE = 9;
    private static final int GOLD_STRUCK_TICKS = 60;
    private static final int GOLD_SATED_TICKS = 400;
    private static final long NO_GOLD = 0;
    private static final Map<UUID, GoldState> GOLD_STATES = new HashMap<>();

    private PiglinBehaviorFabric() {
    }

    public static void register() {
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> GOLD_STATES.clear());
    }

    public static void tick(Piglin piglin) {
        if (!piglin.isAdult()) {
            return;
        }
        GoldState state = GOLD_STATES.computeIfAbsent(piglin.getUUID(), ignored -> new GoldState());
        advanceGoldState(state);
        noticeGold(piglin, state);
        if (isGoldStruck(piglin)) {
            return;
        }
        if (state.satedTicks > 0) {
            return;
        }
        if (isAdmiringItem(piglin)) {
            return;
        }
        if (PiglinGoldFabric.capacity(piglin) == PiglinGold.MORTAL_CAPACITY) {
            swallowInventoryGold(piglin);
        }
    }

    public static boolean swallowGold(Piglin piglin) {
        if (!piglin.isAdult() || hasSoulFireNearby(piglin)) {
            return false;
        }
        GoldState state = GOLD_STATES.get(piglin.getUUID());
        if (state != null && (state.struckTicks > 0 || state.satedTicks > 0)) {
            return false;
        }
        long currentGold = PiglinGoldFabric.gold(piglin);
        long free = PiglinGoldFabric.capacity(piglin) - currentGold;
        if (free <= NO_GOLD) {
            return false;
        }
        GoldTake take = findGoldIncludingOffhand(piglin, free);
        if (take.value() <= NO_GOLD) {
            return false;
        }
        PiglinGoldFabric.setGold(piglin, currentGold + take.value()).orElseThrow();
        take.consume();
        return true;
    }

    public static boolean swallowPickedUpGold(Piglin piglin, ItemStack stack) {
        if (!piglin.isAdult() || PiglinGoldFabric.capacity(piglin) != PiglinGold.MORTAL_CAPACITY
                || hasSoulFireNearby(piglin)) {
            return false;
        }
        GoldState state = GOLD_STATES.get(piglin.getUUID());
        if (state != null && (state.struckTicks > 0 || state.satedTicks > 0)) {
            return false;
        }
        long currentGold = PiglinGoldFabric.gold(piglin);
        long free = PiglinGoldFabric.capacity(piglin) - currentGold;
        GoldTake take = new GoldTake();
        collectStack(take, stack, free);
        if (take.value() <= NO_GOLD) {
            return false;
        }
        PiglinGoldFabric.setGold(piglin, currentGold + take.value()).orElseThrow();
        take.consume();
        return true;
    }

    static boolean canFeed(AbstractPiglin caster) {
        long free = PiglinGoldFabric.capacity(caster) - PiglinGoldFabric.gold(caster);
        return nextFood(caster, free) != ItemStack.EMPTY;
    }

    static boolean eatOne(AbstractPiglin caster) {
        if (caster instanceof Piglin piglin && (hasSoulFireNearby(piglin) || isGoldStruck(piglin))) {
            return false;
        }
        long current = PiglinGoldFabric.gold(caster);
        ItemStack food = nextFood(caster, PiglinGoldFabric.capacity(caster) - current);
        if (food.isEmpty() || PiglinGoldFabric.setGold(caster, current + unitValue(food)).isEmpty()) {
            return false;
        }
        food.shrink(1);
        return true;
    }

    private static ItemStack nextFood(AbstractPiglin caster, long room) {
        if (!(caster instanceof Piglin piglin)) {
            return ItemStack.EMPTY;
        }
        ItemStack offhand = caster.getOffhandItem();
        if (fits(offhand, room) && !isAdmiringItem(piglin)) {
            return offhand;
        }
        var inventory = piglin.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (fits(stack, room)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private static boolean fits(ItemStack stack, long room) {
        long value = unitValue(stack);
        return !stack.isEmpty() && value > 0 && value <= room;
    }

    private static long unitValue(ItemStack stack) {
        long value = PiglinGold.value(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        if (value == 0 && stack.is(ItemTags.PIGLIN_LOVED)) {
            return PiglinGold.INGOT_VALUE;
        }
        return value;
    }

    public static boolean hasSoulFireNearby(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return false;
        }
        BlockPos origin = entity.blockPosition();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = -REPELLENT_HORIZONTAL_RANGE; x <= REPELLENT_HORIZONTAL_RANGE; x++) {
            for (int y = -REPELLENT_VERTICAL_RANGE; y <= REPELLENT_VERTICAL_RANGE; y++) {
                for (int z = -REPELLENT_HORIZONTAL_RANGE; z <= REPELLENT_HORIZONTAL_RANGE; z++) {
                    cursor.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                    if (level.getBlockState(cursor).is(BlockTags.PIGLIN_REPELLENTS)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static boolean isWearingSafeArmor(LivingEntity entity) {
        return net.minecraft.world.entity.monster.piglin.PiglinAi.isWearingSafeArmor(entity);
    }

    public static boolean isGoldStruck(Piglin piglin) {
        GoldState state = GOLD_STATES.get(piglin.getUUID());
        return state != null && state.struckTicks > 0;
    }

    public static int goldStruckTicks(Piglin piglin) {
        GoldState state = GOLD_STATES.get(piglin.getUUID());
        return state == null ? 0 : state.struckTicks;
    }

    public static int goldSatedTicks(Piglin piglin) {
        GoldState state = GOLD_STATES.get(piglin.getUUID());
        return state == null ? 0 : state.satedTicks;
    }

    public static void tickForTest(Piglin piglin) {
        tick(piglin);
    }

    private static void swallowInventoryGold(Piglin piglin) {
        if (hasSoulFireNearby(piglin)) {
            return;
        }
        long gold = PiglinGoldFabric.gold(piglin);
        long capacity = PiglinGoldFabric.capacity(piglin);
        long free = capacity - gold;
        if (free <= NO_GOLD) {
            return;
        }
        GoldTake take = findGoldInInventory(piglin, free);
        if (take.value() <= NO_GOLD) {
            return;
        }
        PiglinGoldFabric.setGold(piglin, gold + take.value()).ifPresent(ignored -> take.consume());
    }

    private static GoldTake findGoldIncludingOffhand(Piglin piglin, long free) {
        GoldTake take = new GoldTake();
        collectStack(take, piglin.getItemInHand(InteractionHand.OFF_HAND), free);
        collectInventoryGold(take, piglin, free);
        return take;
    }

    private static GoldTake findGoldInInventory(Piglin piglin, long free) {
        GoldTake take = new GoldTake();
        collectInventoryGold(take, piglin, free);
        return take;
    }

    private static void collectInventoryGold(GoldTake take, Piglin piglin, long free) {
        for (int slot = 0; slot < piglin.getInventory().getContainerSize() && take.value() < free; slot++) {
            collectStack(take, piglin.getInventory().getItem(slot), free - take.value());
        }
    }

    private static void collectStack(GoldTake take, ItemStack stack, long free) {
        if (stack.isEmpty() || free <= NO_GOLD) {
            return;
        }
        long unitValue = unitValue(stack);
        if (unitValue == NO_GOLD) {
            return;
        }
        int count = (int) Math.min(stack.getCount(), free / unitValue);
        if (count <= 0) {
            return;
        }
        take.sources().add(new Source(stack, count));
        take.add(unitValue * count);
    }

    private static boolean isAdmiringItem(Piglin piglin) {
        return piglin.getBrain().hasMemoryValue(MemoryModuleType.ADMIRING_ITEM);
    }

    private static void noticeGold(Piglin piglin, GoldState state) {
        if (state.struckTicks > 0 || state.satedTicks > 0 || !hasLovedItemNearby(piglin)) {
            return;
        }
        if (PiglinGoldFabric.capacity(piglin) != PiglinGold.MORTAL_CAPACITY) {
            if (!TechniqueFabric.running()) {
                return;
            }
            var service = TechniqueFabric.service();
            UUID uuid = piglin.getUUID();
            var warmup = service.observe(uuid).flatMap(TechniqueService.Observation::cast)
                    .filter(cast -> cast.phase() == CastPhase.WARMUP);
            if (warmup.isEmpty() || !service.interruptWarmup(uuid, warmup.get().active().getRequest().getCastId())) {
                return;
            }
        }
        state.struckTicks = GOLD_STRUCK_TICKS;
    }

    private static boolean hasLovedItemNearby(Piglin piglin) {
        if (!(piglin.level() instanceof ServerLevel level)) {
            return false;
        }
        AABB area = piglin.getBoundingBox().inflate(GOLD_NOTICE_RANGE);
        return level.getEntities(EntityTypeTest.forClass(ItemEntity.class), area,
                        item -> isForeignLovedItem(piglin, item))
                .stream()
                .findFirst()
                .isPresent();
    }

    private static boolean isForeignLovedItem(Piglin piglin, ItemEntity item) {
        if (!item.getItem().is(ItemTags.PIGLIN_LOVED)) {
            return false;
        }
        Entity owner = item.getOwner();
        return owner == null || !owner.getUUID().equals(piglin.getUUID());
    }

    private static void advanceGoldState(GoldState state) {
        if (state.struckTicks > 0) {
            state.struckTicks--;
            if (state.struckTicks == 0) {
                state.satedTicks = GOLD_SATED_TICKS;
            }
            return;
        }
        if (state.satedTicks > 0) {
            state.satedTicks--;
        }
    }

    private static final class GoldState {
        private int struckTicks;
        private int satedTicks;
    }

    private static final class GoldTake {
        private final List<Source> sources = new ArrayList<>();
        private long total;

        private List<Source> sources() {
            return sources;
        }

        private long value() {
            return total;
        }

        private void add(long value) {
            total += value;
        }

        private void consume() {
            sources.forEach(source -> source.stack().shrink(source.count()));
        }
    }

    private record Source(ItemStack stack, int count) {
    }
}
