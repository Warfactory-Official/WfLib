#include "gemrender:particle.glsl"

// Record rewritten each client tick (TorexEffect): spawnPos = previous tick position, spawnVelocity = per-second
// step, age clamped to one tick => vanilla prev -> current lerp. sizeScale = quad width, tintScale = alpha,
// (contactAge, contactNormal / 65535, restAge) = rgb, look = lightmap 0..240, spinPhase = heat (glowing material).
void flw_instanceVertex(in FlwInstance i) {
    GemRenderParticle p = gemrender_particle(i.particle);

    float age = clamp(flw_renderSeconds - p.spawnTime, 0.0, 0.05);
    vec3 center = i.origin + p.spawnPos + p.spawnVelocity * age;
    float size = p.life > 0.0 ? p.sizeScale : 0.0;

    vec3 right = vec3(flw_viewInverse[0]);
    vec3 up = vec3(flw_viewInverse[1]);

    flw_vertexPos = vec4(center + (flw_vertexPos.x * right + flw_vertexPos.y * up) * size, 1.0);
    flw_vertexNormal = -vec3(flw_viewInverse[2]);
    flw_vertexColor = vec4(p.contactAge, p.contactNormal / 65535.0, p.restAge, p.tintScale);
    flw_vertexOverlay = gemrender_particleOverlay(p.spinPhase);
    flw_vertexLight = vec2(p.look / 256.0);
}
