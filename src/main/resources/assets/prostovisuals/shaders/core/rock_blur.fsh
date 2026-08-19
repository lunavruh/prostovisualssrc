#version 150

#moj_import <prostovisuals:common.glsl>

in vec2 FragCoord;
in vec2 TexCoord;
in vec4 FragColor;

uniform sampler2D Sampler0;
uniform vec2 Size;
uniform vec4 Radius;
uniform float Smoothness;
uniform float BlurRadius;

out vec4 OutColor;

void main() {
    vec2 texel = 1.0 / vec2(textureSize(Sampler0, 0));
    vec2 d = texel * max(0.35, min(BlurRadius, 10.0) * 0.72);

    // Kawase-inspired 9 taps instead of the reference shader's ~80 taps.
    vec3 c = texture(Sampler0, TexCoord).rgb * 0.24;
    c += texture(Sampler0, TexCoord + vec2( d.x, 0.0)).rgb * 0.10;
    c += texture(Sampler0, TexCoord + vec2(-d.x, 0.0)).rgb * 0.10;
    c += texture(Sampler0, TexCoord + vec2(0.0,  d.y)).rgb * 0.10;
    c += texture(Sampler0, TexCoord + vec2(0.0, -d.y)).rgb * 0.10;
    vec2 q = d * 0.72;
    c += texture(Sampler0, TexCoord + vec2( q.x,  q.y)).rgb * 0.09;
    c += texture(Sampler0, TexCoord + vec2(-q.x,  q.y)).rgb * 0.09;
    c += texture(Sampler0, TexCoord + vec2( q.x, -q.y)).rgb * 0.09;
    c += texture(Sampler0, TexCoord + vec2(-q.x, -q.y)).rgb * 0.09;

    vec2 center = Size * 0.5;
    float distance = rdist(center - FragCoord * Size, center - 1.0, Radius);
    float alpha = 1.0 - smoothstep(1.0 - Smoothness, 1.0, distance);
    if (alpha <= 0.001) discard;

    OutColor = vec4(c * FragColor.rgb, alpha * FragColor.a);
}
