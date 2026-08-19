#version 150
uniform float uTime; uniform vec2 uResolution; uniform vec2 uCameraDir; uniform float uFov; uniform float uIntensity; uniform float uStars;
in vec2 vScreen; out vec4 fragColor;
float h31(vec3 p){p=fract(p*.1031);p+=dot(p,p.yzx+33.33);return fract((p.x+p.y)*p.z);}
float h21(vec2 p){p=fract(p*vec2(.1031,.11369));p+=dot(p,p.yx+33.33);return fract((p.x+p.y)*p.x);}
mat3 rx(float a){float c=cos(a),s=sin(a);return mat3(1,0,0,0,c,s,0,-s,c);} mat3 ry(float a){float c=cos(a),s=sin(a);return mat3(c,0,s,0,1,0,-s,0,c);}
vec3 ray(){float a=uResolution.x/max(uResolution.y,1.),sp=tan(radians(uFov)*.5);vec3 r=normalize(vec3(vScreen.x*sp*a,vScreen.y*sp,1));return normalize(ry(uCameraDir.x)*rx(uCameraDir.y)*r);}
float n3(vec3 p){vec3 i=floor(p),f=fract(p);f=f*f*(3.-2.*f);float a=h31(i),b=h31(i+vec3(1,0,0)),c=h31(i+vec3(0,1,0)),d=h31(i+vec3(1,1,0));float e=h31(i+vec3(0,0,1)),g=h31(i+vec3(1,0,1)),h=h31(i+vec3(0,1,1)),j=h31(i+vec3(1,1,1));return mix(mix(mix(a,b,f.x),mix(c,d,f.x),f.y),mix(mix(e,g,f.x),mix(h,j,f.x),f.y),f.z);}
float starPlane(vec2 p,float scale,float seed){vec2 g=p*scale+seed;vec2 id=floor(g),q=fract(g)-.5;float r=h21(id+seed);vec2 o=vec2(h21(id+11.7+seed),h21(id+31.1+seed))-.5;return (1.-smoothstep(.018,.065,length(q-o*.6)))*step(1.-.028*uStars,r);}
float stars(vec3 d){vec3 w=pow(abs(d),vec3(5.));w/=max(w.x+w.y+w.z,.001);return starPlane(d.xy,115.,2.1)*w.z+starPlane(d.xz,115.,17.4)*w.y+starPlane(d.yz,115.,41.8)*w.x;}
void main(){vec3 rd=ray();float t=uTime*.018;float alt=clamp(rd.y*.5+.5,0.,1.);vec3 col=mix(vec3(.0015,.002,.010),vec3(.008,.014,.040),alt);
 float n=n3(rd*4.2+vec3(t,-t*.4,t*.2));n=.68*n+.32*n3(rd*8.1+vec3(-t*.6,3.7,t*.3));
 float ribbon=.5+.5*sin(dot(rd,normalize(vec3(.76,.22,.61)))*10.0+n*4.5+t*.8);float cloud=smoothstep(.42,.78,n)*smoothstep(.22,.82,ribbon);
 vec3 neb=mix(vec3(.045,.12,.42),vec3(.42,.055,.50),.5+.5*sin(dot(rd,vec3(5.2,2.1,-4.0))+n*3.));col+=neb*cloud*.42*uIntensity;
 float s=stars(rd);col+=vec3(.68,.80,1.0)*s*.82;col+=vec3(1.0,.72,.55)*stars(normalize(rd+vec3(.17,-.11,.09)))*.33;
 fragColor=vec4(1.-exp(-col*1.12),1.);}
