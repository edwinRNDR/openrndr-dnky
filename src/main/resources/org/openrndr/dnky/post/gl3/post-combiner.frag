#version 330

uniform sampler2D diffuseSpecular;
uniform sampler2D emissive;
uniform sampler2D occlusion;

in vec2 v_texCoord0;
out vec4 o_color;

void main() {

    float o = texture(occlusion, v_texCoord0).r;
    vec3 combined = texture(diffuseSpecular, v_texCoord0).rgb * o + texture(emissive, v_texCoord0).rgb;

    o_color.rgba = vec4(combined, 1.0);

}