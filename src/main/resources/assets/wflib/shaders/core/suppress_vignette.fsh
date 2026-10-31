#version 150

uniform float Strength;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    float r = length((texCoord - 0.5) * vec2(1.6, 1.0));
    fragColor = vec4(0.0, 0.0, 0.0, Strength * smoothstep(0.25, 0.95, r));
}
