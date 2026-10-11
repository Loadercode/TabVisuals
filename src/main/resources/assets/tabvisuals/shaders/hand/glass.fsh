#version 330 core

in vec2 uv;
out vec4 outColor;

uniform sampler2D ColorTexture;
uniform sampler2D DepthTexture;
uniform float time;
uniform vec2 resolution;

uniform float blurSize;
uniform float quality;
uniform float direction;
uniform float refraction;
uniform float brightness;
uniform bool enableChromatic;
uniform bool enableDistortion;
uniform bool hideHand;
uniform float backgroundBlur;

#define PI 3.14159265

vec4 liquidGlassBlur(sampler2D tex, vec2 uv, float dir, float qual, float size) {
    vec2 radius = (size / resolution.y) / resolution;
    vec4 color = texture(tex, uv);
    float total = 1.0;
    for (float d = 0.0; d < PI; d += PI / dir) {
        for (float i = 1.0 / qual; i <= 1.0; i += 1.0 / qual) {
            vec2 offset = vec2(cos(d), sin(d)) * radius * i;
            color += texture(tex, uv + offset);
            total += 1.0;
        }
    }
    return color / total;
}

vec4 matteBlur(sampler2D tex, vec2 uv, float blurRadius) {
    const float TAU = 6.28318530718;
    vec2 radius = blurRadius / resolution.xy;
    vec4 blur = texture(tex, uv);
    float step = TAU / 16.0;
    for (float d = 0.0; d < TAU; d += step) {
        for (float i = 0.2; i <= 1.0; i += 0.2) {
            blur += texture(tex, uv + vec2(cos(d), sin(d)) * radius * i);
        }
    }
    blur /= 81.0;
    return blur;
}

vec2 glassDistortion(vec2 uv, float time, float strength) {
    float wave1 = sin(uv.y * 10.0 + time * 2.0) * 0.01 * strength;
    float wave2 = cos(uv.x * 8.0 - time * 1.5) * 0.01 * strength;
    vec2 center = vec2(0.5, 0.5);
    vec2 toCenter = uv - center;
    float dist = length(toCenter);
    vec2 radial = toCenter * sin(dist * 15.0 - time * 3.0) * 0.005 * strength;
    return vec2(wave1 + radial.x, wave2 + radial.y);
}

void main() {
    vec4 originalColor = texture(ColorTexture, uv);
    vec2 texelSize = 1.0 / textureSize(DepthTexture, 0);
    float minDepth = 1.0;
    float centerDepth = texture(DepthTexture, uv).r;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec2 offset = vec2(x, y) * texelSize;
            float neighborDepth = texture(DepthTexture, uv + offset).r;
            minDepth = min(minDepth, neighborDepth);
        }
    }
    float mask = smoothstep(0.99, 0.98, minDepth);
    if (mask < 0.01) { discard; }
    vec2 distortedUV = uv;
    if (enableDistortion) { distortedUV += glassDistortion(uv, time, refraction); }
    vec4 handColor = texture(ColorTexture, distortedUV);
    vec3 glassColor;
    if (centerDepth < 0.99) {
        if (hideHand) {
            vec2 bgUV = uv;
            if (backgroundBlur > 0.1) {
                glassColor = matteBlur(ColorTexture, bgUV, backgroundBlur).rgb;
            } else {
                glassColor = texture(ColorTexture, bgUV).rgb;
            }
            glassColor *= brightness;
        } else {
            glassColor = liquidGlassBlur(ColorTexture, distortedUV, direction, quality, blurSize).rgb;
            if (enableChromatic) {
                float aberration = 0.003 * refraction;
                float r = liquidGlassBlur(ColorTexture, distortedUV + vec2(aberration, 0.0), direction, quality, blurSize * 0.5).r;
                float b = liquidGlassBlur(ColorTexture, distortedUV - vec2(aberration, 0.0), direction, quality, blurSize * 0.5).b;
                glassColor.r = r;
                glassColor.b = b;
            }
            glassColor *= brightness;
        }
    } else {
        glassColor = handColor.rgb;
    }
    float edgeStrength = 0.0;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            if (x == 0 && y == 0) continue;
            vec2 offset = vec2(x, y) * texelSize;
            float neighborDepth = texture(DepthTexture, uv + offset).r;
            if (neighborDepth > 0.99 && centerDepth < 0.99) {
                float dist = length(vec2(x, y));
                edgeStrength += (1.0 / (dist + 0.1));
            }
        }
    }
    edgeStrength = clamp(edgeStrength * 0.1, 0.0, 1.0);
    vec3 highlight = vec3(1.0) * edgeStrength * 0.3;
    glassColor += highlight;
    float reflection = sin(time * 2.0 + uv.x * 20.0) * 0.5 + 0.5;
    reflection *= sin(time * 1.5 + uv.y * 15.0) * 0.5 + 0.5;
    glassColor += vec3(reflection * 0.1);
    vec3 finalColor = mix(handColor.rgb, glassColor, 0.7);
    outColor = vec4(finalColor, mask);
}
