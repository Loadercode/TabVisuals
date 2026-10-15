#version 150

#moj_import <mre:common.glsl>

in vec2 FragCoord; // normalized fragment coord relative to the primitive
in vec4 FragColor;

uniform vec2 Size; // rectangle size
uniform vec4 Radius; // radius for each vertex
uniform float Thickness; // border thickness
uniform vec2 Smoothness; // internal and external edge smoothness

out vec4 OutColor;

void main() {
    vec2 center = Size * 0.5;
    float dist = rdist(center - (FragCoord.xy * Size), center - 1.0, Radius);
    // Smoothness == 0 → равные края smoothstep неопределены (NaN/мусор)
    float inner = max(Smoothness.x, 1e-4);
    float outer = max(Smoothness.y, 1e-4);
    float alpha = smoothstep(1.0 - Thickness - inner - outer,
        1.0 - Thickness - outer, dist); // internal edge
    alpha *= 1.0 - smoothstep(1.0 - outer, 1.0, dist); // external edge
    vec4 color = vec4(FragColor.rgb, FragColor.a * alpha);

    if (color.a == 0.0) { // alpha test
        discard;
    }

    OutColor = color;
}