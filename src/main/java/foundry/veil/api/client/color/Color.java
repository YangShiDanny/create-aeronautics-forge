package foundry.veil.api.client.color;

import com.mojang.serialization.Codec;

public final class Color implements Colorc {
    private final int value;
    public Color(int value, boolean hasAlpha) { this.value = value; }
    public Color(int value) { this.value = value; }
    @Override public int argb() { return value; }
    @Override public float red() { return ((value >> 16) & 0xFF) / 255.0f; }
    @Override public float green() { return ((value >> 8) & 0xFF) / 255.0f; }
    @Override public float blue() { return (value & 0xFF) / 255.0f; }
    @Override public float alpha() { return ((value >> 24) & 0xFF) / 255.0f; }

    /** 1.20.1 backport: ARGB 整数编解码器（仿 Veil 的 Color.ARGB_INT_CODEC）。 */
    public static final Codec<Integer> ARGB_INT_CODEC = Codec.INT;
}
