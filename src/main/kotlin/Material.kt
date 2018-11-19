package org.openrndr.dnky

import org.openrndr.color.ColorRGBa
import org.openrndr.draw.RenderTarget
import org.openrndr.draw.ShadeStyle
import org.openrndr.draw.shadeStyle
import org.openrndr.math.Vector2
import org.openrndr.math.Vector3
import org.openrndr.math.Vector4
import org.openrndr.math.transforms.normalMatrix

data class MaterialContext(val pass: RenderPass, val lights: List<NodeContent<Light>>, val fogs: List<NodeContent<Fog>>)

interface Material {
    fun generateShadeStyle(context: MaterialContext): ShadeStyle
    fun applyToShadeStyle(context: MaterialContext, shadeStyle: ShadeStyle)
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
|       f_specular += attenuation * pow(f, p_shininess) * p_lightColor$index.rgb * p_specular.rgb;
|   }
}
""".trimMargin()

private fun AmbientLight.fs(index: Int): String = "light += p_lightColor$index.rgb;"

private fun DirectionalLight.fs(index: Int) = """
|{
|    float side = dot(-wnn, p_lightDirection$index);
|    f_diffuse += max(0, side) * p_lightColor$index.rgb * p_diffuse.rgb;
|    if (side > 0.0) {
|        float f = max(0.0, dot(reflect(p_lightDirection$index, wnn), edn));
|        f_specular += pow(f, p_shininess) * p_lightColor$index.rgb * p_specular.rgb;
|    }
|}
""".trimIndent()

private fun HemisphereLight.fs(index: Int): String = """
|{
|   float f = dot(wnn, p_lightDirection$index) * 0.5 + 0.5;
|   vec3 irr = ${irradianceMap?.let { "texture(p_lightIrradianceMap$index, wnn).rgb" } ?: "vec3(1.0)"};
|   f_diffuse += mix(p_lightDownColor$index.rgb, p_lightUpColor$index.rgb, f) * irr;
|}
""".trimIndent()

private fun Fog.fs(index: Int): String = """
|{
|    float dz = min(1.0, -v_viewPosition.z/p_fogEnd$index);
|    f_diffuse = mix(f_diffuse, 0.5 * p_fogColor$index.rgb, dz);
|    f_specular = mix(f_specular, 0.5 * p_fogColor$index.rgb, dz);
|}
""".trimIndent()

class BasicMaterial : Material {
    var diffuse = ColorRGBa.WHITE
    var specular = ColorRGBa.WHITE
    var shininess = 1.0
    var vertexTransform: String? = null
    var fragmentTransform: String? = null
    var parameters = mutableMapOf<String, Any>()

    override fun generateShadeStyle(context: MaterialContext): ShadeStyle {
        val needLight = needLight(context)

        val lights = context.lights
        val lightFS = if (needLight) """
        vec3 f_diffuse = vec3(0.0);
        vec3 f_specular = vec3(0.0);
        vec3 wnn = normalize(v_worldNormal);
        vec3 ep = (p_viewMatrixInverse * vec4(0.0, 0.0, 0.0, 1.0)).xyz;
        vec3 edn = normalize(ep - v_worldPosition);

        ${lights.mapIndexed { index, (node, light) ->
            when (light) {
                is AmbientLight -> light.fs(index)
                is PointLight -> light.fs(index)
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

        val fs = lightFS + combinerFS



        return shadeStyle {
            this.suppressDefaultOutput = true
            this.vertexTransform = this@BasicMaterial.vertexTransform
            fragmentTransform = lightFS
            context.pass.combiners.map {
                this.output(it.targetOutput, rt.colorBufferIndex(it.targetOutput))
            }
        }
    }

    private fun needLight(context: MaterialContext): Boolean {
        val needSpecular = context.pass.combiners.any { FacetType.SPECULAR in it.facets }
        val needDiffuse = context.pass.combiners.any { FacetType.SPECULAR in it.facets }
        val needLight = needSpecular || needDiffuse
        return needLight
    }

    override fun applyToShadeStyle(context: MaterialContext, shadeStyle: ShadeStyle) {
        shadeStyle.parameter("specular", specular)
        shadeStyle.parameter("diffuse", diffuse)
        shadeStyle.parameter("shininess", shininess)

        if (needLight(context)) {
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

fun MeshBase.basicMaterial(init: BasicMaterial.() -> Unit): BasicMaterial = material(init)