package org.openrndr.dnky.post

import org.openrndr.draw.Filter
import org.openrndr.draw.Shader

class PostCombiner : Filter(Shader.createFromCode(filterVertexCode, filterFragmentCode("post-combiner.frag"))) {
    var diffuseSpecular: Int by parameters
    var emissive: Int by parameters
    var occlusion: Int by parameters

    init {
        diffuseSpecular = 0
        emissive = 1
        occlusion = 2
    }
}