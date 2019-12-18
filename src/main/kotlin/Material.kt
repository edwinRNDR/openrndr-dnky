package org.openrndr.dnky

import createLtcMagTexture
import createLtcMatTexture
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.*
import org.openrndr.math.Matrix44
import org.openrndr.math.Vector2
import org.openrndr.math.Vector3
import org.openrndr.math.Vector4
import org.openrndr.math.transforms.normalMatrix
import java.nio.ByteBuffer
import kotlin.math.cos

data class LightContext(val lights: List<NodeContent<Light>>,
                        val shadowMaps: Map<ShadowLight, RenderTarget>)


data class PostContext(val lightContext: LightContext, val inverseViewMatrix: Matrix44)

data class MaterialContext(val pass: RenderPass,
                           val lights: List<NodeContent<Light>>,
                           val fogs: List<NodeContent<Fog>>,
                           val shadowMaps: Map<ShadowLight, RenderTarget>,
                           val meshCubemaps: Map<Mesh, Cubemap>
)

interface Material {
    var doubleSided: Boolean
    fun generateShadeStyle(context: MaterialContext): ShadeStyle
    fun applyToShadeStyle(context: MaterialContext, shadeStyle: ShadeStyle, entity: Entity)
}


private val noise128 by lazy {
    val cb = colorBuffer(128, 128)
    val items = cb.width * cb.height * cb.format.componentCount
    val buffer = ByteBuffer.allocateDirect(items)
    for (y in 0 until cb.height) {
        for (x in 0 until cb.width) {
            for (i in 0 until 4)
                buffer.put((Math.random() * 255).toByte())
        }
    }
    buffer.rewind()
    cb.write(buffer)
    cb.generateMipmaps()
    cb.filter(MinifyingFilter.LINEAR_MIPMAP_LINEAR, MagnifyingFilter.LINEAR)
    cb.wrapU = WrapMode.REPEAT
    cb.wrapV = WrapMode.REPEAT
    cb
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

private fun AmbientLight.fs(index: Int): String = "f_diffuse += p_lightColor$index.rgb;"

private fun DirectionalLight.fs(index: Int) = """
|{
|    vec3 L = normalize(-p_lightDirection$index);
|    float attenuation = 1.0;
|    vec3 H = normalize(V + L);
|    float NoL = clamp(dot(N, L), 0.0, 1.0);
|    float LoH = clamp(dot(L, H), 0.0, 1.0);
|    float NoH = clamp(dot(N, H), 0.0, 1.0);
|    vec3 Lr = p_lightPosition$index - v_worldPosition;
//|    vec3 L = normalize(Lr);
|    ${shadows.fs(index)}
|    
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
|   f_diffuse += mix(p_lightDownColor$index.rgb, p_lightUpColor$index.rgb, f) * irr * ((1.0 - m_metalness) * m_color.rgb ) * m_ambientOcclusion;
|}
""".trimMargin()

private val AreaLight.Companion.ltcMat by lazy { createLtcMatTexture() }
private val AreaLight.Companion.ltcMag by lazy { createLtcMagTexture() }

private fun AreaLight.fs(index: Int): String = """
|{
|   float LUT_SIZE  = 32.0;
|   float LUT_SCALE = (LUT_SIZE - 1.0)/LUT_SIZE;
|   float LUT_BIAS  = 0.5/LUT_SIZE;
|   LTC_Rect rect;
|   vec3 up = cross(p_lightTangent$index, p_lightDirection$index);
|   LTC_initRect(rect, p_lightPosition$index*0.1, p_lightTangent$index, up, p_lightDirection$index, p_lightSize$index*0.1);
|   vec3 points[4];
|   LTC_initRectPoints(rect, points);
|   float theta = acos(dot(N, V));
|   vec2 uv = vec2(m_roughness, theta/(0.5*3.14151925));
|   uv = uv * LUT_SCALE + LUT_BIAS;
|    vec4 t = texture(p_ltcMat, uv);
|    mat3 minv = mat3(
|            vec3(  1,   0, t.y),
|            vec3(  0, t.z,   0),
|            vec3(t.w,   0, t.x)
|    );
|    float attenuation = 1.0;
|   vec3 Lr = p_lightPosition$index - v_worldPosition;
|   vec3 L = normalize(Lr);
|   float NoL = dot(N, L);
|   ${shadows.fs(index)}
|
|    vec3 diffColor = m_color*(1.0 - m_metalness);
|    vec3 specColor = mix(vec3(0.04, 0.04, 0.04), m_color, m_metalness);
|
|    vec3 specular = LTC_Evaluate(N, V, v_worldPosition*0.1, minv, points, false);
|    vec2 schlick = texture(p_ltcMag, uv).xy;
|    vec3 diffuse = LTC_Evaluate(N, V, v_worldPosition*0.1, mat3(1), points, false);
|    specular /= 2.0 * 3.141592654;
|    diffuse /= 2.0 * 3.141592654;
|    f_specular += (attenuation * specular * p_lightColor$index.rgb) * (specColor*schlick.x + (1.0-specColor)*schlick.y);
|    f_diffuse += attenuation *diffuse * p_lightColor$index.rgb * diffColor;
|}
""".trimIndent()


private fun SpotLight.fs(index: Int): String {
    val shadows = shadows
    return """
|{
|   vec3 Lr = p_lightPosition$index - v_worldPosition;
|   float distance = length(Lr);
|   float attenuation = 1.0 / (p_lightConstantAttenuation$index +
|   p_lightLinearAttenuation$index * distance + p_lightQuadraticAttenuation$index * distance * distance);
|   attenuation = 1.0;
|   vec3 L = normalize(Lr);

|   float NoL = clamp(dot(N, L), 0.0, 1.0);
|   float side = dot(L, N);
|   float hit = max(dot(-L, p_lightDirection$index), 0.0);
|   float falloff = clamp((hit - p_lightOuterCos$index) / (p_lightInnerCos$index - p_lightOuterCos$index), 0.0, 1.0);
|   attenuation *= falloff;
|   ${shadows.fs(index)}
|   {
|       vec3 H = normalize(V + L);
|       float LoH = clamp(dot(L, H), 0.0, 1.0);
|       float NoH = clamp(dot(N, H), 0.0, 1.0);
|       f_diffuse += NoL * (0.1+0.9*attenuation) * Fd_Burley(m_roughness * m_roughness, NoV, NoL, LoH) * p_lightColor$index.rgb * m_color.rgb ;
|       float Dg = D_GGX(m_roughness * m_roughness, NoH, H);
|       float Vs = V_SmithGGXCorrelated(m_roughness * m_roughness, NoV, NoL);
|       vec3 F = F_Schlick(m_color * (m_metalness) + 0.04 * (1.0-m_metalness), LoH);
|       vec3 Fr = (Dg * Vs) * F;
|       f_specular += NoL * attenuation * Fr * p_lightColor$index.rgb;
|   }
}
""".trimMargin()
}

private fun Fog.fs(index: Int): String = """
|{
|    float dz = min(1.0, -v_viewPosition.z/p_fogEnd$index);
|    f_fog = vec4(p_fogColor$index.rgb, dz);
|}
""".trimMargin()

sealed class TextureSource
object DummySource : TextureSource()
abstract class TextureFromColorBuffer(var texture: ColorBuffer, var textureFunction: TextureFunction) : TextureSource()

class TextureFromCode(val code: String) : TextureSource()

private fun TextureFromCode.fs(index: Int, target: TextureTarget) = """
|vec4 tex$index = vec4(0.0, 0.0, 0.0, 1.0);
|{
|vec4 texOut;
|$code;
|tex$index = texOut;
|}
"""

enum class TextureFunction(val function: (String, String) -> String) {
    TILING({ texture, uv -> "texture($texture, $uv)" }),
    NOT_TILING({ texture, uv -> "textureNoTile(p_textureNoise, $texture, x_noTileOffset, $uv)" })
}

/**
 * @param texture the texture to sample from
 * @param input input coordinates, default is "va_texCoord0.xy"
 * @param textureFunction the texture function to use, default is TextureFunction.TILING
 * @param pre the pre-fetch shader code to inject, can only adjust "x_texCoord"
 * @param post the post-fetch shader code to inject, can only adjust "x_texture"
 */
class ModelCoordinates(texture: ColorBuffer,
                       var input: String = "va_texCoord0.xy",
                       textureFunction: TextureFunction = TextureFunction.TILING,
                       var pre: String? = null,
                       var post: String? = null) : TextureFromColorBuffer(texture, textureFunction)

class Triplanar(texture: ColorBuffer,
                var scale: Double = 1.0,
                var offset: Vector3 = Vector3.ZERO,
                var sharpness: Double = 2.0,
                textureFunction: TextureFunction = TextureFunction.TILING,
                var pre: String? = null,
                var post: String? = null) : TextureFromColorBuffer(texture, textureFunction) {

    init {
        texture.filter(MinifyingFilter.LINEAR_MIPMAP_LINEAR, MagnifyingFilter.LINEAR)
        texture.wrapU = WrapMode.REPEAT
        texture.wrapV = WrapMode.REPEAT
    }
}

private fun ModelCoordinates.fs(index: Int) = """
|vec4 tex$index = vec4(0.0, 0.0, 0.0, 1.0); 
|{
|   vec2 x_texCoord = $input;
|   vec2 x_noTileOffset = vec2(0.0);
|   ${if (pre != null) "{ $pre } " else ""}
|   x_texture = ${textureFunction.function("p_texture$index", "x_texCoord")};
|   ${if (post != null) "{ $post } " else ""}
|   tex$index = x_texture;
|}
"""

private fun Triplanar.fs(index: Int, target: TextureTarget) = """
|vec4 tex$index = vec4(0.0, 0.0, 0.0, 1.0);
|{
|   vec3 x_normal = va_normal;
|   vec3 x_position = va_position;
|   float x_scale = p_textureTriplanarScale$index;
|   vec3 x_offset = p_textureTriplanarOffset$index;
|   vec2 x_noTileOffset = vec2(0.0);
|   ${if (pre != null) "{ $pre } " else ""}
|   vec3 n = normalize(x_normal);
|   vec3 an = abs(n);
|   vec2 uvY = x_position.xz * x_scale + x_offset.x;
|   vec2 uvX = x_position.zy * x_scale + x_offset.y;
|   vec2 uvZ = x_position.xy * x_scale + x_offset.z;
|   vec4 tY = ${textureFunction.function("p_texture$index", "uvY")};
|   vec4 tX = ${textureFunction.function("p_texture$index", "uvX")};
|   vec4 tZ = ${textureFunction.function("p_texture$index", "uvZ")};
|   vec3 weights = pow(an, vec3(p_textureTriplanarSharpness$index));
|   weights = weights / (weights.x + weights.y + weights.z);
|   tex$index = tX * weights.x + tY * weights.y + weights.z * tZ;
|   ${if (target == TextureTarget.NORMAL) """
    |   vec3 tnX = normalize( tX.xyz - vec3(0.5, 0.5, 0.0));
    |   vec3 tnY = normalize( tY.xyz - vec3(0.5, 0.5, 0.0));
    |   vec3 tnZ = normalize( tZ.xyz - vec3(0.5, 0.5, 0.0));
    |   vec3 nX = vec3(0.0, tnX.yx);
    |   vec3 nY = vec3(tnY.x, 0.0, tnY.y);
    |   vec3 nZ = vec3(tnZ.xy, 0.0);
    |   vec3 normal = normalize(nX * weights.x + nY * weights.y + nZ * weights.z + n);
    |   tex$index = vec4(normal, 0.0);
""".trimMargin() else ""}
|}
    ${if (post != null) """
        vec4 x_texture = tex$index;
        {
            $post
        }
        tex$index = x_texture;
    """.trimIndent() else ""}
""".trimMargin()

sealed class TextureTarget {
    object NONE : TextureTarget()
    object COLOR : TextureTarget()
    object ROUGNESS : TextureTarget()
    object METALNESS : TextureTarget()
    object EMISSION : TextureTarget()
    object NORMAL : TextureTarget()
    object AMBIENT_OCCLUSION : TextureTarget()
    class Height(var scale: Double = 1.0) : TextureTarget()
}

class Texture(var source: TextureSource,
              var target: TextureTarget) {
    fun copy(): Texture {
        val copied = Texture(source, target)
        return copied
    }
}

class BasicMaterial : Material {
    override var doubleSided: Boolean = false

    var environmentMap = false
    var color = ColorRGBa.WHITE
    var metalness = 0.5
    var roughness = 1.0
    var emission = ColorRGBa.BLACK

    var vertexTransform: String? = null
    var parameters = mutableMapOf<String, Any>()
    var textures = mutableListOf<Texture>()

    val shadeStyles = mutableMapOf<MaterialContext, ShadeStyle>()

    fun copy(): BasicMaterial {
        val copied = BasicMaterial()
        copied.environmentMap = environmentMap
        copied.color = color
        copied.metalness = metalness
        copied.roughness = roughness
        copied.emission = emission
        copied.vertexTransform = vertexTransform
        copied.parameters.putAll(parameters)
        copied.textures.addAll(textures.map { it.copy() })
        return copied
    }

    override fun generateShadeStyle(context: MaterialContext): ShadeStyle {
        val cached = shadeStyles.getOrPut(context) {
            val needLight = needLight(context)
            val needLTC = context.lights.any { it.content is AreaLight }
            val preambleFS = """
            vec3 m_color = p_color.rgb;
            float m_f0 = 0.5;
            float m_roughness = p_roughness;
            float m_metalness = p_metalness;
            float m_ambientOcclusion = 1.0;
            vec3 m_emission = p_emission.rgb;
            vec3 m_normal = vec3(0.0, 0.0, 1.0);
            float f_alpha = 1.0;
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
                        TextureTarget.COLOR -> "m_color.rgb *= pow(tex$index.rgb, vec3(2.2));"
                        TextureTarget.METALNESS -> "m_metalness = tex$index.r;"
                        TextureTarget.ROUGNESS -> "m_roughness = tex$index.r;"
                        TextureTarget.EMISSION -> "m_emission += tex$index.rgb;"
                        TextureTarget.NORMAL -> "f_worldNormal = normalize((v_modelNormalMatrix * vec4(tex$index.xyz,0.0)).xyz);"
                        TextureTarget.AMBIENT_OCCLUSION -> "m_ambientOcclusion *= tex$index.r;"
                        is TextureTarget.Height -> ""
                    }
                }).joinToString("\n")
            } else ""

            val displacers = textures.filter { it.target is TextureTarget.Height }

            val textureVS = if (displacers.isNotEmpty()) textures.mapIndexed { index, it ->
                if (it.target is TextureTarget.Height) {
                    when (val source = it.source) {
                        DummySource -> "vec4 tex$index = vec4(1.0);"
                        is ModelCoordinates -> source.fs(index)
                        is Triplanar -> source.fs(index, it.target)
                        is TextureFromCode -> source.fs(index, it.target)
                        else -> TODO()
                    } + """
                x_position += x_normal * tex$index.r * p_textureHeightScale$index;
            """.trimIndent()
                } else ""
            }.joinToString("\n") else ""

            val lights = context.lights
            val lightFS = if (needLight) """
        vec3 f_diffuse = vec3(0.0);
        vec3 f_specular = vec3(0.0);
        vec3 f_emission = m_emission;
        vec3 N = normalize(f_worldNormal);
        vec3 ep = (p_viewMatrixInverse * vec4(0.0, 0.0, 0.0, 1.0)).xyz;
        vec3 Vr = ep - v_worldPosition;
        vec3 V = normalize(Vr);
        float NoV = abs(dot(N, V)) + 1e-5;

        ${if (environmentMap && context.meshCubemaps.isNotEmpty()) """
           {
                vec2 dfg = PrefilteredDFG_Karis(m_roughness, NoV);
                vec3 sc = m_metalness * m_color.rgb + (1.0-m_metalness) * vec3(0.04);

                f_specular.rgb += sc * (texture(p_environmentMap, reflect(-V, normalize(f_worldNormal))).rgb * dfg.x + dfg.y) * m_ambientOcclusion;
            }
        """.trimIndent() else ""}

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
            val vs = (this@BasicMaterial.vertexTransform ?: "") + textureVS

            shadeStyle {
                vertexPreamble = """
|                    $shaderNoRepetitionVert
                """.trimIndent()
                fragmentPreamble = """
            |${if (needLTC) ltcShaders else ""}
            |$shaderLinePlaneIntersect
            |$shaderProjectOnPlane
            |$shaderSideOfPlane
            |$shaderGGX
            |$shaderVSM
            |$shaderNoRepetition
            """.trimMargin()
                this.suppressDefaultOutput = true
                this.vertexTransform = vs
                fragmentTransform = fs
                context.pass.combiners.map {
                    if (rt.colorBuffers.size <= 1) {
                        this.output(it.targetOutput, 0)
                    } else
                        this.output(it.targetOutput, rt.colorBufferIndex(it.targetOutput))
                }
            }
        }
        return cached
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

        parameters.forEach { (k, v) ->
            when (v) {
                is Double -> shadeStyle.parameter(k, v)
                is Int -> shadeStyle.parameter(k, v)
                is Vector2 -> shadeStyle.parameter(k, v)
                is Vector3 -> shadeStyle.parameter(k, v)
                is Vector4 -> shadeStyle.parameter(k, v)
                is BufferTexture -> shadeStyle.parameter(k, v)
                is ColorBuffer -> shadeStyle.parameter(k, v)
                else -> TODO("support ${v::class.java}")
            }
        }
        if (needLight(context)) {
            textures.forEachIndexed { index, texture ->
                when (val source = texture.source) {
                    is TextureFromColorBuffer -> {
                        shadeStyle.parameter("texture$index", source.texture)
                        if (source.textureFunction == TextureFunction.NOT_TILING) {
                            shadeStyle.parameter("textureNoise", noise128)
                        }
                    }
                }
                when (val source = texture.source) {
                    is Triplanar -> {
                        shadeStyle.parameter("textureTriplanarSharpness$index", source.sharpness)
                        shadeStyle.parameter("textureTriplanarScale$index", source.scale)
                        shadeStyle.parameter("textureTriplanarOffset$index", source.offset)
                    }
                }
                if (texture.target is TextureTarget.Height) {
                    val target = texture.target as TextureTarget.Height
                    shadeStyle.parameter("textureHeightScale$index", target.scale)
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
                        shadeStyle.parameter("lightInnerCos$index", cos(Math.toRadians(light.innerAngle)))
                        shadeStyle.parameter("lightOuterCos$index", cos(Math.toRadians(light.outerAngle)))

                        if (light.shadows is Shadows.MappedShadows) {
                            context.shadowMaps[light]?.let {
                                val look = light.view(node)
                                shadeStyle.parameter("lightTransform$index",
                                        light.projection(it) * look)

                                if (light.shadows is Shadows.DepthMappedShadows) {
                                    shadeStyle.parameter("lightShadowMap$index", it.depthBuffer ?: TODO())
                                }

                                if (light.shadows is Shadows.ColorMappedShadows) {
                                    shadeStyle.parameter("lightShadowMap$index", it.colorBuffer(0))
                                }
                            }
                        }
                    }
                    is DirectionalLight -> {
                        shadeStyle.parameter("lightPosition$index", (node.worldTransform * Vector4.UNIT_W).xyz)
                        shadeStyle.parameter("lightDirection$index", ((normalMatrix(node.worldTransform)) * light.direction).normalized)
                        if (light.shadows is Shadows.MappedShadows) {
                            context.shadowMaps[light]?.let {
                                val look = light.view(node)
                                shadeStyle.parameter("lightTransform$index",
                                        light.projection(it) * look)

                                if (light.shadows is Shadows.DepthMappedShadows) {
                                    shadeStyle.parameter("lightShadowMap$index", it.depthBuffer ?: TODO())
                                }

                                if (light.shadows is Shadows.ColorMappedShadows) {
                                    shadeStyle.parameter("lightShadowMap$index", it.colorBuffer(0))
                                }
                            }
                        }
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
                        shadeStyle.parameter("ltcMat", AreaLight.ltcMat)
                        shadeStyle.parameter("ltcMag", AreaLight.ltcMag)
                        shadeStyle.parameter("lightPosition$index", (node.worldTransform * Vector4.UNIT_W).xyz)
                        shadeStyle.parameter("lightDirection$index", ((normalMatrix(node.worldTransform)) * Vector3(0.0, 0.0, 1.0)).normalized)
                        shadeStyle.parameter("lightTangent$index", ((normalMatrix(node.worldTransform)) * Vector3(1.0, 0.0, 0.0)).normalized)
                        shadeStyle.parameter("lightSize$index", Vector2(light.width / 2.0, light.height / 2.0))

                        light.distanceField?.let {
                            shadeStyle.parameter("lightDistanceField$index", it)
                        }

                        if (light.shadows is Shadows.MappedShadows) {
                            context.shadowMaps[light]?.let {
                                val look = light.view(node)
                                shadeStyle.parameter("lightTransform$index",
                                        light.projection(it) * look)

                                if (light.shadows is Shadows.DepthMappedShadows) {
                                    shadeStyle.parameter("lightShadowMap$index", it.depthBuffer ?: TODO())
                                }

                                if (light.shadows is Shadows.ColorMappedShadows) {
                                    shadeStyle.parameter("lightShadowMap$index", it.colorBuffer(0))
                                }
                            }
                        }
                    }
                }
            }
            context.fogs.forEachIndexed { index, (node, fog) ->
                shadeStyle.parameter("fogColor$index", fog.color)
                shadeStyle.parameter("fogEnd$index", fog.end)
            }
        } else {
            textures.forEachIndexed { index, texture ->
                if (texture.target is TextureTarget.Height) {
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
                    val target = texture.target as TextureTarget.Height
                    shadeStyle.parameter("textureHeightScale$index", target.scale)
                }
            }
        }
    }
}

private inline fun <reified T : Material> MeshBase.materialInit(init: T.() -> Unit): T {
    val t: T = T::class.java.constructors[0].newInstance() as T
    t.init()
    material = t
    return t
}

fun material(init: BasicMaterial.() -> Unit): BasicMaterial {
    val m = BasicMaterial()
    m.init()
    return m
}

fun BasicMaterial.texture(init: Texture.() -> Unit): Texture {
    val texture = Texture(DummySource, target = TextureTarget.COLOR)
    texture.init()
    textures.add(texture)
    return texture
}

@Deprecated("deprecated")
fun MeshBase.basicMaterial(init: BasicMaterial.() -> Unit): BasicMaterial = materialInit(init)

@Deprecated("deprecated")
fun LineMesh.basicMaterial(init: BasicMaterial.() -> Unit): BasicMaterial {
    val t = BasicMaterial()
    t.init()
    material = t
    return t
}

fun MeshBase.material(init: BasicMaterial.() -> Unit): BasicMaterial = materialInit(init)

fun LineMesh.material(init: BasicMaterial.() -> Unit): BasicMaterial {
    val t = BasicMaterial()
    t.init()
    material = t
    return t
}



