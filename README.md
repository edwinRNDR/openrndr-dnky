# OPENRNDR-DNKY

A work-in-progress and proof-of-concept library for the drawing of
real-time and interactive 3d-graphics in OPENRNDR.

##### Usage

Currently does not work. The idea would be to have something like


```kotlin
val s =
scene {
    node {
        draw {
            transform = rotateX(seconds)
        }
        mesh {
            geometry(CubeGeometry(10.0))
            material {
                albedo = ColorRGBa.RED
            }
        }
        node {
            pointLight {
                color = ColorRGBa.BLUE
            }
        }
    }
}

```