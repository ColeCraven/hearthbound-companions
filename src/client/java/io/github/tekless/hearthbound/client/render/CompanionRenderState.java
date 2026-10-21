package io.github.tekless.hearthbound.client.render;

import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.Identifier;

/**
 * Render data for one companion. Deliberately not an {@code AvatarRenderState}: the game sends
 * those to the player renderer, and we want our own renderer (downed pose, wide/slim switch).
 */
public class CompanionRenderState extends HumanoidRenderState {
	public Identifier texture = DefaultPlayerSkin.getDefaultTexture();
	public boolean slim;
	public boolean downed;
}
