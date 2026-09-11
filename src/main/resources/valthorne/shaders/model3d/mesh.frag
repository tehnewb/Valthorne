#version 330 core

in vec3 v_worldPos;
in vec3 v_normal;
in vec4 v_color;
in vec2 v_uv;

uniform sampler2D u_shadowTexture;
uniform mat4 u_shadowMatrix;
uniform int u_hasShadow;
uniform float u_shadowBias;
uniform float u_shadowStrength;
uniform sampler2D u_texture;
uniform int u_hasTexture;
uniform float u_alphaCutoff;
uniform vec3 u_ambient;
uniform vec3 u_directional;
uniform int u_pointCount;
uniform vec4 u_pointPositionRange[8];
uniform vec4 u_pointColorIntensity[8];
uniform vec3 u_fog;
uniform vec3 u_lightDir;
uniform vec3 u_cameraPos;
uniform vec4 u_fogColor;
uniform vec4 u_materialTint;
uniform vec4 u_materialEmissive;
uniform float u_materialLightingMix;
uniform float u_materialFogMix;
uniform float u_materialRadianceMix;
uniform sampler2D u_lightTexture;
uniform vec2 u_lightWorldMin;
uniform vec2 u_lightWorldSize;
uniform float u_radianceStrength;
uniform int u_applyRadiance;

out vec4 fragColor;

void main() {
    vec3 normal = normalize(gl_FrontFacing ? v_normal : -v_normal);
    vec3 lightDir = u_lightDir / max(length(u_lightDir), 0.00001);
    vec3 toCamera = normalize(u_cameraPos - v_worldPos);
    float diffuse = max(dot(normal, lightDir), 0.0);
    float rim = pow(max(1.0 - dot(normal, toCamera), 0.0), 2.0) * 0.08;
    float visibility = 1.0;
    if (u_hasShadow == 1) {
        vec4 shadowClip = u_shadowMatrix * vec4(v_worldPos, 1.0);
        vec3 shadow = shadowClip.xyz / shadowClip.w * 0.5 + 0.5;
        if (all(greaterThanEqual(shadow, vec3(0.0))) && all(lessThanEqual(shadow, vec3(1.0)))) {
            vec2 texel = 1.0 / vec2(textureSize(u_shadowTexture, 0));
            float occlusion = 0.0;
            float bias = max(u_shadowBias * (1.0 - diffuse), u_shadowBias * 0.25);
            for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) {
                float depth = texture(u_shadowTexture, shadow.xy + vec2(x, y) * texel).r;
                occlusion += shadow.z - bias > depth ? 1.0 : 0.0;
            }
            visibility -= occlusion / 9.0 * u_shadowStrength;
        }
    }
    vec3 brightness = u_ambient + diffuse * u_directional * visibility + vec3(rim * 0.45);
    for (int i = 0; i < u_pointCount; i++) {
        vec3 delta = u_pointPositionRange[i].xyz - v_worldPos;
        float dist = length(delta);
        float attenuation = pow(max(1.0 - dist / u_pointPositionRange[i].w, 0.0), 2.0);
        brightness += u_pointColorIntensity[i].rgb * u_pointColorIntensity[i].a
        * max(dot(normal, delta / max(dist, 0.00001)), 0.0) * attenuation;
    }
    vec4 base = v_color * u_materialTint;
    if (u_hasTexture == 1) base *= texture(u_texture, v_uv);
    if (base.a <= u_alphaCutoff) discard;
    vec3 baseColor = base.rgb;
    vec3 glow = vec3(0.0);
    if (u_applyRadiance == 1 && u_materialRadianceMix > 0.0) {
        vec2 lightUv = clamp((v_worldPos.xy - u_lightWorldMin) / u_lightWorldSize, vec2(0.001), vec2(0.999));
        vec4 radiance = texture(u_lightTexture, lightUv);
        glow = radiance.rgb * (0.10 + radiance.a * 0.55) * u_radianceStrength * u_materialRadianceMix;
    }
    vec3 shaded = baseColor * brightness;
    vec3 lit = mix(baseColor, shaded, u_materialLightingMix) + glow + u_materialEmissive.rgb * u_materialEmissive.a;
    lit = clamp(lit, 0.0, 1.0);
    float fog = clamp((distance(v_worldPos, u_cameraPos) - u_fog.x) / (u_fog.y - u_fog.x), 0.0, 1.0) * u_fog.z;
    vec3 finalColor = mix(lit, u_fogColor.rgb, fog * u_materialFogMix);
    fragColor = vec4(finalColor, base.a);
}
