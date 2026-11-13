#version 150

uniform vec4 color1;
uniform vec4 color2;
uniform float radius;
uniform float thickness;
uniform float start;
uniform float end;
uniform vec2 size;
uniform vec2 location;

out vec4 fragColor;

#define PI 3.141592653589793
#define RAD 0.0174533

void main() {
    float startAngle = start * RAD;
    float endAngle = startAngle + min(end * RAD, PI * 2.0);

    // 0-размер (length == 0) давал 1/0 = Inf — деградация в NaN.
    vec2 safeSize = max(size, vec2(0.0001));
    // Legacy inset of every edge (the old 2px-wide smoothstep started exactly
    // on the nominal edge); kept so the ring keeps its weight.
    float inset = 3.0 / max(length(size), 0.0001);
    vec2 centerPos = ((gl_FragCoord.xy - location) / safeSize) * 2.0 - 1.0;

    float dist = length(centerPos);
    // Physical pixels per normalized unit: every edge below is antialiased
    // over exactly one pixel, whatever the arc size / GUI scale.
    float pixels = 0.5 * min(safeSize.x, safeSize.y);
    float bandAlpha = clamp((dist - radius - inset) * pixels + 0.5, 0.0, 1.0)
                    * clamp((radius + thickness - inset - dist) * pixels + 0.5, 0.0, 1.0);

    float angle = (atan(centerPos.y, centerPos.x) + PI);
    // pixels per radian at this distance from the centre
    float arcPixels = max(dist, 0.0001) * pixels;
    float angleAlpha = clamp((angle - (startAngle - 0.1 + inset)) * arcPixels + 0.5, 0.0, 1.0)
                     * clamp(((endAngle + 0.1 - inset) - angle) * arcPixels + 0.5, 0.0, 1.0);
    // A full ring has no ends: skip the cut so small rings get no seam at the wrap angle.
    if (start == 0.0 && end * RAD >= PI * 2.0) {
        angleAlpha = 1.0;
    }

    float angle2 = (angle / PI * 180.);
    angle2 = angle2 - 360. * floor(angle2 / 360.);
    if (angle2 >= 180.) {
        angle2 = (360. - angle2) * 2.;
    } else {
        angle2 = angle2 * 2.;
    }
    // Coverage goes into alpha only: scaling rgb as well double-darkened the
    // edges under SRC_ALPHA blending.
    vec4 color = mix(color1, color2, angle2 / 360.);
    fragColor = vec4(color.rgb, color.a * bandAlpha * angleAlpha);
}
