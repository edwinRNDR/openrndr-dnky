package org.openrndr.dnky

import org.openrndr.color.ColorRGBa
import org.openrndr.draw.*
import org.openrndr.math.Matrix44
import org.openrndr.math.Vector3
import org.openrndr.math.transforms.lookAt
import org.openrndr.math.transforms.transform
import java.util.*

enum class FacetType(val shaderFacet: String) {
    WORLD_POSITION("f_worldPosition"),
    VIEW_POSITION("f_viewPosition"),
    CLIP_POSITION("f_clipPosition"),
    WORLD_NORMAL("f_worldNormal"),
    SPECULAR("f_specular"),
    DIFFUSE("f_diffuse")
}

abstract class FacetCombiner(val facets: Set<FacetType>, val targetOutput: String) {
    abstract fun generateShader(): String
}

abstract class ColorBufferFacetCombiner(facets: Set<FacetType>,
                                        targetOutput: String,
                                        val format: ColorFormat,
                                        val type: ColorType) : FacetCombiner(facets, targetOutput)

class PositionFacet : ColorBufferFacetCombiner(setOf(FacetType.WORLD_POSITION), "position", ColorFormat.RGB, ColorType.FLOAT16) {
    override fun generateShader(): String = "o_$targetOutput.rgb = v_worldPosition.rgb;"
}

class ViewPositionFacet : ColorBufferFacetCombiner(setOf(FacetType.WORLD_POSITION), "viewPosition", ColorFormat.RGB, ColorType.FLOAT16) {
    override fun generateShader(): String = "o_$targetOutput.rgb = v_viewPosition.rgb;"
}


class ClipPositionFacet : ColorBufferFacetCombiner(setOf(FacetType.CLIP_POSITION), "position", ColorFormat.RGB, ColorType.FLOAT16) {
    override fun generateShader() = "o_$targetOutput.rgb = gl_FragCoord.xyz;"
}

class LDRColorFacet : ColorBufferFacetCombiner(setOf(FacetType.DIFFUSE, FacetType.SPECULAR), "color", ColorFormat.RGBa, ColorType.UINT8) {
    override fun generateShader() = "o_$targetOutput.rgba = vec4(f_diffuse.rgb + f_specular.rgb, 1.0);"
}

class RenderPass(val combiners: List<FacetCombiner>)

val DefaultPass = RenderPass(listOf(LDRColorFacet()))
val LightPass = RenderPass(listOf(ClipPositionFacet()))

fun createPassTarget(pass: RenderPass, width: Int, height: Int): RenderTarget {
    return renderTarget(width, height) {
        for (combiner in pass.combiners) {
            when (combiner) {
                is ColorBufferFacetCombiner ->
                    colorBuffer(combiner.targetOutput, combiner.format, combiner.type)
            }
        }
        depthBuffer(format = DepthFormat.DEPTH32F)
    }
}

class SceneRenderer {

    var shadowLightTargets = mutableMapOf<ShadowLight, RenderTarget>()

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

        run {
            val pass = LightPass
            val materialContext = MaterialContext(pass, lights, fogs, shadowLightTargets)

            lights.filter { it.content is ShadowLight && (it.content as ShadowLight).shadows }.forEach {
                val shadowLight = it.content as ShadowLight
                val target = shadowLightTargets.getOrPut(shadowLight) {
                    createPassTarget(pass, 1024, 1024)
                }
                target.clearDepth(depth = 1.0)
                val look = shadowLight.view(it.node)
                drawer.isolatedWithTarget(target) {
                    drawer.projection = shadowLight.projection(target)
                    drawer.view = look
                    drawer.model = Matrix44.IDENTITY

                    drawer.background(ColorRGBa.PINK)
                    drawPass(drawer, materialContext, meshes, instancedMeshes)
                }
            }
        }

        run {
            val pass = DefaultPass
            val materialContext = MaterialContext(pass, lights, fogs, shadowLightTargets)
            drawPass(drawer, materialContext, meshes, instancedMeshes)
        }
    }

    private fun drawPass(drawer: Drawer, materialContext: MaterialContext, meshes: List<NodeContent<Mesh>>, instancedMeshes: List<NodeContent<InstancedMesh>>) {
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

