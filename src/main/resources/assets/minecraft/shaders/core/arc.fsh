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
    float endAngle = startAngle + min(end * RAD, PI * 2);

    // 0-размер (length == 0) давал 1/0 = Inf в smoothstep — деградация в NaN.
    float smoothThresh = 6.0 * (1.0 / max(length(size), 0.0001));
    vec2 centerPos = ((gl_FragCoord.xy - location) / max(size.xy, vec2(0.0001))) * 2.0 - 1.0;

    float dist = length(centerPos);
    float bandAlpha = smoothstep(radius, radius + smoothThresh, dist) * smoothstep(radius + thickness, (radius + thickness) - smoothThresh, dist);
    float angle = (atan(centerPos.y, centerPos.x) + PI);
    float angleAlpha = smoothstep(angle, angle - smoothThresh, startAngle - 0.1) * smoothstep(angle, angle + smoothThresh, endAngle + 0.1);

    float angle2 = (angle / PI * 180.);
    angle2 = angle2 - 360. * floor(angle2 / 360.);
    if (angle2 >= 180.) {
        angle2 = (360. - angle2) * 2.;
    } else {
        angle2 = angle2 * 2.;
    }
    fragColor = mix(color1, color2, angle2 / 360.) * bandAlpha * angleAlpha;
}
