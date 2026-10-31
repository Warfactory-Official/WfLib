#version 150

// One body part's front face from a 64x64 skin (base + overlay layer), stained by uHealth:
// 1 = untouched, 0 = desaturated + blood-tinted.

uniform sampler2D Skin;
uniform vec4 uBase;      // region origin (0..1) xy, size zw
uniform vec4 uOverlay;
uniform float uHealth;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 tc = vec2(texCoord.x, 1.0 - texCoord.y);
    vec4 base = texture(Skin, uBase.xy + tc * uBase.zw);
    vec4 ov = texture(Skin, uOverlay.xy + tc * uOverlay.zw);

    vec3 rgb = mix(base.rgb, ov.rgb, ov.a);
    float alpha = max(base.a, ov.a);

    float dmg = clamp(1.0 - uHealth, 0.0, 1.0);
    float lum = dot(rgb, vec3(0.299, 0.587, 0.114));
    vec3 damaged = mix(rgb, vec3(lum), dmg * 0.85);
    damaged = mix(damaged, vec3(0.5, 0.03, 0.03), dmg * 0.55);

    fragColor = vec4(damaged, alpha);
}
