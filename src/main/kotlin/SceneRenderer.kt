package org.openrndr.dnky

import org.openrndr.draw.*
import org.openrndr.math.Matrix44

enum class FacetType(val shaderFacet:String) {
    WORLD_POSITION("f_worldPosition"),
    WORLD_NORMAL("f_worldNormal"),
    SPECULAR("f_specular"),
    DIFFUSE("f_diffuse")
}

abstract class RenderFacet(val facets: Set<FacetType>, val targetOutput: String, val format: ColorFormat, val type: ColorType) {
    abstract fun generateShader(): String
}

class PositionFacet : RenderFacet(setOf(FacetType.WORLD_POSITION),"position", ColorFormat.RGB, ColorType.FLOAT16) {
    override fun generateShader(): String = "o_$targetOutput.rgb = v_worldPosition.rgb;"
}

class NormalFacet : RenderFacet(setOf(FacetType.WORLD_NORMAL), "normal", ColorFormat.RGB, ColorType.FLOAT16) {
    override fun generateShader() = "o_$targetOutput.rgb = v_worldNormal.rgb;"
}

class LDRColorFacet : RenderFacet(setOf(FacetType.DIFFUSE, FacetType.SPECULAR), "color", ColorFormat.RGBa, ColorType.UINT8) {
    override fun generateShader() = "o_$targetOutput.rgba = vec4(f_diffuse.rgb + f_specular.rgb, 1.0);"
}

class RenderPass(val combiners: List<RenderFacet>)

val DefaultPass = RenderPass(listOf(LDRColorFacet()))
val LightPass = RenderPass(listOf(PositionFacet()))

fun createPassTarget(pass: RenderPass, width: Int, height: Int) : RenderTarget {
    return renderTarget(width, height) {
        for (facet in pass.combiners) {
            colorBuffer(facet.targetOutput, facet.format, facet.type)
        }
        depthBuffer()
    }
}

class SceneRenderer {

    fun draw(drawer: Drawer, scene: Scene, camera: Camera) {
        scene.drawFunctions.forEach {
            it()
        }

        // update all the transforms
        scene.root.scan(Matrix44.IDENTITY) { p ->
            worldTransform = p * transform
            worldTransform
        }

        val lights = scene.root.findContent { this as? Light }
        val meshes = scene.root.findContent { this as? Mesh }
        val fogs = scene.root.findContent { this as? Fog }
        val instancedMeshes = scene.root.findContent { this as? InstancedMesh }


        val pass = DefaultPass
        val materialContext = MaterialContext(pass, lights, fogs)

        meshes.forEach {
            val mesh = it.content
            drawer.isolated {
                val shadeStyle = mesh.material.generateShadeStyle(materialContext)
                shadeStyle.parameter("viewMatrixInverse", drawer.view.inversed)
                mesh.material.applyToShadeStyle(materialContext, shadeStyle)
                drawer.shadeStyle = shadeStyle
                drawer.model = it.node.worldTransform
                drawer.vertexBuffer(mesh.geometry.vertexBuffers,
                        DrawPrimitive.TRIANGLES,
                        mesh.geometry.offset,
                        mesh.geometry.vertexCount)
            }
        }

        instancedMeshes.forEach {
            val mesh = it.content
            drawer.isolated {
                val shadeStyle = mesh.material.generateShadeStyle(materialContext)
                shadeStyle.parameter("viewMatrixInverse", drawer.view.inversed)
                mesh.material.applyToShadeStyle(materialContext, shadeStyle)
                drawer.shadeStyle = shadeStyle
                drawer.model = it.node.worldTransform
                drawer.vertexBufferInstances(mesh.geometry.vertexBuffers,
                        mesh.attributes,
                        DrawPrimitive.TRIANGLES,
                        mesh.instances,
                        mesh.geometry.offset,
                        mesh.geometry.vertexCount)
            }
        }
    }
}

