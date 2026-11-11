$input v_color, v_uv, v_glyph, v_band, v_world, v_clip, v_params
#include <bgfx_shader.sh>
SAMPLER2D(u_texture,2);
uniform vec4 u_softness;
uniform vec4 u_volume;
#define uv v_uv
float alphaAt(vec2 p,vec4 bounds){return texture2D(u_texture,clamp(p,bounds.xy,bounds.zw)).a;}
void main(){
    if(v_params.y>0.5){float r=length(uv*2.0-1.0);gl_FragColor=vec4(0.015,0.025,0.04,(1.0-smoothstep(0.1,1.0,r))*v_params.x*.85);return;}
    float height=clamp((uv.y-v_clip.y)/max(.0001,v_clip.w-v_clip.y),0.0,1.0);
    vec2 d=fwidth(uv)*(u_softness.x+(u_volume.x>0.5?height*2.5:0.0));
    float a=alphaAt(uv,v_clip)*0.4;
    a+=(alphaAt(uv+vec2(d.x,0),v_clip)+alphaAt(uv-vec2(d.x,0),v_clip)
       +alphaAt(uv+vec2(0,d.y),v_clip)+alphaAt(uv-vec2(0,d.y),v_clip))*0.15;
    gl_FragColor=u_volume.x>0.5?vec4(.025,.035,.06,a*v_params.x*(1.0-height*.38)):vec4(0,0,0,a*v_params.x);
}
