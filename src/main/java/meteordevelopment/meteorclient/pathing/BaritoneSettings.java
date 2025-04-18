/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.pathing;

import baritone.api.BaritoneAPI;
import baritone.api.utils.SettingsUtil;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.block.Block;
import net.minecraft.item.Item;

import java.awt.*;
import java.lang.reflect.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BaritoneSettings implements IPathManager.ISettings {
    private final Settings settings = new Settings();

    private Setting<Boolean> walkOnWater, walkOnLava;
    private Setting<Boolean> step, noFall;

    private static final Map<String, Double> SETTING_MAX_VALUES = new HashMap<>();

    public BaritoneSettings() {
        createWrappers();
    }

    @Override
    public Settings get() {
        return settings;
    }

    @Override
    public Setting<Boolean> getWalkOnWater() {
        return walkOnWater;
    }

    @Override
    public Setting<Boolean> getWalkOnLava() {
        return walkOnLava;
    }

    @Override
    public Setting<Boolean> getStep() {
        return step;
    }

    @Override
    public Setting<Boolean> getNoFall() {
        return noFall;
    }

    @Override
    public void save() {
        SettingsUtil.save(BaritoneAPI.getSettings());
    }

    static {
        SETTING_MAX_VALUES.put("路径截断因子", 1.0);
    }

    // Wrappers

    @SuppressWarnings({"原始类型", "unchecked"})
    private void createWrappers() {
        SettingGroup sgBool = settings.createGroup("复选框");
        SettingGroup sgDouble = settings.createGroup("数字");
        SettingGroup sgInt = settings.createGroup("整数");
        SettingGroup sgString = settings.createGroup("字符串");
        SettingGroup sgColor = settings.createGroup("颜色");

        SettingGroup sgBlockLists = settings.createGroup("方块列表");
        SettingGroup sgItemLists = settings.createGroup("物品列表");

        try {
            Class<? extends baritone.api.Settings> klass = BaritoneAPI.getSettings().getClass();

            for (Field field : klass.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;

                Object obj = field.get(BaritoneAPI.getSettings());
                if (!(obj instanceof baritone.api.Settings.Setting setting)) continue;

                Object value = setting.value;

                if (value instanceof Boolean) {
                    Setting<Boolean> wrapper = sgBool.add(new BoolSetting.Builder()
                        .name(setting.getName())
                        .description(getDescription(setting.getName()))
                        .defaultValue((boolean) setting.defaultValue)
                        .onChanged(aBoolean -> setting.value = aBoolean)
                        .onModuleActivated(booleanSetting -> booleanSetting.set((Boolean) setting.value))
                        .build()
                    );

                    switch (wrapper.name) {
                        case "假设走水面" -> walkOnWater = wrapper;
                        case "假设走岩浆" -> walkOnLava = wrapper;
                        case "假设踏步" -> step = wrapper;
                    }
                }
                else if (value instanceof Double) {
                    sgDouble.add(new DoubleSetting.Builder()
                        .name(setting.getName())
                        .description(getDescription(setting.getName()))
                        .defaultValue((double) setting.defaultValue)
                        .max(SETTING_MAX_VALUES.getOrDefault(setting.getName(), 10.0))
                        .sliderMax(SETTING_MAX_VALUES.getOrDefault(setting.getName(), 10.0))
                        .onChanged(aDouble -> setting.value = aDouble)
                        .onModuleActivated(doubleSetting -> doubleSetting.set((Double) setting.value))
                        .build()
                    );
                }
                else if (value instanceof Float) {
                    sgDouble.add(new DoubleSetting.Builder()
                        .name(setting.getName())
                        .description(getDescription(setting.getName()))
                        .defaultValue(((Float) setting.defaultValue).doubleValue())
                        .max(SETTING_MAX_VALUES.getOrDefault(setting.getName(), 10.0))
                        .sliderMax(SETTING_MAX_VALUES.getOrDefault(setting.getName(), 10.0))
                        .onChanged(aDouble -> setting.value = aDouble.floatValue())
                        .onModuleActivated(doubleSetting -> doubleSetting.set(((Float) setting.value).doubleValue()))
                        .build()
                    );
                }
                else if (value instanceof Integer) {
                    Setting<Integer> wrapper = sgInt.add(new IntSetting.Builder()
                        .name(setting.getName())
                        .description(getDescription(setting.getName()))
                        .defaultValue((int) setting.defaultValue)
                        .onChanged(integer -> setting.value = integer)
                        .onModuleActivated(integerSetting -> integerSetting.set((Integer) setting.value))
                        .build()
                    );

                    if (wrapper.name.equals("最大跌落高度（无水）")) {
                        noFall = new BoolSetting.Builder()
                            .name(wrapper.name)
                            .description(wrapper.description)
                            .defaultValue(false)
                            .onChanged(aBoolean -> wrapper.set(aBoolean ? 159159 : wrapper.getDefaultValue()))
                            .onModuleActivated(booleanSetting -> booleanSetting.set(wrapper.get() >= 255))
                            .build();
                    }
                }
                else if (value instanceof Long) {
                    sgInt.add(new IntSetting.Builder()
                        .name(setting.getName())
                        .description(getDescription(setting.getName()))
                        .defaultValue(((Long) setting.defaultValue).intValue())
                        .onChanged(integer -> setting.value = integer.longValue())
                        .onModuleActivated(integerSetting -> integerSetting.set(((Long) setting.value).intValue()))
                        .build()
                    );
                }
                else if (value instanceof String) {
                    sgString.add(new StringSetting.Builder()
                        .name(setting.getName())
                        .description(getDescription(setting.getName()))
                        .defaultValue((String) setting.defaultValue)
                        .onChanged(string -> setting.value = string)
                        .onModuleActivated(stringSetting -> stringSetting.set((String) setting.value))
                        .build()
                    );
                }
                else if (value instanceof Color) {
                    Color c = (Color) setting.value;

                    sgColor.add(new ColorSetting.Builder()
                        .name(setting.getName())
                        .description(getDescription(setting.getName()))
                        .defaultValue(new SettingColor(c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha()))
                        .onChanged(color -> setting.value = new Color(color.r, color.g, color.b, color.a))
                        .onModuleActivated(colorSetting -> colorSetting.set(new SettingColor(c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha())))
                        .build()
                    );
                }
                else if (value instanceof List) {
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
                    }
                    else if (type == Item.class) {
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
        }
        catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    // Descriptions

    private static Map<String, String> descriptions;

    private static void addDescription(String settingName, String description) {
        descriptions.put(settingName.toLowerCase(), description);
    }

    private static String getDescription(String settingName) {
        if (descriptions == null) loadDescriptions();

        return descriptions.get(settingName.toLowerCase());
    }

    private static void loadDescriptions() {
        descriptions = new HashMap<>();
        addDescription("可接受的可丢弃物品", "Baritone 允许放置的方块（作为丢弃、隐秘搭桥、支柱等）");
        addDescription("允许破坏", "允许 Baritone 破坏方块");
        addDescription("无论如何允许破坏", "即使允许破坏设置为 false,Baritone 仍允许破坏的方块");
        addDescription("允许对角线升高","允许对角线升高");
        addDescription("允许对角线下降", "允许对角线下降");
        addDescription("允许向下挖掘", "允许挖掘直接在脚下的方块");
        addDescription("允许使用背包", "允许 Baritone 将物品从背包移到热键栏");
        addDescription("允许在 y=256 处跳跃", "如果为 true,公园游乐允许在站立在最大高度的方块上时跳跃,因此玩家脚部为 y=256");
        addDescription("仅允许挖掘暴露的矿石", "这将仅允许 Baritone 挖掘暴露的矿石,可以用于停止在使用矿石混淆器的服务器上。");
        addDescription("允许暴露矿石搜索范围", "启用 allowOnlyExposedOres 时,这是搜索范围的距离。");
        addDescription("允许超越对角线下降","可以通过下降后再进行对角线跳跃吗？玩家会超过着陆点,但不足以摔下来。");
        addDescription("允许公园游乐", "你知道这是什么");
        addDescription("允许公园游乐升高", "这应该赚钱,这太棒了");
        addDescription("允许公园游乐放置", "实际上相当可靠。");
        addDescription("允许放置", "允许 Baritone 放置方块");
        addDescription("允许冲刺", "允许 Baritone 冲刺");
        addDescription("允许藤蔓", "启用一些更高级的藤蔓功能。");
        addDescription("允许走在下方台阶上", "台阶行为复杂,禁用此功能以提高路径可靠性。");
        addDescription("允许水桶掉落", "允许 Baritone 跌落任意距离,并在下方放置水桶。");
        addDescription("防作弊兼容性", "将导致一些小的行为差异,以确保 Baritone 在防作弊系统下正常工作。");
        addDescription("假设外部自动工具", "在运行时禁用 Baritone 的自动工具,但仍假设另一个模组提供自动工具功能。");
        addDescription("假设安全行走", "假设安全行走功能；在后退时不要蹲下。");
        addDescription("假设踏步", "假设阶梯功能；在上升时不要跳跃。");
        addDescription("假设走岩浆", "如果你有防火抗性和耶稣技能,那我猜你可以开启这个功能,哈哈。");
        addDescription("假设走水面", "允许 Baritone 假设它可以像其他方块一样行走在静水上。");
        addDescription("自动工具", "自动选择最佳可用工具");
        addDescription("避免", "切换以下四个设置");
        addDescription("避免破坏乘数", "这会乘以破坏速度,如果设置为 1 以上则是 \"encourage breaking\" 而不是");
        addDescription("避免更新掉落方块", "如果此设置为 true,Baritone 将永远不会破坏与不支持的掉落方块相邻的方块。");
        addDescription("轴高度", "该 \"axis\" 命令（也叫 GoalAxis）将在此 Y 坐标上前往某个轴或对角轴。");
        addDescription("填充", "填充你身后的方块（隐秘 +100）");
        addDescription("回溯成本偏向系数", "设置为 1.0 以有效禁用此功能");
        addDescription("失败时黑名单最近的", "当 GetToBlockProcess 或 MineProcess 计算路径失败时,不仅仅放弃,而是将该方块的最近实例标记为 \"unreachable\" 并前往下一个最近的实例。");
        addDescription("破坏方块附加惩罚", "这只是一个决胜因素,使得在可以避免的情况下,尽量不破坏方块。");
        addDescription("放置方块惩罚", "放置一个方块实际上并不需要二十个刻,但这个惩罚这么高是因为我们想要尽量节省可能有限的方块。");
        addDescription("方块触及距离", "方块触及距离");
        addDescription("避免的方块", "Baritone 将尝试避免的方块（用于避免设置）");
        addDescription("避免破坏的方块", "Baritone 不应破坏的方块,但如果需要可以破坏。");
        addDescription("禁止破坏的方块", "Baritone 不允许破坏的方块");
        addDescription("破坏正确方块惩罚系数", "将破坏构建图中正确方块的成本乘以此系数");
        addDescription("从上方破坏", "允许在 BuilderProcess 中站在方块上方并挖掘它");
        addDescription("构建者扫描半径", "每个刻扫描更新的距离。");
        addDescription("忽略构建方块", "一列方块,将其视为空气。");
        addDescription("忽略构建方向", "如果为 true,构建者将忽略某些方块的方向性,如釉面陶瓦。");
        addDescription("忽略已存在方块", "如果为 true,构建者将把所有非空气方块视为正确。");
        addDescription("分层构建", "在当前层完成之前,不考虑下一层。");
        addDescription("仅构建选择部分", "仅构建选定的图纸部分");
        addDescription("重复构建", "在重复构建之前要移动多远。");
        addDescription("重复构建次数", "重复构建多少次。");
        addDescription("偷偷重复构建", "不要通知图纸它们被移动了。");
        addDescription("跳过构建方块", "一列方块,将其视为正确。");
        addDescription("构建替代品", "一组方块到替代建造方块的映射");
        addDescription("有效构建替代品", "一组方块到在其位置视为正确的方块的映射");
        addDescription("缓存块过期时间（秒）", "缓存块（无论是否在 RAM 或保存到磁盘）将在此秒数后过期并被删除,-1 为禁用");
        addDescription("缓存块透明度", "0.0f = 不可见,完全透明（与其设置为 0,不如关闭 renderCachedChunks） 1.0f = 完全不透明");
        addDescription("目标无效时取消", "如果目标已更改,并且路径原本结束在目标处但现在不再在目标处,取消当前路径。");
        addDescription("屏蔽坐标", "屏蔽目标和方块位置中的坐标");
        addDescription("屏蔽执行的命令", "屏蔽已执行命令的参数,例如隐藏 #goal 的坐标");
        addDescription("聊天控制", "允许通过聊天控制 Baritone。");
        addDescription("仍然启用聊天控制", "一些客户端如 Impact 尝试强制关闭 chatControl,所以这里有一个第二个设置来强制启用。");
        addDescription("聊天调试", "将所有调试信息打印到聊天中");
        addDescription("区块缓存", "大事。");
        addDescription("给当前最佳路径上色", "目前最佳路径的颜色");
        addDescription("colorBlocksToBreak", "待破坏方块的颜色");
        addDescription("colorBlocksToPlace", "待放置方块的颜色");
        addDescription("colorBlocksToWalkInto", "待走入方块的颜色");
        addDescription("colorCurrentPath", "当前路径的颜色");
        addDescription("colorGoalBox", "目标框的颜色");
        addDescription("colorInvertedGoalBox", "目标框反转时的颜色");
        addDescription("colorMostRecentConsidered", "到最近考虑的节点的路径颜色");
        addDescription("colorNextPath", "下一条路径的颜色");
        addDescription("colorSelection", "所有选择的颜色");
        addDescription("colorSelectionPos1", "选择位置1的颜色");
        addDescription("colorSelectionPos2", "选择位置2的颜色");
        addDescription("considerPotionEffects", "例如,如果你有矿工疲劳或急迫效果,调整破坏方块的成本。");
        addDescription("costHeuristic", "这是重要的A*设置。");
        addDescription("costVerificationLookahead", "在任何导致路径COST_INF的情况之前停止5步。");
        addDescription("cutoffAtLoadBoundary", "计算路径后（可能通过缓存的区块）,将其人工截断为仅包含当前加载区块的部分。");
        addDescription("desktopNotifications", "桌面通知");
        addDescription("disableCompletionCheck", "如果你的探索过滤器非常大,不希望检查是否完成,并且不介意它挂在完成时,请打开此选项。");
        addDescription("disconnectOnArrival", "到达目标时从服务器断开连接");
        addDescription("distanceTrim", "修剪距离过远的不正确位置,有助于性能,但在非常大的蓝图中可能影响可靠性。");
        addDescription("doBedWaypoints", "允许Baritone在与床互动时保存床标记点。");
        addDescription("doDeathWaypoints", "允许Baritone保存死亡标记点。");
        addDescription("echoCommands", "执行命令时将其回显到聊天中。");
        addDescription("enterPortal", "当运行前往下界传送门的goto时,走完全程进入传送门,而不是停在前一个方块前。");
        addDescription("exploreChunkSetMinimumSize", "即使这些区块在距离度量上不完全相同,也取10个最接近的区块。");
        addDescription("exploreForBlocks", "当GetToBlock或非合法的Mine不知道所需方块的位置时,随机探索而不是放弃。");
        addDescription("exploreMaintainY", "在探索时尽量保持Y坐标。");
        addDescription("extendCacheOnThreshold", "当缓存扫描给出的方块数低于最大阈值（但仍大于零）时,也扫描主世界。");
        addDescription("fadePath", "从前方20步开始渐隐路径,并在30步前完全停止渲染路径。");
        addDescription("failureTimeoutMS", "路径计算永远不会超过此时间,即使这意味着无法找到任何路径。");
        addDescription("followOffsetDirection", "实际的GoalNear设置为你正在跟随的实体的这个方向。");
        addDescription("followOffsetDistance", "实际的GoalNear设置为距离你正在跟随的实体此距离。");
        addDescription("followRadius", "（GoalNear的）你实际需要接近目标位置的半径。");
        addDescription("forceInternalMining", "在开采某种类型的方块时,尽量一次性开采两个而不是一个。");
        addDescription("freeLook", "无需强制客户端旋转即可移动。");
        addDescription("goalBreakFromAbove", "除了从上方破坏外,还设定一个目标,朝上和朝旁边破坏所有方块。");
        addDescription("goalRenderLineWidthPixels", "渲染时目标线的宽度（以像素为单位）。");
        addDescription("incorrectSize", "不正确方块的集合永远不会超出此大小。");
        addDescription("internalMiningAirException", "对之前设置的修改,只有在forceInternalMining为真时有效。如果为真,只有当目标相邻的方块不是空气时才应用之前的设置。");
        addDescription("itemSaver", "在工具即将破损前停止使用。");
        addDescription("itemSaverThreshold", "使用itemSaver时,工具上剩余的耐久度。");
        addDescription("jumpPenalty", "因为跳跃（攀升、支柱或跑酷）会消耗饥饿,所以额外增加的惩罚。");
        addDescription("layerHeight", "单独层的高度应是多少？");
        addDescription("layerOrder", "false = 从下到上建造。");
        addDescription("legitMine", "禁止MineBehavior使用X射线查看矿物的位置。");
        addDescription("legitMineIncludeDiagonals", "神奇地看到与现有矿物对角线分离的矿物。");
        addDescription("legitMineYLevel", "合法带挖矿时去的Y轴层级。");
        addDescription("logAsToast", "在右上角显示弹出消息,类似于你获得进展时的提示。");
        addDescription("mapArtMode", "在地图艺术模式下构建,这使得Baritone只关注每一列的顶部方块。");
        addDescription("maxCachedWorldScanCount", "在缓存中找到如此多的目标方块后,它将停止扩展区块搜索。");
        addDescription("maxCostIncrease", "如果某个动作的成本在计算和执行之间增加了超过此值（由于环境/世界的变化）,则取消并重新计算。");
        addDescription("最大跌落高度桶", "你允许从多高的地方掉落到坚固的地面（使用水桶）？这并不那么可靠,所以我把它设定在低于会杀死一个未穿盔甲玩家的高度（23）");
        addDescription("最大跌落高度（无水）", "你允许从多高的地方掉落到坚固的地面（没有水桶）？3不会造成任何伤害。");
        addDescription("最大路径历史长度", "如果我们已经执行超过300步,丢弃最旧的路径段,因为它们不再有用");
        addDescription("采矿掉落停留时长（毫秒）感谢Louca", "在挖矿时,挖掘矿石后等待该毫秒数,以查看是否掉落物品,而不是立即进入下一个");
        addDescription("采矿目标更新间隔", "每5个tick重新扫描目标。");
        addDescription("采矿扫描掉落物品", "在挖矿时,它是否应该将正确类型的掉落物品（与矿石块一样）也视为路径目的地？");
        addDescription("最小改进重新传播", "不重新传播低于0.01tick的成本改进。");
        addDescription("采矿时的最小y轴高度", "设置采矿时的最小y轴高度 - 设置为0可关闭。如果世界有负y值,减去最小世界高度得到这里要填写的值。");
        addDescription("怪物回避系数", "设置为 1.0 以有效禁用此功能");
        addDescription("怪物回避半径", "避免怪物的距离。");
        addDescription("怪物生成器回避系数", "设置为 1.0 以有效禁用此功能");
        addDescription("怪物生成器回避半径", "避免怪物生成器的距离。");
        addDescription("移动超时tick", "如果一次移动超过初始成本估算的tick数,则取消该移动");
        addDescription("建造完成时的通知", "建造完成时的桌面通知");
        addDescription("探索完成时的通知", "探索完成时的桌面通知");
        addDescription("农场失败时的通知", "农场失败时的桌面通知");
        addDescription("采矿失败时的通知", "采矿失败时的桌面通知");
        addDescription("路径完成时的通知", "路径完成时的桌面通知");
        addDescription("通知器", "当Baritone发送桌面通知时调用的函数。");
        addDescription("如果是空气", "将变为空气的方块列表");
        addDescription("如果是水", "覆盖建造者行为,避免纠正当前为水的方块");
        addDescription("超越遍历", "如果我们超越了遍历并最终超过目标一个方块,将其标记为成功。");
        addDescription("路径截断因子", "静态截止因子。");
        addDescription("路径截止最小长度", "仅对至少有此长度的路径应用静态截止（按移动次数计）");
        addDescription("路径历史截止数量", "如果当前路径太长,从开始切掉这许多步。");
        addDescription("路径地图默认大小", "路径中使用的Long2ObjectOpenHashMap的默认大小");
        addDescription("路径地图负载因子", "路径中使用的Long2ObjectOpenHashMap的负载因子系数");
        addDescription("路径最大区块边界获取", "它在假设路径已到达已知区域的尽头之前,将从加载或缓存的区块外部获取的最大次数。");
        addDescription("路径渲染线宽（像素）", "渲染时路径的线宽,以像素为单位");
        addDescription("仅通过缓存路径", "仅使用缓存的区块进行路径计算");
        addDescription("暂停采矿以应对掉落方块", "当为了移动破坏方块时,等到所有掉落的方块都已经落定后再继续");
        addDescription("提前计划失败超时（毫秒）", "在执行某段时提前规划不能超过此时间,即使这意味着无法找到任何路径");
        addDescription("提前计划主超时（毫秒）", "执行某段时提前规划会在此时间后结束,但前提是已经找到路径");
        addDescription("规划tick预见", "一旦剩余的移动tick估计总和低于此值,就开始规划下一条路径");
        addDescription("偏好丝触", "始终偏好丝触工具而非普通工具。");
        addDescription("前缀", "聊天控制的命令前缀");
        addDescription("前缀控制", "是否允许你通过前缀运行Baritone命令");
        addDescription("主超时（毫秒）", "路径计算在此时间后结束,但前提是已找到路径");
        addDescription("从RAM中修剪区域", "保存时,删除RAM中距离玩家超过1024个区块的任何缓存区域");
        addDescription("随机观察", "每个tick随机化俯仰角和偏航角的度数。");
        addDescription("随机观察113", "每个tick随机化偏航角的度数。设置为0以禁用");
        addDescription("渲染缓存区块", "将缓存区块渲染为半透明。");
        addDescription("渲染目标", "渲染目标");
        addDescription("渲染目标动画", "将目标渲染为一个炫酷的动画效果,而不仅仅是一个盒子（如果启用了renderGoalXZBeacon,还控制GoalXZ的动画）");
        addDescription("忽略深度渲染目标", "渲染目标时忽略深度");
        addDescription("渲染GoalXZ信标", "使用原版信标光束效果渲染X/Z类型的目标。");
        addDescription("渲染路径", "渲染路径");
        addDescription("将路径渲染为线", "将路径渲染为线,而不是一个奇怪的东西");
        addDescription("忽略深度渲染路径", "渲染路径时忽略深度");
        addDescription("渲染选择", "渲染选择");
        addDescription("渲染选择框", "渲染选择框");
        addDescription("忽略深度渲染选择框", "渲染选择框时忽略深度（例如破坏、放置、走进）");
        addDescription("渲染选择角落", "渲染选择角落");
        addDescription("忽略深度渲染选择", "渲染选择时忽略深度");
        addDescription("任何方块变化时重新打包", "当一个方块变化时,重新打包它所在的整个区块");
        addDescription("重新种植作物", "在耕作时重新种植普通作物,留下仙人掌和甘蔗自生长");
        addDescription("重新种植下界疣", "在耕作时重新种植下界疣。");
        addDescription("到达时右键点击容器", "当朝向一个容器方块（箱子、末影箱、熔炉等）前进时,到达时右键点击并打开它。");
        addDescription("右键点击速度", "允许两次右键点击之间的最大ticks数。");
        addDescription("备选扩展", "当没有指定扩展时,构建命令使用的备选扩展。");
        addDescription("方案X方向", "当此设置为真时,构建方案时,最高的X坐标作为原点,而不是最低的X坐标");
        addDescription("方案Y方向", "当此设置为真时,构建方案时,最高的Y坐标作为原点,而不是最低的Y坐标");
        addDescription("方案Z方向", "当此设置为真时,构建方案时,最高的Z坐标作为原点,而不是最低的Z坐标");
        addDescription("选择线宽", "渲染时目标线的宽度（以像素为单位）。");
        addDescription("选择透明度", "选择的透明度。");
        addDescription("短Baritone前缀", "在聊天日志中使用短的Baritone前缀[B],而不是[Baritone]");
        addDescription("简化未加载的Y坐标", "如果你的目标是一个在未加载区块中的GoalBlock,假设它距离足够远,Y坐标还不重要,并在计算路径之前将其替换为GoalXZ。");
        addDescription("跳过失败层", "如果某一层无法构建,直接跳过它。");
        addDescription("慢路径", "用于调试,考虑节点的速度非常非常慢");
        addDescription("慢路径延迟毫秒数", "每个节点之间的毫秒数");
        addDescription("慢路径超时毫秒数", "启用慢路径时的备用超时数值");
        addDescription("拼接路径", "当计算出一个新的路径段与当前段不重叠,只是从当前段结束处开始时,将其拼接并形成一个更长的路径。");
        addDescription("冲刺上升", "在上升时尽可能提前冲刺并跳跃");
        addDescription("水中冲刺", "在水中继续冲刺");
        addDescription("从指定层开始", "从特定层开始构建方案。");
        addDescription("吐司机", "Baritone将显示吐司时调用的函数。");
        addDescription("吐司计时器", "弹出消息显示的时间");
        addDescription("用剑挖矿", "使用剑来挖矿。");
        addDescription("详细命令异常", "打印出所有命令异常的堆栈跟踪到标准输出,甚至是简单的语法错误");
        addDescription("水面行走惩罚", "在水面行走会很快消耗饥饿值,因此要惩罚它");
        addDescription("边走边破坏", "当需要破坏路上的方块时,不要停止前进");
        addDescription("世界探索区块偏移", "在探索世界时,将最近的未加载区块在两个轴向上偏移这么多。");
        addDescription("Y层级框大小", "当当前目标是GoalYLevel时,渲染的框的大小");
    }
}
