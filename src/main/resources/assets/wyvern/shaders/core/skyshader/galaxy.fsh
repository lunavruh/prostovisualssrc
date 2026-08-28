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

float starLayer(vec2 p, float density, float seed) {
    vec2 cell = p * density + seed;
    vec2 id = floor(cell);
    vec2 q = fract(cell) - .5;
    float rnd = hash21(id + seed);
    vec2 jitter = vec2(hash21(id + seed + 13.7), hash21(id + seed + 31.1)) - .5;
    float point = 1.0 - smoothstep(.012, .052, length(q - jitter * .54));
    return point * step(1.0 - .022 * uStars, rnd) * (.45 + .75 * rnd);
}

float starField(vec3 d) {
    vec3 weights = pow(abs(d), vec3(7.0));
    weights /= max(weights.x + weights.y + weights.z, .001);
    float stars = starLayer(d.xy, 138.0, 3.0) * weights.z;
    stars += starLayer(d.xz, 138.0, 23.0) * weights.y;
    stars += starLayer(d.yz, 138.0, 53.0) * weights.x;
    return stars;
}

void main() {
    vec3 rd = normalize(rotY(uRotation) * viewRay());
    vec3 blackHoleDir = normalize(vec3(.72, .11, .68));
    vec3 side = normalize(cross(vec3(0, 1, 0), blackHoleDir));
    vec3 up = normalize(cross(blackHoleDir, side));
    float facing = dot(rd, blackHoleDir);
    vec2 p = vec2(dot(rd, side), dot(rd, up)) / max(facing, .035);
    p /= uScale;
    float radius = length(p);

    float lens = exp(-pow((radius - .155) / .105, 2.0)) * .14;
    vec3 lensedRay = normalize(rd + blackHoleDir * lens);
    vec3 col = vec3(.00025, .00035, .0011);
    col += vec3(.78, .82, 1.0) * starField(lensedRay) * .82;

    if (facing > .03) {
        vec2 diskPoint = vec2(p.x, p.y / .205);
        float diskRadius = length(diskPoint);
        float angle = atan(diskPoint.y, diskPoint.x);
        float disk = smoothstep(.175, .205, diskRadius) * (1.0 - smoothstep(.66, .82, diskRadius));
        float softDisk = smoothstep(.15, .20, diskRadius) * (1.0 - smoothstep(.48, .88, diskRadius));
        float phase = angle - uTime * uSpeed * .34;
        float strands = .53 + .47 * sin(phase * 11.0 - diskRadius * 38.0);
        strands *= .76 + .24 * sin(phase * 23.0 + diskRadius * 67.0);
        float front = .42 + .58 * smoothstep(-.15, .24, diskPoint.y);
        float hot = clamp(1.05 - diskRadius, 0.0, 1.0);
        vec3 diskColor = mix(vec3(.44, .14, .52), vec3(1.38, .76, 1.0), hot);
        col += diskColor * disk * (.36 + .64 * strands) * front * 1.85 * uIntensity;
        col += vec3(.36, .17, .55) * softDisk * .15 * uIntensity;

        float horizon = 1.0 - smoothstep(.117, .142, radius);
        col *= 1.0 - horizon * .999;
        float photon = exp(-pow((radius - .151) / .0068, 2.0));
        float glare = exp(-pow((radius - .161) / .021, 2.0));
        col += vec3(1.05, .96, 1.22) * photon * 2.25 * uIntensity;
        col += vec3(.45, .30, .72) * glare * .36 * uIntensity;

        float farSide = disk * (1.0 - smoothstep(-.11, .14, diskPoint.y));
        col *= 1.0 - horizon * farSide * .96;
    }

    fragColor = vec4(1.0 - exp(-col * 1.13), 1.0);
}
