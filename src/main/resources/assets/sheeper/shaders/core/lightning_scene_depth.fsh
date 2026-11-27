#version 150

uniform sampler2D SceneDepth;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    gl_FragDepth = texture(SceneDepth, texCoord).r;
    fragColor = vec4(0.0);
}
