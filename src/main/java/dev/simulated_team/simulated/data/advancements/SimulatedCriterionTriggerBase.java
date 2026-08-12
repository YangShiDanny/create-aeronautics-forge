package dev.simulated_team.simulated.data.advancements;

import com.google.common.collect.Maps;
import com.google.gson.JsonObject;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.advancements.CriterionTriggerInstance;
import net.minecraft.advancements.critereon.DeserializationContext;
import net.minecraft.advancements.critereon.SerializationContext;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.*;
import java.util.function.Supplier;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public abstract class SimulatedCriterionTriggerBase<T extends SimulatedCriterionTriggerBase.Instance> implements CriterionTrigger<T> {

    private final ResourceLocation id;
    protected final Map<PlayerAdvancements, Set<Listener<T>>> listeners = Maps.newHashMap();

    public SimulatedCriterionTriggerBase(final ResourceLocation id) {
        this.id = id;
    }

    @Override
    public void addPlayerListener(final PlayerAdvancements pPlayerAdvancements, final Listener<T> pListener) {
        final Set<Listener<T>> playerListeners = this.listeners.computeIfAbsent(pPlayerAdvancements, k -> new HashSet<>());
        playerListeners.add(pListener);
    }

    @Override
    public void removePlayerListener(final PlayerAdvancements pPlayerAdvancements, final Listener<T> pListener) {
        final Set<Listener<T>> playerListeners = this.listeners.get(pPlayerAdvancements);
        if(playerListeners != null)  {
            playerListeners.remove(pListener);
            if(playerListeners.isEmpty()) {
                this.listeners.remove(pPlayerAdvancements);
            }
        }
    }

    @Override
    public void removePlayerListeners(final PlayerAdvancements pPlayerAdvancements) {
        this.listeners.remove(pPlayerAdvancements);
    }

    public ResourceLocation getId() {
        return this.id;
    }

    @Override
    public abstract T createInstance(JsonObject json, DeserializationContext context);

    protected void trigger(final ServerPlayer player,  final List<Supplier<Object>> suppliers) {
        final PlayerAdvancements playerAdvancements = player.getAdvancements();
        final Set<Listener<T>> playerListeners = this.listeners.get(playerAdvancements);
        if(playerListeners != null) {
            final List<Listener<T>> list = new LinkedList<>();

            for (final Listener<T> listener : playerListeners) {
                if(listener.getTriggerInstance().test(suppliers)) {
                    list.add(listener);
                }
            }

            list.forEach(listener -> listener.run(playerAdvancements));
        }
    }

    public abstract static class Instance implements CriterionTriggerInstance {
        private final ResourceLocation id;
        public Instance(final ResourceLocation id) {
            this.id = id;
        }
        @Override
        public ResourceLocation getCriterion() {
            return this.id;
        }
        public ResourceLocation getId() {
            return this.id;
        }
        @Override
        public com.google.gson.JsonObject serializeToJson(final SerializationContext context) {
            return new com.google.gson.JsonObject();
        }
        protected abstract boolean test ( List<Supplier<Object>> suppliers);
    }
}