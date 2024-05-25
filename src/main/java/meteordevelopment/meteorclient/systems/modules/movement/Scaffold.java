/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.movement;

import com.google.common.collect.Streams;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.FallingBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Scaffold extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("渲染");

    private final Setting<List<Block>> blocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("方块")
        .description("选定的方块。")
        .build()
    );

    private final Setting<ListMode> blocksFilter = sgGeneral.add(new EnumSetting.Builder<ListMode>()
        .name("方块过滤器")
        .description("如何使用方块列表设置")
        .defaultValue(ListMode.Blacklist)
        .build()
    );

    private final Setting<Boolean> fastTower = sgGeneral.add(new BoolSetting.Builder()
        .name("快速塔")
        .description("是否更快地向上搭建脚手架。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> towerSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("塔速度")
        .description("塔的建造速度。")
        .defaultValue(0.5)
        .min(0)
        .sliderMax(1)
        .visible(fastTower::get)
        .build()
    );

    private final Setting<Boolean> whileMoving = sgGeneral.add(new BoolSetting.Builder()
        .name("边移动边建造")
        .description("允许你在移动时建造塔。")
        .defaultValue(false)
        .visible(fastTower::get)
        .build()
    );

    private final Setting<Boolean> onlyOnClick = sgGeneral.add(new BoolSetting.Builder()
        .name("仅在点击时")
        .description("仅在按住右键时放置方块。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> renderSwing = sgGeneral.add(new BoolSetting.Builder()
        .name("摆动")
        .description("渲染你客户端的摆动。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> autoSwitch = sgGeneral.add(new BoolSetting.Builder()
        .name("自动切换")
        .description("放置前自动切换到一个方块。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("旋转")
        .description("旋转向被放置的方块。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> airPlace = sgGeneral.add(new BoolSetting.Builder()
        .name("空中放置")
        .description("允许空中放置。这也允许你修改脚手架半径。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> aheadDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("前方距离")
        .description("放置方块的前方距离。")
        .defaultValue(0)
        .min(0)
        .sliderMax(1)
        .visible(() -> !airPlace.get())
        .build()
    );

    private final Setting<Double> placeRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("最近方块范围")
        .description("当你在空中时，脚手架可以放置方块的距离。")
        .defaultValue(4)
        .min(0)
        .sliderMax(8)
        .visible(() -> !airPlace.get())
        .build()
    );

    private final Setting<Double> radius = sgGeneral.add(new DoubleSetting.Builder()
        .name("半径")
        .description("脚手架半径。")
        .defaultValue(0)
        .min(0)
        .max(6)
        .visible(airPlace::get)
        .build()
    );

    private final Setting<Integer> blocksPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("每滴答放置的方块数")
        .description("每个滴答放置多少个方块。")
        .defaultValue(3)
        .min(1)
        .visible(airPlace::get)
        .build()
    );

    // Render

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("渲染")
        .description("是否渲染已放置的方块。")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("形状模式")
        .description("形状的渲染方式。")
        .defaultValue(ShapeMode.Both)
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("侧面颜色")
        .description("目标方块渲染的侧面颜色。")
        .defaultValue(new SettingColor(197, 137, 232, 10))
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("线条颜色")
        .description("目标方块渲染的线条颜色。")
        .defaultValue(new SettingColor(197, 137, 232))
        .visible(render::get)
        .build()
    );

    private final BlockPos.Mutable bp = new BlockPos.Mutable();

    public Scaffold() {
        super(Categories.Movement, "脚手架", "自动在你下方放置方块。");
    }


    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (onlyOnClick.get() && !mc.options.useKey.isPressed()) return;

        Vec3d vec = mc.player.getPos().add(mc.player.getVelocity()).add(0, -0.75, 0);
        if (airPlace.get()) {
            bp.set(vec.getX(), vec.getY(), vec.getZ());
        } else {
            Vec3d pos = mc.player.getPos();
            if (aheadDistance.get() != 0 && !towering() && !mc.world.getBlockState(mc.player.getBlockPos().down()).getCollisionShape(mc.world, mc.player.getBlockPos()).isEmpty()) {
                Vec3d dir = Vec3d.fromPolar(0, mc.player.getYaw()).multiply(aheadDistance.get(), 0, aheadDistance.get());
                if (mc.options.forwardKey.isPressed()) pos = pos.add(dir.x, 0, dir.z);
                if (mc.options.backKey.isPressed()) pos = pos.add(-dir.x, 0, -dir.z);
                if (mc.options.leftKey.isPressed()) pos = pos.add(dir.z, 0, -dir.x);
                if (mc.options.rightKey.isPressed()) pos = pos.add(-dir.z, 0, dir.x);
            }
            bp.set(pos.x, vec.y, pos.z);
        }
        if (mc.options.sneakKey.isPressed() && !mc.options.jumpKey.isPressed() && mc.player.getY() + vec.y > -1) {
            bp.setY(bp.getY() - 1);
        }
        if (bp.getY() >= mc.player.getBlockPos().getY()) {
            bp.setY(mc.player.getBlockPos().getY() - 1);
        }
        BlockPos targetBlock = bp.toImmutable();

        if (!airPlace.get() && (BlockUtils.getPlaceSide(bp) == null)) {
            Vec3d pos = mc.player.getPos();
            pos = pos.add(0, -0.98f, 0);
            pos.add(mc.player.getVelocity());

            List<BlockPos> blockPosArray = new ArrayList<>();
            for (int x = (int) (mc.player.getX() - placeRange.get()); x < mc.player.getX() + placeRange.get(); x++) {
                for (int z = (int) (mc.player.getZ() - placeRange.get()); z < mc.player.getZ() + placeRange.get(); z++) {
                    for (int y = (int) Math.max(mc.world.getBottomY(), mc.player.getY() - placeRange.get()); y < Math.min(mc.world.getTopY(), mc.player.getY() + placeRange.get()); y++) {
                        bp.set(x, y, z);
                        if (BlockUtils.getPlaceSide(bp) == null) continue;
                        if (!BlockUtils.canPlace(bp)) continue;
                        if (mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(bp.offset(BlockUtils.getClosestPlaceSide(bp)))) > 36) continue;
                        blockPosArray.add(new BlockPos(bp));
                    }
                }
            }
            if (blockPosArray.isEmpty()) return;

            blockPosArray.sort(Comparator.comparingDouble((blockPos) -> blockPos.getSquaredDistance(targetBlock)));

            bp.set(blockPosArray.getFirst());
        }

        if (airPlace.get()) {
            List<BlockPos> blocks = new ArrayList<>();
            for (int x = (int) (bp.getX() - radius.get()); x <= bp.getX() + radius.get(); x++) {
                for (int z = (int) (bp.getZ() - radius.get()); z <= bp.getZ() + radius.get(); z++) {
                    BlockPos blockPos = BlockPos.ofFloored(x, bp.getY(), z);
                    if (mc.player.getPos().distanceTo(Vec3d.ofCenter(blockPos)) <= radius.get() || (x == bp.getX() && z == bp.getZ())) {
                        blocks.add(blockPos);
                    }
                }
            }

            if (!blocks.isEmpty()) {
                blocks.sort(Comparator.comparingDouble(PlayerUtils::squaredDistanceTo));
                int counter = 0;
                for (BlockPos block : blocks) {
                    if (place(block)) {
                        counter++;
                    }

                    if (counter >= blocksPerTick.get()) {
                        break;
                    }
                }
            }
        } else {
            place(bp);
        }

        FindItemResult result = InvUtils.findInHotbar(itemStack -> validItem(itemStack, bp));
        if (fastTower.get() && mc.options.jumpKey.isPressed() && !mc.options.sneakKey.isPressed() && result.found() && (autoSwitch.get() || result.getHand() != null)) {
            Vec3d velocity = mc.player.getVelocity();
            Box playerBox = mc.player.getBoundingBox();
            if (Streams.stream(mc.world.getBlockCollisions(mc.player, playerBox.offset(0, 1, 0))).toList().isEmpty()) {
                // If there is no block above the player: move the player up, so he can place another block
                if (whileMoving.get() || !PlayerUtils.isMoving()) {
                    velocity = new Vec3d(velocity.x, towerSpeed.get(), velocity.z);
                }
                mc.player.setVelocity(velocity);
            } else {
                // If there is a block above the player: move the player down, so he's on top of the placed block
                mc.player.setVelocity(velocity.x, Math.ceil(mc.player.getY()) - mc.player.getY(), velocity.z);
                mc.player.setOnGround(true);
            }
        }
    }

    public boolean scaffolding() {
        return isActive() && (!onlyOnClick.get() || (onlyOnClick.get() && mc.options.useKey.isPressed()));
    }

    public boolean towering() {
        FindItemResult result = InvUtils.findInHotbar(itemStack -> validItem(itemStack, bp));
        return scaffolding() && fastTower.get() && mc.options.jumpKey.isPressed() && !mc.options.sneakKey.isPressed() &&
            (whileMoving.get() || !PlayerUtils.isMoving()) && result.found() && (autoSwitch.get() || result.getHand() != null);
    }

    private boolean validItem(ItemStack itemStack, BlockPos pos) {
        if (!(itemStack.getItem() instanceof BlockItem)) return false;

        Block block = ((BlockItem) itemStack.getItem()).getBlock();

        if (blocksFilter.get() == ListMode.Blacklist && blocks.get().contains(block)) return false;
        else if (blocksFilter.get() == ListMode.Whitelist && !blocks.get().contains(block)) return false;

        if (!Block.isShapeFullCube(block.getDefaultState().getCollisionShape(mc.world, pos))) return false;
        return !(block instanceof FallingBlock) || !FallingBlock.canFallThrough(mc.world.getBlockState(pos));
    }

    private boolean place(BlockPos bp) {
        FindItemResult item = InvUtils.findInHotbar(itemStack -> validItem(itemStack, bp));
        if (!item.found()) return false;

        if (item.getHand() == null && !autoSwitch.get()) return false;

        if (BlockUtils.place(bp, item, rotate.get(), 50, renderSwing.get(), true)) {
            // Render block if was placed
            if (render.get())
                RenderUtils.renderTickingBlock(bp.toImmutable(), sideColor.get(), lineColor.get(), shapeMode.get(), 0, 8, true, false);
            return true;
        }
        return false;
    }

    public enum ListMode {
        Whitelist,
        Blacklist
    }
}
