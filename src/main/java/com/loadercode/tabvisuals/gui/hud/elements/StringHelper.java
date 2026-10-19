// language: Java, file: StringHelper.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.gui.hud.elements;

import com.loadercode.tabvisuals.render.font.FontRenderer;
import com.loadercode.tabvisuals.render.font.Fonts;

import java.util.Locale;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** LoxeDLC StringHelper: bind naming, wrapping and time formatting. */
public final class StringHelper {

    private static final Random RANDOM = new Random();

    private StringHelper() {
    }

    public static String randomString(int length) {
        return IntStream.range(0, length)
                .mapToObj(i -> String.valueOf((char) RANDOM.nextInt('a', 'z' + 1)))
                .collect(Collectors.joining());
    }

    /** GLFW key → bind label; delegates to the single source of truth
     *  ({@code KeySetting.keyLabel}) so HUD rows and ClickGUI chips always match. */
    public static String getBindName(int key) {
        return com.loadercode.tabvisuals.gui.setting.KeySetting.keyLabel(key);
    }

    public static String wrap(String input, int width, int size) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        String[] words = input.split(" ");
        StringBuilder output = new StringBuilder();
        float lineWidth = 0;
        float space = Fonts.getSize(size).getStringWidth(" ");
        for (String word : words) {
            float wordWidth = Fonts.getSize(size).getStringWidth(word);
            if (lineWidth + wordWidth > width) {
                output.append('\n');
                lineWidth = 0;
            } else if (lineWidth > 0) {
                output.append(' ');
                lineWidth += space;
            }
            output.append(word);
            lineWidth += wordWidth;
        }
        return output.toString();
    }

    /**
     * Единственный формат «часы:минуты / минуты:секунды» HUD-а — «H:MM».
     * Locale.ROOT: в некоторых локалях %d/%02d дают не-латинские цифры,
     * а раскладка/выравнивание меряются под точный текст.
     */
    public static String format_clock(long hour, long minute) {
        return String.format(Locale.ROOT, "%d:%02d", hour, minute);
    }

    /** Seconds → "M:SS". */
    public static String getDuration(int time) {
        // Отрицательные секунды (истёкший кулдаун) не должны давать "-1:59"
        int clamped = Math.max(0, time);
        return format_clock(clamped / 60, clamped % 60);
    }

    public static String trimToWidth(String text, float maxWidth, FontRenderer font) {
        if (text == null || text.isEmpty() || maxWidth <= 0f) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (font.getStringWidth(builder.toString() + c) > maxWidth) {
                break;
            }
            builder.append(c);
        }
        return builder.toString();
    }
}
