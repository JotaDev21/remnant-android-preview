package com.liferpg.ui
import kotlin.math.*

internal data class Vertex3(val x: Float, val y: Float, val z: Float) {
    operator fun minus(v: Vertex3) = Vertex3(x-v.x,y-v.y,z-v.z)
    fun cross(v: Vertex3) = Vertex3(y*v.z-z*v.y,z*v.x-x*v.z,x*v.y-y*v.x)
    fun unit(): Vertex3 { val d = sqrt(x*x+y*y+z*z).coerceAtLeast(.0001f); return Vertex3(x/d,y/d,z/d) }
    fun dot(v: Vertex3) = x*v.x+y*v.y+z*v.z
    fun rotate(yaw: Float, pitch: Float): Vertex3 {
        val a = x*cos(yaw)+z*sin(yaw); val b = z*cos(yaw)-x*sin(yaw)
        return Vertex3(a,y*cos(pitch)-b*sin(pitch),y*sin(pitch)+b*cos(pitch))
    }
}
