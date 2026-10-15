#version 150

in vec2 TexCoord;
in vec4 FragColor;

uniform sampler2D Sampler0;
uniform float Range; // distance field range of the msdf font texture
uniform float Thickness; // text thickness
uniform float Smoothness; // edge smoothness
uniform bool Outline; // if false, outline computation will be ignored
uniform float OutlineThickness;
uniform vec4 OutlineColor;

out vec4 OutColor;

float median(vec3 color) {
    return max(min(color.r, color.g), min(max(color.r, color.g), color.b));
}

void main() {
    float dist = median(texture(Sampler0, TexCoord).rgb) - 0.5 + Thickness;
    vec2 h = vec2(dFdx(TexCoord.x), dFdy(TexCoord.y)) * textureSize(Sampler0, 0);
    // вырожденные производные (нулевой атлас/экран) дали inversesqrt(0) = Inf
    float pixels = Range * inversesqrt(max(h.x * h.x + h.y * h.y, 1e-6));
    // Smoothness == 0 → равные края smoothstep неопределены (NaN-альфа текста)
    float s = max(Smoothness, 1e-4);
    float alpha = smoothstep(-s, s, dist * pixels);
    vec4 color = vec4(FragColor.rgb, FragColor.a * alpha);
    
    if (Outline) {
        color = mix(OutlineColor, FragColor, alpha);
        color.a *= smoothstep(-s, s, (dist + OutlineThickness) * pixels);
    }
    
    OutColor = color;
}