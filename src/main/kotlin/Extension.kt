package org.openrndr.dnky

import org.openrndr.Extension
import org.openrndr.Program
import org.openrndr.draw.DepthTestPass
import org.openrndr.draw.Drawer

class DNKY : Extension {
    override var enabled: Boolean = true
    var scene = Scene()
    var sceneRenderer = SceneRenderer()

    override fun beforeDraw(drawer: Drawer, program: Program) {
        drawer.depthTestPass = DepthTestPass.LESS_OR_EQUAL
        drawer.depthWrite = true
        sceneRenderer.draw(drawer, scene)
    }
}