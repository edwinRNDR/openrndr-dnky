package org.openrndr.dnky

import org.openrndr.color.ColorRGBa
import org.openrndr.draw.DrawPrimitive
import org.openrndr.draw.IndexBuffer
import org.openrndr.draw.VertexBuffer
import org.openrndr.math.Vector3

sealed class Entity

class Geometry(val vertexBuffers: List<VertexBuffer>,
               val indexBuffer: IndexBuffer?,
               val primitive: DrawPrimitive,
               val offset: Int,
               val vertexCount: Int)

val DummyGeometry = Geometry(emptyList(), null, DrawPrimitive.TRIANGLES, 0, 0)

class GeometryBuilder {
    var vertexBuffers = mutableListOf<VertexBuffer>()
    var indexBuffer: IndexBuffer? = null
    var primitive: DrawPrimitive = DrawPrimitive.TRIANGLES
    var offset = 0
    var vertexCount = 0

    operator fun invoke(): Geometry {
        return Geometry(vertexBuffers, indexBuffer, primitive, offset, vertexCount)
    }
}

fun geometry(vertexBuffer: VertexBuffer,
             primitive: DrawPrimitive = DrawPrimitive.TRIANGLES,
             offset: Int = 0, vertexCount: Int = vertexBuffer.vertexCount): Geometry {
    return Geometry(listOf(vertexBuffer), null, primitive, offset, vertexCount)
}

fun geometry(init: GeometryBuilder.() -> Unit): Geometry {
    val builder = GeometryBuilder()
    builder.init()
    return builder()
}

abstract class MeshBase(var geometry: Geometry, var material: Material) : Entity()
class Mesh(geometry: Geometry, material: Material) : MeshBase(geometry, material)
class InstancedMesh(geometry: Geometry, material: Material, var instances: Int, var attributes: List<VertexBuffer>) : MeshBase(geometry, material)

/** Light entity */
abstract class Light : Entity() {
    var color: ColorRGBa = ColorRGBa.WHITE
}

class Fog : Entity() {
    var color: ColorRGBa = ColorRGBa.WHITE
    var end: Double = 100.0
}

class PointLight(var constantAttenuation: Double = 1.0,
                 var linearAttenuation: Double = 0.0,
                 var quadraticAttenuation: Double = 0.0) : Light()

class AmbientLight : Light()
class DirectionalLight(var direction: Vector3 = Vector3.UNIT_Z) : Light()