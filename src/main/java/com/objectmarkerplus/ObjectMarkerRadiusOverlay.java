package com.objectmarkerplus;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.geom.Area;
import javax.inject.Inject;
import java.awt.Shape;

import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.GameObject;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

public class ObjectMarkerRadiusOverlay extends Overlay
{
    private final Client client;
    private final ObjectMarkerPlugin plugin;
    private final ObjectMarkerConfig config;

    private static final int MAX_DRAW_DISTANCE = 35;

    @Inject
    public ObjectMarkerRadiusOverlay(Client client, ObjectMarkerPlugin plugin, ObjectMarkerConfig config)
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
        if ((!config.showRadiusTiles() && !config.showClickbox()) || client.getLocalPlayer() == null)
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

        Color fillColor = config.radiusFillColor();
        boolean drawBorder = config.showRadiusBorder();
        Color borderColor = config.radiusBorderColor();

        Area combinedArea = new Area();
        boolean hasPolygons = false;

        WorldView wv = client.getTopLevelWorldView();

        for (GameObject gameObject : plugin.getTrackedObjects())
        {
            if (gameObject == null || gameObject.getPlane() != currentPlane)
            {
                continue;
            }

            int id = gameObject.getId();

            // Check if already calculated
            if (!plugin.getResolvedRadiusCache().containsKey(id))
            {
                Integer radius = null;
                ObjectComposition comp = client.getObjectDefinition(id);
                if (comp != null && comp.getName() != null)
                {
                    radius = plugin.getNameRadiusMap().get(comp.getName().toLowerCase());
                }

                // Save to the cache (w/ null to stop recalculating of unconfigured objects)
                plugin.getResolvedRadiusCache().put(id, radius);
            }

            Integer radiusObj = plugin.getResolvedRadiusCache().get(id);

            if (radiusObj == null)
            {
                continue;
            }

            // Try-Catch
            if (config.showClickbox())
            {
                try
                {
                    Shape clickbox = gameObject.getClickbox();
                    if (clickbox != null)
                    {
                        graphics.setColor(config.clickboxColor());
                        graphics.fill(clickbox);
                    }
                }
                catch (Exception e)
                {
                    // ignore objects with broken/missing clickboxes
                }
            }

            if (!config.showRadiusTiles())
            {
                continue;
            }

            int radius = radiusObj;

            Point sceneMin = gameObject.getSceneMinLocation();
            if (sceneMin == null) continue;

            int dx = Math.abs(sceneMin.getX() - playerSceneX);
            int dy = Math.abs(sceneMin.getY() - playerSceneY);

            if (dx > MAX_DRAW_DISTANCE + radius || dy > MAX_DRAW_DISTANCE + radius)
            {
                continue;
            }

            int startX = Math.max(0, sceneMin.getX() - radius);
            int endX = Math.min(Constants.SCENE_SIZE - 1, gameObject.getSceneMaxLocation().getX() + radius);
            int startY = Math.max(0, sceneMin.getY() - radius);
            int endY = Math.min(Constants.SCENE_SIZE - 1, gameObject.getSceneMaxLocation().getY() + radius);

            if (startX > endX || startY > endY)
            {
                continue;
            }

            // Pass drawBorder
            if (addAreaToArea(combinedArea, client, currentPlane, startX, endX, startY, endY, wv, drawBorder))
            {
                hasPolygons = true;
            }
        }

        if (hasPolygons)
        {
            graphics.setColor(fillColor);
            graphics.fill(combinedArea);

            if (drawBorder)
            {
                graphics.setColor(borderColor);
                graphics.setStroke(new BasicStroke(2));
                graphics.draw(combinedArea);
                graphics.setStroke(new BasicStroke(1));
            }
        }

        return null;
    }

    private boolean addAreaToArea(Area mainArea, Client client, int plane, int startX, int endX, int startY, int endY, WorldView wv, boolean drawBorder)
    {
        int sizeX = endX - startX + 1;
        int sizeY = endY - startY + 1;

        if (sizeX <= 0 || sizeY <= 0) return false;

        int centerX = (startX + endX + 1) * 64;
        int centerY = (startY + endY + 1) * 64;

        LocalPoint centerPoint = new LocalPoint(centerX, centerY, wv);

        Polygon areaPoly = Perspective.getCanvasTileAreaPoly(client, centerPoint, sizeX, sizeY, plane, 0);

        if (areaPoly != null)
        {
            if (drawBorder) {
                mainArea.add(new Area(inflatePolygon(areaPoly, 2)));
            } else {
                mainArea.add(new Area(areaPoly));
            }
            return true;
        }

        if (sizeX == 1 && sizeY == 1)
        {
            return false;
        }

        int midX = startX + (sizeX / 2) - 1;
        int midY = startY + (sizeY / 2) - 1;

        boolean drawn = false;
        drawn |= tryAddSubBox(mainArea, client, plane, startX, midX, startY, midY, wv, drawBorder);
        drawn |= tryAddSubBox(mainArea, client, plane, midX + 1, endX, startY, midY, wv, drawBorder);
        drawn |= tryAddSubBox(mainArea, client, plane, startX, midX, midY + 1, endY, wv, drawBorder);
        drawn |= tryAddSubBox(mainArea, client, plane, midX + 1, endX, midY + 1, endY, wv, drawBorder);

        return drawn;
    }

    private boolean tryAddSubBox(Area mainArea, Client client, int plane, int startX, int endX, int startY, int endY, WorldView wv, boolean drawBorder)
    {
        int sizeX = endX - startX + 1;
        int sizeY = endY - startY + 1;

        if (sizeX <= 0 || sizeY <= 0) return false;

        int centerX = (startX + endX + 1) * 64;
        int centerY = (startY + endY + 1) * 64;

        LocalPoint centerPoint = new LocalPoint(centerX, centerY, wv);
        Polygon subPoly = Perspective.getCanvasTileAreaPoly(client, centerPoint, sizeX, sizeY, plane, 0);

        if (subPoly != null)
        {
            // If border toggle on
            if (drawBorder) {
                mainArea.add(new Area(inflatePolygon(subPoly, 2)));
            } else {
                mainArea.add(new Area(subPoly));
            }
            return true;
        }
        return false;
    }

    private Polygon inflatePolygon(Polygon poly, int pixels)
    {
        int cx = (poly.xpoints[0] + poly.xpoints[1] + poly.xpoints[2] + poly.xpoints[3]) / 4;
        int cy = (poly.ypoints[0] + poly.ypoints[1] + poly.ypoints[2] + poly.ypoints[3]) / 4;

        Polygon expanded = new Polygon();

        for (int i = 0; i < 4; i++)
        {
            int x = poly.xpoints[i];
            int y = poly.ypoints[i];

            if (x < cx) x -= pixels; else x += pixels;
            if (y < cy) y -= pixels; else y += pixels;

            expanded.addPoint(x, y);
        }

        return expanded;
    }
}