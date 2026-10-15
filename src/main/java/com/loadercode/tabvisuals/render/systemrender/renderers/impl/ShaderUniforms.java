/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.render.systemrender.renderers.impl;

import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashSet;
import java.util.Set;

/**
 * Безопасный доступ к uniform'ам: на GL 3.2 fallback или при неудачной
 * загрузке шейдера getUniform() возвращает null. Раньше это превращалось
 * в NPE посреди кадра (чёрный экран / краш клиента), теперь draw-call
 * просто пропускается, а предупреждение пишется один раз на шейдер.
 */
final class ShaderUniforms {

    private static final Logger LOGGER = LogManager.getLogger("TabVisuals");
    private static final Set<String> WARNED_SHADERS = new HashSet<>();

    private ShaderUniforms() {}

    /** Возвращает uniform или null (null — сигнал пропустить draw-call). */
    static GlUniform get(ShaderProgram shader, String shaderName, String uniformName) {
        GlUniform uniform = shader == null ? null : shader.getUniform(uniformName);
        if (uniform == null) {
            warnOnce(shaderName, shader == null
                    ? "шейдер не загружен"
                    : "нет uniform '" + uniformName + "'");
        }
        return uniform;
    }

    /** true, если все uniform'ы найдены; иначе draw-call нужно пропустить. */
    static boolean allPresent(GlUniform... uniforms) {
        for (GlUniform uniform : uniforms) {
            if (uniform == null) {
                return false;
            }
        }
        return true;
    }

    /** Один лог на шейдер, без спама в кадрах. */
    private static void warnOnce(String shaderName, String reason) {
        if (WARNED_SHADERS.add(shaderName)) {
            LOGGER.warn("[TabVisuals] шейдер '{}' недоступен ({}), draw-call пропущен", shaderName, reason);
        }
    }
}
