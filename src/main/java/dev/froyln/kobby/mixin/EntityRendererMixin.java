package dev.froyln.kobby.mixin;

import dev.froyln.kobby.cache.CacheRenderDistance;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.settings.GameSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
    @Redirect(method = {"setupCameraTransform", "updateFogColor"}, at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/settings/GameSettings;renderDistanceChunks:I"))
    private int kobby$terrainDistance(GameSettings settings) {
        return CacheRenderDistance.get(settings.renderDistanceChunks);
    }
}
