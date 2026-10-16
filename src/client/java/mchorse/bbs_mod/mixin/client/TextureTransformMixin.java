package mchorse.bbs_mod.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.utils.VideoRecorder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.TextureTransform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Keeps the enchantment glint in step with the world while a video is being recorded.
 *
 * <p>Vanilla scrolls the glint texture by {@code Util.getMillis()}, wall-clock time, while
 * {@link RenderTickCounterMixin} advances the game by a fixed {@code 20 / fps} ticks per recorded
 * frame however long the frame took. On a slow export the world plays at normal speed in the video
 * while the glint races ahead. Here the glint reads the recording's clock instead - the ticks the
 * recorder has advanced plus the partial tick - anchored to the wall clock when recording started,
 * so it neither jumps on the first frame nor drifts afterwards.</p>
 *
 * <p>From BBS FS f67eca496 (Wemppy4/bbs-fs, MIT); in 26.3 the glint matrix lives in TextureTransform.</p>
 */
@Mixin(TextureTransform.class)
public abstract class TextureTransformMixin
{
    @Unique
    private static boolean recordingGlint;

    @Unique
    private static long recordingGlintAnchor;

    @ModifyExpressionValue(method = "setupGlintTexturing", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Util;getMillis()J"))
    private static long bbs$recordingGlintClock(long timeMs)
    {
        VideoRecorder videoRecorder = BBSModClient.getVideoRecorder();

        if (videoRecorder == null || !videoRecorder.isRecording())
        {
            recordingGlint = false;

            return timeMs;
        }

        if (!recordingGlint)
        {
            recordingGlint = true;
            recordingGlintAnchor = timeMs;
        }

        double ticks = videoRecorder.serverTicks + Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);

        return recordingGlintAnchor + (long) (ticks * 50D);
    }
}
