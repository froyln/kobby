package dev.froyln.kobby.cache;

public final class CacheRenderDistance {
    public static final int MAX_DISTANCE = 128;
    private static final int MAX_NATIVE_DISTANCE = 32;
    private static volatile int configured = 16;

    private CacheRenderDistance() {}

    public static void configure(int distance) {
        configured = Math.max(2, Math.min(MAX_DISTANCE, distance));
    }

    public static int get(int liveDistance) {
        return effective(liveDistance, configured, ChunkCache.isEnabled());
    }

    public static int projected(int liveDistance, boolean hasSkyLight) {
        return projected(liveDistance, configured, ChunkCache.isEnabled(), DistantTerrain.isEnabled() && hasSkyLight);
    }

    static int projected(int liveDistance, int cachedDistance, boolean enabled, boolean lod) {
        return enabled && lod ? Math.max(liveDistance, Math.min(MAX_DISTANCE, Math.max(2, cachedDistance)))
                : effective(liveDistance, cachedDistance, enabled);
    }

    static int effective(int liveDistance, int cachedDistance, boolean enabled) {
        return enabled ? Math.max(liveDistance, Math.max(2, Math.min(MAX_NATIVE_DISTANCE, cachedDistance))) : liveDistance;
    }
}
