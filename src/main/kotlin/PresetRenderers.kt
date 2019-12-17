package org.openrndr.dnky

import FilmGrain
import org.openrndr.color.ColorRGBa
import org.openrndr.dnky.post.*
import org.openrndr.draw.ColorFormat
import org.openrndr.draw.ColorType
import post.VolumetricLights

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
        outputPass = RenderPass(listOf(
                DiffuseSpecularFacet(),
                EmissiveFacet(),
                MaterialFacet(),
                BaseColorFacet(),
                ViewPositionFacet(),
                ViewNormalFacet()
        ))
        postSteps += PostStep(0.5, Ssao(), listOf("viewPosition", "viewNormal"), "ssao", ColorFormat.R, ColorType.FLOAT16)
        postSteps += PostStep(2.0, OcclusionBlur(), listOf("ssao", "viewPosition", "viewNormal"), "ssao-4x", ColorFormat.R, ColorType.FLOAT16)
        postSteps += PostStep(1.0, PostCombiner(), listOf("diffuseSpecular", "emissive", "ssao-4x"), "combined", ColorFormat.RGB, ColorType.FLOAT16)
        postSteps += PostStep(1.0, Sslr(), listOf("combined", "viewPosition", "viewNormal", "material"), "reflection", ColorFormat.RGB, ColorType.FLOAT16)
//        postSteps += PostStep(1.0, PositionToCoc(), listOf("reflection", "viewPosition"), "cocImage", ColorFormat.RGBa, ColorType.FLOAT16) {
//
//        }
        postSteps += PostStep(1.0, SslrCombiner(),
                listOf("combined", "reflection", "viewPosition", "viewNormal", "material", "baseColor" ),
                "reflection-combined", ColorFormat.RGB, ColorType.FLOAT16)


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

        postSteps += PostStep(1.0, HexDof(), listOf("cocImage"), "dof", ColorFormat.RGBa, ColorType.FLOAT16)

        postSteps += postStep(FilmGrain()) {
            inputs += "dof"
            output = "dof"
            outputFormat = ColorFormat.RGBa
            outputType = ColorType.FLOAT16
            update = {
                time = Math.random()*10000
                this.grainStrength = 0.2
            }
        }

        postSteps += postStep(TonemapUncharted2()) {
            inputs += "dof"
            output = "ldr"
            outputFormat = ColorFormat.RGBa
            outputType = ColorType.UINT8
            update = {
                exposureBias = pr.exposure
            }
        }

        postSteps += PostStep(1.0, FXAA(), listOf("ldr"), "aa", ColorFormat.RGBa, ColorType.UINT8)
    }
    return pr
}