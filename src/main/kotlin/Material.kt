package org.openrndr.dnky

import org.openrndr.color.ColorRGBa
import org.openrndr.draw.ShadeStyle
import org.openrndr.draw.shadeStyle
import org.openrndr.math.Vector4
import org.openrndr.math.transforms.normalMatrix

data class MaterialContext(val lights: List<NodeContent<Light>>)

interface Material {
    fun generateShadeStyle(context: MaterialContext): ShadeStyle
    fun applyToShadeStyle(context: MaterialContext, shadeStyle: ShadeStyle)
}

class BasicMaterial : Material {
    var diffuse = ColorRGBa.WHITE
    var specular = ColorRGBa.WHITE
    var shininess = 1.0

    override fun generateShadeStyle(context: MaterialContext): ShadeStyle {
        val lights = context.lights
        val fs = """
        vec3 light = vec3(0.0);
        vec3 wnn = normalize(v_worldNormal);
        vec3 ep = (p_viewMatrixInverse * vec4(0.0, 0.0, 0.0, 1.0)).xyz;
        vec3 edn = normalize(ep - v_worldPosition);
        ${lights.mapIndexed { index, (node, light) ->
                when (light) {
                    is AmbientLight -> "light += p_lightColor$index.rgb;"
                    is PointLight -> """
                {
                    vec3 dp = p_lightPosition$index - v_worldPosition;
                    vec3 dpn = normalize(dp);

                    float side = dot(dpn, wnn);
                    light += max(0, side) * p_lightColor$index.rgb * p_diffuse.rgb;
                    if (side > 0.0) {
                        float f = max(0.0, dot(reflect(-dpn, wnn), edn));
                        light += pow(f, p_shininess) * p_lightColor$index.rgb * p_specular.rgb;
                    }
                }
            """.trimIndent()
                    is DirectionalLight -> """
                {
                    light += max(0, dot(wnn , p_lightDirection$index)) * p_lightColor$index.rgb * p_diffuse.rgb;
                }
            """.trimIndent()

                    else -> TODO()
                }


        }.joinToString("\n")
        }
        x_fill.rgb = light;
        x_fill.a = 1.0;
    """.trimIndent()

        return shadeStyle {
            fragmentTransform = fs
        }
    }

    override fun applyToShadeStyle(context: MaterialContext, shadeStyle: ShadeStyle) {
        shadeStyle.parameter("specular", specular)
        shadeStyle.parameter("diffuse", diffuse)
        shadeStyle.parameter("shininess", shininess)

        val lights = context.lights
        lights.forEachIndexed { index, (node, light) ->
            shadeStyle.parameter("lightColor$index", light.color)
            when (light) {
                is AmbientLight -> {
                }

                is PointLight -> {
                    shadeStyle.parameter("lightPosition$index", (node.worldTransform * Vector4.UNIT_W).xyz)
                }

                is DirectionalLight -> {
                    shadeStyle.parameter("lightDirection$index", ((normalMatrix(node.worldTransform)) * light.direction).normalized)
                }
            }
        }
    }
}

private inline fun <reified T : Material> Mesh.material(init: T.() -> Unit): T {
    val t: T = T::class.java.constructors[0].newInstance() as T
    t.init()
    material = t
    return t
}

fun Mesh.basicMaterial(init: BasicMaterial.() -> Unit): BasicMaterial = material(init)