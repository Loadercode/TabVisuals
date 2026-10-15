#version 150

uniform vec2 ScreenSize;
uniform float Time;
uniform float Alpha;

in vec2 Screen;
out vec4 fragColor;

// violet-white palette from the client accent family (0xFF8B5CF6):
// deep violet core easing through lavender into soft white at the corners
const vec3 DEEP  = vec3(0.106, 0.063, 0.235);  // #1B103C centre violet
const vec3 MID   = vec3(0.478, 0.322, 0.878);  // #7A52E0 lifted violet
const vec3 LAV   = vec3(0.808, 0.741, 0.984);  // #CEBDFB lavender
const vec3 WHITE = vec3(0.973, 0.961, 0.996);  // #F9F5FE soft white

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

void main() {
    vec2 uv = Screen / max(ScreenSize, vec2(1.0));
    float aspect = ScreenSize.x / max(ScreenSize.y, 1.0);

    // centred, aspect-corrected coords; the whole field creeps very slowly
    vec2 q = vec2((uv.x - 0.5) * aspect, uv.y - 0.5);
    q += vec2(sin(Time * 0.043), cos(Time * 0.037)) * 0.025;

    // 0 at the centre, exactly 1 at the corners (both axes normalised to the
    // half-screen, so corner distance 1.414 collapses to 1 regardless of aspect)
    vec2 n = vec2(abs(q.x) / max(aspect * 0.5, 0.001), abs(q.y) / 0.5);
    float corner = length(n) * 0.70710678;

    // base: deep violet in the calm centre easing to lifted violet outward,
    // then to lavender as it approaches the edges
    vec3 col = mix(DEEP, MID, smoothstep(0.08, 0.72, corner));
    col = mix(col, LAV, smoothstep(0.55, 1.0, corner));

    // soft white light pool breathing into each corner (nearest-corner
    // distance via abs() — symmetric and one distance() instead of four)
    float d = distance(n, vec2(1.0) + vec2(sin(Time * 0.061), cos(Time * 0.052)) * 0.06);
    col = mix(col, WHITE, exp(-d * d * 1.6) * 0.60);

    // faint lavender sheen drifting diagonally — breaks the symmetry just
    // enough to feel alive, amplitude kept low so it never looks noisy
    float sheen = 0.5 + 0.5 * sin(q.x * 0.9 + q.y * 1.3 + Time * 0.07);
    col = mix(col, LAV, sheen * 0.08);

    // keep the centre a touch darker so logo/buttons stay readable
    col *= mix(0.92, 1.0, corner);

    // dither kills 8-bit banding on the smooth field
    col += (hash(gl_FragCoord.xy + fract(Time)) - 0.5) * (1.5 / 255.0);

    fragColor = vec4(col, Alpha);
}
