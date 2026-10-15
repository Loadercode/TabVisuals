/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.render.color;

import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.chars.Char2IntArrayMap;
import lombok.Getter;
import lombok.experimental.UtilityClass;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.MathHelper;
import org.joml.Vector4i;
import org.lwjgl.opengl.GL11;
import com.loadercode.tabvisuals.render.math.calc.Calculate;

import java.awt.*;
import java.nio.ByteBuffer;
import java.util.regex.Pattern;

@Getter
@UtilityClass
public class ColorAssist {

    public static final int COLOR_FOR_RECTS_CUSTOM = pack(91,  63,  212, 255);
    public static final int COLOR_FOR_RECTS_BLACK  = pack(26,  26,  26,  255);
    public static final int COLOR_FOR_TEXT_WHITE   = pack(255, 255, 255, 255);
    public static final int COLOR_FOR_TEXT_CUSTOM  = pack(130, 100, 210, 255);

    public static int colorForRectsCustom$() { return COLOR_FOR_RECTS_CUSTOM; }
    public static int colorForRectsBlack$()  { return COLOR_FOR_RECTS_BLACK;  }
    public static int colorForTextWhite$()   { return COLOR_FOR_TEXT_WHITE;   }
    public static int colorForTextCustom$()  { return COLOR_FOR_TEXT_CUSTOM;  }

    public static final int green  = pack(64,  255, 64,  255);
    public static final int yellow = pack(255, 255, 64,  255);
    public static final int orange = pack(255, 128, 32,  255);
    public static final int red    = pack(255, 64,  64,  255);

    public static final Pattern FORMATTING_CODE_PATTERN = Pattern.compile("(?i)§[0-9a-f-or]");
    public Char2IntArrayMap colorCodes = new Char2IntArrayMap() {{
        put('0', 0x000000); put('1', 0x0000AA); put('2', 0x00AA00); put('3', 0x00AAAA);
        put('4', 0xAA0000); put('5', 0xAA00AA); put('6', 0xFFAA00); put('7', 0xAAAAAA);
        put('8', 0x555555); put('9', 0x5555FF); put('A', 0x55FF55); put('B', 0x55FFFF);
        put('C', 0xFF5555); put('D', 0xFF55FF); put('E', 0xFFFF55); put('F', 0xFFFFFF);
    }};

    /** Пустая заглушка — ConcurrentHashMap/ScheduledExecutorService удалены. */
    public static void shutdown() {}

    public final int RED        = pack(255, 0,   0,   255);
    public final int GREEN      = pack(0,   255, 0,   255);
    public final int BLUE       = pack(0,   0,   255, 255);
    public final int YELLOW     = pack(255, 255, 0,   255);
    public final int WHITE      = pack(255, 255, 255, 255);
    public final int BLACK      = pack(0,   0,   0,   255);
    public final int HALF_BLACK = pack(0,   0,   0,   128);
    public final int LIGHT_RED  = pack(255, 85,  85,  255);

    public static final int GUI_DARK_BLUR             = pack(0,   0,   0,   200);
    public static final int GUI_DARK_OUTLINE          = pack(18,  19,  20,  225);
    public static final int GUI_DARK_GRADIENT_MAIN    = pack(18,  19,  20,  175);
    public static final int GUI_DARK_GRADIENT_SIDE    = pack(0,   2,   5,   175);
    public static final int GUI_DARK_RECT             = pack(0x1A, 0x1A, 0x1F, 255);
    public static final int GUI_DARK_RECT_2           = pack(0x1E, 0x1E, 0x26, 255);
    public static final int GUI_DARK_TEXT             = pack(255, 255, 255, 255);
    public static final int GUI_DARK_TEXT_SECONDARY   = pack(175, 175, 175, 255);
    public static final int GUI_DARK_OUTLINE_ELEMENT  = pack(0x37, 0x37, 0x46, 255);

    private static boolean _cacheValid        = false;
    private static int     _cachedText        = GUI_DARK_TEXT;
    private static int     _cachedText2       = GUI_DARK_TEXT_SECONDARY;
    private static int     _cachedBlurColor   = pack(0, 0, 0, 150);
    private static int     _cachedRectColor   = pack(18, 19, 20, 75);
    private static int     _cachedRectOutline = pack(18, 19, 20, 35);
    private static int     _cachedOutlineEl   = GUI_DARK_OUTLINE_ELEMENT;
    private static int[]   _cachedGradient    = {
            pack(18,19,20,75), pack(0,2,5,75), pack(0,2,5,75), pack(18,19,20,75)
    };
    private static int     _cachedRect1       = pack(0, 0, 0, 228);
    private static int     _cachedRectDarker1 = pack(0x18, 0x18, 0x1E, 255);

    /** Сброс кэша цветов — вызывать при смене конфигурации темы. */
    public static void flushFrameCache() {
        _cacheValid = false;
    }

    // --- TabVisuals theme config (replaces the LoxeDLC HUD feature coupling) ---
    public static volatile boolean lightTheme = false;
    public static volatile boolean glassMode = false;
    public static volatile float glassOpacity = 1f;
    public static volatile boolean hudBlurMode = false;
    /** Акцент клиента («Цвет клиента») — кормится драйвером из HudTheme. */
    public static volatile int accentColor = COLOR_FOR_RECTS_CUSTOM;

    // Полная тема (тёмная/светлая/кастомная) живёт в HudTheme — драйвер
    // передаёт её сюда, чтобы портированные LoxeDLC-элементы совпадали с
    // остальным HUD. Пока override не установлен, работает легаси-путь ниже.
    private static volatile boolean overrideActive = false;
    private static volatile boolean overrideCustom = false;
    private static volatile int ovText = GUI_DARK_TEXT;
    private static volatile int ovText2 = GUI_DARK_TEXT_SECONDARY;
    private static volatile int ovBlur = 0;
    private static volatile int ovRect = 0;
    private static volatile int ovRectOutline = 0;
    private static volatile int ovOutline = GUI_DARK_OUTLINE_ELEMENT;
    private static volatile int ovRect1 = 0;
    private static volatile int ovRectDarker = 0;
    private static volatile int[] ovGradient;

    /** Один вызов в кадр от HUD-драйвера: значения темы из HudTheme. */
    public static void setThemeOverride(boolean custom, int text, int text2, int blur,
                                        int rect, int rectOutline, int[] gradient,
                                        int outline, int rect1, int rectDarker, int accent) {
        overrideActive = true;
        overrideCustom = custom;
        ovText = text;
        ovText2 = text2;
        ovBlur = blur;
        ovRect = rect;
        ovRectOutline = rectOutline;
        ovGradient = gradient;
        ovOutline = outline;
        ovRect1 = rect1;
        ovRectDarker = rectDarker;
        accentColor = accent;
    }

    private static boolean isLightTheme() {
        return lightTheme;
    }

    private static int withAlpha(int color, int alpha) {
        return pack(red(color), green(color), blue(color), alpha);
    }

    private static int[] themePair(int first, int second) {
        return new int[]{first, second, second, first};
    }

    private static int[] themeSolid(int color) {
        return new int[]{color, color, color, color};
    }

    private static void ensureCache() {
        if (_cacheValid) return;

        if (overrideActive && ovGradient != null) {
            _cachedText        = ovText;
            _cachedText2       = ovText2;
            _cachedBlurColor   = ovBlur;
            _cachedRectColor   = ovRect;
            _cachedRectOutline = ovRectOutline;
            _cachedOutlineEl   = ovOutline;
            _cachedGradient    = ovGradient;
            _cachedRect1       = ovRect1;
            _cachedRectDarker1 = ovRectDarker;
            _cacheValid = true;
            return;
        }

        boolean isGlass = glassMode;
        boolean isHudBlur = hudBlurMode;
        boolean isWhite = isLightTheme();
        float   opacity = glassMode ? glassOpacity : 1f;

        int blurA = Math.min(isGlass ? (int)(100 * opacity) : (isHudBlur ? 200 : 150), 255);
        int rectA = Math.min(isGlass ? (int)(50  * opacity) : 75,  255);
        int outA  = Math.min(isGlass ? (int)(25  * opacity) : 35,  255);

        {
            _cachedText  = isWhite ? pack(30,  30,  30,  255) : GUI_DARK_TEXT;
            _cachedText2 = isWhite ? pack(100, 100, 100, 255) : GUI_DARK_TEXT_SECONDARY;

            _cachedBlurColor   = isWhite ? pack(255, 255, 255, blurA) : pack(0, 0, 0, blurA);
            _cachedRectColor   = isWhite ? pack(255, 255, 255, rectA) : pack(18, 19, 20, rectA);
            _cachedRectOutline = isWhite ? pack(224, 224, 224, outA)  : pack(18, 19, 20, outA);

            _cachedOutlineEl = isGlass  ? pack(60,  65,  75,  180)
                    : isWhite  ? pack(224, 224, 224, 255)
                    : GUI_DARK_OUTLINE_ELEMENT;

            _cachedGradient = isWhite
                    ? new int[]{ pack(255,255,255,rectA), pack(245,245,250,rectA),
                    pack(245,245,250,rectA), pack(255,255,255,rectA) }
                    : new int[]{ pack(18,19,20,rectA),    pack(0,2,5,rectA),
                    pack(0,2,5,rectA),       pack(18,19,20,rectA) };

            _cachedRect1       = isWhite ? pack(255,255,255,232) : pack(0,0,0,228);
            _cachedRectDarker1 = isWhite ? pack(240,240,240,255) : pack(0x18,0x18,0x1E,255);
        }

        _cacheValid = true;
    }

    public int getText() { ensureCache(); return _cachedText; }
    public int getText(float alpha) { return multAlpha(getText(), alpha); }
    public int getText2() { ensureCache(); return _cachedText2; }

    public int getBlurColor()          { ensureCache(); return _cachedBlurColor;   }
    public int getRectangleColor()     { ensureCache(); return _cachedRectColor;   }
    public int getRectangleOutlineColor() { ensureCache(); return _cachedRectOutline; }
    public int getOutlineColor()       { ensureCache(); return _cachedOutlineEl;   }

    public int[] getRectangleGradientColors() {
        ensureCache();
        return _cachedGradient;
    }

    public int getRect(float alpha) {
        ensureCache();
        if (alpha == 1f) return _cachedRect1;
        return multAlpha(_cachedRect1, alpha);
    }

    public int getRectDarker(float alpha) {
        ensureCache();
        if (alpha == 1f) return _cachedRectDarker1;
        return multAlpha(_cachedRectDarker1, alpha);
    }

    public int getColor(int red, int green, int blue, int alpha) {
        return pack(red, green, blue, alpha);
    }
    public int getColor(int red, int green, int blue) {
        return pack(red, green, blue, 255);
    }
    public int getColor(float red, float green, float blue, float alpha) {
        return pack(Math.round(red*255), Math.round(green*255), Math.round(blue*255), Math.round(alpha*255));
    }
    public int getColor(int red, int green, int blue, float alpha) {
        return pack(red, green, blue, Math.round(alpha * 255));
    }
    public int getColor(float red, float green, float blue) {
        return pack(Math.round(red*255), Math.round(green*255), Math.round(blue*255), 255);
    }
    public int getColor(int brightness, int alpha) {
        return pack(brightness, brightness, brightness, alpha);
    }
    public int getColor(int brightness, float alpha) {
        return pack(brightness, brightness, brightness, Math.round(alpha * 255));
    }
    public int getColor(int brightness) {
        return pack(brightness, brightness, brightness, 255);
    }

    /** Внутренний упаковщик ARGB — все getColor() ведут сюда. */
    public static int pack(int r, int g, int b, int a) {
        return ((MathHelper.clamp(a,0,255) << 24) |
                (MathHelper.clamp(r,0,255) << 16) |
                (MathHelper.clamp(g,0,255) << 8)  |
                MathHelper.clamp(b,0,255));
    }

    // ----------------------------------------------------------------
    //  Остальные методы без изменений
    // ----------------------------------------------------------------
    public int red(int c)   { return (c >> 16) & 0xFF; }
    public int green(int c) { return (c >> 8)  & 0xFF; }
    public int blue(int c)  { return  c        & 0xFF; }
    public int alpha(int c) { return (c >> 24) & 0xFF; }

    public float redf(int c)   { return red(c)   / 255.0f; }
    public float greenf(int c) { return green(c) / 255.0f; }
    public float bluef(int c)  { return blue(c)  / 255.0f; }
    public float alphaf(int c) { return alpha(c) / 255.0f; }

    public int[] getRGBA(int c)  { return new int[]{red(c), green(c), blue(c), alpha(c)}; }
    public int[] getRGB(int c)   { return new int[]{red(c), green(c), blue(c)}; }
    public float[] getRGBAf(int c) { return new float[]{redf(c), greenf(c), bluef(c), alphaf(c)}; }
    public float[] getRGBf(int c)  { return new float[]{redf(c), greenf(c), bluef(c)}; }

    public int replAlpha(int color, int alpha)   { return pack(red(color), green(color), blue(color), alpha); }
    public int replAlpha(int color, float alpha) { return pack(red(color), green(color), blue(color), Math.round(alpha * 255)); }

    public int multAlpha(int color, float percent01) {
        return pack(red(color), green(color), blue(color), Math.round(alpha(color) * percent01));
    }

    public static int applyOpacity(int hex, int percent) {
        return applyOpacity(hex, 2.55f * MathHelper.clamp(percent, 0, 100));
    }
    public static int applyOpacity(int hex, float opacity) {
        // opacity задаётся в 0..255; наружу уходило неклампнутое значение,
        // и alpha > 255 переполняла байт (цвет становился прозрачным)
        int alpha = Math.round(getAlpha(hex) * (opacity / 255.0f));
        return pack(getRed(hex), getGreen(hex), getBlue(hex), alpha);
    }

    public static int lerp(float value, int from, int to) { return ColorHelper.lerp(value, from, to); }

    public static int pixelColor(int x, int y) {
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4);
        GL11.glReadPixels(x, y, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, byteBuffer);
        byteBuffer.rewind();
        // Раньше байты шли в getRed/getGreen, которые сдвигают на 16/8 бит:
        // для байта результат всегда 0 — цвет возвращался мусорный.
        int r = byteBuffer.get() & 0xFF;
        int g = byteBuffer.get() & 0xFF;
        int b = byteBuffer.get() & 0xFF;
        return ColorHelper.getArgb(255, r, g, b);
    }

    public static int calculateHuyDegrees(int divisor, int offset) {
        // divisor приходит из конфига и может быть 0 → ArithmeticException на рендер-потоке
        return (int) ((System.currentTimeMillis() / Math.max(1, divisor) + offset) % 360L);
    }

    public static int reAlphaInt(final int color, final int alpha) {
        return (Math.clamp(alpha, 0, 255) << 24) | (color & 16777215);
    }

    public static int astolfo(int speed, int index, float saturation, float brightness, float alpha) {
        float hueStep = 360.0f / 4.0f;
        float basaHuy = (float) calculateHuyDegrees(speed, index);
        float huy = ((basaHuy + index * hueStep) % 360.0f) / 360.0f;
        saturation = Math.clamp(saturation, 0.0f, 1.0f);
        brightness = Math.clamp(brightness, 0.0f, 1.0f);
        int rgb = Color.HSBtoRGB(huy, saturation, brightness);
        int Ialpha = Math.max(0, Math.min(255, (int)(alpha * 255.0F)));
        return reAlphaInt(rgb, Ialpha);
    }

    public static int interpolateColor(int color1, int color2, float amount) {
        amount = Math.min(1, Math.max(0, amount));
        return pack(
                interpolateInt(getRed(color1),   getRed(color2),   amount),
                interpolateInt(getGreen(color1), getGreen(color2), amount),
                interpolateInt(getBlue(color1),  getBlue(color2),  amount),
                interpolateInt(getAlpha(color1), getAlpha(color2), amount)
        );
    }

    public static Double interpolateD(double oldValue, double newValue, double interpolationValue) {
        return oldValue + (newValue - oldValue) * interpolationValue;
    }

    public static int interpolateInt(int oldValue, int newValue, double interpolationValue) {
        return interpolateD(oldValue, newValue, interpolationValue).intValue();
    }

    public static int getRed(final int hex)   { return (hex >> 16) & 255; }
    public static int getGreen(final int hex) { return (hex >> 8)  & 255; }
    public static int getBlue(final int hex)  { return  hex        & 255; }
    public static int getAlpha(final int hex) { return (hex >> 24) & 255; }

    public int multColor(int colorStart, int colorEnd, float progress) {
        return pack(Math.round(red(colorStart)   * (redf(colorEnd)   * progress)),
                Math.round(green(colorStart) * (greenf(colorEnd) * progress)),
                Math.round(blue(colorStart)  * (bluef(colorEnd)  * progress)),
                Math.round(alpha(colorStart) * (alphaf(colorEnd) * progress)));
    }

    public int multRed(int colorStart, int colorEnd, float progress) {
        return pack(Math.round(red(colorStart)   * (redf(colorEnd)   * progress)),
                Math.round(green(colorStart) * (greenf(colorEnd) * progress)),
                Math.round(blue(colorStart)  * (bluef(colorEnd)  * progress)),
                Math.round(alpha(colorStart) * (alphaf(colorEnd) * progress)));
    }

    public int multDark(int color, float percent01) {
        return pack(Math.round(red(color)   * percent01),
                Math.round(green(color) * percent01),
                Math.round(blue(color)  * percent01),
                alpha(color));
    }

    public int multBright(int color, float percent01) {
        // percent01 == 0 давал Infinity → Math.round → 255 на всех каналах (неправильный цвет)
        if (percent01 == 0.0f || Float.isNaN(percent01)) return color;
        return pack(Math.min(255, Math.round(red(color)   / percent01)),
                Math.min(255, Math.round(green(color) / percent01)),
                Math.min(255, Math.round(blue(color)  / percent01)),
                alpha(color));
    }

    public int overCol(int color1, int color2, float percent01) {
        final float p = MathHelper.clamp(percent01, 0F, 1F);
        return pack((int) MathHelper.lerp(p, red(color1),   red(color2)),
                (int) MathHelper.lerp(p, green(color1), green(color2)),
                (int) MathHelper.lerp(p, blue(color1),  blue(color2)),
                (int) MathHelper.lerp(p, alpha(color1), alpha(color2)));
    }

    public Vector4i multRedAndAlpha(Vector4i color, float red, float alpha) {
        // Порядок компонент сохраняется: z и w раньше менялись местами,
        // из-за чего верхние углы квада получали цвета друг друга.
        return new Vector4i(multRedAndAlpha(color.x, red, alpha), multRedAndAlpha(color.y, red, alpha),
                multRedAndAlpha(color.z, red, alpha), multRedAndAlpha(color.w, red, alpha));
    }

    public int multRedAndAlpha(int color, float red, float alpha) {
        // red == 0 → Infinity на зелёном/синем; стабилизируем делитель
        float d = red == 0.0f || Float.isNaN(red) ? 1.0f : red;
        return pack(red(color),
                Math.min(255, Math.round(green(color) / d)),
                Math.min(255, Math.round(blue(color)  / d)),
                Math.round(alpha(color) * alpha));
    }

    public int multRed(int color, float percent01) {
        if (percent01 == 0.0f || Float.isNaN(percent01)) return color;
        return pack(red(color),
                Math.min(255, Math.round(green(color) / percent01)),
                Math.min(255, Math.round(blue(color)  / percent01)),
                alpha(color));
    }

    public int multGreen(int color, float percent01) {
        if (percent01 == 0.0f || Float.isNaN(percent01)) return color;
        return pack(Math.min(255, Math.round(green(color) / percent01)),
                green(color),
                Math.min(255, Math.round(blue(color)  / percent01)),
                alpha(color));
    }

    public int rgba(int red, int green, int blue, int alpha) { return pack(red, green, blue, alpha); }

    public int[] genGradientForText(int color1, int color2, int length) {
        if (length <= 0) return new int[0];
        int[] gradient = new int[length];
        // length == 1 → (length - 1) == 0 → 0/0 = NaN, вместо цвета выходил прозрачный чёрный
        float step = length == 1 ? 0.0f : 1.0f / (length - 1);
        for (int i = 0; i < length; i++) {
            gradient[i] = overCol(color1, color2, i * step);
        }
        return gradient;
    }

    public static float[] rgba(final int color) {
        return new float[]{(color >> 16 & 0xFF) / 255f, (color >> 8 & 0xFF) / 255f,
                (color       & 0xFF) / 255f, (color >> 24 & 0xFF) / 255f};
    }

    public static int interpolate(int start, int end, float value) {
        // фактор вне [0..1] давал экстраполяцию каналов за 0..255 (NaN → чёрный)
        value = MathHelper.clamp(value, 0.0f, 1.0f);
        float[] s = rgba(start), e2 = rgba(end);
        return rgba((int) Calculate.interpolate(s[0]*255, e2[0]*255, value),
                (int) Calculate.interpolate(s[1]*255, e2[1]*255, value),
                (int) Calculate.interpolate(s[2]*255, e2[2]*255, value),
                (int) Calculate.interpolate(s[3]*255, e2[3]*255, value));
    }

    public int rainbow(int speed, int index, float saturation, float brightness, float opacity) {
        int angle = (int)((System.currentTimeMillis() / Math.max(1, speed) + index) % 360);
        int color = Color.HSBtoRGB(angle / 360f, saturation, brightness);
        return pack(red(color), green(color), blue(color), Math.round(opacity * 255));
    }

    public int fade(int speed, int index, int first, int second) {
        int angle = (int)((System.currentTimeMillis() / Math.max(1, speed) + index) % 360);
        angle = angle >= 180 ? 360 - angle : angle;
        return overCol(first, second, angle / 180f);
    }

    public int rgbaFloat(float r, float g, float b, float a) {
        return (int)(MathHelper.clamp(a,0,1)*255) << 24 | (int)(MathHelper.clamp(r,0,1)*255) << 16
                | (int)(MathHelper.clamp(g,0,1)*255) << 8  | (int)(MathHelper.clamp(b,0,1)*255);
    }

    public int fade(int index) {
        return fade(8, index, getClientColor(), -1);
    }

    public Vector4i roundClientColor(float alpha) {
        return new Vector4i(multAlpha(fade(270), alpha), multAlpha(fade(0),   alpha),
                multAlpha(fade(180), alpha), multAlpha(fade(90),  alpha));
    }

    public static int rgb(int r, int g, int b) { return pack(r, g, b, 255); }

    public static void setAlphaColor(final int color, final float alpha) {
        RenderSystem.setShaderColor((color >> 16 & 255) / 255f,
                (color >> 8  & 255) / 255f,
                (color       & 255) / 255f, alpha);
    }

    public static void setColor(int color) {
        setAlphaColor(color, (float)(color >> 24 & 255) / 255.0F);
    }

    public static int toColor(String hexColor) {
        // null / без '#' / неверная длина / не-hex → прозрачный чёрный вместо исключения
        if (hexColor == null || hexColor.isEmpty() || hexColor.charAt(0) != '#') {
            return 0x00000000;
        }
        String digits = hexColor.substring(1);
        if (digits.length() != 6) {
            return 0x00000000;
        }
        try {
            return setAlpha(Integer.parseInt(digits, 16), 255);
        } catch (NumberFormatException e) {
            return 0x00000000;
        }
    }

    public static int setAlpha(int color, int alpha) { return (MathHelper.clamp(alpha, 0, 255) << 24) | (color & 0x00ffffff); }

    public static int gradient(int start, int end, int index, int speed) {
        int angle = (int)((System.currentTimeMillis() / Math.max(1, speed) + index) % 360);
        angle = (angle > 180 ? 360 - angle : angle) + 180;
        int color = interpolate(start, end, Math.clamp(angle / 180f - 1, 0, 1));
        float[] hs = rgba(color);
        float[] hsb = Color.RGBtoHSB((int)(hs[0]*255), (int)(hs[1]*255), (int)(hs[2]*255), null);
        hsb[1] = Math.min(hsb[1] * 1.5F, 1.0f);
        return Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]);
    }

    public static int gradient(int speed, int index, int... colors) {
        if (colors.length == 0) return 0; // без палитры брать нечего — стабильный пустой цвет
        int angle = (int)((System.currentTimeMillis() / Math.max(1, speed) + index) % 360);
        angle = (angle > 180 ? 360 - angle : angle) + 180;
        int colorIndex = (int)(angle / 360f * colors.length);
        if (colorIndex == colors.length) colorIndex--;
        int color2Index = colorIndex == colors.length - 1 ? 0 : colorIndex + 1;
        return interpolate(colors[colorIndex], colors[color2Index], angle / 360f * colors.length - colorIndex);
    }

    public String removeFormatting(String text) {
        return text == null || text.isEmpty() ? null : FORMATTING_CODE_PATTERN.matcher(text).replaceAll("");
    }

    public int getMainGuiColor() {
        return pack(20, 20, 24, 255);
    }

    public int getGuiRectColor(float alpha) {
        return multAlpha(isLightTheme() ? pack(250,250,252,255) : GUI_DARK_RECT, alpha);
    }

    public int getGuiRectColor2(float alpha) {
        return multAlpha(isLightTheme() ? pack(245,245,248,255) : GUI_DARK_RECT_2, alpha);
    }

    public int getClickGuiBlurColor() {
        return isLightTheme() ? pack(255,255,255,200) : GUI_DARK_BLUR;
    }

    public int getClickGuiOutlineColor(float alpha) {
        int c = isLightTheme() ? pack(220,220,220,255) : GUI_DARK_OUTLINE;
        return multAlpha(c, alpha);
    }

    public int[] getClickGuiGradientColors(float alpha) {
        int ai = (int)(175 * alpha);
        if (isLightTheme()) {
            return new int[]{ pack(250,250,252,ai), pack(248,248,252,ai), pack(248,248,252,ai), pack(250,250,252,ai) };
        }
        return new int[]{ multAlpha(GUI_DARK_GRADIENT_MAIN,alpha), multAlpha(GUI_DARK_GRADIENT_SIDE,alpha),
                multAlpha(GUI_DARK_GRADIENT_SIDE,alpha), multAlpha(GUI_DARK_GRADIENT_MAIN,alpha) };
    }

    public int[] getModuleCardGradientColors(int brightnessOffset, int alphaOffset) {
        if (isLightTheme()) {
            int corner = Math.min(145 + alphaOffset, 255);
            int b      = Math.min(215 + brightnessOffset, 255);
            return new int[]{ pack(228,228,232,corner), pack(b,b,Math.min(b+5,255),255),
                    pack(218,218,224,corner), pack(b,b,Math.min(b+5,255),255) };
        }
        int bo = brightnessOffset;
        return new int[]{ pack(23,24,25,145),
                pack(Math.min(19+bo,255),Math.min(19+bo,255),Math.min(21+bo,255),255),
                pack(10,12,15,145),
                pack(Math.min(19+bo,255),Math.min(19+bo,255),Math.min(21+bo,255),255) };
    }

    public int[] getModuleCardSeparatorColors() {
        if (isLightTheme()) return new int[]{ pack(200,200,205,200), pack(188,188,195,200), pack(188,188,195,200), pack(200,200,205,200) };
        return new int[]{ pack(25,25,40,155), pack(55,55,60,155), pack(55,55,60,155), pack(25,25,40,155) };
    }

    public int[] getCategoryTabHoverColors() {
        if (isLightTheme()) return new int[]{ pack(240,240,242,220), pack(230,230,235,220), pack(230,230,235,220), pack(240,240,242,220) };
        return new int[]{ pack(55,55,55,100), pack(85,85,100,100), pack(55,55,55,100), pack(85,85,100,100) };
    }

    public int[] getCategoryTabSelectedColors(int alpha) {
        if (isLightTheme()) return new int[]{ pack(250,250,252,alpha), pack(235,235,240,alpha), pack(235,235,240,alpha), pack(250,250,252,alpha) };
        return new int[]{ pack(21,21,21,alpha), pack(61,61,61,alpha), pack(61,61,61,alpha), pack(21,21,21,alpha) };
    }

    public int[] getScrollbarTrackColors() {
        if (isLightTheme()) return new int[]{ pack(235,235,238,120), pack(225,225,230,150), pack(225,225,230,150), pack(235,235,238,120) };
        return new int[]{ pack(35,35,35,75), pack(45,45,45,95), pack(45,45,45,95), pack(35,35,35,75) };
    }

    public int[] getScrollbarThumbColors() {
        if (isLightTheme()) return new int[]{ pack(200,200,205,120), pack(170,170,178,150), pack(170,170,178,150), pack(200,200,205,120) };
        return new int[]{ pack(44,44,44,75), pack(101,101,101,95), pack(101,101,101,95), pack(44,44,44,75) };
    }

    public int getOutline()                        { ensureCache(); return _cachedOutlineEl; }
    public int getOutline(float alpha)             { return multAlpha(getOutline(), alpha); }
    public int getOutline(float alpha, float bright) { return multBright(multAlpha(getOutline(), alpha), bright); }

    public int getClientColor() {
        return accentColor;
    }
    public int getClientColor(float alpha)  { return multAlpha(getClientColor(), alpha); }
    public int getFriendColor()             { return pack(0x55, 0xFF, 0x55, 255); }

    public static class IntColor {
        public static float[] rgb(final int color) {
            return new float[]{ (color>>16&0xFF)/255f, (color>>8&0xFF)/255f, (color&0xFF)/255f, (color>>24&0xFF)/255f };
        }
        public static int rgba(int r, int g, int b, int a) { return a<<24|r<<16|g<<8|b; }
        public static int rgb(int r, int g, int b)         { return 255<<24|r<<16|g<<8|b; }
        public static int getRed(final int hex)            { return hex>>16&255; }
        public static int getGreen(final int hex)          { return hex>>8&255; }
        public static int getBlue(final int hex)           { return hex&255; }
        public static int getAlpha(final int hex)          { return hex>>24&255; }
    }

    public String formatting(int color) { return "⏏" + color + "⏏"; }
}
