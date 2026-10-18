#include "gemrender:particle.glsl"

void flw_transformBoundingSphere(in FlwInstance i, inout vec3 center, inout float radius) {
    GemRenderParticle p = gemrender_particle(i.particle);

    if (p.life <= 0.0) {
        radius = -1e18;
        return;
    }

    center = i.origin + p.spawnPos + p.spawnVelocity * 0.025;
    radius = radius * p.sizeScale + length(p.spawnVelocity) * 0.025;
}
