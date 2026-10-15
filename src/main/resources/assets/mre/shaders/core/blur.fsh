#version 150

#moj_import <mre:common.glsl>

in vec2 FragCoord; // normalized fragment coord relative to the primitive
in vec2 TexCoord;
in vec4 FragColor;

uniform sampler2D Sampler0;
uniform vec2 Size; // rectangle size
uniform vec4 Radius; // radius for each vertex
uniform float Smoothness; // edge smoothness
uniform float BlurRadius;

out vec4 OutColor;

const int   BLUR_SAMPLES = 24;
const float BLUR_GOLDEN_ANGLE = 2.399963229728653; // ~137.5 degrees

void main() {
    // textureSize необрезанного сэмплера может быть (0,0) → 1/0 = Inf;
    // векторную форму пишем через vec2 — float / ivec2 в GLSL 150 не преобразуется
    vec2 texSize = max(vec2(textureSize(Sampler0, 0)), vec2(1.0));
    vec2 multiplier = vec2(BlurRadius) / texSize;

    // Vogel-disk sampling (25 taps) instead of the 16x5 ring loop (81 taps).
    vec3 average = texture(Sampler0, TexCoord).rgb;
    float total = 1.0;
    for (int i = 0; i < BLUR_SAMPLES; i++) {
        float fi = float(i) + 0.5;
        float r = sqrt(fi / float(BLUR_SAMPLES));
        float a = fi * BLUR_GOLDEN_ANGLE;
        average += texture(Sampler0, TexCoord + vec2(cos(a), sin(a)) * multiplier * r).rgb;
        total += 1.0;
    }
    average /= total;

    vec4 color = vec4(average, 1.0) * FragColor;
    color.a *= ralpha(Size, FragCoord, Radius, Smoothness);

    if (color.a == 0.0) { // alpha test
        discard;
    }

    OutColor = color;
}