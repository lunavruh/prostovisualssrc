#version 150

uniform float uTime;
uniform vec2 uResolution;
uniform vec2 uCameraDir;
uniform float uFov;
uniform float uIntensity;
uniform float uRotation;
uniform float uSpeed;
uniform float uScale;

in vec2 vScreen;
out vec4 fragColor;

float hash31(vec3 p) {
    p = fract(p * .1031);
    p += dot(p, p.yzx + 33.33);
    return fract((p.x + p.y) * p.z);
}

float noise3(vec3 p) {
    vec3 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash31(i), hash31(i + vec3(1, 0, 0)), f.x),
                   mix(hash31(i + vec3(0, 1, 0)), hash31(i + vec3(1, 1, 0)), f.x), f.y),
               mix(mix(hash31(i + vec3(0, 0, 1)), hash31(i + vec3(1, 0, 1)), f.x),
                   mix(hash31(i + vec3(0, 1, 1)), hash31(i + vec3(1, 1, 1)), f.x), f.y), f.z);
}

float fbm(vec3 p) {
    float value = 0.0, weight = .55;
    for (int i = 0; i < 5; i++) {
        value += noise3(p) * weight;
        p = p * 2.04 + vec3(5.2, 8.1, 3.7);
        weight *= .48;
    }
    return value;
}

mat3 rotX(float a) {
    float c = cos(a), s = sin(a);
    return mat3(1, 0, 0, 0, c, s, 0, -s, c);
}

mat3 rotY(float a) {
    float c = cos(a), s = sin(a);
    return mat3(c, 0, s, 0, 1, 0, -s, 0, c);
}

vec3 viewRay() {
    float aspect = uResolution.x / max(uResolution.y, 1.0);
    float spread = tan(radians(uFov) * .5);
    vec3 rd = normalize(vec3(vScreen.x * spread * aspect, vScreen.y * spread, 1.0));
    return normalize(rotY(uCameraDir.x) * rotX(uCameraDir.y) * rd);
}

void main() {
    vec3 rd = normalize(rotY(uRotation) * viewRay());
    float time = uTime * uSpeed * .038;
    vec3 p = rd * (3.8 / uScale);
    float broad = fbm(p + vec3(time, -time * .33, time * .17));
    vec3 warped = p + vec3(broad * 1.8, -broad * 1.15, broad * .9);
    float fine = fbm(warped * 1.55 + vec3(-time * .44, 7.3, time * .29));
    float filamentField = sin(warped.x * 2.65 + fine * 6.8)
            + sin(warped.y * 2.25 - broad * 5.4)
            + sin(warped.z * 2.85 + fine * 4.7);
    float thick = pow(1.0 - smoothstep(.04, .76, abs(filamentField)), 1.45);
    float branches = pow(1.0 - smoothstep(.015, .30,
            abs(sin(filamentField * 3.25 + fine * 8.0))), 2.15);
    float mist = smoothstep(.32, .83, broad * .63 + fine * .52);
    float skyGradient = clamp(rd.y * .5 + .5, 0.0, 1.0);

    vec3 base = mix(vec3(.19, .075, .34), vec3(.48, .25, .72), skyGradient);
    base += vec3(.14, .045, .24) * mist;
    vec3 col = base * (.69 + .28 * uIntensity);
    col += vec3(.63, .41, .94) * thick * (.42 + mist * .55) * uIntensity;
    col += vec3(1.12, .93, 1.30) * branches * (1.0 - mist * .24) * .92 * uIntensity;
    col += vec3(.97, .83, 1.16) * pow(thick, 4.0) * .58 * uIntensity;

    fragColor = vec4(1.0 - exp(-col * 1.10), 1.0);
}
