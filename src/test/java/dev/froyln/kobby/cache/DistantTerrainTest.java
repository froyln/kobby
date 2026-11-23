package dev.froyln.kobby.cache;

import net.minecraft.util.math.ChunkPos;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DistantTerrainTest {
    @Test
    public void lodStartsAtTwelveAndStopsAtVisibleDistance() {
        assertFalse(DistantTerrain.inBand(ChunkPos.asLong(-11, 0), 0, 0, 16));
        assertTrue(DistantTerrain.inBand(ChunkPos.asLong(-12, 12), 0, 0, 16));
        assertTrue(DistantTerrain.inBand(ChunkPos.asLong(0, -16), 0, 0, 16));
        assertFalse(DistantTerrain.inBand(ChunkPos.asLong(0, -17), 0, 0, 16));
        assertFalse(DistantTerrain.inBand(ChunkPos.asLong(-12, 12), -1, 1, 16));
    }

    @Test
    public void outerBandCoversOnlyColumnsPastNativeDistanceThrough128() {
        assertFalse(DistantTerrain.inOuterBand(ChunkPos.asLong(32, 0), 0, 0, 32, 128));
        assertTrue(DistantTerrain.inOuterBand(ChunkPos.asLong(-33, 0), 0, 0, 32, 128));
        assertTrue(DistantTerrain.inOuterBand(ChunkPos.asLong(-128, 128), 0, 0, 32, 128));
        assertFalse(DistantTerrain.inOuterBand(ChunkPos.asLong(129, 0), 0, 0, 32, 128));
        assertFalse(DistantTerrain.inOuterBand(ChunkPos.asLong(33, 0), 0, 0, 32, 0));
        assertFalse(DistantTerrain.inOuterBand(ChunkPos.asLong(0, 0), 0, 0, -3, 0));
    }

    @Test
    public void farSamplePreservesHeight256AndMaterialColor() {
        Bootstrap.register();
        Chunk chunk = new Chunk(null, -128, 0);
        assertEquals(0L, DistantTerrain.sampleFar(chunk));
        ExtendedBlockStorage top = new ExtendedBlockStorage(240, false);
        chunk.getBlockStorageArray()[15] = top;
        top.set(8, 15, 8, Blocks.STONE.getDefaultState());
        long sample = DistantTerrain.sampleFar(chunk);
        assertEquals(256L, sample >>> 24);
        assertEquals(Blocks.STONE.getDefaultState().getMaterial().getMaterialMapColor().colorValue, sample & 0xffffffL);
    }

    @Test
    public void wallsMeetNeighborSurfacesAndUseShortSkirtsAtOpenEdges() {
        assertEquals(62, DistantTerrain.wallBottom(70, 62));
        assertEquals(66, DistantTerrain.wallBottom(70, 0));
        assertEquals(0, DistantTerrain.wallBottom(2, 0));
    }
}
