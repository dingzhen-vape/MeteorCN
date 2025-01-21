/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.combat;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.pathing.PathManagers;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.Target;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.world.TickRate;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Tameable;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.mob.ZombifiedPiglinEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.GameMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public class KillAura extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTargeting = settings.createGroup("目标");
    private final SettingGroup sgTiming = settings.createGroup("时机");

    // General

    private final Setting<Weapon> weapon = sgGeneral.add(new EnumSetting.Builder<Weapon>()
        .name("武器")
        .description("只在手持指定的武器时攻击实体。")
        .defaultValue(Weapon.All)
        .build()
    );

    private final Setting<RotationMode> rotation = sgGeneral.add(new EnumSetting.Builder<RotationMode>()
        .name("旋转")
        .description("决定何时朝向目标旋转。")
        .defaultValue(RotationMode.Always)
        .build()
    );

    private final Setting<Boolean> autoSwitch = sgGeneral.add(new BoolSetting.Builder()
        .name("自动切换")
        .description("攻击目标时切换到你选择的武器。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> onlyOnClick = sgGeneral.add(new BoolSetting.Builder()
        .name("仅点击")
        .description("只在按住左键时攻击。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> onlyOnLook = sgGeneral.add(new BoolSetting.Builder()
        .name("仅注视")
        .description("只在注视实体时攻击。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> pauseOnCombat = sgGeneral.add(new BoolSetting.Builder()
        .name("暂停巴音")
        .description("暂时冻结巴音直到你攻击完实体。")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShieldMode> shieldMode = sgGeneral.add(new EnumSetting.Builder<ShieldMode>()
        .name("盾牌模式")
        .description("会尝试用斧头破坏目标的盾牌。")
        .defaultValue(ShieldMode.Break)
        .visible(() -> autoSwitch.get() && weapon.get() != Weapon.Axe)
        .build()
    );

    // Targeting

    private final Setting<Set<EntityType<?>>> entities = sgTargeting.add(new EntityTypeListSetting.Builder()
        .name("实体")
        .description("要攻击的实体。")
        .onlyAttackable()
        .defaultValue(EntityType.PLAYER)
        .build()
    );

    private final Setting<SortPriority> priority = sgTargeting.add(new EnumSetting.Builder<SortPriority>()
        .name("优先级")
        .description("如何筛选范围内的目标。")
        .defaultValue(SortPriority.ClosestAngle)
        .build()
    );

    private final Setting<Integer> maxTargets = sgTargeting.add(new IntSetting.Builder()
        .name("最大目标")
        .description("一次可以目标的实体数量。")
        .defaultValue(1)
        .min(1)
        .sliderRange(1, 5)
        .visible(() -> !onlyOnLook.get())
        .build()
    );

    private final Setting<Double> range = sgTargeting.add(new DoubleSetting.Builder()
        .name("范围")
        .description("可以攻击实体的最大距离。")
        .defaultValue(4.5)
        .min(0)
        .sliderMax(6)
        .build()
    );

    private final Setting<Double> wallsRange = sgTargeting.add(new DoubleSetting.Builder()
        .name("穿墙范围")
        .description("可以穿墙攻击实体的最大距离。")
        .defaultValue(3.5)
        .min(0)
        .sliderMax(6)
        .build()
    );

    private final Setting<EntityAge> mobAgeFilter = sgTargeting.add(new EnumSetting.Builder<EntityAge>()
        .name("忽略幼体")
        .description("是否攻击实体的幼体变种。")
        .defaultValue(EntityAge.Adult)
        .build()
    );

    private final Setting<Boolean> ignoreNamed = sgTargeting.add(new BoolSetting.Builder()
        .name("忽略命名")
        .description("是否攻击有名字的生物。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> ignorePassive = sgTargeting.add(new BoolSetting.Builder()
        .name("忽略被动")
        .description("只在有时被动的生物攻击你时攻击它们。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> ignoreTamed = sgTargeting.add(new BoolSetting.Builder()
        .name("忽略驯服")
        .description("会避免攻击你驯服的生物。")
        .defaultValue(false)
        .build()
    );

    // Timing

    private final Setting<Boolean> pauseOnLag = sgTiming.add(new BoolSetting.Builder()
        .name("暂停卡顿")
        .description("服务器卡顿时暂停。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> pauseOnUse = sgTiming.add(new BoolSetting.Builder()
        .name("暂停使用")
        .description("使用物品时不攻击。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> pauseOnCA = sgTiming.add(new BoolSetting.Builder()
        .name("暂停CA")
        .description("CA放置时不攻击。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> tpsSync = sgTiming.add(new BoolSetting.Builder()
        .name("TPS同步")
        .description("尝试将攻击延迟与服务器的TPS同步。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> customDelay = sgTiming.add(new BoolSetting.Builder()
        .name("自定义延迟")
        .description("使用自定义的延迟而不是原版的冷却。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> hitDelay = sgTiming.add(new IntSetting.Builder()
        .name("攻击延迟")
        .description("以刻为单位的攻击实体的速度。")
        .defaultValue(11)
        .min(0)
        .sliderMax(60)
        .visible(customDelay::get)
        .build()
    );

    private final Setting<Integer> switchDelay = sgTiming.add(new IntSetting.Builder()
        .name("切换延迟")
        .description("切换快捷栏槽位后攻击实体前等待的刻数。")
        .defaultValue(0)
        .min(0)
        .sliderMax(10)
        .build()
    );

    private final List<Entity> targets = new ArrayList<>();
    private int switchTimer, hitTimer;
    private boolean wasPathing = false;
    public boolean attacking;

    public KillAura() {
        super(Categories.Combat, "杀戮光环", "攻击你周围的指定实体。");
    }

    @Override
    public void onDeactivate() {
        targets.clear();
        attacking = false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!mc.player.isAlive() || PlayerUtils.getGameMode() == GameMode.SPECTATOR) return;
        if (pauseOnUse.get() && (mc.interactionManager.isBreakingBlock() || mc.player.isUsingItem())) return;
        if (onlyOnClick.get() && !mc.options.attackKey.isPressed()) return;
        if (TickRate.INSTANCE.getTimeSinceLastTick() >= 1f && pauseOnLag.get()) return;
        if (pauseOnCA.get() && Modules.get().get(CrystalAura.class).isActive() && Modules.get().get(CrystalAura.class).kaTimer > 0) return;

        if (onlyOnLook.get()) {
            Entity targeted = mc.targetedEntity;

            if (targeted == null) return;
            if (!entityCheck(targeted)) return;

            targets.clear();
            targets.add(mc.targetedEntity);
        } else {
            targets.clear();
            TargetUtils.getList(targets, this::entityCheck, priority.get(), maxTargets.get());
        }

        if (targets.isEmpty()) {
            attacking = false;
            if (wasPathing) {
                PathManagers.get().resume();
                wasPathing = false;
            }
            return;
        }

        Entity primary = targets.getFirst();

        if (autoSwitch.get()) {
            Predicate<ItemStack> predicate = switch (weapon.get()) {
                case Axe -> stack -> stack.getItem() instanceof AxeItem;
                case Sword -> stack -> stack.getItem() instanceof SwordItem;
                case Mace -> stack -> stack.getItem() instanceof MaceItem;
                case Trident -> stack -> stack.getItem() instanceof TridentItem;
                case All -> stack -> stack.getItem() instanceof AxeItem || stack.getItem() instanceof SwordItem || stack.getItem() instanceof MaceItem || stack.getItem() instanceof TridentItem;
                default -> o -> true;
            };
            FindItemResult weaponResult = InvUtils.findInHotbar(predicate);

            if (shouldShieldBreak()) {
                FindItemResult axeResult = InvUtils.findInHotbar(itemStack -> itemStack.getItem() instanceof AxeItem);
                if (axeResult.found()) weaponResult = axeResult;
            }

            InvUtils.swap(weaponResult.slot(), false);
        }

        if (!itemInHand()) return;

        attacking = true;
        if (rotation.get() == RotationMode.Always) Rotations.rotate(Rotations.getYaw(primary), Rotations.getPitch(primary, Target.Body));
        if (pauseOnCombat.get() && PathManagers.get().isPathing() && !wasPathing) {
            PathManagers.get().pause();
            wasPathing = true;
        }

        if (delayCheck()) targets.forEach(this::attack);
    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (event.packet instanceof UpdateSelectedSlotC2SPacket) {
            switchTimer = switchDelay.get();
        }
    }

    private boolean shouldShieldBreak() {
        for (Entity target : targets) {
            if (target instanceof PlayerEntity player) {
                if (player.blockedByShield(mc.world.getDamageSources().playerAttack(mc.player)) && shieldMode.get() == ShieldMode.Break) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean entityCheck(Entity entity) {
        if (entity.equals(mc.player) || entity.equals(mc.cameraEntity)) return false;
        if ((entity instanceof LivingEntity livingEntity && livingEntity.isDead()) || !entity.isAlive()) return false;

        Box hitbox = entity.getBoundingBox();
        if (!PlayerUtils.isWithin(
            MathHelper.clamp(mc.player.getX(), hitbox.minX, hitbox.maxX),
            MathHelper.clamp(mc.player.getY(), hitbox.minY, hitbox.maxY),
            MathHelper.clamp(mc.player.getZ(), hitbox.minZ, hitbox.maxZ),
            range.get()
        )) return false;

        if (!entities.get().contains(entity.getType())) return false;
        if (ignoreNamed.get() && entity.hasCustomName()) return false;
        if (!PlayerUtils.canSeeEntity(entity) && !PlayerUtils.isWithin(entity, wallsRange.get())) return false;
        if (ignoreTamed.get()) {
            if (entity instanceof Tameable tameable
                && tameable.getOwnerUuid() != null
                && tameable.getOwnerUuid().equals(mc.player.getUuid())
            ) return false;
        }
        if (ignorePassive.get()) {
            if (entity instanceof EndermanEntity enderman && !enderman.isAngry()) return false;
            if (entity instanceof ZombifiedPiglinEntity piglin && !piglin.isAttacking()) return false;
            if (entity instanceof WolfEntity wolf && !wolf.isAttacking()) return false;
        }
        if (entity instanceof PlayerEntity player) {
            if (player.isCreative()) return false;
            if (!Friends.get().shouldAttack(player)) return false;
            if (shieldMode.get() == ShieldMode.Ignore && player.blockedByShield(mc.world.getDamageSources().playerAttack(mc.player))) return false;
        }
        if (entity instanceof AnimalEntity animal) {
            return switch (mobAgeFilter.get()) {
                case Baby -> animal.isBaby();
                case Adult -> !animal.isBaby();
                case Both -> true;
            };
        }
        return true;
    }

    private boolean delayCheck() {
        if (switchTimer > 0) {
            switchTimer--;
            return false;
        }

        float delay = (customDelay.get()) ? hitDelay.get() : 0.5f;
        if (tpsSync.get()) delay /= (TickRate.INSTANCE.getTickRate() / 20);

        if (customDelay.get()) {
            if (hitTimer < delay) {
                hitTimer++;
                return false;
            } else return true;
        } else return mc.player.getAttackCooldownProgress(delay) >= 1;
    }

    private void attack(Entity target) {
        if (rotation.get() == RotationMode.OnHit) Rotations.rotate(Rotations.getYaw(target), Rotations.getPitch(target, Target.Body));

        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);

        hitTimer = 0;
    }

    private boolean itemInHand() {
        if (shouldShieldBreak()) return mc.player.getMainHandStack().getItem() instanceof AxeItem;

        return switch (weapon.get()) {
            case Axe -> mc.player.getMainHandStack().getItem() instanceof AxeItem;
            case Sword -> mc.player.getMainHandStack().getItem() instanceof SwordItem;
            case Mace -> mc.player.getMainHandStack().getItem() instanceof MaceItem;
            case Trident -> mc.player.getMainHandStack().getItem() instanceof TridentItem;
            case All -> mc.player.getMainHandStack().getItem() instanceof AxeItem || mc.player.getMainHandStack().getItem() instanceof SwordItem || mc.player.getMainHandStack().getItem() instanceof MaceItem || mc.player.getMainHandStack().getItem() instanceof TridentItem;
            default -> true;
        };
    }

    public Entity getTarget() {
        if (!targets.isEmpty()) return targets.getFirst();
        return null;
    }

    @Override
    public String getInfoString() {
        if (!targets.isEmpty()) return EntityUtils.getName(getTarget());
        return null;
    }

    public enum Weapon {
        Sword,
        Axe,
        Mace,
        Trident,
        All,
        Any
    }

    public enum RotationMode {
        Always,
        OnHit,
        None
    }

    public enum ShieldMode {
        Ignore,
        Break,
        None
    }

    public enum EntityAge {
        Baby,
        Adult,
        Both
    }
}
