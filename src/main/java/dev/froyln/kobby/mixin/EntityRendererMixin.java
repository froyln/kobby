package dev.froyln.kobby.mixin;

import dev.froyln.kobby.cache.CacheRenderDistance;
import dev.froyln.kobby.cache.DistantTerrain;
import dev.froyln.kobby.cache.RenderChunkLookup;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.settings.GameSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
    @Inject(method = "renderWorldPass", at = @At(value = "INVOKE", ordinal = 0, shift = At.Shift.AFTER,
            target = "Lnet/minecraft/client/renderer/RenderGlobal;renderBlockLayer(Lnet/minecraft/util/BlockRenderLayer;DILnet/minecraft/entity/Entity;)I"))
    private void kobby$drawDistantTerrain(int pass, float partialTicks, long finishTime, CallbackInfo ci) {
        Minecraft client = Minecraft.getMinecraft();
        Entity camera = RenderChunkLookup.camera(client);
        if (camera != null) DistantTerrain.draw(client, camera, partialTicks);
    }

    @Redirect(method = "setupCameraTransform", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/settings/GameSettings;renderDistanceChunks:I"))
    private int kobby$projectionDistance(GameSettings settings) {
        Minecraft client = Minecraft.getMinecraft();
        return CacheRenderDistance.projected(settings.renderDistanceChunks,
                client.world != null && client.world.provider.hasSkyLight());
    }

    @Redirect(method = "updateFogColor", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/settings/GameSettings;renderDistanceChunks:I"))
    private int kobby$fogColorDistance(GameSettings settings) {
        return CacheRenderDistance.get(settings.renderDistanceChunks);
    }
}
