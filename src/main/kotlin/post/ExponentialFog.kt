package org.openrndr.dnky.post

import org.openrndr.color.ColorRGBa
import org.openrndr.draw.Filter
import org.openrndr.draw.Shader

class ExponentialFog : Filter(Shader.createFromCode(Filter.filterVertexCode, filterFragmentCode("exponential-fog.frag"))) {
    var density: Double by parameters
    var power: Double by parameters
    var color: ColorRGBa by parameters

    init {
        density = 0.01
        power = 1.0
        color = ColorRGBa.BLACK.toLinear()
    }
}