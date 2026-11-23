package dev.froyln.kobby.mixin;

import dev.froyln.kobby.cache.ChunkCache;
import dev.froyln.kobby.cache.DistantTerrain;
import dev.froyln.kobby.cache.CacheRenderDistance;
import net.minecraft.client.settings.GameSettings;
import org.spongepowered.asm.mixin.injection.Redirect;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.RenderGlobal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderGlobal.class)
public abstract class RenderGlobalMixin {
    @Redirect(method = {"loadRenderers", "setupTerrain"}, at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/settings/GameSettings;renderDistanceChunks:I"))
    private int kobby$terrainDistance(GameSettings settings) {
        return CacheRenderDistance.get(settings.renderDistanceChunks);
    }

    @Inject(method = "setWorldAndLoadRenderers", at = @At("RETURN"))
    private void kobby$resetCacheForWorld(WorldClient world, CallbackInfo ci) {
        ChunkCache.begin(world);
        DistantTerrain.reset(world);
    }
}
