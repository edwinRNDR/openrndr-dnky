package post

import org.openrndr.color.ColorRGBa
import org.openrndr.dnky.post.filterFragmentCode
import org.openrndr.draw.Filter
import org.openrndr.draw.Shader

class BloomDownscale : Filter(Shader.createFromCode(filterVertexCode, filterFragmentCode("bloom-downscale.frag"),""))


class BloomUpscale : Filter(Shader.createFromCode(filterVertexCode, filterFragmentCode("bloom-upscale.frag"),"")) {
    var gain:Double by parameters
    var shape:Double by parameters
    var seed:Double by parameters

    init {
        gain = 1.0
        shape = 1.0
        seed = 1.0
    }
}

class BloomCombine: Filter(Shader.createFromCode(filterVertexCode, filterFragmentCode("bloom-combine.frag"),"")) {
    var gain: Double by parameters
    var bias: ColorRGBa by parameters

    init {
        bias = ColorRGBa.BLACK
        gain = 1.0
    }
}

