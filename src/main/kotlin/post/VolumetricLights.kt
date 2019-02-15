package post

import org.openrndr.dnky.*
import org.openrndr.draw.ColorBuffer
import org.openrndr.draw.Filter
import org.openrndr.draw.filterShaderFromCode
import org.openrndr.math.Matrix44
import org.openrndr.math.Vector3
import org.openrndr.math.Vector4
import org.openrndr.math.transforms.normalMatrix

private fun generateShader(context: LightContext): String {
    return """
|#version 330
|out vec4 o_color;
|uniform sampler2D tex0;
|uniform sampler2D tex1;
|in vec2 v_texCoord0;
|uniform vec3 p_cameraPosition;
${context.lights.mapIndexed { index, it ->
        """
            uniform float p_lightOuterCos$index;
            uniform float p_lightInnerCos$index;
            uniform vec3 p_lightPosition$index;
            uniform vec3 p_lightDirection$index;
            uniform mat4 p_lightTransform$index;
            uniform vec4 p_lightColor$index;
            uniform sampler2D p_lightShadowMap$index;
        """.trimIndent()
    }.joinToString("\n")}
|uniform mat4 u_inverseViewMatrix;
|float chebyshevUpperBound(vec2 moments, float t, float minVariance) {
|   // One-tailed inequality valid if t > Moments.x
|   float p = (t <= moments.x) ? 1.0 : 0.0;
|   // Compute variance.
|   float variance = moments.y - (moments.x * moments.x);
|   variance = max(variance, minVariance);
|   // Compute probabilistic upper bound.
|   float d = t - moments.x;
|   float p_max = variance / (variance + d*d);
|   p_max = smoothstep(0.6, 1, p_max);
|   return max(p, p_max);
}
|float scatter(float lightDotView)
|{
|float G_SCATTERING = 0.9;
|float result = 1.0f - G_SCATTERING * G_SCATTERING;
|result /= (4.0f * 3.1415926535 * pow(1.0f + G_SCATTERING * G_SCATTERING - (2.0f * G_SCATTERING) *      lightDotView, 1.5f));
|return result;
|}
|struct Ray
|{
|    vec3 o;		// origin
|    vec3 d;		// direction
|};
|
|struct Hit
|{
|    float t0;	// solution to p=o+t*d
|    float t1;
|};
|struct Cone
|{
|	float cosa;	// half cone angle
|   float h;	// height
|   vec3 c;		// tip position
|   vec3 v;		// axis
|};
|
|const Hit noHit = Hit(100000, 100000);
|Hit intersectCone(Cone s, Ray r)
|{
|    vec3 co = r.o - s.c;
|
|    float a = dot(r.d,s.v)*dot(r.d,s.v) - s.cosa*s.cosa;
|    float b = 2. * (dot(r.d,s.v)*dot(co,s.v) - dot(r.d,co)*s.cosa*s.cosa);
|    float c = dot(co,s.v)*dot(co,s.v) - dot(co,co)*s.cosa*s.cosa;
|
|    float det = b*b - 4.*a*c;
|    if (det < 0.) return noHit;
|
|    det = sqrt(det);
|    float t1 = (-b - det) / (2. * a);
|    float t2 = (-b + det) / (2. * a);
|
|    // This is a bit messy; there ought to be a more elegant solution.
|    float t = t1;
|    if (t < 0. || t2 > 0. && t2 < t) t = t2;
|    if (t < 0.) return noHit;
|
|    vec3 cp1 = r.o + t1 * r.d - s.c;
|    vec3 cp2 = r.o + t2 * r.d - s.c;
|    vec3 cpt = r.o + t * r.d - s.c;
|    float h = dot(cpt, s.v);
|    if (h < 0. || h > s.h) return noHit;
|
|
|
|    return Hit(t1, t2);
|}
|vec3 accumulateVSM(vec3 from, vec3 to, int steps000, vec3 lightPosition, mat4 lightMatrix, sampler2D lightMap, vec3 lightColor) {
|   vec3 ray = to - from;
|   vec3 rayDirection = normalize(ray);
|   vec3 rayStep = rayDirection;
|   vec3 position = from;
|   vec3 acc = vec3(0.0);;
|   float scattering = 0.1;
|   float scatteringConst = 1.0 - scattering*scattering;
|   float rayLength = length(ray);
|
|   vec4 fromW = (lightMatrix * vec4(from,1.0));
|   vec3 fromProj = (fromW.xyz/fromW.w) * 0.5 + 0.5;
|   vec4 toW = (lightMatrix * vec4(to,1.0));
|   vec3 toProj = (toW.xyz/toW.w) * 0.5 + 0.5;
|   //if (fromW.z < 0 && toW.z < 0) return vec3(0.0);
|   vec4 lightW = fromW;
|   vec4 lightStep = (toW-fromW) / rayLength;
|
|   for (int stepc = 0; stepc < min(512, rayLength); ++stepc) {
|       float VoL = max(0.0, dot(rayDirection, normalize(lightPosition - position)));
|       vec4 smc = (lightMatrix * vec4(position,1.0));
|       //vec3 lightProj = (lightW.xyz/lightW.w) * 0.5 + 0.5;
|       vec3 lightProj = (smc.xyz/smc.w) * 0.5 + 0.5;
|       float attenuation = 1.0;
|       vec3 Lr = lightPosition - position;
|       vec3 L = normalize(Lr);
|       if (lightProj.x > 0.0 && lightProj.x < 1.0 && lightProj.y > 0 && lightProj.y < 1) {
|           vec2 moments = texture(lightMap, lightProj.xy).xy;
|           attenuation *= (chebyshevUpperBound(moments, length(Lr), 50.0));
|           //attenuation *= attenuation;
|           //if (attenuation < 0.5)
|           //acc += vec3(1.0, 0.0, 0.0);
|       }
|       float hit = max(dot(-L, p_lightDirection0), 0.0);
|       float falloff = clamp((hit - p_lightOuterCos0) / (p_lightInnerCos0 - p_lightOuterCos0), 0.0, 1.0);
|       falloff = step(p_lightOuterCos0, hit);
|       //attenuation *= 1.0/(0.04+length(Lr)*length(Lr));
|       attenuation *= falloff;
|
|       //acc += vec3(1.0/64.0) * lightColor * attenuation ;//(scatteringConst /  (4.0f * 3.141592 * pow(1.0 + scattering * scattering - (2.0 * scattering) * VoL, 1.5))) * lightColor;
|       acc += 0.1*scatter(VoL) * lightColor * attenuation;
|       position += rayStep*0.25;
|       lightW += lightStep*0.25;
|   }
|   return acc;
|}
|
|void main() {
|   vec3 acc = vec3(0.0);
|   ${context.lights.mapIndexed { index, it ->

        if (it.content is ShadowLight && it.content is SpotLight) {
            if (it.content.shadows is Shadows.VSM) {
                """ {
                    Cone cone = Cone(p_lightOuterCos$index, 1500.0, p_lightPosition$index, p_lightDirection$index);

                    vec3 viewPosition = texture(tex1, v_texCoord0).xyz;
                    vec3 worldPosition = (u_inverseViewMatrix * vec4(viewPosition, 1.0)).xyz;
                    vec3 origin = (u_inverseViewMatrix * vec4(0.0, 0.0, 0.0, 1.0)).xyz;

                    acc += accumulateVSM(origin, worldPosition,32, p_lightPosition$index, p_lightTransform$index, p_lightShadowMap$index, p_lightColor$index.rgb);
                    }
                """.trimIndent()
            } else {
                null
            }
        } else {
            null
        }

    }.filterNotNull().joinToString("\n")}
|   o_color.rgb =  acc+ texture(tex0, v_texCoord0).rgb;
|   o_color.a = 1.0;
|}

""".trimMargin()
}

private val volumetricFilters = mutableMapOf<String, Filter>()
fun generateVolumetricLights(context: LightContext): Filter {
    val shader = generateShader(context)
    val filter = volumetricFilters.getOrPut(shader) { Filter(filterShaderFromCode(shader)) }

    context.lights.forEachIndexed { index, content ->
        val light = content.content
        (light as? ShadowLight)?.let {

            if (light is SpotLight) {
                filter.parameters["p_lightPosition$index"] = (content.node.worldTransform * Vector4.UNIT_W).xyz
                filter.parameters["p_lightDirection$index"]= ((normalMatrix(content.node.worldTransform)) * light.direction).normalized
                filter.parameters["p_lightConstantAttenuation$index"]= light.constantAttenuation
                filter.parameters["p_lightLinearAttenuation$index"]= light.linearAttenuation
                filter.parameters["p_lightQuadraticAttenuation$index"]= light.quadraticAttenuation
                filter.parameters["p_lightInnerCos$index"]= Math.cos(Math.toRadians(light.innerAngle))
                filter.parameters["p_lightOuterCos$index"]= Math.cos(Math.toRadians(light.outerAngle))

                context.shadowMaps[light]?.let { rt ->
                    val look = light.view(content.node)
                    filter.parameters["p_lightTransform$index"] = light.projection(rt) * look
                    if (light.shadows is Shadows.DepthMappedShadows) {
                        filter.parameters["p_lightShadowMap$index"] = rt.depthBuffer ?: TODO()
                    }
                    if (light.shadows is Shadows.ColorMappedShadows) {
                        println("setting shadow map $index")
                        filter.parameters["p_lightShadowMap$index"] = rt.colorBuffer(0)
                    }
                }
            }
        }
    }

    return filter
}

class VolumetricLights : Filter() {

    private var proxy: Filter? = null
    var context: LightContext? = null
        set(value) {
            proxy = if (value != null) {
                generateVolumetricLights(value)
            } else {
                null
            }
            field = value
    }

    var inverseViewMatrix: Matrix44 = Matrix44.IDENTITY

    override fun apply(source: Array<ColorBuffer>, target: Array<ColorBuffer>) {

        proxy?.let {
            println((inverseViewMatrix * Vector4.UNIT_W).xyz)
            it.parameters["p_cameraPosition"] = (inverseViewMatrix * Vector4.UNIT_W).xyz
            it.parameters["u_inverseViewMatrix"] = inverseViewMatrix
            context?.lights?.forEachIndexed { index, light ->
                it.parameters["p_lightPosition$index"] = (light.node.worldTransform * Vector4.UNIT_W).xyz

                when(val l = light.content) {
                    is SpotLight -> {
                        it.parameters["p_lightDirection$index"] = (((normalMatrix(light.node.worldTransform)) * l.direction).normalized)
                        it.parameters["p_lightInnerCos$index"] = Math.cos(Math.toRadians(l.innerAngle))
                        it.parameters["p_lightOuterCos$index"] = Math.cos(Math.toRadians(l.outerAngle))
                    }
                        else ->
                        it.parameters["p_lightDirection$index"] = (((normalMatrix(light.node.worldTransform)) * Vector3.UNIT_Z).normalized)
                }
//                when (val l = light.content) {
//                    is ShadowLight ->
//                        //it.parameters["p_lightTransform$index"] = l.projection( )l.view(light.node)
//                }
                it.parameters["p_lightColor$index"] = light.content.color
            }
        }

        proxy?.apply(source, target)

    }
}