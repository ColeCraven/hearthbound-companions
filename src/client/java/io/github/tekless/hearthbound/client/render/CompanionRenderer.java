package io.github.tekless.hearthbound.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.tekless.hearthbound.entity.CompanionEntity;
import io.github.tekless.hearthbound.entity.CompanionSkin;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/**
 * Draws companions with the player model and one of the game's built-in default skins. Wide and
 * slim skins need different arm geometry, so the renderer holds both models and picks per entity.
 */
public class CompanionRenderer extends HumanoidMobRenderer<CompanionEntity, CompanionRenderState, CompanionModel> {
	private final CompanionModel wideModel;
	private final CompanionModel slimModel;

	public CompanionRenderer(EntityRendererProvider.Context context) {
		super(context, new CompanionModel(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
		this.wideModel = this.model;
		this.slimModel = new CompanionModel(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
		addLayer(new GatedArmorLayer(this, ArmorModelSet.bake(ModelLayers.PLAYER_ARMOR, context.getModelSet(), part -> new CompanionModel(part, false)),
				context.getEquipmentRenderer(), false));
		addLayer(new GatedArmorLayer(this, ArmorModelSet.bake(ModelLayers.PLAYER_SLIM_ARMOR, context.getModelSet(), part -> new CompanionModel(part, true)),
				context.getEquipmentRenderer(), true));
	}

	@Override
	public CompanionRenderState createRenderState() {
		return new CompanionRenderState();
	}

	@Override
	public void extractRenderState(CompanionEntity entity, CompanionRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		int variant = entity.skinVariant();
		state.texture = Identifier.withDefaultNamespace(CompanionSkin.texturePath(variant));
		state.slim = CompanionSkin.decodeSlim(variant);
		state.downed = entity.isDowned();
		if (state.downed) {
			state.isPassenger = true; // reuse the vanilla sitting leg pose for kneeling
		}
	}

	@Override
	public Identifier getTextureLocation(CompanionRenderState state) {
		return state.texture;
	}

	@Override
	public void submit(CompanionRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		this.model = state.slim ? slimModel : wideModel;
		super.submit(state, poseStack, collector, camera);
	}

	@Override
	protected void scale(CompanionRenderState state, PoseStack poseStack) {
		// Same 15/16 scale the game applies to players.
		poseStack.scale(0.9375F, 0.9375F, 0.9375F);
	}

	@Override
	public Vec3 getRenderOffset(CompanionRenderState state) {
		if (state.downed) {
			return new Vec3(0, -0.55, 0);
		}
		Vec3 offset = super.getRenderOffset(state);
		return state.isCrouching ? offset.add(0, -0.125, 0) : offset;
	}

	/** Armour layer that only draws for the matching arm width. */
	private static final class GatedArmorLayer extends HumanoidArmorLayer<CompanionRenderState, CompanionModel, CompanionModel> {
		private final boolean slim;

		GatedArmorLayer(RenderLayerParent<CompanionRenderState, CompanionModel> parent, ArmorModelSet<CompanionModel> models,
				EquipmentLayerRenderer equipment, boolean slim) {
			super(parent, models, equipment);
			this.slim = slim;
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, CompanionRenderState state, float yRot, float xRot) {
			if (state.slim == slim) {
				super.submit(poseStack, collector, lightCoords, state, yRot, xRot);
			}
		}
	}
}
