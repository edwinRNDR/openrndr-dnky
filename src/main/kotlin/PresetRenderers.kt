package org.openrndr.dnky

import org.openrndr.color.ColorRGBa
import org.openrndr.dnky.post.*
import org.openrndr.draw.ColorFormat
import org.openrndr.draw.ColorType

class PhotographicRenderer(val renderer: SceneRenderer) {
    var exposure = 1.0
    var focalPlane = 100.0
    var aperture = 1.0

    var fogStart = 50.0
    var fogEnd = 150.0
    var fogColor = ColorRGBa.GRAY
}

/**
 * This constructs and configures a 'photographic' renderer.
 * The photographic renderer uses screen space ambient occlusion, screen space reflections
 * depth of field and tone mapping post processing steps.
 */
fun photographicRenderer(): PhotographicRenderer {
    val sr = SceneRenderer()

    val pr = PhotographicRenderer(sr)
    sr.apply {
        outputPass = RenderPass(listOf(
                DiffuseSpecularFacet(),
                EmissiveFacet(),
                MaterialFacet(),
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
        postSteps += postStep(PositionToCoc()) {
            inputs += "reflection"
            inputs += "viewPosition"
            output = "cocImage"
            outputFormat = ColorFormat.RGBa
            outputType = ColorType.FLOAT16
            update = {
                exposure = 1.0
                focalPlane = pr.focalPlane
                aperture = pr.aperture
            }
        }

        postSteps += PostStep(1.0, HexDof(), listOf("cocImage"), "dof", ColorFormat.RGBa, ColorType.FLOAT16)
        postSteps += postStep(LinearFog()) {
            inputs += "dof"
            inputs += "viewPosition"
            output = "fog"
            outputFormat = ColorFormat.RGBa
            outputType = ColorType.FLOAT16
            update = {
                end = pr.fogEnd
                start = pr.fogStart
                color = pr.fogColor
            }
        }
        postSteps += postStep(TonemapUncharted2()) {
            inputs += "fog"
            output = "ldr"
            outputFormat = ColorFormat.RGBa
            outputType = ColorType.UINT8
            update = {
                exposureBias = pr.exposure
            }
        }
    }
    return pr
}