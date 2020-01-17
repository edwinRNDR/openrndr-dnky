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
    var vertexBuffer: VertexBuffer? = null
    var vertexBuffers: MutableList<VertexBuffer>? = null//  mutableListOf<VertexBuffer>()
    var indexBuffer: IndexBuffer? = null
    var primitive: DrawPrimitive = DrawPrimitive.TRIANGLES
    var offset = 0
    var vertexCount: Int? = null

    internal fun build(): Geometry {
        val effectiveVertexBuffers = mutableListOf<VertexBuffer>()

        vertexBuffer?.let {
            effectiveVertexBuffers.add(it)
        }
        vertexBuffers?.let {
            effectiveVertexBuffers.addAll(it)
        }
        require(effectiveVertexBuffers.isNotEmpty()) { "need at least one vertex buffer" }

        val effectiveVertexCount = vertexCount ?: effectiveVertexBuffers[0].vertexCount

        return Geometry(effectiveVertexBuffers, indexBuffer, primitive, offset, effectiveVertexCount)
    }
}

fun geometry(vertexBuffer: VertexBuffer,
             primitive: DrawPrimitive = DrawPrimitive.TRIANGLES,
             offset: Int = 0, vertexCount: Int = vertexBuffer.vertexCount): Geometry {
    return Geometry(listOf(vertexBuffer), null, primitive, offset, vertexCount)
}

fun geometry(vertexBuffers: List<VertexBuffer>,
             primitive: DrawPrimitive = DrawPrimitive.TRIANGLES,
             offset: Int = 0, vertexCount: Int = vertexBuffers[0].vertexCount): Geometry {
    return Geometry(vertexBuffers, null, primitive, offset, vertexCount)
}


fun geometry(init: GeometryBuilder.() -> Unit): Geometry {
    val builder = GeometryBuilder()
    builder.init()
    return builder.build()
}

fun MeshBase.geometry(init: GeometryBuilder.() -> Unit) {
    val builder = GeometryBuilder()
    builder.init()
    geometry = builder.build()
}

abstract class MeshBase(var geometry: Geometry, var material: Material) : Entity()
class Mesh(geometry: Geometry, material: Material) : MeshBase(geometry, material)
class InstancedMesh(geometry: Geometry,
                    material: Material,
                    var instances: Int,
                    var attributes: List<VertexBuffer>) : MeshBase(geometry, material)

class LineMesh(var segments: List<List<Vector3>>, var weights: List<Double>, var colors: List<ColorRGBa>, var material: Material) : Entity()


enum class RayMarcherFunctionType {
    SIGNED_DISTANCE,
    DENSITY
}

class RayMarcher(val functionType: RayMarcherFunctionType, var function: String, var preamble: String, var material: Material) : Entity() {

    var parameters = mutableMapOf<String, Any>()
    fun generateShadeStyle(materialContext: MaterialContext): ShadeStyle {
        val s = material.generateShadeStyle(materialContext)
        val ns = ShadeStyle()

        if (functionType == RayMarcherFunctionType.SIGNED_DISTANCE) {
            ns.fragmentPreamble = s.fragmentPreamble + preamble + """
        |// -- SDF preamble
        |float sdf(vec3 p) {
        |   float x_distance = 0.0;
        |   { $function }
        |   return x_distance;                 
        |}
        |vec3 estimateNormal(vec3 p) {
        |   float EPSILON = 0.01;
        |   return normalize(vec3(
        |       sdf(vec3(p.x + EPSILON, p.y, p.z)) - sdf(vec3(p.x - EPSILON, p.y, p.z)),
        |       sdf(vec3(p.x, p.y + EPSILON, p.z)) - sdf(vec3(p.x, p.y - EPSILON, p.z)),
        |       sdf(vec3(p.x, p.y, p.z  + EPSILON)) - sdf(vec3(p.x, p.y, p.z - EPSILON))
        |   ));
        |}
        """.trimMargin()

            ns.suppressDefaultOutput = true
            ns.fragmentTransform = """
        |// -- SDF fragment transform
        |vec3 v_worldNormal = vec3(0.0, 0.0, 1.0);
        |vec3 va_normal = vec3(0.0, 0.0, 1.0);
        |vec3 va_position = vec3(0.0, 0.0, 0.0);
        |vec3 v_viewPosition = vec3(0.0, 0.0, 0.0);
        |mat4 v_modelNormalMatrix = u_modelNormalMatrix;
        |vec3 v_worldPosition = vec3(0.0, 0.0, 0.0);  
        |vec3 test = (p_projectionMatrixInverse * vec4((v_texCoord0-vec2(0.5)) * 2.0, 1.0, 1.0)).xyz;
        |vec3 rayViewDirection = normalize(test);
        |vec3 rayViewPosition = vec3(0.0) + rayViewDirection;
        |vec3 rayDirection = (p_viewMatrixInverse * vec4(rayViewDirection, 0.0)).xyz;
        |vec3 rayOrigin = (p_viewMatrixInverse * vec4(0.0, 0.0, 0.0, 1.0)).xyz;
        |vec3 rayPosition = rayOrigin + rayDirection;
        |int found = 0;
        |float refDepth = texture(p_depthBuffer, v_texCoord0).r;
        |float A = u_projectionMatrix[2].z;
        |float B = u_projectionMatrix[3].z;
    
        |for (int i = 0; i < 100; ++i) {
        |   vec3 rvp = rayViewPosition;
        |   float d = -rvp.z;
        |   float rayDepth = 0.5*((-A*d + B) / d) + 0.5;   
        |  if (rayDepth > refDepth) {
        |       discard;
        |       break;
        |   }
        |   
        |   float distance = sdf(rayPosition);
        |   if (abs(distance) < 0.001) {
        |       found = 1;
        |       v_worldPosition = rayPosition;
        |       v_worldNormal = estimateNormal(rayPosition);
        |       v_viewPosition = rayViewPosition;
        |       va_normal = v_worldNormal;
        |       va_position = rayPosition;
        |       gl_FragDepth = rayDepth;
        |       
        |       break;
        |   } else {
        |       rayViewPosition += rayViewDirection * distance * 0.5;
                rayPosition += rayDirection * distance * 0.5;
        |   }
        |}
        |if (found == 0) {
        |   discard;
        |}
        """.trimMargin() + s.fragmentTransform
        } else if (functionType == RayMarcherFunctionType.DENSITY) {
            if (functionType == RayMarcherFunctionType.SIGNED_DISTANCE) {
                ns.fragmentPreamble = s.fragmentPreamble + """
        |// -- Density function preamble
        |float density(vec3 p) {
        |   float x_density = 0.0;
        |   { $function }
        |   return x_density;                 
        |}
        |vec3 estimateNormal(vec3 p) {
        |   float EPSILON = 0.01;
        |   return normalize(vec3(
        |       density(vec3(p.x + EPSILON, p.y, p.z)) - density(vec3(p.x - EPSILON, p.y, p.z)),
        |       density(vec3(p.x, p.y + EPSILON, p.z)) - density(vec3(p.x, p.y - EPSILON, p.z)),
        |       density(vec3(p.x, p.y, p.z  + EPSILON)) - density(vec3(p.x, p.y, p.z - EPSILON))
        |   ));
        |}
        """.trimMargin()

                ns.suppressDefaultOutput = true
                ns.fragmentTransform = """
        |// -- density function fragment transform
        |vec3 v_worldNormal = vec3(0.0, 0.0, 1.0);
        |vec3 va_normal = vec3(0.0, 0.0, 1.0);
        |vec3 va_position = vec3(0.0, 0.0, 0.0);
        |vec3 v_viewPosition = vec3(0.0, 0.0, 0.0);
        |mat4 v_modelNormalMatrix = u_modelNormalMatrix;
        |vec3 v_worldPosition = vec3(0.0, 0.0, 0.0);  
        |vec3 test = (p_projectionMatrixInverse * vec4((v_texCoord0-vec2(0.5)) * 2.0, 1.0, 1.0)).xyz;
        |vec3 rayViewDirection = normalize(test);
        |vec3 rayViewPosition = vec3(0.0) + rayViewDirection;
        |vec3 rayDirection = (p_viewMatrixInverse * vec4(rayViewDirection, 0.0)).xyz;
        |vec3 rayOrigin = (p_viewMatrixInverse * vec4(0.0, 0.0, 0.0, 1.0)).xyz;
        |vec3 rayPosition = rayOrigin + rayDirection;
        |int found = 0;
        |float refDepth = texture(p_depthBuffer, v_texCoord0).r;
        |float A = u_projectionMatrix[2].z;
        |float B = u_projectionMatrix[3].z;
    
        |for (int i = 0; i < 100; ++i) {
        |   vec3 rvp = rayViewPosition;
        |   float d = -rvp.z;
        |   float rayDepth = 0.5*((-A*d + B) / d) + 0.5;   
        |  if (rayDepth > refDepth) {
        |       discard;
        |       break;
        |   }
        |   
        |   float distance = density(rayPosition);
        |   if (abs(distance) < 0.001) {
        |       found = 1;
        |       v_worldPosition = rayPosition;
        |       v_worldNormal = estimateNormal(rayPosition);
        |       v_viewPosition = rayViewPosition;
        |       va_normal = v_worldNormal;
        |       va_position = rayPosition;
        |       gl_FragDepth = rayDepth;
        |       
        |       break;
        |   } else {
        |       rayViewPosition += rayViewDirection * distance;
                rayPosition += rayDirection * distance;
        |   }
        |}
        |if (found == 0) {
        |   discard;
        |}
        """.trimMargin() + s.fragmentTransform
            }
        }

        ns.outputs.putAll(s.outputs)
        ns.parameters.putAll(s.parameters)


        return ns
    }
}

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

sealed class Shadows {
    object None : Shadows()
    abstract class MappedShadows(val mapSize: Int) : Shadows()
    abstract class DepthMappedShadows(mapSize: Int) : MappedShadows(mapSize)
    abstract class ColorMappedShadows(mapSize: Int) : MappedShadows(mapSize)
    class Simple(mapSize: Int = 1024) : DepthMappedShadows(mapSize)
    class PCF(mapSize: Int = 1024, val sampleCount: Int = 12) : DepthMappedShadows(mapSize)
    class VSM(mapSize: Int = 1024) : ColorMappedShadows(mapSize)
}

interface ShadowLight {
    var shadows: Shadows
    fun projection(renderTarget: RenderTarget): Matrix44
    fun view(node: SceneNode): Matrix44 {
//        val position = node.worldTransform * Vector4.UNIT_W
//        val rotation = Matrix44.fromColumnVectors(node.worldTransform[0], node.worldTransform[1], node.worldTransform[2] * 1.0, Vector4.UNIT_W).inversed
//
//        return transform {
//
//            translate(position.xyz)
//            multiply(rotation)
//        }
        return node.worldTransform.inversed
    }
}

interface AttenuatedLight {
    var constantAttenuation: Double
    var linearAttenuation: Double
    var quadraticAttenuation: Double
}

class DirectionalLight(var direction: Vector3 = -Vector3.UNIT_Z, override var shadows: Shadows = Shadows.None) : Light(), ShadowLight {
    var projectionSize = 10.0

    override fun projection(renderTarget: RenderTarget): Matrix44 {
        return ortho(-projectionSize / 2.0, projectionSize / 2.0, -projectionSize / 2.0, projectionSize / 2.0, 1.0, 150.0)
    }
}

class SpotLight(var direction: Vector3 = -Vector3.UNIT_Z, var innerAngle: Double = 45.0, var outerAngle: Double = 90.0) : Light(), ShadowLight, AttenuatedLight {
    override var constantAttenuation = 1.0
    override var linearAttenuation = 0.0
    override var quadraticAttenuation = 0.0
    override var shadows: Shadows = Shadows.None
    override fun projection(renderTarget: RenderTarget): Matrix44 {
        return perspective(outerAngle * 2.0, renderTarget.width * 1.0 / renderTarget.height, 1.0, 150.0)
    }
}

class AreaLight : Light(), ShadowLight, AttenuatedLight {
    companion object;
    override var constantAttenuation = 1.0
    override var linearAttenuation = 0.0
    override var quadraticAttenuation = 0.0
    var width: Double = 1.0
    var height: Double = 1.0
    var distanceField: ColorBuffer? = null
    override var shadows: Shadows = Shadows.None
    override fun projection(renderTarget: RenderTarget): Matrix44 {
        val outerAngle = 45.0
        return perspective(outerAngle * 2.0, renderTarget.width * 1.0 / renderTarget.height, 1.0, 150.0)
    }
}

class HemisphereLight(var direction: Vector3 = Vector3.UNIT_Y,
                      var upColor: ColorRGBa = ColorRGBa.WHITE,
                      var downColor: ColorRGBa = ColorRGBa.BLACK) : Light() {
    var irradianceMap: Cubemap? = null
}