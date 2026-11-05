package exp.ftxt.features.gamefps.reader;

import android.util.Log;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GfxInfoParser {
    private static final String TAG = "GameFpsReader";

    public static class FpsResult {
        public boolean parsed;
        public float fps = -1f;
        public String source;
        public String rawSnippet;
    }

    public static FpsResult parse(String output) {
        FpsResult res = new FpsResult();
        if (output == null || output.trim().isEmpty()) {
            res.parsed = false;
            res.source = "empty";
            return res;
        }

        // Try framestats first
        FpsResult r1 = parseFromFrameStats(output);
        if (r1.parsed && r1.fps > 0) {
            res = r1;
            return res;
        }

        // Fallback: try other patterns
        FpsResult r2 = parseFromTotals(output);
        if (r2.parsed && r2.fps > 0) {
            res = r2;
            return res;
        }

        res.parsed = false;
        res.source = "unknown";
        // keep snippet for debug
        String snippet = output.length() > 500 ? output.substring(0, 500) : output;
        res.rawSnippet = snippet;
        Log.d(TAG, "Failed to parse gfxinfo:\n" + snippet);
        return res;
    }

    private static FpsResult parseFromFrameStats(String output) {
        FpsResult res = new FpsResult();
        res.source = "framestats";

        // Look for numbers separated by spaces in framestats lines
        // This is heuristic and varies by OEM; try to find intervals
        Pattern p = Pattern.compile("([0-9]+)\\s+([0-9]+)\\s+([0-9]+)");
        Matcher m = p.matcher(output);
        long lastPresent = -1;
        long lastStart = -1;
        int count = 0;
        while (m.find()) {
            try {
                long start = Long.parseLong(m.group(1));
                long present = Long.parseLong(m.group(3));
                if (lastPresent > 0 && present > lastPresent) {
                    // rough interval between presents? not perfect, but try
                    count++;
                }
                lastStart = start;
                lastPresent = present;
                if (count > 120) break; // enough sample
            } catch (Exception ignored) {}
        }
        // If we saw sequence, hard to get precise without full parsing; skip for now
        // Try another common pattern
        Pattern fpsPat = Pattern.compile("([0-9]+\\.?[0-9]*)\\s*fps", Pattern.CASE_INSENSITIVE);
        Matcher mf = fpsPat.matcher(output);
        if (mf.find()) {
            try {
                float val = Float.parseFloat(mf.group(1));
                if (val > 0 && val < 1000) {
                    res.parsed = true;
                    res.fps = val;
                    res.rawSnippet = mf.group(0);
                    return res;
                }
            } catch (Exception ignored) {}
        }
        res.parsed = false;
        return res;
    }

    private static FpsResult parseFromTotals(String output) {
        FpsResult res = new FpsResult();
        res.source = "totals";

        // Try to find "Total frames rendered" and time window
        Pattern total = Pattern.compile("Total frames rendered[:\\s]+([0-9]+)", Pattern.CASE_INSENSITIVE);
        Matcher mt = total.matcher(output);
        if (!mt.find()) {
            res.parsed = false;
            return res;
        }
        long frames = 0;
        try {
            frames = Long.parseLong(mt.group(1));
        } catch (Exception e) {
            res.parsed = false;
            return res;
        }

        // Try to find time window in ms/seconds - very OEM dependent
        // Look for patterns like "XX.XX seconds" or large numbers
        Pattern sec = Pattern.compile("([0-9]+\\.?[0-9]*)\\s*seconds", Pattern.CASE_INSENSITIVE);
        Matcher ms2 = sec.matcher(output);
        if (ms2.find()) {
            try {
                double secs = Double.parseDouble(ms2.group(1));
                if (secs > 0.1 && frames > 0) {
                    float fps = (float) (frames / secs);
                    if (fps > 0 && fps < 1000) {
                        res.parsed = true;
                        res.fps = fps;
                        res.rawSnippet = mt.group(0) + " / " + ms2.group(0);
                        return res;
                    }
                }
            } catch (Exception ignored) {}
        }

        // Try ms
        Pattern ms = Pattern.compile("([0-9]+)\\s*ms", Pattern.CASE_INSENSITIVE);
        Matcher mm = ms.matcher(output);
        if (mm.find()) {
            try {
                long msVal = Long.parseLong(mm.group(1));
                if (msVal > 100 && frames > 0) {
                    float fps = (float) (frames * 1000.0 / msVal);
                    if (fps > 0 && fps < 1000) {
                        res.parsed = true;
                        res.fps = fps;
                        res.rawSnippet = mt.group(0) + " / " + mm.group(0);
                        return res;
                    }
                }
            } catch (Exception ignored) {}
        }

        res.parsed = false;
        return res;
    }
}
