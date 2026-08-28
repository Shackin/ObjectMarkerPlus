package com.objectmarkerplus;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.inject.Provides;
import lombok.Getter;
import javax.inject.Inject;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.MenuEntry;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.menus.MenuManager;
import net.runelite.client.menus.WidgetMenuOption;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

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
    @Inject private ObjectMarkerOverlay overlay;

    @Getter
    private final Set<GameObject> trackedObjects = new HashSet<>();

    @Getter
    private final Map<Integer, Boolean> matchedIdCache = new HashMap<>();

    @Getter
    private final Set<String> cachedTargetNames = new HashSet<>();

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
        overlayManager.add(overlay);
        updateCachedNames();
    }

    @Override
    protected void shutDown()
    {
        menuManager.removeManagedCustomMenu(EXPORT_OPTION);
        menuManager.removeManagedCustomMenu(IMPORT_OPTION);
        overlayManager.remove(overlay);
        trackedObjects.clear();
        matchedIdCache.clear();
        cachedTargetNames.clear();
    }

    private void updateCachedNames()
    {
        cachedTargetNames.clear();
        String rawNames = config.highlightNames();
        if (rawNames != null && !rawNames.trim().isEmpty())
        {
            for (String name : rawNames.split(","))
            {
                String trimmed = name.trim().toLowerCase();
                if (!trimmed.isEmpty())
                {
                    cachedTargetNames.add(trimmed);
                }
            }
        }
        matchedIdCache.clear();
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
        if (event.getGroup().equals("objectmarkerplus") && event.getKey().equals("highlightNames"))
        {
            updateCachedNames();
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

        // Strip "objectindicators.region_XXXXX=" prefix if it exists
        if (clean.contains("["))
        {
            clean = clean.substring(clean.indexOf("["));
        }

        // Remove backslash escapes (\: and \#) from older marker formats
        clean = clean.replace("\\:", ":");
        clean = clean.replace("\\#", "#");

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

        if (clipboard == null || clipboard.isEmpty())
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
                if (!e.isJsonObject()) continue;

                JsonObject obj = e.getAsJsonObject();
                if (!obj.has("regionId")) continue;

                int region = getSafeInt(obj, "regionId");
                regionMap.computeIfAbsent(region, k -> new JsonArray()).add(obj);
            }

            int totalAdded = 0;
            int totalSkipped = 0; // dupe

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
                    if (!e.isJsonObject()) continue;
                    seen.add(markerKey(e.getAsJsonObject()));
                }

                int added = 0;
                int skipped = 0; // dupe

                for (JsonElement e : newMarkers)
                {
                    if (!e.isJsonObject()) continue;

                    JsonObject obj = e.getAsJsonObject();

                    if (!obj.has("id") || !obj.has("regionX") || !obj.has("regionY") || !obj.has("z"))
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
                    else
                    {
                        skipped++; // dupe
                    }
                }

                totalAdded += added;
                totalSkipped += skipped;

                configManager.setConfiguration(
                        CONFIG_GROUP,
                        regionKey(region),
                        gson.toJson(existingMarkers)
                );
            }

            String message = "Imported " + totalAdded + " Object markers across " + regionMap.size() + " regions";

            if (totalSkipped > 0)
            {
                message += " (Skipped " + totalSkipped + " duplicates)";
            }

            sendChat(message);
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
}