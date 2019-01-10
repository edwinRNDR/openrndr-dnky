#version 330

layout(location=0) out vec4 o_output;

uniform sampler2D image;
uniform sampler2D position;
in vec2 v_texCoord0;

uniform float minCoc;
uniform float maxCoc;
uniform float focalPlane;
uniform float aperture;
uniform float exposure;

uniform float aberrationConstant;
uniform float aberrationLinear;

uniform float aberrationBlendConstant;
uniform float aberrationBlendLinear;

uniform bool near;

float coc(vec2 uv) {
    float eyeZ = -texture(position, uv).z;
    float a = aperture;
    float f = 1.0 - focalPlane/eyeZ;
    float size = a * ( near? abs(f) : max(0.0, f) ) ;
    size = floor(clamp(size, minCoc, maxCoc ));
    return size;
}

void main() {
    vec2 step = 1.0 / textureSize(image, 0);


    float size = 0.0;
    float w = 0.0;
    for (int j = -1; j <= 1; ++j) {
        for (int i = -1; i <= 1; ++i) {
            size += coc(v_texCoord0 + step * vec2(i,j));
            w += 1.0;
        }
    }
    size = min(coc(v_texCoord0), size/w);
    float a = (size-minCoc) / (maxCoc-minCoc);
    float colorR = texture(image, v_texCoord0).r;
    float colorG = texture(image, v_texCoord0+ vec2(step.x, 0.0)*(aberrationLinear*a + aberrationConstant) ).g;
    float colorB = texture(image, v_texCoord0+ vec2(0.0, step.y)*(aberrationLinear*a + aberrationConstant) ).b;

    float f = clamp(aberrationBlendLinear * a + aberrationBlendConstant,0.0, 1.0);

    vec3 color = mix(texture(image, v_texCoord0).rgb, vec3(colorR, colorG, colorB), f);

    o_output = vec4(color*size, size);
}