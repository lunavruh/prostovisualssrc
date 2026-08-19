#version 150
uniform float uTime; uniform vec2 uResolution; uniform vec2 uCameraDir; uniform float uFov; uniform float uIntensity; uniform float uStars;
in vec2 vScreen; out vec4 fragColor;
float h21(vec2 p){p=fract(p*vec2(.1031,.11369));p+=dot(p,p.yx+33.33);return fract((p.x+p.y)*p.x);}
float vnoise(vec2 p){vec2 i=floor(p),f=fract(p);f=f*f*(3.-2.*f);float a=h21(i),b=h21(i+vec2(1,0)),c=h21(i+vec2(0,1)),d=h21(i+vec2(1,1));return mix(mix(a,b,f.x),mix(c,d,f.x),f.y);}
float fbm2(vec2 p){return .67*vnoise(p)+.33*vnoise(mat2(.8,.6,-.6,.8)*p*2.03);}
mat3 rx(float a){float c=cos(a),s=sin(a);return mat3(1,0,0,0,c,s,0,-s,c);} mat3 ry(float a){float c=cos(a),s=sin(a);return mat3(c,0,s,0,1,0,-s,0,c);}
vec3 ray(){float a=uResolution.x/max(uResolution.y,1.),sp=tan(radians(uFov)*.5);vec3 r=normalize(vec3(vScreen.x*sp*a,vScreen.y*sp,1));return normalize(ry(uCameraDir.x)*rx(uCameraDir.y)*r);}
float starPlane(vec2 p,float seed){vec2 g=p*125.+seed,id=floor(g),q=fract(g)-.5;float r=h21(id+seed);vec2 o=vec2(h21(id+17.+seed),h21(id+47.+seed))-.5;return (1.-smoothstep(.016,.058,length(q-o*.6)))*step(1.-.025*uStars,r);}
float stars(vec3 d){vec3 w=pow(abs(d),vec3(5.));w/=max(w.x+w.y+w.z,.001);return starPlane(d.xy,2.)*w.z+starPlane(d.xz,19.)*w.y+starPlane(d.yz,41.)*w.x;}
void main(){vec3 rd=ray();float t=uTime*.10;float up=clamp(rd.y*.5+.5,0.,1.);vec3 col=mix(vec3(.002,.005,.016),vec3(.008,.024,.055),up);col+=vec3(.72,.84,1.)*stars(rd)*.75;
 vec3 aur=vec3(0);float lum=0.;
 for(int i=0;i<3;i++){float fi=float(i);float denom=max(rd.y+.18,.08);vec2 p=rd.xz*(1.0+fi*.38)/denom; p+=vec2(t*(.17+.03*fi)+fi*4.1,-t*(.055+.02*fi)+fi*2.7);
   float d=fbm2(p*vec2(.72,.24)+vec2(fi*6.3,0));float center=.46+fi*.07;float band=exp(-pow((d-center)/(.070+fi*.008),2.));
   float folds=.5+.5*sin(p.x*(5.2+fi*1.1)+fbm2(p*.52)*7.0+t*(.65+.13*fi));band*=.24+.76*folds*folds;
   float fade=smoothstep(-.02+.03*fi,.42+.10*fi,rd.y)*(1.-smoothstep(.985,1.,rd.y));band*=fade;lum+=band*(1.-fi*.22);
   vec3 tint=i==0?vec3(.04,1.05,.48):(i==1?vec3(.02,.70,1.06):vec3(.52,.18,1.06));aur+=tint*band*(1.-fi*.16);
 }
 float crown=smoothstep(.42,.94,rd.y)*(.10+.07*sin((rd.x+rd.z)*12.+t*.7));aur+=mix(vec3(.02,.52,1.0),vec3(.08,.95,.42),.55)*max(crown,0.);
 col+=aur*(.72*uIntensity);col+=mix(vec3(.025,.45,.62),vec3(.05,.75,.38),.55)*exp(-abs(rd.y)*6.0)*(.025+.11*clamp(lum,0.,1.5))*uIntensity;
 fragColor=vec4(1.-exp(-col*1.08),1.);}
