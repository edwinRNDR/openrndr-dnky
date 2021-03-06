package org.openrndr.dnky

import org.openrndr.color.ColorRGBa
import org.openrndr.dnky.post.FilterPostStep
import org.openrndr.dnky.post.PostStep
import org.openrndr.dnky.post.Ssao
import org.openrndr.dnky.post.Sslr
import org.openrndr.draw.*
import org.openrndr.math.Matrix44
import org.openrndr.math.Vector3
import org.openrndr.math.Vector4
import org.openrndr.math.transforms.scale
import org.openrndr.math.transforms.translate
import post.ApproximateGaussianBlur

enum class FacetType(val shaderFacet: String) {
    WORLD_POSITION("f_worldPosition"),
    VIEW_POSITION("f_viewPosition"),
    CLIP_POSITION("f_clipPosition"),
    WORLD_NORMAL("f_worldNormal"),
    VIEW_NORMAL("f_viewNormal"),
    SPECULAR("f_specular"),
    DIFFUSE("f_diffuse"),
    EMISSIVE("f_emission"),
    AMBIENT("f_ambient"),
    OCCLUSION("f_occlusion"),
    COLOR("m_color"),
}

abstract class FacetCombiner(val facets: Set<FacetType>, val targetOutput: String) {
    abstract fun generateShader(): String
}

abstract class ColorBufferFacetCombiner(facets: Set<FacetType>,
                                        targetOutput: String,
                                        val format: ColorFormat,
                                        val type: ColorType,
                                        val blendMode: BlendMode = BlendMode.REPLACE) : FacetCombiner(facets, targetOutput)

class MomentsFacet : ColorBufferFacetCombiner(setOf(FacetType.WORLD_POSITION), "moments", ColorFormat.RG, ColorType.FLOAT16) {
    override fun generateShader(): String {
        return """
            float depth = length(v_viewPosition);
            float dx = dFdx(depth);
            float dy = dFdy(depth);
            o_$targetOutput = vec4(depth, depth*depth + 0.25 * dx*dx+dy*dy, 0.0, 1.0);
        """
    }
}

class DiffuseSpecularFacet : ColorBufferFacetCombiner(setOf(FacetType.DIFFUSE, FacetType.SPECULAR),
        "diffuseSpecular", ColorFormat.RGB, ColorType.FLOAT16) {
    override fun generateShader(): String =
            "o_$targetOutput = vec4( max(vec3(0.0), f_diffuse.rgb) + max(vec3(0.0), f_specular.rgb), 1.0);"
}
class DiffuseSpecularAlphaFacet : ColorBufferFacetCombiner(setOf(FacetType.DIFFUSE, FacetType.SPECULAR),
        "diffuseSpecular", ColorFormat.RGB, ColorType.FLOAT16) {
    override fun generateShader(): String =
            "o_$targetOutput = vec4( (max(vec3(0.0), f_diffuse.rgb) + max(vec3(0.0), f_specular.rgb)) * f_alpha, f_alpha);"
}

class AmbientOcclusionFacet : ColorBufferFacetCombiner(setOf(FacetType.AMBIENT, FacetType.OCCLUSION),
        "ambientOcclusion", ColorFormat.RGBa, ColorType.FLOAT16) {
    override fun generateShader(): String =
            "o_$targetOutput = vec4(f_ambient, f_occlusion);"
}


class MaterialFacet : ColorBufferFacetCombiner(setOf(FacetType.DIFFUSE),
        "material", ColorFormat.RGBa, ColorType.UINT8) {
    override fun generateShader(): String =
            "o_$targetOutput = vec4(m_metalness, m_roughness, 0.0, 1.0);"
}

class BaseColorFacet : ColorBufferFacetCombiner(setOf(FacetType.COLOR),
        "baseColor", ColorFormat.RGB, ColorType.UINT8) {
    override fun generateShader(): String = "o_$targetOutput = vec4(m_color.rgb, 1.0);"
}

class DiffuseFacet : ColorBufferFacetCombiner(setOf(FacetType.DIFFUSE),
        "diffuse", ColorFormat.RGB, ColorType.FLOAT16) {
    override fun generateShader(): String =
            "o_$targetOutput = vec4( max(vec3(0.0), f_diffuse.rgb), 1.0 );"
}

class SpecularFacet : ColorBufferFacetCombiner(setOf(FacetType.SPECULAR),
        "diffuseSpecular", ColorFormat.RGB, ColorType.FLOAT16) {
    override fun generateShader(): String =
            "o_$targetOutput = vec4( max(vec3(0.0), f_specular.rgb), 1.0);"
}

class EmissiveFacet: ColorBufferFacetCombiner(setOf(FacetType.EMISSIVE),
        "emissive", ColorFormat.RGB, ColorType.FLOAT16) {
    override fun generateShader(): String =
            "o_$targetOutput =  vec4(f_emission, 1.0);"
}

class EmissiveAlphaFacet: ColorBufferFacetCombiner(setOf(FacetType.EMISSIVE),
        "emissive", ColorFormat.RGB, ColorType.FLOAT16, BlendMode.OVER) {
    override fun generateShader(): String =
            "o_$targetOutput =  vec4(f_emission, f_alpha);"
}



class PositionFacet : ColorBufferFacetCombiner(setOf(FacetType.WORLD_POSITION), "position", ColorFormat.RGB, ColorType.FLOAT16) {
    override fun generateShader(): String = "o_$targetOutput = vec4(v_worldPosition.rgb, 1.0);"
}

class NormalFacet : ColorBufferFacetCombiner(setOf(FacetType.WORLD_NORMAL), "normal", ColorFormat.RGB, ColorType.FLOAT16) {
    override fun generateShader(): String = "o_$targetOutput = vec4(v_worldNormal.rgb, 1.0);"
}

class ViewPositionFacet : ColorBufferFacetCombiner(setOf(FacetType.VIEW_POSITION), "viewPosition", ColorFormat.RGB, ColorType.FLOAT32) {
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
    vec3 oofinalColor =  (f_diffuse.rgb + f_specular.rgb + f_emission.rgb) * (1.0 - f_fog.a) + f_fog.rgb * f_fog.a;
    o_$targetOutput.rgba = pow(vec4(oofinalColor, 1.0), vec4(1.0/2.2));
    o_$targetOutput.a = f_alpha;
    """
}

class RenderPass(val combiners: List<FacetCombiner>, val renderOpaque: Boolean = true, val renderTransparent: Boolean = false)

val DefaultPass = RenderPass(listOf(LDRColorFacet()))
val LightPass = RenderPass(emptyList())
val VSMLightPass = RenderPass(listOf(MomentsFacet()))

fun createPassTarget(pass: RenderPass, width: Int, height: Int, depthFormat: DepthFormat = DepthFormat.DEPTH24, multisample: BufferMultisample = BufferMultisample.Disabled): RenderTarget {
    return renderTarget(width, height, multisample = multisample) {
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

    class Configuration {
        var multisampleLines = false
    }

    val configuration = Configuration()

    val blur = ApproximateGaussianBlur()

    var shadowLightTargets = mutableMapOf<ShadowLight, RenderTarget>()
    var meshCubemaps = mutableMapOf<Mesh, Cubemap>()

    var cubemapDepthBuffer = depthBuffer(256, 256, DepthFormat.DEPTH16, BufferMultisample.Disabled)

    var outputPasses = mutableListOf(DefaultPass)
    var outputPassTarget: RenderTarget? = null
    var outputPassTargetMS: RenderTarget? = null

    val postSteps = mutableListOf<PostStep>()
    val buffers = mutableMapOf<String, ColorBuffer>()

    var drawFinalBuffer = true

    fun draw(drawer: Drawer, scene: Scene) {
        drawer.pushStyle()
        drawer.depthWrite = true
        drawer.depthTestPass = DepthTestPass.LESS_OR_EQUAL

        drawer.cullTestPass = CullTestPass.FRONT
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
        val lineMeshes = scene.root.findContent { this as? LineMesh }
        val environmentMapMeshes = meshes.filter { (it.content.material as? BasicMaterial)?.environmentMap == true }
        val signedDistanceFunctions = scene.root.findContent { this as? RayMarcher }

        run {
            lights.filter { it.content is ShadowLight && (it.content as ShadowLight).shadows is Shadows.MappedShadows }.forEach {
                val shadowLight = it.content as ShadowLight
                val pass: RenderPass
                pass = when (shadowLight.shadows) {
                    is Shadows.PCF, is Shadows.Simple -> {
                        LightPass
                    }
                    is Shadows.VSM -> {
                        VSMLightPass
                    }
                    else -> TODO()
                }
                val target = shadowLightTargets.getOrPut(shadowLight) {
                    val mapSize = (shadowLight.shadows as Shadows.MappedShadows).mapSize
                    createPassTarget(pass, mapSize, mapSize, DepthFormat.DEPTH16)
                }
                target.clearDepth(depth = 1.0)

                val look = shadowLight.view(it.node)
                val materialContext = MaterialContext(pass, lights, fogs, shadowLightTargets, emptyMap())
                drawer.isolatedWithTarget(target) {
                    drawer.projection = shadowLight.projection(target)
                    drawer.view = look
                    drawer.model = Matrix44.IDENTITY

                    drawer.background(ColorRGBa.BLACK)
                    drawer.cullTestPass = CullTestPass.FRONT
                    drawPass(drawer, pass, materialContext, meshes, instancedMeshes, lineMeshes, signedDistanceFunctions)
                }
                when (shadowLight.shadows) {
                    is Shadows.VSM -> {
                        blur.gain = 1.0
                        blur.sigma = 3.0
                        blur.window = 9
                        blur.spread = 1.0
                        blur.apply(target.colorBuffer(0), target.colorBuffer(0))
                    }
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
                    drawer.background(ColorRGBa.BLACK)
                    drawer.perspective(90.0, 1.0, 0.1, 100.0)
                    drawer.view = Matrix44.IDENTITY
                    drawer.model = Matrix44.IDENTITY
                    drawer.lookAt(position, position + side.forward, side.up)
                    drawPass(drawer, pass, materialContext, meshes - content, instancedMeshes, lineMeshes, signedDistanceFunctions)
                }
                cubemap.generateMipmaps()
                target.detachColorBuffers()
                target.detachDepthBuffer()
                target.destroy()
            }
        }

        run {
            //val pass = outputPasses
            for (pass in outputPasses) {
                val materialContext = MaterialContext(pass, lights, fogs, shadowLightTargets, meshCubemaps)

                if ((pass != DefaultPass || postSteps.isNotEmpty()) && outputPassTarget == null) {
                    outputPassTarget = createPassTarget(pass, RenderTarget.active.width, RenderTarget.active.height)
                }

                if (pass == outputPasses[0]) {
                    outputPassTarget?.let {
                        drawer.withTarget(it) {
                            background(ColorRGBa.PINK)
                        }
                    }
                }
                outputPassTarget?.let { target ->
                    pass.combiners.forEach {
                        if (it is ColorBufferFacetCombiner) {
                            val index = target.colorBufferIndex(it.targetOutput)
                            target.blendMode(index, it.blendMode)
                        }
                    }
                }
                outputPassTarget?.bind()
                drawPass(drawer, pass, materialContext, meshes, instancedMeshes, lineMeshes, signedDistanceFunctions)
                val drawNodes = scene.root.findNodes { drawFunction != null }
                outputPassTarget?.unbind()

                drawNodes.forEach {
                    drawer.isolated {
                        drawer.model = it.worldTransform
                        it.drawFunction?.invoke(drawer)
                    }
                }

                outputPassTarget?.let { output ->
                    for (combiner in pass.combiners) {
                        buffers[combiner.targetOutput] = output.colorBuffer(combiner.targetOutput)
                    }
                }
            }
            val lightContext = LightContext(lights, shadowLightTargets)
            val postContext = PostContext(lightContext, drawer.view.inversed)

            for (postStep in postSteps) {
                if (postStep is FilterPostStep) {
                    if (postStep.filter is Ssao) {
                        postStep.filter.projection = drawer.projection
                    }
                    if (postStep.filter is Sslr) {
                        val p = Matrix44.scale(drawer.width / 2.0, drawer.height / 2.0, 1.0) * Matrix44.translate(Vector3(1.0, 1.0, 0.0)) * drawer.projection
                        postStep.filter.projection = p
                    }
                }
                postStep.apply(buffers, postContext)
            }

        }

        drawer.popStyle()
        if (drawFinalBuffer) {
            outputPassTarget?.let { output ->
                drawer.isolated {
                    drawer.ortho()
                    drawer.view = Matrix44.IDENTITY
                    drawer.model = Matrix44.IDENTITY
                    val outputName = (postSteps.last() as FilterPostStep).output
                    val outputBuffer = buffers[outputName]
                            ?: throw IllegalArgumentException("can't find $outputName buffer")
                    drawer.image(outputBuffer)
                }
            }
        }
    }

    private fun drawPass(drawer: Drawer, pass: RenderPass, materialContext: MaterialContext,
                         meshes: List<NodeContent<Mesh>>,
                         instancedMeshes: List<NodeContent<InstancedMesh>>,
                         lineMeshes: List<NodeContent<LineMesh>>,
                         signedDistanceFunctions: List<NodeContent<RayMarcher>>) {



        // -- draw all meshes
        meshes
                .filter { (it.content.material.transparent && pass.renderTransparent) || (!it.content.material.transparent && pass.renderOpaque) }
                .forEach {
            val mesh = it.content
            drawer.isolated {
                if (mesh.material.doubleSided) {
                    drawer.drawStyle.cullTestPass = CullTestPass.ALWAYS
                }

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

        // -- draw all instanced meshes
        instancedMeshes
                .filter { (it.content.material.transparent && pass.renderTransparent) || (!it.content.material.transparent && pass.renderOpaque) }
                .forEach {
            val mesh = it.content
            drawer.isolated {
                val shadeStyle = mesh.material.generateShadeStyle(materialContext)
                shadeStyle.parameter("viewMatrixInverse", drawer.view.inversed)
                mesh.material.applyToShadeStyle(materialContext, shadeStyle, mesh)
                if (mesh.material.doubleSided) {
                    drawer.drawStyle.cullTestPass = CullTestPass.ALWAYS
                }
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


        val effectiveLineMeshes = lineMeshes.filter { (it.content.material.transparent && pass.renderTransparent) || (!it.content.material.transparent && pass.renderOpaque) }


        // -- draw all line meshes
        if (effectiveLineMeshes.isNotEmpty()) {
            // -- determine if line meshes will be rendered on a multi sample target
            val needMS = (pass in outputPasses) && configuration.multisampleLines //(materialContext.pass.combiners.any { FacetType.DIFFUSE in it.facets })
            if (needMS) {
                if (outputPassTargetMS == null) {
                    outputPassTargetMS = createPassTarget(pass, outputPassTarget!!.width, outputPassTarget!!.height, multisample = BufferMultisample.SampleCount(8))
                }
                outputPassTarget!!.resolveTo(outputPassTargetMS!!)
                outputPassTargetMS!!.bind()
            }

            effectiveLineMeshes.forEach {
                val mesh = it.content
                drawer.isolated {
                    val shadeStyle = mesh.material.generateShadeStyle(materialContext)
                    shadeStyle.parameter("viewMatrixInverse", drawer.view.inversed)
                    drawer.drawStyle.cullTestPass = CullTestPass.ALWAYS
                    mesh.material.applyToShadeStyle(materialContext, shadeStyle, mesh)
                    drawer.shadeStyle = shadeStyle
                    drawer.model = it.node.worldTransform
                    drawer.lineStrips(it.content.segments, it.content.weights, it.content.colors)
                }
            }

            if (needMS) {
                outputPassTargetMS!!.unbind()
                outputPassTargetMS!!.resolveTo(outputPassTarget!!)
            }
        }

        // -- draw all signed distance functions
        run {
            signedDistanceFunctions
                    .filter { (it.content.material.transparent && pass.renderTransparent) || (!it.content.material.transparent && pass.renderOpaque) }
                    .forEach {
                val art = RenderTarget.active

                if (art.hasColorBuffer) {
                    val sdf = it.content
                    val shadeStyle = sdf.generateShadeStyle(materialContext)

                    shadeStyle.parameter("viewMatrixInverse", drawer.view.inversed)
                    shadeStyle.parameter("projectionMatrixInverse", drawer.projection.inversed)

                    val tempDepth = depthBuffer(art.width, art.height, format = DepthFormat.DEPTH24, multisample = BufferMultisample.Disabled)

                    art.depthBuffer!!.copyTo(tempDepth)
                    shadeStyle.parameter("depthBuffer", art.depthBuffer!!)
                    sdf.material.applyToShadeStyle(materialContext, shadeStyle, sdf)


                    val depthBufferOut = RenderTarget.active.depthBuffer

                    val filter = ShadeStyleFilter(shadeStyle)
                    depthBufferOut?.let {
                        filter.depthBufferOut = it
                    }

                    filter.shader.begin()
                    drawer.context.applyToShader(filter.shader)
                    filter.shader.end()

                    filter.apply(emptyArray(), RenderTarget.active.colorBuffers.toTypedArray())
                    tempDepth.destroy()
                }
            }

        }

    }
}

fun sceneRenderer(builder: SceneRenderer.() -> Unit): SceneRenderer {
    val sceneRenderer = SceneRenderer()
    sceneRenderer.builder()
    return sceneRenderer
}