package dev.froyln.kobby.cache;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.lwjgl.opengl.GL11;

public final class DistantTerrain {
    private static final int FIRST_LOD_CHUNK = 12;
    private static final int CELLS = 4;
    private static final int STEP = 16 / CELLS;
    private static final int BUILDS_PER_TICK = 8;
    private static final Long2ObjectOpenHashMap<Surface> SURFACES = new Long2ObjectOpenHashMap<>();
    private static final Map<Long, Long> FAR = new ConcurrentHashMap<>();
    private static final LongOpenHashSet FAR_MISSES = new LongOpenHashSet();
    private static volatile boolean enabled;
    private static WorldClient currentWorld;

    private DistantTerrain() {}

    public static void configure(boolean lod) {
        enabled = lod;
    }

    static boolean isEnabled() {
        return enabled;
    }

    static void clearFar() {
        FAR.clear();
        FAR_MISSES.clear();
    }

    static void pruneFar(int centerX, int centerZ, int near, int far) {
        FAR.keySet().removeIf(key -> !inOuterBand(key, centerX, centerZ, near, far));
        FAR_MISSES.removeIf(key -> !inOuterBand(key, centerX, centerZ, near, far));
    }

    static boolean inOuterBand(long key, int centerX, int centerZ, int near, int far) {
        return far > 0 && !ChunkCache.withinDistance(key, centerX, centerZ, near)
                && ChunkCache.withinDistance(key, centerX, centerZ, far);
    }

    static boolean hasFar(long key) {
        return FAR.containsKey(key) || FAR_MISSES.contains(key);
    }

    static void removeFar(long key) {
        FAR.remove(key);
        FAR_MISSES.remove(key);
    }

    static void putFar(long key, long sample) {
        if (sample == 0) {
            FAR.remove(key);
            FAR_MISSES.add(key);
        } else {
            FAR_MISSES.remove(key);
            FAR.put(key, sample);
        }
    }

    public static synchronized void reset(World world) {
        SURFACES.clear();
        currentWorld = world instanceof WorldClient ? (WorldClient) world : null;
    }

    public static boolean usesLod(World world, Chunk snapshot) {
        if (snapshot == null) return false;
        synchronized (DistantTerrain.class) {
            Surface surface = world == currentWorld ? SURFACES.get(ChunkPos.asLong(snapshot.x, snapshot.z)) : null;
            return surface != null && surface.source == snapshot;
        }
    }

    public static void update(WorldClient world, int centerX, int centerZ, int distance, boolean enabled) {
        if (world == null) return;
        enabled = enabled && ChunkCache.isEnabled() && world.provider.hasSkyLight();
        long[] positions = enabled ? ChunkCache.positions(world) : new long[0];
        LongArrayList changed = new LongArrayList();
        synchronized (DistantTerrain.class) {
            if (currentWorld != world) return;
            LongIterator keys = SURFACES.keySet().iterator();
            while (keys.hasNext()) {
                long key = keys.nextLong();
                if (!enabled || !inBand(key, centerX, centerZ, distance)
                        || ChunkCache.resident(world, key) != SURFACES.get(key).source) {
                    keys.remove();
                    changed.add(key);
                }
            }
        }

        int built = 0;
        for (long key : positions) {
            if (!inBand(key, centerX, centerZ, distance) || SURFACES.containsKey(key)) continue;
            Chunk snapshot = ChunkCache.resident(world, key);
            if (snapshot == null || world.getChunkProvider().getLoadedChunk(snapshot.x, snapshot.z) != null) continue;
            Surface surface = new Surface(snapshot);
            synchronized (DistantTerrain.class) {
                SURFACES.put(key, surface);
            }
            changed.add(key);
            if (++built == BUILDS_PER_TICK) break;
        }

        for (long key : changed) {
            ChunkCache.invalidateColumn(world, ChunkCache.chunkX(key), ChunkCache.chunkZ(key));
        }
    }

    static boolean inBand(long key, int centerX, int centerZ, int distance) {
        return !ChunkCache.withinDistance(key, centerX, centerZ, FIRST_LOD_CHUNK - 1)
                && ChunkCache.withinDistance(key, centerX, centerZ, distance);
    }

    public static void draw(Minecraft client, Entity camera, double ticks) {
        if (client.world != currentWorld || !enabled || (SURFACES.isEmpty() && FAR.isEmpty()) || !ChunkCache.isEnabled()) return;
        double x = camera.lastTickPosX + (camera.posX - camera.lastTickPosX) * ticks;
        double y = camera.lastTickPosY + (camera.posY - camera.lastTickPosY) * ticks;
        double z = camera.lastTickPosZ + (camera.posZ - camera.lastTickPosZ) * ticks;
        Frustum frustum = new Frustum();
        frustum.setPosition(x, y, z);

        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.disableTexture2D();
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        try {
            buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
            buffer.setTranslation(-x, -y, -z);
            float daylight = 0.35F + 0.65F * currentWorld.getSunBrightness((float) ticks);
            for (Surface surface : SURFACES.values()) {
                if (!visible(surface) || surface.maxHeight == 0) continue;
                int blockX = surface.source.x << 4;
                int blockZ = surface.source.z << 4;
                if (frustum.isBoxInFrustum(blockX, 0, blockZ,
                        blockX + 16, surface.maxHeight, blockZ + 16)) {
                    float light = CachedTerrainTint.applies(currentWorld, surface.source.x, surface.source.z)
                            ? daylight * 0.75F : daylight;
                    surface.draw(buffer, blockX, blockZ, light);
                }
            }
            drawFar(buffer, frustum, CachedTerrainTint.isEnabled() ? daylight * 0.75F : daylight);
            tessellator.draw();
        } finally {
            buffer.setTranslation(0, 0, 0);
            GlStateManager.enableTexture2D();
        }
    }

    private static void drawFar(BufferBuilder buffer, Frustum frustum, float light) {
        for (Map.Entry<Long, Long> entry : FAR.entrySet()) {
            int x = ChunkCache.chunkX(entry.getKey());
            int z = ChunkCache.chunkZ(entry.getKey());
            int x0 = x << 4;
            int z0 = z << 4;
            long sample = entry.getValue();
            int y = (int) (sample >>> 24);
            if (!frustum.isBoxInFrustum(x0, 0, z0, x0 + 16, y, z0 + 16)
                    || currentWorld.getChunkProvider().getLoadedChunk(x, z) != null) continue;
            int color = (int) sample & 0xffffff;
            top(buffer, x0, z0, x0 + 16, z0 + 16, y, color, light);
            wall(buffer, x0, z0, x0 + 16, z0, y, farHeight(x, z - 1), color, light * 0.8F);
            wall(buffer, x0 + 16, z0 + 16, x0, z0 + 16, y, farHeight(x, z + 1), color, light * 0.8F);
            wall(buffer, x0, z0 + 16, x0, z0, y, farHeight(x - 1, z), color, light * 0.65F);
            wall(buffer, x0 + 16, z0, x0 + 16, z0 + 16, y, farHeight(x + 1, z), color, light * 0.65F);
        }
    }

    private static int farHeight(int x, int z) {
        Long sample = FAR.get(ChunkPos.asLong(x, z));
        return sample == null ? 0 : (int) (sample >>> 24);
    }

    static long sampleFar(Chunk chunk) {
        ExtendedBlockStorage[] sections = chunk.getBlockStorageArray();
        return sample(sections, sections.length - 1, 8, 8);
    }

    private static long sample(ExtendedBlockStorage[] sections, int top, int x, int z) {
        for (int y = (top + 1) * 16 - 1; y >= 0; y--) {
            ExtendedBlockStorage section = sections[y >> 4];
            if (section == null || section.isEmpty()) {
                y &= ~15;
                continue;
            }
            IBlockState state = section.get(x, y & 15, z);
            MapColor color = state.getMaterial().getMaterialMapColor();
            if (color != MapColor.AIR && (state.getMaterial().blocksMovement() || state.getMaterial().isLiquid())) {
                return ((long) (y + 1) << 24) | color.colorValue;
            }
        }
        return 0;
    }

    private static boolean visible(Surface surface) {
        int x = surface.source.x;
        int z = surface.source.z;
        return currentWorld.getChunkProvider().getLoadedChunk(x, z) == null
                && ChunkCache.resident(currentWorld, ChunkPos.asLong(x, z)) == surface.source;
    }

    static int wallBottom(int height, int neighborHeight) {
        // shortcut: Short skirts at missing neighbors. Use edge samples if visible seams need fixing.
        return neighborHeight == 0 ? Math.max(0, height - STEP) : neighborHeight;
    }

    private static final class Surface {
        final Chunk source;
        final int[] heights = new int[CELLS * CELLS];
        final int[] colors = new int[CELLS * CELLS];
        int maxHeight;

        Surface(Chunk source) {
            this.source = source;
            ExtendedBlockStorage[] sections = source.getBlockStorageArray();
            int top = sections.length - 1;
            while (top >= 0 && (sections[top] == null || sections[top].isEmpty())) top--;
            for (int z = 0; z < CELLS; z++) {
                for (int x = 0; x < CELLS; x++) {
                    int index = z * CELLS + x;
                    long sample = sample(sections, top, x * STEP + STEP / 2, z * STEP + STEP / 2);
                    heights[index] = (int) (sample >>> 24);
                    colors[index] = (int) sample & 0xffffff;
                    maxHeight = Math.max(maxHeight, heights[index]);
                }
            }
        }

        private Surface neighbor(int dx, int dz) {
            Surface other = SURFACES.get(ChunkPos.asLong(source.x + dx, source.z + dz));
            return other != null && visible(other) ? other : null;
        }

        private static int height(Surface surface, int x, int z) {
            return surface == null ? 0 : surface.heights[z * CELLS + x];
        }

        void draw(BufferBuilder buffer, int blockX, int blockZ, float light) {
            Surface north = neighbor(0, -1);
            Surface south = neighbor(0, 1);
            Surface west = neighbor(-1, 0);
            Surface east = neighbor(1, 0);
            for (int z = 0; z < CELLS; z++) {
                for (int x = 0; x < CELLS; x++) {
                    int index = z * CELLS + x;
                    int y = heights[index];
                    if (y == 0) continue;
                    int x0 = blockX + x * STEP;
                    int z0 = blockZ + z * STEP;
                    int x1 = x0 + STEP;
                    int z1 = z0 + STEP;
                    int color = colors[index];
                    top(buffer, x0, z0, x1, z1, y, color, light);
                    wall(buffer, x0, z0, x1, z0, y, z == 0 ? height(north, x, CELLS - 1) : heights[index - CELLS], color, light * 0.8F);
                    wall(buffer, x1, z1, x0, z1, y, z == CELLS - 1 ? height(south, x, 0) : heights[index + CELLS], color, light * 0.8F);
                    wall(buffer, x0, z1, x0, z0, y, x == 0 ? height(west, CELLS - 1, z) : heights[index - 1], color, light * 0.65F);
                    wall(buffer, x1, z0, x1, z1, y, x == CELLS - 1 ? height(east, 0, z) : heights[index + 1], color, light * 0.65F);
                }
            }
        }
    }

    private static void top(BufferBuilder buffer, int x0, int z0, int x1, int z1,
                            int height, int color, float light) {
        vertex(buffer, x0, height, z0, color, light);
        vertex(buffer, x0, height, z1, color, light);
        vertex(buffer, x1, height, z1, color, light);
        vertex(buffer, x1, height, z0, color, light);
    }

    private static void wall(BufferBuilder buffer, int x0, int z0, int x1, int z1,
                             int height, int neighbor, int color, float light) {
        int bottom = wallBottom(height, neighbor);
        if (bottom >= height) return;
        vertex(buffer, x0, height, z0, color, light);
        vertex(buffer, x1, height, z1, color, light);
        vertex(buffer, x1, bottom, z1, color, light);
        vertex(buffer, x0, bottom, z0, color, light);
    }

    private static void vertex(BufferBuilder buffer, int x, int y, int z, int color, float light) {
        buffer.pos(x, y, z).color((int) ((color >> 16 & 255) * light),
                (int) ((color >> 8 & 255) * light), (int) ((color & 255) * light), 255).endVertex();
    }
}
