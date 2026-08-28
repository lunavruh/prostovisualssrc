#version 150
uniform float uTime; uniform vec2 uResolution; uniform vec2 uCameraDir; uniform float uFov; uniform float uIntensity; uniform float uStars; uniform float uAlpha; uniform float uSpeed; uniform float uScale; uniform vec3 uColor;
in vec2 vScreen; out vec4 fragColor;
mat3 rx(float a){float c=cos(a),s=sin(a);return mat3(1,0,0,0,c,s,0,-s,c);} mat3 ry(float a){float c=cos(a),s=sin(a);return mat3(c,0,s,0,1,0,-s,0,c);}
vec3 ray(){float a=uResolution.x/max(uResolution.y,1.),sp=tan(radians(uFov)*.5);vec3 r=normalize(vec3(vScreen.x*sp*a,vScreen.y*sp,1));return normalize(ry(uCameraDir.x)*rx(uCameraDir.y)*r);}
float lines(vec2 p,float t){float a=sin(p.x*3.0+sin(p.y*2.2+t)*1.0-t*.55);float b=sin(p.y*3.5+sin(p.x*2.0-t*.68)*1.10+t*.42);float c=sin((p.x+p.y)*2.2+sin((p.x-p.y)*1.55+t*.48));float v=abs(a+b+c)/3.;return pow(1.-clamp(v,0.,1.),3.0);}
void main(){
 vec3 rd=ray(); float t=uTime*(.15+.24*uSpeed);
 // Continuous coordinates -> no hard longitude seam.
 vec2 p=vec2(rd.x+rd.z*.65,rd.y-rd.z*.28)*(3.1+.38*uScale);
 float breath=.78+.22*sin(t*.72);
 float swell=1.0+.055*sin(t*.43)+.025*sin(t*.19+1.7);
 p*=swell;
 p+=vec2(.13*sin(t*.29)+.05*sin(t*.67), .11*cos(t*.24)-.04*sin(t*.51));
 float c1=lines(p,t),c2=lines(p*1.34+vec2(.7,-.3),-t*.61);
 float c=max(c1,c2*.58); float halo=pow(c,.55);
 vec3 tintBase=max(uColor,vec3(.015));
 vec3 base=mix(tintBase*.010,tintBase*.105,clamp(rd.y*.55+.55,0.,1.));
 vec3 tint=max(uColor,vec3(.015));
 vec3 glow=mix(tint*.58, min(tint*1.55+vec3(.10),vec3(1.30)),.5+.5*sin((p.x-p.y)*.23+t*.16));
 // Significantly toned down vs v3.2: still luminous, no retina burn.
 vec3 col=base+glow*c*(.42+.30*breath)*uAlpha*uIntensity+vec3(.08,.13,.55)*halo*.055*breath*uIntensity;
 float haze=exp(-abs(rd.y-.16)*2.6)*(.028+.028*breath);
 col+=mix(vec3(.035,.08,.42),vec3(.32,.025,.52),.5+.5*sin(t*.17))*haze;
 fragColor=vec4(1.-exp(-col*.96),1.);
}
