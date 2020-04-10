package org.openrndr.dnky.post

import org.openrndr.color.ColorRGBa
import org.openrndr.draw.Filter
import org.openrndr.draw.Shader

class LinearFog : Filter(Shader.createFromCode(Filter.filterVertexCode, filterFragmentCode("linear-fog.frag"),"linear-fog")) {
    var start: Double by parameters
    var end: Double by parameters
    var color: ColorRGBa by parameters

    init {
        start = 50.0
        end = 150.0
        color = ColorRGBa.BLACK.toLinear()
    }
}