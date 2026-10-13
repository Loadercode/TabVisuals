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

    // base diagonal: milky white (top-left) -> soft blue-grey (bottom-right)
    vec3 tl = vec3(0.972, 0.980, 0.992);
    vec3 br = vec3(0.878, 0.906, 0.957);
    vec3 col = mix(tl, br, clamp(uv.x * 0.52 + uv.y * 0.48, 0.0, 1.0));

    // large cool core with a slow drift — tinted deepen, gaussian falloff
    vec2 c1 = vec2(0.38 * aspect + 0.022 * sin(Time * 0.10), 0.42 + 0.018 * cos(Time * 0.083));
    float d1 = distance(p, c1);
    col = mix(col, vec3(0.816, 0.855, 0.941), exp(-d1 * d1 * 3.4) * 0.55);

    // lavender falloff lower-right, counter-phase drift
    vec2 c2 = vec2(0.76 * aspect + 0.020 * cos(Time * 0.071), 0.62 + 0.017 * sin(Time * 0.127));
    float d2 = distance(p, c2);
    col = mix(col, vec3(0.855, 0.827, 0.937), exp(-d2 * d2 * 5.2) * 0.45);

    // faint breathing lighten inside the core (subtle detail)
    float shimmer = exp(-d1 * d1 * 14.0) * (0.5 + 0.5 * sin(Time * 0.55));
    col = mix(col, vec3(1.0), shimmer * 0.07);

    // soft top-left sheen
    vec2 sPos = uv - vec2(0.14, 0.06);
    col = mix(col, vec3(1.0), exp(-dot(sPos, sPos) * 5.5) * 0.12);

    // vignette — gently deeper edges so the white never reads flat
    float vig = 1.0 - smoothstep(0.30, 1.15, distance(uv, vec2(0.5, 0.48)));
    col *= mix(0.905, 1.0, vig);

    // blue-noise-ish dither kills 8-bit banding on the smooth field
    col += (hash(gl_FragCoord.xy + fract(Time)) - 0.5) / 255.0;

    fragColor = vec4(col, Alpha);
}
