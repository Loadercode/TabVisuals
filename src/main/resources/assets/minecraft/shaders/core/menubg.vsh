#version 150

in vec3 Position;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 Screen;

void main() {
    Screen = Position.xy;
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
