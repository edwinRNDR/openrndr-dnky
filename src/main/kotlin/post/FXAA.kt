package org.openrndr.dnky.post

import org.openrndr.draw.Filter
import org.openrndr.draw.Shader

class FXAA : Filter(Shader.createFromCode(Filter.filterVertexCode, filterFragmentCode("fxaa.frag"),"fxaa")) {

    var lumaThreshold: Double by parameters
    var maxSpan: Double by parameters
    var directionReduceMultiplier:Double by parameters

    var directionReduceMinimum:Double by parameters

    init {
        lumaThreshold = 0.5
        maxSpan = 8.0
        directionReduceMinimum = 0.0
        directionReduceMultiplier = 0.0
    }
}