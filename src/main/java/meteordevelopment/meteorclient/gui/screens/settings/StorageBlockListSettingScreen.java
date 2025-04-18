/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.gui.screens.settings;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.StorageBlockListSetting;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.Item;
import net.minecraft.item.Items;

import java.util.List;
import java.util.Map;

public class StorageBlockListSettingScreen extends CollectionListSettingScreen<BlockEntityType<?>> {
    private static final Map<BlockEntityType<?>, BlockEntityTypeInfo> BLOCK_ENTITY_TYPE_INFO_MAP = new Object2ObjectOpenHashMap<>();
    private static final BlockEntityTypeInfo UNKNOWN = new BlockEntityTypeInfo(Items.BARRIER, "未知");

    static {
        // Map of storage blocks
        BLOCK_ENTITY_TYPE_INFO_MAP.put(BlockEntityType.BARREL, new BlockEntityTypeInfo(Items.BARREL, "桶"));
        BLOCK_ENTITY_TYPE_INFO_MAP.put(BlockEntityType.BLAST_FURNACE, new BlockEntityTypeInfo(Items.BLAST_FURNACE, "高炉"));
        BLOCK_ENTITY_TYPE_INFO_MAP.put(BlockEntityType.BREWING_STAND, new BlockEntityTypeInfo(Items.BREWING_STAND, "酿造台"));
        BLOCK_ENTITY_TYPE_INFO_MAP.put(BlockEntityType.CAMPFIRE, new BlockEntityTypeInfo(Items.CAMPFIRE, "篝火"));
        BLOCK_ENTITY_TYPE_INFO_MAP.put(BlockEntityType.CHEST, new BlockEntityTypeInfo(Items.CHEST, "箱子"));
        BLOCK_ENTITY_TYPE_INFO_MAP.put(BlockEntityType.CHISELED_BOOKSHELF, new BlockEntityTypeInfo(Items.CHISELED_BOOKSHELF, "雕刻书架"));
        BLOCK_ENTITY_TYPE_INFO_MAP.put(BlockEntityType.CRAFTER, new BlockEntityTypeInfo(Items.CRAFTER, "工匠台"));
        BLOCK_ENTITY_TYPE_INFO_MAP.put(BlockEntityType.DISPENSER, new BlockEntityTypeInfo(Items.DISPENSER, "发射器"));
        BLOCK_ENTITY_TYPE_INFO_MAP.put(BlockEntityType.DECORATED_POT, new BlockEntityTypeInfo(Items.DECORATED_POT, "装饰陶罐"));
        BLOCK_ENTITY_TYPE_INFO_MAP.put(BlockEntityType.DROPPER, new BlockEntityTypeInfo(Items.DROPPER, "投掷器"));
        BLOCK_ENTITY_TYPE_INFO_MAP.put(BlockEntityType.ENDER_CHEST, new BlockEntityTypeInfo(Items.ENDER_CHEST, "末影箱"));
        BLOCK_ENTITY_TYPE_INFO_MAP.put(BlockEntityType.FURNACE, new BlockEntityTypeInfo(Items.FURNACE, "熔炉"));
        BLOCK_ENTITY_TYPE_INFO_MAP.put(BlockEntityType.HOPPER, new BlockEntityTypeInfo(Items.HOPPER, "漏斗"));
        BLOCK_ENTITY_TYPE_INFO_MAP.put(BlockEntityType.SHULKER_BOX, new BlockEntityTypeInfo(Items.SHULKER_BOX, "箱型 Shulker"));
        BLOCK_ENTITY_TYPE_INFO_MAP.put(BlockEntityType.SMOKER, new BlockEntityTypeInfo(Items.SMOKER, "熏炉"));
        BLOCK_ENTITY_TYPE_INFO_MAP.put(BlockEntityType.TRAPPED_CHEST, new BlockEntityTypeInfo(Items.TRAPPED_CHEST, "陷阱箱"));
    }

    public StorageBlockListSettingScreen(GuiTheme theme, Setting<List<BlockEntityType<?>>> setting) {
        super(theme, "选择存储方块", setting, setting.get(), StorageBlockListSetting.REGISTRY);
    }

    @Override
    protected WWidget getValueWidget(BlockEntityType<?> value) {
        Item item = BLOCK_ENTITY_TYPE_INFO_MAP.getOrDefault(value, UNKNOWN).item();
        return theme.itemWithLabel(item.getDefaultStack(), getValueName(value));
    }

    @Override
    protected String getValueName(BlockEntityType<?> value) {
        return BLOCK_ENTITY_TYPE_INFO_MAP.getOrDefault(value, UNKNOWN).name();
    }

    private record BlockEntityTypeInfo(Item item, String name) {}
}
