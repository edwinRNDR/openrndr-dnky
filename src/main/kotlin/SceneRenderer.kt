package org.openrndr.dnky

import org.openrndr.draw.*
import org.openrndr.math.Matrix44
import org.openrndr.math.Vector4
import org.openrndr.math.transforms.normalMatrix

class SceneRenderer {

    fun draw(drawer: Drawer, scene: Scene, camera: Camera) {
        // -- call all draw() functions
        scene.root.visit {
            draw?.invoke()
        }

        // update all the transforms
        scene.root.scan(Matrix44.IDENTITY) { p ->
            worldTransform = p * transform
            worldTransform
        }

        val lights = scene.root.findNodes { entity is Light }
        val meshes = scene.root.findNodes { entity is Mesh }

        meshes.forEach {
            drawer.isolated {
                drawer.shadeStyle = shadeStyle(lights)
                drawer.model = it.worldTransform
                drawer.vertexBuffer((it.entity as Mesh).geometry.vertexBuffers, DrawPrimitive.TRIANGLES)
            }
        }
    }
}

private fun shadeStyle(lights: List<SceneNode>): ShadeStyle {
    val fs = """
        vec3 light = vec3(0.0);
        vec3 wnn = normalize(v_worldNormal);
        ${lights.mapIndexed { index, node ->
        when (node.entity) {
            is AmbientLight -> "light += p_lightColor$index.rgb;"
            is PointLight -> """
                {
                vec3 dp = p_lightPosition$index - v_worldPosition;
                vec3 dpn = normalize(dp);
                light += max(0, dot(dpn, wnn)) * p_lightColor$index.rgb;
                }
            """.trimIndent()
            is DirectionalLight -> """
                {
                light += max(0, dot(wnn , p_lightDirection$index)) * p_lightColor$index.rgb;
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
        lights.forEachIndexed { index, node ->
            parameter("lightColor$index", (node.entity as Light).color)
            when (val light = node.entity as Light) {
                is AmbientLight -> {

                }

                is PointLight -> {
                    parameter("lightPosition$index", (node.worldTransform * Vector4.UNIT_W).xyz)
                }

                is DirectionalLight -> {
                    parameter("lightDirection$index", ((normalMatrix(node.worldTransform)) * light.direction).normalized)
                }
            }
        }
    }
}