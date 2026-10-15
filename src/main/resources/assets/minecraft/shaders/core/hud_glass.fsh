#version 150

uniform vec2 size;
uniform vec2 location;
uniform vec4 radius;
uniform float thickness;
uniform float softness;

uniform sampler2D InputSampler;
uniform vec2 InputResolution;
uniform float Quality;
uniform float BlurSize;
uniform float Brightness;
uniform float ChromaticStrength;
uniform float Time;

uniform vec4 color1;
uniform vec4 color2;
uniform vec4 color3;
uniform vec4 color4;
uniform vec4 outlineColor;

out vec4 fragColor;

#define PI 3.14159265
#define TAU 6.28318530718

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

// liquidGlassBlur - как в HandsShader Glass, даёт характерное "жидкое стекло"
vec4 liquidGlassBlur(vec2 uv, float dir, float qual, float blurSize) {
    vec2 radius = blurSize / max(InputResolution, vec2(1.0));
    vec4 color = texture(InputSampler, uv);
    float total = 1.0;
    for (float d = 0.0; d < PI; d += PI / dir) {
        for (float i = 1.0 / qual; i <= 1.0; i += 1.0 / qual) {
            vec2 offset = vec2(cos(d), sin(d)) * radius * i;
            color += texture(InputSampler, uv + offset);
            total += 1.0;
        }
    }
    return color / total;
}

void main() {
    // setup() мог не отработать: InputResolution = (0,0) → 1/0 → Inf → NaN.
    vec2 safeResolution = max(InputResolution, vec2(1.0));
    vec2 uv = gl_FragCoord.xy / safeResolution;
    float distance = roundedBoxSDF(gl_FragCoord.xy - location - (size / 2.0), size / 2.0, radius);
    // softness == 0 → smoothstep с равными краями неопределён (NaN-альфа).
    float safeSoftness = max(softness, 0.0001);
    float smoothedAlpha = 1.0 - smoothstep(-1.0, thickness > 0. ? 1. : safeSoftness + 1., distance);

    if(smoothedAlpha < 0.49 && thickness > 0.) {
        float smoothedborderAlpha = (1.0 - smoothstep(-safeSoftness, safeSoftness, distance));
        fragColor = vec4(outlineColor.rgb, smoothedborderAlpha * outlineColor.a);
    } else {
        vec4 rectColor = createGradient((gl_FragCoord.xy - location) / max(size, vec2(0.0001)), color1, color2, color3, color4);

        // Основной blur в стиле liquid glass (dir=6, qual=4 — облегчённый: 24 выборки вместо 48)
        vec3 glassColor = liquidGlassBlur(uv, 6.0, 4.0, BlurSize).rgb;

        // Хроматическая аберрация — RGB разделение как на руках
        if (ChromaticStrength > 0.01) {
            float aberration = 0.003 * ChromaticStrength;
            glassColor.r = liquidGlassBlur(uv + vec2(aberration, 0.0), 4.0, 3.0, BlurSize * 0.5).r;
            glassColor.b = liquidGlassBlur(uv - vec2(aberration, 0.0), 4.0, 3.0, BlurSize * 0.5).b;
        }

        // Яркость
        glassColor *= Brightness;

        // Подсветка краёв (как на руках)
        float edgeDist = abs(distance);
        // Ширина зоны подсветки краёв регулируется порогами smoothstep.
        // Было: -2.0 .. 2.0 (узко). Станет шире: -3.5 .. 3.5.
        float edgeStrength = 1.0 - smoothstep(-4.5, 4.5, edgeDist);
        glassColor += vec3(1.0) * edgeStrength * 0.25;

        // Лёгкое отражение (как на руках)
        float reflection = sin(Time * 2.0 + uv.x * 20.0) * 0.5 + 0.5;
        reflection *= sin(Time * 1.5 + uv.y * 15.0) * 0.5 + 0.5;
        // Множитель отражения сильнее => заметнее бликовые участки.
        glassColor += vec3(reflection * 0.2);

        // Смешивание с overlay (rectColor)
        vec4 blur = vec4((vec4(glassColor, 1.0) * (1.0 - rectColor.a)).rgb, rectColor.a) + rectColor;

        float borderAlpha = 1.0 - smoothstep(thickness - 2.0, thickness, abs(distance));
        vec4 basicColor = vec4(blur.rgb, blur.a * smoothedAlpha);
        fragColor = mix(vec4(blur.rgb, 0.), mix(basicColor, thickness > 0. ? outlineColor : basicColor, borderAlpha), smoothedAlpha);
    }
}
