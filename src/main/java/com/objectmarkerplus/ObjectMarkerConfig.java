package com.objectmarkerplus;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("objectmarkerplus")
public interface ObjectMarkerConfig extends Config
{
    @ConfigItem(
            keyName = "highlightNames",
            name = "Highlight Names",
            description = "Object names separated by comma. (bed, chest)",
            position = 1
    )
    default String highlightNames()
    {
        return "";
    }

    @ConfigItem(
            keyName = "showTile",
            name = "Highlight Tile",
            description = "Highlights the tile footprint of the object",
            position = 2
    )
    default boolean showTile()
    {
        return true;
    }

    @ConfigItem(
            keyName = "showHull",
            name = "Highlight Hull",
            description = "Highlights the outline of the model",
            position = 5
    )
    default boolean showHull()
    {
        return false;
    }


    @Alpha
    @ConfigItem(
            keyName = "highlightFillColor",
            name = "Tile Fill Color",
            description = "Fill color of the tile",
            position = 3
    )
    default Color highlightFillColor()
    {
        return new Color(0, 220, 220, 50);
    }

    @Alpha
    @ConfigItem(
            keyName = "highlightOutlineColor",
            name = "Tile Outline Color",
            description = "Outline color of the tile",
            position = 4
    )
    default Color highlightOutlineColor()
    {
        return new Color(0, 225, 225, 255);
    }

    @Alpha
    @ConfigItem(
            keyName = "hullFillColor",
            name = "Hull Fill Color",
            description = "Fill color of the hull",
            position = 6
    )
    default Color hullFillColor()
    {
        return new Color(0, 225, 0, 50);
    }

    @Alpha
    @ConfigItem(
            keyName = "hullOutlineColor",
            name = "Hull Outline Color",
            description = "Outline color of the hull",
            position = 7
    )
    default Color hullOutlineColor()
    {
        return new Color(0, 225, 0, 225);
    }
}