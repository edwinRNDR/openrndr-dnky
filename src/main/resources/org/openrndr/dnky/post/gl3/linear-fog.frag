#version 330

uniform sampler2D tex0; // color
uniform sampler2D tex1; // depth

in vec2 v_texCoord0;

uniform vec4 color;
uniform float start;
uniform float end;

out vec4 o_color;

void main() {
    vec4 original = texture(tex0, v_texCoord0);
    float depth = -texture(tex1, v_texCoord0).z;
    float f = clamp((depth-start) / (end-start), 0.0, 1.0);
    o_color = mix(original, color, f);
}