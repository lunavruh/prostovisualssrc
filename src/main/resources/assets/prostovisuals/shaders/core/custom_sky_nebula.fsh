#version 150

uniform float uTime;
uniform vec2 uResolution;
uniform vec2 uCameraDir;
uniform float uFov;
uniform float uIntensity;
uniform float uStars;
uniform float uRotation;
uniform float uSpeed;
uniform float uScale;
uniform vec3 uColor;

in vec2 vScreen;
out vec4 fragColor;

const mat2 ROT = mat2(0.82, 0.57, -0.57, 0.82);

float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 345.45));
    p += dot(p, p + 34.345);
    return fract(p.x * p.y);
}

float noise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash21(i), hash21(i + vec2(1,0)), f.x),
               mix(hash21(i + vec2(0,1)), hash21(i + vec2(1,1)), f.x), f.y);
}

float fbm(vec2 p) {
    float s = 0.0, a = 0.54;
    for (int i = 0; i < 4; i++) {
        s += noise(p) * a;
        p = ROT * p * 2.03 + 7.1;
        a *= 0.48;
    }
    return s;
}

vec3 rayDir() {
    float aspect = uResolution.x / max(1.0, uResolution.y);
    float tanHalf = tan(radians(clamp(uFov, 35.0, 120.0)) * 0.5);
    vec3 d = normalize(vec3(vScreen.x * aspect * tanHalf, vScreen.y * tanHalf, -1.0));
    float cp = cos(-uCameraDir.y), sp = sin(-uCameraDir.y);
    d = vec3(d.x, d.y * cp - d.z * sp, d.y * sp + d.z * cp);
    float yaw = uCameraDir.x + uRotation;
    float cy = cos(yaw), sy = sin(yaw);
    return normalize(vec3(d.x * cy - d.z * sy, d.y, d.x * sy + d.z * cy));
}

vec3 starField(vec3 rd, float time) {
    vec3 p = rd * 48.0;
    vec3 c = vec3(0.0);
    float density = 0.045 * clamp(uStars, 0.0, 1.35);
    for (int i = 0; i < 3; i++) {
        vec3 id = floor(p);
        vec3 q = fract(p) - 0.5;
        float r = hash21(id.xy + id.z * 13.0);
        float r2 = hash21(id.yz + id.x * 21.0);
        float core = smoothstep(0.23, 0.0, length(q));
        float hit = step(r, density);
        vec3 tint = mix(vec3(1.0, 0.72, 0.52), vec3(0.62, 0.82, 1.0), r2);
        c += hit * core * core * tint * (0.76 + 0.24 * sin(time + r * 40.0));
        p = p * 1.58 + 11.4;
        density *= 0.60;
    }
    return c * 0.72;
}

void main() {
    float time = uTime * max(0.05, uSpeed);
    vec3 rd = rayDir();
    float horizon = smoothstep(-0.9, 0.8, rd.y);
    vec3 col = mix(vec3(0.025, 0.018, 0.068), vec3(0.004, 0.006, 0.026), horizon);

    float scale = 2.25 / clamp(uScale, 0.50, 2.40);
    vec2 p = vec2(atan(rd.z, rd.x) * 0.82, rd.y * 1.8) * scale;
    p += vec2(time * 0.012, -time * 0.004);

    float n1 = fbm(p);
    float n2 = fbm(p * 1.42 + vec2(5.7, -3.1) - time * 0.006);
    float n3 = fbm(p * 2.18 - vec2(2.4, 7.8) + time * 0.003);
    float cloud = smoothstep(0.34, 0.78, n1 * 0.62 + n2 * 0.28 + n3 * 0.18);
    float filaments = pow(clamp(abs(n1 - n2) * 2.2, 0.0, 1.0), 1.6);

    vec3 c1 = uColor;
    vec3 c2 = mix(vec3(0.08, 0.56, 1.0), vec3(1.0, 0.16, 0.62), 0.5 + 0.5 * sin(p.x + time * 0.03));
    vec3 neb = mix(c1, c2, clamp(n2, 0.0, 1.0));
    col += neb * cloud * (0.42 + 0.34 * filaments) * clamp(uIntensity, 0.25, 1.35);
    col += mix(c2, vec3(0.16, 0.34, 1.0), 0.55) * filaments * cloud * 0.12;
    col += starField(rd, time);

    col = col / (vec3(1.0) + col * 0.72);
    col = pow(clamp(col, 0.0, 1.0), vec3(0.92));
    fragColor = vec4(clamp(col, 0.0, 0.94), 1.0);
}
