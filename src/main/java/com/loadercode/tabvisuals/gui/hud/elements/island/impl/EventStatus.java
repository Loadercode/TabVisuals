/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.gui.hud.elements.island.impl;

import net.minecraft.client.gui.DrawContext;
import com.loadercode.tabvisuals.gui.hud.elements.island.IslandStatus;
import com.loadercode.tabvisuals.gui.hud.compat.FTHelper;
import com.loadercode.tabvisuals.render.color.ColorAssist;
import com.loadercode.tabvisuals.render.font.FontRenderer;
import com.loadercode.tabvisuals.render.font.Fonts;
import com.loadercode.tabvisuals.render.interfaces.QuickImports;
import com.loadercode.tabvisuals.render.scissor.ScissorAssist;
import com.loadercode.tabvisuals.render.shape.ShapeProperties;

import java.util.ArrayList;
import java.util.List;

public class EventStatus extends IslandStatus implements QuickImports {

    private static final int[] EVENT_COLORS = {
            0xFF8A2BE2, 0xFFFF4500, 0xFFFF8C00, 0xFF4682B4,
            0xFFF3C452, 0xFF8BDDDD, 0xFF8D63B8, 0xFF29FD05,
            0xFF5A9E98
    };
    private static final float TIME_BOX_PAD = 3f;  // отступ цифр внутри маленького окна таймера
    private static final float ROW_H = 17f;
    private static final float ROW_GAP = 2f;
    private static final float LIST_ANIM_SMOOTH = 0.15f;

    /** Список только активных ивентов (timeEnd > now). Истёкшие не показываем, чтобы островок не зависал. */
    private static List<FTHelper.ServerEvent> getActiveEvents() {
        FTHelper ft = FTHelper.getInstance();
        if (ft == null) return List.of();
        List<FTHelper.ServerEvent> events = ft.getServerEvents();
        if (events == null) return List.of();
        return events.stream()
                .filter(e -> e.timeEnd() - System.currentTimeMillis() > 0)
                .toList();
    }

    /** Ивент для отображения: первый из активных (следующий в очереди). При появлении нового — показываем последний (новый). */
    private static FTHelper.ServerEvent getEventToShow(List<FTHelper.ServerEvent> active) {
        if (active == null || active.isEmpty()) return null;
        return active.getLast();
    }

    /**
     * Порядок строк в развёрнутом списке: сверху тот же ивент, что в компактном виде, ниже — остальные в исходном порядке.
     */
    private static List<FTHelper.ServerEvent> getRowsForList(List<FTHelper.ServerEvent> active) {
        if (active == null || active.isEmpty()) return List.of();
        if (active.size() == 1) return List.of(active.getFirst());
        List<FTHelper.ServerEvent> rows = new ArrayList<>(active.size());
        rows.add(active.getLast());
        for (int i = 0; i < active.size() - 1; i++) {
            rows.add(active.get(i));
        }
        return rows;
    }

    private boolean listExpanded = false;
    private float listAnim = 0f;

    @Override
    public boolean canShow() {
        return !getActiveEvents().isEmpty();
    }

    private static float rowWidth(FTHelper.ServerEvent event, long remainingSec) {
        if (remainingSec <= 0) return 0f;
        int min = (int) (remainingSec / 60);
        int sec = (int) (remainingSec % 60);
        String timeStr = String.format("%d:%02d", min, sec);
        float timeW = mc.textRenderer.getWidth(timeStr);
        float boxW = TIME_BOX_PAD * 2 + timeW;
        var font7 = Fonts.getSize(18, Fonts.Type.NOTVISUALS_MEDIUM);
        return 10f + boxW + font7.getStringWidth(event.name());
    }

    @Override
    public void updateSize() {
        List<FTHelper.ServerEvent> active = getActiveEvents();
        FTHelper.ServerEvent event = getEventToShow(active);
        if (event == null) return;
        long remaining = (long) ((event.timeEnd() - System.currentTimeMillis()) / 1000);
        if (remaining <= 0) return;

        float compactW = rowWidth(event, remaining);
        float compactH = ROW_H;

        if (active.size() <= 1) {
            listExpanded = false;
            listAnim = 0f;
            width = compactW;
            height = compactH;
            return;
        }

        float target = listExpanded ? 1f : 0f;
        listAnim += (target - listAnim) * LIST_ANIM_SMOOTH;

        List<FTHelper.ServerEvent> rows = getRowsForList(active);
        float maxW = compactW;
        float totalH = 0f;
        int n = 0;
        for (FTHelper.ServerEvent e : rows) {
            long rs = (long) ((e.timeEnd() - System.currentTimeMillis()) / 1000);
            if (rs <= 0) continue;
            float rw = rowWidth(e, rs);
            maxW = Math.max(maxW, rw);
            totalH += ROW_H;
            if (n > 0) totalH += ROW_GAP;
            n++;
        }
        if (n == 0) {
            width = compactW;
            height = compactH;
            return;
        }

        width = compactW + (maxW - compactW) * listAnim;
        height = compactH + (totalH - compactH) * listAnim;
    }

    @Override
    public void draw(DrawContext context, float x, float y, float islandWidth, float islandHeight, float alpha, float slideAnim) {
        List<FTHelper.ServerEvent> active = getActiveEvents();
        FTHelper.ServerEvent primary = getEventToShow(active);
        if (primary == null) return;

        var font7 = Fonts.getSize(18, Fonts.Type.NOTVISUALS_MEDIUM);
        List<FTHelper.ServerEvent> rows = getRowsForList(active);
        boolean multi = active.size() > 1;
        boolean showList = multi && (listAnim > 0.02f || listExpanded);

        ScissorAssist scissor = ScissorAssist.INSTANCE;
        scissor.push(context.getMatrices().peek().getPositionMatrix(), x, y, islandWidth, islandHeight);

        if (!showList) {
            long remaining = (long) ((primary.timeEnd() - System.currentTimeMillis()) / 1000);
            if (remaining <= 0) {
                scissor.pop();
                return;
            }
            drawEventRow(context, x, y, primary, remaining, font7);
            scissor.pop();
            return;
        }

        float dy = y;
        for (FTHelper.ServerEvent e : rows) {
            long remaining = (long) ((e.timeEnd() - System.currentTimeMillis()) / 1000);
            if (remaining <= 0) continue;
            drawEventRow(context, x, dy, e, remaining, font7);
            dy += ROW_H + ROW_GAP;
        }

        scissor.pop();
    }

    private void drawEventRow(DrawContext context, float x, float y, FTHelper.ServerEvent event, long remainingSec, FontRenderer font7) {
        int min = (int) (remainingSec / 60);
        int sec = (int) (remainingSec % 60);
        String timeStr = String.format("%d:%02d", min, sec);

        // Math.abs(Integer.MIN_VALUE) остаётся отрицательным → отрицательный
        // индекс и ArrayIndexOutOfBoundsException при рендере.
        int colorIdx = Math.floorMod(event.name().hashCode(), EVENT_COLORS.length);
        int color = EVENT_COLORS[colorIdx];

        float timeW = mc.textRenderer.getWidth(timeStr);
        float boxW = TIME_BOX_PAD * 2 + timeW;
        float boxH = 12f;
        float boxX = x + 4;
        float boxY = y + 2f;
        float timeY = boxY + (boxH - mc.textRenderer.fontHeight) / 2f + 1f;
        float textY = boxY + boxH + 2f - font7.getStringHeight("A") / 2f;

        rectangle.render(ShapeProperties.create(context.getMatrices(), boxX, boxY, boxW, boxH).round(3f).color(color | 0xFF000000).build());
        context.drawText(mc.textRenderer, timeStr, (int) (boxX + TIME_BOX_PAD), (int) timeY, ColorAssist.getText(), false);
        font7.drawString(context.getMatrices(), event.name(), boxX + boxW + 2, textY, ColorAssist.getText());
    }

    @Override
    public void click(double mouseX, double mouseY, float islandX, float islandY, float islandWidth, float islandHeight) {
        List<FTHelper.ServerEvent> active = getActiveEvents();
        if (active.size() > 1) {
            listExpanded = !listExpanded;
        }
    }

    @Override
    public void onOutsideClick() {
        listExpanded = false;
    }

    @Override
    public int getColor() {
        return 0xE0252525;
    }
}
