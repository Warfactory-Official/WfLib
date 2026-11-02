#version 150

// X-Ray's postprocess.ps (r2 phase PP): two duality-shifted taps averaged, gray, noise, then (image * base + add) * 2.
// Base / gray colours are 8-bit vertex colours there => clamped to 0..1. fx_noise2: mean 0.5, sigma 0.027, channels r=0.95.

uniform sampler2D DiffuseSampler;
uniform vec2 OutSize;
uniform float BaseR, BaseG, BaseB;
uniform float AddR, AddG, AddB;
uniform float GrayColorR, GrayColorG, GrayColorB;
uniform float Gray;
uniform float Blur;
uniform float DualH;
uniform float DualV;
uniform float Noise;
uniform float Grain;
uniform float Seed;

in vec2 texCoord;

out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233)) + Seed) * 43758.5453);
}

void main() {
    // u_calc_tc_duality_ss: both taps shifted half a pixel times blur; the views split by half the duality each way.
    vec2 shift = vec2(DualH, DualV) * 0.5;
    vec2 px = (0.5 / OutSize) * (1.0 + Blur);
    vec2 tc0 = px + texCoord * (1.0 - abs(shift)) + max(-shift, 0.0);
    vec2 tc1 = px + texCoord * (1.0 - abs(shift)) + max(shift, 0.0);
    vec3 image = (texture(DiffuseSampler, tc0).rgb + texture(DiffuseSampler, tc1).rgb) * 0.5;
    float g = dot(image, vec3(GrayColorR, GrayColorG, GrayColorB));
    image = mix(image, vec3(g), Gray);
    vec2 cell = floor(gl_FragCoord.xy / max(Grain, 0.01));
    vec3 n = vec3(0.5 + 0.0935 * (hash(cell) - 0.5)) + 0.02 * (vec3(hash(cell + 17.0), hash(cell + 41.0), hash(cell + 73.0)) - 0.5);
    image = mix(image, image * n * 2.0, Noise);
    image = (image * vec3(BaseR, BaseG, BaseB) + vec3(AddR, AddG, AddB)) * 2.0;
    fragColor = vec4(image, 1.0);
}
