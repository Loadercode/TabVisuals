/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.render.systemrender.builders;

import com.loadercode.tabvisuals.render.systemrender.builders.impl.*;



public final class Builder {

    // Билдеры больше не синглтоны: каждый вызов отдаёт свежий экземпляр,
    // поэтому вложенный build() из колбэка не портит состояние чужой цепочки.
    // Сигнатуры методов не менялись.

    public static RectangleBuilder rectangle() {
        return new RectangleBuilder();
    }

    public static BorderBuilder border() {
        return new BorderBuilder();
    }

    public static TextureBuilder texture() {
        return new TextureBuilder();
    }

    public static TextBuilder text() {
        return new TextBuilder();
    }

    public static BlurBuilder blur() {
        return new BlurBuilder();
    }

}