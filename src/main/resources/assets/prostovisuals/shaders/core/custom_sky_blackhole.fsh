#version 150
uniform float uTime; uniform vec2 uResolution; uniform vec2 uCameraDir; uniform float uFov; uniform float uIntensity; uniform float uStars; uniform vec2 uAnchorDir; uniform float uRotation;
in vec2 vScreen; out vec4 fragColor;

float h21(vec2 p){p=fract(p*vec2(.1031,.11369));p+=dot(p,p.yx+33.33);return fract((p.x+p.y)*p.x);}
mat3 rx(float a){float c=cos(a),s=sin(a);return mat3(1,0,0,0,c,s,0,-s,c);} 
mat3 ry(float a){float c=cos(a),s=sin(a);return mat3(c,0,s,0,1,0,-s,0,c);}
vec3 ray(){float a=uResolution.x/max(uResolution.y,1.),sp=tan(radians(uFov)*.5);vec3 r=normalize(vec3(vScreen.x*sp*a,vScreen.y*sp,1));return normalize(ry(uCameraDir.x)*rx(uCameraDir.y)*r);} 
vec3 dirAng(vec2 a){return normalize(ry(a.x)*rx(a.y)*vec3(0,0,1));}
float starPlane(vec2 p,float seed){vec2 g=p*120.+seed,id=floor(g),q=fract(g)-.5;float r=h21(id+seed);return (1.-smoothstep(.016,.060,length(q)))*step(1.-.022*uStars,r);}
float stars(vec3 d){vec3 w=pow(abs(d),vec3(5.));w/=max(w.x+w.y+w.z,.001);return starPlane(d.xy,3.)*w.z+starPlane(d.xz,21.)*w.y+starPlane(d.yz,49.)*w.x;}

void main(){
    vec3 rd=ray();
    vec3 hd=dirAng(vec2(uAnchorDir.x+uRotation,uAnchorDir.y));
    float facing=dot(rd,hd);
    vec3 side=normalize(cross(vec3(0,1,0),hd)+vec3(.00001,0,0));
    vec3 up=normalize(cross(hd,side));
    vec2 p=vec2(dot(rd,side),dot(rd,up))/max(facing,.055);
    float r=length(p);

    // Background gravitational lensing: stars bend around the mass instead of staying flat.
    float bend=exp(-pow((r-.18)/.115,2.))*.16 + exp(-pow((r-.115)/.050,2.))*.10;
    vec3 warped=normalize(rd+hd*bend);
    vec3 col=vec3(.0007,.001,.004)+vec3(.66,.76,1.0)*stars(warped)*.72;

    if(facing>.045){
        // Deep event horizon: dark center + inward falloff + lower inner rim creates depth.
        float horizon=1.0-smoothstep(.112,.137,r);
        float throat=1.0-smoothstep(.080,.126,r);
        col*=1.0-horizon*.995;
        col*=1.0-throat*.97;
        float innerDepth=exp(-pow((r-.116)/.020,2.));
        col += vec3(.018,.024,.045)*innerDepth*.42;

        // Photon ring hugs the event horizon, but is asymmetric to read as 3D lighting.
        float photon=exp(-pow((r-.145)/.0075,2.));
        float photonLight=.58+.42*smoothstep(-.35,.65,p.x/r);
        col+=vec3(.80,.90,1.16)*photon*photonLight*1.45*uIntensity;

        // One fixed accretion disk. The disk itself stays in place/orientation.
        // Matter rotates around the black hole's OWN axis: only the angular phase travels.
        vec2 dp=vec2(p.x,p.y/.30);
        float dr=length(dp);
        float ang=atan(dp.y,dp.x);
        float flowAng=ang-uTime*.62;
        float disk=smoothstep(.165,.205,dr)*(1.-smoothstep(.205,.79,dr));

        // Moving spiral lanes + clumps. Rotation is geometric, not just brightness pulsing.
        float lanes=.52+.48*sin(flowAng*8.0-dr*33.0);
        float lanes2=.70+.30*sin(flowAng*17.0+dr*49.0+sin(flowAng*3.0)*.7);
        float clumps=.72+.28*sin(flowAng*29.0-dr*71.0);
        float density=disk*(.36+.64*lanes)*lanes2*clumps;

        // Perspective: front side is brighter/thicker, far side is dimmer, making the disk spatial.
        float front=.34+.66*smoothstep(-.16,.24,dp.y);
        float far=.30+.70*smoothstep(-.38,.08,-dp.y);
        float doppler=.62+.78*smoothstep(-.60,.72,cos(ang-.40));
        vec3 warm=mix(vec3(.40,.045,.006),vec3(1.35,.66,.16),clamp(1.-dr*.72,0.,1.));
        vec3 blue=vec3(.32,.55,1.18);
        vec3 diskCol=mix(warm,blue,clamp((doppler-1.0)*.42,0.,.34));
        col += diskCol*density*front*doppler*1.28*uIntensity;

        // Thin occlusion over the far side: the black sphere crosses in front of the disk.
        float occluder=(1.0-smoothstep(.124,.147,r))*far;
        col*=1.0-occluder*.94;

        // Subtle depth glow only around the horizon; avoids the old giant top/bottom arcs.
        float nearGlow=exp(-pow((r-.168)/.030,2.));
        col+=vec3(.15,.25,.58)*nearGlow*.13*uIntensity;
    }

    fragColor=vec4(1.-exp(-col*1.18),1.);
}
