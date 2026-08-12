package foundry.veil.api;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * [1.20.1 移植] 由 Veil 1.21 的 CodecReloadListener 回填。
 * NeoForge 1.21 提供 CodecReloadListener(Codec&lt;T&gt;, FileToIdConverter)，Forge 1.20.1 无此类。
 * 改为继承官方 SimplePreparableReloadListener&lt;Map&lt;ResourceLocation,T&gt;&gt;，
 * 在 prepare 中用 Codec 解析 FileToIdConverter 命中的资源文件，apply 交由子类（SimpleResourceManager）覆写。
 */
public abstract class CodecReloadListener<T> extends SimplePreparableReloadListener<Map<ResourceLocation, T>> {

    private static final Gson GSON = new Gson();

    private final Codec<T> codec;
    private final FileToIdConverter converter;

    protected CodecReloadListener(final Codec<T> codec, final FileToIdConverter converter) {
        this.codec = codec;
        this.converter = converter;
    }

    @Override
    protected Map<ResourceLocation, T> prepare(final ResourceManager manager, final ProfilerFiller profiler) {
        final Map<ResourceLocation, T> result = new HashMap<>();
        for (final Map.Entry<ResourceLocation, Resource> entry : this.converter.listMatchingResources(manager).entrySet()) {
            final ResourceLocation id = this.converter.fileToId(entry.getKey());
            try (final InputStreamReader reader = new InputStreamReader(entry.getValue().open(), StandardCharsets.UTF_8)) {
                final JsonElement json = GSON.fromJson(reader, JsonElement.class);
                final DataResult<T> dataResult = this.codec.parse(JsonOps.INSTANCE, json);
                dataResult.result().ifPresent(value -> result.put(id, value));
            } catch (final Exception ignored) {
                // 单个文件解析失败不阻断整体数据加载
            }
        }
        return result;
    }

    @Override
    protected abstract void apply(final Map<ResourceLocation, T> data, final ResourceManager manager, final ProfilerFiller profiler);
}
