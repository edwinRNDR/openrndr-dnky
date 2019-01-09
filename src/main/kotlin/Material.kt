package org.openrndr.dnky

import org.openrndr.color.ColorRGBa
import org.openrndr.draw.*
import org.openrndr.math.Vector2
import org.openrndr.math.Vector3
import org.openrndr.math.Vector4
import org.openrndr.math.transforms.normalMatrix

data class MaterialContext(val pass: RenderPass,
                           val lights: List<NodeContent<Light>>,
                           val fogs: List<NodeContent<Fog>>,
                           val shadowMaps: Map<ShadowLight, RenderTarget>,
                           val meshCubemaps: Map<Mesh, Cubemap>
)

interface Material {
    fun generateShadeStyle(context: MaterialContext): ShadeStyle
    fun applyToShadeStyle(context: MaterialContext, shadeStyle: ShadeStyle, entity: Entity)
}

private fun PointLight.fs(index: Int): String = """
|{
|   vec3 Lr = p_lightPosition$index - v_worldPosition;
|   float distance = length(Lr);
|   float attenuation = 1.0 / (p_lightConstantAttenuation$index +
|   p_lightLinearAttenuation$index * distance + p_lightQuadraticAttenuation$index * distance * distance);
|   vec3 L = normalize(Lr);
|
|   float side = dot(L, N) ;
|   f_diffuse += attenuation * max(0, side / 3.1415) * p_lightColor$index.rgb * m_color.rgb;
|   f_specular += attenuation * ggx(N, V, L, m_roughness, m_f0) * p_lightColor$index.rgb * m_color.rgb;
}
""".trimMargin()

private fun AmbientLight.fs(index: Int): String = "light += p_lightColor$index.rgb;"

private fun DirectionalLight.fs(index: Int) = """
|{
|    vec3 L = normalize(p_lightDirection$index);
|    vec3 H = normalize(V + L);
|    float NoL = clamp(dot(N, L), 0.0, 1.0);
|    float LoH = clamp(dot(L, H), 0.0, 1.0);
|    float NoH = clamp(dot(N, H), 0.0, 1.0);
|    f_diffuse += NoL * attenuation * Fd_Burley(m_roughness * m_roughness, NoV, NoL, LoH) * p_lightColor$index.rgb * m_color.rgb ;
|    float Dg = D_GGX(m_roughness * m_roughness, NoH, H);
|    float Vs = V_SmithGGXCorrelated(m_roughness * m_roughness, NoV, NoL);
|    vec3 F = F_Schlick(m_color * (m_metalness) + 0.04 * (1.0-m_metalness), LoH);
|    vec3 Fr = (Dg * Vs) * F;
|    f_specular += NoL * attenuation * Fr * p_lightColor$index.rgb;
|}
""".trimMargin()

private fun HemisphereLight.fs(index: Int): String = """
|{
|   float f = dot(N, p_lightDirection$index) * 0.5 + 0.5;
|   vec3 irr = ${irradianceMap?.let { "texture(p_lightIrradianceMap$index, N).rgb" } ?: "vec3(1.0)"};
|   f_diffuse += mix(p_lightDownColor$index.rgb, p_lightUpColor$index.rgb, f) * irr * m_color.rgb;
|}
""".trimMargin()

private fun AreaLight.fs(index: Int): String = """
|{
|   vec3 up = cross(p_lightTangent$index, p_lightDirection$index);
|   float width = p_lightSize$index.x;
|   float height = p_lightSize$index.y;
|   vec3 projection = projectOnPlane(v_worldPosition, p_lightPosition$index, p_lightDirection$index);
|   vec3 dir = projection - p_lightPosition$index;
|   vec2 diagonal = vec2(dot(dir,p_lightTangent$index),dot(dir,up));
|   vec2 nearest2D = vec2(clamp( diagonal.x,-width,width  ),clamp(  diagonal.y,-height,height));
            ${if(distanceField!=null) """
                vec2 dtc = nearest2D / (vec2(width, height) * 2.0) + vec2(0.5);
                vec3 ddir = texture(p_lightDistanceField$index, dtc).rgb;
                ddir.xy /= textureSize(p_lightDistanceField$index, 0);
                if (ddir.z < 0.5) {
                    nearest2D += ddir.xy * vec2(width, height);
                }
            """.trimIndent() else ""}
|   vec3 nearestPointInside = p_lightPosition$index + (p_lightTangent$index*nearest2D.x+up*nearest2D.y);
|   float dist = distance(v_worldPosition, nearestPointInside);
|   vec3 L = normalize(nearestPointInside - v_worldPosition);
|   float attenuation = 1.0 / (1.0 + dist*0.1);
|   float nDotL = dot(p_lightDirection$index, -L);
|   if (nDotL > 0.0 && sideOfPlane(v_worldPosition, p_lightPosition$index, p_lightDirection$index) == 1) {
|       vec3 R = reflect(V, N);
|       vec3 E = linePlaneIntersect(v_worldPosition ,R, p_lightPosition$index, p_lightDirection$index);
|       float specAngle = dot(R, p_lightDirection$index);
|       if (specAngle > 0.0) {
|           vec3 dirSpec = E - p_lightPosition$index;
|           vec2 dirSpec2D = vec2(dot(dirSpec, p_lightTangent$index), dot(dirSpec,up));
            float eps = 0.0;
|           vec2 nearestSpec2D = vec2(clamp( dirSpec2D.x,-width + eps,width-eps  ),clamp(  dirSpec2D.y,-height + eps ,height - eps));
            ${if(distanceField!=null) """
                vec2 tc = nearestSpec2D / (vec2(width, height) * 2.0) + vec2(0.5);
                vec3 dir = texture(p_lightDistanceField$index, tc).rgb;
                dir.xy /= textureSize(p_lightDistanceField$index, 0);
                if (dir.z == 0.0) {
                    nearestSpec2D += dir.xy * vec2(width, -height);
                }

            """.trimIndent() else ""}
|           vec3 nearestSpec3D = p_lightPosition$index + (p_lightTangent$index*nearestSpec2D.x+up*nearestSpec2D.y);
|           vec3 toLight = nearestSpec3D-v_worldPosition;
|           float sf = max(0.0, dot(-normalize(toLight), p_lightDirection$index));
|           float realDist = length(toLight);
|           float specDist = length(nearestSpec2D-dirSpec2D);
|           float specFactor = exp(-specDist) * exp(-realDist*0.05) * sf; //1.0-clamp(length(nearestSpec2D-dirSpec2D)* (sf*10.0 + 1.0),0.0,1.0);
            //specFactor =1.0-clamp(length(nearestSpec2D-dirSpec2D), 0.0, 1.0);
            f_specular += p_lightColor$index.rgb * specFactor * specAngle * m_color.rgb;
|       }
|   }
    f_diffuse += m_color.rgb * p_lightColor$index.rgb  * max(0.0, nDotL) * attenuation;
|}
""".trimIndent()


private fun SpotLight.fs(index: Int): String = """
|{
|   vec3 Lr = p_lightPosition$index - v_worldPosition;
|   float distance = length(Lr);
|   float attenuation = 1.0 / (p_lightConstantAttenuation$index +
|   p_lightLinearAttenuation$index * distance + p_lightQuadraticAttenuation$index * distance * distance);
|   attenuation = 1.0;
|   vec3 L = normalize(Lr);
|
|   float side = dot(L, N);
|   float hit = max(dot(-L, p_lightDirection$index), 0.0);
|   float falloff = clamp((hit - p_lightOuterCos$index) / (p_lightInnerCos$index - p_lightOuterCos$index), 0.0, 1.0);
|   attenuation *= falloff;
|   ${if (shadows) """
    |vec4 smc = (p_lightTransform$index * vec4(v_worldPosition,1.0));
    |vec3 lightProj = (smc.xyz/smc.w) * 0.5 + 0.5;
    |if (lightProj.x > 0.0 && lightProj.x < 1.0 && lightProj.y > 0 && lightProj.y < 1) {
    |   vec3 smz = texture(p_lightShadowMap$index, lightProj.xy).rgb;
    |   float currentDepth = lightProj.z;
    |   float closestDepth = smz.x;
    |   float shadow = (currentDepth - 0.005)  > closestDepth  ? 0.0 : 1.0;
    |   attenuation *= shadow;
    |}
""".trimMargin() else ""}
|   vec3 H = normalize(V + L);
|   float NoL = clamp(dot(N, L), 0.0, 1.0);
|   float LoH = clamp(dot(L, H), 0.0, 1.0);
|   float NoH = clamp(dot(N, H), 0.0, 1.0);
|   f_diffuse += NoL * attenuation * Fd_Burley(m_roughness * m_roughness, NoV, NoL, LoH) * p_lightColor$index.rgb * m_color.rgb ;
|   float Dg = D_GGX(m_roughness * m_roughness, NoH, H);
|   float Vs = V_SmithGGXCorrelated(m_roughness * m_roughness, NoV, NoL);
|   vec3 F = F_Schlick(m_color * (m_metalness) + 0.04 * (1.0-m_metalness), LoH);
|   vec3 Fr = (Dg * Vs) * F;
|   f_specular += NoL * attenuation * Fr * p_lightColor$index.rgb;
}
""".trimMargin()

private fun Fog.fs(index: Int): String = """
|{
    float dz = min(1.0, -v_viewPosition.z/p_fogEnd$index);
    f_fog = vec4(p_fogColor$index.rgb, dz);
//|    f_diffuse = mix(f_diffuse, (1.0/3.0) * p_fogColor$index.rgb, dz);
//|    f_specular = mix(f_specular, (1.0/3.0) * p_fogColor$index.rgb, dz);
//|    f_emission = mix(f_emissive, (1.0/3.0) * p_fogColor$index.rgb, dz);
|}
""".trimMargin()

sealed class TextureSource
object DummySource : TextureSource()
abstract class TextureFromColorBuffer(val texture: ColorBuffer) : TextureSource()

class TextureFromCode(val code:String) : TextureSource()
private fun TextureFromCode.fs(index: Int, target: TextureTarget) = """
|vec4 tex$index = vec4(0.0, 0.0, 0.0, 1.0);
|{
|vec4 texture;
|$code;
|tex$index = texture;
|}
"""


class ModelCoordinates(texture: ColorBuffer,
                       val input: String = "va_texCoord0.xy") : TextureFromColorBuffer(texture)

class Triplanar(texture: ColorBuffer,
                var scale: Double = 1.0,
                var offset: Vector3 = Vector3.ZERO,
                var sharpness: Double = 2.0) : TextureFromColorBuffer(texture) {

    init {
        texture.filter(MinifyingFilter.LINEAR_MIPMAP_LINEAR, MagnifyingFilter.LINEAR)
        texture.wrapU = WrapMode.REPEAT
        texture.wrapV = WrapMode.REPEAT
    }
}

private fun ModelCoordinates.fs(index: Int) = "vec4 tex$index = texture(p_texture$index, $input);"
private fun Triplanar.fs(index: Int, target: TextureTarget) = """
|vec4 tex$index = vec4(0.0, 0.0, 0.0, 1.0);
|{
|   vec3 n = normalize(va_normal);
|   vec3 an = abs(n);
|   vec2 uvY = va_position.xz * p_textureTriplanarScale$index + p_textureTriplanarOffset$index.x;
|   vec2 uvX = va_position.zy * p_textureTriplanarScale$index + p_textureTriplanarOffset$index.y;
|   vec2 uvZ = va_position.xy * p_textureTriplanarScale$index + p_textureTriplanarOffset$index.z;
|   vec4 tY = texture(p_texture$index, uvY);
|   vec4 tX = texture(p_texture$index, uvX);
|   vec4 tZ = texture(p_texture$index, uvZ);
|   vec3 weights = pow(an, vec3(p_textureTriplanarSharpness$index));
|   weights = weights / (weights.x + weights.y + weights.z);
|   tex$index = tX * weights.x + tY * weights.y + weights.z * tZ;
|   ${if(target == TextureTarget.NORMAL) """
    |   vec3 tnX = normalize( tX.xyz - vec3(0.5, 0.5, 0.0));
    |   vec3 tnY = normalize( tY.xyz - vec3(0.5, 0.5, 0.0));
    |   vec3 tnZ = normalize( tZ.xyz - vec3(0.5, 0.5, 0.0));
    |   vec3 nX = vec3(0.0, tnX.yx);
    |   vec3 nY = vec3(tnY.x, 0.0, tnY.y);
    |   vec3 nZ = vec3(tnZ.xy, 0.0);
    |   vec3 normal = normalize(nX * weights.x + nY * weights.y + nZ * weights.z + n);
    |   tex$index = vec4(normal, 0.0);
""".trimMargin() else "" }
|}
""".trimMargin()

enum class TextureTarget {
    NONE,
    COLOR,
    ROUGNESS,
    METALNESS,
    EMISSION,
    NORMAL,
}

class Texture(var source: TextureSource,
              var target: TextureTarget)

class BasicMaterial : Material {
    var environmentMap = false
    var color = ColorRGBa.WHITE
    var metalness = 0.5
    var roughness = 1.0
    var emission = 0.0

    var vertexTransform: String? = null
    var parameters = mutableMapOf<String, Any>()
    var textures = mutableListOf<Texture>()

    override fun generateShadeStyle(context: MaterialContext): ShadeStyle {
        val needLight = needLight(context)

        val preambleFS = """
            vec3 m_color = p_color.rgb;
            float m_f0 = 0.5;
            float m_roughness = p_roughness;
            float m_metalness = p_metalness;
            float m_emission = p_emission;
            vec3 m_normal = vec3(0.0, 0.0, 1.0);
            vec4 f_fog = vec4(0.0, 0.0, 0.0, 0.0);
            vec3 f_worldNormal = v_worldNormal;
        """.trimIndent()

        val textureFs = if (needLight) {
            (textures.mapIndexed { index, it ->
                when (val source = it.source) {
                    DummySource -> "vec4 tex$index = vec4(1.0);"
                    is ModelCoordinates -> source.fs(index)
                    is Triplanar -> source.fs(index, it.target)
                    is TextureFromCode -> source.fs(index, it.target)
                    else -> TODO()
                }
            } + textures.mapIndexed { index, texture ->
                when (texture.target) {
                    TextureTarget.NONE -> ""
                    TextureTarget.COLOR -> "m_color.rgb *= tex$index.rgb;"
                    TextureTarget.METALNESS -> "m_metalness = tex$index.r;"
                    TextureTarget.ROUGNESS -> "m_roughness = tex$index.r;"
                    TextureTarget.EMISSION -> "m_emission += tex$index.r;"
                    TextureTarget.NORMAL -> "f_worldNormal = normalize((u_modelNormalMatrix * vec4(tex$index.xyz,0.0)).xyz); "
                }
            }).joinToString("\n")
        } else ""

        val lights = context.lights
        val lightFS = if (needLight) """
        vec3 f_diffuse = vec3(0.0);
        vec3 f_specular = vec3(0.0);
        float f_emission = m_emission;
        vec3 N = normalize(f_worldNormal);
        vec3 ep = (p_viewMatrixInverse * vec4(0.0, 0.0, 0.0, 1.0)).xyz;
        vec3 Vr = ep - v_worldPosition;
        vec3 V = normalize(Vr);
        float NoV = abs(dot(N, V)) + 1e-5;

        ${if(environmentMap && context.meshCubemaps.isNotEmpty()) """
            /*
            float fresnelBias = 0.1;
            float fresnelScale = 0.5;
            float fresnelPower = 0.3;
            float reflectivity = fresnelBias + fresnelScale * pow(1.0 + dot(-V, f_worldNormal), fresnelPower);
            */

            {
                vec2 dfg = PrefilteredDFG_Karis(m_roughness, NoV);
                vec3 sc = m_metalness * m_color.rgb + (1.0-m_metalness) * vec3(0.04);

                f_specular.rgb += sc * (texture(p_environmentMap, reflect(-V, normalize(f_worldNormal))).rgb * dfg.x + dfg.y);
            }
        """.trimIndent()  else ""  }

        ${lights.mapIndexed { index, (node, light) ->
            when (light) {
                is AmbientLight -> light.fs(index)
                is PointLight -> light.fs(index)
                is SpotLight -> light.fs(index)
                is DirectionalLight -> light.fs(index)
                is HemisphereLight -> light.fs(index)
                is AreaLight -> light.fs(index)
                else -> TODO()
            }
        }.joinToString("\n")}

        ${context.fogs.mapIndexed { index, (node, fog) ->
            fog.fs(index)
        }.joinToString("\n")}



    """.trimIndent() else ""

        val rt = RenderTarget.active

        val combinerFS = context.pass.combiners.map {
            it.generateShader()
        }.joinToString("\n")

        val fs = preambleFS + textureFs + lightFS + combinerFS

        return shadeStyle {
            fragmentPreamble = """
            |$shaderLinePlaneIntersect
            |$shaderProjectOnPlane
            |$shaderSideOfPlane
            |$shaderGGX
            """.trimMargin()
            this.suppressDefaultOutput = true
            this.vertexTransform = this@BasicMaterial.vertexTransform
            fragmentTransform = fs
            context.pass.combiners.map {
                if (rt.colorBuffers.size <= 1) {
                    this.output(it.targetOutput, 0)
                } else
                    this.output(it.targetOutput, rt.colorBufferIndex(it.targetOutput))
            }
        }
    }

    private fun needLight(context: MaterialContext): Boolean {
        val needSpecular = context.pass.combiners.any { FacetType.SPECULAR in it.facets }
        val needDiffuse = context.pass.combiners.any { FacetType.DIFFUSE in it.facets }
        val needLight = needSpecular || needDiffuse
        return needLight
    }

    override fun applyToShadeStyle(context: MaterialContext, shadeStyle: ShadeStyle, entity: Entity) {

        if (entity is Mesh) {
            if (environmentMap) {
                context.meshCubemaps[entity]?.let {
                    shadeStyle.parameter("environmentMap", it)
                }
            }
        }

        shadeStyle.parameter("emission", emission)
        shadeStyle.parameter("color", color)
        shadeStyle.parameter("metalness", metalness)
        shadeStyle.parameter("roughness", roughness)

        parameters.forEach { k, v ->
            when (v) {
                is Double -> shadeStyle.parameter(k, v)
                is Int -> shadeStyle.parameter(k, v)
                is Vector2 -> shadeStyle.parameter(k, v)
                is Vector3 -> shadeStyle.parameter(k, v)
                is Vector4 -> shadeStyle.parameter(k, v)
                is BufferTexture -> shadeStyle.parameter(k, v)
                else -> TODO("support ${v::class.java}")
            }
        }
        if (needLight(context)) {
            textures.forEachIndexed { index, texture ->
                when (val source = texture.source) {
                    is TextureFromColorBuffer -> shadeStyle.parameter("texture$index", source.texture)
                }
                when (val source = texture.source) {
                    is Triplanar -> {
                        shadeStyle.parameter("textureTriplanarSharpness$index", source.sharpness)
                        shadeStyle.parameter("textureTriplanarScale$index", source.scale)
                        shadeStyle.parameter("textureTriplanarOffset$index", source.offset)
                    }

                }
            }

            val lights = context.lights
            lights.forEachIndexed { index, (node, light) ->
                shadeStyle.parameter("lightColor$index", light.color)
                when (light) {
                    is AmbientLight -> {
                    }

                    is PointLight -> {
                        shadeStyle.parameter("lightPosition$index", (node.worldTransform * Vector4.UNIT_W).xyz)
                        shadeStyle.parameter("lightConstantAttenuation$index", light.constantAttenuation)
                        shadeStyle.parameter("lightLinearAttenuation$index", light.linearAttenuation)
                        shadeStyle.parameter("lightQuadraticAttenuation$index", light.quadraticAttenuation)
                    }

                    is SpotLight -> {
                        shadeStyle.parameter("lightPosition$index", (node.worldTransform * Vector4.UNIT_W).xyz)
                        shadeStyle.parameter("lightDirection$index", ((normalMatrix(node.worldTransform)) * light.direction).normalized)
                        shadeStyle.parameter("lightConstantAttenuation$index", light.constantAttenuation)
                        shadeStyle.parameter("lightLinearAttenuation$index", light.linearAttenuation)
                        shadeStyle.parameter("lightQuadraticAttenuation$index", light.quadraticAttenuation)
                        shadeStyle.parameter("lightInnerCos$index", Math.cos(Math.toRadians(light.innerAngle)))
                        shadeStyle.parameter("lightOuterCos$index", Math.cos(Math.toRadians(light.outerAngle)))

                        if (light.shadows) {
                            context.shadowMaps[light]?.let {
                                val look = light.view(node)
                                shadeStyle.parameter("lightTransform$index",
                                        light.projection(it) * look)
                                shadeStyle.parameter("lightShadowMap$index", it.depthBuffer ?: TODO())
                            }
                        }
                    }

                    is DirectionalLight -> {
                        shadeStyle.parameter("lightDirection$index", ((normalMatrix(node.worldTransform)) * light.direction).normalized)
                    }

                    is HemisphereLight -> {
                        shadeStyle.parameter("lightDirection$index", ((normalMatrix(node.worldTransform)) * light.direction).normalized)
                        shadeStyle.parameter("lightUpColor$index", light.upColor)
                        shadeStyle.parameter("lightDownColor$index", light.downColor)

                        light.irradianceMap?.let {
                            shadeStyle.parameter("lightIrradianceMap$index", it)
                        }
                    }

                    is AreaLight -> {
                        shadeStyle.parameter("lightPosition$index", (node.worldTransform * Vector4.UNIT_W).xyz)
                        shadeStyle.parameter("lightDirection$index", ((normalMatrix(node.worldTransform)) * Vector3(0.0, 0.0, 1.0)).normalized)
                        shadeStyle.parameter("lightTangent$index", ((normalMatrix(node.worldTransform)) * Vector3(1.0, 0.0, 0.0)).normalized)
                        shadeStyle.parameter("lightSize$index", Vector2(light.width/2.0, light.height/2.0))

                        light.distanceField?.let {
                            shadeStyle.parameter("lightDistanceField$index", it)
                        }

                    }
                }
            }
            context.fogs.forEachIndexed { index, (node, fog) ->
                shadeStyle.parameter("fogColor$index", fog.color)
                shadeStyle.parameter("fogEnd$index", fog.end)
            }
        }
    }
}

private inline fun <reified T : Material> MeshBase.material(init: T.() -> Unit): T {
    val t: T = T::class.java.constructors[0].newInstance() as T
    t.init()
    material = t
    return t
}

fun BasicMaterial.texture(init: Texture.() -> Unit): Texture {
    val texture = Texture(DummySource, target = TextureTarget.COLOR)
    texture.init()
    textures.add(texture)
    return texture
}

fun MeshBase.basicMaterial(init: BasicMaterial.() -> Unit): BasicMaterial = material(init)