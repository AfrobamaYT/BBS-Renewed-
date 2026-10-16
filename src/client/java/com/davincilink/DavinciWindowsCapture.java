package com.davincilink;

import mchorse.bbs_mod.utils.FFMpegUtils;

import java.io.IOException;

/** WGC delivers changed frames, including when another window covers Resolve. */
final class DavinciWindowsCapture extends DavinciProcessFrames
{
    DavinciWindowsCapture(String hwnd, int width, int height) throws IOException
    {
        super(start(hwnd, width, height), "Resolve WGC capture (ffmpeg must support gfxcapture)");
    }

    private static Process start(String hwnd, int width, int height) throws IOException
    {
        if (!hwnd.matches("[0-9]+")) throw new IOException("Invalid Resolve HWND: " + hwnd);
        return new ProcessBuilder(System.getProperty("davincilink.ffmpeg", FFMpegUtils.getFFMPEG()), "-hide_banner", "-v", "error",
            "-filter_complex", "gfxcapture=hwnd=" + hwnd + ":capture_cursor=0:max_framerate=60:width="
                + width + ":height=" + height + ",hwdownload,format=bgra",
            "-fps_mode", "passthrough", "-f", "rawvideo", "-pix_fmt", "bgra", "-threads", "1", "pipe:1").start();
    }
}
