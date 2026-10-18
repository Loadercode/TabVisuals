/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.render.interfaces;

import com.google.gson.Gson;
import net.minecraft.client.MinecraftClient;
import com.loadercode.tabvisuals.render.draw.DrawEngine;
import com.loadercode.tabvisuals.render.draw.DrawEngineImpl;
import com.loadercode.tabvisuals.render.shape.implement.*;
import com.loadercode.tabvisuals.render.shape.implement.Arc;
import com.loadercode.tabvisuals.render.shape.implement.Blur;
import com.loadercode.tabvisuals.render.shape.implement.Image;
import com.loadercode.tabvisuals.render.shape.implement.Rectangle;

public interface QuickImports extends QuickLogger {
    MinecraftClient mc = MinecraftClient.getInstance();

    DrawEngine drawEngine = new DrawEngineImpl();

    Rectangle rectangle = new Rectangle();
    Blur blur = new Blur();
    Arc arc = new Arc();
    Image image = new Image();

    Gson gson = new Gson();
}
