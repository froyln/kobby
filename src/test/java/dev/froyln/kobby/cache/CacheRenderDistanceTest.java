package dev.froyln.kobby.cache;

import com.google.gson.GsonBuilder;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class CacheRenderDistanceTest {
    @Test public void extendsTerrainWithoutReducingLiveDistance() {
        assertEquals(16, CacheRenderDistance.effective(2, 16, true));
        assertEquals(24, CacheRenderDistance.effective(24, 16, true));
        assertEquals(2, CacheRenderDistance.effective(2, 16, false));
    }
    @Test public void oldConfigurationKeepsDefaultCachedDistance() {
        dev.froyln.kobby.LiteModKobby config = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create().fromJson(
                "{\"enabled\":true}", dev.froyln.kobby.LiteModKobby.class);
        assertEquals(16, config.cacheRenderDistance);
        assertEquals("", config.cacheProfile);
        org.junit.Assert.assertFalse(config.fallbackEnabled);
    }
    @Test public void onlyLodExtendsProjectionPastNativeDistance() {
        assertEquals(128, CacheRenderDistance.projected(8, Integer.MAX_VALUE, true, true));
        assertEquals(32, CacheRenderDistance.projected(8, 128, true, false));
        assertEquals(8, CacheRenderDistance.projected(8, 128, false, true));
        assertEquals(32, CacheRenderDistance.effective(8, 128, true));
    }

    @Test public void boundsConfiguredDistance() {
        assertEquals(32, CacheRenderDistance.effective(2, Integer.MAX_VALUE, true));
        assertEquals(2, CacheRenderDistance.effective(2, Integer.MIN_VALUE, true));
    }
}
