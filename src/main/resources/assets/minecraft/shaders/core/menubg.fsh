#version 150

uniform vec2 ScreenSize;
uniform float Time;
uniform float Alpha;

in vec2 Screen;
out vec4 fragColor;

// Night backdrop: a near-black indigo base with three slow aurora pools
// (violet, indigo, plum) drifting behind a soft noise veil. The edges fall
// to black so white chrome stays readable everywhere; a dither removes
// 8-bit banding on the long ramps.
const vec3 BASE   = vec3(0.022, 0.020, 0.050);
const vec3 VIOLET = vec3(0.400, 0.230, 0.860);
const vec3 INDIGO = vec3(0.180, 0.200, 0.720);
const vec3 PLUM   = vec3(0.520, 0.130, 0.520);

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

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

float fbm(vec2 p) {
    float v = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 3; i++) {
        v += amp * vnoise(p);
        p = p * 2.03 + vec2(17.3, 9.1);
        amp *= 0.5;
    }
    return v;
}

float pool(vec2 q, vec2 c, float tight) {
    vec2 d = q - c;
    return exp(-dot(d, d) * tight);
}

void main() {
    vec2 uv = Screen / max(ScreenSize, vec2(1.0));
    float aspect = ScreenSize.x / max(ScreenSize.y, 1.0);
    vec2 q = vec2((uv.x - 0.5) * aspect, uv.y - 0.5);

    float t = Time;
    // the veil: low-frequency noise sliding very slowly — breaks the pools
    // into soft cloud shapes instead of perfect circles
    float veil = fbm(q * 1.35 + vec2(t * 0.021, -t * 0.016));
    float veil2 = fbm(q * 2.1 - vec2(t * 0.013, t * 0.019) + 7.7);

    vec2 c1 = vec2(-0.08, -0.14) + vec2(sin(t * 0.071), cos(t * 0.053)) * vec2(0.22, 0.10);
    vec2 c2 = vec2(0.42, 0.10) + vec2(cos(t * 0.047), sin(t * 0.061)) * vec2(0.18, 0.14);
    vec2 c3 = vec2(-0.46, 0.30) + vec2(sin(t * 0.039 + 1.7), cos(t * 0.057 + 0.6)) * vec2(0.16, 0.10);

    vec3 col = BASE;
    col += VIOLET * pool(q, c1, 2.6) * (0.34 + 0.30 * veil);
    col += INDIGO * pool(q, c2, 2.2) * (0.26 + 0.30 * veil2);
    col += PLUM   * pool(q, c3, 3.0) * (0.20 + 0.24 * veil);

    // faint floor light, like a lit window spilling onto snow
    float floorGlow = exp(-pow((uv.y - 0.84) * 4.2, 2.0)) * exp(-q.x * q.x * 1.1);
    col += VIOLET * floorGlow * (0.10 + 0.05 * sin(t * 0.23));

    // vignette to near-black at the border
    vec2 n = vec2(abs(q.x) / max(aspect * 0.5, 0.001), abs(q.y) / 0.5);
    float edge = length(n) * 0.70710678;
    col *= mix(1.0, 0.30, smoothstep(0.35, 1.05, edge));

    col += (hash(gl_FragCoord.xy + fract(t)) - 0.5) * (1.8 / 255.0);
    fragColor = vec4(max(col, vec3(0.0)), Alpha);
}
