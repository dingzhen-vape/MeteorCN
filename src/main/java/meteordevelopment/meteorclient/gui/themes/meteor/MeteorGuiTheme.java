/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.gui.themes.meteor;

import meteordevelopment.meteorclient.gui.DefaultSettingsWidgetFactory;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WidgetScreen;
import meteordevelopment.meteorclient.gui.renderer.packer.GuiTexture;
import meteordevelopment.meteorclient.gui.themes.meteor.widgets.*;
import meteordevelopment.meteorclient.gui.themes.meteor.widgets.input.WMeteorDropdown;
import meteordevelopment.meteorclient.gui.themes.meteor.widgets.input.WMeteorSlider;
import meteordevelopment.meteorclient.gui.themes.meteor.widgets.input.WMeteorTextBox;
import meteordevelopment.meteorclient.gui.themes.meteor.widgets.pressable.*;
import meteordevelopment.meteorclient.gui.utils.AlignmentX;
import meteordevelopment.meteorclient.gui.utils.CharFilter;
import meteordevelopment.meteorclient.gui.widgets.*;
import meteordevelopment.meteorclient.gui.widgets.containers.WSection;
import meteordevelopment.meteorclient.gui.widgets.containers.WView;
import meteordevelopment.meteorclient.gui.widgets.containers.WWindow;
import meteordevelopment.meteorclient.gui.widgets.input.WDropdown;
import meteordevelopment.meteorclient.gui.widgets.input.WSlider;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.*;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.accounts.Account;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

import static meteordevelopment.meteorclient.MeteorClient.mc;
import static net.minecraft.client.MinecraftClient.IS_SYSTEM_MAC;

public class MeteorGuiTheme extends GuiTheme {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgColors = settings.createGroup("颜色");
    private final SettingGroup sgTextColors = settings.createGroup("文本");
    private final SettingGroup sgBackgroundColors = settings.createGroup("背景");
    private final SettingGroup sgOutline = settings.createGroup("轮廓");
    private final SettingGroup sgSeparator = settings.createGroup("分隔符");
    private final SettingGroup sgScrollbar = settings.createGroup("滚动条");
    private final SettingGroup sgSlider = settings.createGroup("滑块");
    private final SettingGroup sgStarscript = settings.createGroup("Starscript");

    // General

    public final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("比例")
        .description("GUI的比例。")
        .defaultValue(1)
        .min(0.75)
        .sliderRange(0.75, 4)
        .onSliderRelease()
        .onChanged(aDouble -> {
            if (mc.currentScreen instanceof WidgetScreen) ((WidgetScreen) mc.currentScreen).invalidate();
        })
        .build()
    );

    public final Setting<AlignmentX> moduleAlignment = sgGeneral.add(new EnumSetting.Builder<AlignmentX>()
        .name("模块对齐")
        .description("模块标题的对齐方式。")
        .defaultValue(AlignmentX.Center)
        .build()
    );

    public final Setting<Boolean> categoryIcons = sgGeneral.add(new BoolSetting.Builder()
        .name("类别图标")
        .description("为模块类别添加项目图标。")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> hideHUD = sgGeneral.add(new BoolSetting.Builder()
        .name("隐藏HUD")
        .description("在GUI中隐藏HUD。")
        .defaultValue(false)
        .onChanged(v -> {
            if (mc.currentScreen instanceof WidgetScreen) mc.options.hudHidden = v;
        })
        .build()
    );

    // Colors

    public final Setting<SettingColor> accentColor = color("强调色", "GUI的主要颜色。", new SettingColor(145, 61, 226));
    public final Setting<SettingColor> checkboxColor = color("复选框", "复选框的颜色。", new SettingColor(145, 61, 226));
    public final Setting<SettingColor> plusColor = color("加号", "加号按钮的颜色。", new SettingColor(50, 255, 50));
    public final Setting<SettingColor> minusColor = color("减号", "减号按钮的颜色。", new SettingColor(255, 50, 50));
    public final Setting<SettingColor> favoriteColor = color("收藏", "选中收藏按钮的颜色。", new SettingColor(250, 215, 0));

    // Text

    public final Setting<SettingColor> textColor = color(sgTextColors, "文本", "文本的颜色。", new SettingColor(255, 255, 255));
    public final Setting<SettingColor> textSecondaryColor = color(sgTextColors, "次要文本", "次要文本的颜色。", new SettingColor(150, 150, 150));
    public final Setting<SettingColor> textHighlightColor = color(sgTextColors, "文本高亮", "文本高亮的颜色。", new SettingColor(45, 125, 245, 100));
    public final Setting<SettingColor> titleTextColor = color(sgTextColors, "标题文本", "标题文本的颜色。", new SettingColor(255, 255, 255));
    public final Setting<SettingColor> loggedInColor = color(sgTextColors, "登录文本", "登录账户名称的颜色。", new SettingColor(45, 225, 45));
    public final Setting<SettingColor> placeholderColor = color(sgTextColors, "占位符", "占位符文本的颜色。", new SettingColor(255, 255, 255, 20));

    // Background

    public final ThreeStateColorSetting backgroundColor = new ThreeStateColorSetting(
            sgBackgroundColors,
            "背景",
            new SettingColor(20, 20, 20, 200),
            new SettingColor(30, 30, 30, 200),
            new SettingColor(40, 40, 40, 200)
    );

    public final Setting<SettingColor> moduleBackground = color(sgBackgroundColors, "模块背景", "模块激活时的背景颜色。", new SettingColor(50, 50, 50));

    // Outline

    public final ThreeStateColorSetting outlineColor = new ThreeStateColorSetting(
            sgOutline,
            "轮廓",
            new SettingColor(0, 0, 0),
            new SettingColor(10, 10, 10),
            new SettingColor(20, 20, 20)
    );

    // Separator

    public final Setting<SettingColor> separatorText = color(sgSeparator, "分隔符文本", "分隔符文本的颜色。", new SettingColor(255, 255, 255));
    public final Setting<SettingColor> separatorCenter = color(sgSeparator, "分隔符中心", "分隔符的中心颜色。", new SettingColor(255, 255, 255));
    public final Setting<SettingColor> separatorEdges = color(sgSeparator, "分隔符边缘", "分隔符边缘的颜色。", new SettingColor(225, 225, 225, 150));

    // Scrollbar

    public final ThreeStateColorSetting scrollbarColor = new ThreeStateColorSetting(
            sgScrollbar,
            "滚动条",
            new SettingColor(30, 30, 30, 200),
            new SettingColor(40, 40, 40, 200),
            new SettingColor(50, 50, 50, 200)
    );

    // Slider

    public final ThreeStateColorSetting sliderHandle = new ThreeStateColorSetting(
            sgSlider,
            "滑块手柄",
            new SettingColor(130, 0, 255),
            new SettingColor(140, 30, 255),
            new SettingColor(150, 60, 255)
    );

    public final Setting<SettingColor> sliderLeft = color(sgSlider, "滑块左侧", "滑块左侧部分的颜色。", new SettingColor(100,35,170));
    public final Setting<SettingColor> sliderRight = color(sgSlider, "滑块右侧", "滑块右侧部分的颜色。", new SettingColor(50, 50, 50));

    // Starscript

    private final Setting<SettingColor> starscriptText = color(sgStarscript, "Starscript文本", "Starscript代码中的文本颜色。", new SettingColor(169, 183, 198));
    private final Setting<SettingColor> starscriptBraces = color(sgStarscript, "Starscript大括号", "Starscript代码中的大括号颜色。", new SettingColor(150, 150, 150));
    private final Setting<SettingColor> starscriptParenthesis = color(sgStarscript, "Starscript括号", "Starscript代码中的括号颜色。", new SettingColor(169, 183, 198));
    private final Setting<SettingColor> starscriptDots = color(sgStarscript, "Starscript点", "Starscript代码中的点的颜色。", new SettingColor(169, 183, 198));
    private final Setting<SettingColor> starscriptCommas = color(sgStarscript, "Starscript逗号", "Starscript代码中的逗号颜色。", new SettingColor(169, 183, 198));
    private final Setting<SettingColor> starscriptOperators = color(sgStarscript, "Starscript操作符", "Starscript代码中的操作符颜色。", new SettingColor(169, 183, 198));
    private final Setting<SettingColor> starscriptStrings = color(sgStarscript, "Starscript字符串", "Starscript代码中的字符串颜色。", new SettingColor(106, 135, 89));
    private final Setting<SettingColor> starscriptNumbers = color(sgStarscript, "Starscript数字", "Starscript代码中的数字颜色。", new SettingColor(104, 141, 187));
    private final Setting<SettingColor> starscriptKeywords = color(sgStarscript, "Starscript关键字", "Starscript代码中的关键字颜色。", new SettingColor(204, 120, 50));
    private final Setting<SettingColor> starscriptAccessedObjects = color(sgStarscript, "Starscript访问对象", "Starscript代码中访问对象（点前）的颜色。", new SettingColor(152, 118, 170));

    public MeteorGuiTheme() {
        super("Meteor");

        settingsFactory = new DefaultSettingsWidgetFactory(this);
    }

    private Setting<SettingColor> color(SettingGroup group, String name, String description, SettingColor color) {
        return group.add(new ColorSetting.Builder()
                .name(name + "-颜色")
                .description(description)
                .defaultValue(color)
                .build());
    }
    private Setting<SettingColor> color(String name, String description, SettingColor color) {
        return color(sgColors, name, description, color);
    }

    // Widgets

    @Override
    public WWindow window(WWidget icon, String title) {
        return w(new WMeteorWindow(icon, title));
    }

    @Override
    public WLabel label(String text, boolean title, double maxWidth) {
        if (maxWidth == 0) return w(new WMeteorLabel(text, title));
        return w(new WMeteorMultiLabel(text, title, maxWidth));
    }

    @Override
    public WHorizontalSeparator horizontalSeparator(String text) {
        return w(new WMeteorHorizontalSeparator(text));
    }

    @Override
    public WVerticalSeparator verticalSeparator() {
        return w(new WMeteorVerticalSeparator());
    }

    @Override
    protected WButton button(String text, GuiTexture texture) {
        return w(new WMeteorButton(text, texture));
    }

    @Override
    public WMinus minus() {
        return w(new WMeteorMinus());
    }

    @Override
    public WPlus plus() {
        return w(new WMeteorPlus());
    }

    @Override
    public WCheckbox checkbox(boolean checked) {
        return w(new WMeteorCheckbox(checked));
    }

    @Override
    public WSlider slider(double value, double min, double max) {
        return w(new WMeteorSlider(value, min, max));
    }

    @Override
    public WTextBox textBox(String text, String placeholder, CharFilter filter, Class<? extends WTextBox.Renderer> renderer) {
        return w(new WMeteorTextBox(text, placeholder, filter, renderer));
    }

    @Override
    public <T> WDropdown<T> dropdown(T[] values, T value) {
        return w(new WMeteorDropdown<>(values, value));
    }

    @Override
    public WTriangle triangle() {
        return w(new WMeteorTriangle());
    }

    @Override
    public WTooltip tooltip(String text) {
        return w(new WMeteorTooltip(text));
    }

    @Override
    public WView view() {
        return w(new WMeteorView());
    }

    @Override
    public WSection section(String title, boolean expanded, WWidget headerWidget) {
        return w(new WMeteorSection(title, expanded, headerWidget));
    }

    @Override
    public WAccount account(WidgetScreen screen, Account<?> account) {
        return w(new WMeteorAccount(screen, account));
    }

    @Override
    public WWidget module(Module module) {
        return w(new WMeteorModule(module));
    }

    @Override
    public WQuad quad(Color color) {
        return w(new WMeteorQuad(color));
    }

    @Override
    public WTopBar topBar() {
        return w(new WMeteorTopBar());
    }

    @Override
    public WFavorite favorite(boolean checked) {
        return w(new WMeteorFavorite(checked));
    }

    // Colors

    @Override
    public Color textColor() {
        return textColor.get();
    }

    @Override
    public Color textSecondaryColor() {
        return textSecondaryColor.get();
    }

    //     Starscript

    @Override
    public Color starscriptTextColor() {
        return starscriptText.get();
    }

    @Override
    public Color starscriptBraceColor() {
        return starscriptBraces.get();
    }

    @Override
    public Color starscriptParenthesisColor() {
        return starscriptParenthesis.get();
    }

    @Override
    public Color starscriptDotColor() {
        return starscriptDots.get();
    }

    @Override
    public Color starscriptCommaColor() {
        return starscriptCommas.get();
    }

    @Override
    public Color starscriptOperatorColor() {
        return starscriptOperators.get();
    }

    @Override
    public Color starscriptStringColor() {
        return starscriptStrings.get();
    }

    @Override
    public Color starscriptNumberColor() {
        return starscriptNumbers.get();
    }

    @Override
    public Color starscriptKeywordColor() {
        return starscriptKeywords.get();
    }

    @Override
    public Color starscriptAccessedObjectColor() {
        return starscriptAccessedObjects.get();
    }

    // Other

    @Override
    public TextRenderer textRenderer() {
        return TextRenderer.get();
    }

    @Override
    public double scale(double value) {
        double scaled = value * scale.get();

        if (IS_SYSTEM_MAC) {
            scaled /= (double) mc.getWindow().getWidth() / mc.getWindow().getFramebufferWidth();
        }

        return scaled;
    }

    @Override
    public boolean categoryIcons() {
        return categoryIcons.get();
    }

    @Override
    public boolean hideHUD() {
        return hideHUD.get();
    }

    public class ThreeStateColorSetting {
        private final Setting<SettingColor> normal, hovered, pressed;

        public ThreeStateColorSetting(SettingGroup group, String name, SettingColor c1, SettingColor c2, SettingColor c3) {
            normal = color(group, name, "颜色" + name + "。", c1);
            hovered = color(group, "悬停-" + name, "颜色" + name + "悬停时的颜色。", c2);
            pressed = color(group, "按下-" + name, "颜色" + name + "按下时的颜色。", c3);
        }

        public SettingColor get() {
            return normal.get();
        }

        public SettingColor get(boolean pressed, boolean hovered, boolean bypassDisableHoverColor) {
            if (pressed) return this.pressed.get();
            return (hovered && (bypassDisableHoverColor || !disableHoverColor)) ? this.hovered.get() : this.normal.get();
        }

        public SettingColor get(boolean pressed, boolean hovered) {
            return get(pressed, hovered, false);
        }
    }
}
