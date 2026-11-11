$input v_color, v_local, v_polygon
#include <bgfx_shader.sh>
void main() {
    if (v_polygon.x > 0.0) {
        float angle = atan2(v_local.y, v_local.x);
        float sector = fract(angle * v_polygon.x / 6.283185307179586);
        float edgeDistance = length(v_local) * cos((sector - 0.5) * 6.283185307179586 / v_polygon.x);
        if (edgeDistance > v_polygon.y) discard;
    }
    gl_FragColor = v_color;
}
