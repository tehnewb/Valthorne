#version 330 core
in vec2 local;flat in vec4 light;flat in vec4 color;flat in vec4 shape;
flat in float elevation;
uniform sampler2D u_shadows;uniform float u_rows, u_resolution;
out vec4 fragColor;
float compareShadow(float u, float row, float t, float width){
    float coordinate = fract(u) * u_resolution - .5, base = floor(coordinate), fraction = fract(coordinate);
    float a = texture(u_shadows, vec2((base + .5) / u_resolution, row)).r;
    float b = texture(u_shadows, vec2((base + 1.5) / u_resolution, row)).r;
    // Interpolate comparisons, not depths, to avoid long streaks at depth discontinuities.
    return mix(1.0 - smoothstep(a, a + width, t), 1.0 - smoothstep(b, b + width, t), fraction);
}
void main(){ float d = length(local), t = d / light.z;if (t >= 1.0)discard;
    float angle = atan(local.y, local.x), u = angle / 6.28318530718;float visibility = 1.0;
    if (light.w >= 0.0){ float row = (light.w + .5) / u_rows;float blocker = texture(u_shadows, vec2(u, row)).r;
        float penumbra = shape.x / light.z * max(t - blocker, 0.0) / max(t * blocker, .0001) / 6.28318530718;
        float spread = clamp(penumbra, 0.0, .035);float sum = 0.0;
        float edgeWidth = max(.0005, fwidth(t));
        for (int i = -2;i <= 2;i++)sum += compareShadow(u + float(i) * spread * .5, row, t, edgeWidth);
        visibility = sum * .2; }
    float cone = 1.0;
    if (shape.z > -.9999){ float c = cos(angle - shape.w);cone = shape.y - shape.z < .00001?step(shape.z, c):smoothstep(shape.z, shape.y, c); }
    float t3=length(vec3(local,elevation))/light.z;if(t3>=1.0)discard;
    float window = 1.0 - t3 * t3;float falloff = window * window / (1.0 + 6.0 * t3 * t3);
    fragColor = vec4(color.rgb * color.a * falloff * visibility * cone, 0); }
