#version 150

// One-quad radial glow: UV0 runs -1..1 across the quad, the falloff is a
// smooth bell that reaches exactly zero at the quad edge (no visible box),
// dithered so the wide soft ramp never bands on 8-bit output.

in vec2 Local;
in vec4 Tint;

out vec4 fragColor;

void main() {
    float d = length(Local);
    float f = clamp(1.0 - d, 0.0, 1.0);
    f = f * f * (3.0 - 2.0 * f);
    f *= f;
    float n = fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453);
    float a = Tint.a * f + (n - 0.5) * (1.5 / 255.0) * step(0.004, f);
    if (a <= 0.0) {
        discard;
    }
    fragColor = vec4(Tint.rgb, min(a, 1.0));
}
