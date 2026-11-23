package dev.froyln.kobby.mixin;

import dev.froyln.kobby.cache.ChunkCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Shadow public WorldClient world;

    @Inject(method = "loadWorld(Lnet/minecraft/client/multiplayer/WorldClient;Ljava/lang/String;)V", at = @At("HEAD"))
    private void kobby$captureDepartingWorld(WorldClient nextWorld, String message, CallbackInfo ci) {
        if (world != null && world != nextWorld) {
            ChunkCache.captureRemaining(world);
        }
    }

    @Inject(method = "shutdownMinecraftApplet", at = @At("HEAD"))
    private void kobby$finishSaves(CallbackInfo ci) {
        ChunkCache.flushPendingSaves();
    }
}
