package dev.froyln.kobby;

import com.mumfrey.liteloader.Tickable;
import com.mumfrey.liteloader.Configurable;
import com.mumfrey.liteloader.modconfig.ConfigPanel;
import dev.froyln.kobby.config.KobbyConfigPanel;
import com.mumfrey.liteloader.core.LiteLoader;
import com.mumfrey.liteloader.modconfig.ExposableOptions;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;

import java.io.File;
import java.io.FileReader;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.input.Keyboard;

import dev.froyln.kobby.cache.ChunkCache;
import dev.froyln.kobby.cache.CacheProfile;
import dev.froyln.kobby.cache.CacheRenderDistance;
import dev.froyln.kobby.cache.IntegratedServerDistance;
import dev.froyln.kobby.cache.CachedTerrainTint;
import dev.froyln.kobby.cache.RenderChunkLookup;
import net.minecraft.entity.Entity;

@ExposableOptions(filename = "bobby112.json")
public class LiteModKobby implements Tickable, Configurable {
    public static final String VERSION = "@MOD_VERSION@";
    private static final Gson CONFIG_READER = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();

    @Expose
    public boolean enabled = true;

    @Expose
    public int cacheRenderDistance = 16;

    @Expose
    public boolean fallbackEnabled = false;

    @Expose
    public String cacheProfile = "";

    @Expose
    public int integratedServerDistance = 0;

    @Expose
    public boolean tintCachedTerrain = false;

    private File configFile;
    private long configModified;
    private KeyBinding toggleKey;

    @Override
    public String getName() {
        return "Kobby";
    }

    @Override
    public String getVersion() {
        return VERSION;
    }

    @Override
    public Class<? extends ConfigPanel> getConfigPanelClass() {
        return KobbyConfigPanel.class;
    }

    @Override
    public void init(File configPath) {
        ChunkCache.initialize(LiteLoader.getGameDirectory());
        configFile = new File(LiteLoader.getCommonConfigFolder(), "bobby112.json");
        reloadConfig();
        try {
            setCacheProfile(cacheProfile);
        } catch (IllegalArgumentException invalid) {
            setCacheProfile("");
            System.err.println("[Kobby] Invalid cache profile; using the default profile");
        }
        toggleKey = new KeyBinding("Toggle Kobby terrain cache", Keyboard.KEY_NONE, "Kobby");
        LiteLoader.getInput().registerKeyBinding(toggleKey);
        System.out.println("[Kobby] " + VERSION + " loaded");
    }

    @Override
    public void onTick(Minecraft client, float partialTicks, boolean inGame, boolean clock) {
        if (clock) {
            reloadConfig();
            while (toggleKey.isPressed()) {
                if (inGame && client.currentScreen == null) {
                    enabled = !enabled;
                    LiteLoader.getInstance().writeConfig(this);
                    client.ingameGUI.setOverlayMessage("Kobby terrain cache: " + (enabled ? "enabled" : "disabled"), false);
                }
            }
            setCacheProfile(cacheProfile);
            CacheRenderDistance.configure(cacheRenderDistance);
            IntegratedServerDistance.configure(integratedServerDistance);
            boolean tintChanged = CachedTerrainTint.configure(tintCachedTerrain);
            boolean fallbackChanged = ChunkCache.setFallbackEnabled(fallbackEnabled);
            boolean wasEnabled = ChunkCache.isEnabled();
            ChunkCache.setEnabled(enabled);
            if ((wasEnabled != enabled || fallbackChanged || tintChanged) && client.world != null) {
                client.renderGlobal.loadRenderers();
            }
        }
        WorldClient world = client.world;
        Entity camera = RenderChunkLookup.camera(client);
        if (enabled && world != null && camera != null) {
            ChunkCache.scanAround(world, MathHelper.floor(camera.posX) >> 4,
                    MathHelper.floor(camera.posZ) >> 4, CacheRenderDistance.get(client.gameSettings.renderDistanceChunks) + 2);
        }
    }

    public void setCacheProfile(String value) {
        cacheProfile = ChunkCache.configureProfile(value);
    }

    private void reloadConfig() {
        if (configFile == null || !configFile.isFile() || configFile.lastModified() <= configModified) {
            return;
        }
        configModified = configFile.lastModified();
        try (FileReader reader = new FileReader(configFile)) {
            LiteModKobby config = CONFIG_READER.fromJson(reader, LiteModKobby.class);
            if (config != null) {
                String profile = CacheProfile.normalize(config.cacheProfile);
                enabled = config.enabled;
                cacheRenderDistance = config.cacheRenderDistance;
                fallbackEnabled = config.fallbackEnabled;
                integratedServerDistance = config.integratedServerDistance;
                tintCachedTerrain = config.tintCachedTerrain;
                setCacheProfile(profile);
            }
        } catch (Exception e) {
            System.err.println("[Kobby] Could not reload config: " + e.getMessage());
        }
    }

    @Override
    public void upgradeSettings(String version, File configPath, File oldConfigPath) {
    }
}
