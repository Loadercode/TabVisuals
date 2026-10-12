// language: Java, file: MusicManager.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.gui.music;

/**
 * Track metadata + synchronized lyrics source for the title screens.
 * Placeholder data for now — Spotify / VK Music / system-audio hooks
 * (cover art, title, lines) land behind these accessors later.
 */
public final class MusicManager {

    private MusicManager() {
    }

    public static String trackTitle() {
        return "Midnight Drive";
    }

    public static String artist() {
        return "NOIR";
    }

    /** Previous synchronized lyric line — small dim text above the current one in the menu. */
    public static String prevLine() {
        return "где-то между нами снова выключили свет";
    }

    /** Current synchronized lyric line — large text on the splash, right block in the menu. */
    public static String currentLine() {
        return "Она ждала так долго, что отдала себя в Убере (давай)";
    }

    /** Next line / translation — small dim text under the current one. */
    public static String nextLine() {
        return "и этот город снова горит, пока мы молчим";
    }
}
