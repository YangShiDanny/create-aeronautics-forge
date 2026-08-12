package dev.ryanhcode.sable.physics.config.block_properties;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.mixinterface.block_properties.BlockStateExtension;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;

public class PhysicsBlockPropertiesDefinitionLoader extends SimpleJsonResourceReloadListener {
    public static final String NAME = "physics_block_properties";
    public static final ResourceLocation ID = Sable.sablePath(NAME);

    protected static final Gson GSON = new Gson();
    public static final PhysicsBlockPropertiesDefinitionLoader INSTANCE = new PhysicsBlockPropertiesDefinitionLoader();
    private final ObjectList<PhysicsBlockPropertiesDefinition> definitions = new ObjectArrayList<>();

    private PhysicsBlockPropertiesDefinitionLoader() {
        super(GSON, NAME);
    }

    @Override
    public String getName() {
        return super.getName();
    }

    @Override
    protected void apply(final Map<ResourceLocation, JsonElement> map, final ResourceManager resourceManager, final ProfilerFiller profilerFiller) {
        this.definitions.clear();

        for (final Map.Entry<ResourceLocation, JsonElement> entry : map.entrySet()) {
            final ResourceLocation file = entry.getKey();
            final JsonElement json = entry.getValue();

            final DataResult<Pair<PhysicsBlockPropertiesDefinition, JsonElement>> decoded = PhysicsBlockPropertiesDefinition.CODEC.decode(JsonOps.INSTANCE, json);

            decoded.result().ifPresent(pair -> {
                final PhysicsBlockPropertiesDefinition definition = pair.getFirst();
                this.definitions.add(definition);
            });

            decoded.error().ifPresent(error -> {
                Sable.LOGGER.error("Error while loading physics block properties entry: {}", error);
            });
        }

        // Sort by priority
        this.definitions.sort(Comparator.comparingInt(PhysicsBlockPropertiesDefinition::priority));

                // [1.20.1 移植修正] 物理方块属性的应用不再放在此处。
        // 资源重载监听器 apply() 的触发时机仍早于 BuiltInRegistries.BLOCK 的方块标签注册完成，
        // 此时 getTag(...) 查询全部为空，会刷出大量 "Unknown tag" 且属性未写入。
        // 改为在标签真正就绪的 TagsUpdatedEvent 时统一应用（见 SableForge.onTagsUpdated），
        // 服务端世界加载与客户端 Ponder 教程均可正确生效。

    }

    /**
     * Applies a singular physics definition to a block or set of blocks
     */
    public static void applyToBlocks(final PhysicsBlockPropertiesDefinition definition) {
        final ExtraCodecs.TagOrElementLocation selector = definition.selector();
        final ObjectArrayList<Block> blocks = new ObjectArrayList<>(16);

        if (selector.tag()) {
            // The selector is a tag, let's pick all blocks
            final TagKey<Block> tagKey = TagKey.create(Registries.BLOCK, selector.id());
            final Optional<HolderSet.Named<Block>> tagBlocks = BuiltInRegistries.BLOCK.getTag(tagKey);

            if (tagBlocks.isPresent()) {
                final HolderSet.Named<Block> blockHolders = tagBlocks.get();

                for (final Holder<Block> blockHolder : blockHolders) {
                    final Block block = blockHolder.value();

                    blocks.add(block);
                }
            } else {
                Sable.LOGGER.error("Failed to apply tag physics properties. Unknown tag: {}", selector.id());
            }
        } else {
            if (BuiltInRegistries.BLOCK.containsKey(selector.id())) {
                // The selector is not a tag, let's just get the block
                final Block block = BuiltInRegistries.BLOCK.get(selector.id());
                blocks.add(block);
            } else {
                Sable.LOGGER.error("Failed to apply tag physics properties. Unknown block: {}", selector.id());
            }
        }

        for (final Block block : blocks) {
            final StateDefinition<Block, BlockState> stateDefinition = block.getStateDefinition();
            for (final BlockState state : stateDefinition.getPossibleStates()) {
                ((BlockStateExtension) state).sable$loadProperties(stateDefinition, definition);
            }
        }
    }

    /**
     * Applies all registered physics definitions to blocks
     */
    public void applyAll() {
        for (final PhysicsBlockPropertiesDefinition definition : this.definitions) {
            applyToBlocks(definition);
        }
    }

    public Collection<PhysicsBlockPropertiesDefinition> getDefinitions() {
        return this.definitions;
    }
}
