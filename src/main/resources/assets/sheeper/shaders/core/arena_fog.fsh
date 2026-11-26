#version 150

uniform sampler2D SceneDepth;
uniform mat4 InverseViewProjection;
uniform vec3 RayOrigin;
uniform vec3 FogCylinder;
uniform vec2 FogHeights;
uniform vec4 FogColor;
uniform float FogDensity;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    float depth = texture(SceneDepth, texCoord).r;
    vec4 point = InverseViewProjection * vec4(texCoord * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec3 endpoint = point.xyz / point.w;
    vec3 delta = endpoint - RayOrigin;
    float distance = length(delta);
    if (distance < 0.0001) discard;
    vec3 ray = delta / distance;
    vec2 relative = RayOrigin.xz - FogCylinder.xy;
    float enter = 0.0;
    float exit = distance;

    // Analytic intersection avoids ray marching and confines fog to the cylinder.
    float a = dot(ray.xz, ray.xz);
    float b = dot(relative, ray.xz);
    float c = dot(relative, relative) - FogCylinder.z * FogCylinder.z;
    if (a < 0.000001) {
        if (c > 0.0) discard;
    } else {
        float discriminant = b * b - a * c;
        if (discriminant < 0.0) discard;
        float root = sqrt(discriminant);
        enter = max(enter, (-b - root) / a);
        exit = min(exit, (-b + root) / a);
    }

    if (abs(ray.y) < 0.000001) {
        if (RayOrigin.y < FogHeights.x || RayOrigin.y > FogHeights.y) discard;
    } else {
        vec2 vertical = (FogHeights - RayOrigin.y) / ray.y;
        enter = max(enter, min(vertical.x, vertical.y));
        exit = min(exit, max(vertical.x, vertical.y));
    }

    float fogDistance = max(0.0, exit - enter);
    if (fogDistance <= 0.0) discard;
    // Only fog in front of the sampled terrain contributes. Foreground walls
    // remain clear; looking deeper into the arena increases attenuation naturally.
    float opacity = 1.0 - exp(-fogDistance * FogDensity);
    fragColor = vec4(FogColor.rgb, opacity * FogColor.a);
}
