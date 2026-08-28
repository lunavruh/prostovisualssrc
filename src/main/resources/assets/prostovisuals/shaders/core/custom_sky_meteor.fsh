#version 150

uniform float uTime;
uniform vec2 uResolution;
uniform vec2 uCameraDir;
uniform float uFov;
uniform float uWorldTime;
uniform float uIntensity;
uniform float uStars;
uniform float uRotation;
uniform float uMeteorFrequency;

in vec2 vScreen;
out vec4 fragColor;

const float PI = 3.14159265359;
const float TAU = 6.28318530718;

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

mat3 rotX(float a) {
    float c = cos(a);
    float s = sin(a);
    return mat3(1.0, 0.0, 0.0, 0.0, c, s, 0.0, -s, c);
}

mat3 rotY(float a) {
    float c = cos(a);
    float s = sin(a);
    return mat3(c, 0.0, s, 0.0, 1.0, 0.0, -s, 0.0, c);
}

vec3 viewRay() {
    float aspect = uResolution.x / max(uResolution.y, 1.0);
    float spread = tan(radians(uFov) * 0.5);
    vec3 localRay = normalize(vec3(vScreen.x * spread * aspect, vScreen.y * spread, 1.0));
    return normalize(rotY(uCameraDir.x + uRotation) * rotX(uCameraDir.y) * localRay);
}

float starLayer(vec2 uv, float scale, float threshold) {
    vec2 cell = floor(uv * scale);
    vec2 local = fract(uv * scale) - 0.5;
    float seed = hash12(cell);
    vec2 offset = vec2(hash12(cell + 4.7), hash12(cell + 26.1)) - 0.5;
    float point = 1.0 - smoothstep(0.0, 0.085, length(local - offset * 0.67));
    return point * smoothstep(threshold, 1.0, seed);
}

float wrappedDelta(float value) {
    return mod(value + 1.0, 2.0) - 1.0;
}

float meteor(vec2 skyUv, float index) {
    float speed = mix(0.075, 0.14, hash12(vec2(index, 3.4))) * max(uMeteorFrequency, 0.2);
    float phase = uTime * speed + index * 0.0713;
    float localTime = fract(phase);
    float cycle = floor(phase);
    float seedX = hash12(vec2(index * 2.17, cycle + 1.3));
    float seedY = hash12(vec2(index * 5.31, cycle + 7.1));
    vec2 start = vec2(mix(-1.0, 1.0, seedX), mix(0.20, 0.48, seedY));
    vec2 radiant = vec2(0.08 * sin(cycle * 0.37), -0.34);
    vec2 direction = normalize(radiant - start);
    vec2 head = start + direction * localTime * 0.92;
    vec2 relative = skyUv - head;
    relative.x = wrappedDelta(relative.x);

    float along = dot(relative, -direction);
    float across = abs(relative.x * direction.y - relative.y * direction.x);
    float trailLength = mix(0.12, 0.34, hash12(vec2(index, 18.0)));
    float frontFade = smoothstep(-0.006, 0.014, along);
    float endFade = 1.0 - smoothstep(trailLength * 0.72, trailLength, along);
    float lengthFade = exp(-max(along, 0.0) * 3.8 / trailLength);
    float tail = frontFade * endFade * lengthFade;
    float core = exp(-pow(across / 0.0028, 2.0));
    float glow = exp(-pow(across / 0.011, 2.0));
    float roundHead = exp(-dot(relative, relative) / 0.000055);
    float life = smoothstep(0.0, 0.055, localTime) * (1.0 - smoothstep(0.86, 1.0, localTime));
    return life * (tail * core * 1.42 + tail * glow * 0.22 + roundHead * 0.38);
}

void main() {
    vec3 ray = viewRay();
    vec2 skyUv = vec2(atan(ray.z, ray.x) / PI, asin(clamp(ray.y, -1.0, 1.0)) / PI);
    float altitude = clamp(ray.y * 0.5 + 0.5, 0.0, 1.0);
    vec3 color = mix(vec3(0.035, 0.028, 0.050), vec3(0.003, 0.008, 0.026), altitude);

    float stars = starLayer(skyUv + vec2(0.12, 0.25), 125.0, 0.91);
    stars += starLayer(skyUv - vec2(0.24, 0.14), 230.0, 0.968) * 0.62;
    color += vec3(0.70, 0.84, 1.0) * stars * uStars * 1.20;

    float streaks = 0.0;
    for (int i = 0; i < 10; i++) {
        streaks += meteor(skyUv, float(i));
    }
    float skyMask = smoothstep(-0.20, 0.06, ray.y);
    color += vec3(0.34, 0.72, 1.0) * streaks * 0.88 * skyMask;
    color += vec3(0.78, 0.94, 1.0) * streaks * streaks * 0.18 * skyMask;

    float horizon = exp(-abs(ray.y + 0.10) * 6.0);
    color += vec3(0.12, 0.07, 0.09) * horizon * 0.24;
    float exposure = mix(0.84, 1.16, clamp(uIntensity / 1.8, 0.0, 1.0));
    color = vec3(1.0) - exp(-color * exposure);
    fragColor = vec4(color, 1.0);
}
