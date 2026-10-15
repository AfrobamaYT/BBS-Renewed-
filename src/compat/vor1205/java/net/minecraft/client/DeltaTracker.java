package net.minecraft.client;

/** vor1205: 1.21s DeltaTracker auf Minecraft#getFrameTime/getDeltaFrameTime von 1.20.4. */
public interface DeltaTracker
{
    DeltaTracker ZERO = new DeltaTracker()
    {
        @Override public float getGameTimeDeltaTicks() { return 0F; }
        @Override public float getGameTimeDeltaPartialTick(boolean laeuft) { return 0F; }
        @Override public float getRealtimeDeltaTicks() { return 0F; }
    };

    float getGameTimeDeltaTicks();

    float getGameTimeDeltaPartialTick(boolean laeuft);

    float getRealtimeDeltaTicks();

    /** Der Zeitgeber des Spiels: liest bei jedem Aufruf frisch. */
    final class Timer implements DeltaTracker
    {
        public static final Timer SPIEL = new Timer();

        @Override
        public float getGameTimeDeltaTicks()
        {
            return Minecraft.getInstance().getDeltaFrameTime();
        }

        @Override
        public float getGameTimeDeltaPartialTick(boolean laeuft)
        {
            return Minecraft.getInstance().getFrameTime();
        }

        @Override
        public float getRealtimeDeltaTicks()
        {
            return Minecraft.getInstance().getDeltaFrameTime();
        }
    }
}
