package com.objectmarkerplus;

import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;

//Clipboard
public class ObjectMarkerClip
{
    public static void set(String text)
    {
        Toolkit.getDefaultToolkit()
                .getSystemClipboard()
                .setContents(new StringSelection(text), null);
    }

    public static String get()
    {
        try
        {
            return (String) Toolkit.getDefaultToolkit()
                    .getSystemClipboard()
                    .getData(DataFlavor.stringFlavor);
        }
        catch (Exception e)
        {
            return null;
        }
    }
}