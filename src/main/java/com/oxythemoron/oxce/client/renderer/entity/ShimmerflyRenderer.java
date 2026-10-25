package com.oxythemoron.oxce.client.renderer.entity;

import com.oxythemoron.oxce.client.entity.Shimmerfly;
import com.oxythemoron.oxce.client.model.OXCEModelLayers;
import com.oxythemoron.oxce.client.model.ShimmerflyModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/*
Just uses base mob renderer with nothing fancy
 */
public class ShimmerflyRenderer extends MobRenderer<Shimmerfly, ShimmerflyModel> {
    public static final ResourceLocation SHIMMERFLY_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/bat.png");

    public ShimmerflyRenderer(EntityRendererProvider.Context p_173929_) {
        super(p_173929_, new ShimmerflyModel(p_173929_.bakeLayer(OXCEModelLayers.SHIMMERFLY)), 0.25F);
    }

    @Override
    public ResourceLocation getTextureLocation(Shimmerfly shimmerfly) {
        return SHIMMERFLY_LOCATION;
    }

}
