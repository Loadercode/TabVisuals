#version 150

uniform vec4 color1;
uniform vec4 color2;
uniform vec4 color3;
uniform vec4 color4;

uniform vec4 outlineColor;
uniform vec2 size;
uniform vec2 location;
uniform vec4 radius;
uniform float thickness;
uniform float softness;

out vec4 fragColor;

// All distances are in physical pixels (gl_FragCoord space).
float roundedBoxSDF(vec2 center, vec2 halfSize, vec4 radius) {
    radius.xy = (center.x > 0.0) ? radius.xy : radius.zw;
    radius.x  = (center.y > 0.0) ? radius.x : radius.y;
    // A radius above half the short side (or below zero) folds the SDF inside out.
    float r = clamp(radius.x, 0.0, min(halfSize.x, halfSize.y));

    vec2 q = abs(center) - halfSize + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

vec4 createGradient(vec2 coords, vec4 color1, vec4 color2, vec4 color3, vec4 color4){
    // The quad is padded for the AA fringe: without the clamp the gradient extrapolates there.
    coords = clamp(coords, 0.0, 1.0);
    vec4 color = mix(mix(color1, color2, coords.y), mix(color3, color4, coords.y), coords.x);
    color += mix(0.0019607843, -0.0019607843, fract(sin(dot(coords.xy, vec2(12.9898, 78.233))) * 43758.5453));
    return clamp(color, 0.0, 1.0);
}

void main() {
    vec2 halfSize = size * 0.5;
    float dist = roundedBoxSDF(gl_FragCoord.xy - location - halfSize, halfSize, radius);

    // Box-filter coverage: exactly one pixel wide, so pixel-aligned edges stay
    // crisp and curved / moving ones are antialiased.
    float crisp = clamp(0.5 - dist, 0.0, 1.0);
    bool outlined = thickness > 0.0;

    float glow = 0.0;
    float coverage;
    if (outlined) {
        // softness = halo of the outline colour outside the shape
        if (softness > 0.0001 && dist > 0.0) {
            glow = 1.0 - smoothstep(-softness, softness, dist);
        }
        coverage = max(crisp, glow);
    } else {
        // softness = soft falloff of the fill (shadows / glows)
        float soft = 1.0 - smoothstep(-1.0, max(softness, 0.0) + 1.0, dist);
        coverage = mix(crisp, soft, clamp(softness, 0.0, 1.0));
    }
    if (coverage <= 0.0) {
        discard;
    }

    vec4 fill = createGradient((gl_FragCoord.xy - location) / max(size, vec2(0.0001)), color1, color2, color3, color4);
    if (!outlined) {
        fragColor = vec4(fill.rgb, fill.a * coverage);
        return;
    }

    // Stroke band on the inner side of the edge; its centre line sits at
    // thickness - 1 px (the convention the callers were tuned against).
    float band = clamp(thickness - 0.5 - abs(dist), 0.0, 1.0);
    // Mixed premultiplied: a straight-alpha mix dragged the rgb of a
    // transparent fill into the stroke (dark fringe on the outline).
    vec4 pFill = vec4(fill.rgb * fill.a, fill.a);
    vec4 pLine = vec4(outlineColor.rgb * outlineColor.a, outlineColor.a);
    vec4 pre = mix(pFill, pLine, band) * crisp + pLine * max(glow - crisp, 0.0);
    fragColor = vec4(pre.rgb / max(pre.a, 0.00001), pre.a);
}
