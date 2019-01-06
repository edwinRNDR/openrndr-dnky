package org.openrndr.dnky.post

import org.openrndr.draw.Filter
import org.openrndr.draw.Shader

class TonemapUncharted2 : Filter(Shader.createFromCode(
        Filter.filterVertexCode,
        filterFragmentCode("tonemap-uncharted2.frag")))
