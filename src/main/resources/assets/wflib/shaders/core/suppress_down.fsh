#version 150

uniform sampler2D DiffuseSampler;
uniform vec2 HalfPixel;
uniform float Offset;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 o = HalfPixel * Offset;
    vec4 sum = texture(DiffuseSampler, texCoord) * 4.0;
    sum += texture(DiffuseSampler, texCoord - o);
    sum += texture(DiffuseSampler, texCoord + o);
    sum += texture(DiffuseSampler, texCoord + vec2(o.x, -o.y));
    sum += texture(DiffuseSampler, texCoord - vec2(o.x, -o.y));
    fragColor = vec4(sum.rgb / 8.0, 1.0);
}
