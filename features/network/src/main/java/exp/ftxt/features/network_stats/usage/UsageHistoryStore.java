package exp.ftxt.features.network_stats.usage;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class UsageHistoryStore extends SQLiteOpenHelper {

    private static final String DB_NAME = "network_usage_history.db";
    private static final int DB_VERSION = 1;

    private static final String T_DAILY = "usage_daily";
    private static final String T_META = "usage_meta";

    private static final String KEY_LAST_SYNC_AT = "last_sync_at";
    private static final String KEY_LAST_SYNC_DAY = "last_sync_day";
    private static final long FULL_SYNC_INTERVAL_MS = 5L * 60L * 1000L;

    private static volatile UsageHistoryStore instance;

    public static UsageHistoryStore get(Context context) {
        if (instance == null) {
            synchronized (UsageHistoryStore.class) {
                if (instance == null) {
                    instance = new UsageHistoryStore(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private UsageHistoryStore(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        try {
            db.enableWriteAheadLogging();
        } catch (Exception ignored) {
        }
        try {
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_usage_daily_day ON " + T_DAILY + "(day)");
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + T_DAILY + " (" +
                "day TEXT NOT NULL," +
                "network TEXT NOT NULL," +
                "uid INTEGER NOT NULL," +
                "package TEXT," +
                "rx INTEGER NOT NULL," +
                "tx INTEGER NOT NULL," +
                "updated_at INTEGER NOT NULL," +
                "PRIMARY KEY(day, network, uid))");
        db.execSQL("CREATE INDEX idx_usage_daily_day ON " + T_DAILY + "(day)");
        db.execSQL("CREATE TABLE " + T_META + " (" +
                "key TEXT PRIMARY KEY," +
                "value TEXT)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion == newVersion) return;
        db.execSQL("DROP TABLE IF EXISTS " + T_DAILY);
        db.execSQL("DROP TABLE IF EXISTS " + T_META);
        onCreate(db);
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onUpgrade(db, oldVersion, newVersion);
    }

    public synchronized void sync(Map<String, Map<String, Map<Integer, long[]>>> daily,
                                  Map<Integer, String> packages,
                                  Set<String> availableNetworks,
                                  String today,
                                  String replaceStartDay) {
        SQLiteDatabase db = getWritableDatabase();
        long updatedAt = System.currentTimeMillis();
        db.beginTransaction();
        try {
            for (String network : availableNetworks) {
                if (replaceStartDay == null) {
                    db.delete(T_DAILY, "day = ? AND network = ?", new String[]{today, network});
                } else {
                    db.delete(T_DAILY, "day >= ? AND day <= ? AND network = ?",
                            new String[]{replaceStartDay, today, network});
                }
            }

            for (Map.Entry<String, Map<String, Map<Integer, long[]>>> dayEntry : daily.entrySet()) {
                String day = dayEntry.getKey();
                for (Map.Entry<String, Map<Integer, long[]>> netEntry : dayEntry.getValue().entrySet()) {
                    String network = netEntry.getKey();
                    if (!availableNetworks.contains(network)) continue;

                    for (Map.Entry<Integer, long[]> uidEntry : netEntry.getValue().entrySet()) {
                        int uid = uidEntry.getKey();
                        long[] bytes = uidEntry.getValue();
                        long rx = Math.max(0L, bytes[0]);
                        long tx = Math.max(0L, bytes[1]);
                        if (rx + tx <= 0) continue;

                        ContentValues values = new ContentValues();
                        values.put("day", day);
                        values.put("network", network);
                        values.put("uid", uid);
                        values.put("rx", rx);
                        values.put("tx", tx);
                        values.put("updated_at", updatedAt);
                        String packageName = packages.get(uid);
                        if (packageName != null) values.put("package", packageName);

                        if (day.equals(today) || replaceStartDay != null) {
                            db.insertWithOnConflict(T_DAILY, null, values,
                                    SQLiteDatabase.CONFLICT_REPLACE);
                        } else {
                            int updated = db.update(T_DAILY, values,
                                    "day = ? AND network = ? AND uid = ?",
                                    new String[]{day, network, String.valueOf(uid)});
                            if (updated == 0) {
                                db.insertOrThrow(T_DAILY, null, values);
                            }
                        }
                    }
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public synchronized List<Entry> loadRange(String startDay, String endDay) {
        List<Entry> entries = new ArrayList<>();
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT day, network, uid, package, rx, tx FROM " + T_DAILY
                        + " WHERE day >= ? AND day <= ?"
                        + " ORDER BY day ASC, network ASC, uid ASC",
                new String[]{startDay, endDay});
        try {
            while (cursor.moveToNext()) {
                entries.add(new Entry(
                        cursor.getString(0),
                        cursor.getString(1),
                        cursor.getInt(2),
                        cursor.getString(3),
                        cursor.getLong(4),
                        cursor.getLong(5)));
            }
        } finally {
            cursor.close();
        }
        return entries;
    }

    public synchronized void trimBefore(String cutoffDay) {
        getWritableDatabase().delete(T_DAILY, "day < ?", new String[]{cutoffDay});
    }

    public boolean shouldFullSync(long nowMs, String today) {
        long lastSyncAt = getMetaLong(KEY_LAST_SYNC_AT, 0L);
        String lastSyncDay = getMeta(KEY_LAST_SYNC_DAY);
        return lastSyncAt <= 0
                || nowMs - lastSyncAt >= FULL_SYNC_INTERVAL_MS
                || !today.equals(lastSyncDay);
    }

    public void setLastSync(long nowMs, String today) {
        setMeta(KEY_LAST_SYNC_AT, String.valueOf(nowMs));
        setMeta(KEY_LAST_SYNC_DAY, today);
    }

    private String getMeta(String key) {
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT value FROM " + T_META + " WHERE key = ?",
                new String[]{key});
        try {
            return cursor.moveToFirst() ? cursor.getString(0) : null;
        } finally {
            cursor.close();
        }
    }

    private long getMetaLong(String key, long fallback) {
        String value = getMeta(key);
        if (value == null) return fallback;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void setMeta(String key, String value) {
        ContentValues values = new ContentValues();
        values.put("key", key);
        values.put("value", value);
        getWritableDatabase().insertWithOnConflict(T_META, null, values,
                SQLiteDatabase.CONFLICT_REPLACE);
    }

    public static final class Entry {
        public final String day;
        public final String network;
        public final int uid;
        public final String packageName;
        public final long rxBytes;
        public final long txBytes;

        public Entry(String day, String network, int uid, String packageName,
                     long rxBytes, long txBytes) {
            this.day = day;
            this.network = network;
            this.uid = uid;
            this.packageName = packageName;
            this.rxBytes = rxBytes;
            this.txBytes = txBytes;
        }
    }
}
