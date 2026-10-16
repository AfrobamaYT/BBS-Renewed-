import com.davincilink.DavinciViewerFinder;

/**
 * DavinciViewerFinder against painted frames (no Minecraft needed):
 *
 *   java -cp build/classes/java/client davinci/tests/ViewerFinderCheck.java
 */
public class ViewerFinderCheck
{
    static int failures;

    static byte[] frame(int w, int h)
    {
        byte[] rgb = new byte[w * h * 4];
        for (int i = 0; i < rgb.length; i += 4) { rgb[i] = 40; rgb[i + 1] = 40; rgb[i + 2] = 46; rgb[i + 3] = (byte) 255; }  // Resolve grey
        return rgb;
    }

    static void paint(byte[] rgb, int w, int x, int y, int rw, int rh, int r, int g, int b)
    {
        for (int yy = y; yy < y + rh; yy++)
            for (int xx = x; xx < x + rw; xx++)
            {
                int o = (yy * w + xx) * 4;
                rgb[o] = (byte) r; rgb[o + 1] = (byte) g; rgb[o + 2] = (byte) b;
            }
    }

    static void check(String what, boolean ok)
    {
        System.out.println((ok ? "  OK   " : "  FAIL ") + what);
        if (!ok) failures++;
    }

    public static void main(String[] args)
    {
        int w = 950, h = 936;

        byte[] both = frame(w, h);
        paint(both, w, 267 / 2, 282 / 2, 192 / 2 * 2, 107, 43, 53, 66);        // a project thumbnail
        paint(both, w, 500, 120, 360, 200, 43, 53, 66);                         // the viewer
        int[] box = DavinciViewerFinder.find(both, w, h);
        check("the viewer wins over a thumbnail", box != null && box[0] == 500 && box[1] == 120
            && Math.abs(box[2] - 360) <= 4 && Math.abs(box[3] - 200) <= 4);

        byte[] thumb = frame(w, h);
        paint(thumb, w, 133, 141, 192, 107, 43, 53, 66);
        check("a thumbnail alone is not a viewer (it swallowed clicks)", DavinciViewerFinder.find(thumb, w, h) == null);

        byte[] shifted = frame(w, h);
        paint(shifted, w, 200, 200, 400, 225, 43 + 6, 53 - 5, 66 + 7);
        check("H.264 shifts within the tolerance are still the viewer", DavinciViewerFinder.find(shifted, w, h) != null);

        byte[] small = frame(w, h);
        paint(small, w, 728, 196, 220, 144, 43, 53, 66);                         // measured: 3.6 %
        check("the cut-off viewer of the smallest tab (3.6 %) is still found", DavinciViewerFinder.find(small, w, h) != null);

        check("an empty frame has no viewer", DavinciViewerFinder.find(frame(w, h), w, h) == null);
        System.exit(failures == 0 ? 0 : 1);
    }
}
