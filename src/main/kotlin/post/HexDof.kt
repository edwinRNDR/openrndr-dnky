@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package org.openrndr.dnky.post

import org.openrndr.draw.*
import org.openrndr.math.Vector2


class HexDof : Filter(Shader.createFromCode(Filter.filterVertexCode, filterFragmentCode("hex-dof-pass-1.frag"))) {
    private var pass1 = Pass1()
    private var pass2 = Pass2()
    private var vertical: ColorBuffer? = null
    private var diagonal: ColorBuffer? = null

    var phase: Double = 0.0
    var samples = 20

    class Pass1 : Filter(Shader.createFromCode(Filter.filterVertexCode, filterFragmentCode("hex-dof-pass-1.frag"))) {
        var image: Int by parameters

        init {
            image = 0
        }
    }

    class Pass2 : Filter(Shader.createFromCode(Filter.filterVertexCode, filterFragmentCode("hex-dof-pass-2.frag"))) {
        var vertical: Int by parameters
        var diagonal: Int by parameters
        var original: Int by parameters

        init {
            vertical = 0
            diagonal = 1
            original = 2
        }
    }

    override fun apply(source: Array<ColorBuffer>, target: Array<ColorBuffer>) {

        if (vertical != null && (vertical!!.width != target[0].width || vertical!!.height != target[0].height)) {
            vertical!!.destroy()
            vertical = null
            diagonal!!.destroy()
            diagonal = null
        }

        if (vertical == null) {
            vertical = ColorBuffer.create(target[0].width, target[0].height, target[0].contentScale, ColorFormat.RGBa, ColorType.FLOAT16)
            diagonal = ColorBuffer.create(target[0].width, target[0].height, target[0].contentScale, ColorFormat.RGBa, ColorType.FLOAT16)
        }

        source[0].filter(MinifyingFilter.LINEAR, MagnifyingFilter.LINEAR) // image


        vertical!!.filter(MinifyingFilter.LINEAR, MagnifyingFilter.LINEAR)
        diagonal!!.filter(MinifyingFilter.LINEAR, MagnifyingFilter.LINEAR)

        pass1.parameters["samples"] = samples
        pass1.parameters["phase"] = phase
        pass1.parameters["vertical"] = Vector2(Math.cos(Math.PI / 2), Math.sin(Math.PI / 2))
        pass1.parameters["diagonal"] = Vector2(Math.cos(-Math.PI / 6), Math.sin(-Math.PI / 6))
        pass1.apply(source[0], arrayOf(vertical!!, diagonal!!))
        pass2.parameters["samples"] = samples
        pass2.parameters["phase"] = phase
        pass2.parameters["direction0"] = Vector2(Math.cos(-Math.PI / 6), Math.sin(-Math.PI / 6))
        pass2.parameters["direction1"] = Vector2(Math.cos(-5 * Math.PI / 6), Math.sin(-5 * Math.PI / 6))
        source[0].filter(MinifyingFilter.LINEAR, MagnifyingFilter.LINEAR)
        pass2.apply(arrayOf(vertical!!, diagonal!!, source[0]), target)
    }
}

class PositionToCoc : Filter(Shader.createFromCode(Filter.filterVertexCode, filterFragmentCode("position-to-coc.frag"))) {
    var minCoc: Double by parameters
    var maxCoc: Double by parameters

    var aperture: Double by parameters
    var focalPlane: Double by parameters
    var exposure: Double by parameters

    var position: Int by parameters

    var aberrationConstant: Double by parameters
    var aberrationLinear: Double by parameters

    var aberrationBlendConstant: Double by parameters
    var aberrationBlendLinear: Double by parameters

    init {
        minCoc = 2.0
        maxCoc = 20.0
        position = 1
        exposure = 1.0

        focalPlane = 4.0
        aperture = 1.0
        aberrationConstant = 1.0
        aberrationLinear = 8.0
        aberrationBlendConstant = 1.0
        aberrationBlendLinear = 1.0
    }
}
