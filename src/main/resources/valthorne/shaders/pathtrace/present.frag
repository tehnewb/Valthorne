#version 430 core
uniform sampler2D resultImage;
uniform sampler2D specularImage, materialImage;
uniform float exposure;
in vec2 uv;
out vec4 color;
void main(){ vec3 x = max((texture(resultImage, uv).rgb * texture(materialImage, uv).rgb + texture(specularImage, uv).rgb) * exposure, vec3(0));
    x = clamp((x * (2.51 * x + .03)) / (x * (2.43 * x + .59) + .14), 0, 1);
    color = vec4(mix(12.92 * x, 1.055 * pow(x, vec3(1 / 2.4)) - .055, step(vec3(.0031308), x)), 1);
}
