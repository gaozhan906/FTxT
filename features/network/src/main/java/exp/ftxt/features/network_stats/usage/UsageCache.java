package exp.ftxt.features.network_stats.usage;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public final class UsageCache {

    private static final int MAX_ENTRIES = 8;
    private static final Map<String, UsageSnapshot> CACHE =
            new LinkedHashMap<>(MAX_ENTRIES, 0.75f, true);

    private UsageCache() {
    }

    public static synchronized UsageSnapshot get(String key) {
        return CACHE.get(key);
    }

    public static synchronized void put(String key, UsageSnapshot snapshot) {
        if (key == null || snapshot == null) return;
        CACHE.put(key, snapshot);
        while (CACHE.size() > MAX_ENTRIES) {
            Iterator<String> iterator = CACHE.keySet().iterator();
            if (!iterator.hasNext()) break;
            iterator.next();
            iterator.remove();
        }
    }

    public static synchronized void clear() {
        CACHE.clear();
    }
}
