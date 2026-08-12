package com.objectmarkerplus;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.inject.Provides;

import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.MenuEntry;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.menus.MenuManager;
import net.runelite.client.menus.WidgetMenuOption;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.GameObject;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.GameState;

import lombok.Getter;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@PluginDescriptor(
        name = "Object Markers Plus",
        description = "Import & export object markers"
)
public class ObjectMarkerPlugin extends Plugin
{
    private static final String CONFIG_GROUP = "objectindicators";

    private static final WidgetMenuOption EXPORT_OPTION =
            new WidgetMenuOption("Export", "Object Markers",
                    InterfaceID.Orbs.WORLDMAP,
                    InterfaceID.OrbsNomap.WORLDMAP);

    private static final WidgetMenuOption IMPORT_OPTION =
            new WidgetMenuOption("Import", "Object Markers",
                    InterfaceID.Orbs.WORLDMAP,
                    InterfaceID.OrbsNomap.WORLDMAP);

    @Inject private Gson gson;
    @Inject private MenuManager menuManager;
    @Inject private ConfigManager configManager;
    @Inject private ChatMessageManager chatMessageManager;
    @Inject private Client client;

    @Inject private OverlayManager overlayManager;
    @Inject private ObjectMarkerConfig config;
    @Inject private ObjectMarkerRadiusOverlay radiusOverlay;

    // REMOVED: idRadiusMap (User input for IDs is no longer allowed)
    @Getter
    private final Map<String, Integer> nameRadiusMap = new HashMap<>();

    @Getter
    private final Map<Integer, Integer> resolvedRadiusCache = new HashMap<>();

    @Getter
    private final Set<GameObject> trackedObjects = new HashSet<>();

    @Provides
    ObjectMarkerConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(ObjectMarkerConfig.class);
    }

    @Override
    protected void startUp()
    {
        menuManager.addManagedCustomMenu(EXPORT_OPTION, this::HExportObjMarkers);
        menuManager.addManagedCustomMenu(IMPORT_OPTION, this::HImportObjMarkers);

        // Parse config and draw
        updateRadiusConfig();
        overlayManager.add(radiusOverlay);
    }

    @Override
    protected void shutDown()
    {
        menuManager.removeManagedCustomMenu(EXPORT_OPTION);
        menuManager.removeManagedCustomMenu(IMPORT_OPTION);

        // Stop drawing/clear memory
        overlayManager.remove(radiusOverlay);
        nameRadiusMap.clear();
        resolvedRadiusCache.clear();
        trackedObjects.clear();
    }

    private int getRegion()
    {
        if (client.getLocalPlayer() == null)
        {
            return -1;
        }
        return client.getLocalPlayer().getWorldLocation().getRegionID();
    }

    private String regionKey(int region)
    {
        return "region_" + region;
    }

    private String sanitizeInput(String input)
    {
        if (input == null) return "";
        String clean = input.trim();

        if (clean.contains("["))
        {
            clean = clean.substring(clean.indexOf("["));
        }

        clean = clean.replace(":", ":");
        clean = clean.replace("#", "#");

        return clean;
    }

    private int getSafeInt(JsonObject obj, String key)
    {
        return obj.get(key).getAsJsonPrimitive().getAsNumber().intValue();
    }

    private void HExportObjMarkers(MenuEntry ignored)
    {
        int region = getRegion();

        if (region == -1)
        {
            sendChat("Region detection error.");
            return;
        }

        String data = configManager.getConfiguration(CONFIG_GROUP, regionKey(region));

        if (data == null || data.isEmpty())
        {
            sendChat("No object markers in this region.");
            return;
        }

        try
        {
            JsonArray markers = gson.fromJson(data, JsonArray.class);
            int count = markers.size();

            ObjectMarkerClip.set(data);

            sendChat("Exported " + count + " object markers from region " + region);
        }
        catch (Exception e)
        {
            sendChat("Obj export failed.");
        }
    }

    private void HImportObjMarkers(MenuEntry ignored)
    {
        String clipboard = sanitizeInput(ObjectMarkerClip.get());

        if (clipboard.isEmpty())
        {
            sendChat("Clipboard is empty.");
            return;
        }

        try
        {
            JsonArray allMarkers = gson.fromJson(clipboard, JsonArray.class);

            if (allMarkers == null || allMarkers.size() == 0)
            {
                sendChat("No object markers found.");
                return;
            }

            Map<Integer, JsonArray> regionMap = new HashMap<>();

            for (JsonElement e : allMarkers)
            {
                if (!e.isJsonObject())
                {
                    continue;
                }

                JsonObject obj = e.getAsJsonObject();

                if (!obj.has("regionId"))
                {
                    continue;
                }

                int region = getSafeInt(obj, "regionId");
                regionMap.computeIfAbsent(region, k -> new JsonArray()).add(obj);
            }

            int totalAdded = 0;

            for (Map.Entry<Integer, JsonArray> entry : regionMap.entrySet())
            {
                int region = entry.getKey();
                JsonArray newMarkers = entry.getValue();

                String existingData = configManager.getConfiguration(CONFIG_GROUP, regionKey(region));

                JsonArray existingMarkers = (existingData != null && !existingData.isEmpty())
                        ? gson.fromJson(existingData, JsonArray.class)
                        : new JsonArray();

                Set<String> seen = new HashSet<>();

                for (JsonElement e : existingMarkers)
                {
                    if (!e.isJsonObject())
                    {
                        continue;
                    }

                    JsonObject obj = e.getAsJsonObject();
                    seen.add(markerKey(obj));
                }

                int added = 0;

                for (JsonElement e : newMarkers)
                {
                    if (!e.isJsonObject())
                    {
                        continue;
                    }

                    JsonObject obj = e.getAsJsonObject();

                    if (!obj.has("id") ||
                            !obj.has("regionX") ||
                            !obj.has("regionY") ||
                            !obj.has("z"))
                    {
                        continue;
                    }

                    String key = markerKey(obj);

                    if (!seen.contains(key))
                    {
                        existingMarkers.add(obj);
                        seen.add(key);
                        added++;
                    }
                }

                totalAdded += added;

                configManager.setConfiguration(
                        CONFIG_GROUP,
                        regionKey(region),
                        gson.toJson(existingMarkers)
                );
            }

            sendChat("Imported " + totalAdded + " Object markers across " + regionMap.size() + " regions");
        }
        catch (Exception e)
        {
            sendChat("Object Import failed.");
        }
    }

    private String markerKey(JsonObject obj)
    {
        return getSafeInt(obj, "id") + "_" +
                getSafeInt(obj, "regionX") + "_" +
                getSafeInt(obj, "regionY") + "_" +
                getSafeInt(obj, "z");
    }

    private void sendChat(String msg)
    {
        chatMessageManager.queue(
                QueuedMessage.builder()
                        .type(ChatMessageType.CONSOLE)
                        .runeLiteFormattedMessage(msg)
                        .build()
        );
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() == GameState.LOADING)
        {
            trackedObjects.clear();
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        // Only listen for changes to the Names configuration now
        if (event.getGroup().equals("objectmarkerplus") && event.getKey().equals("radiusNames"))
        {
            updateRadiusConfig();
        }
    }

    @Subscribe
    public void onGameObjectSpawned(GameObjectSpawned event)
    {
        GameObject gameObject = event.getGameObject();
        if (gameObject != null)
        {
            trackedObjects.add(gameObject);
        }
    }

    @Subscribe
    public void onGameObjectDespawned(GameObjectDespawned event)
    {
        GameObject gameObject = event.getGameObject();
        if (gameObject != null)
        {
            trackedObjects.remove(gameObject);
        }
    }

    private void updateRadiusConfig()
    {
        nameRadiusMap.clear();
        resolvedRadiusCache.clear(); // Clear cache when settings are changed

        String rawNames = config.radiusNames();
        if (rawNames != null && !rawNames.trim().isEmpty())
        {
            String[] pairs = rawNames.split(",");
            for (String pair : pairs)
            {
                String[] parts = pair.split(":");
                if (parts.length == 2)
                {
                    try
                    {
                        // case-insensitive matching
                        String name = parts[0].trim().toLowerCase();
                        int radius = Integer.parseInt(parts[1].trim());
                        nameRadiusMap.put(name, radius);
                    }
                    catch (NumberFormatException e) { }
                }
            }
        }
    }
}