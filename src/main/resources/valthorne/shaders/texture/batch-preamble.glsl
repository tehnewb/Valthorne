#version 330 core
${ SAMPLERS}in vec2 v_uv;
in vec4 v_col;
in float v_tex;
in vec2 v_world;
in vec4 v_clipRect;
in float v_clipEnabled;

vec4 sampleBatchTexture(vec2 uv) {
    float t = v_tex;
${ SELECTION}    return texture(u_tex0, uv);
}

bool batchClipped() {
    return v_clipEnabled > 0.5 &&
    (v_world.x < v_clipRect.x ||
    v_world.y < v_clipRect.y ||
    v_world.x > v_clipRect.x + v_clipRect.z ||
    v_world.y > v_clipRect.y + v_clipRect.w);
}

