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

    // base diagonal: dim teal (top-left) -> deep blue-black (bottom-right)
    vec3 tl = vec3(0.047, 0.196, 0.259);
    vec3 br = vec3(0.014, 0.055, 0.090);
    vec3 col = mix(tl, br, clamp(uv.x * 0.52 + uv.y * 0.48, 0.0, 1.0));

    // large teal core with a slow drift — gaussian falloff, no rings
    vec2 c1 = vec2(0.38 * aspect + 0.022 * sin(Time * 0.10), 0.42 + 0.018 * cos(Time * 0.083));
    float d1 = distance(p, c1);
    col += vec3(0.030, 0.115, 0.135) * exp(-d1 * d1 * 3.4);

    // cooler blue falloff lower-right, counter-phase drift
    vec2 c2 = vec2(0.76 * aspect + 0.020 * cos(Time * 0.071), 0.62 + 0.017 * sin(Time * 0.127));
    float d2 = distance(p, c2);
    col += vec3(0.045, 0.080, 0.170) * exp(-d2 * d2 * 5.2);

    // faint breathing cyan sparkle inside the core (subtle detail)
    float shimmer = exp(-d1 * d1 * 14.0) * (0.5 + 0.5 * sin(Time * 0.55));
    col += vec3(0.006, 0.020, 0.024) * shimmer;

    // soft top-left sheen
    vec2 sPos = uv - vec2(0.14, 0.06);
    col += vec3(0.010, 0.026, 0.032) * exp(-dot(sPos, sPos) * 5.5);

    // vignette
    float vig = smoothstep(1.15, 0.30, distance(uv, vec2(0.5, 0.48)));
    col *= mix(0.78, 1.0, vig);

    // blue-noise-ish dither kills 8-bit banding on the smooth field
    col += (hash(gl_FragCoord.xy + fract(Time)) - 0.5) / 255.0;

    fragColor = vec4(col, Alpha);
}
