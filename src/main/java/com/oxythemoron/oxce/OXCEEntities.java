package com.oxythemoron.oxce;

import com.oxythemoron.oxce.client.entity.Shimmerfly;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/*
Full guide to first shimmerfly entity + notes - first making it
EntityType is the base that all other entities draw from, all reference it.
Deferred register is of course a register that is deferred, means it 'activates' later in mod loading, adding it to the base minecraft built in registries when the time comes
An entry needs a deferred holder connected to an entity type. Again it points to the entity but isn't the entity itself before the mod actually loads it
Two register methods just to confuse me.
key was the name of the entity and mod 'oxys.shimmerfly'
needs to supply the builder to make the entity when it is request (RESEARCH LATER)
Supply some attributes to the builder which are needed for every mob
 */


public class OXCEEntities {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, OxysCavernousEcology.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<Shimmerfly>> SHIMMERFLY = ENTITY_TYPES.register("shimmerfly", () -> EntityType.Builder.of(Shimmerfly::new, MobCategory.AMBIENT)
            .sized(0.5F, 0.9F).eyeHeight(0.45F).clientTrackingRange(5).build("shimmerfly"));


}
