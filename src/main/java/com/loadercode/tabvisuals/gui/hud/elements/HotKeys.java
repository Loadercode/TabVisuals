/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.gui.hud.elements;

import com.loadercode.tabvisuals.gui.UiRender;
import com.loadercode.tabvisuals.gui.hud.Hud;
import com.loadercode.tabvisuals.gui.hud.elements.HudHelper;
import com.loadercode.tabvisuals.gui.icon.Icons;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import com.loadercode.tabvisuals.gui.hud.draggable.AbstractDraggable;
import com.loadercode.tabvisuals.gui.Category;
import com.loadercode.tabvisuals.gui.module.ModuleRegistry;
import com.loadercode.tabvisuals.gui.module.ModuleStub;
import com.loadercode.tabvisuals.render.font.FontRenderer;
import com.loadercode.tabvisuals.render.font.Fonts;
import com.loadercode.tabvisuals.render.shape.ShapeProperties;
import com.loadercode.tabvisuals.render.color.ColorAssist;
import com.loadercode.tabvisuals.render.math.calc.Calculate;
import com.loadercode.tabvisuals.gui.hud.elements.StringHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class HotKeys extends AbstractDraggable {
    private final List<ModuleStub> keysList = new ArrayList<>();
    private long lastKeyChange = 0;
    private java.lang.String currentRandomKey = "NONE";
    private int activeBindCount = 0;
    private FontRenderer font;
    private FontRenderer fontModule;
    private FontRenderer items;
    private static final Random RANDOM = new Random();
    private static final List<java.lang.String> PREVIEW_KEYS = List.of("A", "B", "C", "D", "E");

    private static final long ROW_STAGGER_MS = 45L;
    private static final long ROW_ENTER_MS = 320L;
    private static final long ROW_HIGHLIGHT_MS = 650L;
    /** Появление строки по имени модуля — вход и подсветка считаются от него. */
    private final Map<java.lang.String, RowAnim> row_anims = new HashMap<>();
    /** Переиспользуемый набор живых строк — без аллокации HashSet на кадр. */
    private final Set<java.lang.String> live_rows = new HashSet<>();
    /** Кадровая развязка для въезда панели. */
    private long last_frame_ns;
    private float entrance_t = 1f;
    /** Размеры панели, накапливаемые циклом строк внутри Calculate.setAlpha. */
    private float draw_offset = 23f;
    private float draw_max_width = 80f;

    private static final class RowAnim {
        long appear_nanos;
        float last_enter;
    }

    public HotKeys() {
        super("Hot Keys", 300, 40, 80, 23, true);
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : Math.min(1f, v);
    }

    private static float smoothstep(float t) {
        t = clamp01(t);
        return t * t * (3f - 2f * t);
    }

    /** Временной прогресс входа строки: elapsed_ms уже включает задержку стаггера.
     *  Считается от nanoTime, поэтому не зависит от FPS. Прогресс монотонный:
     *  при сдвиге индекса (новый бинд сверху) строка не «схлопывается» обратно.
     */
    private static float row_enter(RowAnim state, int index, long now_nanos) {
        float elapsed = (now_nanos - state.appear_nanos) / 1_000_000f - index * ROW_STAGGER_MS;
        float progress = smoothstep(elapsed / ROW_ENTER_MS);
        if (progress < state.last_enter) {
            progress = state.last_enter;
        } else {
            state.last_enter = progress;
        }
        return progress;
    }

    /** Пульс подсветки строки при срабатывании бинда: 1 → 0 за ROW_HIGHLIGHT_MS. */
    private static float row_highlight(long appear_nanos, long now_nanos) {
        float elapsed = (now_nanos - appear_nanos) / 1_000_000f;
        return 1f - smoothstep(elapsed / ROW_HIGHLIGHT_MS);
    }

    /** Category tab glyph from the shared ClickGUI set (Icons.CAT_*). */
    private static Icons.Glyph categoryGlyph(Category category) {
        return switch (category) {
            case COMBAT -> Icons.CAT_COMBAT;
            case PLAYER -> Icons.CAT_TARGET;
            case MOVEMENT -> Icons.CAT_MOVEMENT;
            case RENDER -> Icons.CAT_VISUAL;
            case MISC -> Icons.CAT_WORLD;
            case CONFIGS -> Icons.CAT_CONFIGS;
        };
    }

    @Override
    public boolean visible() {
        return Hud.getInstance().interfaceSettings.isSelected("Hot Keys")
                && (!keysList.isEmpty() || HudHelper.isChat(mc.currentScreen));
    }

    @Override
    public void tick() {
        keysList.clear();

        int activeCount = 0;
        for (ModuleStub module : ModuleRegistry.ALL) {
            if ((module.bind() == -1 || module.bind() == 0) && holdKey(module) == null) {
                continue;
            }
            if (!module.enabled()) {
                continue;
            }
            keysList.add(module);
            activeCount++;
        }
        activeBindCount = activeCount;
        if (keysList.isEmpty() && HudHelper.isChat(mc.currentScreen)) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastKeyChange >= 1000) {
                currentRandomKey = PREVIEW_KEYS.get(RANDOM.nextInt(PREVIEW_KEYS.size()));
                lastKeyChange = currentTime;
            }
        }
    }

    @Override
    public void drawDraggable(DrawContext context) {
        MatrixStack matrix = context.getMatrices();
        if (font == null) {
            font = Fonts.getSize(13, Fonts.Type.DEFAULT);
            fontModule = Fonts.getSize(13, Fonts.Type.DEFAULT);
            items = Fonts.getSize(12, Fonts.Type.DEFAULT);
        }

        java.lang.String moduleCountText = java.lang.String.valueOf(activeBindCount);

        // wall-clock развязка: пауза отрисовки (>250 мс) перезапускает въезд панели
        long prev_ns = last_frame_ns;
        long now = System.nanoTime();
        last_frame_ns = now;
        float dt = prev_ns == 0L ? 0.016f : (now - prev_ns) / 1_000_000_000f;
        if (dt > 0.1f) dt = 0.1f;
        if (prev_ns == 0L || now - prev_ns > 250_000_000L) entrance_t = 0f;
        entrance_t = Math.min(1f, entrance_t + dt / 0.30f);
        float enterInv = 1f - entrance_t;
        final float enter = 1f - enterInv * enterInv * enterInv;

        int blurQuality = com.loadercode.tabvisuals.gui.hud.Hud.getInstance().getBlurQuality();
        draw_offset = 23f;
        draw_max_width = 80f;
        live_rows.clear();

        // Въезд панели: подъём снизу + альфа, как у CoolDowns/Potions
        matrix.push();
        matrix.translate(0f, (1f - enter) * -6f, 0f);
        Calculate.setAlpha(enter, () -> {
        int[] gc = ColorAssist.getRectangleGradientColors();
        blur.render(ShapeProperties.create(matrix, getX(), getY(), getWidth(), getHeight())
                .round(4f)
                .quality(blurQuality)
                .color(ColorAssist.getBlurColor())
                .build());
        rectangle.render(ShapeProperties.create(matrix, getX(), getY(), getWidth(), getHeight())
                .round(4f)
                .thickness(0.1f)
                .outlineColor(ColorAssist.getOutlineColor())
                .color(gc[0], gc[1], gc[2], gc[3])
                .build());
            blur.render(ShapeProperties.create(matrix, getX(), getY() + 16.5F, getWidth(), getHeight() - 17)
                    .round(0,4,0,4).quality(com.loadercode.tabvisuals.gui.hud.Hud.getInstance().getBlurQuality())
                    .color(ColorAssist.getBlurColor())
                    .build());
            rectangle.render(ShapeProperties.create(matrix, getX(), getY() + 16.5F, getWidth(), getHeight() - 17)
                    .round(0,4,0,4)
                    .thickness(0.1f)
                    .outlineColor(ColorAssist.getOutlineColor())
                    .color(gc[0], gc[1], gc[2], gc[3])
                .build());

        // Правая колонка — фиксированный ЛЕВЫЙ якорь: getStringWidth завышает
        // ширину (глифы округляются вверх при генерации атласа), правое
        // выравнивание по нему уезжало влево и наезжало на заголовок. Заголовок
        // «Hot Keys» кончается на ~48 юнитов (22 + ~26), панель шириной ≥80 —
        // старт colX = max(56, ширина − 34) всегда свободен слева, а строка
        // «Active: N» (~23 юнита) влезает вправо даже на трёхзначном счётчике.
        final float rightColX = HudHelper.hud_px(getX() + Math.max(56f, getWidth() - 34f));
        items.drawString(matrix, "Active: " + moduleCountText,
                rightColX, getY() + HudHelper.hud_px(6.5f), ColorAssist.getText());

        rectangle.render(ShapeProperties.create(matrix, getX() + 18, getY() + 5, 1f, 6)
                .color(ColorAssist.getText(0.5F)).round(0F).build());

        // Иконка шапки — центр строки, оптически на одной линии с заголовком
        Icons.draw(matrix, Icons.KEYBOARD, getX() + HudHelper.hud_px(4.5f),
                UiRender.iconLineY(font, Icons.KEYBOARD, 17f, getY() + HudHelper.hud_px(6.5f)),
                17f, ColorAssist.getText());
        font.drawString(matrix, getName(), getX() + HudHelper.hud_px(22f), getY() + HudHelper.hud_px(6.5f), ColorAssist.getText());

        float centerX = getX() + getWidth() / 2F;

        if (keysList.isEmpty() && HudHelper.isChat(mc.currentScreen)) {
            float centerY = getY() + draw_offset;
            java.lang.String name = "Example Module";
            java.lang.String bind = "[" + currentRandomKey + "]";
            Icons.Glyph iconGlyph = Icons.CAT_COMBAT;
            RowAnim state = row_anims.computeIfAbsent("__preview__", k -> {
                RowAnim a = new RowAnim();
                a.appear_nanos = now;
                return a;
            });
            live_rows.add("__preview__");
            float enterRow = row_enter(state, 0, now);
            float highlight = row_highlight(state.appear_nanos, now);
            int textColor = ColorAssist.getText();
            int bindColor = ColorAssist.lerp(highlight, textColor, ColorAssist.getClientColor());
            int textAlpha = 255;
            int colorWithAlpha = ColorAssist.rgba((textColor >> 16) & 255, (textColor >> 8) & 255, textColor & 255, textAlpha);
            matrix.push();
            // Скольжение строки из-под шапки: 8px слева при enter=0
            matrix.translate((enterRow - 1f) * 8f, 0f, 0f);
            Calculate.scale(matrix, centerX, centerY, 1, enterRow, () -> {
                draw_row_highlight(matrix, centerY, highlight);
                Icons.draw(matrix, iconGlyph, getX() + HudHelper.hud_px(4.5f),
                        UiRender.iconLineY(fontModule, iconGlyph, 16f, centerY + 1), 16f, textColor);
                rectangle.render(ShapeProperties.create(matrix, getX() + 15F, centerY - 1, 1F, 7).color(ColorAssist.getOutline(1, 0.5F)).build());
                fontModule.drawString(matrix, name, getX() + HudHelper.hud_px(19f), centerY + 1, colorWithAlpha);
                fontModule.drawString(matrix, bind, HudHelper.hud_text_right(fontModule, bind, HudHelper.hud_px(getX() + getWidth() - 8f)), centerY + 1, bindColor);
            });
            matrix.pop();
            int width = (int) Math.ceil(fontModule.getStringWidth(name + bind)) + 25;
            draw_max_width = Math.max(width, draw_max_width);
            draw_offset += 11;
        } else {
            int row_index = 0;
            for (ModuleStub module : keysList) {
                java.lang.String keyName = module.bind() > 0
                        ? StringHelper.getBindName(module.bind()) : holdKey(module);
                java.lang.String bind = "[" + keyName + "]";
                float centerY = getY() + draw_offset;
                RowAnim state = row_anims.computeIfAbsent(module.name(), k -> {
                    RowAnim a = new RowAnim();
                    a.appear_nanos = now;
                    return a;
                });
                live_rows.add(module.name());
                float enterRow = row_enter(state, row_index, now);
                float highlight = row_highlight(state.appear_nanos, now);
                row_index++;
                Icons.Glyph iconGlyph = categoryGlyph(module.category());
                int textColor = ColorAssist.getText();
                int bindColor = ColorAssist.lerp(highlight, textColor, ColorAssist.getClientColor());
                int textAlpha = 255;
                int colorWithAlpha = ColorAssist.rgba((textColor >> 16) & 255, (textColor >> 8) & 255, textColor & 255, textAlpha);
                matrix.push();
                // Скольжение строки из-под шапки: 8px слева при enter=0
                matrix.translate((enterRow - 1f) * 8f, 0f, 0f);
                Calculate.scale(matrix, centerX, centerY, 1, enterRow, () -> {
                    draw_row_highlight(matrix, centerY, highlight);
                    Icons.draw(matrix, iconGlyph, getX() + HudHelper.hud_px(4.5f),
                            UiRender.iconLineY(fontModule, iconGlyph, 16f, centerY + 1), 16f, textColor);
                    rectangle.render(ShapeProperties.create(matrix, getX() + 15F, centerY - 1, 1F, 7).color(ColorAssist.getOutline(1, 0.5F)).build());
                    fontModule.drawString(matrix, module.name(), getX() + HudHelper.hud_px(19f), centerY + 1, colorWithAlpha);
                    fontModule.drawString(matrix, bind, HudHelper.hud_text_right(fontModule, bind, HudHelper.hud_px(getX() + getWidth() - 8f)), centerY + 1, bindColor);
                });
                matrix.pop();
                float width = (float) Math.ceil(fontModule.getStringWidth(module.name() + bind)) + 25;
                draw_max_width = Math.max(width, draw_max_width);
                draw_offset += 11;
            }
        }
        });
        matrix.pop();
        row_anims.keySet().retainAll(live_rows);
        setWidth((int) (draw_max_width + 10));
        setHeight((int) draw_offset);
    }

    /** Акцентная подложка строки при срабатывании бинда: плавный всплеск alpha. */
    private void draw_row_highlight(MatrixStack matrix, float centerY, float highlight) {
        if (highlight <= 0.01f) {
            return;
        }
        int accent = ColorAssist.getClientColor();
        rectangle.render(ShapeProperties.create(matrix, getX() + 2f, centerY - 4.5f, getWidth() - 4f, 10f)
                .round(3f)
                .color(ColorAssist.multAlpha(accent, 0.16f * highlight))
                .build());
        // Тонкая слева-акцентная черта, ярче основной подложки
        rectangle.render(ShapeProperties.create(matrix, getX() + 2f, centerY - 3.5f, 1f, 8f)
                .round(0.5f)
                .color(ColorAssist.multAlpha(accent, 0.85f * highlight))
                .build());
    }

    /** Hold-key label (Zoom/FreeLook KeySetting) shown when the toggle bind is empty. */
    private static java.lang.String holdKey(ModuleStub module) {
        for (com.loadercode.tabvisuals.gui.setting.Setting s : module.settings()) {
            if (s instanceof com.loadercode.tabvisuals.gui.setting.KeySetting ks && ks.get() > 0) {
                return ks.label();
            }
        }
        return null;
    }
}
