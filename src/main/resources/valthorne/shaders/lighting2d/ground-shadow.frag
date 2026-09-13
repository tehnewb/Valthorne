#version 330 core
in vec2 uv;
uniform sampler2D u_texture;
uniform float u_opacity,u_softness;
uniform vec4 u_uvBounds;
uniform int u_contact;
uniform int u_volume;
out vec4 fragColor;
float alphaAt(vec2 p){return texture(u_texture,clamp(p,u_uvBounds.xy,u_uvBounds.zw)).a;}
void main(){
    if(u_contact==1){float r=length(uv*2.0-1.0);fragColor=vec4(0.015,0.025,0.04,(1.0-smoothstep(0.1,1.0,r))*u_opacity*.85);return;}
    float height=clamp((uv.y-u_uvBounds.y)/max(.0001,u_uvBounds.w-u_uvBounds.y),0.0,1.0);
    vec2 d=fwidth(uv)*(u_softness+(u_volume==1?height*2.5:0.0));
    float a=alphaAt(uv)*0.4;
    a+=(alphaAt(uv+vec2(d.x,0))+alphaAt(uv-vec2(d.x,0))
       +alphaAt(uv+vec2(0,d.y))+alphaAt(uv-vec2(0,d.y)))*0.15;
    fragColor=u_volume==1?vec4(.025,.035,.06,a*u_opacity*(1.0-height*.38)):vec4(0,0,0,a*u_opacity);
}
