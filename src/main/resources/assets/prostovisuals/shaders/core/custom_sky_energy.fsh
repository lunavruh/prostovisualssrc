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

mat3 rx(float a){float c=cos(a),s=sin(a);return mat3(1,0,0,0,c,s,0,-s,c);}
mat3 ry(float a){float c=cos(a),s=sin(a);return mat3(c,0,s,0,1,0,-s,0,c);}
vec3 ray(){
    float aspect=uResolution.x/max(uResolution.y,1.0);
    float spread=tan(radians(clamp(uFov,30.0,130.0))*0.5);
    vec3 d=normalize(vec3(vScreen.x*spread*aspect,vScreen.y*spread,1.0));
    return normalize(ry(uCameraDir.x+uRotation)*rx(uCameraDir.y)*d);
}
float hash21(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453123);}
float starField(vec3 d){
    vec2 uv=vec2(atan(d.z,d.x)/6.28318530718+.5,asin(clamp(d.y,-1.0,1.0))/3.14159265359+.5);
    vec2 grid=uv*vec2(150.0,75.0);
    vec2 id=floor(grid), q=fract(grid)-.5;
    float r=hash21(id);
    vec2 off=vec2(hash21(id+11.0),hash21(id+37.0))-.5;
    float dist=length(q-off*.42);
    float aa=max(fwidth(dist)*1.7,.004);
    float dotv=1.0-smoothstep(.035,.035+aa,dist);
    return dotv*step(1.0-.018*clamp(uStars,0.0,1.5),r);
}
void main(){
    vec3 d=ray();
    float t=uTime*0.42; // uTime is already scaled by the Java speed slider
    float scale=max(uScale,.18);
    float w1=sin(dot(d,normalize(vec3(.82,.31,.47)))*8.0/scale+t);
    float w2=sin(dot(d,normalize(vec3(-.26,.91,.31)))*11.0/scale-t*.73);
    float ridge=pow(1.0-clamp(abs((w1+w2)*.5),0.0,1.0),3.0);
    float pulse=.72+.28*sin(t*.41+dot(d,vec3(2.0,3.0,1.0)));
    vec3 bg=mix(vec3(.003,.002,.014),vec3(.018,.005,.040),clamp(d.y*.5+.5,0.0,1.0));
    vec3 col=bg;
    col+=vec3(.23,.045,.48)*ridge*(.55+.45*pulse)*uIntensity;
    col+=vec3(.95,.25,.80)*pow(ridge,4.0)*.70*uIntensity;
    col+=vec3(.72,.84,1.0)*starField(d)*.62;
    fragColor=vec4(clamp(col,0.0,1.0),1.0);
}
