package org.openrndr.dnky

import org.openrndr.color.ColorRGBa
import org.openrndr.draw.ShadeStyle
import org.openrndr.draw.shadeStyle
import org.openrndr.math.Vector2
import org.openrndr.math.Vector3
import org.openrndr.math.Vector4
import org.openrndr.math.transforms.normalMatrix

data class MaterialContext(val lights: List<NodeContent<Light>>, val fogs: List<NodeContent<Fog>>)

interface Material {
    fun generateShadeStyle(context: MaterialContext): ShadeStyle
    fun applyToShadeStyle(context: MaterialContext, shadeStyle: ShadeStyle)
}

class BasicMaterial : Material {
    var diffuse = ColorRGBa.WHITE
    var specular = ColorRGBa.WHITE
    var shininess = 1.0
    var vertexTransform : String? = null
    var fragmentTransform : String? = null
    var parameters = mutableMapOf<String, Any>()

    override fun generateShadeStyle(context: MaterialContext): ShadeStyle {
        val lights = context.lights
        val fs = """
        highp vec3 light = vec3(0.0);
        vec3 wnn = normalize(v_worldNormal);
        vec3 ep = (p_viewMatrixInverse * vec4(0.0, 0.0, 0.0, 1.0)).xyz;
        vec3 edn = normalize(ep - v_worldPosition);
        ${lights.mapIndexed { index, (node, light) ->
                when (light) {
                    is AmbientLight -> "light += p_lightColor$index.rgb;"
                    is PointLight -> """
                {
                    vec3 dp = p_lightPosition$index - v_worldPosition;
                    float distance = length(dp);
                    float attenuation = 1.0 / (p_lightConstantAttenuation$index +
                    p_lightLinearAttenuation$index * distance + p_lightQuadraticAttenuation$index * distance * distance);
                    vec3 dpn = normalize(dp);

                    float side = dot(dpn, wnn);
                    light += attenuation * max(0, side) * p_lightColor$index.rgb * p_diffuse.rgb;
                    if (side > 0.0) {
                        float f = max(0.0, dot(reflect(-dpn, wnn), edn));
                        light += attenuation * pow(f, p_shininess) * p_lightColor$index.rgb * p_specular.rgb;
                    }
                }
            """.trimIndent()
                    is DirectionalLight -> """
                {
                    float side = dot(-wnn, p_lightDirection$index);
                    light += max(0, side) * p_lightColor$index.rgb * p_diffuse.rgb;
                    if (side > 0.0) {
                        float f = max(0.0, dot(reflect(p_lightDirection$index, wnn), edn));
                        light += pow(f, p_shininess) * p_lightColor$index.rgb * p_specular.rgb;
                    }


                }
            """.trimIndent()
                    else -> TODO()
                }
        }.joinToString("\n")
        }

        ${context.fogs.mapIndexed { index, (node, fog) ->
           """ {
                float dz = min(1.0, -v_viewPosition.z/p_fogEnd$index);
                light = mix(light, p_fogColor$index.rgb, dz);
                }
            """
        }.joinToString("\n")}
        x_fill.rgb = light;
        x_fill.a = 1.0;
    """.trimIndent()

        return shadeStyle {
            this.vertexTransform = this@BasicMaterial.vertexTransform
            fragmentTransform = fs
        }
    }

    override fun applyToShadeStyle(context: MaterialContext, shadeStyle: ShadeStyle) {
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
            }
        }

        context.fogs.forEachIndexed { index, (node, fog) ->
            shadeStyle.parameter("fogColor$index", fog.color)
            shadeStyle.parameter("fogEnd$index", fog.end)
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