#version 330 core

uniform mat4 u_mvp;
in vec2 a_local;
in vec2 a_uv;
in vec4 i_xywh;
in vec4 i_col;
in float i_tex;
in vec4 i_uvRect;
in vec2 i_origin;
in vec2 i_rot;
in vec4 i_clipRect;
in float i_clipEnabled;

out vec2 v_uv;
out vec4 v_col;
out float v_tex;
out vec2 v_world;
out vec4 v_clipRect;
out float v_clipEnabled;

void main() {
    vec2 local = a_local * i_xywh.zw;
    local -= i_origin;

    vec2 rotated = vec2(
            local.x * i_rot.y - local.y * i_rot.x,
            local.x * i_rot.x + local.y * i_rot.y
    );

    vec2 world = i_xywh.xy + i_origin + rotated;

    gl_Position = u_mvp * vec4(world.xy, 0.0, 1.0);
    v_uv = mix(i_uvRect.xy, i_uvRect.zw, a_uv);
    v_col = i_col;
    v_tex = i_tex;
    v_world = world;
    v_clipRect = i_clipRect;
    v_clipEnabled = i_clipEnabled;
}
