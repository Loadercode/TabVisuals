// language: Java, file: StringHelper.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.gui.hud.elements;

import com.loadercode.tabvisuals.render.font.FontRenderer;
import com.loadercode.tabvisuals.render.font.Fonts;

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

    /** GLFW key → "W", "1", "MOUSE 4", "LSHIFT" style label. */
    public static String getBindName(int key) {
        if (key < 0) {
            return "N/A";
        }
        return HudHelper.getKeyType(key).createFromCode(key).getTranslationKey()
                .replace("key.keyboard.", "")
                .replace("key.mouse.", "mouse ")
                .replace(".", " ")
                .toUpperCase();
    }

    public static String wrap(String input, int width, int size) {
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

    /** Seconds → "M:SS". */
    public static String getDuration(int time) {
        int mins = time / 60;
        String sec = String.format("%02d", time % 60);
        return mins + ":" + sec;
    }

    public static String trimToWidth(String text, float maxWidth, FontRenderer font) {
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
