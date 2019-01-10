#version 330

uniform sampler2D tex0; // color
uniform sampler2D tex1; // depth

in vec2 v_texCoord0;

uniform vec4 color;
uniform float density;

out vec4 o_color;

void main() {
    vec4 original = texture(tex0, v_texCoord0);
    float depth = -texture(tex1, v_texCoord0).z;
    float f = exp(-depth*density);
    o_color = mix(color, original, f);
}