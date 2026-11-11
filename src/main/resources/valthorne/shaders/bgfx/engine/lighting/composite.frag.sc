$input v_color, v_uv, v_glyph, v_band, v_world, v_clip, v_params
#include <bgfx_shader.sh>
#define uv v_uv
SAMPLER2D(u_scene,0); SAMPLER2D(u_light,1); uniform vec4 u_exposure; uniform vec4 u_lightTexel;
vec3 decode(vec3 c){ return mix(c / 12.92, pow((c + .055) / 1.055, vec3_splat(2.4)), step(vec3_splat(.04045), c)); }
vec3 encode(vec3 c){ return mix(c * 12.92, 1.055 * pow(max(c, vec3_splat(0)), vec3_splat(1.0 / 2.4)) - .055, step(vec3_splat(.0031308), c)); }
void main(){ vec4 scene = texture2D(u_scene, uv);vec2 texel = u_lightTexel.xy;
    vec3 illumination = texture2D(u_light, uv).rgb * .5;
    illumination += (texture2D(u_light, uv + vec2(texel.x, 0)).rgb + texture2D(u_light, uv - vec2(texel.x, 0)).rgb
    + texture2D(u_light, uv + vec2(0, texel.y)).rgb + texture2D(u_light, uv - vec2(0, texel.y)).rgb) * .125;
    vec3 x = decode(scene.rgb) * illumination * u_exposure.x;
    vec3 mapped = clamp((x * (2.51 * x + .03)) / (x * (2.43 * x + .59) + .14), 0.0, 1.0);gl_FragColor = vec4(encode(mapped), scene.a); }
