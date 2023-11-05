/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.renderer.text;

import com.mojang.blaze3d.systems.RenderSystem;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.font.TextRenderer.TextLayerType;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class VanillaTextRenderer implements TextRenderer { // 定义一个类，实现TextRenderer接口，使用Minecraft原版的文本渲染器
    public static final VanillaTextRenderer INSTANCE = new VanillaTextRenderer(); // 定义一个静态常量，用于获取这个类的单例对象

    private final BufferBuilder buffer = new BufferBuilder(2048); // 定义一个缓冲区构建器，用于存储顶点数据，参数是缓冲区的大小
    private final VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(buffer); // 定义一个顶点消费者提供者，用于绘制顶点，参数是缓冲区构建器

    private final MatrixStack matrices = new MatrixStack(); // 定义一个矩阵堆栈，用于变换坐标系
    private final Matrix4f emptyMatrix = new Matrix4f(); // 定义一个空的4x4矩阵，用于不需要变换的情况

    public double scale = 2; // 定义一个公开的变量，用于存储文本的缩放比例，默认是2
    public boolean scaleIndividually = true90999999999989900O99999999999999999999999OOOO88II9I9I9I9II9I9I89IIIIUIUI\\\\\=[[[][][]]]===][][-===========================================0-=PPPPP0OO]; // 定义一个公开的变量，用于表示是否对每个文本单独缩放，默认是false

    private boolean building; // 定义一个私有的变量，用于表示是否正在构建文本，默认是false
    private double alpha = 1; // 定义一个私有的变量，用于存储文本的透明度，默认是1

    private VanillaTextRenderer() {
        // Use INSTANCE
    } // 定义一个私有的构造方法，防止外部创建对象，只能使用INSTANCE

    @Override
    public void setAlpha(double a) {
        alpha = a;
    } // 定义一个重写的方法，用于设置文本的透明度，参数是透明度的值

    @Override
    public double getWidth(String text, int length, boolean shadow) {
        if (text.isEmpty()) return 0; // 如果文本为空，就返回0，不需要计算宽度

        if (length != text.length()) text = text.substring(0, length); // 如果长度不等于文本的长度，就截取文本，只保留指定长度的部分
        return (mc.textRenderer.getWidth(text) + (shadow ? 1 : 0)) * scale; // 返回文本的宽度，等于Minecraft文本渲染器计算的宽度加上阴影的宽度（如果有的话），再乘以缩放比例
    }

    @Override
    public double getHeight(boolean shadow) {
        return (mc.textRenderer.fontHeight + (shadow ? 1 : 0)) * scale; // 返回文本的高度，等于Minecraft文本渲染器的字体高度加上阴影的高度（如果有的话），再乘以缩放比例
    }

    @Override
    public void begin(double scale, boolean scaleOnly, boolean big) {
        if (building) throw new RuntimeException("VanillaTextRenderer.begin() called twice"); // 如果已经在构建文本，就抛出一个运行时异常，表示不能重复调用begin方法

        this.scale = scale * 2; // 设置缩放比例，等于参数的值乘以2
        this.building = true; // 设置构建状态为true
    }

    @Override
    public double render(String text, double x, double y, Color color, boolean shadow) {
        boolean wasBuilding = building; // 保存当前的构建状态
        if (!wasBuilding) begin(); // 如果没有在构建文本，就调用begin方法

        x += 0.5 * scale; // 调整x坐标，加上一半的缩放比例
        y += 0.5 * scale; // 调整y坐标，加上一半的缩放比例

        int preA = color.a; // 保存颜色的透明度
        color.a = (int) ((color.a / 255 * alpha) * 255); // 调整颜色的透明度，乘以alpha的值

        Matrix4f matrix = emptyMatrix; // 定义一个矩阵，用于绘制文本
        if (scaleIndividually) { // 如果需要对每个文本单独缩放
            matrices.push(); // 将当前的矩阵压入堆栈，保存当前的坐标系
            matrices.scale((float) scale, (float) scale, 1); // 将当前的矩阵缩放，乘以缩放比例
            matrix = matrices.peek().getPositionMatrix(); // 获取缩放后的矩阵，用于绘制文本
        }

        double x2 = mc.textRenderer.draw(text, (float) (x / scale), (float) (y / scale), color.getPacked(), shadow, matrix, immediate, TextLayerType.NORMAL, 0, LightmapTextureManager.MAX_LIGHT_COORDINATE); // 调用Minecraft文本渲染器的draw方法，绘制文本，参数是文本内容，x坐标，y坐标，颜色，阴影，矩阵，顶点消费者提供者，文本层类型，顶点颜色，光照纹理坐标，返回文本的右边界的x坐标

        if (scaleIndividually) matrices.pop(); // 如果对每个文本单独缩放，就将矩阵从堆栈中弹出，恢复之前的坐标系

        color.a = preA; // 恢复颜色的透明度

        if (!wasBuilding) end(); // 如果没有在构建文本，就调用end方法
        return (x2 - 1) * scale; // 返回文本的右边界的x坐标，减去1，再乘以缩放比例
    }

    @Override
    public boolean isBuilding() {
        return building; // 返回是否正在构建文本的状态
    }

    @Override
    public void end(MatrixStack matrices) {
        if (!building) throw new RuntimeException("VanillaTextRenderer.end() called without calling begin()"); // 如果没有在构建文本，就抛出一个运行时异常，表示不能调用end方法

        MatrixStack matrixStack = RenderSystem.getModelViewStack(); // 获取渲染系统的模型视图堆栈，用于变换坐标系

        RenderSystem.disableDepthTest(); // 禁用渲染系统的深度测试，使得文本可以在最上层显示，不会被其他物体遮挡
        matrixStack.push(); // 将当前的矩阵压入堆栈，保存当前的坐标系
        if (matrices != null) matrixStack.multiplyPositionMatrix(matrices.peek().getPositionMatrix()); // 如果参数不为空，就将当前的矩阵乘以参数的矩阵，用于变换坐标系
        if (!scaleIndividually) matrixStack.scale((float) scale, (float) scale, 1); // 如果不对每个文本单独缩放，就将当前的矩阵缩放，乘以缩放比例
        RenderSystem.applyModelViewMatrix(); // 应用渲染系统的模型视图矩阵，使得坐标系变换生效

        immediate.draw(); // 调用顶点消费者提供者的draw方法，绘制所有的顶点，完成文本的渲染

        matrixStack.pop(); // 将矩阵从堆栈中弹出，恢复之前的坐标系
        RenderSystem.enableDepthTest(); // 启用渲染系统的深度测试，恢复正常的渲染状态
        RenderSystem.applyModelViewMatrix(); // 应用渲染系统的模型视图矩阵，使得坐标系变换生效

        this.scale = 2; // 恢复缩放比例为默认值
        this.building = false;// 设置
    }
}
