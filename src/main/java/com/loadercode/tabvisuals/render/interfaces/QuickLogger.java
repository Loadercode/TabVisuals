/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.render.interfaces;

import net.minecraft.text.Text;
import net.minecraft.util.Formatting;


public interface QuickLogger {

    static Text getPrefix() {
        return QuickLoggerHelper.getPrefix();
    }

    default void logDirect(Text... components) {
        QuickLoggerHelper.logDirect(components);
    }

    default void logDirect(String message, Formatting color) {
        QuickLoggerHelper.logDirect(message, color);
    }

    default void logDirect(String message) {
        QuickLoggerHelper.logDirect(message, Formatting.GRAY);
    }
}