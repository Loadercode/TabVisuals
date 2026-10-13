#version 150

uniform vec2 ScreenSize;
uniform float Time;
uniform float Alpha;

in vec2 Screen;
out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

void main() {
    vec2 uv = Screen / max(ScreenSize, vec2(1.0));
    float aspect = ScreenSize.x / max(ScreenSize.y, 1.0);
    vec2 p = vec2(uv.x * aspect, uv.y);

    // base vertical: deep navy (top) -> near-black indigo (bottom), like the logo bg
    vec3 top = vec3(0.043, 0.051, 0.129);
    vec3 bot = vec3(0.027, 0.031, 0.094);
    vec3 col = mix(top, bot, clamp(uv.y * 0.85 + uv.x * 0.15, 0.0, 1.0));

    // indigo core, top-left corner — slow drift, gaussian falloff (logo blue glow)
    vec2 c1 = vec2(0.04 * aspect + 0.022 * sin(Time * 0.10), 0.05 + 0.018 * cos(Time * 0.083));
    float d1 = distance(p, c1);
    col = mix(col, vec3(0.196, 0.259, 0.639), exp(-d1 * d1 * 1.15) * 0.75);

    // violet glow, right edge mid-height — counter-phase drift (logo violet)
    vec2 c2 = vec2(1.02 * aspect + 0.020 * cos(Time * 0.071), 0.42 + 0.017 * sin(Time * 0.127));
    float d2 = distance(p, c2);
    col = mix(col, vec3(0.416, 0.216, 0.729), exp(-d2 * d2 * 1.35) * 0.70);

    // second violet, bottom-right corner, deeper purple
    vec2 c3 = vec2(0.92 * aspect + 0.018 * sin(Time * 0.09), 1.04 + 0.015 * cos(Time * 0.11));
    float d3 = distance(p, c3);
    col = mix(col, vec3(0.337, 0.165, 0.604), exp(-d3 * d3 * 1.5) * 0.62);

    // faint indigo wash bottom-left so the field never reads flat
    vec2 c4 = vec2(-0.04 * aspect, 0.78);
    float d4 = distance(p, c4);
    col = mix(col, vec3(0.145, 0.133, 0.427), exp(-d4 * d4 * 2.4) * 0.42);

    // breathing shimmer inside the indigo core (subtle life)
    float shimmer = exp(-d1 * d1 * 5.0) * (0.5 + 0.5 * sin(Time * 0.55));
    col += vec3(0.10, 0.12, 0.22) * shimmer * 0.35;

    // vignette — gently deeper edges/corners
    float vig = 1.0 - smoothstep(0.35, 1.25, distance(uv, vec2(0.5, 0.48)));
    col *= mix(0.78, 1.0, vig);

    // blue-noise-ish dither kills 8-bit banding on the smooth field
    col += (hash(gl_FragCoord.xy + fract(Time)) - 0.5) / 255.0;

    fragColor = vec4(col, Alpha);
}
