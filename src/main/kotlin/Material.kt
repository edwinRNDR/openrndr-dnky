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
|   vec3 dp = p_lightPosition$index - v_worldPosition;
|   float distance = length(dp);
|   float attenuation = 1.0 / (p_lightConstantAttenuation$index +
|   p_lightLinearAttenuation$index * distance + p_lightQuadraticAttenuation$index * distance * distance);
|   vec3 dpn = normalize(dp);
|
|   float side = dot(dpn, wnn);
|   f_diffuse += attenuation * max(0, side) * p_lightColor$index.rgb * p_diffuse.rgb;
|   if (side > 0.0) {
|       float f = max(0.0, dot(reflect(-dpn, wnn), edn));
|       f_specular += attenuation * pow(f, m_shininess) * p_lightColor$index.rgb * m_specular.rgb;
|   }
}
""".trimMargin()

private fun AmbientLight.fs(index: Int): String = "light += p_lightColor$index.rgb;"

private fun DirectionalLight.fs(index: Int) = """
|{
|    float side = dot(-wnn, p_lightDirection$index);
|    f_diffuse += max(0, side) * p_lightColor$index.rgb * m_diffuse.rgb;
|    if (side > 0.0) {
|        float f = max(0.0, dot(reflect(p_lightDirection$index, wnn), edn));
|        f_specular += pow(f, m_shininess) * p_lightColor$index.rgb * m_specular.rgb;
|    }
|}
""".trimMargin()

private fun HemisphereLight.fs(index: Int): String = """
|{
|   float f = dot(wnn, p_lightDirection$index) * 0.5 + 0.5;
|   vec3 irr = ${irradianceMap?.let { "texture(p_lightIrradianceMap$index, wnn).rgb" } ?: "vec3(1.0)"};
|   f_diffuse += mix(p_lightDownColor$index.rgb, p_lightUpColor$index.rgb, f) * irr * m_diffuse.rgb;
|}
""".trimMargin()

private fun SpotLight.fs(index: Int): String = """
|{
|   vec3 dp = p_lightPosition$index - v_worldPosition;
|   float distance = length(dp);
|   float attenuation = 1.0 / (p_lightConstantAttenuation$index +
|   p_lightLinearAttenuation$index * distance + p_lightQuadraticAttenuation$index * distance * distance);
|   attenuation = 1.0;
|   vec3 dpn = normalize(dp);
|
|   float side = dot(dpn, wnn);
|   float hit = max(dot(-dpn, p_lightDirection$index), 0.0);
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
    |
    |}
""".trimMargin() else ""}
|   f_diffuse += attenuation * max(0, side) * p_lightColor$index.rgb * p_diffuse.rgb;
|   if (side > 0.0) {
|       float f = max(0.0, dot(reflect(-dpn, wnn), edn));
|       f_specular += attenuation * pow(f, m_shininess) * p_lightColor$index.rgb * m_specular.rgb;
|   }
}
""".trimMargin()

private fun Fog.fs(index: Int): String = """
|{
|    float dz = min(1.0, -v_viewPosition.z/p_fogEnd$index);
|    f_diffuse = mix(f_diffuse, (1.0/3.0) * p_fogColor$index.rgb, dz);
|    f_specular = mix(f_specular, (1.0/3.0) * p_fogColor$index.rgb, dz);
|    f_emissive = mix(f_emissive, (1.0/3.0) * p_fogColor$index.rgb, dz);
|}
""".trimMargin()

sealed class TextureSource
object DummySource : TextureSource()
abstract class TextureFromColorBuffer(val texture: ColorBuffer) : TextureSource()

class ModelCoordinates(texture: ColorBuffer,
                       val input: String = "v_texCoord0.xy") : TextureFromColorBuffer(texture)

class Triplanar(texture: ColorBuffer,
                var scale: Double = 1.0,
                var sharpness: Double = 2.0) : TextureFromColorBuffer(texture)

private fun ModelCoordinates.fs(index: Int) = "vec4 tex$index = texture(p_texture$index ,$input);"
private fun Triplanar.fs(index: Int, target: TextureTarget) = """
|vec4 tex$index = vec4(0.0, 0.0, 0.0, 1.0);
|{
|   vec3 n = normalize(va_normal);
|   vec3 an = abs(n);
|   vec2 uvY = va_position.xz * p_textureTriplanarScale$index;
|   vec2 uvX = va_position.zy * p_textureTriplanarScale$index;
|   vec2 uvZ = va_position.xy * p_textureTriplanarScale$index;
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
    DIFFUSE,
    SPECULAR,
    DIFFUSE_SPECULAR,
    EMISSIVE,
    NORMAL,
    SHININESS,
}

class Texture(var source: TextureSource,
              var target: TextureTarget)

class BasicMaterial : Material {
    var environmentMap = false
    var diffuse = ColorRGBa.WHITE
    var specular = ColorRGBa.WHITE
    var emissive = ColorRGBa.BLACK
    var shininess = 1.0
    var vertexTransform: String? = null
    var fragmentTransform: String? = null
    var parameters = mutableMapOf<String, Any>()
    var textures = mutableListOf<Texture>()

    override fun generateShadeStyle(context: MaterialContext): ShadeStyle {
        val needLight = needLight(context)


        val preambleFS = """
            vec3 m_diffuse = p_diffuse.rgb;
            vec3 m_specular = p_specular.rgb;
            float m_shininess = p_shininess;
            vec3 m_emissive = p_emissive.rgb;
            vec3 m_normal = vec3(0.0, 0.0, 1.0);

            vec3 f_worldNormal = v_worldNormal;
        """.trimIndent()

        val textureFs = if (needLight) {
            (textures.mapIndexed { index, it ->
                when (val source = it.source) {
                    DummySource -> "vec4 tex$index = vec4(1.0);"
                    is ModelCoordinates -> source.fs(index)
                    is Triplanar -> source.fs(index, it.target)
                    else -> TODO()
                }
            } + textures.mapIndexed { index, texture ->
                when (texture.target) {
                    TextureTarget.NONE -> ""
                    TextureTarget.DIFFUSE -> "m_diffuse.rgb *= tex$index.rgb;"
                    TextureTarget.DIFFUSE_SPECULAR -> "m_diffuse.rgb *= tex$index.rgb; m_specular.rgb *= tex$index.rgb;"
                    TextureTarget.SPECULAR -> "m_specular.rgb *= tex$index.rgb;"
                    TextureTarget.EMISSIVE -> "m_emissive.rgb += tex$index.rgb;"
                    TextureTarget.NORMAL -> "f_worldNormal = normalize((u_modelNormalMatrix * vec4(tex$index.xyz,0.0)).xyz); "
                    TextureTarget.SHININESS -> "m_shininess *= tex$index.r;"
                }
            }).joinToString("\n")
        } else ""

        val lights = context.lights
        val lightFS = if (needLight) """
        vec3 f_diffuse = vec3(0.0);
        vec3 f_specular = vec3(0.0);
        vec3 f_emissive = m_emissive;
        vec3 wnn = normalize(f_worldNormal);
        vec3 ep = (p_viewMatrixInverse * vec4(0.0, 0.0, 0.0, 1.0)).xyz;
        vec3 edn = normalize(ep - v_worldPosition);

        ${if(environmentMap && context.meshCubemaps.isNotEmpty()) """
            float fresnelBias = 0.1;
            float fresnelScale = 0.5;
            float fresnelPower = 0.3;
            float reflectivity = fresnelBias + fresnelScale * pow(1.0 + dot(-edn, f_worldNormal), fresnelPower);

            f_specular.rgb += texture(p_environmentMap, reflect(-edn, normalize(f_worldNormal))).rgb * reflectivity;
        """.trimIndent()  else ""  }

        ${lights.mapIndexed { index, (node, light) ->
            when (light) {
                is AmbientLight -> light.fs(index)
                is PointLight -> light.fs(index)
                is SpotLight -> light.fs(index)
                is DirectionalLight -> light.fs(index)
                is HemisphereLight -> light.fs(index)
                else -> TODO()
            }
        }.joinToString("\n")}

        ${context.fogs.mapIndexed { index, (node, fog) ->
            fog.fs(index)
        }.joinToString("\n")}

        ${context.pass.combiners.joinToString("\n") {
            it.generateShader()
        }
        }
    """.trimIndent() else ""

        val rt = RenderTarget.active

        val combinerFS = context.pass.combiners.map {
            it.generateShader()
        }.joinToString("\n")

        val fs = preambleFS + textureFs + lightFS + combinerFS

        return shadeStyle {
            fragmentPreamble = """
            |vec3 rnmBlendUnpacked(vec3 n1, vec3 n2) {
            |   n1 += vec3( 0,  0, 1);
            |   n2 *= vec3(-1, -1, 1);
            |   return normalize(n1*dot(n1, n2)/n1.z - n2);
            |}
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

        shadeStyle.parameter("emissive", emissive)
        shadeStyle.parameter("specular", specular)
        shadeStyle.parameter("diffuse", diffuse)
        shadeStyle.parameter("shininess", shininess)

        parameters.forEach { k, v ->
            when (v) {
                is Double -> shadeStyle.parameter(k, v)
                is Int -> shadeStyle.parameter(k, v)
                is Vector2 -> shadeStyle.parameter(k, v)
                is Vector3 -> shadeStyle.parameter(k, v)
                is Vector4 -> shadeStyle.parameter(k, v)
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
    val texture = Texture(DummySource, target = TextureTarget.DIFFUSE)
    texture.init()
    textures.add(texture)
    return texture
}

fun MeshBase.basicMaterial(init: BasicMaterial.() -> Unit): BasicMaterial = material(init)