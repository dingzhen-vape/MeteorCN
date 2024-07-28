/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.gui.tabs.builtin;

import baritone.api.BaritoneAPI;
import baritone.api.utils.SettingsUtil;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.tabs.Tab;
import meteordevelopment.meteorclient.gui.tabs.TabScreen;
import meteordevelopment.meteorclient.gui.tabs.WindowTabScreen;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.block.Block;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.Item;

import java.awt.*;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BaritoneTab extends Tab {
    private static Settings settings;
    private static Map<String, String> descriptions;

    public BaritoneTab() {
        super("Baritone");
    }

    @SuppressWarnings("unchecked")
    private static Settings getSettings() {
        if (settings != null) return settings;

        settings = new Settings();

        SettingGroup sgBool = settings.createGroup("Checkboxes");
        SettingGroup sgDouble = settings.createGroup("Numbers");
        SettingGroup sgInt = settings.createGroup("Whole Numbers");
        SettingGroup sgString = settings.createGroup("Strings");
        SettingGroup sgColor = settings.createGroup("Colors");

        SettingGroup sgBlockLists = settings.createGroup("Block Lists");
        SettingGroup sgItemLists = settings.createGroup("Item Lists");

        try {
            Class<? extends baritone.api.Settings> klass = BaritoneAPI.getSettings().getClass();
            for (Field field : klass.getDeclaredFields()) {
                Object obj = field.get(BaritoneAPI.getSettings());
                if (!(obj instanceof baritone.api.Settings.Setting setting)) continue;

                Object value = setting.value;

                if (value instanceof Boolean) {
                    sgBool.add(new BoolSetting.Builder()
                        .name(setting.getName())
                        .description(getDescription(setting.getName()))
                        .defaultValue((boolean) setting.defaultValue)
                        .onChanged(aBoolean -> setting.value = aBoolean)
                        .onModuleActivated(booleanSetting -> booleanSetting.set((Boolean) setting.value))
                        .build()
                    );
                } else if (value instanceof Double) {
                    sgDouble.add(new DoubleSetting.Builder()
                        .name(setting.getName())
                        .description(getDescription(setting.getName()))
                        .defaultValue((double) setting.defaultValue)
                        .onChanged(aDouble -> setting.value = aDouble)
                        .onModuleActivated(doubleSetting -> doubleSetting.set((Double) setting.value))
                        .build()
                    );
                } else if (value instanceof Float) {
                    sgDouble.add(new DoubleSetting.Builder()
                        .name(setting.getName())
                        .description(getDescription(setting.getName()))
                        .defaultValue(((Float) setting.defaultValue).doubleValue())
                        .onChanged(aDouble -> setting.value = aDouble.floatValue())
                        .onModuleActivated(doubleSetting -> doubleSetting.set(((Float) setting.value).doubleValue()))
                        .build()
                    );
                } else if (value instanceof Integer) {
                    sgInt.add(new IntSetting.Builder()
                        .name(setting.getName())
                        .description(getDescription(setting.getName()))
                        .defaultValue((int) setting.defaultValue)
                        .onChanged(integer -> setting.value = integer)
                        .onModuleActivated(integerSetting -> integerSetting.set((Integer) setting.value))
                        .build()
                    );
                } else if (value instanceof Long) {
                    sgInt.add(new IntSetting.Builder()
                        .name(setting.getName())
                        .description(getDescription(setting.getName()))
                        .defaultValue(((Long) setting.defaultValue).intValue())
                        .onChanged(integer -> setting.value = integer.longValue())
                        .onModuleActivated(integerSetting -> integerSetting.set(((Long) setting.value).intValue()))
                        .build()
                    );
                } else if (value instanceof String) {
                    sgString.add(new StringSetting.Builder()
                        .name(setting.getName())
                        .description(getDescription(setting.getName()))
                        .defaultValue((String) setting.defaultValue)
                        .onChanged(string -> setting.value = string)
                        .onModuleActivated(stringSetting -> stringSetting.set((String) setting.value))
                        .build()
                    );
                } else if (value instanceof Color) {
                    Color c = (Color) setting.value;

                    sgColor.add(new ColorSetting.Builder()
                        .name(setting.getName())
                        .description(getDescription(setting.getName()))
                        .defaultValue(new SettingColor(c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha()))
                        .onChanged(color -> setting.value = new Color(color.r, color.g, color.b, color.a))
                        .onModuleActivated(colorSetting -> colorSetting.set(new SettingColor(c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha())))
                        .build()
                    );
                } else if (value instanceof List) {
                    Type listType = ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];
                    Type type = ((ParameterizedType) listType).getActualTypeArguments()[0];

                    if (type == Block.class) {
                        sgBlockLists.add(new BlockListSetting.Builder()
                            .name(setting.getName())
                            .description(getDescription(setting.getName()))
                            .defaultValue((List<Block>) setting.defaultValue)
                            .onChanged(blockList -> setting.value = blockList)
                            .onModuleActivated(blockListSetting -> blockListSetting.set((List<Block>) setting.value))
                            .build()
                        );
                    } else if (type == Item.class) {
                        sgItemLists.add(new ItemListSetting.Builder()
                            .name(setting.getName())
                            .description(getDescription(setting.getName()))
                            .defaultValue((List<Item>) setting.defaultValue)
                            .onChanged(itemList -> setting.value = itemList)
                            .onModuleActivated(itemListSetting -> itemListSetting.set((List<Item>) setting.value))
                            .build()
                        );
                    }
                }
            }
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }

        return settings;
    }

    @Override
    public TabScreen createScreen(GuiTheme theme) {
        return new BaritoneScreen(theme, this);
    }

    @Override
    public boolean isScreen(Screen screen) {
        return screen instanceof BaritoneScreen;
    }

    private static void addDescription(String settingName, String description) {
        descriptions.put(settingName.toLowerCase(), description);
    }

    private static String getDescription(String settingName) {
        if (descriptions == null) loadDescriptions();

        return descriptions.get(settingName.toLowerCase());
    }

    private static class BaritoneScreen extends WindowTabScreen {
        public BaritoneScreen(GuiTheme theme, Tab tab) {
            super(theme, tab);

            getSettings().onActivated();
        }

        @Override
        public void initWidgets() {
            WTextBox filter = add(theme.textBox("")).minWidth(400).expandX().widget();
            filter.setFocused(true);
            filter.action = () -> {
                clear();

                add(filter);
                add(theme.settings(getSettings(), filter.get().trim())).expandX();
            };

            add(theme.settings(getSettings(), filter.get().trim())).expandX();
        }

        @Override
        protected void onClosed() {
            SettingsUtil.save(BaritoneAPI.getSettings());
        }
    }

    private static void loadDescriptions() {
        descriptions = new HashMap<>();
        addDescription("acceptableThrowawayItems", "允许Baritone放置的方块（作为抛弃物，用于潜行桥接，柱状结构等）");
        addDescription("allowBreak", "允许Baritone破坏方块");
        addDescription("allowBreakAnyway", "即使allowBreak设置为false，也允许Baritone破坏的方块");
        addDescription("allowDiagonalAscend", "允许对角上升");
        addDescription("allowDiagonalDescend", "允许对角下降");
        addDescription("allowDownward", "允许挖掘其脚下的方块");
        addDescription("allowInventory", "允许Baritone将物品从你的背包移动到快捷栏");
        addDescription("allowJumpAt256", "如果为真，允许在站在最大高度的方块上时进行跳跃，玩家脚的高度为y=256");
        addDescription("allowOnlyExposedOres", "这只允许Baritone挖掘暴露的矿石，可以用来阻止使用反矿透的服务器。");
        addDescription("allowOnlyExposedOresDistance", "启用allowOnlyExposedOres时，这是搜索的距离。");
        addDescription("allowOvershootDiagonalDescend", "在下降后对角行走时是否可以冲刺？ 玩家在着陆时超越，但不足以掉落。");
        addDescription("allowParkour", "你知道是什么");
        addDescription("allowParkourAscend", "这个应该收费，它太好了");
        addDescription("allowParkourPlace", "实际上非常可靠。");
        addDescription("allowPlace", "允许Baritone放置方块");
        addDescription("allowSprint", "允许Baritone冲刺");
        addDescription("allowVines", "启用一些更高级的藤蔓功能。");
        addDescription("allowWalkOnBottomSlab", "台阶行为复杂，禁用此功能以提高路径的可靠性。");
        addDescription("allowWaterBucketFall", "允许Baritone从任意高度掉落，并在其下方放置一个水桶。");
        addDescription("antiCheatCompatibility", "将导致一些轻微的行为差异，以确保Baritone在反作弊系统中工作。");
        addDescription("assumeExternalAutoTool", "在运行时禁用baritone的自动工具，但仍假定其他模组将提供自动工具功能");
        addDescription("assumeSafeWalk", "假定安全行走功能；在后放置遍历时不要潜行。");
        addDescription("assumeStep", "假定步伐功能；在上升时不要跳跃。");
        addDescription("assumeWalkOnLava", "如果你有火焰抗性和Jesus，那么我想你可以开启这个功能，哈哈");
        addDescription("assumeWalkOnWater", "允许Baritone假定它可以像在其他方块上一样在静止的水面上行走。");
        addDescription("autoTool", "自动选择最佳可用工具");
        addDescription("avoidance", "切换以下4个设置");
        addDescription("avoidBreakingMultiplier", "这将倍增破坏速度，如果设置为1以上，则改为“鼓励破坏”");
        addDescription("avoidUpdatingFallingBlocks", "如果此设置为真，Baritone将永远不会破坏邻近不受支撑的下落方块的方块。");
        addDescription("axisHeight", "“轴”命令（即GoalAxis）将在此Y层级到达一个轴或对角轴。");
        addDescription("backfill", "在你身后填充方块（隐形+100）");
        addDescription("backtrackCostFavoringCoefficient", "设置为1.0以有效禁用此功能");
        addDescription("blacklistClosestOnFailure", "当GetToBlockProcess或MineProcess未能计算出路径时，不只是放弃，而是将该方块的最近实例标记为“无法到达”，然后转向下一个最近的实例。");
        addDescription("blockBreakAdditionalPenalty", "这只是一个平衡器，以减少破坏方块的可能性，如果可以避免。");
        addDescription("blockPlacementPenalty", "放置一个方块实际上不需要二十刻的时间，这个成本如此之高是因为我们通常希望节省可能有限的方块。");
        addDescription("blockReachDistance", "方块触及距离");
        addDescription("blocksToAvoid", "Baritone将尝试避免的方块（用于避免）");
        addDescription("blocksToAvoidBreaking", "baritone不应破坏的方块，但如果需要可以破坏。");
        addDescription("blocksToDisallowBreaking", "Baritone不允许破坏的方块");
        addDescription("breakCorrectBlockPenaltyMultiplier", "将破坏建筑商图纸中正确方块的成本乘以这个系数");
        addDescription("breakFromAbove", "允许在BuilderProcess中站在方块上方进行挖掘");
        addDescription("builderTickScanRadius", "每刻扫描的更新距离。");
        addDescription("buildIgnoreBlocks", "将被视为空气的方块列表。");
        addDescription("buildIgnoreDirection", "如果为真，建筑者将忽略某些方块的方向性，如釉面陶瓦。");
        addDescription("buildIgnoreExisting", "如果为真，建筑者将视所有非空气方块为正确。");
        addDescription("buildInLayers", "在当前层完成之前，不考虑下一层");
        addDescription("buildOnlySelection", "只构建选定的图纸部分");
        addDescription("buildRepeat", "在重复建造之前移动的距离。");
        addDescription("buildRepeatCount", "重复建造的次数。");
        addDescription("buildRepeatSneaky", "不要通知图纸它们已被移动。");
        addDescription("buildSkipBlocks", "被视为正确的方块列表。");
        addDescription("buildSubstitutes", "替代构建方块的映射");
        addDescription("buildValidSubstitutes", "在其位置上被视为正确的方块映射。");
        addDescription("cachedChunksExpirySeconds", "缓存的区块（无论它们是否在RAM中或保存到磁盘）在此秒数后过期并删除，-1为禁用");
        addDescription("cachedChunksOpacity", "0.0f = 不可见，完全透明（而不是将此设置为0，关闭renderCachedChunks） 1.0f = 完全不透明");
        addDescription("cancelOnGoalInvalidation", "如果目标已更改，取消当前路径，并且路径最初结束在目标中但不再如此。");
        addDescription("censorCoordinates", "在目标和方块位置中屏蔽坐标");
        addDescription("censorRanCommands", "屏蔽运行命令的参数，以隐藏，例如，#goal的坐标");
        addDescription("chatControl", "允许基于聊天的Baritone控制。");
        addDescription("chatControlAnyway", "一些客户端如Impact尝试强制关闭chatControl，所以这是第二个设置仍然可以控制");
        addDescription("chatDebug", "将所有调试消息打印到聊天");
        addDescription("chunkCaching", "大项。");
        addDescription("colorBestPathSoFar", "到目前为止最佳路径的颜色");
        addDescription("colorBlocksToBreak", "待破坏方块的颜色");
        addDescription("colorBlocksToPlace", "待放置方块的颜色");
        addDescription("colorBlocksToWalkInto", "待行走的方块颜色");
        addDescription("colorCurrentPath", "当前路径的颜色");
        addDescription("colorGoalBox", "目标框的颜色");
        addDescription("colorInvertedGoalBox", "反转时目标框的颜色");
        addDescription("colorMostRecentConsidered", "最近考虑节点路径的颜色");
        addDescription("colorNextPath", "下一个路径的颜色");
        addDescription("colorSelection", "所有选择的颜色");
        addDescription("colorSelectionPos1", "选择位置1的颜色");
        addDescription("colorSelectionPos2", "选择位置2的颜色");
        addDescription("considerPotionEffects", "例如，如果你有挖掘疲劳或急速，根据情况调整破坏方块的成本。");
        addDescription("costHeuristic", "这是大A*设置。");
        addDescription("costVerificationLookahead", "在任何导致路径COST_INF的东西前停止5步。");
        addDescription("cutoffAtLoadBoundary", "计算路径后（可能通过缓存的区块），将其人为截断到当前加载区块内的部分。");
        addDescription("desktopNotifications", "桌面通知");
        addDescription("disableCompletionCheck", "如果你的探索过滤器很大，不希望检查完成情况，并且你只是接受它在完成时挂起，请开启此选项");
        addDescription("disconnectOnArrival", "到达目标后从服务器断开连接");
        addDescription("distanceTrim", "修剪太远的不正确位置，有助于性能，但在非常大的图纸中会降低可靠性");
        addDescription("doBedWaypoints", "允许Baritone在与床交互时保存床路线点");
        addDescription("doDeathWaypoints", "允许Baritone保存死亡路线点");
        addDescription("echoCommands", "运行命令时将命令回显到聊天");
        addDescription("enterPortal", "当跑向下界传送门块时，走到传送门内而不是在前一块停止。");
        addDescription("exploreChunkSetMinimumSize", "即使它们在距离度量上与起点不严格绑定，也选择10个最近的区块。");
        addDescription("exploreForBlocks", "当GetToBlock或非法挖掘不知道所需方块的位置时，随机探索而不是放弃。");
        addDescription("exploreMaintainY", "尝试在探索时保持Y坐标");
        addDescription("extendCacheOnThreshold", "当缓存扫描的块少于最大阈值（但仍高于零）时，也扫描主世界。");
        addDescription("fadePath", "在前方20步开始淡化路径，在前方30步完全停止渲染。");
        addDescription("failureTimeoutMS", "路径规划永远不会超过这个时间，即使这意味着完全找不到路径");
        addDescription("followOffsetDirection", "实际的GoalNear设置在你跟随的实体的这个方向上。");
        addDescription("followOffsetDistance", "实际的GoalNear设置在你跟随的实体的这个距离之外");
        addDescription("followRadius", "GoalNear的半径，表示你实际到目标位置的接近程度");
        addDescription("forceInternalMining", "在挖掘某种类型的块时，尝试同时挖掘两个而不是一个。");
        addDescription("freeLook", "移动而不强制客户端侧的旋转");
        addDescription("goalBreakFromAbove", "不仅从上方破坏，还设置目标为所有待破坏块的上方和侧面。");
        addDescription("goalRenderLineWidthPixels", "渲染时目标的线宽，以像素为单位");
        addDescription("incorrectSize", "不正确块的集合永远不会超过这个大小");
        addDescription("internalMiningAirException", "对之前设置的修改，仅在forceInternalMining为true时有效 如果为true，仅在目标相邻的块不是空气时应用之前的设置。");
        addDescription("itemSaver", "在工具即将破坏时停止使用工具。");
        addDescription("itemSaverThreshold", "使用itemSaver时工具的耐久度");
        addDescription("jumpPenalty", "按下空格键（上升、柱状或跑酷）的额外惩罚，因为它消耗饥饿");
        addDescription("layerHeight", "个别层的高度是多少？");
        addDescription("layerOrder", "false = 从下往上建造");
        addDescription("legitMine", "不允许MineBehavior使用X光来查看矿石位置。");
        addDescription("legitMineIncludeDiagonals", "魔法般地看到与现有矿石斜对角分离的矿石。");
        addDescription("legitMineYLevel", "合法条带挖掘的Y层级");
        addDescription("logAsToast", "在右上角显示弹出消息，类似于获得进展时的情况");
        addDescription("mapArtMode", "在地图艺术模式下建造，使baritone只关心每列中的顶部块");
        addDescription("maxCachedWorldScanCount", "在缓存中找到这么多目标块实例后，将停止向外扩展区块搜索。");
        addDescription("maxCostIncrease", "如果移动的成本在计算和执行之间增加超过此数值（由于环境/世界的变化），取消并重新计算");
        addDescription("maxFallHeightBucket", "你可以掉落到实地的最大高度（使用水桶）是多少？它不是那么可靠，所以我将其设置为不会杀死无甲玩家的水平（23）");
        addDescription("maxFallHeightNoWater", "你可以掉落到实地的最大高度（没有水桶）是多少？3不会造成任何伤害。");
        addDescription("maxPathHistoryLength", "如果当前路径已经超过300步，丢弃最旧的段，因为它们不再有用");
        addDescription("mineDropLoiterDurationMSThanksLouca", "在挖矿时，在挖矿后等待此时间（以毫秒为单位），看看是否会掉落物品，而不是立即进入下一个");
        addDescription("mineGoalUpdateInterval", "每5刻重新扫描目标。");
        addDescription("mineScanDroppedItems", "在挖矿时，它是否还会将正确类型的掉落物品视为路径目的地（以及矿石块）？");
        addDescription("minimumImprovementRepropagation", "不要重新传播低于0.01刻的成本改进。");
        addDescription("minYLevelWhileMining", "设置挖矿时的最小y层级 - 设置为0以关闭。如果世界有负的y值，请减去最小的世界高度以获得这里要输入的值");
        addDescription("mobAvoidanceCoefficient", "设置为1.0以有效禁用此功能");
        addDescription("mobAvoidanceRadius", "避免怪物的距离。");
        addDescription("mobSpawnerAvoidanceCoefficient", "设置为1.0以有效禁用此功能");
        addDescription("mobSpawnerAvoidanceRadius", "避免刷怪笼的距离。");
        addDescription("movementTimeoutTicks", "如果移动所需的时间比其初始成本估计多了这么多刻，则取消");
        addDescription("notificationOnBuildFinished", "建造完成时的桌面通知");
        addDescription("notificationOnExploreFinished", "探索完成时的桌面通知");
        addDescription("notificationOnFarmFail", "农场失败时的桌面通知");
        addDescription("notificationOnMineFail", "挖矿失败时的桌面通知");
        addDescription("notificationOnPathComplete", "路径完成时的桌面通知");
        addDescription("notifier", "当Baritone发送桌面通知时调用的函数。");
        addDescription("okIfAir", "允许变为空气的块的列表");
        addDescription("okIfWater", "覆盖建筑者的行为，不尝试修正当前是水的块");
        addDescription("overshootTraverse", "如果我们超过了穿越并最终超出目的地一块，将其标记为成功。");
        addDescription("pathCutoffFactor", "静态切断因子（这什么）。");
        addDescription("pathCutoffMinimumLength", "仅对至少有此长度（以移动次数计算）的路径应用静态截断");
        addDescription("pathHistoryCutoffAmount", "如果当前路径太长，从开头截断这么多移动。");
        addDescription("pathingMapDefaultSize", "路径规划中使用的Long2ObjectOpenHashMap的默认大小");
        addDescription("pathingMapLoadFactor", "路径规划中使用的Long2ObjectOpenHashMap的装载系数");
        addDescription("pathingMaxChunkBorderFetch", "在假设路径规划已到达已知区域的尽头并应停止之前，最大抓取次数为加载或缓存区块外部。");
        addDescription("pathRenderLineWidthPixels", "渲染路径时的线条宽度，以像素为单位");
        addDescription("pathThroughCachedOnly", "仅使用缓存的区块进行路径规划");
        addDescription("pauseMiningForFallingBlocks", "在进行移动的过程中破坏方块时，等待所有掉落方块稳定后继续");
        addDescription("planAheadFailureTimeoutMS", "在执行段期间提前规划的时间不得超过此时间，即使这意味着无法找到任何路径");
        addDescription("planAheadPrimaryTimeoutMS", "在执行段期间提前规划的时间结束，但只有在找到路径的情况下");
        addDescription("planningTickLookahead", "当剩余的移动时间估算总和小于此值时开始规划下一个路径");
        addDescription("preferSilkTouch", "总是优先使用丝绸之触工具而不是普通工具。");
        addDescription("prefix", "聊天控制的命令前缀");
        addDescription("prefixControl", "是否允许使用前缀运行Baritone命令");
        addDescription("primaryTimeoutMS", "路径规划在此时间后结束，但只有在找到路径的情况下");
        addDescription("pruneRegionsFromRAM", "保存时，从内存中删除距离玩家超过1024块的缓存区域");
        addDescription("randomLooking", "每个刻度随机化俯仰角和偏航角的度数。");
        addDescription("randomLooking113", "每个刻度随机化偏航角的度数。设置为0以禁用");
        addDescription("renderCachedChunks", "将缓存的区块渲染为半透明。");
        addDescription("renderGoal", "渲染目标");
        addDescription("renderGoalAnimated", "将目标渲染为酷炫的动画效果，而不仅仅是一个方块（如果启用了renderGoalXZBeacon，也控制GoalXZ的动画）");
        addDescription("renderGoalIgnoreDepth", "渲染目标时忽略深度");
        addDescription("renderGoalXZBeacon", "使用原版信标束效果渲染X/Z类型的目标。");
        addDescription("renderPath", "渲染路径");
        addDescription("renderPathAsLine", "将路径渲染为线条而不是奇怪的东西");
        addDescription("renderPathIgnoreDepth", "渲染路径时忽略深度");
        addDescription("renderSelection", "渲染选择");
        addDescription("renderSelectionBoxes", "渲染选择框");
        addDescription("renderSelectionBoxesIgnoreDepth", "渲染选择框时忽略深度（要破坏，要放置，要进入的方块）");
        addDescription("renderSelectionCorners", "渲染选择的角");
        addDescription("renderSelectionIgnoreDepth", "渲染选择时忽略深度");
        addDescription("repackOnAnyBlockChange", "每当方块变化时，重新打包所在的整个区块");
        addDescription("replantCrops", "耕作时重新种植正常的作物，仙人掌和甘蔗则留待重新生长");
        addDescription("replantNetherWart", "耕作时重新种植地狱疣。");
        addDescription("rightClickContainerOnArrival", "当进行到容器块（箱子，末影箱，熔炉等）的goto时，到达后右键点击并打开它。");
        addDescription("rightClickSpeed", "允许的右键点击之间的刻数。");
        addDescription("schematicFallbackExtension", "在未指定扩展名时，build命令使用的备用扩展名。");
        addDescription("schematicOrientationX", "当此设置为真时，构建以最高X坐标为原点的结构，而不是最低的");
        addDescription("schematicOrientationY", "当此设置为真时，构建以最高Y坐标为原点的结构，而不是最低的");
        addDescription("schematicOrientationZ", "当此设置为真时，构建以最高Z坐标为原点的结构，而不是最低的");
        addDescription("selectionLineWidth", "渲染时的选择线宽，以像素为单位");
        addDescription("selectionOpacity", "选择的透明度。");
        addDescription("shortBaritonePrefix", "使用简短的Baritone前缀[B]而不是[Baritone]记录到聊天");
        addDescription("simplifyUnloadedYCoord", "如果目标是未加载区块中的GoalBlock，假设它足够远，Y坐标不重要，并在计算路径前将其替换为同一位置的GoalXZ。");
        addDescription("skipFailedLayers", "如果一层无法构建，则跳过它。");
        addDescription("slowPath", "用于调试，考虑节点非常非常慢");
        addDescription("slowPathTimeDelayMS", "每个节点之间的毫秒数");
        addDescription("slowPathTimeoutMS", "当启用slowPath时的备用超时时间");
        addDescription("splicePath", "当计算出一个新段落，它不与当前段落重叠，但只是从当前段落结束的地方开始时，将其拼接并生成一个更长的组合路径。");
        addDescription("sprintAscends", "尽可能在上升时提前一个方块冲刺和跳跃");
        addDescription("sprintInWater", "在水中继续冲刺");
        addDescription("startAtLayer", "在特定层开始构建示意图。");
        addDescription("toaster", "当Baritone显示提示时调用的函数。");
        addDescription("toastTimer", "弹出窗口中的消息显示时间");
        addDescription("useSwordToMine", "使用剑进行挖掘。");
        addDescription("verboseCommandExceptions", "将所有命令异常作为堆栈跟踪输出到stdout，即使是简单的语法错误");
        addDescription("walkOnWaterOnePenalty", "在水上行走消耗饥饿值非常快，因此要受到惩罚");
        addDescription("walkWhileBreaking", "需要破坏前方的方块时不要停止前进");
        addDescription("worldExploringChunkOffset", "在探索世界时，在两个轴上偏移最近的未加载区块这么多。");
        addDescription("yLevelBoxSize", "当前目标为GoalYLevel时渲染的方框大小");
    }
}
