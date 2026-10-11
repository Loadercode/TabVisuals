#version 330 core

in vec2 uv;
out vec4 outColor;

uniform sampler2D ColorTexture;
uniform sampler2D DepthTexture;
uniform vec2 resolution;
uniform float time;
uniform vec3 portalColor1;
uniform vec3 portalColor2;
uniform float portalSpeed;
uniform float portalIntensity;

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

    vec2 p = (2.0 * uv - 1.0) * resolution.y / min(resolution.x, resolution.y);
    float r = length(p);
    float a = atan(p.y, p.x);
    float twist = a + r * 4.0 - time * portalSpeed * 3.0;
    float spiral = sin(twist * 2.0) * 0.5 + 0.5;
    float depth = 1.0 - smoothstep(0.0, 1.2, r);
    float layer1 = sin(twist) * 0.5 + 0.5;
    float layer2 = sin(twist * 1.5 + 1.0) * 0.5 + 0.5;
    vec3 portal = mix(portalColor1, portalColor2, layer1 * depth);
    portal += portalColor2 * layer2 * 0.3 * depth;
    portal *= (0.7 + 0.3 * spiral) * depth * portalIntensity;

    vec4 effectColor = vec4(portal, 1.0);
    vec4 finalHandColor = mix(originalColor, effectColor, 0.88);
    outColor = vec4(finalHandColor.rgb, mask);
}
