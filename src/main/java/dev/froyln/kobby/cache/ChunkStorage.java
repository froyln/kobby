package dev.froyln.kobby.cache;

import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.datafix.DataFixesManager;
import net.minecraft.world.MinecraftException;
import net.minecraft.world.chunk.Chunk;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

final class ChunkStorage {
    private static final Object REGION_IO_LOCK = new Object();
    private static final ThreadPoolExecutor IO = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(128), runnable -> {
                Thread thread = new Thread(runnable, "Kobby cache I/O");
                thread.setDaemon(true);
                return thread;
            });
    private static final PendingSaveQueue<Chunk> PENDING_SAVES = new PendingSaveQueue<>(1024);
    private static final Object SAVE_LOCK = new Object();
    private static final ExecutorService SAVE_IO = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Kobby cache writer");
        thread.setDaemon(true);
        return thread;
    });
    private static volatile boolean warnedSaveBacklog;
    private static boolean saveDrainScheduled;
    private static boolean saveFailed;

    private final CachedChunkLoader loader;
    private final CacheRetention retention;
    private final File directory;
    private final FallbackWorld fallback;

    ChunkStorage(File directory, CacheRetention retention, FallbackWorld fallback) {
        this.directory = directory;
        this.fallback = fallback;
        this.retention = retention;
        this.loader = new CachedChunkLoader(directory, DataFixesManager.createFixer());
    }

    void save(WorldClient world, Chunk chunk) {
        Runnable task = () -> {
            synchronized (REGION_IO_LOCK) {
                if (retention == null || !retention.canSave()) {
                    return;
                }
                File region = new File(new File(directory, "region"),
                        "r." + (chunk.x >> 5) + "." + (chunk.z >> 5) + ".mca");
                long before = region.length();
                try {
                    loader.saveSnapshot(world, chunk);
                } catch (IOException | MinecraftException e) {
                    throw new IllegalStateException("Terrain snapshot write failed", e);
                } finally {
                    retention.recordSizeChange(region.length() - before);
                }
            }
        };
        enqueueSave(saveKey(chunk.x, chunk.z), chunk, task);
    }

    static void enqueueSave(String key, Chunk chunk, Runnable task) {
        if (PENDING_SAVES.offer(key, chunk, task) && !warnedSaveBacklog) {
            warnedSaveBacklog = true;
            System.err.println("[Kobby] Cache save backlog is full; the oldest pending chunk save was dropped.");
        }
        synchronized (SAVE_LOCK) {
            if (!saveDrainScheduled) {
                saveDrainScheduled = true;
                SAVE_IO.execute(ChunkStorage::drainSaves);
            }
        }
    }

    static boolean flush() {
        Future<?> drained = SAVE_IO.submit(ChunkStorage::drainSaves);
        boolean interrupted = false;
        try {
            while (true) {
                try {
                    drained.get();
                    synchronized (SAVE_LOCK) {
                        boolean success = !saveFailed;
                        saveFailed = false;
                        return success;
                    }
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
        } catch (ExecutionException e) {
            throw new IllegalStateException("Could not drain terrain cache saves", e.getCause());
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void drainSaves() {
        while (true) {
            Runnable task = PENDING_SAVES.poll();
            if (task != null) {
                try {
                    task.run();
                } catch (RuntimeException e) {
                    synchronized (SAVE_LOCK) {
                        saveFailed = true;
                    }
                    e.printStackTrace();
                }
                continue;
            }
            synchronized (SAVE_LOCK) {
                if (PENDING_SAVES.size() == 0) {
                    saveDrainScheduled = false;
                    return;
                }
            }
        }
    }

    void load(WorldClient world, int x, int z, Object token) {
        try {
            IO.execute(() -> {
                loadNow(world, x, z, token);
            });
        } catch (RejectedExecutionException full) {
            ChunkCache.loadDeferred(world, x, z, token);
        }
    }

    boolean scan(WorldClient world, int centerX, int centerZ, int distance, int outerDistance, int generation, LongSet live) {
        try {
            IO.execute(() -> {
                for (int radius = 0; radius <= Math.max(distance, outerDistance); radius++) {
                    if (!ChunkCache.scanCurrent(world, generation)) return;
                    for (int dx = -radius; dx <= radius; dx++) {
                        int x = centerX + dx;
                        if (Math.abs(dx) == radius) {
                            for (int dz = -radius; dz <= radius; dz++) {
                                scanColumn(world, x, centerZ + dz, radius, distance, outerDistance, generation, live);
                            }
                        } else {
                            scanColumn(world, x, centerZ - radius, radius, distance, outerDistance, generation, live);
                            scanColumn(world, x, centerZ + radius, radius, distance, outerDistance, generation, live);
                        }
                    }
                }
            });
            return true;
        } catch (RejectedExecutionException full) {
            return false;
        }
    }

    private void scanColumn(WorldClient world, int x, int z, int radius, int distance,
                            int outerDistance, int generation, LongSet live) {
        if (!ChunkCache.scanCurrent(world, generation)) return;
        long key = ChunkPos.asLong(x, z);
        if (live.contains(key)) return;
        if (radius <= distance) {
            Object token = ChunkCache.beginLoad(world, x, z);
            if (token != null) loadNow(world, x, z, token);
        }
        if (radius > distance - 2 && radius <= outerDistance && ChunkCache.needsFar(world, key, generation)) {
            Chunk chunk = readSnapshot(world, x, z);
            ChunkCache.farLoaded(world, key, generation, chunk == null ? 0 : DistantTerrain.sampleFar(chunk));
        }
    }

    private String saveKey(int x, int z) {
        return directory.getAbsolutePath() + '|' + x + '|' + z;
    }

    private Chunk readSnapshot(WorldClient world, int x, int z) {
        Chunk chunk = null;
        try {
            synchronized (REGION_IO_LOCK) {
                Chunk pending = PENDING_SAVES.latest(saveKey(x, z));
                chunk = pending == null ? (loader.isChunkGeneratedAt(x, z) ? loader.loadChunk(world, x, z) : null)
                        : ChunkCache.copySnapshot(world, pending);
            }
        } catch (IOException | RuntimeException e) {
            e.printStackTrace();
        }
        if (chunk == null && ChunkCache.isFallbackEnabled()) {
            try {
                chunk = fallback.load(world, x, z, loader);
            } catch (IOException | RuntimeException e) {
                e.printStackTrace();
            }
        }
        return chunk;
    }

    private void loadNow(WorldClient world, int x, int z, Object token) {
        Chunk chunk = readSnapshot(world, x, z);
        if (chunk != null) {
            ChunkCache.loaded(world, x, z, chunk, token);
        } else {
            ChunkCache.loadFailed(world, x, z, token);
        }
    }
}
