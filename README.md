# OPENRNDR-DNKY

A work-in-progress and proof-of-concept library for the drawing of
real-time and interactive 3d-graphics in OPENRNDR.

##### Usage

Currently barely works. DNKY's use currently looks like this:

```kotlin
import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.dnky.*
import org.openrndr.draw.DepthTestPass
import org.openrndr.extensions.Debug3D
import org.openrndr.extras.meshgenerators.bufferWriter
import org.openrndr.extras.meshgenerators.extrudeShape
import org.openrndr.extras.meshgenerators.meshVertexBuffer
import org.openrndr.math.Vector3
import org.openrndr.math.transforms.transform
import org.openrndr.shape.Circle

fun main() = application {
    program {
        val outerCylinder = meshVertexBuffer(2_0000)
        val outerCylinderVertices = outerCylinder.put {
            extrudeShape(
                Circle(0.0, 0.0, 16.0).shape, -16.0, 16.0, 0.025, true, bufferWriter(this)
            )
        }

        val vb = meshVertexBuffer(2_000)
        val vertexCount = vb.put {
            extrudeShape(
                Circle(0.0, 0.0, 4.0).shape, -2.0, 2.0, 0.025, bufferWriter(this)
            )
        }
        val renderer = SceneRenderer()
        val scene = scene {

            node {
                mesh {
                    geometry = geometry(vb, vertexCount = vertexCount)
                }
                ambientLight {
                    color = ColorRGBa.PINK
                }
            }

            node {
                transform = transform { rotate(Vector3.UNIT_X, 90.0) }
                mesh {
                    geometry = geometry(outerCylinder, vertexCount = outerCylinderVertices)
                }
            }

            node {
                draw {
                    transform = transform { translate(0.0, 8.0, 0.0) }
                }
                pointLight {
                    color = ColorRGBa.PINK.shade(0.1)
                }
            }

            node {
                transform = transform {
                    translate(0.0, 14.0, 0.0)
                    rotate(Vector3.UNIT_X, 90.0)
                }
                mesh {
                    geometry = geometry(vb, vertexCount = vertexCount)
                    basicMaterial {
                        diffuse = ColorRGBa.WHITE
                    }
                }
            }
        }

        val camera = PerspectiveCamera(90.0, width * 1.0, height * 1.0, 0.1, 100.0)

        extend(Debug3D())
        extend {
            drawer.background(ColorRGBa.PINK)
            drawer.depthTestPass = DepthTestPass.LESS_OR_EQUAL
            drawer.depthWrite = true
            renderer.draw(drawer, scene, camera)
        }
    }
}
```