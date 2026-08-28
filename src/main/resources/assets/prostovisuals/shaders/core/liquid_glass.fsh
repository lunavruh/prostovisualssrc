#version 150

#moj_import <prostovisuals:common.glsl>

in vec2 FragCoord;
in vec2 TexCoord;
in vec4 FragColor;

uniform sampler2D Sampler0;
uniform vec2 Size;
uniform vec4 Radius;
uniform float Smoothness;
uniform vec2 ScreenSize;
uniform vec2 PanelCenter;
uniform float Time;
uniform float BlurRadius;
uniform float DistortionSpeed;
uniform float DistortionIntensity;
uniform float RimStrength;
uniform float Zoom;

out vec4 OutColor;

float roundedDistance(vec2 p) {
    vec2 halfSize = Size * 0.5 - 1.0;
    return rdist(p, halfSize, Radius);
}

vec3 blur9(vec2 uv, vec2 texel, float radius) {
    vec2 d = texel * max(radius, 0.35);
    vec3 c = texture(Sampler0, uv).rgb * 0.24;
    c += texture(Sampler0, clamp(uv + vec2( d.x, 0.0), vec2(0.001), vec2(0.999))).rgb * 0.10;
    c += texture(Sampler0, clamp(uv + vec2(-d.x, 0.0), vec2(0.001), vec2(0.999))).rgb * 0.10;
    c += texture(Sampler0, clamp(uv + vec2(0.0,  d.y), vec2(0.001), vec2(0.999))).rgb * 0.10;
    c += texture(Sampler0, clamp(uv + vec2(0.0, -d.y), vec2(0.001), vec2(0.999))).rgb * 0.10;
    vec2 q = d * 0.72;
    c += texture(Sampler0, clamp(uv + vec2( q.x,  q.y), vec2(0.001), vec2(0.999))).rgb * 0.09;
    c += texture(Sampler0, clamp(uv + vec2(-q.x,  q.y), vec2(0.001), vec2(0.999))).rgb * 0.09;
    c += texture(Sampler0, clamp(uv + vec2( q.x, -q.y), vec2(0.001), vec2(0.999))).rgb * 0.09;
    c += texture(Sampler0, clamp(uv + vec2(-q.x, -q.y), vec2(0.001), vec2(0.999))).rgb * 0.09;
    return c;
}

void main() {
    float shapeAlpha = ralpha(Size, FragCoord, Radius, Smoothness);
    if (shapeAlpha <= 0.001) discard;

    vec2 center = Size * 0.5;
    vec2 localPos = FragCoord * Size - center;
    float distToEdge = abs(roundedDistance(localPos));
    float maxDist = max(1.0, min(center.x, center.y));

    // Rockstar-style edge Fresnel: stronger near the curved rim.
    float edgeGradient = 1.0 - clamp(distToEdge / maxDist, 0.0, 1.0);
    float fresnel = pow(clamp(edgeGradient, 0.0, 1.0), 4.2);
    float thinRim = 1.0 - smoothstep(0.15, 1.55, abs(roundedDistance(localPos)));

    vec2 radial = localPos / max(length(localPos), 0.0001);
    float t = Time * max(DistortionSpeed, 0.01);

    // Two cheap coherent waves.  They bend the sampled scene, but never turn
    // the glass into noisy animated jelly.
    vec2 wave = vec2(
        sin(FragCoord.y * 7.0 + t) + 0.45 * sin((FragCoord.x + FragCoord.y) * 5.0 - t * 0.63),
        cos(FragCoord.x * 6.0 - t * 0.87) + 0.40 * cos((FragCoord.x - FragCoord.y) * 4.0 + t * 0.52)
    );
    wave *= 0.5;

    // Use actual framebuffer coordinates instead of panel-local TexCoord.
    // The old code sampled every glass panel from the wrong part of the screen,
    // which made the scene behind glass look shifted/stretched.
    vec2 baseUv = gl_FragCoord.xy / max(ScreenSize, vec2(1.0));
    vec2 zoomed = PanelCenter + (baseUv - PanelCenter) / max(Zoom, 1.0);
    vec2 refractOffset = radial * fresnel * DistortionIntensity * 3.0;
    refractOffset += wave * DistortionIntensity * mix(0.30, 0.90, fresnel);
    vec2 uv = clamp(zoomed + refractOffset, vec2(0.001), vec2(0.999));

    vec2 texel = 1.0 / max(ScreenSize, vec2(1.0));
    vec3 blurred = blur9(uv, texel, clamp(BlurRadius * 0.70, 0.35, 7.0));
    vec3 sharp = texture(Sampler0, uv).rgb;

    // Preserve background detail while the half-resolution capture supplies
    // the soft glass blur for almost no extra bandwidth.
    vec3 glass = mix(sharp, blurred, 0.64);

    // Premium edge dispersion: only the curved rim gets a tiny RGB split.
    // It reads like thick optical glass without turning the center into a
    // rainbow or adding a second post-processing pass.
    vec2 chromaOffset = radial * texel * (1.6 + BlurRadius * 0.16) * fresnel * 3.2;
    vec3 chromatic = vec3(
        texture(Sampler0, clamp(uv + chromaOffset, vec2(0.001), vec2(0.999))).r,
        sharp.g,
        texture(Sampler0, clamp(uv - chromaOffset, vec2(0.001), vec2(0.999))).b
    );
    glass = mix(glass, chromatic, fresnel * 0.20 * RimStrength);

    // Subtle cool neutralization and contrast, never milky/white.
    float luma = dot(glass, vec3(0.2126, 0.7152, 0.0722));
    glass = mix(vec3(luma), glass, 0.94);
    glass = (glass - 0.5) * 0.97 + 0.495;
    glass = mix(glass, vec3(0.91, 0.95, 1.0), 0.018);

    // Fresnel edge from the supplied reference, toned down for a black UI.
    vec3 fresnelColor = vec3(0.82, 0.91, 1.0);
    glass = mix(glass, fresnelColor, fresnel * 0.055 * RimStrength);
    glass += fresnelColor * thinRim * RimStrength * 0.18;

    // Slow travelling highlight gives motion without another texture pass.
    float sweep = exp(-pow((FragCoord.x * 0.72 + FragCoord.y * 0.28 - fract(Time * 0.035 + 0.18)) / 0.075, 2.0));
    glass += vec3(0.025, 0.035, 0.055) * sweep * (0.35 + 0.65 * fresnel);

    // Broad static highlight from the upper-left, like a curved VisionOS lens.
    float lensHighlight = pow(clamp(1.0 - length(FragCoord - vec2(0.18, 0.12)) * 1.18, 0.0, 1.0), 4.0);
    glass += vec3(0.055, 0.070, 0.095) * lensHighlight * (0.24 + fresnel * 0.76) * RimStrength;

    OutColor = vec4(clamp(glass, 0.0, 1.0) * FragColor.rgb, shapeAlpha * FragColor.a);
}
