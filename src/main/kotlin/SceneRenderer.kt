package org.openrndr.dnky

import org.openrndr.draw.*
import org.openrndr.math.Matrix44

class SceneRenderer {

    fun draw(drawer: Drawer, scene: Scene, camera: Camera) {
        // -- call all draw() functions
        scene.root.visit {
            draw?.invoke()
        }

        // update all the transforms
        scene.root.scan(Matrix44.IDENTITY) { p ->
            worldTransform = p * transform
            worldTransform
        }

        val lights = scene.root.findNodes { entity is Light }
        val meshes = scene.root.findNodes { entity is Mesh }

        val materialContext = MaterialContext(lights)

        meshes.forEach {
            val mesh = it.entity as Mesh
            drawer.isolated {
                val shadeStyle = mesh.material.generateShadeStyle(materialContext)
                mesh.material.applyToShadeStyle(materialContext, shadeStyle)
                drawer.shadeStyle = shadeStyle
                drawer.model = it.worldTransform
                drawer.vertexBuffer((it.entity as Mesh).geometry.vertexBuffers, DrawPrimitive.TRIANGLES)
            }
        }
    }
}

