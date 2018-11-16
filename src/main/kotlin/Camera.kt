package org.openrndr.dnky

import org.openrndr.math.Matrix44
import org.openrndr.math.Vector3
import org.openrndr.math.transforms.lookAt
import org.openrndr.math.transforms.perspective

interface Camera {
    val projectionMatrix: Matrix44
    val viewMatrix: Matrix44
}

class PerspectiveCamera(var fovDegrees: Double, var width: Double, var height: Double, var near: Double, var far: Double) : Camera {
    var position: Vector3 = Vector3.ZERO
    var target: Vector3 = Vector3.ZERO
    var up: Vector3 = Vector3.ZERO

    override val projectionMatrix: Matrix44
        get() = perspective(fovDegrees, width / height, near, far)

    override val viewMatrix: Matrix44
        get() = lookAt(position, target, up)
}