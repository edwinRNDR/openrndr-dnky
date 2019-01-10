package org.openrndr.dnky.post

import org.openrndr.draw.Filter
import org.openrndr.draw.Shader
import org.openrndr.math.Matrix44

class Sslr : Filter(Shader.createFromCode(Filter.filterVertexCode, filterFragmentCode("sslr.frag"))) {
    var projection: Matrix44 by parameters
    var colors: Int by parameters
    var positions: Int by parameters
    var normals: Int by parameters
    var material: Int by parameters
    var baseColors: Int by parameters

    var jitterOriginGain: Double by parameters
    var iterationLimit: Int by parameters
    var distanceLimit: Double by parameters
    var gain: Double by parameters
    var borderWidth: Double by parameters

    init {
        colors = 0
        positions = 1
        normals = 2
        material = 3

        projection = Matrix44.IDENTITY

        distanceLimit = 1280.0 / 20.0
        iterationLimit = 128
        jitterOriginGain = 0.0

        gain = 1.0
        borderWidth = 130.0
    }
}

class SslrCombiner : Filter(Shader.createFromCode(Filter.filterVertexCode, filterFragmentCode("sslr-combiner.frag"))) {

    var colors: Int by parameters
    var reflections: Int by parameters
    var positions: Int by parameters
    var normals: Int by parameters
    var materials: Int by parameters
    var baseColors: Int by parameters

    init {
        colors = 0
        reflections = 1
        positions = 2
        normals = 3
        materials = 4
        baseColors = 5
    }

}
