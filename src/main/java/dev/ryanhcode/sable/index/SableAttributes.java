package dev.ryanhcode.sable.index;

import com.google.common.collect.ImmutableMap;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraftforge.registries.RegistryObject;

import java.util.Map;
import java.util.Objects;

public class SableAttributes {

    public static final String PUNCH_STRENGTH_NAME = "player.sub_level_punch_strength";
    public static final Attribute PUNCH_STRENGTH_ATTRIBUTE = new RangedAttribute("attribute.name." + PUNCH_STRENGTH_NAME, 1.0, -100.0, 100.0).setSyncable(true);

    public static final String PUNCH_COOLDOWN_NAME = "player.sub_level_punch_cooldown";
    public static final Attribute PUNCH_COOLDOWN_ATTRIBUTE = new RangedAttribute("attribute.name." + PUNCH_COOLDOWN_NAME, 0.0, 0, 10).setSyncable(true);

    /**
     * Assigned by loader-specific code
     */
    public static RegistryObject<Attribute> PUNCH_STRENGTH;
    public static RegistryObject<Attribute> PUNCH_COOLDOWN;

    public static void register() {

        final AttributeSupplier supplier = DefaultAttributes.getSupplier(EntityType.PLAYER);

        final Map<Attribute, AttributeInstance> additionalInstances = AttributeSupplier.builder().add(PUNCH_STRENGTH.get()).add(PUNCH_COOLDOWN.get()).build().instances;

        // java was tweaking with generics
        //noinspection unchecked,rawtypes
        supplier.instances = (Map<Attribute, AttributeInstance>) (ImmutableMap) ImmutableMap.builder()
                .putAll(supplier.instances)
                .putAll(additionalInstances)
                .buildKeepingLast();
    }

    public static int getPushCooldownTicks(final LivingEntity entity) {
        return Mth.ceil(Objects.requireNonNull(entity.getAttribute(PUNCH_COOLDOWN.get())).getValue() * 20);
    }
}
