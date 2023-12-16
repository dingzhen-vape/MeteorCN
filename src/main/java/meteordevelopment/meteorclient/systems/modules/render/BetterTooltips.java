/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render;

import com.google.gson.JsonParseException;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import meteordevelopment.meteorclient.events.game.ItemStackTooltipEvent;
import meteordevelopment.meteorclient.events.game.SectionVisibleEvent;
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
import net.minecraft.block.entity.BannerPattern;
import net.minecraft.block.entity.BannerPatterns;
import net.minecraft.entity.Bucketable;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffectUtil;
import net.minecraft.inventory.Inventories;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.collection.DefaultedList;

import java.io.IOException;
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
        .name("什么时候显示")
        .description("何时显示预览。")
        .defaultValue(DisplayWhen.Keybind)
        .build()
    );

    private final Setting<Keybind> keybind = sgGeneral.add(new KeybindSetting.Builder()
        .name("keybind")
        .description("键绑定模式的绑定。")
        .defaultValue(Keybind.fromKey(GLFW_KEY_LEFT_ALT))
        .visible(() -> displayWhen.get() == DisplayWhen.Keybind)
        .build()
    );

    private final Setting<Boolean> middleClickOpen = sgGeneral.add(new BoolSetting.Builder()
        .name("中键打开")
        .description("当你在物品栏中中键点击存储方块的物品时，打开一个带有存储方块物品的GUI窗口。")
        .defaultValue(true)
        .build()
    );

    // Previews

    private final Setting<Boolean> shulkers = sgPreviews.add(new BoolSetting.Builder()
        .name("容器预览")
        .description("当你在物品栏中悬停在一个容器上时，显示一个容器的预览。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> shulkerCompactTooltip = sgPreviews.add(new BoolSetting.Builder()
        .name("压缩潜影盒")
        .description("压缩潜影盒的提示行。")
        .defaultValue(true)
        .visible(shulkers::get)
        .build()
    );

    public final Setting<Boolean> echest = sgPreviews.add(new BoolSetting.Builder()
        .name("末影箱预览")
        .description("当你在物品栏中悬停在你的末影箱上时，显示你的末影箱的预览。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> maps = sgPreviews.add(new BoolSetting.Builder()
        .name("地图")
        .description("当你在物品栏中悬停在一张地图上时，显示一张地图的预览。")
        .defaultValue(true)
        .build()
    );

    public final Setting<Double> mapsScale = sgPreviews.add(new DoubleSetting.Builder()
        .name("地图比例")
        .description("地图预览的比例。")
        .defaultValue(1)
        .min(0.001)
        .sliderMax(1)
        .visible(maps::get)
        .build()
    );

    private final Setting<Boolean> books = sgPreviews.add(new BoolSetting.Builder()
        .name("书")
        .description("当你在物品栏中悬停在一本书上时，显示一本书的内容。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> banners = sgPreviews.add(new BoolSetting.Builder()
        .name("旗帜图案")
        .description("当你在物品栏中悬停在一个旗帜上时，显示旗帜的图案。也适用于盾牌。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> entitiesInBuckets = sgPreviews.add(new BoolSetting.Builder()
        .name("桶内实体")
        .description("当你在物品栏中悬停在一个桶上时，显示桶里的实体。")
        .defaultValue(true)
        .build()
    );

    // Extras

    public final Setting<Boolean> byteSize = sgOther.add(new BoolSetting.Builder()
        .name("字节大小")
        .description("在提示中显示一个物品的字节大小。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> statusEffects = sgOther.add(new BoolSetting.Builder()
        .name("状态效果")
        .description("在食物物品的提示中添加状态效果的列表。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> beehive = sgOther.add(new BoolSetting.Builder()
        .name("蜂巢")
        .description("显示一个蜂箱或蜂巢的信息。")
        .defaultValue(true)
        .build()
    );

    //Hide flags

    private final Setting<Boolean> enchantments = sgHideFlags.add(new BoolSetting.Builder()
        .name("附魔")
        .description("当它被隐藏时，显示附魔。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> modifiers = sgHideFlags.add(new BoolSetting.Builder()
        .name("物品修饰符")
        .description("当它被隐藏时，显示物品修饰符。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> unbreakable = sgHideFlags.add(new BoolSetting.Builder()
        .name("不毁")
        .description("当它被隐藏时，显示\"Unbreakable\"标签。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> canDestroy = sgHideFlags.add(new BoolSetting.Builder()
        .name("可以被破坏")
        .description("当它被隐藏时，显示\"CanDestroy\"标签。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> canPlaceOn = sgHideFlags.add(new BoolSetting.Builder()
        .name("可以放在哪")
        .description("当它被隐藏时，显示\"CanPlaceOn\"标签。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> additional = sgHideFlags.add(new BoolSetting.Builder()
        .name("additional")
        .description("当它被隐藏时，显示药水效果，烟花状态，书籍作者等。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> dye = sgHideFlags.add(new BoolSetting.Builder()
        .name("dye")
        .description("当它被隐藏时，显示染色物品的标签。")
        .defaultValue(false)
        .build()
    );

    public BetterTooltips() {
        super(Categories.Render, "更好的物品标签", "为某些物品显示更有用的提示。");
    }

    @EventHandler
    private void appendTooltip(ItemStackTooltipEvent event) {
        // Status effects
        if (statusEffects.get()) {
            if (event.itemStack.getItem() == Items.SUSPICIOUS_STEW) {
                NbtCompound tag = event.itemStack.getNbt();

                if (tag != null) {
                    NbtList effects = tag.getList("Effects", 10);

                    if (effects != null) {
                        for (int i = 0; i < effects.size(); i++) {
                            NbtCompound effectTag = effects.getCompound(i);
                            byte effectId = effectTag.getByte("EffectId");
                            int effectDuration = effectTag.contains("EffectDuration") ? effectTag.getInt("EffectDuration") : 160;
                            StatusEffect type = Registries.STATUS_EFFECT.get(effectId);

                            if (type != null) {
                                StatusEffectInstance effect = new StatusEffectInstance(type, effectDuration, 0);
                                event.list.add(1, getStatusText(effect));
                            }
                        }
                    }
                }
            }
            else if (event.itemStack.getItem().isFood()) {
                FoodComponent food = event.itemStack.getItem().getFoodComponent();

                if (food != null) {
                    food.getStatusEffects().forEach((e) -> {
                        StatusEffectInstance effect = e.getFirst();
                        event.list.add(1, getStatusText(effect));
                    });
                }
            }
        }

        //Beehive
        if (beehive.get()) {
            if (event.itemStack.getItem() == Items.BEEHIVE || event.itemStack.getItem() == Items.BEE_NEST) {
                NbtCompound tag = event.itemStack.getNbt();

                if (tag != null) {
                    NbtCompound blockStateTag = tag.getCompound("BlockStateTag");
                    if (blockStateTag != null) {
                        int level = blockStateTag.getInt("honey_level");
                        event.list.add(1, Text.literal(String.format("%s蜂蜜等级: %s%d%s。", Formatting.GRAY, Formatting.YELLOW, level, Formatting.GRAY)));
                    }

                    NbtCompound blockEntityTag = tag.getCompound("BlockEntityTag");
                    if (blockEntityTag != null) {
                        NbtList beesTag = blockEntityTag.getList("Bees", 10);
                        event.list.add(1, Text.literal(String.format("%s蜜蜂: %s%d%s。", Formatting.GRAY, Formatting.YELLOW, beesTag.size(), Formatting.GRAY)));
                    }
                }
            }
        }

        // Item size tooltip
        if (byteSize.get()) {
            try {
                event.itemStack.writeNbt(new NbtCompound()).write(ByteCountDataOutput.INSTANCE);

                int byteCount = ByteCountDataOutput.INSTANCE.getCount();
                String count;

                ByteCountDataOutput.INSTANCE.reset();

                if (byteCount >= 1024) count = String.format("%.2f kb", byteCount / (float) 1024);
                else count = String.format("%d 字节", byteCount);

                event.list.add(Text.literal(count).formatted(Formatting.GRAY));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Hold to preview tooltip
        if ((shulkers.get() && !previewShulkers() && Utils.hasItems(event.itemStack))
            || (event.itemStack.getItem() == Items.ENDER_CHEST && echest.get() && !previewEChest())
            || (event.itemStack.getItem() == Items.FILLED_MAP && maps.get() && !previewMaps())
            || (event.itemStack.getItem() == Items.WRITABLE_BOOK && books.get() && !previewBooks())
            || (event.itemStack.getItem() == Items.WRITTEN_BOOK && books.get() && !previewBooks())
            || (event.itemStack.getItem() instanceof EntityBucketItem && entitiesInBuckets.get() && !previewEntities())
            || (event.itemStack.getItem() instanceof BannerItem && banners.get() && !previewBanners())
            || (event.itemStack.getItem() instanceof BannerPatternItem && banners.get()  && !previewBanners())
            || (event.itemStack.getItem() == Items.SHIELD && banners.get() && !previewBanners())) {
            event.list.add(Text.literal(""));
            event.list.add(Text.literal("按住 " + Formatting.YELLOW + keybind + Formatting.RESET + " 来预览"));
        }
    }

    @EventHandler
    private void getTooltipData(TooltipDataEvent event) {
        // Container preview
        if (previewShulkers() && Utils.hasItems(event.itemStack)) {
            NbtCompound compoundTag = event.itemStack.getSubNbt("BlockEntityTag");
            DefaultedList<ItemStack> itemStacks = DefaultedList.ofSize(27, ItemStack.EMPTY);
            Inventories.readNbt(compoundTag, itemStacks);
            event.tooltipData = new ContainerTooltipComponent(itemStacks, Utils.getShulkerColor(event.itemStack));
        }

        // EChest preview
        else if (event.itemStack.getItem() == Items.ENDER_CHEST && previewEChest()) {
            event.tooltipData = EChestMemory.isKnown() ? new ContainerTooltipComponent(EChestMemory.ITEMS, ECHEST_COLOR)
                : new TextTooltipComponent(Text.literal("未知的末影箱物品。").formatted(Formatting.DARK_RED));
        }

        // Map preview
        else if (event.itemStack.getItem() == Items.FILLED_MAP && previewMaps()) {
            Integer mapId = FilledMapItem.getMapId(event.itemStack);
            if (mapId != null) event.tooltipData = new MapTooltipComponent(mapId);
        }

        // Book preview
        else if ((event.itemStack.getItem() == Items.WRITABLE_BOOK || event.itemStack.getItem() == Items.WRITTEN_BOOK) && previewBooks()) {
            Text page = getFirstPage(event.itemStack);
            if (page != null) event.tooltipData = new BookTooltipComponent(page);
        }

        // Banner preview
        else if (event.itemStack.getItem() instanceof BannerItem && previewBanners()) {
            event.tooltipData = new BannerTooltipComponent(event.itemStack);
        }
        else if (event.itemStack.getItem() instanceof BannerPatternItem patternItem && previewBanners()) {
            boolean present = Registries.BANNER_PATTERN.getEntryList(patternItem.getPattern()).isPresent() && Registries.BANNER_PATTERN.getEntryList(patternItem.getPattern()).get().size() != 0;

            RegistryEntry<BannerPattern> bannerPattern = (present ? Registries.BANNER_PATTERN.getEntryList(patternItem.getPattern()).get().get(0) : null);
            if (bannerPattern != null) event.tooltipData = new BannerTooltipComponent(createBannerFromPattern(bannerPattern));
        }
        else if (event.itemStack.getItem() == Items.SHIELD && previewBanners()) {
            ItemStack banner = createBannerFromShield(event.itemStack);
            if (banner != null) event.tooltipData = new BannerTooltipComponent(banner);
        }

        // Fish peek
        else if (event.itemStack.getItem() instanceof EntityBucketItem bucketItem && previewEntities()) {
            EntityType<?> type = ((EntityBucketItemAccessor) bucketItem).getEntityType();
            Entity entity = type.create(mc.world);
            if (entity != null) {
                ((Bucketable) entity).copyDataFromNbt(event.itemStack.getOrCreateNbt());
                ((EntityAccessor) entity).setInWater(true);
                event.tooltipData = new EntityTooltipComponent(entity);
            }
        }
    }

    @EventHandler
    private void onSectionVisible(SectionVisibleEvent event) {
        if (enchantments.get() && event.section == ItemStack.TooltipSection.ENCHANTMENTS ||
            modifiers.get() && event.section == ItemStack.TooltipSection.MODIFIERS ||
            unbreakable.get() && event.section == ItemStack.TooltipSection.UNBREAKABLE ||
            canDestroy.get() && event.section == ItemStack.TooltipSection.CAN_DESTROY ||
            canPlaceOn.get() && event.section == ItemStack.TooltipSection.CAN_PLACE ||
            additional.get() && event.section == ItemStack.TooltipSection.ADDITIONAL ||
            dye.get() && event.section == ItemStack.TooltipSection.DYE)
            event.visible = true;
    }

    public void applyCompactShulkerTooltip(ItemStack stack, List<Text> tooltip) {
        NbtCompound tag = stack.getSubNbt("BlockEntityTag");

        if (tag != null) {
            if (tag.contains("LootTable", 8)) {
                tooltip.add(Text.literal("???????"));
            }

            if (tag.contains("Items", 9)) {
                DefaultedList<ItemStack> items = DefaultedList.ofSize(27, ItemStack.EMPTY);
                Inventories.readNbt(tag, items);

                Object2IntMap<Item> counts = new Object2IntOpenHashMap<>();

                for (ItemStack item : items) {
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
    }

    private MutableText getStatusText(StatusEffectInstance effect) {
        MutableText text = Text.translatable(effect.getTranslationKey());
        if (effect.getAmplifier() != 0) {
            text.append(String.format(" %d (%s)", effect.getAmplifier() + 1, StatusEffectUtil.getDurationText(effect, 1, mc.world.getTickManager().getTickRate()).getString()));
        }
        else {
            text.append(String.format(" (%s)", StatusEffectUtil.getDurationText(effect, 1, mc.world.getTickManager().getTickRate()).getString()));
        }

        if (effect.getEffectType().isBeneficial()) return text.formatted(Formatting.BLUE);
        return text.formatted(Formatting.RED);
    }

    private Text getFirstPage(ItemStack stack) {
        NbtCompound tag = stack.getNbt();
        if (tag == null) return null;

        NbtList pages = tag.getList("pages", 8);
        if (pages.size() < 1) return null;
        if (stack.getItem() == Items.WRITABLE_BOOK) return Text.literal(pages.getString(0));

        try {
            return Text.Serialization.fromLenientJson(pages.getString(0));
        } catch (JsonParseException e) {
            return Text.literal("无效的书籍数据");
        }
    }

    private ItemStack createBannerFromPattern(RegistryEntry<BannerPattern> pattern) {
        ItemStack itemStack = new ItemStack(Items.GRAY_BANNER);
        NbtCompound nbt = itemStack.getOrCreateSubNbt("BlockEntityTag");
        NbtList listNbt = new BannerPattern.Patterns().add(BannerPatterns.BASE, DyeColor.BLACK).add(pattern, DyeColor.WHITE).toNbt();
        nbt.put("Patterns", listNbt);
        return itemStack;
    }

    private ItemStack createBannerFromShield(ItemStack item) {
        if (!item.hasNbt()
            || !item.getNbt().contains("BlockEntityTag")
            || !item.getNbt().getCompound("BlockEntityTag").contains("Base"))
            return null;
        NbtList listNbt = new BannerPattern.Patterns().add(BannerPatterns.BASE, ShieldItem.getColor(item)).toNbt();
        NbtCompound nbt = item.getOrCreateSubNbt("BlockEntityTag");
        ItemStack bannerItem = new ItemStack(Items.GRAY_BANNER);
        NbtCompound bannerTag = bannerItem.getOrCreateSubNbt("BlockEntityTag");
        bannerTag.put("Patterns", listNbt);
        if (!nbt.contains("Patterns")) return bannerItem;
        NbtList shieldPatterns = nbt.getList("Patterns", NbtElement.COMPOUND_TYPE);
        listNbt.addAll(shieldPatterns);
        return bannerItem;
    }

    public boolean middleClickOpen() {
        return isActive() && middleClickOpen.get();
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

    public enum DisplayWhen {
        Keybind,
        Always
    }
}
