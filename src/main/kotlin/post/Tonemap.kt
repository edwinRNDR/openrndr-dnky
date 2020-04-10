package org.openrndr.dnky.post

import org.openrndr.draw.Filter
import org.openrndr.draw.Shader

class TonemapUncharted2 : Filter(Shader.createFromCode(
        Filter.filterVertexCode,
        filterFragmentCode("tonemap-uncharted2.frag"),"tonemap-uncharted2")) {

    var exposureBias: Double by parameters

    init {
        exposureBias = 16.0
    }
}

class TonemapAces : Filter(Shader.createFromCode(
        Filter.filterVertexCode,
        filterFragmentCode("tonemap-aces.frag"),"tonemap-aces")) {

    var exposureBias: Double by parameters

    init {
        exposureBias = 1.0
    }
}
