package org.openrndr.dnky

import org.openrndr.color.ColorRGBa
import org.openrndr.draw.*
import org.openrndr.math.Matrix44
import org.openrndr.math.Vector3
import org.openrndr.math.Vector4
import org.openrndr.math.transforms.ortho
import org.openrndr.math.transforms.perspective
import org.openrndr.math.transforms.transform

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
class InstancedMesh(geometry: Geometry,
                    material: Material,
                    var instances: Int,
                    var attributes: List<VertexBuffer>) : MeshBase(geometry, material)

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
interface ShadowLight {
    var shadows: Boolean
    fun projection(renderTarget: RenderTarget): Matrix44
    fun view(node: SceneNode): Matrix44 {
        val position = node.worldTransform * Vector4.UNIT_W
        val rotation = Matrix44.fromColumnVectors(node.worldTransform[0], node.worldTransform[1], node.worldTransform[2] * -1.0, Vector4.UNIT_W).inversed

        return transform {
            multiply(rotation)
            translate(-position.xyz)
        }
    }
}

class DirectionalLight(var direction: Vector3 = Vector3.UNIT_Z, override var shadows: Boolean = false) : Light(), ShadowLight {
    override fun projection(renderTarget: RenderTarget): Matrix44 {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun view(node: SceneNode): Matrix44 {
        TODO("not implemented")
    }
}

class SpotLight(var direction: Vector3 = Vector3.UNIT_Z, var innerAngle: Double = 45.0, var outerAngle: Double = 90.0) : Light(), ShadowLight {
    var constantAttenuation = 1.0
    var linearAttenuation = 0.0
    var quadraticAttenuation = 0.0
    override var shadows: Boolean = false
    override fun projection(renderTarget: RenderTarget): Matrix44 {
        return perspective(outerAngle * 2.0, renderTarget.width * 1.0 / renderTarget.height, 1.0, 150.0)
    }
}

class AreaLight() : Light(), ShadowLight {
    var width: Double = 1.0
    var height: Double = 1.0
    override var shadows: Boolean = false
    override fun projection(renderTarget: RenderTarget): Matrix44 {
        return ortho(-width/2.0, width/2.0, -height/2.0, height/2.0, 0.0, 1000.0)
    }
}

class HemisphereLight(var direction: Vector3 = Vector3.UNIT_Y,
                      var upColor: ColorRGBa = ColorRGBa.WHITE,
                      var downColor: ColorRGBa = ColorRGBa.BLACK) : Light() {
    var irradianceMap: Cubemap? = null
}