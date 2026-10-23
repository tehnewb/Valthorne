$input v_color, v_uv, v_glyph, v_band, v_world, v_clip, v_params
#include <bgfx_shader.sh>
#define uv v_uv
#define local v_band.xy
#define world v_world

SAMPLER2D(u_texture,2);
uniform vec4 u_opacity;
uniform vec4 u_softness;
uniform vec4 u_uvBounds;
uniform vec4 u_contact;
uniform vec4 u_volume;

float alphaAt(vec2 p){return texture2D(u_texture,clamp(p,u_uvBounds.xy,u_uvBounds.zw)).a;}
void main(){
    if(u_contact.x==1){float r=length(uv*2.0-1.0);gl_FragColor=vec4(0.015,0.025,0.04,(1.0-smoothstep(0.1,1.0,r))*u_opacity.x*.85);return;}
    float height=clamp((uv.y-u_uvBounds.y)/max(.0001,u_uvBounds.w-u_uvBounds.y),0.0,1.0);
    vec2 d=fwidth(uv)*(u_softness.x+(u_volume.x==1?height*2.5:0.0));
    float a=alphaAt(uv)*0.4;
    a+=(alphaAt(uv+vec2(d.x,0))+alphaAt(uv-vec2(d.x,0))
       +alphaAt(uv+vec2(0,d.y))+alphaAt(uv-vec2(0,d.y)))*0.15;
    gl_FragColor=u_volume.x==1?vec4(.025,.035,.06,a*u_opacity.x*(1.0-height*.38)):vec4(0,0,0,a*u_opacity.x);
}
