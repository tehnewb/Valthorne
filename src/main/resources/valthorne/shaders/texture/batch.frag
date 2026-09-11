#version 330 core
${ SAMPLERS}in vec2 v_uv;
in vec4 v_col;
in float v_tex;
in vec2 v_world;
in vec4 v_clipRect;
in float v_clipEnabled;

out vec4 fragColor;

void main() {
    if (v_clipEnabled > 0.5) {
        if (v_world.x < v_clipRect.x || v_world.y < v_clipRect.y ||
            v_world.x > v_clipRect.x + v_clipRect.z ||
            v_world.y > v_clipRect.y + v_clipRect.w) {
            discard;
        }
    }

    vec4 c;
    float t = v_tex;
${ SELECTION}    else c = texture(u_tex0, v_uv);
fragColor = c * v_col;
}
