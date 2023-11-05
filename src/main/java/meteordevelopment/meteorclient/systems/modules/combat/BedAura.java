/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.combat;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.*;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.meteorclient.utils.world.CardinalDirection;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BedBlock;
import net.minecraft.block.entity.BedBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BedItem;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class BedAura extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTargeting = settings.createGroup("目标");
    private final SettingGroup sgAutoMove = settings.createGroup("库存");
    private final SettingGroup sgPause = settings.createGroup("暂停");
    private final SettingGroup sgRender = settings.createGroup("渲染");

    // General

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("延迟")
        .description("在刻度中放置床之间的延迟。")
        .defaultValue(9)
        .min(0)
        .sliderMax(20)
        .build()
    );

    private final Setting<Boolean> strictDirection = sgGeneral.add(new BoolSetting.Builder()
        .name("严格方向")
        .description("仅将床放置在您面对的方向上。")
        .defaultValue(false)
        .build()
    );

    // Targeting

    private final Setting<Double> targetRange = sgTargeting.add(new DoubleSetting.Builder()
        .name("target-range")
        .description("玩家可以被瞄准的范围。")
        .defaultValue(4)
        .min(0)
        .sliderMax(5)
        .build()
    );

    private final Setting<SortPriority> priority = sgTargeting.add(new EnumSetting.Builder<SortPriority>()
        .name("target-priority")
        .description("如何过滤范围内的目标。")
        .defaultValue(SortPriority.LowestHealth)
        .build()
    );

    private final Setting<Double> minDamage = sgTargeting.add(new DoubleSetting.Builder()
        .name("min-damage")
        .description("对目标造成的最小伤害.")
        .defaultValue(7)
        .range(0, 36)
        .sliderMax(36)
        .build()
    );

    private final Setting<Double> maxSelfDamage = sgTargeting.add(new DoubleSetting.Builder()
        .name("最大自我伤害")
        .description("对自己造成的最大伤害。")
        .defaultValue(7)
        .range(0, 36)
        .sliderMax(36)
        .build()
    );

    private final Setting<Boolean> antiSuicide = sgTargeting.add(new BoolSetting.Builder()
        .name("反自杀")
        .description("如果他们会杀了你,不会放置和打破床。")
        .defaultValue(true)
        .build()
    );

    // Auto move

    private final Setting<Boolean> autoMove = sgAutoMove.add(new BoolSetting.Builder()
        .name("自动移动")
        .description("将床移动到选定的热栏插槽中。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> autoMoveSlot = sgAutoMove.add(new IntSetting.Builder()
        .name("auto-move-slot")
        .description("插槽自动移动将床移动到。")
        .defaultValue(9)
        .range(1, 9)
        .sliderRange(1, 9)
        .visible(autoMove::get)
        .build()
    );

    private final Setting<Boolean> autoSwitch = sgAutoMove.add(new BoolSetting.Builder()
        .name("自动切换")
        .description("自动切换到床和从床切换。")
        .defaultValue(true)
        .build()
    );

    // Pause

    private final Setting<Boolean> pauseOnEat = sgPause.add(new BoolSetting.Builder()
        .name("pause-on- eat")
        .description("吃饭时暂停。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> pauseOnDrink = sgPause.add(new BoolSetting.Builder()
        .name("pause-on-drink")
        .description("喝酒时暂停。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> pauseOnMine = sgPause.add(new BoolSetting.Builder()
        .name("pause-on-mine")
        .description("挖矿时暂停。")
        .defaultValue(true)
        .build()
    );

    // Render

    private final Setting<Boolean> swing = sgRender.add(new BoolSetting.Builder()
        .name("swing")
        .description("是否摇摆手客户端-side.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("render")
        .description("渲染放置床的块。")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("如何渲染形状。")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .description("要放置的位置的侧面颜色被放置。")
        .defaultValue(new SettingColor(15, 255, 211,75))
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("要放置的位置的线条颜色。")
        .defaultValue(new SettingColor(15, 255, 211))
        .build()
    );

    private CardinalDirection direction;
    private PlayerEntity target;
    private BlockPos placePos, breakPos;
    private int timer;

    public BedAura() {
        super(Categories.Combat, "bed-aura", "自动放置并爆炸下界和末地的床。");
    }

    @Override
    public void onActivate() {
        timer = delay.get();
        direction = CardinalDirection.North;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        // Check if beds can explode here
        if (mc.world.getDimension().bedWorks()) {
            error("你不能炸毁这个维度的床,让人无法行动。");
            toggle();
            return;
        }

        // Pause
        if (PlayerUtils.shouldPause(pauseOnMine.get(), pauseOnEat.get(), pauseOnDrink.get())) return;

        // Find a target
        target = TargetUtils.getPlayerTarget(targetRange.get(), priority.get());
        if (target == null) {
            placePos = null;
            breakPos = null;
            return;
        }

        // Auto move
        if (autoMove.get()) {
            FindItemResult bed = InvUtils.find(itemStack -> itemStack.getItem() instanceof BedItem);

            if (bed.found() && bed.slot() != autoMoveSlot.get() - 1) {
                InvUtils.move().from(bed.slot()).toHotbar(autoMoveSlot.get() - 1);
            }
        }

        if (breakPos == null) {
            placePos = findPlace(target);
        }

        // Place bed
        if (timer <= 0 && placeBed(placePos)) {
            timer = delay.get();
        }
        else {
            timer--;
        }

        if (breakPos == null) breakPos = findBreak();
        breakBed(breakPos);
    }

    private BlockPos findPlace(PlayerEntity target) {
        if (!InvUtils.find(itemStack -> itemStack.getItem() instanceof BedItem).found()) return null;

        for (int index = 0; index < 3; index++) {
            int i = index == 0 ? 1 : index == 1 ? 0 : 2;

            for (CardinalDirection dir : CardinalDirection.values()) {
                if (strictDirection.get()
                    && dir.toDirection() != mc.player.getHorizontalFacing()
                    && dir.toDirection().getOpposite() != mc.player.getHorizontalFacing()) continue;

                BlockPos centerPos = target.getBlockPos().up(i);

                double headSelfDamage = DamageUtils.bedDamage(mc.player, Utils.vec3d(centerPos));
                double offsetSelfDamage = DamageUtils.bedDamage(mc.player, Utils.vec3d(centerPos.offset(dir.toDirection())));

                if (mc.world.getBlockState(centerPos).isReplaceable()
                    && BlockUtils.canPlace(centerPos.offset(dir.toDirection()))
                    && DamageUtils.bedDamage(target, Utils.vec3d(centerPos)) >= minDamage.get()
                    && offsetSelfDamage < maxSelfDamage.get()
                    && headSelfDamage < maxSelfDamage.get()
                    && (!antiSuicide.get() || PlayerUtils.getTotalHealth() - headSelfDamage > 0)
                    && (!antiSuicide.get() || PlayerUtils.getTotalHealth() - offsetSelfDamage > 0)) {
                    return centerPos.offset((direction = dir).toDirection());
                }
            }
        }

        return null;
    }

    private BlockPos findBreak() {
        for (BlockEntity blockEntity : Utils.blockEntities()) {
            if (!(blockEntity instanceof BedBlockEntity)) continue;

            BlockPos bedPos = blockEntity.getPos();
            Vec3d bedVec = Utils.vec3d(bedPos);

            if (PlayerUtils.isWithinReach(bedVec)
                && DamageUtils.bedDamage(target, bedVec) >= minDamage.get()
                && DamageUtils.bedDamage(mc.player, bedVec) < maxSelfDamage.get()
                && (!antiSuicide.get() || PlayerUtils.getTotalHealth() - DamageUtils.bedDamage(mc.player, bedVec) > 0)) {
                return bedPos;
            }
        }

        return null;
    }

    private boolean placeBed(BlockPos pos) {
        if (pos == null) return false;

        FindItemResult bed = InvUtils.findInHotbar(itemStack -> itemStack.getItem() instanceof BedItem);
        if (bed.getHand() == null && !autoSwitch.get()) return false;

        double yaw = switch (direction) {
            case East -> 90;
            case South -> 180;
            case West -> -90;
            default -> 0;
        };

        Rotations.rotate(yaw, Rotations.getPitch(pos), () -> {
            BlockUtils.place(pos, bed, false, 0, swing.get(), true);
            breakPos = pos;
        });

        return true;
    }

    private void breakBed(BlockPos pos) {
        if (pos == null) return;
        breakPos = null;

        if (!(mc.world.getBlockState(pos).getBlock() instanceof BedBlock)) return;

        boolean wasSneaking = mc.player.isSneaking();
        if (wasSneaking) mc.player.setSneaking(false);

        mc.interactionManager.interactBlock(mc.player, Hand.OFF_HAND, new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false));

        mc.player.setSneaking(wasSneaking);
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (render.get() && placePos != null && breakPos == null) {
            int x = placePos.getX();
            int y = placePos.getY();
            int z = placePos.getZ();

            switch (direction) {
                case North -> event.renderer.box(x, y, z, x + 1, y + 0.6, z + 2, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
                case South -> event.renderer.box(x, y, z - 1, x + 1, y + 0.6, z + 1, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
                case East -> event.renderer.box(x - 1, y, z, x + 1, y + 0.6, z + 1, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
                case West -> event.renderer.box(x, y, z, x + 2, y + 0.6, z + 1, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
            }
        }
    }

    @Override
    public String getInfoString() {
        return EntityUtils.getName(target);
    }
}
