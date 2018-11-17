package org.openrndr.dnky

import org.openrndr.draw.*
import org.openrndr.math.Matrix44

class SceneRenderer {

    fun draw(drawer: Drawer, scene: Scene, camera: Camera) {
        scene.drawFunctions.forEach {
            it()
        }

        // update all the transforms
        scene.root.scan(Matrix44.IDENTITY) { p ->
            worldTransform = p * transform
            worldTransform
        }

        val lights = scene.root.findContent { this as? Light }
        val meshes = scene.root.findContent { this as? Mesh }
        val fogs = scene.root.findContent { this as? Fog }
        val instancedMeshes = scene.root.findContent { this as? InstancedMesh }


        val materialContext = MaterialContext(lights, fogs)

        meshes.forEach {
            val mesh = it.content
            drawer.isolated {
                val shadeStyle = mesh.material.generateShadeStyle(materialContext)
                shadeStyle.parameter("viewMatrixInverse", drawer.view.inversed)
                mesh.material.applyToShadeStyle(materialContext, shadeStyle)
                drawer.shadeStyle = shadeStyle
                drawer.model = it.node.worldTransform
                drawer.vertexBuffer(mesh.geometry.vertexBuffers,
                        DrawPrimitive.TRIANGLES,
                        mesh.geometry.offset,
                        mesh.geometry.vertexCount)
            }
        }

        instancedMeshes.forEach {
            val mesh = it.content
            drawer.isolated {
                val shadeStyle = mesh.material.generateShadeStyle(materialContext)
                shadeStyle.parameter("viewMatrixInverse", drawer.view.inversed)
                mesh.material.applyToShadeStyle(materialContext, shadeStyle)
                drawer.shadeStyle = shadeStyle
                drawer.model = it.node.worldTransform
                drawer.vertexBufferInstances(mesh.geometry.vertexBuffers,
                        mesh.attributes,
                        DrawPrimitive.TRIANGLES,
                        mesh.instances,
                        mesh.geometry.offset,
                        mesh.geometry.vertexCount)
            }
        }
    }
}

