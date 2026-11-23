package dev.kizuna.meridian.piglin.fabric;

import dev.kizuna.meridian.piglin.server.PiglinGold;
import dev.kizuna.meridian.technique.fabric.TechniqueFabric;
import dev.kizuna.meridian.technique.server.TechniqueService;
import dev.kizuna.meridian.vanilla.fabric.VanillaDamageFabric;
import dev.kizuna.meridian.vanilla.fabric.IncomingDamage;
import dev.kizuna.meridian.vanilla.fabric.mixin.HurtCooldownAccess;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

final class GoldAttacks {
    static final double DART_RANGE = 12;
    static final double REACH = 2.5;
    private static final float DART_DAMAGE = 2;
    private static final float DART_QI_DAMAGE = 1;
    private static final int HOTBAR_AND_POCKET_SIZE = 18;
    private static final long MISS_RECOVERY_MILLIS = 1000;
    private static final ResourceKey<DamageType> DART_TYPE = ResourceKey.create(Registries.DAMAGE_TYPE,
            Identifier.parse("meridian_piglin:coin_dart"));
    private static final ResourceKey<DamageType> DART_QI_TYPE = ResourceKey.create(Registries.DAMAGE_TYPE,
            Identifier.parse("meridian_piglin:coin_dart_qi"));

    private GoldAttacks() {
    }

    static void register() {
        VanillaDamageFabric.registerDamageObserver(event -> {
            if (event.source() instanceof DartImpact impact) {
                impact.hitBody = true;
                if (!event.bodyDead() && event.input().guarded().isEmpty() && event.input().damageAfterEffects() > 0) {
                    event.input().hit().ifPresent(hit -> impact.part = hit.getPartId());
                }
            }
        });
    }

    static void dart(Mob caster, ServerLevel level) {
        caster.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        var hit = ProjectileUtil.getHitResultOnViewVector(caster, entity -> entity instanceof LivingEntity, DART_RANGE);
        Vec3 origin = caster.getEyePosition();
        var nugget = new ItemEntity(level, origin.x, origin.y, origin.z, new ItemStack(Items.GOLD_NUGGET));
        nugget.setThrower(caster);
        if (hit instanceof EntityHitResult contact && contact.getEntity() instanceof LivingEntity target) {
            dartHit(level, caster, nugget, target);
        }
        nugget.setPos(hit.getLocation());
        nugget.setDeltaMovement(Vec3.ZERO);
        level.addFreshEntity(nugget);
    }

    private static void dartHit(ServerLevel level, Mob caster, ItemEntity nugget, LivingEntity target) {
        var types = level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE);
        var impact = new DartImpact(types.getOrThrow(DART_TYPE), nugget, caster);
        if (!target.hurtServer(level, impact, DART_DAMAGE) || !target.isAlive()) {
            return;
        }
        var qi = new IncomingDamage.QiSource(types.getOrThrow(DART_QI_TYPE), nugget, caster);
        if (impact.hitBody) {
            if (impact.part != null) {
                var extra = new VanillaDamageFabric.DirectDamage(target, qi, impact.part,
                        VanillaDamageFabric.bodyHp(DART_QI_DAMAGE));
                VanillaDamageFabric.damageDirect(extra);
            }
            return;
        }
        // 未绑定身体的目标沿用原版受击；附伤不改变主击留下的无敌帧门槛。
        var cooldown = (HurtCooldownAccess) target;
        float lastHit = cooldown.meridian$lastHurt();
        int invulnerability = target.invulnerableTime;
        try {
            target.hurtServer(level, qi, DART_QI_DAMAGE);
        } finally {
            cooldown.meridian$setLastHurt(lastHit);
            target.invulnerableTime = invulnerability;
        }
    }

    /** 伤害观察器只向本次主击返回实际命中部位。 */
    private static final class DartImpact extends DamageSource {
        private boolean hitBody;
        private String part;

        private DartImpact(Holder<DamageType> type, ItemEntity nugget, Mob caster) {
            super(type, nugget, caster);
        }
    }

    static void devour(Mob caster) {
        caster.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        var hit = ProjectileUtil.getHitResultOnViewVector(caster, entity -> entity instanceof LivingEntity, REACH);
        if (hit instanceof EntityHitResult contact && contact.getEntity() instanceof LivingEntity target
                && takeGold(caster, target)) {
            return;
        }
        var recovery = new TechniqueService.Stagger(MISS_RECOVERY_MILLIS, 0, TechniqueService.StaggerKind.HOLD);
        TechniqueFabric.service().stagger(caster.getUUID(), recovery);
    }

    static boolean takeGold(LivingEntity caster, LivingEntity target) {
        if (!(caster instanceof Piglin)) {
            return false;
        }
        long stored = PiglinGoldFabric.gold(caster);
        long free = PiglinGoldFabric.capacity(caster) - stored;
        List<Take> takes = new ArrayList<>();
        for (ItemStack hand : List.of(target.getMainHandItem(), target.getOffhandItem())) {
            if (goldTool(hand)) {
                if (free < PiglinGold.INGOT_VALUE) {
                    return false;
                }
                takes.add(new Take(hand, 1, PiglinGold.INGOT_VALUE));
                return deposit(caster, stored, takes);
            }
        }
        long budget = Math.min(free, PiglinGold.INGOT_VALUE);
        budget -= collect(takes, target.getOffhandItem(), budget);
        if (target instanceof Player player) {
            var inventory = player.getInventory();
            for (int slot = 0; slot < HOTBAR_AND_POCKET_SIZE && budget > 0; slot++) {
                budget -= collect(takes, inventory.getItem(slot), budget);
            }
        } else {
            budget -= collect(takes, target.getMainHandItem(), budget);
            if (target instanceof Piglin piglin) {
                var inventory = piglin.getInventory();
                for (int slot = 0; slot < inventory.getContainerSize() && budget > 0; slot++) {
                    budget -= collect(takes, inventory.getItem(slot), budget);
                }
            }
        }
        return deposit(caster, stored, takes);
    }

    static boolean holdsGoldTool(LivingEntity target) {
        return goldTool(target.getMainHandItem()) || goldTool(target.getOffhandItem());
    }

    private static boolean goldTool(ItemStack stack) {
        return stack.is(ItemTags.PIGLIN_LOVED) && stack.isDamageableItem();
    }

    private static long collect(List<Take> takes, ItemStack stack, long budget) {
        if (budget == 0 || !(stack.is(Items.GOLD_INGOT) || stack.is(Items.GOLD_NUGGET))) {
            return 0;
        }
        long unit = PiglinGold.NUGGET_VALUE;
        if (stack.is(Items.GOLD_INGOT)) {
            unit = PiglinGold.INGOT_VALUE;
        }
        int count = (int) Math.min(stack.getCount(), budget / unit);
        long value = count * unit;
        if (count > 0) {
            takes.add(new Take(stack, count, value));
        }
        return value;
    }

    private static boolean deposit(LivingEntity caster, long stored, List<Take> takes) {
        long value = takes.stream().mapToLong(Take::value).sum();
        if (value == 0 || PiglinGoldFabric.setGold(caster, stored + value).isEmpty()) {
            return false;
        }
        takes.forEach(take -> take.stack().shrink(take.count()));
        return true;
    }

    private record Take(ItemStack stack, int count, long value) { }
}
