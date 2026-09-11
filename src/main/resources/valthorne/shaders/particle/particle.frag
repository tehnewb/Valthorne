#version 330 core

uniform sampler2D u_texture;
uniform vec4 u_uvRect; // (u0, v0, u1, v1)

in vec4 v_col;
in vec2 v_aspect;
in float v_rot;

out vec4 fragColor;

vec2 rot2(vec2 p, float radians) {
    float c = cos(radians);
    float s = sin(radians);
    p -= vec2(0.5);
    vec2 r = vec2(p.x * c - p.y * s, p.x * s + p.y * c);
    return r + vec2(0.5);
}

void main() {
    vec2 uv = gl_PointCoord;

    vec2 centered = uv - vec2(0.5);
    centered /= max(v_aspect, vec2(0.0001));
    uv = centered + vec2(0.5);

    if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) discard;

    uv = rot2(uv, radians(-v_rot));

    vec2 atlasUV = vec2(
            mix(u_uvRect.x, u_uvRect.z, uv.x),
            mix(u_uvRect.y, u_uvRect.w, uv.y)
    );

    vec4 tex = texture(u_texture, atlasUV);
    fragColor = tex * v_col;
}
