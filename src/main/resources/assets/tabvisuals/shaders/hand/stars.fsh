#version 330 core

in vec2 uv;
out vec4 outColor;

uniform sampler2D ColorTexture;
uniform sampler2D DepthTexture;
uniform vec2 resolution;
uniform float time;


float hash1(vec2 p) {
    uvec2 q = uvec2(floatBitsToUint(p.x), floatBitsToUint(p.y));
    uint n = q.x * 1664525u + q.y * 1013904223u + 1234567891u;
    n = (n ^ (n >> 16u)) * 0x45d9f3bu;
    return float(n) / float(0xffffffffu);
}

vec2 hash2(vec2 p) {
    uvec2 q = uvec2(floatBitsToUint(p.x), floatBitsToUint(p.y));
    uint a = q.x * 1664525u + q.y * 1013904223u;
    uint b = q.y * 1664525u + q.x * 1013904223u + 2891336453u;
    a ^= (a >> 16u); b ^= (b >> 16u);
    return vec2(float(a), float(b)) / float(0xffffffffu);
}

float valueNoise(vec2 p) {
    vec2 i = floor(p); vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    float a = hash1(i),              b = hash1(i + vec2(1,0));
    float c = hash1(i + vec2(0,1)), d = hash1(i + vec2(1,1));
    return mix(mix(a,b,u.x), mix(c,d,u.x), u.y);
}

float nebulaNoise(vec2 p) {
    return valueNoise(p) * 0.6 + valueNoise(p * 2.1 + vec2(3.7, 1.9)) * 0.4;
}

float starLayer(vec2 p, float scale, float seed, float brightness, float speed) {
    vec2 drift = vec2(time * 0.045 * speed, time * 0.028 * speed);
    vec2 sp = (p + drift) * scale;
    vec2 id = floor(sp);
    vec2 gv = fract(sp) - 0.5;

    vec2 r = hash2(id + seed);

    vec2 starPos = (r - 0.5) * 0.50;
    float d = length(gv - starPos);

    float size = 0.012 + r.x * 0.030;

    float twinkle = sin(time * (1.5 + r.y * 3.0) + r.y * 6.28318) * 0.35 + 0.65;

    float star = smoothstep(size, size * 0.2, d) * twinkle * brightness;
    star += smoothstep(size * 3.5, size * 0.6, d) * 0.10 * twinkle * brightness;
    return star;
}

vec3 flareLayer(vec2 p) {
    vec2 drift = vec2(time * 0.045, time * 0.028);
    vec2 fp  = (p + drift) * 3.5;
    vec2 id  = floor(fp);
    vec2 gv  = fract(fp) - 0.5;
    vec2 fr  = hash2(id + 999.9);

    if (fr.x <= 0.87) return vec3(0.0);

    vec2 fpos = (fr - 0.5) * 0.50;
    vec2 lg   = gv - fpos;
    float fd  = length(lg);

    float ft = sin(time * 1.2 + fr.y * 12.566) * 0.5 + 0.5;

    float rays = max(
    smoothstep(0.24, 0.0, abs(lg.x)) * smoothstep(0.05, 0.0, abs(lg.y)),
    smoothstep(0.24, 0.0, abs(lg.y)) * smoothstep(0.05, 0.0, abs(lg.x))
    );

    float val = (smoothstep(0.035, 0.0, fd) + rays * 0.4) * ft * 1.8;
    return vec3(0.9, 0.95, 1.0) * val;
}


float meteor(vec2 p) {
    float period = 5.0;
    float t = mod(time * 0.65, period);
    if (t > 1.3) return 0.0;

    float prog = t / 1.3;
    float fade = sin(prog * 3.14159);

    vec2 dir    = normalize(vec2(0.6, -0.8));
    vec2 origin = vec2(-0.3, 0.7);
    float len   = 0.55;
    vec2 pos    = origin + dir * prog * len;

    vec2 dp   = p - pos;
    float t2  = clamp(dot(dp, -dir) / (len * prog + 0.001), 0.0, 1.0);
    float dist = length(dp + dir * t2 * len * prog);

    return smoothstep(0.004, 0.0, dist) * (1.0 - t2 * 0.8) * fade;
}


void main() {
    vec2 ts = 1.0 / textureSize(DepthTexture, 0);
    float d0 = texture(DepthTexture, uv).r;
    float minDepth = min(d0, min(
    min(texture(DepthTexture, uv + vec2( ts.x, 0)).r,
    texture(DepthTexture, uv + vec2(-ts.x, 0)).r),
    min(texture(DepthTexture, uv + vec2(0,  ts.y)).r,
    texture(DepthTexture, uv + vec2(0, -ts.y)).r)
    ));
    float mask = smoothstep(0.99, 0.98, minDepth);
    if (mask < 0.01) discard;

    vec2 aspect = vec2(resolution.x / resolution.y, 1.0);
    vec2 p = uv * aspect;

    float cs = sin(time * 0.15) * 0.5 + 0.5;

    float n1 = nebulaNoise(p * 2.5 + vec2( time * 0.032,  time * 0.018));
    float n2 = nebulaNoise(p * 3.1 + vec2(-time * 0.019,  time * 0.027) + vec2(5.2, 1.3));
    float n3 = nebulaNoise(p * 1.8 + vec2( time * 0.024, -time * 0.015) + vec2(2.1, 8.7));

    vec3 col1 = mix(vec3(0.05, 0.08, 0.65), vec3(0.30, 0.03, 0.55), cs);
    vec3 col2 = mix(vec3(0.45, 0.04, 0.65), vec3(0.05, 0.25, 0.65), cs);
    vec3 col3 = mix(vec3(0.00, 0.40, 0.55), vec3(0.50, 0.05, 0.40), 1.0 - cs);

    vec3 nebula = col1 * pow(n1, 2.2) * 1.8
    + col2 * pow(n2, 2.5) * 1.4
    + col3 * pow(n3, 2.8) * 1.0;
    nebula *= 0.85 + 0.15 * sin(time * 0.35);

    float l1 = starLayer(p, 19.0, 0.0,   0.85, 0.4);
    float l2 = starLayer(p, 12.0, 73.1,  1.10, 0.7);
    float l3 = starLayer(p,  7.0, 137.5, 1.45, 1.0);

    vec2 spBig = p + vec2(time * 0.045, time * 0.028);
    vec2 hxy   = hash2(floor((spBig + 137.5) * 7.0));
    float hz   = hash1(floor((spBig + 137.5) * 7.0) + 1.0);
    vec3 tint  = mix(vec3(0.7, 0.85, 1.0),
    mix(vec3(1.0, 0.95, 0.7), vec3(0.8, 0.65, 1.0), hz), hxy.x);
    vec3 starColor = vec3(0.85, 0.92, 1.0) * (l1 + l2) + tint * l3;

    vec3 flareColor   = flareLayer(p);
    vec3 meteorColor  = vec3(0.85, 0.92, 1.0) * meteor(p) * 2.5;

    vec3 cosmos = vec3(0.0, 0.0, 0.055) + nebula + starColor + flareColor + meteorColor;

    vec2  vp      = uv - 0.5;
    float vig     = 1.0 - dot(vp, vp) * 1.3;
    float breathe = 0.93 + 0.07 * sin(time * 0.5 + 1.0);
    cosmos *= clamp(vig, 0.0, 1.0) * breathe;

    outColor = vec4(cosmos, mask);
}
