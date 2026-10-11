#version 330 core

in vec2 uv;
out vec4 outColor;

uniform sampler2D Shader1Texture;
uniform sampler2D Shader2Texture;
uniform sampler2D DepthTexture;
uniform int blendMode;
uniform float blendIntensity;
uniform float shader1Strength;
uniform float shader2Strength;

vec3 blendMix(vec3 base, vec3 blend, float intensity) { return mix(base, blend, intensity); }
vec3 blendAdd(vec3 base, vec3 blend) { return min(base + blend, vec3(1.0)); }
vec3 blendMultiply(vec3 base, vec3 blend) { return base * blend; }
vec3 blendScreen(vec3 base, vec3 blend) { return vec3(1.0) - (vec3(1.0) - base) * (vec3(1.0) - blend); }
vec3 blendOverlay(vec3 base, vec3 blend) {
    vec3 result;
    result.r = base.r < 0.5 ? (2.0 * base.r * blend.r) : (1.0 - 2.0 * (1.0 - base.r) * (1.0 - blend.r));
    result.g = base.g < 0.5 ? (2.0 * base.g * blend.g) : (1.0 - 2.0 * (1.0 - base.g) * (1.0 - blend.g));
    result.b = base.b < 0.5 ? (2.0 * base.b * blend.b) : (1.0 - 2.0 * (1.0 - base.b) * (1.0 - blend.b));
    return result;
}
vec3 blendDifference(vec3 base, vec3 blend) { return abs(base - blend); }

void main() {
    vec4 shader1Color = texture(Shader1Texture, uv);
    vec4 shader2Color = texture(Shader2Texture, uv);
    vec2 texelSize = 1.0 / textureSize(DepthTexture, 0);
    float minDepth = 1.0;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec2 offset = vec2(x, y) * texelSize;
            float neighborDepth = texture(DepthTexture, uv + offset).r;
            minDepth = min(minDepth, neighborDepth);
        }
    }
    float mask = smoothstep(0.99, 0.98, minDepth);
    if (mask < 0.01) { discard; }
    vec3 color1 = shader1Color.rgb * shader1Strength;
    vec3 color2 = shader2Color.rgb * shader2Strength;
    vec3 blendedColor;
    if (blendMode == 0) { blendedColor = blendMix(color1, color2, blendIntensity); }
    else if (blendMode == 1) { blendedColor = mix(color1, blendAdd(color1, color2), blendIntensity); }
    else if (blendMode == 2) { blendedColor = mix(color1, blendMultiply(color1, color2), blendIntensity); }
    else if (blendMode == 3) { blendedColor = mix(color1, blendScreen(color1, color2), blendIntensity); }
    else if (blendMode == 4) { blendedColor = mix(color1, blendOverlay(color1, color2), blendIntensity); }
    else if (blendMode == 5) { blendedColor = mix(color1, blendDifference(color1, color2), blendIntensity); }
    else { blendedColor = color1; }
    outColor = vec4(blendedColor, mask);
}
