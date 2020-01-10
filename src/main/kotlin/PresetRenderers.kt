package org.openrndr.dnky

import FilmGrain
import org.openrndr.color.ColorRGBa
import org.openrndr.dnky.post.*
import org.openrndr.draw.*
import post.*

class PhotographicRenderer(val renderer: SceneRenderer) {
    var exposure = 1.0
    var focalPlane = 100.0
    var aperture = 1.0

    var fogDensity = 0.01
    var fogPower = 1.0
    var fogColor = ColorRGBa.GRAY

    var aberrationConstant = 0.0
    var aberrationLinear = 0.0
    var aberrationBlendConstant = 0.0
    var aberrationBlendLinear = 0.0

    var filmGrainGain = 0.1
}

/**
 * This constructs and configures a 'photographic' renderer.
 * The photographic renderer uses screen space ambient occlusion, screen space reflections
 * depth of field and tone mapping post processing steps.
 */
fun photographicRenderer(volumetricPost:Boolean = false): PhotographicRenderer {
    val sr = SceneRenderer()

    val pr = PhotographicRenderer(sr)
    sr.apply {
        // -- setup render facets
        outputPasses = mutableListOf(RenderPass(listOf(
                DiffuseSpecularFacet(),
                EmissiveFacet(),
                MaterialFacet(),
                BaseColorFacet(),
                AmbientOcclusionFacet(),
                ViewPositionFacet(),
                ViewNormalFacet()
        )), RenderPass(listOf(
                DiffuseSpecularAlphaFacet(),
                EmissiveAlphaFacet(),
                MaterialFacet(),
                BaseColorFacet(),
                AmbientOcclusionFacet(),
                ViewPositionFacet(),
                ViewNormalFacet()
        ), renderOpaque = false, renderTransparent = true))

        postSteps += FilterPostStep(0.5, BloomDownscale(), listOf("emissive"), "bloom-1", ColorFormat.RGBa, ColorType.FLOAT16)
        postSteps += FilterPostStep(1.0, ApproximateGaussianBlur(), listOf("bloom-1"), "bloom-1", ColorFormat.RGBa, ColorType.FLOAT16)

        for (i in 1 until 6) {
            postSteps += FilterPostStep(0.5, BloomDownscale(), listOf("bloom-$i"), "bloom-${i+1}", ColorFormat.RGBa, ColorType.FLOAT16)
            postSteps += FilterPostStep(1.0, ApproximateGaussianBlur(), listOf("bloom-${i+1}"), "bloom-${i+1}", ColorFormat.RGBa, ColorType.FLOAT16)
        }
        postSteps += FilterPostStep(2.0, BloomUpscale(), (1..6).map { "bloom-$it"}, "bloom", ColorFormat.RGBa, ColorType.FLOAT16)
        //postSteps += FilterPostStep(1.0, BloomCombine(), listOf("emissive", "bloom"), "emissive", ColorFormat.RGBa, ColorType.FLOAT16)


        // -- insert Screen-space ambient occlusions step at half-scale
        postSteps += FilterPostStep(0.5, Ssao(), listOf("viewPosition", "viewNormal"), "ssao", ColorFormat.R, ColorType.FLOAT16)

        // -- insert occlusion denoiser + upscaler
        postSteps += FilterPostStep(2.0, OcclusionBlur(), listOf("ssao", "viewPosition", "viewNormal"), "ssao-4x", ColorFormat.R, ColorType.FLOAT16)

        // --
        postSteps += FilterPostStep(1.0, PostCombiner(), listOf("diffuseSpecular", "emissive", "ssao-4x", "ambientOcclusion"), "combined", ColorFormat.RGB, ColorType.FLOAT16)


        // -- gaussian pyramid for reflections
        postSteps += FilterPostStep(0.5, BloomDownscale(), listOf("combined"), "combined-1", ColorFormat.RGBa, ColorType.FLOAT16)
        postSteps += FilterPostStep(1.0, ApproximateGaussianBlur(), listOf("combined-1"), "combined-1", ColorFormat.RGBa, ColorType.FLOAT16)

        for (i in 1 until 6) {
            postSteps += FilterPostStep(0.5, BloomDownscale(), listOf("combined-$i"), "combined-${i+1}", ColorFormat.RGB, ColorType.FLOAT16)
            //postSteps += FilterPostStep(1.0, ApproximateGaussianBlur(), listOf("combined-${i+1}"), "combined-${i+1}", ColorFormat.RGBa, ColorType.FLOAT16)
        }

        postSteps += postStep {
            val source = it.getValue("combined")
            val target = it.getOrPut("pyramid") {
                colorBuffer(source.width, source.height, format = ColorFormat.RGB, type = ColorType.FLOAT16, levels = 6).apply {
                }
            }
            target.filter(MinifyingFilter.LINEAR, MagnifyingFilter.LINEAR)
            it.getValue("combined").copyTo(target, 0, 0)
            it.getValue("combined-1").copyTo(target, 0, 1)
            it.getValue("combined-2").copyTo(target, 0, 2)
            it.getValue("combined-3").copyTo(target, 0, 3)
            it.getValue("combined-4").copyTo(target, 0, 4)
            it.getValue("combined-5").copyTo(target, 0, 5)

            target.filter(MinifyingFilter.LINEAR_MIPMAP_LINEAR, MagnifyingFilter.LINEAR)
            //target.generateMipmaps()
        }


        // -- insert screen-space local reflections step
        postSteps += FilterPostStep(1.0, Sslr(), listOf("pyramid", "viewPosition", "viewNormal", "material"), "reflection", ColorFormat.RGB, ColorType.FLOAT16)

        // -- insert reflection combiner step
        postSteps += FilterPostStep(1.0, SslrCombiner(),
                listOf("combined", "reflection", "viewPosition", "viewNormal", "material", "baseColor" ),
                "reflection-combined", ColorFormat.RGB, ColorType.FLOAT16)

        // -- insert exponential fog step
        postSteps += postStep(ExponentialFog()) {
            inputs += "reflection-combined"
            inputs += "viewPosition"
            output = "fog"
            outputFormat = ColorFormat.RGBa
            outputType = ColorType.FLOAT16
            update = {
                density = pr.fogDensity
                power = pr.fogPower
                color = pr.fogColor
            }
        }

        if (volumetricPost) {
            postSteps += postStep(VolumetricLights()) {
                inputs += "fog"
                inputs += "viewPosition"
                output = "fog"

                outputFormat = ColorFormat.RGBa
                outputType = ColorType.FLOAT16
                update = {
                    context = it.lightContext
                    inverseViewMatrix = it.inverseViewMatrix
                }
            }
        }

        // -- insert DOF pre-process step
        postSteps += postStep(PositionToCoc()) {
            inputs += "fog"
            inputs += "viewPosition"
            output = "cocImage"
            outputFormat = ColorFormat.RGBa
            outputType = ColorType.FLOAT16
            update = {
                exposure = 1.0
                focalPlane = pr.focalPlane
                aperture = pr.aperture
                aberrationLinear = pr.aberrationLinear
                aberrationBlendLinear = pr.aberrationBlendLinear
                aberrationConstant = pr.aberrationConstant
                aberrationBlendConstant = pr.aberrationBlendConstant
            }
        }

        // -- insert DOF process step
        postSteps += FilterPostStep(1.0, HexDof(), listOf("cocImage"), "dof", ColorFormat.RGBa, ColorType.FLOAT16)

        // -- insert film-grain step, before tone mapping
        postSteps += postStep(FilmGrain()) {
            inputs += "dof"
            output = "dof"
            outputFormat = ColorFormat.RGBa
            outputType = ColorType.FLOAT16
            update = {
                time = Math.random()*10000
                this.grainStrength = pr.filmGrainGain
            }
        }

        // -- insert tone mapping step
        postSteps += postStep(TonemapUncharted2()) {
            inputs += "dof"
            output = "ldr"
            outputFormat = ColorFormat.RGBa
            outputType = ColorType.UINT8
            update = {
                exposureBias = pr.exposure
            }
        }

        // -- insert anti-aliasing step, through FXAA
        postSteps += FilterPostStep(1.0, FXAA(), listOf("ldr"), "aa", ColorFormat.RGBa, ColorType.UINT8)
    }
    return pr
}