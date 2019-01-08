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

/*
N - world space normal
V - eye - world vertex position
L - world light pos - world vertex position
 */
val shaderGGX = """
float ggx (vec3 N, vec3 V, vec3 L, float roughness, float F0) {
  float alpha = roughness*roughness;
  vec3 H = normalize(L - V);
  float dotLH = max(0.0, dot(L,H));
  float dotNH = max(0.0, dot(N,H));
  float dotNL = max(0.0, dot(N,L));
  float alphaSqr = alpha * alpha;
  float denom = dotNH * dotNH * (alphaSqr - 1.0) + 1.0;
  float D = alphaSqr / (3.141592653589793 * denom * denom);
  float F = F0 + (1.0 - F0) * pow(1.0 - dotLH, 5.0);
  float k = 0.5 * alpha;
  float k2 = k * k;
  return dotNL * D * F / (dotLH*dotLH*(1.0-k2)+k2);
}
""".trimIndent()