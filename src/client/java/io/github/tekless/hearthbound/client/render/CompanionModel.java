package io.github.tekless.hearthbound.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.HumanoidArm;

/**
 * The vanilla player body (head, hat, jacket, sleeves, trouser layers) driven by companion render
 * state. It is baked from the game's own {@code player} / {@code player_slim} model layers, so
 * proportions match real players exactly - including 3px arms for slim skins.
 */
public class CompanionModel extends HumanoidModel<CompanionRenderState> {
	private final ModelPart leftSleeve;
	private final ModelPart rightSleeve;
	private final ModelPart leftPants;
	private final ModelPart rightPants;
	private final ModelPart jacket;
	private final boolean slim;

	public CompanionModel(ModelPart root, boolean slim) {
		super(root, RenderTypes::entityTranslucent);
		this.slim = slim;
		this.leftSleeve = leftArm.getChild("left_sleeve");
		this.rightSleeve = rightArm.getChild("right_sleeve");
		this.leftPants = leftLeg.getChild("left_pants");
		this.rightPants = rightLeg.getChild("right_pants");
		this.jacket = body.getChild("jacket");
	}

	@Override
	public void setupAnim(CompanionRenderState state) {
		hat.visible = true;
		jacket.visible = true;
		leftSleeve.visible = true;
		rightSleeve.visible = true;
		leftPants.visible = true;
		rightPants.visible = true;
		super.setupAnim(state);
		if (state.downed) {
			// Kneeling on the ground, head bowed (the "sitting" pose plus a slump).
			head.xRot = 0.6F;
			body.xRot = 0.35F;
			rightArm.xRot = -0.3F;
			leftArm.xRot = -0.3F;
		}
	}

	@Override
	public void translateToHand(CompanionRenderState state, HumanoidArm arm, PoseStack poseStack) {
		root().translateAndRotate(poseStack);
		ModelPart part = getArm(arm);
		if (slim) {
			float offset = 0.5F * (arm == HumanoidArm.RIGHT ? 1 : -1);
			part.x += offset;
			part.translateAndRotate(poseStack);
			part.x -= offset;
		} else {
			part.translateAndRotate(poseStack);
		}
	}
}
