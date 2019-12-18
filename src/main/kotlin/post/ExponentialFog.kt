package org.openrndr.dnky.post

import org.openrndr.color.ColorRGBa
import org.openrndr.draw.Filter
import org.openrndr.draw.Shader

/**
 * Exponential fog filter
 */
class ExponentialFog : Filter(Shader.createFromCode(filterVertexCode, filterFragmentCode("exponential-fog.frag"))) {
    var density: Double by parameters
    var power: Double by parameters

    /**
     * color of the fog
     */
    var color: ColorRGBa by parameters

    init {
        density = 0.01
        power = 1.0
        color = ColorRGBa.BLACK.toLinear()
    }
}