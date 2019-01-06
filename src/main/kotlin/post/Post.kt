package org.openrndr.dnky.post

import org.openrndr.draw.*

class PostStep(val outputScale: Double,
               val filter: Filter,
               val inputs: List<String>,
               val output: String,
               val outputFormat: ColorFormat,
               val outputType: ColorType,
               val update: (Filter.() -> Unit)? = null) {

    fun apply(buffers: MutableMap<String, ColorBuffer>) {
        val inputBuffers = inputs.map { buffers[it]!! }
        val outputBuffer = buffers.getOrPut(output) {
            colorBuffer((inputBuffers[0].width * outputScale).toInt(),
                    (inputBuffers[0].height * outputScale).toInt(),
                    format = outputFormat,
                    type = outputType)
        }
        update?.invoke(filter)
        filter.apply(inputBuffers.toTypedArray(), outputBuffer)
    }
}

class PostStepBuilder<T : Filter>(val filter: T) {
    var outputScale = 1.0
    val inputs = mutableListOf<String>()
    var output = "untitled"
    var outputFormat = ColorFormat.RGBa
    var outputType = ColorType.UINT8
    var update: (T.() -> Unit)? = null

    internal fun build(): PostStep {
        return PostStep(outputScale, filter, inputs, output, outputFormat, outputType, update as (Filter.() -> Unit)?)
    }
}

fun <T : Filter> postStep(filter: T, configure: PostStepBuilder<T>.() -> Unit) : PostStep {
    val psb = PostStepBuilder(filter)
    psb.configure()
    return psb.build()
}
