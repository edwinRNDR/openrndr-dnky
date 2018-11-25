package org.openrndr.dnky

import org.openrndr.math.Matrix44

class Scene(val root: SceneNode = SceneNode(),
            val updateFunctions: MutableList<() -> Unit> = mutableListOf())

class NodeContent<T : Entity>(val node: SceneNode, val content: T) {
    operator fun component1() = node
    operator fun component2() = content
}

open class SceneNode(var entities: MutableList<Entity> = mutableListOf()) {
    var parent: SceneNode? = null
    var transform = Matrix44.IDENTITY
    var worldTransform = Matrix44.IDENTITY
    val children = mutableListOf<SceneNode>()
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

fun <P : Entity> SceneNode.findContent(selector: Entity.() -> P?): List<NodeContent<P>> {
    val result = mutableListOf<NodeContent<P>>()

    visit {
        entities.forEach {
            val s = it.selector()
            if (s != null) {
                result.add(NodeContent(this, s))
            }
        }
    }
    return result
}

fun scene(init: Scene.() -> Unit): Scene {
    val scene = Scene()
    scene.init()
    return scene
}

fun Scene.update(updateFunction: () -> Unit) {
    updateFunctions.add(updateFunction)
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

fun SceneNode.mesh(geometry: Geometry, material: Material, init: Mesh.() -> Unit = {}): Mesh {
    val mesh = Mesh(geometry, material).apply(init)
    entities.add(mesh)
    return mesh
}

private val DefaultMaterial = BasicMaterial()
fun SceneNode.mesh(init: Mesh.() -> Unit): Mesh {
    val mesh = Mesh(DummyGeometry, DefaultMaterial)
    mesh.init()
    entities.add(mesh)
    return mesh
}

fun SceneNode.instancedMesh(init: InstancedMesh.() -> Unit): InstancedMesh {
    val instanced = InstancedMesh(DummyGeometry, DefaultMaterial, 1, mutableListOf())
    instanced.init()
    entities.add(instanced)
    return instanced
}

fun SceneNode.pointLight(init: PointLight.() -> Unit): PointLight {
    val pointLight = PointLight().apply(init)
    entities.add(pointLight)
    return pointLight
}

fun SceneNode.spotLight(init: SpotLight.() -> Unit): SpotLight {
    val spotLight = SpotLight().apply(init)
    entities.add(spotLight)
    return spotLight
}

fun SceneNode.directionalLight(init: DirectionalLight.() -> Unit): DirectionalLight {
    val directionalLight = DirectionalLight().apply(init)
    entities.add(directionalLight)
    return directionalLight
}

fun SceneNode.ambientLight(init: AmbientLight.() -> Unit): AmbientLight {
    val ambientLight = AmbientLight().apply(init)
    entities.add(ambientLight)
    return ambientLight
}

fun SceneNode.hemisphereLight(init: HemisphereLight. () -> Unit) : HemisphereLight {
    val hemisphereLight = HemisphereLight().apply(init)
    entities.add(hemisphereLight)
    return hemisphereLight
}

fun SceneNode.fog(init:Fog.() -> Unit) : Fog {
    val fog = Fog().apply(init)
    entities.add(fog)
    return fog
}

private fun sample() {
    val s = scene {
        node {
            update {
                transform = Matrix44.IDENTITY
            }
            node {
                pointLight {

                }
            }
        }
    }
}