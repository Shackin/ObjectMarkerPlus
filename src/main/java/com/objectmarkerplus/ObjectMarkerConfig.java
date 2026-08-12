package com.objectmarkerplus;

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Alpha;

@ConfigGroup("objectmarkerplus")
public interface ObjectMarkerConfig extends Config
{
    @ConfigItem(
            keyName = "showRadiusTiles",
            name = "Enable Radius",
            description = "Toggle radius tiles",
            position = 1
    )
    default boolean showRadiusTiles()
    {
        return false;
    }

    @Alpha
    @ConfigItem(
            keyName = "radiusFillColor",
            name = "Fill Color",
            description = "Tile color",
            position = 2
    )
    default Color radiusFillColor()
    {
        return new Color(0, 255, 255, 30);
    }

    @Alpha
    @ConfigItem(
            keyName = "radiusBorderColor",
            name = "Border Color",
            description = "The color of the radius outline",
            position = 3
    )
    default Color radiusBorderColor()
    {
        return new Color(0, 255, 255, 255);
    }

    @ConfigItem(
            keyName = "showRadiusBorder",
            name = "Show Border",
            description = "Draw an outline around the highlighted radius",
            position = 4
    )
    default boolean showRadiusBorder()
    {
        return false;
    }

    @ConfigItem(
            keyName = "radiusNames",
            name = "Object Names",
            description = "Format: name:radius,name:radius (e.g. table:3,bank booth:0)",
            position = 5
    )
    default String radiusNames()
    {
        return "";
    }

    @ConfigItem(
            keyName = "showClickbox",
            name = "Show Clickbox",
            description = "Highlights the exact invisible clickable area of the object",
            position = 6
    )
    default boolean showClickbox()
    {
        return false;
    }

    @Alpha
    @ConfigItem(
            keyName = "clickboxColor",
            name = "Clickbox Color",
            description = "Color of the object's clickable area",
            position = 7
    )
    default Color clickboxColor()
    {
        return new Color(255, 0, 0, 50);
    }
}