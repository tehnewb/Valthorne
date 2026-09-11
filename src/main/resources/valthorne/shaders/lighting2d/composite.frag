#version 330 core
in vec2 uv;out vec4 fragColor;uniform sampler2D u_scene, u_light;uniform float u_exposure;
vec3 decode(vec3 c){ return mix(c / 12.92, pow((c + .055) / 1.055, vec3(2.4)), step(vec3(.04045), c)); }
vec3 encode(vec3 c){ return mix(c * 12.92, 1.055 * pow(max(c, vec3(0)), vec3(1.0 / 2.4)) - .055, step(vec3(.0031308), c)); }
void main(){ vec4 scene = texture(u_scene, uv);vec2 texel = 1.0 / vec2(textureSize(u_light, 0));
    vec3 illumination = texture(u_light, uv).rgb * .5;
    illumination += (texture(u_light, uv + vec2(texel.x, 0)).rgb + texture(u_light, uv - vec2(texel.x, 0)).rgb
    + texture(u_light, uv + vec2(0, texel.y)).rgb + texture(u_light, uv - vec2(0, texel.y)).rgb) * .125;
    vec3 x = decode(scene.rgb) * illumination * u_exposure;
    vec3 mapped = clamp((x * (2.51 * x + .03)) / (x * (2.43 * x + .59) + .14), 0.0, 1.0);fragColor = vec4(encode(mapped), scene.a); }
