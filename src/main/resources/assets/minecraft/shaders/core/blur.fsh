#version 150

uniform vec2 size;
uniform vec2 location;
uniform vec4 radius;
uniform float thickness;
uniform float softness;

uniform sampler2D InputSampler;
uniform vec2 InputResolution;
uniform float Quality;
uniform float SampleCount;

uniform vec4 color1;
uniform vec4 color2;
uniform vec4 color3;
uniform vec4 color4;
uniform vec4 outlineColor;

out vec4 fragColor;

float roundedBoxSDF(vec2 center, vec2 size, vec4 radius) {
    radius.xy = (center.x > 0.0) ? radius.xy : radius.zw;
    radius.x  = (center.y > 0.0) ? radius.x : radius.y;

    vec2 q = abs(center) - size + radius.x;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - radius.x;
}

vec4 createGradient(vec2 coords, vec4 color1, vec4 color2, vec4 color3, vec4 color4){
    vec4 color = mix(mix(color1, color2, coords.y), mix(color3, color4, coords.y), coords.x);
    color += mix(0.0019607843, -0.0019607843, fract(sin(dot(coords.xy, vec2(12.9898, 78.233))) * 43758.5453));
    return color;
}

// Vogel-disk sampling: равномерное покрытие без выраженной сетки.
// Обычный HUD использует 24 точки, полноэкранное стекло может запросить
// более плотную выборку без увеличения стоимости остальных blur-элементов.
const int   MAX_BLUR_SAMPLES = 64;
const float BLUR_GOLDEN_ANGLE = 2.399963229728653; // ~137.5 degrees

vec4 blur() {
    // setup() мог не отработать (ресайз/первый кадр): InputResolution = (0,0)
    // давал texel = 1/0 = Inf и неопределённую выборку текстуры.
    vec2 safeResolution = max(InputResolution, vec2(1.0));
    vec4 rectColor = createGradient((gl_FragCoord.xy - location) / max(size, vec2(0.0001)), color1, color2, color3, color4);
    vec2 texel = 1.0 / safeResolution;
    vec2 uv = gl_FragCoord.xy / safeResolution;

    vec4 blur = texture(InputSampler, uv);
    float total = 1.0;
    int sampleCount = clamp(int(SampleCount), 1, MAX_BLUR_SAMPLES);

    for (int i = 0; i < MAX_BLUR_SAMPLES; i++) {
        if (i >= sampleCount) {
            break;
        }
        float fi = float(i) + 0.5;
        float r = sqrt(fi / float(sampleCount)) * Quality;
        float a = fi * BLUR_GOLDEN_ANGLE;
        blur += texture(InputSampler, uv + vec2(cos(a), sin(a)) * r * texel);
        total += 1.0;
    }

    blur /= total;
    return vec4((blur * (1 - rectColor.a)).rgb, rectColor.a) + rectColor;
}

void main() {
    float distance = roundedBoxSDF(gl_FragCoord.xy - location - (size / 2.0), size / 2.0, radius);
    // softness == 0 → smoothstep с равными краями неопределён (NaN-альфа).
    float safeSoftness = max(softness, 0.0001);
    float smoothedAlpha = 1.0 - smoothstep(-1.0, thickness > 0. ? 1. : safeSoftness + 1., distance);

    if(smoothedAlpha < 0.49 && thickness > 0.) {
        float smoothedborderAlpha = (1.0 - smoothstep(-safeSoftness, safeSoftness, distance));
        fragColor = vec4(outlineColor.rgb, smoothedborderAlpha * outlineColor.a);
    } else {
        float borderAlpha = 1.0 - smoothstep(thickness - 2.0, thickness, abs(distance));
        vec4 blur = blur();
        vec4 basicColor = vec4(blur.rgb, blur.a * smoothedAlpha);
        fragColor = mix(vec4(blur.rgb, 0.), mix(basicColor, thickness > 0. ? outlineColor : basicColor, borderAlpha), smoothedAlpha);
    }
}
