#version 150

uniform vec4 ColorModulator;

// x: 0(起点) -> endU(终点)，y 恒为 endU = (length + 0.5) / length
in vec2 lengthData;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    float endTaper = (lengthData.x - 1.0) / (lengthData.y - 1.0);
    vec4 color = vertexColor;
    color.a *= (1.0 - max(endTaper, 0.0));

    // [BUG-30·第二十四轮·主修复] 激光是发光体，不该被世界雾吞掉。
    // 原片元着色器乘了 linear_fog_fade(vertexDistance, FogStart, FogEnd)，
    // 而 FogStart/FogEnd 这两个 uniform 在本端口的自定义 ShaderInstance 里
    // 始终是 laser.json 里的默认值 0.0 / 1.0（方块实体渲染时游戏不会把世界雾
    // 推给自定义着色器），于是任意距离 > 1 的顶点都被雾衰减成 0 → 整条激光不可见。
    // 直接去掉雾衰减项，仅保留 endTaper 的远端渐隐（激光本身的“射程耗尽”效果），
    // 再乘 ColorModulator（默认 (1,1,1,1)，安全）。
    fragColor = color * ColorModulator;
}
