#version 150
uniform float uTime; uniform vec2 uResolution; uniform vec2 uCameraDir; uniform float uFov; uniform float uIntensity; uniform float uStars; uniform float uAlpha; uniform float uSpeed; uniform float uScale; uniform vec3 uColor;
in vec2 vScreen; out vec4 fragColor;
mat3 rx(float a){float c=cos(a),s=sin(a);return mat3(1,0,0,0,c,s,0,-s,c);} mat3 ry(float a){float c=cos(a),s=sin(a);return mat3(c,0,s,0,1,0,-s,0,c);}
vec3 ray(){float a=uResolution.x/max(uResolution.y,1.),sp=tan(radians(uFov)*.5);vec3 r=normalize(vec3(vScreen.x*sp*a,vScreen.y*sp,1));return normalize(ry(uCameraDir.x)*rx(uCameraDir.y)*r);}
void main(){
 vec3 rd=ray(); float t=uTime*(.13+.22*uSpeed);
 vec2 p=vec2(rd.x+rd.z*.58,rd.y-rd.z*.30)*(5.0+.45*uScale);
 p+=vec2(.08*sin(t*.31),.06*cos(t*.27));
 float a=sin(p.x+sin(p.y*.72-t)*.78+t*.82);
 float b=sin(p.y*1.18+sin(p.x*.66+t*.63)*.72-t*.74);
 float mesh=.5+.5*sin((a+b)*1.35);
 float crest=pow(mesh,7.0);
 float broad=.5+.5*sin(p.x*.42+p.y*.31-t*.32);
 float breath=.88+.12*sin(t*.58);
 vec3 tintBase=max(uColor,vec3(.015));
 vec3 base=mix(tintBase*.012,tintBase*.12,clamp(rd.y*.58+.58,0.,1.));
 vec3 tint=max(uColor,vec3(.015));
 vec3 cyan=mix(tint*.52, min(tint*1.65+vec3(.08),vec3(1.25)),crest);
 vec3 col=base+cyan*crest*(.34+.28*breath)*uAlpha*uIntensity;
 col+=vec3(.00,.18,.30)*broad*.035*breath*uIntensity;
 col+=vec3(.01,.18,.28)*exp(-abs(rd.y+.04)*5.0)*.055;
 fragColor=vec4(1.-exp(-col*1.02),1.);
}
