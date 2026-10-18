// language: Java, file: Icons.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.gui.icon;

import com.google.common.base.Suppliers;
import com.loadercode.tabvisuals.gui.UiRender;
import com.loadercode.tabvisuals.render.atlasfont.msdf.MsdfFont;
import com.loadercode.tabvisuals.render.font.FontRenderer;
import com.loadercode.tabvisuals.render.font.Fonts;
import com.loadercode.tabvisuals.render.systemrender.builders.Builder;
import net.minecraft.client.util.math.MatrixStack;

import java.util.function.Supplier;

/**
 * Единая точка доступа к иконкам клиента. Иконки живут в пяти MSDF-гарнитурах
 * (assets/minecraft/fonts/{icons,icon2,guiicons,categoryicons,iconloxedlcreg}.ttf),
 * глифы на ASCII-буквах. Маппинг «буква -> смысл» больше не разбросан по файлам
 * магическими строками — только через этот enum.
 *
 * PAUSE в шрифтах отсутствует, поэтому рисуется векторно (два бара).
 */
public final class Icons {

    private Icons() {
    }

    /** Семантическая иконка: гарнитура + символ (для PAUSE символ не используется). */
    public enum Glyph {
        // --- icons: медиа, статусы, HUD ------------------------------------
        PLAY(Fonts.Type.ICONS, 'I'),
        STOP(Fonts.Type.ICONS, 'J'),
        PAUSE(Fonts.Type.ICONS, '\0'),         // вектор: два бара
        SKIP_PREV(Fonts.Type.ICONS, 'O'),
        SKIP_NEXT(Fonts.Type.ICONS, 'P'),
        SEARCH(Fonts.Type.ICONS, 'U'),
        CLOSE(Fonts.Type.ICONS, 'L'),          // X в круге
        KEYBOARD(Fonts.Type.ICONS, 'B'),
        POTION(Fonts.Type.ICONS, 'C'),
        CLOCK(Fonts.Type.ICONS, 'D'),
        EYE_OFF(Fonts.Type.ICONS, 'F'),
        INVENTORY(Fonts.Type.ICONS, 'G'),
        PERSON(Fonts.Type.ICONS, 'H'),
        NODE(Fonts.Type.ICONS, 'K'),           // «глючный» узел — статус сети
        SWORDS(Fonts.Type.ICONS, 'M'),
        SPEED(Fonts.Type.ICONS, 'N'),
        IMAGE(Fonts.Type.ICONS, 'Q'),
        DICE(Fonts.Type.ICONS, 'R'),
        STAR(Fonts.Type.ICONS, 'S'),
        TARGET(Fonts.Type.ICONS, 'T'),
        SHIELD(Fonts.Type.ICONS, 'V'),
        WIND(Fonts.Type.ICONS, 'W'),
        PIN(Fonts.Type.ICONS, 'X'),

        // --- icon2: действия и навигация ------------------------------------
        BRUSH(Fonts.Type.ICONSTYPENEW, 'a'),   // тема/цвет
        GAMEPAD(Fonts.Type.ICONSTYPENEW, 'b'),
        BRIGHTNESS(Fonts.Type.ICONSTYPENEW, 'c'),
        USER(Fonts.Type.ICONSTYPENEW, 'd'),
        ADD(Fonts.Type.ICONSTYPENEW, 'e'),     // сетка с плюсом
        FLASK(Fonts.Type.ICONSTYPENEW, 'f'),
        LOGOUT(Fonts.Type.ICONSTYPENEW, 'h'),
        LOGIN(Fonts.Type.ICONSTYPENEW, 'i'),
        FOLDER(Fonts.Type.ICONSTYPENEW, 'j'),
        MENU(Fonts.Type.ICONSTYPENEW, 'l'),
        ELLIPSIS(Fonts.Type.ICONSTYPENEW, 'm'),
        DASHBOARD(Fonts.Type.ICONSTYPENEW, 'o'),
        HOURGLASS(Fonts.Type.ICONSTYPENEW, 'p'),
        CHEVRON_DOWN(Fonts.Type.ICONSTYPENEW, 'q'),
        CHEVRON_UP(Fonts.Type.ICONSTYPENEW, 'r'),
        GEAR(Fonts.Type.ICONSTYPENEW, 's'),
        SHUFFLE(Fonts.Type.ICONSTYPENEW, 't'),
        TRASH(Fonts.Type.ICONSTYPENEW, 'v'),
        ACTIVITY(Fonts.Type.ICONSTYPENEW, 'w'),
        BELL(Fonts.Type.ICONSTYPENEW, 'x'),

        // --- guiicons: панели, окна, настройки ------------------------------
        SLIDERS(Fonts.Type.GUIICONS, 'A'),
        INFO(Fonts.Type.GUIICONS, 'C'),
        MOON(Fonts.Type.GUIICONS, 'E'),
        SUN(Fonts.Type.GUIICONS, 'F'),
        LINK(Fonts.Type.GUIICONS, 'G'),
        MOVE_H(Fonts.Type.GUIICONS, 'H'),
        COPY(Fonts.Type.GUIICONS, 'I'),
        BOX(Fonts.Type.GUIICONS, 'J'),
        SAVE(Fonts.Type.GUIICONS, 'M'),
        REFRESH(Fonts.Type.GUIICONS, 'N'),
        DOWNLOAD(Fonts.Type.GUIICONS, 'P'),

        // --- categoryicons: вкладки категорий ClickGUI -----------------------
        CAT_COMBAT(Fonts.Type.ICONSCATEGORY, 'A'),
        CAT_MOVEMENT(Fonts.Type.ICONSCATEGORY, 'B'),
        CAT_VISUAL(Fonts.Type.ICONSCATEGORY, 'C'),
        CAT_TARGET(Fonts.Type.ICONSCATEGORY, 'D'),
        CAT_WORLD(Fonts.Type.ICONSCATEGORY, 'E'),
        CAT_MISC(Fonts.Type.ICONSCATEGORY, 'F'),
        CAT_CLIENT(Fonts.Type.ICONSCATEGORY, 'G'),
        CAT_CONFIGS(Fonts.Type.ICONSCATEGORY, 'H'),

        // --- iconloxedlcreg: бренд и крупные акценты -------------------------
        LOGO(Fonts.Type.ICONRICHREG, 'A'),
        ROCKET(Fonts.Type.ICONRICHREG, 'F'),
        USER_OUTLINE(Fonts.Type.ICONRICHREG, 'G'),
        EYE(Fonts.Type.ICONRICHREG, 'E'),
        INVISIBLE(Fonts.Type.ICONRICHREG, 'L'),
        CUBE(Fonts.Type.ICONRICHREG, 'R'),

        /** Бренд-монограмма «D»: MSDF-атлас mre:clienticon1 (json+png), не ttf —
         *  типа Fonts.Type у неё нет (type == null), во всех методах Icons
         *  перехватывается ДО font(), как векторный PAUSE. */
        BRAND(null, 'D');

        private final Fonts.Type type;
        private final char ch;

        Glyph(Fonts.Type type, char ch) {
            this.type = type;
            this.ch = ch;
        }

        public Fonts.Type type() {
            return type;
        }

        public char ch() {
            return ch;
        }

        public String asString() {
            return String.valueOf(ch);
        }
    }

    // ------------------------------------------------------- convenience aliases
    // Icons.GEAR — короткий путь к Glyph.GEAR (тот же объект, читается лаконичнее).

    public static final Glyph PLAY = Glyph.PLAY;
    public static final Glyph STOP = Glyph.STOP;
    public static final Glyph PAUSE = Glyph.PAUSE;
    public static final Glyph SKIP_PREV = Glyph.SKIP_PREV;
    public static final Glyph SKIP_NEXT = Glyph.SKIP_NEXT;
    public static final Glyph SEARCH = Glyph.SEARCH;
    public static final Glyph CLOSE = Glyph.CLOSE;
    public static final Glyph KEYBOARD = Glyph.KEYBOARD;
    public static final Glyph POTION = Glyph.POTION;
    public static final Glyph CLOCK = Glyph.CLOCK;
    public static final Glyph EYE_OFF = Glyph.EYE_OFF;
    public static final Glyph INVENTORY = Glyph.INVENTORY;
    public static final Glyph PERSON = Glyph.PERSON;
    public static final Glyph NODE = Glyph.NODE;
    public static final Glyph SWORDS = Glyph.SWORDS;
    public static final Glyph SPEED = Glyph.SPEED;
    public static final Glyph IMAGE = Glyph.IMAGE;
    public static final Glyph DICE = Glyph.DICE;
    public static final Glyph STAR = Glyph.STAR;
    public static final Glyph TARGET = Glyph.TARGET;
    public static final Glyph SHIELD = Glyph.SHIELD;
    public static final Glyph WIND = Glyph.WIND;
    public static final Glyph PIN = Glyph.PIN;

    public static final Glyph BRUSH = Glyph.BRUSH;
    public static final Glyph GAMEPAD = Glyph.GAMEPAD;
    public static final Glyph BRIGHTNESS = Glyph.BRIGHTNESS;
    public static final Glyph USER = Glyph.USER;
    public static final Glyph ADD = Glyph.ADD;
    public static final Glyph FLASK = Glyph.FLASK;
    public static final Glyph LOGOUT = Glyph.LOGOUT;
    public static final Glyph LOGIN = Glyph.LOGIN;
    public static final Glyph FOLDER = Glyph.FOLDER;
    public static final Glyph MENU = Glyph.MENU;
    public static final Glyph ELLIPSIS = Glyph.ELLIPSIS;
    public static final Glyph DASHBOARD = Glyph.DASHBOARD;
    public static final Glyph HOURGLASS = Glyph.HOURGLASS;
    public static final Glyph CHEVRON_DOWN = Glyph.CHEVRON_DOWN;
    public static final Glyph CHEVRON_UP = Glyph.CHEVRON_UP;
    public static final Glyph GEAR = Glyph.GEAR;
    public static final Glyph SHUFFLE = Glyph.SHUFFLE;
    public static final Glyph TRASH = Glyph.TRASH;
    public static final Glyph ACTIVITY = Glyph.ACTIVITY;
    public static final Glyph BELL = Glyph.BELL;

    public static final Glyph SLIDERS = Glyph.SLIDERS;
    public static final Glyph INFO = Glyph.INFO;
    public static final Glyph MOON = Glyph.MOON;
    public static final Glyph SUN = Glyph.SUN;
    public static final Glyph LINK = Glyph.LINK;
    public static final Glyph MOVE_H = Glyph.MOVE_H;
    public static final Glyph COPY = Glyph.COPY;
    public static final Glyph BOX = Glyph.BOX;
    public static final Glyph SAVE = Glyph.SAVE;
    public static final Glyph REFRESH = Glyph.REFRESH;
    public static final Glyph DOWNLOAD = Glyph.DOWNLOAD;

    public static final Glyph CAT_COMBAT = Glyph.CAT_COMBAT;
    public static final Glyph CAT_MOVEMENT = Glyph.CAT_MOVEMENT;
    public static final Glyph CAT_VISUAL = Glyph.CAT_VISUAL;
    public static final Glyph CAT_TARGET = Glyph.CAT_TARGET;
    public static final Glyph CAT_WORLD = Glyph.CAT_WORLD;
    public static final Glyph CAT_MISC = Glyph.CAT_MISC;
    public static final Glyph CAT_CLIENT = Glyph.CAT_CLIENT;
    public static final Glyph CAT_CONFIGS = Glyph.CAT_CONFIGS;

    public static final Glyph LOGO = Glyph.LOGO;
    public static final Glyph ROCKET = Glyph.ROCKET;
    public static final Glyph USER_OUTLINE = Glyph.USER_OUTLINE;
    public static final Glyph EYE = Glyph.EYE;
    public static final Glyph INVISIBLE = Glyph.INVISIBLE;
    public static final Glyph CUBE = Glyph.CUBE;
    public static final Glyph BRAND = Glyph.BRAND;

    /** Векторный PAUSE: два бара шириной 0.13·size с зазором 0.12·size. */
    private static final float PAUSE_W_FACTOR = 0.38f;   // 2 бара + зазор
    private static final float PAUSE_H_FACTOR = 0.55f;

    /** Шрифт иконки нужного размера (4..32 предзагружены в Fonts.init). */
    private static FontRenderer font(Glyph glyph, float size) {
        int s = Math.max(1, Math.min(32, Math.round(size)));
        return Fonts.getSize(s, glyph.type());
    }

    private static boolean isVector(Glyph glyph) {
        return glyph == Glyph.PAUSE;
    }

    private static boolean isBrand(Glyph glyph) {
        return glyph == Glyph.BRAND;
    }

    /** MSDF-атлас бренда (mre:clienticon1): ленивая сборка на render-потоке — тот же
     *  паттерн Suppliers.memoize, что был в Watermark до переезда на Icons. */
    private static final Supplier<MsdfFont> BRAND_FONT =
            Suppliers.memoize(() -> MsdfFont.builder().atlas("clienticon1").data("clienticon1").build());

    private static void drawBrand(MatrixStack m, float x, float y, float size, int argb) {
        Builder.text()
                .font(BRAND_FONT.get())
                .text(Glyph.BRAND.asString())
                .size(size)
                .color(argb)
                .build()
                .render(m.peek().getPositionMatrix(), x, y);
    }

    /**
     * Рисует иконку с верхнего левого угла (x, y) — как drawString.
     * PAUSE рисуется векторно (два бара), бренд — MSDF-глифом, остальные — глифом.
     */
    public static void draw(MatrixStack m, Glyph glyph, float x, float y, float size, int argb) {
        if (glyph == null || m == null) return;
        if (isBrand(glyph)) {
            drawBrand(m, x, y, size, argb);
            return;
        }
        if (isVector(glyph)) {
            drawPause(m, x, y + (size - PAUSE_H_FACTOR * size) / 2f, size, argb);
            return;
        }
        FontRenderer f = font(glyph, size);
        if (f == null) return;
        f.drawString(m, glyph.asString(), x, y, argb);
    }

    private static void drawPause(MatrixStack m, float x, float y, float size, int argb) {
        float barW = size * 0.13f;
        float barH = size * PAUSE_H_FACTOR;
        float gap = size * 0.12f;
        float round = Math.min(barW, barH) / 2f;
        UiRender.rect(m, x, y, barW, barH, round, argb);
        UiRender.rect(m, x + barW + gap, y, barW, barH, round, argb);
    }

    /** Ширина иконки при данном размере (для раскладки/центрирования). */
    public static float width(Glyph glyph, float size) {
        if (glyph == null) return 0f;
        if (isBrand(glyph)) return BRAND_FONT.get().getWidth(glyph.asString(), size);
        if (isVector(glyph)) return PAUSE_W_FACTOR * size;
        FontRenderer f = font(glyph, size);
        return f == null ? 0f : f.getStringWidth(glyph.asString());
    }

    /** Высота иконки при данном размере. */
    public static float height(Glyph glyph, float size) {
        if (glyph == null) return 0f;
        if (isVector(glyph)) return PAUSE_H_FACTOR * size;
        // у MSDF-атласа бренда getHeight нет — эм-бокс глифа ≈ size
        if (isBrand(glyph)) return size;
        FontRenderer f = font(glyph, size);
        return f == null ? 0f : f.getStringHeight(glyph.asString());
    }

    /** Иконка, центрированная по точке (cx, cy). */
    public static void drawCentered(MatrixStack m, Glyph glyph, float cx, float cy, float size, int argb) {
        if (glyph == null || m == null) return;
        if (isBrand(glyph)) {
            drawBrand(m, cx - width(glyph, size) / 2f, cy - size / 2f, size, argb);
            return;
        }
        if (isVector(glyph)) {
            float barW = size * 0.13f;
            float barH = size * PAUSE_H_FACTOR;
            float gap = size * 0.12f;
            float total = PAUSE_W_FACTOR * size;
            float round = Math.min(barW, barH) / 2f;
            float x0 = cx - total / 2f;
            float y0 = cy - barH / 2f;
            UiRender.rect(m, x0, y0, barW, barH, round, argb);
            UiRender.rect(m, x0 + barW + gap, y0, barW, barH, round, argb);
            return;
        }
        FontRenderer f = font(glyph, size);
        if (f == null) return;
        String s = glyph.asString();
        f.drawString(m, s, cx - f.getStringWidth(s) / 2f, cy - f.getStringHeight(s) / 2f, argb);
    }

    /** Иконка, центрированная внутри прямоугольника (x, y, w, h). */
    public static void drawInBox(MatrixStack m, Glyph glyph, float x, float y, float w, float h, float size, int argb) {
        drawCentered(m, glyph, x + w / 2f, y + h / 2f, size, argb);
    }
}
