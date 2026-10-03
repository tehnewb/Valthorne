#version 330 core
in vec4 v_color;
in vec2 v_local;
flat in vec2 v_polygon;
out vec4 fragColor;
void main() {
    if (v_polygon.x > 0.0) {
        float angle = atan(v_local.y, v_local.x);
        float sector = fract(angle * v_polygon.x / 6.283185307179586);
        float edgeDistance = length(v_local) * cos((sector - 0.5) * 6.283185307179586 / v_polygon.x);
        if (edgeDistance > v_polygon.y) discard;
    }
    fragColor = v_color;
}
