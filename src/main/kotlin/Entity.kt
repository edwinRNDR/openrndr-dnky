package org.openrndr.dnky

import org.openrndr.color.ColorRGBa
import org.openrndr.draw.DrawPrimitive
import org.openrndr.draw.IndexBuffer
import org.openrndr.draw.VertexBuffer


sealed class Entity {

}

class Geometry(val vertexBuffers : List<VertexBuffer>, val indexBuffer: IndexBuffer?, val primitive: DrawPrimitive, val offset: Int, val vertexCount : Int)





class Mesh(val geometry: Geometry, val material: Material) : Entity() {

}

abstract class Light : Entity() {
    var color: ColorRGBa = ColorRGBa.WHITE
}

class PointLight : Light()