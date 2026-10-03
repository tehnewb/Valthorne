#version 330 core
uniform mat4 u_mvp;
uniform vec2 u_pixelScale;
in vec2 a_pos;
in float a_size;
in vec2 a_aspect;
in float a_rot;
in vec4 a_col;
out vec4 v_col;
out vec2 v_uv;
const vec2 corners[6] = vec2[6](vec2(0,0), vec2(1,0), vec2(1,1), vec2(0,0), vec2(1,1), vec2(0,1));
void main() {
    vec2 corner = corners[gl_VertexID];
    v_uv = vec2(corner.x, 1.0 - corner.y);
    vec2 offset = (corner - vec2(0.5)) * a_size * a_aspect;
    float angle = radians(a_rot);
    offset = mat2(cos(angle), sin(angle), -sin(angle), cos(angle)) * offset;
    vec4 center = u_mvp * vec4(a_pos, 0, 1);
    center.xy += offset * u_pixelScale * center.w;
    gl_Position = center;
    v_col = a_col;
}
