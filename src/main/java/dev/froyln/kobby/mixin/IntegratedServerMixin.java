package dev.froyln.kobby.mixin;

import dev.froyln.kobby.cache.IntegratedServerDistance;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.server.integrated.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(IntegratedServer.class)
public abstract class IntegratedServerMixin {
    @Redirect(method = "tick", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/settings/GameSettings;renderDistanceChunks:I"), require = 3)
    private int kobby$liveDistance(GameSettings settings) {
        return IntegratedServerDistance.get(settings.renderDistanceChunks);
    }
}
