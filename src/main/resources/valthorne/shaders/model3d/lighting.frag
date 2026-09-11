#version 330 core
in vec3 v_worldPos; in vec3 v_normal; in vec4 v_color; in vec2 v_uv;
out vec4 fragColor;
uniform sampler2D u_texture;
uniform sampler2DShadow u_shadowTexture;
uniform samplerBuffer u_lights;
uniform isamplerBuffer u_grid;
uniform int u_hasTexture, u_hasShadow, u_lightCount, u_tileColumns, u_tileRows, u_tileSize;
uniform vec2 u_viewportOrigin;
uniform mat4 u_shadowMatrix;
uniform float u_shadowBias, u_shadowStrength, u_shadowSoftness;
uniform vec3 u_cameraPos, u_lightDir, u_directional, u_sky, u_ground, u_fog;
uniform vec4 u_materialTint, u_materialEmissive, u_fogColor;
uniform float u_alphaCutoff, u_materialLightingMix, u_materialFogMix, u_roughness, u_metallic, u_exposure;
const float PI = 3.14159265359;
vec3 safeNormal(vec3 v) { return v * inversesqrt(max(dot(v, v), 1e-10)); }
vec3 decode(vec3 c) { return mix(c / 12.92, pow(max((c + .055) / 1.055, vec3(0)), vec3(2.4)), step(vec3(.04045), c)); }
vec3 encode(vec3 c) { return mix(c * 12.92, 1.055 * pow(max(c, vec3(0)), vec3(1.0 / 2.4)) - .055, step(vec3(.0031308), c)); }
vec3 brdf(vec3 n, vec3 v, vec3 l, vec3 base, float rough, float metal) {
    float nl = max(dot(n, l), 0.0); if (nl <= 0.0)return vec3(0);
    float nv = max(dot(n, v), .0001);vec3 h = safeNormal(v + l);
    float nh = max(dot(n, h), 0.0), vh = max(dot(v, h), 0.0);
    float a = rough * rough, a2 = a * a, d = nh * nh * (a2 - 1.0) + 1.0;
    float distribution = a2 / (PI * d * d);
    float visibility = .5 / max(nl * sqrt(nv * nv * (1.0 - a2) + a2) + nv * sqrt(nl * nl * (1.0 - a2) + a2), .0001);
    vec3 f0 = mix(vec3(.04), base, metal);
    float f = 1.0 - vh;float f5 = f * f * f * f * f;
    vec3 fresnel = f0 + (1.0 - f0) * f5;
    return ((1.0 - fresnel) * (1.0 - metal) * base / PI + distribution * visibility * fresnel) * nl;
}
float shadowVisibility(vec3 normal, vec3 light) {
    if (u_hasShadow == 0 || dot(normal, light) <= 0.0)return 1.0;
    vec4 clip = u_shadowMatrix * vec4(v_worldPos, 1);
    vec3 s = clip.xyz / clip.w * .5 + .5;
    if (any(lessThan(s, vec3(0))) || any(greaterThan(s, vec3(1))))return 1.0;
    float bias = max(u_shadowBias * (1.0 - max(dot(normal, light), 0.0)), u_shadowBias * .2);
    vec2 texel = vec2(u_shadowSoftness) / vec2(textureSize(u_shadowTexture, 0));
    float sum = 0.0;
    // Four bilinear hardware comparisons: sixteen comparisons with four texture instructions.
    sum += texture(u_shadowTexture, vec3(s.xy + vec2(-.5, -.5) * texel, s.z - bias));
    sum += texture(u_shadowTexture, vec3(s.xy + vec2(.5, -.5) * texel, s.z - bias));
    sum += texture(u_shadowTexture, vec3(s.xy + vec2(-.5, .5) * texel, s.z - bias));
    sum += texture(u_shadowTexture, vec3(s.xy + vec2(.5, .5) * texel, s.z - bias));
    return mix(1.0, sum * .25, u_shadowStrength);
}
void main() {
    vec4 base = v_color * u_materialTint;
    if (u_hasTexture == 1)base *= texture(u_texture, v_uv);
    if (base.a <= u_alphaCutoff)discard;
    vec3 n = safeNormal(gl_FrontFacing?v_normal:-v_normal), v = safeNormal(u_cameraPos - v_worldPos);
    vec3 albedo = decode(max(base.rgb, vec3(0)));
    float rough = max(.055, u_roughness), metal = u_metallic;
    // Derivative roughness floor reduces shimmering on subpixel specular highlights.
    rough = clamp(sqrt(rough * rough + min(.2, dot(dFdx(n), dFdx(n)) + dot(dFdy(n), dFdy(n)))), .055, 1.0);
    vec3 light = safeNormal(u_lightDir);
    vec3 radiance = mix(u_ground, u_sky, n.z * .5 + .5) * albedo * (1.0 - metal);
    vec3 reflected = reflect(-v, n);
    vec3 environment = mix(u_ground, u_sky, reflected.z * .5 + .5);
    radiance += environment * mix(vec3(.04), albedo, metal) * (1.0 - .5 * rough);
    radiance += brdf(n, v, light, albedo, rough, metal) * u_directional * shadowVisibility(n, light);
    ivec2 tile = clamp(ivec2((gl_FragCoord.xy - u_viewportOrigin) / float(u_tileSize)), ivec2(0), ivec2(u_tileColumns - 1, u_tileRows - 1));
    int offset = (tile.y * u_tileColumns + tile.x) * 65;
    int count = texelFetch(u_grid, offset).r;
    bool overflow = count < 0;if (overflow)count = u_lightCount;
    for (int i = 0;i < count;i++) {
        int index = overflow?i:texelFetch(u_grid, offset + 1 + i).r;
        vec4 pr = texelFetch(u_lights, index * 2), ci = texelFetch(u_lights, index * 2 + 1);
        vec3 delta = pr.xyz - v_worldPos;float d2 = dot(delta, delta), r2 = pr.w * pr.w;
        if (d2 >= r2)continue;
        float window = max(1.0 - (d2 / r2) * (d2 / r2), 0.0);
        float attenuation = window * window / max(d2, .04);
        radiance += brdf(n, v, safeNormal(delta), albedo, rough, metal) * ci.rgb * ci.a * attenuation;
    }
    radiance += decode(max(u_materialEmissive.rgb, vec3(0))) * u_materialEmissive.a;
    vec3 x = max(radiance * u_exposure, vec3(0));
    vec3 mapped = clamp((x * (2.51 * x + .03)) / (x * (2.43 * x + .59) + .14), 0.0, 1.0);
    vec3 color = mix(base.rgb, encode(mapped), u_materialLightingMix);
    float fog = clamp((length(u_cameraPos - v_worldPos) - u_fog.x) / (u_fog.y - u_fog.x), 0.0, 1.0) * u_fog.z * u_materialFogMix;
    fragColor = vec4(mix(color, u_fogColor.rgb, fog), base.a);
}
