#version 150

in vec3 Position;
in vec4 Color;

out vec2 vScreen;

void main() {
    vScreen = Position.xy;
    gl_Position = vec4(Position, 1.0);
}
