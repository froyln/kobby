package dev.froyln.kobby.mixin;

import dev.froyln.kobby.cache.RenderChunkLookup;
import dev.froyln.kobby.cache.TintedChunkView;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ChunkCache;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(RenderChunk.class)
public abstract class RenderChunkMixin {
    @Redirect(method = "rebuildWorldView", at = @At(value = "NEW", target = "net/minecraft/world/ChunkCache"))
    private ChunkCache kobby$useCachedChunks(World world, BlockPos from, BlockPos to, int subtract) {
        try (RenderChunkLookup.ChunkCacheScope ignored = RenderChunkLookup.beginCacheBuild()) {
            return new TintedChunkView(world, from, to, subtract);
        }
    }
}
