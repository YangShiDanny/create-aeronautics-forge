uniform vec4 ColorModulator;

in vec4 vertexColor;

out vec4 fragColor;

// 物理法杖光束“特调辉光”：自发光 + 微冷调，配合叠加混合（ADDITIVE_TRANSPARENCY）
// 让光束本身呈现发光质感，重叠处更亮形成辉光核心。
// 不依赖 Veil 后处理（1.20.1 Forge 无 Veil），故自包含、不 include 任何 veil 片段。
void main() {
    // 提亮顶点色，叠加一点冷调，模拟原版特调辉光
    vec3 glow = vertexColor.rgb * 1.6 + vec3(0.08);
    glow = mix(glow, vec3(0.55, 0.85, 1.0), 0.18);

    float a = vertexColor.a * 0.9;
    fragColor = vec4(glow, a) * ColorModulator;
}
