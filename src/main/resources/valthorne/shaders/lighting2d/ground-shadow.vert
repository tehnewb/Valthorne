#version 330 core
layout(location=0) in vec3 a_projected0;
layout(location=1) in vec3 a_projected1;
layout(location=2) in vec3 a_projected2;
layout(location=3) in vec3 a_projected3;
layout(location=4) in vec2 a_uv0;
layout(location=5) in vec2 a_uv1;
layout(location=6) in vec2 a_uv2;
layout(location=7) in vec2 a_uv3;
layout(location=8) in vec4 a_uvBounds;
layout(location=9) in vec2 a_style;
uniform mat4 u_projection;
out vec2 uv;
flat out vec4 uvBounds;
flat out float opacity;
flat out int contact;
void main(){
    vec3 projected;
    if(gl_VertexID==0){projected=a_projected0;uv=a_uv0;}
    else if(gl_VertexID==1){projected=a_projected1;uv=a_uv1;}
    else if(gl_VertexID==2){projected=a_projected2;uv=a_uv2;}
    else{projected=a_projected3;uv=a_uv3;}
    uvBounds=a_uvBounds;opacity=a_style.x;contact=int(a_style.y);
    gl_Position=u_projection*vec4(projected.xy,0.0,projected.z);
}
