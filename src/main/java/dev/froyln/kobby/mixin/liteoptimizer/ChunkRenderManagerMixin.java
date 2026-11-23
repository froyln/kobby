package dev.froyln.kobby.mixin.liteoptimizer;

import dev.froyln.kobby.cache.RenderChunkLookup;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderManager", remap = false)
public abstract class ChunkRenderManagerMixin {
    @Redirect(method = "createChunkRender", remap = false, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/WorldClient;getChunk(II)Lnet/minecraft/world/chunk/Chunk;", remap = true))
    private Chunk kobby$renderOnlyChunk(WorldClient world, int x, int z) {
        return RenderChunkLookup.getCachedOrLive(world, x, z, world.getChunkProvider());
    }
}
