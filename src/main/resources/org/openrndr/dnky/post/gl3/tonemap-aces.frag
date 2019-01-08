// https://www.shadertoy.com/view/XlKSDR
// Narkowicz 2015, "ACES Filmic Tone Mapping Curve"

#version 330

float a = 2.51;
float b = 0.03;
float c = 2.43;
float d = 0.59;
float e = 0.14;

in vec2 v_texCoord0;
uniform sampler2D tex0;
uniform float exposureBias;
out vec4 o_color;


void main()
{
   vec3 x = texture(tex0, v_texCoord0).rgb * exposureBias;
   vec3 mapped = (x * (a * x + b)) / (x * (c * x + d) + e);

   vec3 srgb = pow(mapped, vec3(1/2.2));
   o_color = vec4(srgb, 1);
}