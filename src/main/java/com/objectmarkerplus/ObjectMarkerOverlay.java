package com.objectmarkerplus;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Shape;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

@Singleton
public class ObjectMarkerOverlay extends Overlay
{
    private final Client client;
    private final ObjectMarkerPlugin plugin;
    private final ObjectMarkerConfig config;

    private static final int MAX_DRAW_DISTANCE = 40;

    @Inject
    public ObjectMarkerOverlay(Client client, ObjectMarkerPlugin plugin, ObjectMarkerConfig config)
    {
        this.client = client;
        this.plugin = plugin;
        this.config = config;

        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        Set<String> targetNames = plugin.getCachedTargetNames();
        if (targetNames.isEmpty() || client.getLocalPlayer() == null)
        {
            return null;
        }

        boolean drawTile = config.showTile() && (config.highlightFillColor().getAlpha() > 0 || config.highlightOutlineColor().getAlpha() > 0);
        boolean drawHull = config.showHull();

        if (!drawTile && !drawHull)
        {
            return null;
        }

        int currentPlane = client.getPlane();

        LocalPoint playerLocal = client.getLocalPlayer().getLocalLocation();
        if (playerLocal == null)
        {
            return null;
        }
        int playerSceneX = playerLocal.getSceneX();
        int playerSceneY = playerLocal.getSceneY();

        for (GameObject gameObject : plugin.getTrackedObjects())
        {
            if (gameObject == null || gameObject.getPlane() != currentPlane)
            {
                continue;
            }

            Point sceneMin = gameObject.getSceneMinLocation();
            if (sceneMin == null) continue;

            int dx = Math.abs(sceneMin.getX() - playerSceneX);
            int dy = Math.abs(sceneMin.getY() - playerSceneY);

            if (dx > MAX_DRAW_DISTANCE || dy > MAX_DRAW_DISTANCE)
            {
                continue;
            }

            int id = gameObject.getId();
            Boolean isMatch = plugin.getMatchedIdCache().get(id);

            if (isMatch == null)
            {
                ObjectComposition comp = client.getObjectDefinition(id);
                if (comp != null && comp.getName() != null && targetNames.contains(comp.getName().toLowerCase()))
                {
                    isMatch = true;
                }
                else
                {
                    isMatch = false;
                }
                plugin.getMatchedIdCache().put(id, isMatch);
            }

            if (!isMatch)
            {
                continue;
            }

            // IN RANGE
            if (drawTile)
            {
                int sizeX = gameObject.sizeX();
                int sizeY = gameObject.sizeY();
                LocalPoint localPoint = gameObject.getLocalLocation();

                if (localPoint != null)
                {
                    Polygon poly = Perspective.getCanvasTileAreaPoly(client, localPoint, sizeX, sizeY, currentPlane, 0);
                    if (poly != null)
                    {
                        graphics.setColor(config.highlightFillColor());
                        graphics.fill(poly);
                        graphics.setColor(config.highlightOutlineColor());
                        graphics.draw(poly);
                    }
                }
            }

            if (drawHull)
            {
                Shape hull = gameObject.getConvexHull();
                if (hull != null)
                {
                    graphics.setColor(config.hullFillColor());
                    graphics.fill(hull);
                    graphics.setColor(config.hullOutlineColor());
                    graphics.draw(hull);
                }
            }
        }
        return null;
    }
}