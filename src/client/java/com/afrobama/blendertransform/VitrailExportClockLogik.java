package com.afrobama.blendertransform;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.integrations.vitrail.VitrailFilmClock;
import mchorse.bbs_mod.utils.VideoRecorder;

/** Mandatory hot-swappable handler for the optional Vitrail FrameState hook. */
public final class VitrailExportClockLogik
{
    private VitrailExportClockLogik() {}

    public static double[] advance(int frame, float total)
    {
        VideoRecorder recorder = BBSModClient.getVideoRecorder();
        VitrailFilmClock.Step step = VitrailFilmClock.advance(recorder != null && recorder.isRecording(),
            BBSRendering.canRender, BBSRendering.getVideoFrameRate(), frame, total);
        return step == null ? null : new double[] {step.frame(), step.elapsed(), step.total()};
    }
}
