package org.openrndr.dnky

import org.openrndr.draw.ColorBuffer
import org.openrndr.draw.ColorFormat
import org.openrndr.draw.ColorType
import org.openrndr.draw.colorBuffer
import org.openrndr.resourceUrl
import java.net.URL
import java.nio.Buffer
import java.nio.ByteBuffer
import java.nio.ByteOrder

fun createLtcMatTexture(): ColorBuffer {
    val cb = colorBuffer(32, 32, 1.0, ColorFormat.RGBa, ColorType.FLOAT32)
    val fb = ByteBuffer.allocateDirect(32*32*4*4)
    fb.order(ByteOrder.nativeOrder())
    (fb as Buffer).rewind()

    val bytes = URL(resourceUrl("/org/openrndr/dnky/ltc/ltc_mat.bin")).readBytes()
    fb.put(bytes)

    cb.write(fb)
    return cb
}

fun createLtcMagTexture(): ColorBuffer {
    val cb = colorBuffer(32, 32, 1.0, ColorFormat.RG, ColorType.FLOAT32)
    val fb = ByteBuffer.allocateDirect(32*32*2*4)
    fb.order(ByteOrder.nativeOrder())
    (fb as Buffer).rewind()

    val bytes = URL(resourceUrl("/org/openrndr/dnky/ltc/ltc_amp.bin")).readBytes()
    fb.put(bytes)

    cb.write(fb)
    return cb
}
