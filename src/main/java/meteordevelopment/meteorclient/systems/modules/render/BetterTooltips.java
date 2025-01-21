/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import meteordevelopment.meteorclient.events.game.ItemStackTooltipEvent;
import meteordevelopment.meteorclient.events.render.TooltipDataEvent;
import meteordevelopment.meteorclient.mixin.EntityAccessor;
import meteordevelopment.meteorclient.mixin.EntityBucketItemAccessor;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.misc.ByteCountDataOutput;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.player.EChestMemory;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.tooltip.*;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.entity.BeehiveBlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.*;
import net.minecraft.component.type.SuspiciousStewEffectsComponent.StewEffect;
import net.minecraft.entity.Bucketable;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffectUtil;
import net.minecraft.item.*;
import net.minecraft.item.consume.ApplyEffectsConsumeEffect;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.MutableText;
import net.minecraft.text.RawFilteredPair;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Formatting;

import java.util.Comparator;
import java.util.List;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT;

public class BetterTooltips extends Module {
    public static final Color ECHEST_COLOR = new Color(0, 50, 50);
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgPreviews = settings.createGroup("预览");
    private final SettingGroup sgOther = settings.createGroup("其他");
    private final SettingGroup sgHideFlags = settings.createGroup("隐藏标志");

    // General

    private final Setting<DisplayWhen> displayWhen = sgGeneral.add(new EnumSetting.Builder<DisplayWhen>()
        .name("显示时机")
        .description("何时显示预览。")
        .defaultValue(DisplayWhen.Keybind)
        .onChanged(value -> updateTooltips = true)
        .build()
    );

    private final Setting<Keybind> keybind = sgGeneral.add(new KeybindSetting.Builder()
        .name("按键绑定")
        .description("按键绑定模式的绑定键。")
        .defaultValue(Keybind.fromKey(GLFW_KEY_LEFT_ALT))
        .visible(() -> displayWhen.get() == DisplayWhen.Keybind)
        .onChanged(value -> updateTooltips = true)
        .build()
    );

    private final Setting<Boolean> middleClickOpen = sgGeneral.add(new BoolSetting.Builder()
        .name("中键点击打开")
        .description("当你中键点击物品时，打开一个带有存储方块或书籍物品栏的GUI窗口。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> pauseInCreative = sgGeneral.add(new BoolSetting.Builder()
        .name("创意模式暂停")
        .description("当玩家处于创意模式时，暂停中键点击打开。")
        .defaultValue(true)
        .visible(middleClickOpen::get)
        .build()
    );

    // Previews

    private final Setting<Boolean> shulkers = sgPreviews.add(new BoolSetting.Builder()
        .name("容器")
        .description("当将鼠标悬停在容器上时，在物品栏中显示该容器的预览。")
        .defaultValue(true)
        .onChanged(value -> updateTooltips = true)
        .build()
    );

    private final Setting<Boolean> shulkerCompactTooltip = sgPreviews.add(new BoolSetting.Builder()
        .name("紧凑的末影箱提示")
        .description("紧凑化末影箱提示框中的行。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> echest = sgPreviews.add(new BoolSetting.Builder()
        .name("末影箱")
        .description("当将鼠标悬停在末影箱上时，在物品栏中显示末影箱的预览。")
        .defaultValue(true)
        .onChanged(value -> updateTooltips = true)
        .build()
    );

    private final Setting<Boolean> maps = sgPreviews.add(new BoolSetting.Builder()
        .name("地图")
        .description("当将鼠标悬停在地图上时，在物品栏中显示地图的预览。")
        .defaultValue(true)
        .onChanged(value -> updateTooltips = true)
        .build()
    );

    public final Setting<Double> mapsScale = sgPreviews.add(new DoubleSetting.Builder()
        .name("地图缩放")
        .description("地图预览的缩放比例。")
        .defaultValue(1)
        .min(0.001)
        .sliderMax(1)
        .visible(maps::get)
        .build()
    );

    private final Setting<Boolean> books = sgPreviews.add(new BoolSetting.Builder()
        .name("书籍")
        .description("当将鼠标悬停在书籍上时，在物品栏中显示书籍的内容。")
        .defaultValue(true)
        .onChanged(value -> updateTooltips = true)
        .build()
    );

    private final Setting<Boolean> banners = sgPreviews.add(new BoolSetting.Builder()
        .name("旗帜")
        .description("当将鼠标悬停在旗帜上时，显示旗帜的图案。也适用于盾牌。")
        .defaultValue(true)
        .onChanged(value -> updateTooltips = true)
        .build()
    );

    private final Setting<Boolean> entitiesInBuckets = sgPreviews.add(new BoolSetting.Builder()
        .name("桶中的实体")
        .description("当将鼠标悬停在桶中时，显示桶里的实体。")
        .defaultValue(true)
        .onChanged(value -> updateTooltips = true)
        .build()
    );

    // Extras

    public final Setting<Boolean> byteSize = sgOther.add(new BoolSetting.Builder()
        .name("字节大小")
        .description("显示物品大小（以字节为单位）在提示框中。")
        .defaultValue(true)
        .onChanged(value -> updateTooltips = true)
        .build()
    );

    private final Setting<Boolean> statusEffects = sgOther.add(new BoolSetting.Builder()
        .name("状态效果")
        .description("在食物物品的提示框中添加状态效果列表。")
        .defaultValue(true)
        .onChanged(value -> updateTooltips = true)
        .build()
    );

    private final Setting<Boolean> beehive = sgOther.add(new BoolSetting.Builder()
        .name("蜂巢")
        .description("显示蜂巢或蜂巢巢穴的信息。")
        .defaultValue(true)
        .onChanged(value -> updateTooltips = true)
        .build()
    );

    //Hide flags

    public final Setting<Boolean> tooltip = sgHideFlags.add(new BoolSetting.Builder()
        .name("提示框")
        .description("显示提示框，即使它是隐藏的。")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> additional = sgHideFlags.add(new BoolSetting.Builder()
        .name("额外")
        .description("显示隐藏时的药水效果、烟花状态、书籍作者等信息。")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> armorTrim = sgHideFlags.add(new BoolSetting.Builder()
        .name("盔甲修饰")
        .description("显示隐藏时的盔甲修饰。")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> attributeModifiers = sgHideFlags.add(new BoolSetting.Builder()
        .name("属性修饰")
        .description("显示隐藏时的物品修饰符。")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> canBreak = sgHideFlags.add(new BoolSetting.Builder()
        .name("可破坏")
        .description("显示 \"can_break\" 组件，当它隐藏时。")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> canPlaceOn = sgHideFlags.add(new BoolSetting.Builder()
        .name("可放置在")
        .description("显示 \"can_place_on\" 组件，当它隐藏时。")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> dye = sgHideFlags.add(new BoolSetting.Builder()
        .name("染色")
        .description("显示物品染色标签，当它隐藏时。")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> enchantments = sgHideFlags.add(new BoolSetting.Builder()
        .name("附魔")
        .description("显示附魔信息，当它隐藏时。")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> jukeboxPlayable = sgHideFlags.add(new BoolSetting.Builder()
        .name("唱片机可播放")
        .description("显示物品是否可以在唱片机中播放，当它隐藏时。")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> unbreakable = sgHideFlags.add(new BoolSetting.Builder()
        .name("不可破坏")
        .description("显示 \"Unbreakable\" 组件，当它隐藏时。")
        .defaultValue(false)
        .build()
    );

    private boolean updateTooltips = false;
    private static final ItemStack[] ITEMS = new ItemStack[27];

    public BetterTooltips() {
        super(Categories.Render, "更好的提示框", "为某些物品显示更有用的提示框。");
    }

    @EventHandler
    private void appendTooltip(ItemStackTooltipEvent event) {
        // Hide hidden (empty) tooltips unless the tooltip hide flag setting is true.
        if (!tooltip.get() && event.list().isEmpty()) {
            // Hold-to-preview tooltip text is always added when needed.
            appendPreviewTooltipText(event, false);
            return;
        }

        // Status effects
        if (statusEffects.get()) {
            if (event.itemStack().getItem() == Items.SUSPICIOUS_STEW) {
                SuspiciousStewEffectsComponent stewEffectsComponent = event.itemStack().get(DataComponentTypes.SUSPICIOUS_STEW_EFFECTS);
                if (stewEffectsComponent != null) {
                    for (StewEffect effectTag : stewEffectsComponent.effects()) {
                        StatusEffectInstance effect = new StatusEffectInstance(effectTag.effect(), effectTag.duration(), 0);
                        event.appendStart(getStatusText(effect));
                    }
                }
            } else {
                ConsumableComponent consumable = event.itemStack().get(DataComponentTypes.CONSUMABLE);
                if (consumable != null) {
                    consumable.onConsumeEffects().stream()
                        .filter(ApplyEffectsConsumeEffect.class::isInstance)
                        .map(ApplyEffectsConsumeEffect.class::cast)
                        .flatMap(apply -> apply.effects().stream())
                        .forEach(effect -> event.appendStart(getStatusText(effect)));
                }
            }
        }

        //Beehive
        if (beehive.get()) {
            if (event.itemStack().getItem() == Items.BEEHIVE || event.itemStack().getItem() == Items.BEE_NEST) {
                BlockStateComponent blockStateComponent = event.itemStack().get(DataComponentTypes.BLOCK_STATE);
                if (blockStateComponent != null) {
                    String level = blockStateComponent.properties().get("蜂蜜等级");
                    event.appendStart(Text.literal(String.format("%s蜂蜜等级: %s%s%s。", Formatting.GRAY, Formatting.YELLOW, level, Formatting.GRAY)));
                }

                List<BeehiveBlockEntity.BeeData> bees = event.itemStack().get(DataComponentTypes.BEES);
                if (bees != null) {
                    event.appendStart(Text.literal(String.format("%s蜜蜂: %s%d%s。", Formatting.GRAY, Formatting.YELLOW, bees.size(), Formatting.GRAY)));
                }
            }
        }

        // Item size tooltip
        if (byteSize.get()) {
            try {
                event.itemStack().toNbt(mc.player.getRegistryManager()).write(ByteCountDataOutput.INSTANCE);

                int byteCount = ByteCountDataOutput.INSTANCE.getCount();
                String count;

                ByteCountDataOutput.INSTANCE.reset();

                if (byteCount >= 1024) count = String.format("%.2f kb", byteCount / (float) 1024);
                else count = String.format("%d 字节", byteCount);

                event.appendEnd(Text.literal(count).formatted(Formatting.GRAY));
            } catch (Exception e) {
                event.appendEnd(Text.literal("获取字节时出错。").formatted(Formatting.RED));
            }
        }

        // Hold to preview tooltip
        appendPreviewTooltipText(event, true);
    }

    @EventHandler
    private void getTooltipData(TooltipDataEvent event) {
        // Container preview
        if (previewShulkers() && Utils.hasItems(event.itemStack)) {
            Utils.getItemsInContainerItem(event.itemStack, ITEMS);
            event.tooltipData = new ContainerTooltipComponent(ITEMS, Utils.getShulkerColor(event.itemStack));
        }

        // EChest preview
        else if (event.itemStack.getItem() == Items.ENDER_CHEST && previewEChest()) {
            event.tooltipData = EChestMemory.isKnown()
                ? new ContainerTooltipComponent(EChestMemory.ITEMS.toArray(new ItemStack[27]), ECHEST_COLOR)
                : new TextTooltipComponent(Text.literal("未知物品栏。").formatted(Formatting.DARK_RED));
        }

        // Map preview
        else if (event.itemStack.getItem() == Items.FILLED_MAP && previewMaps()) {
            MapIdComponent mapIdComponent = event.itemStack.get(DataComponentTypes.MAP_ID);
            if (mapIdComponent != null) event.tooltipData = new MapTooltipComponent(mapIdComponent.id());
        }

        // Book preview
        else if ((event.itemStack.getItem() == Items.WRITABLE_BOOK || event.itemStack.getItem() == Items.WRITTEN_BOOK) && previewBooks()) {
            Text page = getFirstPage(event.itemStack);
            if (page != null) event.tooltipData = new BookTooltipComponent(page);
        }

        // Banner preview
        else if (event.itemStack.getItem() instanceof BannerItem && previewBanners()) {
            event.tooltipData = new BannerTooltipComponent(event.itemStack);
        } else if (event.itemStack.getItem() instanceof BannerPatternItem bannerPatternItem && previewBanners()) {
            event.tooltipData = createBannerFromBannerPatternItem(bannerPatternItem);
        } else if (event.itemStack.getItem() == Items.SHIELD && previewBanners()) {
            if (!event.itemStack.getOrDefault(DataComponentTypes.BANNER_PATTERNS, BannerPatternsComponent.DEFAULT).layers().isEmpty()) {
                event.tooltipData = createBannerFromShield(event.itemStack);
            }
        }

        // Fish peek
        else if (event.itemStack.getItem() instanceof EntityBucketItem bucketItem && previewEntities()) {
            EntityType<?> type = ((EntityBucketItemAccessor) bucketItem).getEntityType();
            Entity entity = type.create(mc.world, SpawnReason.NATURAL);
            if (entity != null) {
                NbtComponent nbtComponent = event.itemStack.getOrDefault(DataComponentTypes.BUCKET_ENTITY_DATA, NbtComponent.DEFAULT);
                if (nbtComponent.isEmpty()) {
                    return;
                }

                ((Bucketable) entity).copyDataFromNbt(nbtComponent.copyNbt());
                ((EntityAccessor) entity).setInWater(true);
                event.tooltipData = new EntityTooltipComponent(entity);
            }
        }
    }

    public void applyCompactShulkerTooltip(ItemStack shulkerItem, List<Text> tooltip) {
        if (shulkerItem.contains(DataComponentTypes.CONTAINER_LOOT)) {
            tooltip.add(Text.literal("???????"));
        }

        if (Utils.hasItems(shulkerItem)) {
            Utils.getItemsInContainerItem(shulkerItem, ITEMS);

            Object2IntMap<Item> counts = new Object2IntOpenHashMap<>();

            for (ItemStack item : ITEMS) {
                if (item.isEmpty()) continue;

                int count = counts.getInt(item.getItem());
                counts.put(item.getItem(), count + item.getCount());
            }

            counts.keySet().stream().sorted(Comparator.comparingInt(value -> -counts.getInt(value))).limit(5).forEach(item -> {
                MutableText mutableText = item.getName().copyContentOnly();
                mutableText.append(Text.literal(" x").append(String.valueOf(counts.getInt(item))).formatted(Formatting.GRAY));
                tooltip.add(mutableText);
            });

            if (counts.size() > 5) {
                tooltip.add((Text.translatable("container.shulkerBox.more", counts.size() - 5)).formatted(Formatting.ITALIC));
            }
        }
    }

    private void appendPreviewTooltipText(ItemStackTooltipEvent event, boolean spacer) {
        if (!isPressed() && (
            shulkers.get() && Utils.hasItems(event.itemStack())
                || (event.itemStack().getItem() == Items.ENDER_CHEST && echest.get())
                || (event.itemStack().getItem() == Items.FILLED_MAP && maps.get())
                || (event.itemStack().getItem() == Items.WRITABLE_BOOK && books.get())
                || (event.itemStack().getItem() == Items.WRITTEN_BOOK && books.get())
                || (event.itemStack().getItem() instanceof EntityBucketItem && entitiesInBuckets.get())
                || (event.itemStack().getItem() instanceof BannerItem && banners.get())
                || (event.itemStack().getItem() instanceof BannerPatternItem && banners.get())
                || (event.itemStack().getItem() == Items.SHIELD && banners.get())
        )) {
            // we don't want to add the spacer if the tooltip is hidden
            if (spacer) event.appendEnd(Text.literal(""));
            event.appendEnd(Text.literal("按住 " + Formatting.YELLOW + keybind + Formatting.RESET + " 进行预览"));
        }
    }

    private MutableText getStatusText(StatusEffectInstance effect) {
        MutableText text = Text.translatable(effect.getTranslationKey());
        if (effect.getAmplifier() != 0) {
            text.append(String.format(" %d (%s)", effect.getAmplifier() + 1, StatusEffectUtil.getDurationText(effect, 1, mc.world.getTickManager().getTickRate()).getString()));
        } else {
            text.append(String.format(" (%s)", StatusEffectUtil.getDurationText(effect, 1, mc.world.getTickManager().getTickRate()).getString()));
        }

        if (effect.getEffectType().value().isBeneficial()) return text.formatted(Formatting.BLUE);
        return text.formatted(Formatting.RED);
    }

    @SuppressWarnings("数据流问题")
    private Text getFirstPage(ItemStack bookItem) {
        if (bookItem.get(DataComponentTypes.WRITABLE_BOOK_CONTENT) != null) {
            List<RawFilteredPair<String>> pages = bookItem.get(DataComponentTypes.WRITABLE_BOOK_CONTENT).pages();

            if (pages.isEmpty()) return null;
            return Text.literal(pages.getFirst().get(false));
        } else if (bookItem.get(DataComponentTypes.WRITTEN_BOOK_CONTENT) != null) {
            List<RawFilteredPair<Text>> pages = bookItem.get(DataComponentTypes.WRITTEN_BOOK_CONTENT).pages();
            if (pages.isEmpty()) return null;

            return pages.getFirst().get(false);
        }

        return null;
    }

    private BannerTooltipComponent createBannerFromBannerPatternItem(BannerPatternItem item) {
        // I can't imagine getting the banner pattern from a banner pattern item would fail without some serious messing around
        BannerPatternsComponent component = new BannerPatternsComponent.Builder().add(mc.player.getRegistryManager().getOrThrow(RegistryKeys.BANNER_PATTERN).getOrThrow(item.getPattern()).get(0), DyeColor.WHITE).build();
        return new BannerTooltipComponent(DyeColor.GRAY, component);
    }

    private BannerTooltipComponent createBannerFromShield(ItemStack shieldItem) {
        DyeColor dyeColor2 = shieldItem.getOrDefault(DataComponentTypes.BASE_COLOR, DyeColor.WHITE);
        BannerPatternsComponent bannerPatternsComponent = shieldItem.getOrDefault(DataComponentTypes.BANNER_PATTERNS, BannerPatternsComponent.DEFAULT);
        return new BannerTooltipComponent(dyeColor2, bannerPatternsComponent);
    }

    public boolean middleClickOpen() {
        return (isActive() && middleClickOpen.get()) && (!pauseInCreative.get() || !mc.player.isInCreativeMode());
    }

    public boolean previewShulkers() {
        return isActive() && isPressed() && shulkers.get();
    }

    public boolean shulkerCompactTooltip() {
        return isActive() && shulkerCompactTooltip.get();
    }

    private boolean previewEChest() {
        return isPressed() && echest.get();
    }

    private boolean previewMaps() {
        return isPressed() && maps.get();
    }

    private boolean previewBooks() {
        return isPressed() && books.get();
    }

    private boolean previewBanners() {
        return isPressed() && banners.get();
    }

    private boolean previewEntities() {
        return isPressed() && entitiesInBuckets.get();
    }

    private boolean isPressed() {
        return (keybind.get().isPressed() && displayWhen.get() == DisplayWhen.Keybind) || displayWhen.get() == DisplayWhen.Always;
    }

    public boolean updateTooltips() {
        if (updateTooltips && isActive()) {
            updateTooltips = false;
            return true;
        }

        return false;
    }

    public enum DisplayWhen {
        Keybind,
        Always
    }
}
