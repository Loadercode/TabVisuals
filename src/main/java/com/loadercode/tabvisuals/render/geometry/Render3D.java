/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.render.geometry;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.UtilityClass;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShape;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.joml.Vector4i;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import com.loadercode.tabvisuals.render.interfaces.QuickImports;
import com.loadercode.tabvisuals.render.color.ColorAssist;
import com.loadercode.tabvisuals.render.math.calc.Calculate;
import com.loadercode.tabvisuals.render.math.projection.Projection;
import com.loadercode.tabvisuals.render.shader.Shader;

import java.util.HashMap;
import java.util.function.Consumer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.Random;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.stream.Collectors;

@UtilityClass
public class Render3D implements QuickImports {
    private final Map<VoxelShape, Pair<List<Box>, List<Line>>> SHAPE_OUTLINES = new HashMap<>();
    private final Map<VoxelShape, List<Box>> SHAPE_BOXES = new HashMap<>();
    public final List<Texture> TEXTURE_DEPTH = new ArrayList<>();
    public final List<Texture> TEXTURE = new ArrayList<>();
    public final List<Line> LINE_DEPTH = new ArrayList<>();
    public final List<Line> LINE = new ArrayList<>();
    public final List<Quad> QUAD_DEPTH = new ArrayList<>();
    public final List<Quad> QUAD = new ArrayList<>();
    @Setter public Matrix4f lastProjMat = new Matrix4f();
    @Setter public MatrixStack.Entry lastWorldSpaceMatrix = new MatrixStack().peek();

    /**
     * Сглаживание линий (GL_LINE_SMOOTH). CustomHitBox и др. могут включать через setLineSmooth(true).
     *
     * <p>Флаг живёт ровно один кадр: он сбрасывается в конце {@link #onWorldRender},
     * иначе настройка одного модуля «протекала» бы на линии всех остальных навсегда
     * (после выключения CustomHitBox сглаживание оставалось выключенным).
     */
    private static volatile boolean lineSmoothEnabled = true;
    public static void setLineSmooth(boolean enabled) { lineSmoothEnabled = enabled; }

    private static BufferBuilder batchBuffer = null;
    @Getter
    private static Identifier batchTexture = null;


    public static Vec3d toRelative(Vec3d worldPos) {
        Vec3d camPos = mc.gameRenderer.getCamera().getPos();
        return new Vec3d(worldPos.x - camPos.x, worldPos.y - camPos.y, worldPos.z - camPos.z);
    }


    public void beginBillboardBatch(Identifier texture) {
        if (batchBuffer != null) {
            endBillboardBatch();
        }
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);

        batchTexture = texture;
        batchBuffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
    }

    public void batchBillboard(MatrixStack matrices, Vec3d pos, float size, int color) {
        if (batchBuffer == null) return;

        matrices.push();
        Vec3d rel = toRelative(pos);
        matrices.translate((float)rel.x, (float)rel.y, (float)rel.z);

        Camera camera = mc.gameRenderer.getCamera();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float halfSize = size / 2f;

        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = ((color >> 24) & 0xFF) / 255f;

        batchBuffer.vertex(matrix, -halfSize, -halfSize, 0).texture(0, 1).color(r, g, b, a);
        batchBuffer.vertex(matrix,  halfSize, -halfSize, 0).texture(1, 1).color(r, g, b, a);
        batchBuffer.vertex(matrix,  halfSize,  halfSize, 0).texture(1, 0).color(r, g, b, a);
        batchBuffer.vertex(matrix, -halfSize,  halfSize, 0).texture(0, 0).color(r, g, b, a);

        matrices.pop();
    }

    public void endBillboardBatch() {
        if (batchBuffer != null) {
            BufferRenderer.drawWithGlobalProgram(batchBuffer.end());
            batchBuffer = null;
            batchTexture = null;
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
        }
    }

    public static void setBatchTexture(Identifier batchTexture) {
        Render3D.batchTexture = batchTexture;
    }

    public static class Crystal {
        private final Vec3d position;
        private final Vec3d rotation;
        private final float size;
        private final float rotationSpeed;

        public Crystal(Entity entity, Vec3d position, Vec3d rotation) {
            this.position = position;
            this.rotation = rotation;
            this.size = 0.09f;
            this.rotationSpeed = 0.5f + (float)(Math.random() * 1.5f);
        }

        public void render(MatrixStack ms) {
            w(ms, toRelative(position), rotationSpeed, rotation);
            int baseColor = ColorAssist.fade(90);
            RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
            drawCrystal(ms, baseColor, 0.2f, true);
            RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);
            drawCrystal(ms, baseColor, 0.3f, true);
            drawCrystal(ms, baseColor, 0.8f, false);
            RenderSystem.disableBlend();
            RenderSystem.enableCull();
            ms.pop();
        }

        public static void w(MatrixStack ms, Vec3d position, float rotationSpeed, Vec3d rotation) {
            ms.push();
            ms.translate((float)position.x, (float)position.y, (float)position.z);
            float pulsation = 1.0f + (float) (Math.sin(System.currentTimeMillis() / 500.0) * 0.1f);
            ms.scale(pulsation, pulsation, pulsation);
            float selfRotation = (System.currentTimeMillis() % 36000) / 100.0f * rotationSpeed;
            ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees((float) rotation.x));
            ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) rotation.y + selfRotation));
            ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) rotation.z));
            RenderSystem.disableCull();
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);
            RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        }

        private void drawCrystal(MatrixStack ms, int baseColor, float alpha, boolean filled) {
            BufferBuilder bufferBuilder = Tessellator.getInstance().begin(
                    filled ? VertexFormat.DrawMode.TRIANGLES : VertexFormat.DrawMode.DEBUG_LINES,
                    VertexFormats.POSITION_COLOR
            );
            float s = size;
            float h_prism = size;
            float h_pyramid = size * 1.5f;
            int numSides = 8;
            List<Vec3d> topVertices = new ArrayList<>();
            List<Vec3d> bottomVertices = new ArrayList<>();
            for (int i = 0; i < numSides; i++) {
                float angle = (float) (2 * Math.PI * i / numSides);
                float x = (float) (s * Math.cos(angle));
                float z = (float) (s * Math.sin(angle));
                topVertices.add(new Vec3d(x, h_prism / 2, z));
                bottomVertices.add(new Vec3d(x, -h_prism / 2, z));
            }
            Vec3d vTop    = new Vec3d(0,  h_prism / 2 + h_pyramid, 0);
            Vec3d vBottom = new Vec3d(0, -h_prism / 2 - h_pyramid, 0);
            int finalColor = ColorAssist.setAlpha(baseColor, (int) (alpha * 255));
            for (int i = 0; i < numSides; i++) {
                Vec3d v1 = bottomVertices.get(i);
                Vec3d v2 = bottomVertices.get((i + 1) % numSides);
                Vec3d v3 = topVertices.get((i + 1) % numSides);
                Vec3d v4 = topVertices.get(i);
                drawQuad(ms, bufferBuilder, v1, v2, v3, v4, finalColor, filled);
            }
            for (int i = 0; i < numSides; i++) {
                Vec3d v1 = topVertices.get(i);
                Vec3d v2 = topVertices.get((i + 1) % numSides);
                drawTriangle(ms, bufferBuilder, vTop, v1, v2, finalColor, filled);
            }
            for (int i = 0; i < numSides; i++) {
                Vec3d v1 = bottomVertices.get(i);
                Vec3d v2 = bottomVertices.get((i + 1) % numSides);
                drawTriangle(ms, bufferBuilder, vBottom, v2, v1, finalColor, filled);
            }
            BufferRenderer.drawWithGlobalProgram(bufferBuilder.end());
        }

        private void drawTriangle(MatrixStack ms, BufferBuilder bb, Vec3d v1, Vec3d v2, Vec3d v3, int color, boolean filled) {
            if (filled) {
                a(ms, bb, v1, v2, v3, color);
            } else {
                s(ms, bb, v1, v2, v3, v1, color);
            }
        }

        public static void a(MatrixStack ms, BufferBuilder bb, Vec3d v1, Vec3d v2, Vec3d v3, int color) {
            bb.vertex(ms.peek().getPositionMatrix(), (float)v1.x, (float)v1.y, (float)v1.z).color(color);
            bb.vertex(ms.peek().getPositionMatrix(), (float)v2.x, (float)v2.y, (float)v2.z).color(color);
            bb.vertex(ms.peek().getPositionMatrix(), (float)v3.x, (float)v3.y, (float)v3.z).color(color);
        }

        private void drawQuad(MatrixStack ms, BufferBuilder bb, Vec3d v1, Vec3d v2, Vec3d v3, Vec3d v4, int color, boolean filled) {
            if (filled) {
                drawTriangle(ms, bb, v1, v2, v3, color, true);
                drawTriangle(ms, bb, v1, v3, v4, color, true);
            } else {
                s(ms, bb, v1, v2, v3, v4, color);
                bb.vertex(ms.peek().getPositionMatrix(), (float)v4.x, (float)v4.y, (float)v4.z).color(color);
                bb.vertex(ms.peek().getPositionMatrix(), (float)v1.x, (float)v1.y, (float)v1.z).color(color);
            }
        }

        private static void s(MatrixStack ms, BufferBuilder bb, Vec3d v1, Vec3d v2, Vec3d v3, Vec3d v4, int color) {
            a(ms, bb, v1, v2, v2, color);
            a(ms, bb, v3, v3, v4, color);
        }
    }

    public void drawEntity(Entity entity, Vec3d pos, float yaw, int alpha, MatrixStack matrices, float tickDelta) {
        if (!(entity instanceof LivingEntity livingEntity)) return;
        matrices.push();
        Vec3d rel = toRelative(pos);
        matrices.translate(rel.x, rel.y, rel.z);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
        matrices.scale(1.0f, 1.0f, 1.0f);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.enableDepthTest();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha / 255.0F);
        EntityRenderer renderer = mc.getEntityRenderDispatcher().getRenderer(entity);
        if (renderer != null) {
            int light = renderer.getLight(livingEntity, tickDelta);
            VertexConsumerProvider.Immediate vertexConsumers = mc.getBufferBuilders().getEntityVertexConsumers();
            EntityRenderState renderState = renderer.getAndUpdateRenderState(livingEntity, tickDelta);
            if (renderState != null) {
                renderer.render(renderState, matrices, vertexConsumers, light);
            }
            vertexConsumers.draw();
        }
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
        matrices.pop();
    }

    /** Очистка всех списков рендера при раннем выходе — иначе старые данные «плывут» на след. кадре. */
    public void clearAllRenderLists() {
        TEXTURE.clear();
        TEXTURE_DEPTH.clear();
        LINE.clear();
        LINE_DEPTH.clear();
        QUAD.clear();
        QUAD_DEPTH.clear();
    }

    public void onWorldRender() {
        if (mc == null || mc.world == null || mc.worldRenderer == null) {
            clearAllRenderLists();
            return;
        }
        Camera camera = mc.gameRenderer.getCamera();
        if (camera == null || mc.worldRenderer.frustum == null) {
            clearAllRenderLists();
            return;
        }

        if (mc.getFramebuffer() != null) {
            try {
                mc.getFramebuffer().beginWrite(true);
            } catch (Exception ignored) {}
        }

        if (!TEXTURE.isEmpty()) {
            try {
                Map<Identifier, List<Texture>> groups = groupTextures(TEXTURE);
                RenderSystem.enableBlend();
                RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_CONSTANT_ALPHA);
                for (Map.Entry<Identifier, List<Texture>> group : groups.entrySet()) {
                    RenderSystem.setShaderTexture(0, group.getKey());
                    RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
                    BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
                    for (Texture tex : group.getValue()) {
                        quadTexture(tex.entry, buffer, tex.x, tex.y, tex.width, tex.height, tex.color);
                    }
                    BufferRenderer.drawWithGlobalProgram(buffer.end());
                }
            } finally {
                RenderSystem.disableBlend();
            }
            TEXTURE.clear();
        }

        if (!TEXTURE_DEPTH.isEmpty()) {
            try {
                Map<Identifier, List<Texture>> groups = groupTextures(TEXTURE_DEPTH);
                RenderSystem.enableBlend();
                RenderSystem.enableDepthTest();
                RenderSystem.depthFunc(GL11.GL_LEQUAL);
                RenderSystem.depthMask(false);
                RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_CONSTANT_ALPHA);
                for (Map.Entry<Identifier, List<Texture>> group : groups.entrySet()) {
                    RenderSystem.setShaderTexture(0, group.getKey());
                    RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
                    BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
                    for (Texture tex : group.getValue()) {
                        quadTexture(tex.entry, buffer, tex.x, tex.y, tex.width, tex.height, tex.color);
                    }
                    BufferRenderer.drawWithGlobalProgram(buffer.end());
                }
            } finally {
                RenderSystem.depthMask(true);
                RenderSystem.disableBlend();
            }
            TEXTURE_DEPTH.clear();
        }

        if (!LINE.isEmpty()) {
            try {
                if (lineSmoothEnabled) GL11.glEnable(GL11.GL_LINE_SMOOTH);
                Set<Float> widths = LINE.stream().map(line -> line.width).collect(Collectors.toCollection(LinkedHashSet::new));
                RenderSystem.enableBlend();
                RenderSystem.disableCull();
                RenderSystem.disableDepthTest();
                RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_CONSTANT_ALPHA);
                RenderSystem.setShader(ShaderProgramKeys.RENDERTYPE_LINES);
                widths.forEach(width -> {
                    RenderSystem.lineWidth(width);
                    BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.LINES, VertexFormats.LINES);
                    LINE.stream().filter(line -> line.width == width).forEach(line ->
                            vertexLineRaw(line.entry, buffer, line.start.toVector3f(), line.end.toVector3f(), line.colorStart, line.colorEnd));
                    BufferRenderer.drawWithGlobalProgram(buffer.end());
                });
            } finally {
                RenderSystem.lineWidth(1f);
                RenderSystem.enableDepthTest();
                RenderSystem.enableCull();
                RenderSystem.disableBlend();
                if (lineSmoothEnabled) GL11.glDisable(GL11.GL_LINE_SMOOTH);
            }
            LINE.clear();
        }

        if (!QUAD.isEmpty()) {
            try {
                RenderSystem.enableBlend();
                RenderSystem.disableCull();
                RenderSystem.disableDepthTest();
                RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_CONSTANT_ALPHA);
                RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
                BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
                QUAD.forEach(quad -> vertexQuadRaw(quad.entry, buffer, quad.x.toVector3f(), quad.y.toVector3f(), quad.w.toVector3f(), quad.z.toVector3f(), quad.color));
                BufferRenderer.drawWithGlobalProgram(buffer.end());
            } finally {
                RenderSystem.enableDepthTest();
                RenderSystem.enableCull();
                RenderSystem.disableBlend();
            }
            QUAD.clear();
        }

        if (!LINE_DEPTH.isEmpty()) {
            try {
                if (lineSmoothEnabled) GL11.glEnable(GL11.GL_LINE_SMOOTH);
                Set<Float> widths = LINE_DEPTH.stream().map(line -> line.width).collect(Collectors.toCollection(LinkedHashSet::new));
                RenderSystem.enableBlend();
                RenderSystem.disableCull();
                RenderSystem.enableDepthTest();
                RenderSystem.depthFunc(GL11.GL_LEQUAL);
                RenderSystem.depthMask(false);
                RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_CONSTANT_ALPHA);
                RenderSystem.setShader(ShaderProgramKeys.RENDERTYPE_LINES);
                widths.forEach(width -> {
                    RenderSystem.lineWidth(width);
                    BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.LINES, VertexFormats.LINES);
                    LINE_DEPTH.stream().filter(line -> line.width == width).forEach(line ->
                            vertexLineRaw(line.entry, buffer, line.start.toVector3f(), line.end.toVector3f(), line.colorStart, line.colorEnd));
                    BufferRenderer.drawWithGlobalProgram(buffer.end());
                });
            } finally {
                RenderSystem.lineWidth(1f);
                RenderSystem.depthMask(true);
                RenderSystem.enableCull();
                RenderSystem.disableBlend();
                if (lineSmoothEnabled) GL11.glDisable(GL11.GL_LINE_SMOOTH);
            }
            LINE_DEPTH.clear();
        }

        if (!QUAD_DEPTH.isEmpty()) {
            try {
                RenderSystem.enableBlend();
                RenderSystem.disableCull();
                RenderSystem.enableDepthTest();
                RenderSystem.depthFunc(GL11.GL_LEQUAL);
                RenderSystem.depthMask(false);
                RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_CONSTANT_ALPHA);
                RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
                BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
                QUAD_DEPTH.forEach(quad -> vertexQuadRaw(quad.entry, buffer, quad.x.toVector3f(), quad.y.toVector3f(), quad.w.toVector3f(), quad.z.toVector3f(), quad.color));
                BufferRenderer.drawWithGlobalProgram(buffer.end());
            } finally {
                RenderSystem.depthMask(true);
                RenderSystem.enableCull();
                RenderSystem.disableBlend();
            }
            QUAD_DEPTH.clear();
        }

        runPostWorldRenderCallbacks();

        // См. setLineSmooth: флаг действует только на flush этого кадра.
        lineSmoothEnabled = true;
    }

    private static final List<Runnable> postWorldRenderCallbacks = new ArrayList<>();
    public static void addPostWorldRenderCallback(Runnable r) { postWorldRenderCallbacks.add(r); }
    private static void runPostWorldRenderCallbacks() {
        for (Runnable r : new ArrayList<>(postWorldRenderCallbacks)) r.run();
    }

    public void drawShape(BlockPos blockPos, VoxelShape voxelShape, int color, float width) {
        drawShape(blockPos, voxelShape, color, width, true, false);
    }

    public void drawShape(BlockPos blockPos, VoxelShape voxelShape, int color, float width, boolean fill, boolean depth) {
        if (SHAPE_BOXES.containsKey(voxelShape)) {
            SHAPE_BOXES.get(voxelShape).forEach(box -> {
                box = box.offset(blockPos);
                if (Projection.canSee(box)) drawBox(box, color, width, true, fill, depth);
            });
            return;
        }
        SHAPE_BOXES.put(voxelShape, voxelShape.getBoundingBoxes());
    }

    public void drawShapeAlternative(BlockPos blockPos, VoxelShape voxelShape, int color, float width, boolean fill, boolean depth) {
        drawShapeAlternative(blockPos, voxelShape, color, width, fill, 0.1f, depth);
    }

    public void drawShapeAlternative(BlockPos blockPos, VoxelShape voxelShape, int color, float width, boolean fill, float fillAlpha, boolean depth) {
        drawShapeAlternative(blockPos, voxelShape, color, color, width, fill, fillAlpha, depth);
    }

    /** Контур и заливка могут использовать разные цвета */
    public void drawShapeAlternative(BlockPos blockPos, VoxelShape voxelShape, int outlineColor, int fillColor, float width, boolean fill, float fillAlpha, boolean depth) {
        Vec3d vec3d = Vec3d.of(blockPos);
        if (Projection.canSee(new Box(blockPos))) {
            if (SHAPE_OUTLINES.containsKey(voxelShape)) {
                Pair<List<Box>, List<Line>> pair = SHAPE_OUTLINES.get(voxelShape);
                if (fill) pair.getLeft().forEach(box -> drawBox(box.offset(vec3d), outlineColor, fillColor, width, false, true, fillAlpha, depth));
                pair.getRight().forEach(line -> drawLine(line.start.add(vec3d), line.end.add(vec3d), outlineColor, width, depth));
                return;
            }
            List<Line> lines = new ArrayList<>();
            voxelShape.forEachEdge((minX, minY, minZ, maxX, maxY, maxZ) ->
                    lines.add(new Line(null, new Vec3d(minX, minY, minZ), new Vec3d(maxX, maxY, maxZ), 0, 0, 0)));
            SHAPE_OUTLINES.put(voxelShape, new Pair<>(voxelShape.getBoundingBoxes(), lines));
            Pair<List<Box>, List<Line>> pair = SHAPE_OUTLINES.get(voxelShape);
            if (fill) pair.getLeft().forEach(box -> drawBox(box.offset(vec3d), outlineColor, fillColor, width, false, true, fillAlpha, depth));
            pair.getRight().forEach(line -> drawLine(line.start.add(vec3d), line.end.add(vec3d), outlineColor, width, depth));
        }
    }
    private static int overlayFillVao = -1;
    private static int overlayFillVbo = -1;
    private static final int MAX_OVERLAY_VERTICES = 256 * 3;
    private static final float[] overlayFillVertexBuf = new float[MAX_OVERLAY_VERTICES];
    private static FloatBuffer overlayFillDirectBuf;
    private static final Vector4f overlayFillVec = new Vector4f();

    public void drawShapeFillWithShader(BlockPos blockPos, VoxelShape voxelShape, Shader shader, Consumer<Shader> setUniforms, boolean depth) {
        if (!Projection.canSee(new Box(blockPos))) return;
        Vec3d camPos = mc.gameRenderer.getCamera().getPos();
        Vec3d offset = new Vec3d(blockPos.getX() - camPos.x, blockPos.getY() - camPos.y, blockPos.getZ() - camPos.z);
        MatrixStack.Entry entry = lastWorldSpaceMatrix;
        List<Box> boxes = voxelShape.getBoundingBoxes();
        if (boxes.isEmpty()) return;
        Matrix4f posMat = entry.getPositionMatrix();
        int n = 0;
        for (Box box : boxes) {
            box = box.offset(offset).expand(1e-3);
            double x1 = box.minX, y1 = box.minY, z1 = box.minZ;
            double x2 = box.maxX, y2 = box.maxY, z2 = box.maxZ;
            if (n + 6 * 18 > MAX_OVERLAY_VERTICES) break;
            n = putViewTriQuad(posMat, n, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2);
            n = putViewTriQuad(posMat, n, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1);
            n = putViewTriQuad(posMat, n, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2);
            n = putViewTriQuad(posMat, n, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2);
            n = putViewTriQuad(posMat, n, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1);
            n = putViewTriQuad(posMat, n, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1);
        }
        if (n == 0) return;
        if (overlayFillVao == -1) {
            overlayFillVao = GL30.glGenVertexArrays();
            overlayFillVbo = GL30.glGenBuffers();
            overlayFillDirectBuf = ByteBuffer.allocateDirect(MAX_OVERLAY_VERTICES * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
            GL30.glBindVertexArray(overlayFillVao);
            GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, overlayFillVbo);
            GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 0, 0);
            GL20.glEnableVertexAttribArray(0);
            GL30.glBindVertexArray(0);
        }
        int prevVao     = GL30.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int prevVbo     = GL30.glGetInteger(GL30.GL_ARRAY_BUFFER_BINDING);
        int prevProgram = GL30.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        if (depth) RenderSystem.depthMask(false);
        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);
        shader.bind();
        shader.setUniformMatrix4f("ProjMat", lastProjMat);
        if (setUniforms != null) setUniforms.accept(shader);
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, overlayFillVbo);
        GL30.glBufferData(GL30.GL_ARRAY_BUFFER, n * 4L, GL30.GL_STREAM_DRAW);
        overlayFillDirectBuf.clear();
        overlayFillDirectBuf.put(overlayFillVertexBuf, 0, n);
        overlayFillDirectBuf.flip();
        GL30.glBufferSubData(GL30.GL_ARRAY_BUFFER, 0, overlayFillDirectBuf);
        GL30.glBindVertexArray(overlayFillVao);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, n / 3);
        GL30.glBindVertexArray(prevVao);
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, prevVbo);
        GL20.glUseProgram(prevProgram);
        if (depth) RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    /** Квад как два треугольника (GL_QUADS отсутствует в ядре OpenGL 3.3): v0,v1,v2 и v0,v2,v3 */
    private static int putViewTriQuad(Matrix4f posMat, int offset,
                                      double x1, double y1, double z1,
                                      double x2, double y2, double z2,
                                      double x3, double y3, double z3,
                                      double x4, double y4, double z4) {
        putViewVertex(posMat, offset,      x1, y1, z1);
        putViewVertex(posMat, offset + 3,  x2, y2, z2);
        putViewVertex(posMat, offset + 6,  x3, y3, z3);
        putViewVertex(posMat, offset + 9,  x1, y1, z1);
        putViewVertex(posMat, offset + 12, x3, y3, z3);
        putViewVertex(posMat, offset + 15, x4, y4, z4);
        return offset + 18;
    }

    private static void putViewVertex(Matrix4f posMat, int offset, double wx, double wy, double wz) {
        overlayFillVec.set((float) wx, (float) wy, (float) wz, 1f).mul(posMat);
        Render3D.overlayFillVertexBuf[offset]     = overlayFillVec.x;
        Render3D.overlayFillVertexBuf[offset + 1] = overlayFillVec.y;
        Render3D.overlayFillVertexBuf[offset + 2] = overlayFillVec.z;
    }

    public void drawBox(Box box, int color, float width) {
        drawBox(box, color, width, true, true, false);
    }

    public void drawBox(Box box, int color, float width, boolean line, boolean fill, boolean depth) {
        drawBox(null, box, color, width, line, fill, 0.1f, depth);
    }

    public void drawBox(Box box, int color, float width, boolean line, boolean fill, float fillAlpha, boolean depth) {
        drawBox(null, box, color, color, width, line, fill, fillAlpha, depth);
    }

    public void drawBox(Box box, int outlineColor, int fillColor, float width, boolean line, boolean fill, float fillAlpha, boolean depth) {
        drawBox(null, box, outlineColor, fillColor, width, line, fill, fillAlpha, depth);
    }

    public void drawBox(MatrixStack.Entry entry, Box box, int color, float width, boolean line, boolean fill, boolean depth) {
        drawBox(entry, box, color, width, line, fill, 0.1f, depth);
    }

    public void drawBox(MatrixStack.Entry entry, Box box, int color, float width, boolean line, boolean fill, float fillAlpha, boolean depth) {
        drawBox(entry, box, color, color, width, line, fill, fillAlpha, depth);
    }

    public void drawBox(MatrixStack.Entry entry, Box box, int outlineColor, int fillColor, float width, boolean line, boolean fill, float fillAlpha, boolean depth) {
        box = box.expand(1e-3);
        double x1 = box.minX, y1 = box.minY, z1 = box.minZ;
        double x2 = box.maxX, y2 = box.maxY, z2 = box.maxZ;
        if (fill) {
            int fillColorApplied = ColorAssist.multAlpha(fillColor, fillAlpha);
            drawQuad(entry, new Vec3d(x1,y1,z1), new Vec3d(x2,y1,z1), new Vec3d(x2,y1,z2), new Vec3d(x1,y1,z2), fillColorApplied, depth);
            drawQuad(entry, new Vec3d(x1,y1,z1), new Vec3d(x1,y2,z1), new Vec3d(x2,y2,z1), new Vec3d(x2,y1,z1), fillColorApplied, depth);
            drawQuad(entry, new Vec3d(x2,y1,z1), new Vec3d(x2,y2,z1), new Vec3d(x2,y2,z2), new Vec3d(x2,y1,z2), fillColorApplied, depth);
            drawQuad(entry, new Vec3d(x1,y1,z2), new Vec3d(x2,y1,z2), new Vec3d(x2,y2,z2), new Vec3d(x1,y2,z2), fillColorApplied, depth);
            drawQuad(entry, new Vec3d(x1,y1,z1), new Vec3d(x1,y1,z2), new Vec3d(x1,y2,z2), new Vec3d(x1,y2,z1), fillColorApplied, depth);
            drawQuad(entry, new Vec3d(x1,y2,z1), new Vec3d(x1,y2,z2), new Vec3d(x2,y2,z2), new Vec3d(x2,y2,z1), fillColorApplied, depth);
        }
        if (line) {
            drawLine(entry, x1,y1,z1, x2,y1,z1, outlineColor, width, depth);
            drawLine(entry, x2,y1,z1, x2,y1,z2, outlineColor, width, depth);
            drawLine(entry, x2,y1,z2, x1,y1,z2, outlineColor, width, depth);
            drawLine(entry, x1,y1,z2, x1,y1,z1, outlineColor, width, depth);
            drawLine(entry, x1,y1,z2, x1,y2,z2, outlineColor, width, depth);
            drawLine(entry, x1,y1,z1, x1,y2,z1, outlineColor, width, depth);
            drawLine(entry, x2,y1,z2, x2,y2,z2, outlineColor, width, depth);
            drawLine(entry, x2,y1,z1, x2,y2,z1, outlineColor, width, depth);
            drawLine(entry, x1,y2,z1, x2,y2,z1, outlineColor, width, depth);
            drawLine(entry, x2,y2,z1, x2,y2,z2, outlineColor, width, depth);
            drawLine(entry, x2,y2,z2, x1,y2,z2, outlineColor, width, depth);
            drawLine(entry, x1,y2,z2, x1,y2,z1, outlineColor, width, depth);
        }
    }

    /**
     * Немедленный рендер линии в существующий BufferBuilder.
     * Принимает абсолютные мировые координаты — toRelative применяется внутри.
     */
    public void vertexLine(MatrixStack matrices, VertexConsumer buffer, Vec3d start, Vec3d end, int startColor, int endColor) {
        Vector3f relStart = toRelative(start).toVector3f();
        Vector3f relEnd   = toRelative(end).toVector3f();
        vertexLineRaw(matrices.peek(), buffer, relStart, relEnd, startColor, endColor);
    }
    /**
     * Немедленный рендер quad в существующий BufferBuilder.
     * Принимает абсолютные мировые координаты — toRelative применяется внутри.
     */
    public void vertexQuad(MatrixStack.Entry entry, VertexConsumer buffer, Vec3d vec1, Vec3d vec2, Vec3d vec3, Vec3d vec4, int color) {
        Vector3f v1 = toRelative(vec1).toVector3f();
        Vector3f v2 = toRelative(vec2).toVector3f();
        Vector3f v3 = toRelative(vec3).toVector3f();
        Vector3f v4 = toRelative(vec4).toVector3f();
        MatrixStack tempStack = new MatrixStack();
        tempStack.peek().getNormalMatrix().set(entry.getNormalMatrix());
        tempStack.peek().getPositionMatrix().set(entry.getPositionMatrix());
        tempStack.peek().getPositionMatrix().translation(0, 0, 0);
        vertexQuadRaw(tempStack.peek(), buffer, v1, v2, v3, v4, color);
    }

    // Переиспользуемые временные векторы. Раньше на каждый примитив приходилось
    // 3–5 объектов Vector3f (копия + поворот + нормаль); ESP, хитбоксы и линии
    // кладут в буфер тысячи таких примитивов за кадр. Методы вызываются только
    // из рендер-потока, поэтому одного набора хватает всем.
    private static final Vector3f CAMERA_SPACE = new Vector3f();

    private static void toCameraSpace(Vector3f rel, Vector3f out) {
        Camera camera = mc.gameRenderer.getCamera();
        float pitch = (float) Math.toRadians(camera.getPitch());
        float yaw   = (float) Math.toRadians(camera.getYaw() + 180f);
        out.set(rel).rotateY(yaw).rotateX(pitch);
    }

    private void vertexLineRaw(MatrixStack.Entry entry, VertexConsumer buffer, Vector3f start, Vector3f end, int startColor, int endColor) {
        // s и e — это один и тот же переиспользуемый вектор, поэтому после
        // второго toCameraSpace значения первого читаем уже в скалярах.
        toCameraSpace(start, CAMERA_SPACE);
        float sx = CAMERA_SPACE.x;
        float sy = CAMERA_SPACE.y;
        float sz = CAMERA_SPACE.z;
        toCameraSpace(end, CAMERA_SPACE);
        float ex = CAMERA_SPACE.x;
        float ey = CAMERA_SPACE.y;
        float ez = CAMERA_SPACE.z;

        float nx = sx - ex;
        float ny = sy - ey;
        float nz = sz - ez;
        float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length > 1.0E-6f) {
            nx /= length;
            ny /= length;
            nz /= length;
        }
        buffer.vertex(sx, sy, sz).color(startColor).normal(nx, ny, nz);
        buffer.vertex(ex, ey, ez).color(endColor).normal(nx, ny, nz);
    }

    private void vertexQuadRaw(MatrixStack.Entry entry, VertexConsumer buffer, Vector3f v1, Vector3f v2, Vector3f v3, Vector3f v4, int color) {
        toCameraSpace(v1, CAMERA_SPACE);
        buffer.vertex(CAMERA_SPACE.x, CAMERA_SPACE.y, CAMERA_SPACE.z).color(color);
        toCameraSpace(v2, CAMERA_SPACE);
        buffer.vertex(CAMERA_SPACE.x, CAMERA_SPACE.y, CAMERA_SPACE.z).color(color);
        toCameraSpace(v3, CAMERA_SPACE);
        buffer.vertex(CAMERA_SPACE.x, CAMERA_SPACE.y, CAMERA_SPACE.z).color(color);
        toCameraSpace(v4, CAMERA_SPACE);
        buffer.vertex(CAMERA_SPACE.x, CAMERA_SPACE.y, CAMERA_SPACE.z).color(color);
    }

    public void drawLine(MatrixStack.Entry entry, double minX, double minY, double minZ,
                         double maxX, double maxY, double maxZ, int color, float width, boolean depth) {
        drawLine(entry, new Vec3d(minX, minY, minZ), new Vec3d(maxX, maxY, maxZ), color, color, width, depth);
    }

    public void drawLine(Vec3d start, Vec3d end, int color, float width, boolean depth) {
        drawLine(null, start, end, color, color, width, depth);
    }

    public void drawLine(MatrixStack.Entry entry, Vec3d start, Vec3d end, int colorStart, int colorEnd, float width, boolean depth) {
        Vec3d relStart = toRelative(start);
        Vec3d relEnd   = toRelative(end);
        Line line = new Line(entry, relStart, relEnd, colorStart, colorEnd, width);
        if (depth) LINE_DEPTH.add(line);
        else       LINE.add(line);
    }


    public void drawQuad(Vec3d x, Vec3d y, Vec3d w, Vec3d z, int color, boolean depth) {
        drawQuad(null, x, y, w, z, color, depth);
    }

    public void drawQuad(MatrixStack.Entry entry, Vec3d x, Vec3d y, Vec3d w, Vec3d z, int color, boolean depth) {
        Quad quad = new Quad(entry, toRelative(x), toRelative(y), toRelative(w), toRelative(z), color);
        if (depth) QUAD_DEPTH.add(quad);
        else       QUAD.add(quad);
    }

    public void drawTexture(MatrixStack.Entry entry, Identifier id, float x, float y, float width, float height, Vector4i color, boolean depth) {
        Texture texture = new Texture(entry, id, x, y, width, height, color);
        if (depth) TEXTURE_DEPTH.add(texture);
        else       TEXTURE.add(texture);
    }

    public void quadTexture(MatrixStack.Entry entry, BufferBuilder buffer, float x, float y, float width, float height, Vector4i color) {
        buffer.vertex(entry, x,         y + height, 0).texture(0, 0).color(color.x);
        buffer.vertex(entry, x + width, y + height, 0).texture(0, 1).color(color.y);
        buffer.vertex(entry, x + width, y,          0).texture(1, 1).color(color.w);
        buffer.vertex(entry, x,         y,          0).texture(1, 0).color(color.z);
    }




    public Vector3f getNormal(Vector3f start, Vector3f end) {
        Vector3f normal = new Vector3f(start).sub(end);
        float sqrt = (float) Math.sqrt(normal.lengthSquared());
        return normal.div(sqrt);
    }

    // --- Группировка батчей по текстуре ---
    //
    // Раньше для каждой уникальной текстуры весь список проходился заново:
    // K проходов по N квадрам плюс стрим-коллектор в каждый кадр. Один проход
    // по списку даёт тот же порядок отрисовки (LinkedHashMap держит первый
    // встречный id, внутри группы — исходный порядок квадов), то есть картинка
    // не меняется ни на пиксель.
    private static final Map<Identifier, List<Texture>> BATCH_GROUPS = new LinkedHashMap<>();
    /** Пустые списки под группы: пересоздавать их каждый кадр незачем. */
    private static final List<List<Texture>> BATCH_GROUP_POOL = new ArrayList<>();

    private static Map<Identifier, List<Texture>> groupTextures(List<Texture> source) {
        // Пушируем списки прошлого кадра в пул, а не в мусор.
        for (List<Texture> list : BATCH_GROUPS.values()) {
            list.clear();
            BATCH_GROUP_POOL.add(list);
        }
        BATCH_GROUPS.clear();

        for (Texture texture : source) {
            List<Texture> group = BATCH_GROUPS.get(texture.id);
            if (group == null) {
                group = BATCH_GROUP_POOL.isEmpty() ? new ArrayList<>()
                        : BATCH_GROUP_POOL.remove(BATCH_GROUP_POOL.size() - 1);
                BATCH_GROUPS.put(texture.id, group);
            }
            group.add(texture);
        }
        return BATCH_GROUPS;
    }

    public record Texture(MatrixStack.Entry entry, Identifier id, float x, float y, float width, float height, Vector4i color) {}
    public record Line(MatrixStack.Entry entry, Vec3d start, Vec3d end, int colorStart, int colorEnd, float width) {}
    public record Quad(MatrixStack.Entry entry, Vec3d x, Vec3d y, Vec3d w, Vec3d z, int color) {}
}
