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
import net.minecraft.component.DataComponentTypes;
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
        .description("槽位移动之间的刻延迟.")
        .defaultValue(0)
        .min(0)
        .sliderMax(20)
        .build()
    );
    private final Setting<Item> preferreditem = sgCombat.add(new EnumSetting.Builder<Item>()
        .name("物品")
        .description("要在副手持有的物品.")
        .defaultValue(Item.Crystal)
        .build()
    );

    private final Setting<Boolean> hotbar = sgCombat.add(new BoolSetting.Builder()
        .name("物品栏")
        .description("是否使用物品栏中的物品.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> rightgapple = sgCombat.add(new BoolSetting.Builder()
        .name("右键金苹果")
        .description("按住右键时切换到金苹果.(不要和药水开启时一起使用)")
        .defaultValue(false)
        .build()
    );


    private final Setting<Boolean> SwordGap = sgCombat.add(new BoolSetting.Builder()
        .name("剑金苹果")
        .description("拿着剑并按住右键时切换到金苹果.")
        .defaultValue(false)
        .visible(rightgapple::get)
        .build()
    );

    private final Setting<Boolean> alwaysSwordGap = sgCombat.add(new BoolSetting.Builder()
        .name("剑总是金苹果")
        .description("拿着剑时总是持有附魔金苹果.")
        .defaultValue(false)
        .visible(() -> !rightgapple.get())
        .build()
    );


    private final Setting<Boolean> alwaysPot = sgCombat.add(new BoolSetting.Builder()
        .name("剑总是药水")
        .description("拿着剑时切换到药水")
        .defaultValue(false)
        .visible(() -> !rightgapple.get() && !alwaysSwordGap.get())
        .build()
    );
    private final Setting<Boolean> potionClick = sgCombat.add(new BoolSetting.Builder()
        .name("剑药水")
        .description("拿着剑并按住右键时切换到药水.")
        .defaultValue(false)
        .visible(() -> !rightgapple.get() && !alwaysPot.get() && !alwaysSwordGap.get() )
        .build()
    );

    //Totem

    private final Setting<Double> minHealth = sgTotem.add(new DoubleSetting.Builder()
        .name("最低生命值")
        .description("低于这个生命值时持有图腾.")
        .defaultValue(10)
        .range(0,36)
        .sliderRange(0,36)
        .build()
    );

    private final Setting<Boolean> elytra = sgTotem.add(new BoolSetting.Builder()
        .name("鞘翅")
        .description("飞行时总是持有图腾.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> falling = sgTotem.add(new BoolSetting.Builder()
        .name("下落")
        .description("如果摔落伤害能杀死你，就持有图腾.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> explosion = sgTotem.add(new BoolSetting.Builder()
        .name("爆炸")
        .description("如果爆炸伤害能杀死你，就持有图腾.")
        .defaultValue(true)
        .build()
    );


    private boolean isClicking;
    private boolean sentMessage;

    private Item currentItem;
    public boolean locked;

    private int totems, ticks;

    public Offhand() {
        super(Categories.Combat, "副手", "让你在副手持有指定的物品.");
    }

    @Override
    public void onActivate() {
        ticks = 0;
        sentMessage = false;
        isClicking = false;
        currentItem = preferreditem.get();
    }

    @EventHandler(priority = HIGHEST + 999)
    private void onTick(TickEvent.Pre event) {
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
                            warning("没有找到选择的物品.");
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
            || mc.player.getMainHandStack().getItem().getComponents().contains(DataComponentTypes.FOOD);
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
        final net.minecraft.item.Item item;
        Item(net.minecraft.item.Item item) {
            this.item = item;
        }
    }

}
