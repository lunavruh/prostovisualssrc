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

in vec2 vScreen;
out vec4 fragColor;

float hash21(vec2 p) {
    p = fract(p * vec2(.1031, .11369));
    p += dot(p, p.yx + 33.33);
    return fract((p.x + p.y) * p.x);
}

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
    float value = 0.0, weight = .54;
    for (int i = 0; i < 5; i++) {
        value += noise3(p) * weight;
        p = p * 2.03 + vec3(7.1, 3.7, 5.9);
        weight *= .49;
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

float starLayer(vec2 p, float seed) {
    vec2 cell = p * 126.0 + seed;
    vec2 id = floor(cell), q = fract(cell) - .5;
    float rnd = hash21(id + seed);
    return (1.0 - smoothstep(.013, .055, length(q))) * step(1.0 - .016 * uStars, rnd);
}

float starField(vec3 d) {
    vec3 w = pow(abs(d), vec3(7.0));
    w /= max(w.x + w.y + w.z, .001);
    return starLayer(d.xy, 4.0) * w.z + starLayer(d.xz, 24.0) * w.y + starLayer(d.yz, 54.0) * w.x;
}

void main() {
    vec3 rd = normalize(rotY(uRotation) * viewRay());
    float time = uTime * uSpeed * .040;
    vec3 energyDir = normalize(vec3(.38, -.04, .92));
    vec3 side = normalize(cross(vec3(0, 1, 0), energyDir));
    vec3 up = normalize(cross(energyDir, side));
    float facing = dot(rd, energyDir);
    vec2 p = vec2(dot(rd, side), dot(rd, up)) / max(facing, .075);
    p /= uScale;

    vec3 col = vec3(.0007, .00015, .0018);
    col += vec3(.80, .73, 1.0) * starField(rd) * .48;

    float region = smoothstep(-.08, .10, facing) * (1.0 - smoothstep(1.35, 2.25, length(p)));
    vec2 center = p - vec2(.34, -.06);
    float r = length(center);
    float a = atan(center.y, center.x);
    vec3 sphereP = rd * (3.2 / uScale);
    float warp = fbm(sphereP + vec3(time, -time * .43, time * .16));
    float detail = fbm(sphereP * 1.72 + vec3(-time * .31, 8.2, time * .28));
    float spiralA = a + r * 3.8 - time * 1.15 + (warp - .5) * 2.8;
    float curls = .5 + .5 * sin(spiralA * 5.0 - detail * 6.0);
    float ridges = pow(1.0 - abs(curls * 2.0 - 1.0), 2.0);
    float smoke = smoothstep(.34, .76, warp * .67 + detail * .46);
    float ringA = exp(-pow((r - .28 - sin(a * 3.0 + time) * .035) / .11, 2.0));
    float ringB = exp(-pow((r - .63 - sin(a * 4.0 - time * .7) * .09) / .18, 2.0));
    float energy = region * smoke * (ridges * .82 + ringA * .75 + ringB * .42);
    float core = exp(-dot(center * vec2(1.25, .86), center * vec2(1.25, .86)) * 8.7) * region;
    float halo = exp(-r * 1.65) * region;

    vec3 darkPurple = vec3(.11, .008, .19);
    vec3 hotPink = vec3(1.18, .055, .72);
    vec3 energyColor = mix(darkPurple, hotPink, clamp(ridges + core * .35, 0.0, 1.0));
    col += energyColor * energy * 1.55 * uIntensity;
    col += vec3(.47, .045, .69) * halo * (.18 + smoke * .38) * uIntensity;
    col += vec3(1.42, .63, 1.18) * core * (1.05 + detail * .55) * uIntensity;
    col += vec3(1.0, .91, 1.0) * pow(core, 3.0) * 1.75 * uIntensity;

    fragColor = vec4(1.0 - exp(-col * 1.16), 1.0);
}
