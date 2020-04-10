@file:Suppress("unused")

package org.openrndr.dnky.post

import org.openrndr.draw.Filter
import org.openrndr.draw.Shader
import org.openrndr.math.Matrix44
import org.openrndr.math.Vector4
import org.openrndr.math.mix

class Ssao : Filter(Shader.createFromCode(filterVertexCode, filterFragmentCode("ssao.frag"),"ssao")) {
    var projection: Matrix44 by parameters
    var positions: Int by parameters
    var normals: Int by parameters

    var radius: Double by parameters

    init {
        val poissonSamples = Array(64) { Vector4.ZERO }
        for (i in 0..63) {
            var scale = i / 63.0
            scale = mix(0.1, 1.0, scale * scale)
            poissonSamples[i] = Vector4(Math.random() * 2 - 1, Math.random() * 2 - 1, Math.random(), 0.0).normalized * scale
        }
        parameters["poissonSamples"] = poissonSamples
        positions = 0
        normals = 1
        projection = Matrix44.IDENTITY
        radius = 1.0
    }
}


class OcclusionBlur : Filter(Shader.createFromCode(filterVertexCode, filterFragmentCode("occlusion-blur.frag"),"occlusion-blur")) {
    var occlusion: Int by parameters
    var positions: Int by parameters
    var normals: Int by parameters

    init {
        occlusion = 0
        positions = 1
        normals = 2
    }
}