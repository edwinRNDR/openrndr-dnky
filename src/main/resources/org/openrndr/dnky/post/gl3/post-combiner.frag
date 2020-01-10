#version 330

uniform sampler2D diffuseSpecular;
uniform sampler2D emissive;
uniform sampler2D ssao;
uniform sampler2D ambientOcclusion;

in vec2 v_texCoord0;
out vec4 o_color;

void main() {
    float o = texture(ssao, v_texCoord0).r;
    vec4 fao = texture(ambientOcclusion, v_texCoord0);
    vec3 ambient = fao.rgb;
    vec3 combined = texture(diffuseSpecular, v_texCoord0).rgb + texture(emissive, v_texCoord0).rgb + ambient * fao.a * o*o;
    o_color.rgba = vec4(combined, 1.0);
}