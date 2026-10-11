/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.render.math.projection;

import com.loadercode.tabvisuals.render.math.calc.Calculate;
import lombok.experimental.UtilityClass;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import org.joml.*;
import org.lwjgl.opengl.GL11;
import com.loadercode.tabvisuals.render.interfaces.QuickImports;
import com.loadercode.tabvisuals.render.geometry.Render3D;

import java.lang.Math;

@UtilityClass
public class Projection implements QuickImports {

    // --- Переиспользуемые буферы проекции ---
    //
    // Раньше каждый вызов делал синхронный glGetIntegerv(GL_VIEWPORT) и
    // аллоцировал int[4], Vector3f, Vector4f и копию Matrix4f. getVector4D()
    // зовёт это восемь раз на бокс (по четыре угла, две интерполяции), а
    // ItemESP/CustomHitBox/HWHelper проецируют за кадр десятки сущностей —
    // сотни round-trip'ов в драйвер и мелких объектов в GC.
    //
    // Видовой прямоугольник в кадре не меняется: его заново снимаем, только
    // когда сменился объект матрицы мира или размер фреймбуфера (ресайз).
    // Всё пишется из рендер-потока — как и раньше, ничего параллельного.
    private static final int[] viewport = new int[4];
    private static final Matrix4f projectionScratch = new Matrix4f();
    private static final Vector4f transformedCoordinates = new Vector4f();
    private static final Vector3f projected = new Vector3f();
    private static final double[] screen = new double[3];
    private static MatrixStack.Entry viewportSource;
    private static int viewportWidth = -1;
    private static int viewportHeight = -1;

    public @NotNull Vec3d worldSpaceToScreenSpace(Vec3d pos) {
        return worldSpaceToScreenSpace(pos.x, pos.y, pos.z);
    }

    public @NotNull Vec3d worldSpaceToScreenSpace(double px, double py, double pz) {
        if (project(px, py, pz)) {
            return new Vec3d(screen[0], screen[1], screen[2]);
        }
        return Vec3d.ZERO;
    }

    /**
     * Проекция точки в экранные координаты, записывает их в {@link #screen}.
     *
     * <p>Глубина тут <b>не</b> проверяется: вызывающие сами смотрят
     * {@code screen[2]} в диапазоне (0, 1), и вырезать её здесь значило бы
     * тихо менять результат для всех, кто уже сегодня это делает сам.</p>
     *
     * @return false — игрока, окна или матриц ещё нет, считать не из чего
     */
    private boolean project(double px, double py, double pz) {
        if (Render3D.lastWorldSpaceMatrix == null || Render3D.lastProjMat == null || mc == null || mc.getWindow() == null) {
            return false;
        }

        int width = mc.getWindow().getFramebufferWidth();
        int height = mc.getWindow().getFramebufferHeight();
        if (viewportSource != Render3D.lastWorldSpaceMatrix
                || viewportWidth != width || viewportHeight != height) {
            viewportSource = Render3D.lastWorldSpaceMatrix;
            viewportWidth = width;
            viewportHeight = height;
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        }

        Camera camera = mc.gameRenderer.getCamera();
        Vec3d camPos = camera.getPos();

        // project() мутирует матрицу, поэтому копия обязательна — но копия в
        // переиспользуемый буфер вместо новой Matrix4f на каждый вызов.
        projectionScratch.set(Render3D.lastProjMat);
        transformedCoordinates.set((float) (px - camPos.x), (float) (py - camPos.y), (float) (pz - camPos.z), 1f)
                .mul(Render3D.lastWorldSpaceMatrix.getPositionMatrix());
        projectionScratch.project(transformedCoordinates.x(), transformedCoordinates.y(),
                transformedCoordinates.z(), viewport, projected);

        double scaleFactor = mc.getWindow().getScaleFactor();
        screen[0] = projected.x() / scaleFactor;
        screen[1] = (mc.getWindow().getHeight() - projected.y()) / scaleFactor;
        screen[2] = projected.z();
        return true;
    }

    public static double getDistanceToGround() {
        // Не assert: в продакшене он выключен. Вызывается и без игрока/мира —
        // тогда возвращаем полную высоту мира, как и при отсутствии блока.
        if (mc.player == null || mc.world == null) return 256;
        for (double y = mc.player.getY(); y > 0; y -= 0.1) {
            if (!mc.world.getBlockState(mc.player.getBlockPos().down((int) ((mc.player.getY() - y) + 1))).isAir()) {
                return mc.player.getY() - y;
            }
        }
        return 256;
    }

    public Vector4d getVector4D(Entity ent) {
        Vector4d position = null;
        if (ent != null) {
            for (Vec3d vector : getVec3ds(ent, Calculate.interpolate(ent))) {
                // Без промежуточного Vec3d: восемь углов бокса на сущность —
                // восемь объектов меньше, остальное считается тем же кодом.
                if (!project(vector.x, vector.y, vector.z)) continue;
                double x = screen[0];
                double y = screen[1];
                double z = screen[2];
                if (z > 0 && z < 1) {
                    if (position == null) position = new Vector4d(x, y, z, 0);
                    position.x = Math.min(x, position.x);
                    position.y = Math.min(y, position.y);
                    position.z = Math.max(x, position.z);
                    position.w = Math.max(y, position.w);
                }
            }
        }
        return position;
    }

    public @NotNull Vec3d[] getVec3ds(Entity ent, Vec3d pos) {
        Box axisAlignedBB2 = ent.getBoundingBox();
        Box axisAlignedBB = new Box(axisAlignedBB2.minX - ent.getX() + pos.x - 0.1F, axisAlignedBB2.minY - ent.getY() + pos.y - 0.1F, axisAlignedBB2.minZ - ent.getZ() + pos.z - 0.1F, axisAlignedBB2.maxX - ent.getX() + pos.x + 0.1F, axisAlignedBB2.maxY - ent.getY() + pos.y + 0.1F, axisAlignedBB2.maxZ - ent.getZ() + pos.z + 0.1F);
        return new Vec3d[]{new Vec3d(axisAlignedBB.minX, axisAlignedBB.minY, axisAlignedBB.minZ), new Vec3d(axisAlignedBB.minX, axisAlignedBB.maxY, axisAlignedBB.minZ), new Vec3d(axisAlignedBB.maxX, axisAlignedBB.minY, axisAlignedBB.minZ), new Vec3d(axisAlignedBB.maxX, axisAlignedBB.maxY, axisAlignedBB.minZ), new Vec3d(axisAlignedBB.minX, axisAlignedBB.minY, axisAlignedBB.maxZ), new Vec3d(axisAlignedBB.minX, axisAlignedBB.maxY, axisAlignedBB.maxZ), new Vec3d(axisAlignedBB.maxX, axisAlignedBB.minY, axisAlignedBB.maxZ), new Vec3d(axisAlignedBB.maxX, axisAlignedBB.maxY, axisAlignedBB.maxZ)};
    }

    public boolean canSee(Vec3d vec3d) {
        if (mc == null || mc.getEntityRenderDispatcher() == null) return false;
        Camera camera = mc.getEntityRenderDispatcher().camera;
        if (camera == null) return false;
        Vec3d dir = vec3d.subtract(camera.getPos());
        float yaw = (float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(dir.z, dir.x)) - 90.0);
        float pitch = (float) MathHelper.wrapDegrees(Math.toDegrees(-Math.atan2(dir.y, Math.hypot(dir.x, dir.z))));
        return (Math.abs(MathHelper.wrapDegrees(yaw - camera.getYaw())) < 90 && Math.abs(MathHelper.wrapDegrees(pitch - camera.getPitch())) < 60) || canSee(new Box(BlockPos.ofFloored(vec3d)));
    }

    public boolean canSee(Box box) {
        if (mc == null || mc.worldRenderer == null || box == null) return false;
        Frustum frustum = mc.worldRenderer.frustum;
        return frustum != null && frustum.isVisible(box);
    }

    public boolean cantSee(Vector4d vec) {
       return vec == null || (vec.x < 0 && vec.z < 1) || (vec.y < 0 && vec.w < 1);
    }

    public double centerX(Vector4d vec) {
        return vec.x + (vec.z - vec.x) / 2;
    }
}