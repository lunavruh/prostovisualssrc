#version 150

in vec3 Position;
in vec2 UV0;
in vec4 Color;

out vec2 TexCoord;
out vec4 VertexColor;

void main() {
    TexCoord = UV0;
    VertexColor = Color;
    gl_Position = vec4(Position, 1.0);
}
