#version 330 core
in vec2 uv,local,world;
uniform sampler2D u_texture;
uniform vec4 u_card,u_tint,u_lights[5];
uniform vec2 u_base;
uniform float u_energy[5];
uniform int u_count;
out vec4 fragColor;
void main(){
    vec4 texel=texture(u_texture,uv)*u_tint;
    if(texel.a<.01)discard;
    float h=clamp((local.y-u_card.y)/max(.01,u_card.z-u_card.y),0.0,1.0);
    float side=clamp((local.x-u_card.x)*3.2,-.95,.95);
    // Camera-facing rounded card: X lateral, Y into the ground plane, Z height.
    vec3 normal=normalize(vec3(side,-sqrt(max(.05,1.0-side*side)),(h-.3)*1.5));
    vec3 position=vec3(world.x,u_base.y,h*u_card.w);
    float total=0.0,diffuse=0.0;
    for(int i=0;i<u_count;i++){
        vec3 offset=u_lights[i].xyz-position;
        float distance=length(offset),a=max(0.0,1.0-distance/max(1.0,u_lights[i].w));
        float energy=a*a*u_energy[i];
        diffuse+=energy*max(0.0,dot(normal,offset/max(distance,.001)));total+=energy;
    }
    // Lighting2D supplies light color/intensity; this pass modulates surface orientation only.
    float relief=mix(.82,.52+1.05*diffuse/max(.001,total),smoothstep(0.0,.15,total));
    float contact=mix(.72,1.0,smoothstep(0.0,.25,h));
    fragColor=vec4(texel.rgb*relief*contact,texel.a);
}
