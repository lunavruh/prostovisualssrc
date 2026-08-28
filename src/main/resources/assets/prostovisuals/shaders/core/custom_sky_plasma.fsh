#version 150
uniform float uTime;
uniform vec2 uResolution;
uniform vec2 uCameraDir;
uniform float uFov;
uniform float uIntensity;
uniform float uRotation;
uniform float uSpeed;
uniform float uScale;
uniform float uStars;
in vec2 vScreen;
out vec4 fragColor;

mat3 rx(float a){float c=cos(a),s=sin(a);return mat3(1,0,0,0,c,s,0,-s,c);}
mat3 ry(float a){float c=cos(a),s=sin(a);return mat3(c,0,s,0,1,0,-s,0,c);}
vec3 ray(){
    float aspect=uResolution.x/max(uResolution.y,1.0);
    float spread=tan(radians(clamp(uFov,30.0,130.0))*0.5);
    vec3 d=normalize(vec3(vScreen.x*spread*aspect,vScreen.y*spread,1.0));
    return normalize(ry(uCameraDir.x+uRotation)*rx(uCameraDir.y)*d);
}
void main(){
    vec3 d=ray();
    float t=uTime*.34; // Java applies speed globally
    float scale=max(uScale,.18);
    float a=sin((d.x*6.2+d.y*3.4+d.z*2.0)/scale+t);
    float b=sin((d.y*7.4-d.z*4.8)/scale-t*.81);
    float wave=(a+b)*.5;
    float vein=pow(1.0-clamp(abs(wave),0.0,1.0),2.6);
    float halo=.5+.5*sin(t*.55+wave*3.0);
    vec3 base=mix(vec3(.018,.004,.055),vec3(.055,.015,.105),clamp(d.y*.5+.5,0.0,1.0));
    vec3 col=base+vec3(.32,.12,.62)*vein*(.55+.45*halo)*uIntensity;
    col+=vec3(.92,.64,1.0)*pow(vein,4.0)*.52*uIntensity;
    // Keep uStars active in the program without adding an unstable procedural star field.
    col+=vec3(.01,.012,.018)*clamp(uStars,0.0,1.5);
    fragColor=vec4(clamp(col,0.0,1.0),1.0);
}
