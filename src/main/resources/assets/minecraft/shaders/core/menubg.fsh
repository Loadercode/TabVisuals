#version 150

uniform vec2 ScreenSize;
uniform float Time;
uniform float Alpha;

in vec2 Screen;
out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

// smooth value noise for the iridescent drift
float vnoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash(i);
    float b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0));
    float d = hash(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

void main() {
    vec2 uv = Screen / max(ScreenSize, vec2(1.0));
    float aspect = ScreenSize.x / max(ScreenSize.y, 1.0);
    vec2 p = vec2(uv.x * aspect, uv.y);

    // ---- dark navy base (matches the logo's dark body: #0A0A2E -> #060418) ----
    // kept dark so the centred logo's own dark background blends into it
    vec3 baseTop = vec3(0.043, 0.047, 0.145);   // #0B0C25
    vec3 baseBot = vec3(0.024, 0.016, 0.075);   // #060413
    float diag = clamp(uv.y * 0.65 + uv.x * 0.35, 0.0, 1.0);
    vec3 col = mix(baseTop, baseBot, diag);

    // ---- blue glow, top-left corner (logo #343FB3 -> #4055D0); tight so it stays in the corner ----
    vec2 c1 = vec2(-0.08 * aspect + 0.020 * sin(Time * 0.09), -0.02 + 0.016 * cos(Time * 0.075));
    float d1 = distance(p, c1);
    col = mix(col, vec3(0.204, 0.271, 0.690), exp(-d1 * d1 * 2.4) * 0.85);

    // ---- violet glow, bottom-right corner (logo #6A30D4) ----
    vec2 c3 = vec2(1.06 * aspect + 0.018 * sin(Time * 0.088), 1.04 + 0.015 * cos(Time * 0.105));
    float d3 = distance(p, c3);
    col = mix(col, vec3(0.416, 0.188, 0.831), exp(-d3 * d3 * 2.3) * 0.82);

    // ---- secondary deep-violet, right edge mid (logo #260D61) ----
    vec2 c2 = vec2(1.08 * aspect + 0.020 * cos(Time * 0.067), 0.46 + 0.016 * sin(Time * 0.121));
    float d2 = distance(p, c2);
    col = mix(col, vec3(0.286, 0.129, 0.541), exp(-d2 * d2 * 3.0) * 0.55);

    // ---- iridescent sweep: slow flowing band that lifts blue<->violet across the field ----
    float flow = vnoise(p * 1.5 + vec2(Time * 0.032, -Time * 0.02));
    float band = sin((p.x * 0.85 + p.y * 0.55) * 2.0 - Time * 0.2 + flow * 2.3);
    band = smoothstep(-0.1, 1.0, band);
    vec3 irid = mix(vec3(0.14, 0.19, 0.50), vec3(0.38, 0.20, 0.72), band);
    col += irid * 0.055 * (0.5 + 0.5 * flow);

    // ---- breathing shimmer inside the blue core (subtle life) ----
    float shimmer = exp(-d1 * d1 * 5.0) * (0.5 + 0.5 * sin(Time * 0.5));
    col += vec3(0.10, 0.13, 0.25) * shimmer * 0.35;

    // ---- vignette: deepen the true edges/corners beyond the glows ----
    float vig = 1.0 - smoothstep(0.45, 1.35, distance(uv, vec2(0.5, 0.47)));
    col *= mix(0.68, 1.0, vig);

    // ---- dither kills 8-bit banding on the smooth field ----
    col += (hash(gl_FragCoord.xy + fract(Time)) - 0.5) / 255.0;

    fragColor = vec4(col, Alpha);
}
