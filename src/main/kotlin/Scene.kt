package org.openrndr.dnky

import org.openrndr.math.Matrix44

class Scene(val root: SceneNode = SceneNode())

open class SceneNode(var entity: Entity? = null) {
    var parent: SceneNode? = null
    var transform = Matrix44.IDENTITY
    var worldTransform = Matrix44.IDENTITY
    val children = mutableListOf<SceneNode>()
    var draw: (() -> Unit)? = null
}

fun SceneNode.visit(visitor: SceneNode.() -> Unit) {
    visitor()
    children.forEach { it.visit(visitor) }
}

fun <P> SceneNode.scan(initial: P, scanner: SceneNode.(P) -> P) {
    val p = scanner(initial)
    children.forEach { it.scan(p, scanner) }
}

fun SceneNode.findNodes(selector: SceneNode.() -> Boolean): List<SceneNode> {
    val result = mutableListOf<SceneNode>()
    visit {
        if (selector()) result.add(this)
    }
    return result
}

fun <P> SceneNode.findContent(selector: SceneNode.() -> P?): List<P> {
    val result = mutableListOf<P>()

    visit {
        val s = selector()
        if (s != null) {
            result.add(s)
        }
    }
    return result
}

fun scene(init: Scene.() -> Unit): Scene {
    val scene = Scene()
    scene.init()
    return scene
}

fun Scene.node(init: SceneNode.() -> Unit): SceneNode {
    val node = SceneNode()
    node.init()
    root.children.add(node)
    node.parent = root
    return node
}

fun SceneNode.node(init: SceneNode.() -> Unit): SceneNode {
    val node = SceneNode()
    node.init()
    children.add(node)
    node.parent = this
    return node
}

fun SceneNode.draw(call: () -> Unit) {
    draw = call
}

fun SceneNode.mesh(geometry: Geometry, material: Material, init: Mesh.() -> Unit = {}): Mesh {
    val mesh = Mesh(geometry, material).apply(init)
    entity = mesh
    return mesh
}

fun SceneNode.pointLight(init: PointLight.() -> Unit): PointLight {
    val pointLight = PointLight().apply(init)
    entity = pointLight
    return pointLight
}

fun SceneNode.directionalLight(init: DirectionalLight.() -> Unit): DirectionalLight {
    val directionalLight = DirectionalLight().apply(init)
    entity = directionalLight
    return directionalLight
}

fun SceneNode.ambientLight(init: AmbientLight.() -> Unit): AmbientLight {
    val ambientLight = AmbientLight().apply(init)
    entity = ambientLight
    return ambientLight
}

private fun sample() {
    val s = scene {
        node {
            draw {
                transform = Matrix44.IDENTITY
            }
            node {
                pointLight {

                }
            }
        }
    }
}