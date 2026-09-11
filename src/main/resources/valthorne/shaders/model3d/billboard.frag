#version 330 core

in vec3 v_worldPos;
in vec2 v_uv;
in vec4 v_color;

uniform vec3 u_fog;
uniform float u_alphaCutoff;
uniform vec3 u_cameraPos;
uniform vec4 u_fogColor;
uniform vec4 u_materialTint;
uniform vec4 u_materialEmissive;
uniform float u_materialFogMix;
uniform float u_materialRadianceMix;
uniform sampler2D u_texture;
uniform sampler2D u_lightTexture;
uniform vec2 u_lightWorldMin;
uniform vec2 u_lightWorldSize;
uniform float u_radianceStrength;
uniform int u_applyRadiance;

out vec4 fragColor;

void main() {
    vec4 texel = texture(u_texture, v_uv);
    if (texel.a * v_color.a * u_materialTint.a <= u_alphaCutoff) {
        discard;
    }

    vec4 base = texel * v_color * u_materialTint;
    vec3 lit = base.rgb + u_materialEmissive.rgb * u_materialEmissive.a;
    if (u_applyRadiance == 1 && u_materialRadianceMix > 0.0) {
        vec2 lightUv = clamp((v_worldPos.xy - u_lightWorldMin) / u_lightWorldSize, vec2(0.001), vec2(0.999));
        vec4 radiance = texture(u_lightTexture, lightUv);
        lit += radiance.rgb * (0.08 + radiance.a * 0.42) * u_radianceStrength * u_materialRadianceMix;
    }

    float fog = clamp((distance(v_worldPos, u_cameraPos) - u_fog.x) / (u_fog.y - u_fog.x), 0.0, 1.0) * u_fog.z;
    vec3 finalColor = mix(clamp(lit, 0.0, 1.0), u_fogColor.rgb, fog * u_materialFogMix);
    fragColor = vec4(finalColor, base.a);
}
