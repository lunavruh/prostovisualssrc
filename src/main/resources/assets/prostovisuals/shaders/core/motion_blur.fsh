#version 150

uniform sampler2D Sampler0;
uniform vec2 ViewResolution;
uniform vec2 MotionPixels;
uniform float Strength;
uniform int SampleCount;

in vec2 TexCoord;
in vec4 VertexColor;
out vec4 OutColor;

/*
 * Performance version: maximum seven texture fetches per pixel.
 * The old shader could do 22-28 fetches per pixel, which is brutal at 1440p.
 */
void main() {
    vec2 uv = clamp(TexCoord, vec2(0.001), vec2(0.999));
    vec2 velocity = MotionPixels / max(ViewResolution, vec2(1.0));
    float pixelLength = length(MotionPixels);

    vec4 center = texture(Sampler0, uv);
    if (pixelLength < 0.65 || Strength <= 0.001) {
        OutColor = center * VertexColor;
        return;
    }

    vec4 blurred;

    if (SampleCount <= 3) {
        // 3 taps. Linear filtering of the half-res source already gives a soft
        // result, so this is enough for small/normal turns.
        vec4 a = texture(Sampler0, clamp(uv - velocity * 0.42, vec2(0.001), vec2(0.999)));
        vec4 b = texture(Sampler0, clamp(uv + velocity * 0.42, vec2(0.001), vec2(0.999)));
        blurred = center * 0.40 + (a + b) * 0.30;
    } else if (SampleCount <= 5) {
        vec4 a = texture(Sampler0, clamp(uv - velocity * 0.50, vec2(0.001), vec2(0.999)));
        vec4 b = texture(Sampler0, clamp(uv - velocity * 0.22, vec2(0.001), vec2(0.999)));
        vec4 c = texture(Sampler0, clamp(uv + velocity * 0.22, vec2(0.001), vec2(0.999)));
        vec4 d = texture(Sampler0, clamp(uv + velocity * 0.50, vec2(0.001), vec2(0.999)));
        blurred = center * 0.28 + (b + c) * 0.22 + (a + d) * 0.14;
    } else {
        vec4 a = texture(Sampler0, clamp(uv - velocity * 0.50, vec2(0.001), vec2(0.999)));
        vec4 b = texture(Sampler0, clamp(uv - velocity * 0.33, vec2(0.001), vec2(0.999)));
        vec4 c = texture(Sampler0, clamp(uv - velocity * 0.16, vec2(0.001), vec2(0.999)));
        vec4 d = texture(Sampler0, clamp(uv + velocity * 0.16, vec2(0.001), vec2(0.999)));
        vec4 e = texture(Sampler0, clamp(uv + velocity * 0.33, vec2(0.001), vec2(0.999)));
        vec4 f = texture(Sampler0, clamp(uv + velocity * 0.50, vec2(0.001), vec2(0.999)));
        blurred = center * 0.20 + (c + d) * 0.17 + (b + e) * 0.13 + (a + f) * 0.10;
    }

    float movement = smoothstep(0.65, 3.2, pixelLength);
    float amount = movement * (0.24 + 0.76 * Strength);
    OutColor = mix(center, blurred, clamp(amount, 0.0, 1.0)) * VertexColor;
}
