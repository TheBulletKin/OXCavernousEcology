package com.oxythemoron.oxce.client.renderer.entity;

import com.google.common.collect.ImmutableMap;
import com.oxythemoron.oxce.OXCEEntities;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Map;


/*
Not a register but just a collection of entities mapping to a provider. Again a rendering thing that won't fire until the thing is created
Ties renderer to an entity to call when needed
 */

@OnlyIn(Dist.CLIENT)
public class OXCEEntityRenderers {

    //Set up a provider registry. Like suppliers it registers it then calls it when ready
    private static final Map<EntityType<?>, EntityRendererProvider<?>> PROVIDERS = new Object2ObjectOpenHashMap<>();

    //Give it the renderer to use when it's ready
    static {
        //Needs get here because it will need to get what the holder points to, not the holder itself
        register(OXCEEntities.SHIMMERFLY.get(), ShimmerflyRenderer::new);
    }

    public static <T extends Entity> void register(EntityType<? extends T> entityType, EntityRendererProvider<T> provider) {
        PROVIDERS.put(entityType, provider);
    }

    public static Map<EntityType<?>, EntityRenderer<?>> createEntityRenderers(EntityRendererProvider.Context context) {
        ImmutableMap.Builder<EntityType<?>, EntityRenderer<?>> builder = ImmutableMap.builder();
        PROVIDERS.forEach((p_339310_, p_339311_) -> {
            try {
                builder.put((EntityType<?>)p_339310_, p_339311_.create(context));
            } catch (Exception exception) {
                throw new IllegalArgumentException("Failed to create model for " + BuiltInRegistries.ENTITY_TYPE.getKey((EntityType<?>)p_339310_), exception);
            }
        });
        return builder.build();
    }
}
