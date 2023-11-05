/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.combat;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixin.AbstractBlockAccessor;
import meteordevelopment.meteorclient.mixininterface.IBox;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockIterator;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.meteorclient.utils.world.Dir;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public class HoleFiller extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgSmart = settings.createGroup("Smart");
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<List<Block>> blocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("blocks")
        .description("哪些块可用于填充孔。")
        .defaultValue(
            Blocks.OBSIDIAN,
            Blocks.CRYING_OBSIDIAN,
            Blocks.NETHERITE_BLOCK,
            Blocks.RESPAWN_ANCHOR,
            Blocks.COBWEB
        )
        .build()
    );

    private final Setting<Integer> searchRadius = sgGeneral.add(new IntSetting.Builder()
        .name("search-radius")
        .description("搜索孔的水平半径。")
        .defaultValue(5)
        .min(0)
        .sliderMax(6)
        .build()
    );

    private final Setting<Double> placeRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("place-range")
        .description("您可以在离玩家多远的地方放置方块。")
        .defaultValue(4.5)
        .min(0)
        .sliderMax(6)
        .build()
    );

    private final Setting<Boolean> doubles = sgGeneral.add(new BoolSetting.Builder()
        .name("双打")
        .description("填充双孔。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("旋转")
        .description("自动旋转到正在填充的孔。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> placeDelay = sgGeneral.add(new IntSetting.Builder()
        .name("放置延迟")
        .description("刻度延迟放置之间。")
        .defaultValue(1)
        .min(0)
        .build()
    );

    private final Setting<Integer> blocksPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("每刻度块")
        .description("一次刻度放置多少块。")
        .defaultValue(3)
        .min(1)
        .build()
    );

    // Smart

    private final Setting<Boolean> smart = sgSmart.add(new BoolSetting.Builder()
        .name("智能")
        .description("在填充孔之前考虑更多因素。")
        .defaultValue(true)
        .build()
    );

    public final Setting<Keybind> forceFill = sgSmart.add(new KeybindSetting.Builder()
        .name("强制填充")
        .description("填充无论目标检查如何,你周围的所有洞。")
        .defaultValue(Keybind.none())
        .visible(smart::get)
        .build()
    );

    private final Setting<Boolean> predict = sgSmart.add(new BoolSetting.Builder()
        .name("预测")
        .description("预测目标移动以考虑 ping。")
        .defaultValue(true)
        .visible(smart::get)
        .build()
    );

    private final Setting<Boolean> ignoreSafe = sgSmart.add(new BoolSetting.Builder()
        .name("忽略安全")
        .description("忽略安全洞中的玩家。")
        .defaultValue(true)
        .visible(smart::get)
        .build()
    );

    private final Setting<Boolean> onlyMoving = sgSmart.add(new BoolSetting.Builder()
        .name("仅移动")
        .description("忽略玩家如果他们站着不动。")
        .defaultValue(true)
        .visible(smart::get)
        .build()
    );

    private final Setting<Double> targetRange = sgSmart.add(new DoubleSetting.Builder()
        .name("目标范围")
        .description("到目标球员有多远。")
        .defaultValue(7)
        .min(0)
        .sliderMin(1)
        .sliderMax(10)
        .visible(smart::get)
        .build()
    );

    private final Setting<Double> feetRange = sgSmart.add(new DoubleSetting.Builder()
        .name("英尺范围")
        .description("球员的脚必须离洞多远才能填满它。")
        .defaultValue(1.5)
        .min(0)
        .sliderMax(4)
        .visible(smart::get)
        .build()
    );

    // Render

    private final Setting<Boolean> swing = sgRender.add(new BoolSetting.Builder()
        .name("挥杆")
        .description("放置时挥动玩家的手。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("渲染")
        .description("渲染将放置块的覆盖层。")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("如何渲染形状。")
        .defaultValue(ShapeMode.Both)
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .description("目标块渲染的侧面颜色。")
        .defaultValue(new SettingColor(197, 137, 232, 10))
        .visible(() -> render.get() && shapeMode.get().sides())
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("目标块渲染的线条颜色。")
        .defaultValue(new SettingColor(197, 137, 232))
        .visible(() -> render.get() && shapeMode.get().lines())
        .build()
    );

    private final Setting<SettingColor> nextSideColor = sgRender.add(new ColorSetting.Builder()
        .name("next-side-color")
        .description("下一个要放置的块的侧面颜色。")
        .defaultValue(new SettingColor(227, 196, 245, 10))
        .visible(() -> render.get() && shapeMode.get().sides())
        .build()
    );

    private final Setting<SettingColor> nextLineColor = sgRender.add(new ColorSetting.Builder()
        .name("next-line-color")
        .description("下一个要放置的块的线条颜色。")
        .defaultValue(new SettingColor(227, 196, 245))
        .visible(() -> render.get() && shapeMode.get().lines())
        .build()
    );

    private final List<PlayerEntity> targets = new ArrayList<>();
    private final List<Hole> holes = new ArrayList<>();

    private final BlockPos.Mutable testPos = new BlockPos.Mutable();
    private final Box box = new Box(0, 0, 0, 0, 0, 0);
    private int timer;

    public HoleFiller() {
        super(Categories.Combat, "hole-filler", "用指定的块填充孔。");
    }

    @Override
    public void onActivate() {
        timer = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (smart.get()) setTargets();
        holes.clear();

        FindItemResult block = InvUtils.findInHotbar(itemStack -> blocks.get().contains(Block.getBlockFromItem(itemStack.getItem())));
        if (!block.found()) return;

        BlockIterator.register(searchRadius.get(), searchRadius.get(), (blockPos, blockState) -> {
            if (!validHole(blockPos)) return;

            int bedrock = 0, obsidian = 0;
            Direction air = null;

            for (Direction direction : Direction.values()) {
                if (direction == Direction.UP) continue;

                BlockState state = mc.world.getBlockState(blockPos.offset(direction));

                if (state.getBlock() == Blocks.BEDROCK) bedrock++;
                else if (state.getBlock() == Blocks.OBSIDIAN) obsidian++;
                else if (direction == Direction.DOWN) return;
                else if (validHole(blockPos.offset(direction)) && air == null) {
                    for (Direction dir : Direction.values()) {
                        if (dir == direction.getOpposite() || dir == Direction.UP) continue;

                        BlockState blockState1 = mc.world.getBlockState(blockPos.offset(direction).offset(dir));

                        if (blockState1.getBlock() == Blocks.BEDROCK) bedrock++;
                        else if (blockState1.getBlock() == Blocks.OBSIDIAN) obsidian++;
                        else return;
                    }

                    air = direction;
                }

                if (obsidian + bedrock == 5 && air == null) holes.add(new Hole(blockPos, (byte) 0));
                else if (obsidian + bedrock == 8 && doubles.get() && air != null) {
                    holes.add(new Hole(blockPos, Dir.get(air)));
                }
            }
        });

        BlockIterator.after(() -> {
            if (timer > 0 || holes.isEmpty()) return;

            int bpt = 0;
            for (Hole hole : holes) {
                if (bpt >= blocksPerTick.get()) continue;
                if (BlockUtils.place(hole.blockPos, block, rotate.get(), 10, swing.get(), true)) bpt++;
            }

            timer = placeDelay.get();
        });

        timer--;
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void onRender(Render3DEvent event) {
        if (!render.get() || holes.isEmpty()) return;

        for (Hole hole : holes) {
            boolean isNext = false;
            for (int i = 0; i < holes.size(); i++) {
                if (!holes.get(i).equals(hole)) continue;
                if (i < blocksPerTick.get()) isNext = true;
            }

            Color side = isNext ? nextSideColor.get() : sideColor.get();
            Color line = isNext ? nextLineColor.get() : lineColor.get();

            event.renderer.box(hole.blockPos, side, line, shapeMode.get(), hole.exclude);
        }
    }

    private boolean validHole(BlockPos pos) {
        testPos.set(pos);

        if (mc.player.getBlockPos().equals(testPos)) return false;
        if (distance(mc.player, testPos, false) > placeRange.get()) return false;
        if (mc.world.getBlockState(testPos).getBlock() == Blocks.COBWEB) return false;

        if (((AbstractBlockAccessor) mc.world.getBlockState(testPos).getBlock()).isCollidable()) return false;
        testPos.add(0, 1, 0);
        if (((AbstractBlockAccessor) mc.world.getBlockState(testPos).getBlock()).isCollidable()) return false;
        testPos.add(0, -1, 0);

        ((IBox) box).set(pos);
        if (!mc.world.getOtherEntities(null, box, entity
            -> entity instanceof PlayerEntity
            || entity instanceof TntEntity
            || entity instanceof EndCrystalEntity).isEmpty()) return false;

        if (!smart.get() || forceFill.get().isPressed()) return true;

        return targets.stream().anyMatch(target
            -> target.getY() > testPos.getY()
            && (distance(target, testPos, true) < feetRange.get()));
    }

    private void setTargets() {
        targets.clear();

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player.distanceTo(mc.player) > targetRange.get() ||
                player.isCreative() ||
                player == mc.player ||
                player.isDead() ||
                !Friends.get().shouldAttack(player) ||
                (ignoreSafe.get() && isSurrounded(player)) ||
                (onlyMoving.get() && (player.getX() - player.prevX != 0 || player.getY() - player.prevY != 0 || player.getZ() - player.prevZ != 0))
            ) continue;

            targets.add(player);
        }
    }

    private boolean isSurrounded(PlayerEntity target) {
        for (Direction dir : Direction.values()) {
            if (dir == Direction.UP || dir == Direction.DOWN) continue;

            testPos.set(target.getBlockPos().offset(dir));
            Block block = mc.world.getBlockState(testPos).getBlock();
            if (block != Blocks.OBSIDIAN &&
                block != Blocks.BEDROCK &&
                block != Blocks.RESPAWN_ANCHOR &&
                block != Blocks.CRYING_OBSIDIAN &&
                block != Blocks.NETHERITE_BLOCK) return false;
        }

        return true;
    }

    private double distance(PlayerEntity player, BlockPos pos, boolean feet) {
        Vec3d testVec = player.getPos();
        if (!feet) testVec.add(0, player.getEyeHeight(mc.player.getPose()), 0);

        else if (predict.get()) {
            testVec.add(
                player.getX() - player.prevX,
                player.getY() - player.prevY,
                player.getZ() - player.prevZ
            );
        }

        double i = testVec.x - (pos.getX() + 0.5);
        double j = testVec.y - (pos.getY() + ((feet) ? 1 : 0.5));
        double k = testVec.z - (pos.getZ() + 0.5);

        return Math.sqrt(i * i + j * j + k * k);
    }

    private static class Hole {
        private final BlockPos.Mutable blockPos = new BlockPos.Mutable();
        private final byte exclude;

        public Hole(BlockPos blockPos, byte exclude) {
            this.blockPos.set(blockPos);
            this.exclude = exclude;
        }
    }
}
