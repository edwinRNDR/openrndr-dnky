#version 330
// --- varyings ---
in vec2 v_texCoord0;

// --- G buffer ---
uniform sampler2D colors;
uniform sampler2D reflections;
uniform sampler2D positions;
uniform sampler2D normals;
uniform sampler2D materials;
uniform sampler2D baseColors;

// --- transforms ---
uniform mat4 projection;

// --- output ---
layout(location = 0) out vec4 o_color;

vec2 PrefilteredDFG_Karis(float roughness, float NoV) {
    //https://www.shadertoy.com/view/XlKSDR
    // Karis 2014, "Physically Based Material on Mobile"
    const vec4 c0 = vec4(-1.0, -0.0275, -0.572,  0.022);
    const vec4 c1 = vec4( 1.0,  0.0425,  1.040, -0.040);

    vec4 r = roughness * c0 + c1;
    float a004 = min(r.x * r.x, exp2(-9.28 * NoV)) * r.x + r.y;

    return vec2(-1.04, 1.04) * a004 + r.zw;
}



void main() {

    vec4 material = texture(materials, v_texCoord0);
    vec3 position = texture(positions, v_texCoord0).xyz;
    vec3 normal = texture(normals, v_texCoord0).xyz;
    vec3 baseColor = texture(baseColors, v_texCoord0).rgb;
    vec3 reflection = texture(reflections, v_texCoord0).rgb;
    vec3 color = texture(colors, v_texCoord0).rgb;

    float roughness = material.b;
    float metalness = material.r;
    vec2 step = 1.0 / textureSize(reflections, 0);
    int w = int(roughness*roughness * 8);
    float weight = 0.0;
    vec3 sum = vec3(0.0);
    for (int j = -w; j <= w; ++j) {
        for (int i = -w; i <= w; ++i) {

            sum += texture(reflections, v_texCoord0 + step* vec2(i,j)).rgb;
            weight += 1.0;

        }
    }
    sum /= weight;

    vec3 sc = (metalness) * baseColor.rgb + (1.0-metalness) * vec3(0.08);


    vec2 dfg = PrefilteredDFG_Karis(roughness, dot(normalize(normal), normalize(-position)));
    o_color.rgb = color + sc * (sum * dfg.x + sum * dfg.y);
    o_color.a = 1.0;
}