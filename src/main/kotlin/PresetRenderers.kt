package org.openrndr.dnky

import org.openrndr.dnky.post.*
import org.openrndr.draw.ColorFormat
import org.openrndr.draw.ColorType

class PhotographicRenderer(val renderer:SceneRenderer) {
    var exposure = 1.0
    var focalPlane = 100.0
    var aperture = 1.0
}

/**
 * This constructs and configures a 'photographic' renderer.
 * The photographic renderer uses screen space ambient occlusion, screen space reflections
 * depth of field and tone mapping post processing steps.
 */
fun photographicRenderer() : PhotographicRenderer {
    val sr = SceneRenderer()

    val pr = PhotographicRenderer(sr)
    sr.apply {
        outputPass = RenderPass(listOf(
                DiffuseSpecularFacet(),
                EmissiveFacet(),
                ViewPositionFacet(),
                ViewNormalFacet()
        ))
        postSteps += PostStep(0.5, Ssao(), listOf("viewPosition", "viewNormal"), "ssao", ColorFormat.R, ColorType.FLOAT16)
        postSteps += PostStep(2.0, OcclusionBlur(), listOf("ssao", "viewPosition", "viewNormal"), "ssao-4x", ColorFormat.R, ColorType.FLOAT16)
        postSteps += PostStep(1.0, PostCombiner(), listOf("diffuseSpecular", "emissive", "ssao-4x"), "combined", ColorFormat.RGB, ColorType.FLOAT16)
        postSteps += PostStep(1.0, Sslr(), listOf("combined", "viewPosition", "viewNormal"), "reflection", ColorFormat.RGB, ColorType.FLOAT16)
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
                exposure = pr.exposure
                focalPlane = pr.focalPlane
                aperture = pr.aperture
            }
        }

        postSteps += PostStep(1.0, HexDof(), listOf("cocImage"), "dof", ColorFormat.RGBa, ColorType.FLOAT16)
        postSteps += PostStep(1.0, TonemapUncharted2(), listOf("dof"), "ldr", ColorFormat.RGBa, ColorType.UINT8)
    }


    return pr
}