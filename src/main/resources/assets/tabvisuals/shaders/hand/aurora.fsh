#version 330 core

in vec2 uv;
out vec4 outColor;

uniform sampler2D ColorTexture;
uniform sampler2D DepthTexture;
uniform vec2 resolution;
uniform float time;
uniform vec3 auroraColor1;
uniform vec3 auroraColor2;
uniform float auroraSpeed;
uniform float auroraIntensity;

// Hash for noise
float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}
float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash(i);
    float b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0));
    float d = hash(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}
float fbm(vec2 p) {
    float v = 0.0;
    v += noise(p) * 0.5;
    v += noise(p * 2.0) * 0.25;
    v += noise(p * 4.0) * 0.125;
    return v;
}

void main() {
    vec4 originalColor = texture(ColorTexture, uv);
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

    // Aurora: horizontal curtains flowing like fabric, vertical bands
    vec2 p = (2.0 * uv - 1.0) * resolution.y / min(resolution.x, resolution.y);
    float curtain = p.y * 2.0 + fbm(vec2(p.x * 3.0 + time * auroraSpeed * 0.3, p.y * 8.0 + time * auroraSpeed));
    curtain = smoothstep(0.2, 0.8, curtain) * smoothstep(1.2, 0.6, curtain);
    float band = sin(p.y * 12.0 + time * auroraSpeed * 0.5) * 0.5 + 0.5;
    band *= sin(p.x * 6.0 + time * auroraSpeed * 0.2) * 0.5 + 0.5;
    float veil = fbm(vec2(p.x * 2.0 + time * 0.1, p.y * 4.0)) * band;
    vec3 aurora = mix(auroraColor1, auroraColor2, curtain);
    aurora *= (curtain * 0.7 + veil * 0.5);
    aurora *= (0.85 + 0.15 * sin(time * 1.5 + p.x * 3.0));
    aurora *= auroraIntensity;

    vec4 effectColor = vec4(aurora, 1.0);
    vec4 finalHandColor = mix(originalColor, effectColor, 0.88);
    outColor = vec4(finalHandColor.rgb, mask);
}
