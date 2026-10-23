$input v_color, v_uv, v_glyph, v_band, v_world, v_clip, v_params
#include <bgfx_shader.sh>
#define uv v_uv
#define local v_band.xy
#define world v_world

SAMPLER2D(u_texture,2);
uniform vec4 u_card,u_tint,u_lights[5];
uniform vec4 u_base;
uniform vec4 u_energy[5];
uniform vec4 u_count;

void main(){
    vec4 texel=texture2D(u_texture,uv)*u_tint;
    if(texel.a<.01)discard;
    float h=clamp((local.y-u_card.y)/max(.01,u_card.z-u_card.y),0.0,1.0);
    float side=clamp((local.x-u_card.x)*3.2,-.95,.95);
    // Camera-facing rounded card: X lateral, Y into the ground plane, Z height.
    vec3 normal=normalize(vec3(side,-sqrt(max(.05,1.0-side*side)),(h-.3)*1.5));
    vec3 position=vec3(world.x,u_base.xy.y,h*u_card.w);
    float total=0.0,diffuse=0.0;
    for(int i=0;i<u_count.x;i++){
        vec3 offset=u_lights[i].xyz-position;
        float distance=length(offset),a=max(0.0,1.0-distance/max(1.0,u_lights[i].w));
        float energy=a*a*u_energy[i].x;
        diffuse+=energy*max(0.0,dot(normal,offset/max(distance,.001)));total+=energy;
    }
    // Lighting2D supplies light color/intensity; this pass modulates surface orientation only.
    float relief=mix(.82,.52+1.05*diffuse/max(.001,total),smoothstep(0.0,.15,total));
    float contact=mix(.72,1.0,smoothstep(0.0,.25,h));
    gl_FragColor=vec4(texel.rgb*relief*contact,texel.a);
}
