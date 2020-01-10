package org.openrndr.dnky.post

import org.openrndr.dnky.PostContext
import org.openrndr.draw.*

interface PostStep {
    fun apply(buffers: MutableMap<String, ColorBuffer>, postContext: PostContext)
}

class FilterPostStep(val outputScale: Double,
               val filter: Filter,
               val inputs: List<String>,
               val output: String,
               val outputFormat: ColorFormat,
               val outputType: ColorType,
               val update: (Filter.(PostContext) -> Unit)? = null) : PostStep {

    override fun apply(buffers: MutableMap<String, ColorBuffer>, postContext: PostContext) {
        val inputBuffers = inputs.map { buffers[it]!! }
        val outputBuffer = buffers.getOrPut(output) {
            colorBuffer((inputBuffers[0].width * outputScale).toInt(),
                    (inputBuffers[0].height * outputScale).toInt(),
                    format = outputFormat,
                    type = outputType)
        }
        update?.invoke(filter, postContext)
        filter.apply(inputBuffers.toTypedArray(), outputBuffer)
    }
}

class FunctionPostStep(val function:(MutableMap<String, ColorBuffer>)->Unit) : PostStep {
    override fun apply(buffers: MutableMap<String, ColorBuffer>, postContext: PostContext) {
        function(buffers)
    }
}

class FilterPostStepBuilder<T : Filter>(val filter: T) {
    var outputScale = 1.0
    val inputs = mutableListOf<String>()
    var output = "untitled"
    var outputFormat = ColorFormat.RGBa
    var outputType = ColorType.UINT8
    var update: (T.(PostContext) -> Unit)? = null

    internal fun build(): PostStep {
        @Suppress("UNCHECKED_CAST", "PackageDirectoryMismatch")
        return FilterPostStep(outputScale, filter, inputs, output, outputFormat, outputType, update as (Filter.(PostContext) -> Unit)?)
    }
}

fun <T : Filter> postStep(filter: T, configure: FilterPostStepBuilder<T>.() -> Unit) : PostStep {
    val psb = FilterPostStepBuilder(filter)
    psb.configure()
    return psb.build()
}

fun postStep(function: (MutableMap<String,ColorBuffer>)->Unit) : PostStep {
    return FunctionPostStep(function)
}
