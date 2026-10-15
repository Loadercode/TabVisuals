/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.gui.hud.elements.island.impl;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import com.loadercode.tabvisuals.gui.UiRender;
import com.loadercode.tabvisuals.gui.hud.elements.HudHelper;
import com.loadercode.tabvisuals.gui.hud.elements.StringHelper;
import com.loadercode.tabvisuals.gui.hud.elements.island.IslandStatus;
import com.loadercode.tabvisuals.util.Network;
import com.loadercode.tabvisuals.render.color.ColorAssist;
import com.loadercode.tabvisuals.render.font.Fonts;
import com.loadercode.tabvisuals.render.interfaces.QuickImports;
import com.loadercode.tabvisuals.render.shape.ShapeProperties;

import java.util.ArrayList;
import java.util.List;

public class MineStatus extends IslandStatus implements QuickImports {

    private static final Vec3d FT_SPAWN = new Vec3d(-68, 81, 5);
    private static final float TIME_BOX_PAD = 3f;  // отступ цифр внутри маленького окна таймера

    /**
     * Один обход мира вместо двух за кадр, и не чаще, чем раз в 150 мс.
     *
     * <p>Раньше {@code canShow()} и {@code parseMineData()} каждый кадр проходили
     * по всем сущностям мира по отдельности, а {@code DynamicIsland} зовёт
     * {@code canShow()} для каждого из восьми статусов, пока не найдёт первый
     * показываемый — то есть на анархии со спавном в кубе сотен арморстендов это
     * были два полных скана мира каждый кадр. Таймер на табличке меняется раз в
     * секунду, так что 150 мс отставания незаметны.</p>
     */
    private static final long SCAN_TTL_MS = 150L;
    private long scannedAt;
    private boolean scanned;
    private boolean nearStand;
    private String scannedTime = "";
    private String scannedMineType = "";

    private void refreshScan() {
        long now = System.currentTimeMillis();
        if (scanned && now - scannedAt < SCAN_TTL_MS) return;

        scannedTime = "";
        scannedMineType = "";
        nearStand = false;
        scannedAt = now;
        if (mc.world == null || mc.player == null) {
            scanned = true;
            return;
        }

        List<ArmorStandEntity> stands = new ArrayList<>();
        for (Entity e : mc.world.getEntities()) {
            if (e instanceof ArmorStandEntity as && near(as)) {
                stands.add(as);
                if (as.isAlive()) nearStand = true;
            }
        }
        stands.sort((a, b) -> Double.compare(b.getY(), a.getY()));

        String time = "";
        String mineType = "";
        for (ArmorStandEntity as : stands) {
            Text name = as.getCustomName();
            if (name == null) continue;
            String s = name.getString().trim();
            if (s.matches("\\d{1,2}:\\d{2}")) {
                time = s.replaceFirst("^0", "");
            } else if (s.contains("осталось:")) {
                int idx = s.indexOf(':');
                if (idx >= 0 && idx + 2 < s.length()) {
                    String t = s.substring(idx + 2).trim();
                    int mi = t.indexOf(" мин.");
                    int si = t.indexOf(" сек.");
                    // Без проверки порядка и без try/catch «через 5 мин.» или иной
                    // порядок блоков ронял NumberFormatException/substring прямо в draw().
                    try {
                        if (mi >= 0 && si > mi + 5) {
                            int min = Integer.parseInt(t.substring(0, mi).trim());
                            int sec = Integer.parseInt(t.substring(mi + 5, si).trim());
                            time = StringHelper.format_clock(min, sec);
                        }
                    } catch (NumberFormatException | IndexOutOfBoundsException ignored) {
                        // Формат серверного текста не совпал — оставляем прошлое время.
                    }
                }
            } else if (s.startsWith("Следующая:")) {
                int idx = s.indexOf(':');
                if (idx >= 0 && idx + 2 < s.length()) mineType = s.substring(idx + 2).trim();
            }
            if (!time.isEmpty() && !mineType.isEmpty()) break;
        }

        scannedTime = time;
        scannedMineType = mineType;
        scanned = true;
    }

    @Override
    public boolean canShow() {
        if (mc.world == null || mc.player == null) return false;
        if (!Network.isFunTime()) return false;
//        if (!Network.spawn()) return false;
        refreshScan();
        return nearStand;
    }

    private String[] parseMineData() {
        refreshScan();
        return new String[]{scannedTime, scannedMineType};
    }

    @Override
    public void draw(DrawContext context, float x, float y, float islandWidth, float islandHeight, float alpha, float slideAnim) {
        if (mc.world == null || mc.player == null) return;

        String[] parsed = parseMineData();
        String time = parsed[0];
        String mineType = parsed[1];
        if (time.isEmpty() || mineType.isEmpty()) return;

        int color = 0xFFFCA800;
        var font7 = Fonts.getSize(18, Fonts.Type.NOTVISUALS_MEDIUM);
        float timeW = mc.textRenderer.getWidth(time);
        float boxW = TIME_BOX_PAD * 2 + timeW;
        width = 10f + boxW + font7.getStringWidth(mineType);
        height = 17f;
        float boxH = 12f;
        float boxX = x + 4;
        float boxY = y + 2f;
        float timeY = boxY + (boxH - mc.textRenderer.fontHeight) / 2f + 1f;  // +1 для точной вертикали
        float textY = HudHelper.hud_text_mid_y(font7, mineType, y, height);

        int textColor = UiRender.mulAlpha(ColorAssist.getText(), alpha);
        rectangle.render(ShapeProperties.create(context.getMatrices(), boxX, boxY, boxW, boxH).round(3f).color(UiRender.mulAlpha(color, alpha)).build());
        context.drawText(mc.textRenderer, time, (int) (boxX + TIME_BOX_PAD), (int) timeY, textColor, false);
        font7.drawStringSnapped(context.getMatrices(), mineType, boxX + boxW + 2, textY, textColor);
    }

    private boolean near(ArmorStandEntity a) {
        return Math.abs(a.getX() - MineStatus.FT_SPAWN.x) <= 3 && Math.abs(a.getY() - MineStatus.FT_SPAWN.y) <= 3 && Math.abs(a.getZ() - MineStatus.FT_SPAWN.z) <= 3;  // 2 — радиус поиска арморстендов
    }

}
