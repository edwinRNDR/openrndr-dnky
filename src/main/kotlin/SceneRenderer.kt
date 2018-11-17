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

        val lights = scene.root.findContent { this as? Light }
        val meshes = scene.root.findContent { this as? Mesh }

        val materialContext = MaterialContext(lights)

        meshes.forEach {
            val mesh = it.content
            drawer.isolated {
                val shadeStyle = mesh.material.generateShadeStyle(materialContext)
                shadeStyle.parameter("viewMatrixInverse", drawer.view.inversed)
                mesh.material.applyToShadeStyle(materialContext, shadeStyle)
                drawer.shadeStyle = shadeStyle
                drawer.model = it.node.worldTransform
                drawer.vertexBuffer(mesh.geometry.vertexBuffers, DrawPrimitive.TRIANGLES)
            }
        }
    }
}

