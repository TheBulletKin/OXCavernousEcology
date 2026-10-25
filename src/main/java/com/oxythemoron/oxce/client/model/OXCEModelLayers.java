package com.oxythemoron.oxce.client.model;

import com.google.common.collect.Sets;
import com.oxythemoron.oxce.OxysCavernousEcology;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Set;

/*
Model layers are essentially the models themselves
Set can't have more than one
Model location
 */

@OnlyIn(Dist.CLIENT)
public class OXCEModelLayers {

    private static final Set<ModelLayerLocation> ALL_MODELS = Sets.newHashSet();

    public static final ModelLayerLocation SHIMMERFLY = register("shimmerfly");

    private static ModelLayerLocation register(String path) {
        return register(path, "main");
    }

    private static ModelLayerLocation register(String path, String model) {
        ModelLayerLocation modellayerlocation = createLocation(path, model);
        if (!ALL_MODELS.add(modellayerlocation)) {
            throw new IllegalStateException("Duplicate registration for " + String.valueOf(modellayerlocation));
        } else {
            return modellayerlocation;
        }
    }

    private static ModelLayerLocation createLocation(String path, String model) {
        return new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(OxysCavernousEcology.MODID, path), model);
    }
}
