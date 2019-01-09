package org.openrndr.dnky

import org.openrndr.color.ColorRGBa
import org.openrndr.dnky.post.PostStep
import org.openrndr.dnky.post.Ssao
import org.openrndr.dnky.post.Sslr
import org.openrndr.draw.*
import org.openrndr.math.Matrix44
import org.openrndr.math.Vector3
import org.openrndr.math.Vector4
import org.openrndr.math.transforms.scale
import org.openrndr.math.transforms.translate

enum class FacetType(val shaderFacet: String) {
    WORLD_POSITION("f_worldPosition"),
    VIEW_POSITION("f_viewPosition"),
    CLIP_POSITION("f_clipPosition"),
    WORLD_NORMAL("f_worldNormal"),
    VIEW_NORMAL("f_viewNormal"),
    SPECULAR("f_specular"),
    DIFFUSE("f_diffuse"),
    EMISSIVE("f_emission"),
    COLOR("m_color"),
}

abstract class FacetCombiner(val facets: Set<FacetType>, val targetOutput: String) {
    abstract fun generateShader(): String
}

abstract class ColorBufferFacetCombiner(facets: Set<FacetType>,
                                        targetOutput: String,
                                        val format: ColorFormat,
                                        val type: ColorType) : FacetCombiner(facets, targetOutput)

class DiffuseSpecularFacet : ColorBufferFacetCombiner( setOf(FacetType.DIFFUSE, FacetType.SPECULAR),
        "diffuseSpecular", ColorFormat.RGB, ColorType.FLOAT16) {
    override fun generateShader(): String =
            "o_$targetOutput =vec4( max(vec3(0.0), f_diffuse.rgb) + max(vec3(0.0), f_specular.rgb), 1.0);"
}

class MaterialFacet : ColorBufferFacetCombiner( setOf(FacetType.DIFFUSE),
        "material", ColorFormat.RGBa, ColorType.UINT8) {
    override fun generateShader(): String =
        "o_$targetOutput = vec4(m_metalness, m_roughness, m_f0, 0.0);"
}

class BaseColorFacet : ColorBufferFacetCombiner( setOf(FacetType.COLOR),
        "baseColor", ColorFormat.RGB, ColorType.UINT8) {
    override fun generateShader(): String = "o_$targetOutput = vec4(m_color.rgb, 1.0);"

}

class DiffuseFacet : ColorBufferFacetCombiner( setOf(FacetType.DIFFUSE),
        "diffuse", ColorFormat.RGB, ColorType.FLOAT16) {
    override fun generateShader(): String =
            "o_$targetOutput = vec4( max(vec3(0.0), f_diffuse.rgb), 1.0 );"
}

class SpecularFacet : ColorBufferFacetCombiner( setOf(FacetType.SPECULAR),
        "diffuseSpecular", ColorFormat.RGB, ColorType.FLOAT16) {
    override fun generateShader(): String =
            "o_$targetOutput = vec4( max(vec3(0.0), f_specular.rgb), 1.0);"
}

class EmissiveFacet : ColorBufferFacetCombiner( setOf(FacetType.EMISSIVE),
        "emissive", ColorFormat.RGB, ColorType.FLOAT16) {
    override fun generateShader(): String =
            "o_$targetOutput =  vec4(f_emission * m_color, 1.0);"
}

class PositionFacet : ColorBufferFacetCombiner(setOf(FacetType.WORLD_POSITION), "position", ColorFormat.RGB, ColorType.FLOAT16) {
    override fun generateShader(): String = "o_$targetOutput = vec4(v_worldPosition.rgb, 1.0);"
}

class NormalFacet : ColorBufferFacetCombiner(setOf(FacetType.WORLD_NORMAL), "normal", ColorFormat.RGB, ColorType.FLOAT16) {
    override fun generateShader(): String = "o_$targetOutput = vec4(v_worldNormal.rgb, 1.0);"
}

class ViewPositionFacet : ColorBufferFacetCombiner(setOf(FacetType.VIEW_POSITION), "viewPosition", ColorFormat.RGB, ColorType.FLOAT16) {
    override fun generateShader(): String = "o_$targetOutput.rgb = v_viewPosition.rgb;"
}

class ViewNormalFacet : ColorBufferFacetCombiner(setOf(FacetType.VIEW_NORMAL), "viewNormal", ColorFormat.RGB, ColorType.FLOAT16) {
    override fun generateShader(): String = "o_$targetOutput.rgb = normalize( (u_viewNormalMatrix * vec4(f_worldNormal,0.0)).xyz );"
}

class ClipPositionFacet : ColorBufferFacetCombiner(setOf(FacetType.CLIP_POSITION), "position", ColorFormat.RGB, ColorType.FLOAT16) {
    override fun generateShader() = "o_$targetOutput.rgb = gl_FragCoord.xyz;"
}

class LDRColorFacet : ColorBufferFacetCombiner(setOf(FacetType.DIFFUSE, FacetType.SPECULAR), "color", ColorFormat.RGBa, ColorType.UINT8) {
    override fun generateShader() = """
    vec3 oofinalColor =  (f_diffuse.rgb + f_specular.rgb + f_emission * m_color.rgb) * (1.0 - f_fog.a) + f_fog.rgb * f_fog.a;
    o_$targetOutput.rgba = pow(vec4(oofinalColor, 1.0), vec4(1.0));
    """
}

class RenderPass(val combiners: List<FacetCombiner>)

val DefaultPass = RenderPass(listOf(LDRColorFacet()))
val LightPass = RenderPass(emptyList())

fun createPassTarget(pass: RenderPass, width: Int, height: Int, depthFormat: DepthFormat = DepthFormat.DEPTH24): RenderTarget {
    return renderTarget(width, height) {
        for (combiner in pass.combiners) {
            when (combiner) {
                is ColorBufferFacetCombiner ->
                    colorBuffer(combiner.targetOutput, combiner.format, combiner.type)
            }
        }
        depthBuffer(depthFormat)
    }
}


class SceneRenderer {

    var shadowLightTargets = mutableMapOf<ShadowLight, RenderTarget>()
    var meshCubemaps = mutableMapOf<Mesh, Cubemap>()

    var cubemapDepthBuffer = depthBuffer(256, 256, DepthFormat.DEPTH16, BufferMultisample.Disabled)

    var outputPass = DefaultPass
    var outputPassTarget: RenderTarget? = null

    val postSteps = mutableListOf<PostStep>()
    val buffers = mutableMapOf<String, ColorBuffer>()

    var drawFinalBuffer = true

    fun draw(drawer: Drawer, scene: Scene, camera: Camera) {
        drawer.pushStyle()
        drawer.depthWrite = true
        drawer.depthTestPass = DepthTestPass.LESS_OR_EQUAL

        scene.updateFunctions.forEach {
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
        val environmentMapMeshes = meshes.filter { (it.content.material as? BasicMaterial)?.environmentMap == true }

        run {
            val pass = LightPass
            val materialContext = MaterialContext(pass, lights, fogs, shadowLightTargets, emptyMap())

            lights.filter { it.content is ShadowLight && (it.content as ShadowLight).shadows }.forEach {
                val shadowLight = it.content as ShadowLight
                val target = shadowLightTargets.getOrPut(shadowLight) {
                    createPassTarget(pass, 1024, 1024, DepthFormat.DEPTH16)
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

        for (content in environmentMapMeshes) {
            val (node, mesh) = content
            val position = (node.worldTransform * Vector4.UNIT_W).xyz
            val pass = DefaultPass
            val materialContext = MaterialContext(pass, lights, fogs, shadowLightTargets, emptyMap())

            val cubemap = meshCubemaps.getOrPut(mesh) {
                Cubemap.create(256)
            }
            for (side in CubemapSide.values()) {
                val target = renderTarget(256, 256) {
                    colorBuffer(cubemap.side(side))
                    depthBuffer(cubemapDepthBuffer)
                }

                drawer.isolatedWithTarget(target) {
                    drawer.background(ColorRGBa.PINK)
                    drawer.perspective(90.0, 1.0, 0.1, 100.0)
                    drawer.view = Matrix44.IDENTITY
                    drawer.model = Matrix44.IDENTITY
                    drawer.lookAt(position, position + side.forward, side.up)
                    drawPass(drawer, materialContext, meshes - content, instancedMeshes)
                }
                cubemap.generateMipmaps()
                target.detachColorBuffers()
                target.detachDepthBuffer()
                target.destroy()
            }
        }

        run {
            val pass = outputPass
            val materialContext = MaterialContext(pass, lights, fogs, shadowLightTargets, meshCubemaps)

            if ((pass != DefaultPass || postSteps.isNotEmpty()) && outputPassTarget == null) {
                outputPassTarget = createPassTarget(outputPass, RenderTarget.active.width, RenderTarget.active.height)
            }

            outputPassTarget?.let {
                drawer.withTarget(it) {
                    background(ColorRGBa.PINK)
                }
            }
            outputPassTarget?.bind()
            drawPass(drawer, materialContext, meshes, instancedMeshes)
            val drawNodes = scene.root.findNodes { drawFunction != null }
            outputPassTarget?.unbind()

            drawNodes.forEach {
                drawer.isolated {
                    drawer.model = it.worldTransform
                    it.drawFunction?.invoke(drawer)
                }
            }

            outputPassTarget?.let { output ->
                for (combiner in outputPass.combiners) {
                    buffers[combiner.targetOutput] = output.colorBuffer(combiner.targetOutput)
                }
            }
            for (postStep in postSteps) {
                if (postStep.filter is Ssao) {
                    postStep.filter.projection = drawer.projection
                }

                if (postStep.filter is Sslr) {
                    val p = scale(drawer.width / 2.0, drawer.height / 2.0, 1.0) * translate(Vector3(1.0, 1.0, 0.0)) * drawer.projection
                    postStep.filter.projection = p //drawer.projection
                }

                postStep.apply(buffers)
            }
        }

        drawer.popStyle()
        if (drawFinalBuffer) {
            outputPassTarget?.let { output ->
                drawer.isolated {
                    drawer.ortho()
                    drawer.view = Matrix44.IDENTITY
                    drawer.model = Matrix44.IDENTITY
                    val outputName = postSteps.last().output
                    val outputBuffer = buffers[outputName] ?: throw IllegalArgumentException("can't find $outputName buffer")
                    drawer.image(outputBuffer)
                }
            }
        }
    }

    private fun drawPass(drawer: Drawer, materialContext: MaterialContext, meshes: List<NodeContent<Mesh>>, instancedMeshes: List<NodeContent<InstancedMesh>>) {
        meshes.forEach {
            val mesh = it.content
            drawer.isolated {
                val shadeStyle = mesh.material.generateShadeStyle(materialContext)
                shadeStyle.parameter("viewMatrixInverse", drawer.view.inversed)
                mesh.material.applyToShadeStyle(materialContext, shadeStyle, mesh)
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
                mesh.material.applyToShadeStyle(materialContext, shadeStyle, mesh)
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

fun sceneRenderer(builder: SceneRenderer.() -> Unit): SceneRenderer {
    val sceneRenderer = SceneRenderer()
    sceneRenderer.builder()
    return sceneRenderer
}