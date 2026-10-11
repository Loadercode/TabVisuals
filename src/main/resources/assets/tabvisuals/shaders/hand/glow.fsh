#version 330 core

in vec2 uv;
out vec4 outColor;

uniform sampler2D ColorTexture;
uniform sampler2D DepthTexture;
uniform float time;
uniform vec2 resolution;

uniform bool fillGlow;
uniform float glowRadius;
uniform float glowPower;
uniform float glowDispersion;
uniform vec3 glowColor;

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
    float edgeStrength = 0.0;
    float outwardGlow = 0.0;
    int disperseRadius = int(glowDispersion);
    for (int y = -disperseRadius; y <= disperseRadius; y++) {
        for (int x = -disperseRadius; x <= disperseRadius; x++) {
            if (x == 0 && y == 0) continue;
            vec2 offset = vec2(x, y) * texelSize;
            float neighborDepth = texture(DepthTexture, uv + offset).r;
            float dist = length(vec2(x, y));
            if (neighborDepth > 0.99 && centerDepth < 0.99) {
                edgeStrength += (1.0 / (dist + 0.1));
            }
            if (centerDepth > 0.99 && neighborDepth < 0.99) {
                float falloff = 1.0 - (dist / float(disperseRadius));
                falloff = pow(falloff, 2.5);
                outwardGlow += falloff * 0.2;
            }
        }
    }
    edgeStrength = clamp(edgeStrength * 0.15, 0.0, 1.0);
    outwardGlow = clamp(outwardGlow, 0.0, 1.0);
    float pulse = 0.9 + sin(time * 2.0) * 0.1;
    vec3 finalColor;
    if (centerDepth > 0.99 && outwardGlow > 0.01) {
        vec3 dispersedGlow = glowColor * outwardGlow * glowPower * pulse * glowRadius * 10.0;
        outColor = vec4(dispersedGlow, outwardGlow * 0.8);
        return;
    }
    if (fillGlow) {
        vec3 handGlow = glowColor * (glowRadius * 10.0);
        vec3 edgeGlow = glowColor * edgeStrength * glowPower * pulse * 2.5;
        finalColor = mix(originalColor.rgb, handGlow + edgeGlow, 0.75);
    } else {
        vec3 edgeGlow = glowColor * edgeStrength * glowPower * pulse * glowRadius * 25.0;
        finalColor = originalColor.rgb + edgeGlow;
    }
    outColor = vec4(finalColor, mask);
}
