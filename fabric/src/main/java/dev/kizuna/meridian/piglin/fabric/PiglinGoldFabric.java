package dev.kizuna.meridian.piglin.fabric;

import dev.kizuna.meridian.piglin.server.PiglinGold;
import dev.kizuna.meridian.piglin.server.PiglinContent;
import dev.kizuna.meridian.humanoid.fabric.HumanoidActivityHost;
import dev.kizuna.meridian.humanoid.server.HumanoidTier;
import dev.kizuna.meridian.protocol.ActivityHostProtobuf;
import dev.kizuna.meridian.protocol.HealthHostProtobuf;
import dev.kizuna.meridian.protocol.MeridianProtocol;
import dev.kizuna.meridian.protocol.pb.ActivityHostMessages.ActivityHostRequest;
import dev.kizuna.meridian.protocol.pb.ActivityMessages.BodyActivity;
import dev.kizuna.meridian.protocol.pb.BodyMessages.BodySnapshot;
import dev.kizuna.meridian.protocol.pb.HealthHostMessages.HealthHostResponse;
import dev.kizuna.meridian.protocol.pb.MeridianMessages.BodyCommand;
import dev.kizuna.meridian.protocol.pb.MeridianMessages.RequestEnvelope;
import dev.kizuna.meridian.protocol.pb.MeridianMessages.FailureCode;
import dev.kizuna.meridian.server.BodyActivityHost;
import dev.kizuna.meridian.vanilla.fabric.VanillaDamageFabric;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** 金囊进度的活动提交、消耗和金粒掉落接线。 */
public final class PiglinGoldFabric {
    static final String CALLER = "meridian_piglin:gold";
    private static final String ABDOMEN_PART_ID = "abdomen";
    private static final int GOLD_NUGGET_STACK_SIZE = 64;
    private static final Set<UUID> SETTLED_DEATHS = new HashSet<>();

    private PiglinGoldFabric() {
    }

    static void register() {
        VanillaDamageFabric.registerDamageObserver(PiglinGoldFabric::observeDamage);
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> settleDeath(entity));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> SETTLED_DEATHS.clear());
    }

    public static long gold(LivingEntity entity) {
        return VanillaDamageFabric.inspectBody(entity).map(PiglinGold::read).orElse(0L);
    }

    static Optional<GoldSnapshot> snapshot(LivingEntity entity) {
        return VanillaDamageFabric.inspectBody(entity).map(PiglinGoldFabric::snapshot);
    }

    static GoldSnapshot snapshot(BodySnapshot body) {
        return new GoldSnapshot(body, PiglinGold.read(body), PiglinGold.capacity(HumanoidTier.of(body)));
    }

    static long capacity(LivingEntity entity) {
        return VanillaDamageFabric.inspectBody(entity)
                .map(body -> PiglinGold.capacity(HumanoidTier.of(body)))
                .orElse(0L);
    }

    public static boolean addGold(LivingEntity entity, long currentGold) {
        Optional<GoldSnapshot> current = snapshot(entity);
        if (current.isEmpty()) {
            return false;
        }
        long updatedGold = currentGold + PiglinGold.INGOT_VALUE;
        if (updatedGold > current.get().capacity()) {
            return false;
        }
        return setGold(entity, current.get().body(), updatedGold).isPresent();
    }

    static Optional<BodySnapshot> spend(LivingEntity entity, long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("金量消耗必须为正数");
        }
        Optional<BodySnapshot> current = VanillaDamageFabric.inspectBody(entity);
        if (current.isEmpty()) {
            return Optional.empty();
        }
        long currentGold = PiglinGold.read(current.get());
        if (currentGold < amount) {
            return Optional.empty();
        }
        long replacement = currentGold - amount;
        return submitGold(entity, current.get(), replacement);
    }

    public static Optional<BodySnapshot> setGold(LivingEntity entity, long amount) {
        Optional<BodySnapshot> current = VanillaDamageFabric.inspectBody(entity);
        if (current.isEmpty()) {
            return Optional.empty();
        }
        return setGold(entity, current.get(), amount);
    }

    static Optional<BodySnapshot> setGold(LivingEntity entity, BodySnapshot current, long amount) {
        long capacity = PiglinGold.capacity(HumanoidTier.of(current));
        if (amount < 0 || amount > capacity) {
            throw new IllegalArgumentException("金囊金量超出容量");
        }
        return submitGold(entity, current, amount);
    }

    private static void observeDamage(VanillaDamageFabric.DamageEvent event) {
        LivingEntity entity = event.target();
        if (!PiglinFabric.accepts(entity)) {
            return;
        }
        BodySnapshot before = event.result().before();
        BodySnapshot after = event.result().body();
        if (after.getVitality().getDead()) {
            settleDeath(entity, after);
            return;
        }
        if (pouchBroken(before, after)) {
            clearAndDrop(entity, after);
        }
    }

    private static boolean pouchBroken(BodySnapshot before, BodySnapshot after) {
        double beforeHp = organHp(before, PiglinContent.GOLD_POUCH);
        double afterHp = organHp(after, PiglinContent.GOLD_POUCH);
        boolean organDestroyed = beforeHp > 0 && afterHp <= 0;
        double beforePartHp = partHp(before, ABDOMEN_PART_ID);
        double afterPartHp = partHp(after, ABDOMEN_PART_ID);
        boolean pouchPartDestroyed = beforePartHp > 0 && afterPartHp <= 0;
        return organDestroyed || pouchPartDestroyed;
    }

    private static double organHp(BodySnapshot body, String organId) {
        return body.getOrganStatesList().stream()
                .filter(state -> state.getId().equals(organId))
                .findFirst()
                .map(state -> state.getHp())
                .orElse(0D);
    }

    private static double partHp(BodySnapshot body, String partId) {
        return body.getPartStatesList().stream()
                .filter(state -> state.getId().equals(partId))
                .findFirst()
                .map(state -> state.getHp())
                .orElse(0D);
    }

    private static void clearAndDrop(LivingEntity entity, BodySnapshot body) {
        long amount = PiglinGold.read(body);
        if (amount == 0) {
            return;
        }
        List<ItemEntity> dropped = dropNuggets(entity, amount);
        try {
            Optional<BodySnapshot> cleared = submitGold(entity, body, 0);
            if (cleared.isEmpty()) {
                discard(dropped);
                throw new IllegalStateException("金囊清空失败");
            }
        } catch (RuntimeException failure) {
            discard(dropped);
            throw failure;
        }
    }

    private static void settleDeath(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel)) {
            return;
        }
        if (SETTLED_DEATHS.contains(entity.getUUID())) {
            return;
        }
        VanillaDamageFabric.inspectBody(entity).ifPresent(body -> settleDeath(entity, body));
    }

    private static void settleDeath(LivingEntity entity, BodySnapshot body) {
        if (!SETTLED_DEATHS.add(entity.getUUID())) {
            return;
        }
        long amount = PiglinGold.read(body);
        try {
            dropNuggets(entity, amount);
        } catch (RuntimeException failure) {
            SETTLED_DEATHS.remove(entity.getUUID());
            throw failure;
        }
    }

    private static Optional<BodySnapshot> submitGold(LivingEntity entity, BodySnapshot body, long amount) {
        BodyActivity activity = BodyActivity.newBuilder()
                .putProgress(PiglinGold.PROGRESS_KEY, PiglinGold.progress(amount))
                .build();
        return submit(entity, body, activity);
    }

    static Optional<BodySnapshot> submit(LivingEntity entity, BodySnapshot body, BodyActivity activity) {
        BodyCommand command = BodyCommand.newBuilder()
                .setBodyId(body.getId())
                .setExpectedRevision(body.getRevision())
                .setExecuteActivity(activity)
                .build();
        String requestId = UUID.randomUUID().toString();
        RequestEnvelope envelope = RequestEnvelope.newBuilder()
                .setProtocolVersion(MeridianProtocol.VERSION)
                .setRequestId(requestId)
                .setBodyCommand(command)
                .build();
        ActivityHostRequest request = ActivityHostRequest.newBuilder()
                .setProtocolVersion(MeridianProtocol.VERSION)
                .setRequestId(requestId)
                .setSubjectId(entity.getStringUUID())
                .setActivity(envelope)
                .build();
        List<BodyActivityHost> hosts = FabricLoader.getInstance()
                .getEntrypoints("meridian_activity_host", BodyActivityHost.class);
        for (var host : hosts) {
            if (!(host instanceof HumanoidActivityHost)) {
                continue;
            }
            byte[] encoded = ActivityHostProtobuf.encodeRequest(request);
            HealthHostResponse response = HealthHostProtobuf.decodeResponse(host.exchange(CALLER, encoded));
            if (response.hasBody()) {
                return Optional.of(response.getBody());
            }
            if (response.hasFailure() && response.getFailure().getCode() == FailureCode.BODY_DEAD) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    record GoldSnapshot(BodySnapshot body, long gold, long capacity) {
    }

    private static List<ItemEntity> dropNuggets(LivingEntity entity, long amount) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return List.of();
        }
        List<ItemEntity> spawnedItems = new ArrayList<>();
        long remaining = amount;
        while (remaining > 0) {
            int count = (int) Math.min(remaining, GOLD_NUGGET_STACK_SIZE);
            ItemStack stack = new ItemStack(Items.GOLD_NUGGET, count);
            ItemEntity spawnedItem = entity.spawnAtLocation(level, stack);
            if (spawnedItem == null) {
                discard(spawnedItems);
                throw new IllegalStateException("金粒掉落失败");
            }
            spawnedItems.add(spawnedItem);
            remaining -= count;
        }
        return spawnedItems;
    }

    private static void discard(List<ItemEntity> dropped) {
        dropped.forEach(ItemEntity::discard);
    }
}
