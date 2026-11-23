package dev.froyln.kobby.mixin.liteoptimizer;

import dev.froyln.kobby.cache.RenderChunkLookup;
import dev.froyln.kobby.cache.CachedTerrainTint;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.client.multiplayer.ChunkProviderClient;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.world.WorldSlice", remap = false)
public abstract class WorldSliceMixin {
    @Shadow @Final private World world;
    @Shadow private int baseX;
    @Shadow private int baseZ;
    @Unique private boolean kobby$tinted;
    @Unique private float kobby$gamma;

    @Inject(method = "copyData", at = @At("RETURN"), remap = false)
    private void kobby$captureTint(CallbackInfo ci) {
        // The pinned WorldSlice stores a one-column neighbor border before its origin.
        kobby$tinted = CachedTerrainTint.applies(world, (baseX >> 4) + 1, (baseZ >> 4) + 1);
        kobby$gamma = net.minecraft.client.Minecraft.getMinecraft().gameSettings.gammaSetting;
    }

    @Inject(method = "getLightFromNeighborsFor", at = @At("RETURN"), cancellable = true, remap = false)
    private void kobby$shadeLight(EnumSkyBlock type, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        if (kobby$tinted) cir.setReturnValue(type == EnumSkyBlock.SKY
                ? CachedTerrainTint.sky(cir.getReturnValueI(), kobby$gamma)
                : CachedTerrainTint.block(cir.getReturnValueI()));
    }

    @Redirect(method = "prepare", remap = false, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;getChunk(II)Lnet/minecraft/world/chunk/Chunk;", remap = true))
    private static Chunk kobby$renderOnlyChunk(World world, int x, int z) {
        if (world instanceof WorldClient) {
            WorldClient clientWorld = (WorldClient) world;
            return RenderChunkLookup.getCachedOrLive(world, x, z, clientWorld.getChunkProvider());
        }
        return world.getChunk(x, z);
    }
}
