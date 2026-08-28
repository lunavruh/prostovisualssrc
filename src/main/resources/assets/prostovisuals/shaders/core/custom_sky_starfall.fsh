#version 150

uniform float uTime;
uniform vec2 uResolution;
uniform vec2 uCameraDir;
uniform float uFov;
uniform float uIntensity;
uniform float uStars;
uniform float uMeteorFrequency;

in vec2 vScreen;
out vec4 fragColor;

const float PI=3.14159265359;
const float TAU=6.28318530718;
float hash12(vec2 p){ vec3 p3=fract(vec3(p.xyx)*0.1031); p3+=dot(p3,p3.yzx+33.33); return fract((p3.x+p3.y)*p3.z); }
mat3 rx(float a){float c=cos(a),s=sin(a);return mat3(1,0,0,0,c,s,0,-s,c);}
mat3 ry(float a){float c=cos(a),s=sin(a);return mat3(c,0,s,0,1,0,-s,0,c);}
vec3 ray(){float asp=uResolution.x/max(uResolution.y,1.0);float spread=tan(radians(uFov)*0.5);vec3 r=normalize(vec3(vScreen.x*spread*asp,vScreen.y*spread,1.0));return normalize(ry(uCameraDir.x)*rx(uCameraDir.y)*r);}
float stars(vec3 rd,float density){vec2 uv=vec2(atan(rd.z,rd.x)/TAU,asin(clamp(rd.y,-1.0,1.0))/PI);vec2 g=uv*vec2(240.0,130.0);vec2 id=floor(g);vec2 q=fract(g)-0.5;float h=hash12(id);vec2 o=vec2(hash12(id+13.7),hash12(id+47.1))-0.5;float p=1.0-smoothstep(0.018,0.07,length(q-o*0.65));float tw=0.78+0.22*sin(uTime*(0.5+h)+h*40.0);return p*step(1.0-density,h)*tw;}

vec3 meteor(vec3 rd,float seed){float period=(3.0+hash12(vec2(seed,2.))*3.0)/max(.25,uMeteorFrequency);float cyc=uTime/period+seed*3.17;float ph=fract(cyc);if(ph>.18)return vec3(0);float k=ph/.18;float az=hash12(vec2(seed,floor(cyc)))*TAU;float el=.25+hash12(vec2(floor(cyc),seed+8.))*0.55;vec3 st=vec3(cos(el)*cos(az),sin(el),cos(el)*sin(az));vec3 east=normalize(cross(vec3(0,1,0),st)+vec3(.00001,0,0));vec3 north=normalize(cross(st,east));vec3 dir=normalize(-north+east*(hash12(vec2(seed+5.,floor(cyc)))-.5));vec3 n=normalize(cross(st,dir));vec3 b=cross(n,st);float off=dot(rd,n),along=atan(dot(rd,b),dot(rd,st));float head=k*.55,s=along-head,tail=.16;float line=exp(-off*off/.000006);float profile=clamp(1.+s/tail,0.,1.);float env=sin(k*PI);return vec3(.72,.86,1.)*line*profile*profile*step(s,0.)*step(-tail,s)*env;}
void main(){vec3 rd=ray();vec3 col=mix(vec3(.004,.008,.024),vec3(.014,.025,.062),clamp(rd.y*.5+.5,0.,1.));col+=vec3(.76,.86,1.)*stars(rd,.042*uStars);vec3 m=vec3(0);for(int i=0;i<5;i++)m+=meteor(rd,float(i)+1.0);col+=m*1.25*uIntensity;fragColor=vec4(1.-exp(-col*1.05),1.);}
