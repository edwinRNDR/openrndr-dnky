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
        val vb = meshVertexBuffer(2_000)
        val vertexCount = vb.put {
            extrudeShape(
                Circle(0.0, 0.0, 4.0).shape, -0.5, 0.5, 0.025, bufferWriter(this)
            )
        }
        val renderer = SceneRenderer()
        val scene = scene {
            node {
                ambientLight {
                    color = ColorRGBa.GRAY
                }
            }

            node {
                directionalLight {
                    color = ColorRGBa.RED
                }
            }

            node {
                draw {
                    transform = transform { translate(0.0, 100.0, 50.0 + Math.cos(seconds)*50.0) }
                }
                pointLight {
                    color = ColorRGBa.BLUE
                }
            }

            node {
                mesh(geometry(vb, vertexCount = vertexCount), Material())

                node {
                    draw {
                        transform = transform {
                            translate(Vector3(Math.cos(seconds), 0.0, 2.0))
                        }
                    }
                    mesh(geometry(vb, vertexCount = vertexCount), Material())
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