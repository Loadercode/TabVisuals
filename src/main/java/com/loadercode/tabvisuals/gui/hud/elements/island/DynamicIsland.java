/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.gui.hud.elements.island;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import com.loadercode.tabvisuals.gui.hud.elements.HudHelper;
import com.loadercode.tabvisuals.gui.hud.elements.island.impl.*;
import com.loadercode.tabvisuals.gui.hud.Hud;
import com.loadercode.tabvisuals.gui.hud.compat.Instance;
import com.loadercode.tabvisuals.gui.hud.draggable.AbstractDraggable;
import com.loadercode.tabvisuals.render.color.ColorAssist;
import com.loadercode.tabvisuals.render.font.FontRenderer;
import com.loadercode.tabvisuals.render.font.Fonts;
import com.loadercode.tabvisuals.render.geometry.Render2D;
import com.loadercode.tabvisuals.render.shape.ShapeProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class DynamicIsland extends AbstractDraggable {

    public static DynamicIsland getInstance() {
        return Instance.getDraggable(DynamicIsland.class);
    }

    private static final Identifier AIRPLANE_ICON = Identifier.of("tabvisuals", "textures/island/airplane.png");

    private static final int Y_OFFSET = 2;
    /** Примерная высота одной полосы ванильного боссбара (scaled), когда он не скрыт */
    private static final float BOSS_BAR_ROW_HEIGHT = 19f;
    private static final int EXTERNAL_FONT_SIZE = 16;
    private IslandStatus cachedActiveStatus;

    private final List<IslandStatus> statuses = new ArrayList<>();
    private IslandStatus prevStatus;
    private float statusAlpha = 1f;
    private float slideAnim = 1f;
    private float currentWidth = 48f;
    private float currentHeight = 20f;
    private long lastStatusLookupTime;
    private long lastTimeUpdate;
    /** Момент предыдущего кадра — из него считается dt для FPS-независимого сглаживания. */
    private long lastFrameNs;
    private String cachedTime = "";
    private FontRenderer externalFont;

    public DynamicIsland() {
        super("Dynamic Island", 0, Y_OFFSET, 48, 18, false);
        statuses.add(new TrapStatus());
        statuses.add(new PVPStatus());
        statuses.add(new PVPActiveStatus());
        statuses.add(new TeleportStatus());
        statuses.add(new EventStatus());
        statuses.add(new MineStatus());
        statuses.add(new MusicStatus());
        statuses.add(new DefaultStatus());
    }

    @Override
    public boolean visible() {
        return Hud.getInstance().interfaceSettings.isSelected("Dynamic Island");
    }

    @Override
    public void drawDraggable(DrawContext context) {
        if (mc.player == null) return;
        IslandStatus activeStatus = getActiveStatus();
        cachedActiveStatus = activeStatus;

        // 1 - exp(-dt*k), dt ограничен 0.1 — скорость не зависит от FPS
        long now = System.nanoTime();
        float dt = lastFrameNs == 0L ? 0.0166f : (now - lastFrameNs) / 1_000_000_000f;
        lastFrameNs = now;
        if (dt > 0.1f) dt = 0.1f;

        MatrixStack matrix = context.getMatrices();
        float screenW = mc.getWindow().getScaledWidth();
        float x = screenW / 2f - currentWidth / 2f;
        float y = Y_OFFSET + getBossBarYOffsetBelowBars();

        // Обновляем позицию/размер перед выбором активного статуса
        setX((int) x);
        setY((int) y);
        setWidth((int) currentWidth);
        setHeight((int) currentHeight);

        if (activeStatus != null) {
            activeStatus.updateSize();
            currentWidth  = HudHelper.hud_anim_approach(currentWidth,  activeStatus.getWidth(), 10f, dt);
            currentHeight = HudHelper.hud_anim_approach(currentHeight, activeStatus.getHeight(), 10f, dt);
        } else {
            currentWidth  = HudHelper.hud_anim_approach(currentWidth,  48f, 10f, dt);
            currentHeight = HudHelper.hud_anim_approach(currentHeight, 15f, 10f, dt);
        }

        x = screenW / 2f - currentWidth / 2f;
        setX((int) x);
        setY((int) y);

        if (prevStatus != activeStatus) {
            prevStatus = activeStatus;
            statusAlpha = 0f;
            slideAnim = 0f;
        }
        statusAlpha = HudHelper.hud_anim_approach(statusAlpha, 1f, 12f, dt);  // появление статуса
        slideAnim = HudHelper.hud_anim_approach(slideAnim, 1f, 10f, dt);      // слайд

        int[] gradientColors = ColorAssist.getRectangleGradientColors();
        blur.render(ShapeProperties.create(matrix, x, y, currentWidth, currentHeight)
                    .round(8f)
                    .quality(Hud.getInstance().getBlurQuality())
                    .color(ColorAssist.getBlurColor())
                    .build());
            rectangle.render(ShapeProperties.create(matrix, x, y, currentWidth, currentHeight)
                    .round(8f)
                    .thickness(0.1f)
                    .outlineColor(ColorAssist.getRectangleOutlineColor())
                .color(gradientColors[0], gradientColors[1], gradientColors[2], gradientColors[3])
                .build());

        if (externalFont == null) {
            externalFont = Fonts.getSize(EXTERNAL_FONT_SIZE, Fonts.Type.NOTVISUALS_MEDIUM);
        }
        // Время и пинг — фиксированные отступы от островка, выравнивание по точной
        // ширине и посадка на пиксельную сетку
        String time = getCurrentTimeCached();
        externalFont.drawRightSnapped(matrix, time, x - 8,
                HudHelper.hud_text_mid_y(externalFont, time, y, currentHeight), 0xFFFFFFFF);

        float iconPingX = x + currentWidth + 6;
        float iconSize = 12f;
        float iconCenterY = y + currentHeight / 2f - iconSize / 2f;
        if (mc.isInSingleplayer() || mc.getNetworkHandler() == null || mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid()) == null) {
            Render2D.drawTexture(matrix, AIRPLANE_ICON, (int) iconPingX, (int) iconCenterY, iconSize, iconSize, 0, 0, 256, 256, 256, 256, 0xFFFFFFFF);
        } else {
            int ping = Objects.requireNonNull(mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid())).getLatency();
            String pingStr = ping + " ms";
            externalFont.drawStringSnapped(matrix, pingStr, iconPingX,
                    HudHelper.hud_text_mid_y(externalFont, pingStr, y, currentHeight), 0xFFFFFFFF);
        }

        // 0.02 — ниже alpha byte 4 ванильный текст форсировал бы непрозрачность
        if (activeStatus != null && statusAlpha > 0.02f) {
            float slideX = -256f * (1f - slideAnim);
            activeStatus.draw(context, x + slideX, y, currentWidth - slideX, currentHeight, statusAlpha, slideAnim);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        Hud hud = Hud.getInstance();
        if (hud == null || !hud.isState() || !hud.interfaceSettings.isSelected("Dynamic Island")) {
            return false;
        }
        if (hud.interfaceSettings.isSelected("Media Player")) {
            return false;
        }
        if (button != 0) return false;

        float x = getX();
        float y = getY();
        float w = currentWidth;
        float h = currentHeight;

        IslandStatus activeStatus = getActiveStatus();
        if (activeStatus == null) return false;

        if (mouseX < x || mouseX > x + w || mouseY < y || mouseY > y + h) {
            activeStatus.onOutsideClick();
            return false;
        }

        activeStatus.click(mouseX, mouseY, x, y, w, h);
        return true;
    }

    private IslandStatus findActiveStatus() {
        for (IslandStatus s : statuses) {
            if (s.canShow()) return s;
        }
        return null;
    }

    private IslandStatus getActiveStatus() {
        long now = System.currentTimeMillis();
        if (now - lastStatusLookupTime >= 100L) {
            cachedActiveStatus = findActiveStatus();
            lastStatusLookupTime = now;
        }
        return cachedActiveStatus;
    }

    /** Скрывать ванильный боссбар только когда активный статус явно дублирует его содержимое. */
    public boolean shouldHideVanillaBossBar() {
        return cachedActiveStatus != null && cachedActiveStatus.hidesVanillaBossBar();
    }

    /** Сдвиг островка вниз, когда боссбар остаётся видимым под ним. */
    private float getBossBarYOffsetBelowBars() {
        if (shouldHideVanillaBossBar()) return 0f;
        if (mc.inGameHud == null) return 0f;
        int rows = ((com.loadercode.tabvisuals.mixin.BossBarHudAccessor) mc.inGameHud.getBossBarHud()).getBossBars().size();
        if (rows <= 0) return 0f;
        return rows * BOSS_BAR_ROW_HEIGHT;
    }

    private String getCurrentTimeCached() {
        long now = System.currentTimeMillis();
        if (now - lastTimeUpdate >= 1000L || cachedTime.isEmpty()) {
            // Один вызов: два отдельных LocalTime.now() могли разделиться
            // границей суток и дать «23:00» вместо «0:00».
            java.time.LocalTime localTime = java.time.LocalTime.now();
            cachedTime = String.format("%d:%02d", localTime.getHour(), localTime.getMinute());
            lastTimeUpdate = now;
        }
        return cachedTime;
    }
}
