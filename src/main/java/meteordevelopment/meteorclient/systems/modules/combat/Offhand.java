/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.combat;

import meteordevelopment.meteorclient.events.meteor.MouseButtonEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.misc.input.KeyAction;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.*;

import static meteordevelopment.orbit.EventPriority.HIGHEST;
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT;

public class Offhand extends Module {
    private final SettingGroup sgCombat = settings.createGroup("战斗");
    private final SettingGroup sgTotem = settings.createGroup("图腾");

    //Combat

    private final Setting<Integer> delayTicks = sgCombat.add(new IntSetting.Builder()
        .name("物品切换延迟")
        .description("槽位移动之间的延迟。")
        .defaultValue(0)
        .min(0)
        .sliderMax(20)
        .build()
    );
    private final Setting<Item> preferreditem = sgCombat.add(new EnumSetting.Builder<Item>()
        .name("物品")
        .description("副手持有哪个物品。")
        .defaultValue(Item.Crystal)
        .build()
    );

    private final Setting<Boolean> hotbar = sgCombat.add(new BoolSetting.Builder()
        .name("快捷栏")
        .description("是否使用热键栏中的物品。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> rightgapple = sgCombat.add(new BoolSetting.Builder()
        .name("right-gapple")
        .description("按住右键时将切换到gapple。(请勿与药水一起使用)")
        .defaultValue(false)
        .build()
    );


    private final Setting<Boolean> SwordGap = sgCombat.add(new BoolSetting.Builder()
        .name("sword-gapple")
        .description("按住a时将切换到gapple剑并右键单击。")
        .defaultValue(false)
        .visible(rightgapple::get)
        .build()
    );

    private final Setting<Boolean> alwaysSwordGap = sgCombat.add(new BoolSetting.Builder()
        .name("always-gap-on-sword")
        .description("当你拿着剑时持有魔法金苹果。")
        .defaultValue(false)
        .visible(() -> !rightgapple.get())
        .build()
    );


    private final Setting<Boolean> alwaysPot = sgCombat.add(new BoolSetting.Builder()
        .name("always-pot-on-sword")
        .description("持有时会切换到药水剑")
        .defaultValue(false)
        .visible(() -> !rightgapple.get() && !alwaysSwordGap.get())
        .build()
    );
    private final Setting<Boolean> potionClick = sgCombat.add(new BoolSetting.Builder()
        .name("sword-pot")
        .description("拿着剑并右键单击时将切换到药水。")
        .defaultValue(false)
        .visible(() -> !rightgapple.get() && !alwaysPot.get() && !alwaysSwordGap.get() )
        .build()
    );

    //Totem

    private final Setting<Double> minHealth = sgTotem.add(new DoubleSetting.Builder()
        .name("min-health")
        .description("低于此生命值时将持有图腾。")
        .defaultValue(10)
        .range(0,36)
        .sliderRange(0,36)
        .build()
    );

    private final Setting<Boolean> elytra = sgTotem.add(new BoolSetting.Builder()
        .name("鞘翅")
        .description("在使用鞘翅飞行时将始终持有图腾。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> falling = sgTotem.add(new BoolSetting.Builder()
        .name("坠落")
        .description("如果坠落伤害会杀死你,你会持有图腾。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> explosion = sgTotem.add(new BoolSetting.Builder()
        .name("爆炸")
        .description("当爆炸伤害可能杀死你时,你会持有图腾。")
        .defaultValue(true)
        .build()
    );


    private boolean isClicking;
    private boolean sentMessage;

    private Item currentItem;
    public boolean locked;

    private int totems, ticks;

    public Offhand() {
        super(Categories.Combat, "副手", "允许你在副手上持有指定的物品。");
    }

    @Override
    public void onActivate() {
        ticks = 0;
        sentMessage = false;
        isClicking = false;
        currentItem = preferreditem.get();
    }

    @EventHandler(priority = HIGHEST + 999)
    private void onTick(TickEvent.Pre event) throws InterruptedException {
        FindItemResult result = InvUtils.find(Items.TOTEM_OF_UNDYING);
        totems = result.count();

        if (totems <= 0) locked = false;
        else if (ticks > delayTicks.get()) {
            boolean low = mc.player.getHealth() + mc.player.getAbsorptionAmount() - PlayerUtils.possibleHealthReductions(explosion.get(), falling.get()) <= minHealth.get();
            boolean ely = elytra.get() && mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.ELYTRA && mc.player.isFallFlying();
            FindItemResult item = InvUtils.find(itemStack -> itemStack.getItem() == currentItem.item, 0, 35);

            // Calculates Damage from Falling, Explosions + Elyta
            locked = (low || ely);

            if (locked && mc.player.getOffHandStack().getItem() != Items.TOTEM_OF_UNDYING) {
                InvUtils.move().from(result.slot()).toOffhand();
            }

            ticks = 0;
            return;
        }
        ticks++;

        AutoTotem autoTotem = Modules.get().get(AutoTotem.class);

        // Returns to the original Item
        currentItem = preferreditem.get();

        // Sword Gap & Right Gap
        if (rightgapple.get()) {
            if (!locked) {
                if (SwordGap.get() && mc.player.getMainHandStack().getItem() instanceof SwordItem) {
                    if (isClicking) {
                        currentItem = Item.EGap;
                    }
                }
                if (!SwordGap.get()) {
                    if (isClicking) {
                        currentItem = Item.EGap;
                    }
                }
            }
        }

        // Always Gap
        else if ((mc.player.getMainHandStack().getItem() instanceof SwordItem || mc.player.getMainHandStack().getItem() instanceof AxeItem) && alwaysSwordGap.get()) currentItem = Item.EGap;

            // Potion Click
        else if (potionClick.get()) {
            if (!locked) {
                if (mc.player.getMainHandStack().getItem() instanceof SwordItem) {
                    if (isClicking) {
                        currentItem = Item.Potion;
                    }
                }
            }
        }

        // Always Pot
        else if ((mc.player.getMainHandStack().getItem() instanceof SwordItem || mc.player.getMainHandStack().getItem() instanceof AxeItem) && alwaysPot.get()) currentItem = Item.Potion;


        else currentItem = preferreditem.get();

        // Checking offhand item
        if (mc.player.getOffHandStack().getItem() != currentItem.item) {
            if (ticks >= delayTicks.get()) {
                if (!locked) {
                    FindItemResult item = InvUtils.find(itemStack -> itemStack.getItem() == currentItem.item, hotbar.get() ? 0 : 9, 35);

                    // No offhand item
                    if (!item.found()) {
                        if (!sentMessage) {
                            warning("未找到所选物品。");
                            sentMessage = true;
                        }
                    }

                    // Swap to offhand
                    else if ((isClicking || !autoTotem.isLocked() && !item.isOffhand())) {
                        InvUtils.move().from(item.slot()).toOffhand();
                        sentMessage = false;
                    }
                    ticks = 0;
                    return;
                }
                ticks++;
            }
        }
    }

    @EventHandler
    private void onMouseButton(MouseButtonEvent event) {
        // Detects if the User is right-clicking
        isClicking = mc.currentScreen == null && !Modules.get().get(AutoTotem.class).isLocked() && !usableItem() && !mc.player.isUsingItem() && event.action == KeyAction.Press && event.button == GLFW_MOUSE_BUTTON_RIGHT;
    }

    private boolean usableItem() {
        // What counts as a Usable Item
        return mc.player.getMainHandStack().getItem() == Items.BOW
            || mc.player.getMainHandStack().getItem() == Items.TRIDENT
            || mc.player.getMainHandStack().getItem() == Items.CROSSBOW
            || mc.player.getMainHandStack().getItem().isFood();
    }

    @Override
    public String getInfoString() {
        return preferreditem.get().name();
    }

    public enum Item {
        // Items the module could put on your offhand
        EGap(Items.ENCHANTED_GOLDEN_APPLE),
        Gap(Items.GOLDEN_APPLE),
        Crystal(Items.END_CRYSTAL),
        Totem(Items.TOTEM_OF_UNDYING),
        Shield(Items.SHIELD),
        Potion(Items.POTION);
        net.minecraft.item.Item item;
        Item(net.minecraft.item.Item item) {
            this.item = item;
        }
    }

}
