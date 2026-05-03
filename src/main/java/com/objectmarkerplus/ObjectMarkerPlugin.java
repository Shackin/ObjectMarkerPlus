package com.objectmarkerplus;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
    // Using "objectindicators" config group to keep this plugin lite.
    // This is to allow importing/exporting
    private static final String CONFIG_GROUP = "objectindicators";

    private static final WidgetMenuOption EXPORT_OPTION =
            new WidgetMenuOption("Export", "Object Markers",
                    InterfaceID.Orbs.WORLDMAP,
                    InterfaceID.OrbsNomap.WORLDMAP);

    private static final WidgetMenuOption IMPORT_OPTION =
            new WidgetMenuOption("Import", "Object Markers",
                    InterfaceID.Orbs.WORLDMAP,
                    InterfaceID.OrbsNomap.WORLDMAP);

    @Inject
    private Gson gson;

    @Inject private MenuManager menuManager;
    @Inject private ConfigManager configManager;
    @Inject private ChatMessageManager chatMessageManager;
    @Inject private Client client;

    // Show / Hide
    @Override
    protected void startUp()
    {
        menuManager.addManagedCustomMenu(EXPORT_OPTION, this::HExportObjMarkers);
        menuManager.addManagedCustomMenu(IMPORT_OPTION, this::HImportObjMarkers);
    }

    @Override
    protected void shutDown()
    {
        menuManager.removeManagedCustomMenu(EXPORT_OPTION);
        menuManager.removeManagedCustomMenu(IMPORT_OPTION);
    }

    // REGION
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

    // EXPORT
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

    // IMPORT
    private void HImportObjMarkers(MenuEntry ignored)
    {
        String clipboard = ObjectMarkerClip.get();

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
                if (!e.isJsonObject())
                {
                    continue;
                }

                JsonObject obj = e.getAsJsonObject();

                if (!obj.has("regionId"))
                {
                    continue;
                }

                int region = obj.get("regionId").getAsInt();
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


    // DUPE

    private String markerKey(JsonObject obj)
    {
        return obj.get("id").getAsInt() + "_" +
                obj.get("regionX").getAsInt() + "_" +
                obj.get("regionY").getAsInt() + "_" +
                obj.get("z").getAsInt();
    }


    // CHAT OUTPUT
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