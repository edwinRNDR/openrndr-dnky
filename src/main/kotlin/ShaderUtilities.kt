package org.openrndr.dnky

val shaderProjectOnPlane = """
vec3 projectOnPlane(vec3 p, vec3 pc, vec3 pn) {
    float distance = dot(pn, p-pc);
    return p - distance * pn;
}
""".trimIndent()

val shaderSideOfPlane = """
int sideOfPlane(in vec3 p, in vec3 pc, in vec3 pn){
   if (dot(p-pc,pn) >= 0.0) return 1; else return 0;
}
""".trimIndent()

val shaderLinePlaneIntersect = """
vec3 linePlaneIntersect(in vec3 lp, in vec3 lv, in vec3 pc, in vec3 pn){
   return lp+lv*(dot(pn,pc-lp)/dot(pn,lv));
}
""".trimIndent()