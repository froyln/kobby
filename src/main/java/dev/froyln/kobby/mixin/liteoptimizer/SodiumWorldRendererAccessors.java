package dev.froyln.kobby.mixin.liteoptimizer;

import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer", remap = false)
public interface SodiumWorldRendererAccessors {
    @Accessor("loadedChunkPositions")
    LongSet kobby$loadedChunks();

    @Accessor("client")
    Minecraft kobby$client();

    @Accessor("world")
    WorldClient kobby$world();

    @Invoker("onChunkAdded")
    void kobby$addChunk(int x, int z);

    @Invoker("onChunkRemoved")
    void kobby$removeChunk(int x, int z);
}
