package com.mojang.blaze3d.platform;

/* BBS-Shim fuer Minecraft vor 1.21.5: diese Klasse gibt es dort nicht; sie bildet die oeffentliche
 * Form der 1.21.11-Fassung nach (eigene Implementierung, siehe docs/MEHRVERSION.md "vor1215"). */
public enum DepthTestFunction
{
    NO_DEPTH_TEST, EQUAL_DEPTH_TEST, LEQUAL_DEPTH_TEST, LESS_DEPTH_TEST, GREATER_DEPTH_TEST
}
