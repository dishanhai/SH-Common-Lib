// Author: dishanhai
package com.dishanhai.sh_common_lib.client.jei;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.SimpleBakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;

import java.util.ArrayList;
import java.util.List;

/** Keeps the atlas UVs and original render types of flat generated icons. */
public final class JeiFlatItemModel extends BakedModelWrapper<BakedModel> {
    private static final Cache<BakedModel, BakedModel> MODELS = CacheBuilder.newBuilder()
            .weakKeys().weakValues().maximumSize(4096).build();

    private final List<BakedQuad> front;

    private JeiFlatItemModel(BakedModel original, List<BakedQuad> front) {
        super(original);
        this.front = front;
    }

    public static BakedModel forGui(BakedModel model) {
        if (!JeiSearchPreferences.current().optimizeRendering() || model.getClass() != SimpleBakedModel.class
                || model.isGui3d() || model.getTransforms().gui != ItemTransform.NO_TRANSFORM) {
            return model;
        }
        BakedModel cached = MODELS.getIfPresent(model);
        if (cached != null) return cached;
        BakedModel flat = create(model);
        MODELS.put(model, flat);
        return flat;
    }

    private static BakedModel create(BakedModel model) {
        var random = RandomSource.create(42L);
        for (Direction direction : Direction.values()) {
            if (!model.getQuads(null, direction, random).isEmpty()) return model;
        }

        var quads = model.getQuads(null, null, random);
        var front = new ArrayList<BakedQuad>();
        for (BakedQuad quad : quads) {
            int[] vertices = quad.getVertices();
            int stride = vertices.length / 4;
            boolean isFront = quad.getDirection() == Direction.SOUTH;
            float expectedZ = (isFront ? 8.5F : 7.5F) / 16.0F;
            for (int vertex = 0; vertex < 4; vertex++) {
                float z = Float.intBitsToFloat(vertices[vertex * stride + 2]);
                if (isFront || quad.getDirection() == Direction.NORTH) {
                    if (z != expectedZ) return model;
                } else if (z != 7.5F / 16.0F && z != 8.5F / 16.0F) {
                    return model;
                }
            }
            if (isFront) front.add(quad);
        }
        return front.isEmpty() || front.size() == quads.size() ? model : new JeiFlatItemModel(model, List.copyOf(front));
    }

    @Override
    public BakedModel applyTransform(ItemDisplayContext context, PoseStack pose, boolean leftHand) {
        BakedModel transformed = originalModel.applyTransform(context, pose, leftHand);
        return transformed == originalModel && context == ItemDisplayContext.GUI ? this : transformed;
    }

    @Override
    public List<BakedModel> getRenderPasses(ItemStack stack, boolean fabulous) {
        var passes = originalModel.getRenderPasses(stack, fabulous);
        return passes.size() == 1 && passes.get(0) == originalModel ? List.of(this) : passes;
    }

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random) {
        return side == null ? front : List.of();
    }

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random, ModelData data, RenderType type) {
        return getQuads(state, side, random);
    }
}
