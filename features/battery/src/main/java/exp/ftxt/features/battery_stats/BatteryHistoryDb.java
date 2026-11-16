package exp.ftxt.features.battery_stats;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/**
 * Database riwayat baterai (SQLiteOpenHelper bawaan framework, tanpa Room).
 * Empat tabel: sampel metrik time-series (sumber kartu grafik & export snapshot),
 * sesi pengisian daya (metadata lengkap bahan estimator & riwayat sesi),
 * sesi pengosongan (discharge tracker) dan activity log.
 * Tanpa auto-trim — biarkan riwayat hidup; ukuran per baris sangat kecil.
 */
public class BatteryHistoryDb extends SQLiteOpenHelper {

    private static final String DB_NAME = "battery_history.db";
    private static final int DB_VERSION = 6;

    private static final String T_SAMPLES = "samples";
    private static final String T_SESSIONS = "sessions";
    private static final String T_DISCHARGE = "discharge_sessions";
    private static final String T_ACTIVITY = "activity_log";
    private static final String T_META = "meta";
    private static final String T_ANOMALIES = "anomalies";

    private static volatile BatteryHistoryDb instance;

    public static BatteryHistoryDb get(Context context) {
        if (instance == null) {
            synchronized (BatteryHistoryDb.class) {
                if (instance == null) {
                    instance = new BatteryHistoryDb(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private BatteryHistoryDb(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    /**
     * Aktifkan Write-Ahead Logging (WAL) sejak awal. WAL membuat operasi baca tidak
     * pernah diblokir oleh operasi tulis (reader mendapat snapshot), sehingga query
     * grafik/estimasi dari thread UI/background tidak saling mengantre saat sampling
     * menulis setiap beberapa detik. Murni konfigurasi — tidak mengubah skema.
     */
    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        try {
            db.enableWriteAheadLogging();
        } catch (Exception ignored) {}
        // Jaring pengaman: pastikan index pada kolom waktu SELALU ada, termasuk
        // untuk database lama yang dibuat sebelum index ditambahkan. Tanpa index,
        // query rentang (WHERE time >= x ORDER BY time) menjadi full-scan + sort
        // dan bisa memakan puluhan detik (penyebab ANR Estimator). Idempoten.
        try {
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_samples_time ON samples(time)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_activity_time ON activity_log(time)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_discharge_time ON discharge_sessions(start_time)");
        } catch (Exception ignored) {}
        try {
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_anomaly_time ON anomalies(time)");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_anomaly_unique ON anomalies(time,type)");
        } catch (Exception ignored) {}
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + T_SAMPLES + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "time INTEGER NOT NULL," +
                "temp_c REAL NOT NULL," +
                "percent INTEGER NOT NULL," +
                "voltage_v REAL NOT NULL," +
                "current_ma INTEGER NOT NULL," +
                "power_w REAL NOT NULL," +
                "charge_mah INTEGER NOT NULL," +
                "cycle_count INTEGER NOT NULL," +
                "status INTEGER NOT NULL," +
                "plugged INTEGER NOT NULL," +
                "technology TEXT)");
        db.execSQL("CREATE INDEX idx_samples_time ON " + T_SAMPLES + "(time)");

        db.execSQL("CREATE TABLE " + T_SESSIONS + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "time INTEGER NOT NULL," +
                "capacity_mah REAL NOT NULL," +
                "mostly_screen_off INTEGER NOT NULL," +
                "sample_count INTEGER NOT NULL," +
                "start_time INTEGER," +
                "end_time INTEGER," +
                "start_percent INTEGER," +
                "end_percent INTEGER," +
                "charge_mah_counter REAL," +
                "charge_mah_integral REAL," +
                "delta_charge_mah REAL," +
                "temp_min REAL," +
                "temp_max REAL," +
                "temp_avg REAL," +
                "valid INTEGER DEFAULT 1," +
                "invalid_reason TEXT," +
                "calculation_override INTEGER DEFAULT 0)");

        db.execSQL("CREATE TABLE " + T_DISCHARGE + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "start_time INTEGER NOT NULL," +
                "end_time INTEGER NOT NULL," +
                "start_percent INTEGER NOT NULL," +
                "end_percent INTEGER NOT NULL," +
                "used_mah_counter REAL NOT NULL," +
                "used_mah_integral REAL NOT NULL," +
                "capacity_mah REAL," +
                "efficiency_discharge REAL," +
                "screen_off_dominant INTEGER NOT NULL," +
                "temp_min REAL," +
                "temp_max REAL," +
                "temp_avg REAL," +
                "sample_count INTEGER NOT NULL," +
                "valid INTEGER DEFAULT 1," +
                "invalid_reason TEXT," +
                "calculation_override INTEGER DEFAULT 0)");
        db.execSQL("CREATE INDEX idx_discharge_time ON " + T_DISCHARGE + "(start_time)");

        db.execSQL("CREATE TABLE " + T_ACTIVITY + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "time INTEGER NOT NULL," +
                "status INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX idx_activity_time ON " + T_ACTIVITY + "(time)");

        db.execSQL("CREATE TABLE " + T_META + " (" +
                "key TEXT PRIMARY KEY," +
                "value TEXT)");

        createAnomalyTable(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("CREATE TABLE " + T_ACTIVITY + " (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "time INTEGER NOT NULL," +
                    "status INTEGER NOT NULL)");
            db.execSQL("CREATE INDEX idx_activity_time ON " + T_ACTIVITY + "(time)");
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE " + T_SESSIONS + " ADD COLUMN start_time INTEGER");
            db.execSQL("ALTER TABLE " + T_SESSIONS + " ADD COLUMN end_time INTEGER");
            db.execSQL("ALTER TABLE " + T_SESSIONS + " ADD COLUMN start_percent INTEGER");
            db.execSQL("ALTER TABLE " + T_SESSIONS + " ADD COLUMN end_percent INTEGER");
            db.execSQL("ALTER TABLE " + T_SESSIONS + " ADD COLUMN charge_mah_counter REAL");
            db.execSQL("ALTER TABLE " + T_SESSIONS + " ADD COLUMN charge_mah_integral REAL");
            db.execSQL("ALTER TABLE " + T_SESSIONS + " ADD COLUMN delta_charge_mah REAL");
            db.execSQL("ALTER TABLE " + T_SESSIONS + " ADD COLUMN temp_min REAL");
            db.execSQL("ALTER TABLE " + T_SESSIONS + " ADD COLUMN temp_max REAL");
            db.execSQL("ALTER TABLE " + T_SESSIONS + " ADD COLUMN temp_avg REAL");
            db.execSQL("CREATE TABLE " + T_DISCHARGE + " (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "start_time INTEGER NOT NULL," +
                    "end_time INTEGER NOT NULL," +
                    "start_percent INTEGER NOT NULL," +
                    "end_percent INTEGER NOT NULL," +
                    "used_mah_counter REAL NOT NULL," +
                    "used_mah_integral REAL NOT NULL," +
                    "capacity_mah REAL," +
                    "efficiency_discharge REAL," +
                    "screen_off_dominant INTEGER NOT NULL," +
                    "temp_min REAL," +
                    "temp_max REAL," +
                    "temp_avg REAL," +
                    "sample_count INTEGER NOT NULL)");
            db.execSQL("CREATE INDEX idx_discharge_time ON " + T_DISCHARGE + "(start_time)");
        }
        if (oldVersion < 4) {
            try {
                db.execSQL("ALTER TABLE " + T_SESSIONS
                        + " ADD COLUMN valid INTEGER DEFAULT 1");
                db.execSQL("ALTER TABLE " + T_SESSIONS
                        + " ADD COLUMN invalid_reason TEXT");
                db.execSQL("ALTER TABLE " + T_DISCHARGE
                        + " ADD COLUMN valid INTEGER DEFAULT 1");
                db.execSQL("ALTER TABLE " + T_DISCHARGE
                        + " ADD COLUMN invalid_reason TEXT");
            } catch (Exception ignored) {}
        }
        if (oldVersion < 5) {
            try {
                createAnomalyTable(db);
            } catch (Exception ignored) {}
        }
        if (oldVersion < 6) {
            db.execSQL("ALTER TABLE " + T_SESSIONS
                    + " ADD COLUMN calculation_override INTEGER DEFAULT 0");
            db.execSQL("ALTER TABLE " + T_DISCHARGE
                    + " ADD COLUMN calculation_override INTEGER DEFAULT 0");
        }
    }

    private static void createAnomalyTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + T_ANOMALIES + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "time INTEGER NOT NULL," +
                "type TEXT NOT NULL," +
                "severity INTEGER NOT NULL," +
                "percent_before INTEGER," +
                "percent_after INTEGER," +
                "voltage_before REAL," +
                "voltage_after REAL," +
                "current_before INTEGER," +
                "current_after INTEGER," +
                "status_before INTEGER," +
                "status_after INTEGER," +
                "prev_value REAL," +
                "new_value REAL," +
                "reason TEXT," +
                "created_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_anomaly_time ON "
                + T_ANOMALIES + "(time)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_anomaly_unique ON "
                + T_ANOMALIES + "(time,type)");
    }

    /** Baris sesi pengisian daya hasil estimasi (metadata lengkap). */
    public static final class SessionRow {
        public final long time;
        public final float capacityMah;
        public final boolean mostlyScreenOff;
        public final int sampleCount;
        public final long startTime;
        public final long endTime;
        public final int startPercent;
        public final int endPercent;
        public final double mAhCounter;
        public final double mAhIntegral;
        public final double deltaChargeMah;
        public final float tempMin;
        public final float tempMax;
        public final float tempAvg;
        public final boolean valid;
        public final String invalidReason;
        public final int calculationOverride;

        /** Sesi tanpa metadata (mis. migrasi JSON lama atau data v2). */
        SessionRow(long time, float capacityMah, boolean mostlyScreenOff, int sampleCount) {
            this(time, capacityMah, mostlyScreenOff, sampleCount,
                    time, time, -1, -1, 0d, 0d, 0d, 0f, 0f, 0f,
                    true, null);
        }

        SessionRow(long time, float capacityMah, boolean mostlyScreenOff, int sampleCount,
                   long startTime, long endTime, int startPercent, int endPercent,
                   double mAhCounter, double mAhIntegral, double deltaChargeMah,
                   float tempMin, float tempMax, float tempAvg) {
            this(time, capacityMah, mostlyScreenOff, sampleCount,
                    startTime, endTime, startPercent, endPercent,
                    mAhCounter, mAhIntegral, deltaChargeMah,
                    tempMin, tempMax, tempAvg, true, null);
        }

        SessionRow(long time, float capacityMah, boolean mostlyScreenOff, int sampleCount,
                   long startTime, long endTime, int startPercent, int endPercent,
                   double mAhCounter, double mAhIntegral, double deltaChargeMah,
                   float tempMin, float tempMax, float tempAvg,
                   boolean valid, String invalidReason) {
            this(time, capacityMah, mostlyScreenOff, sampleCount, startTime, endTime,
                    startPercent, endPercent, mAhCounter, mAhIntegral, deltaChargeMah,
                    tempMin, tempMax, tempAvg, valid, invalidReason, 0);
        }

        SessionRow(long time, float capacityMah, boolean mostlyScreenOff, int sampleCount,
                   long startTime, long endTime, int startPercent, int endPercent,
                   double mAhCounter, double mAhIntegral, double deltaChargeMah,
                   float tempMin, float tempMax, float tempAvg,
                   boolean valid, String invalidReason, int calculationOverride) {
            this.time = time;
            this.capacityMah = capacityMah;
            this.mostlyScreenOff = mostlyScreenOff;
            this.sampleCount = sampleCount;
            this.startTime = startTime;
            this.endTime = endTime;
            this.startPercent = startPercent;
            this.endPercent = endPercent;
            this.mAhCounter = mAhCounter;
            this.mAhIntegral = mAhIntegral;
            this.deltaChargeMah = deltaChargeMah;
            this.tempMin = tempMin;
            this.tempMax = tempMax;
            this.tempAvg = tempAvg;
            this.valid = valid;
            this.invalidReason = invalidReason;
            this.calculationOverride = calculationOverride;
        }
    }

    /** Baris sesi pengosongan daya (discharge tracker). */
    public static final class DischargeSession {
        public final long startTime;
        public final long endTime;
        public final int startPercent;
        public final int endPercent;
        public final double usedMahCounter;
        public final double usedMahIntegral;
        public final float capacityMah;
        public final float efficiencyPercent;
        public final boolean screenOffDominant;
        public final float tempMin;
        public final float tempMax;
        public final float tempAvg;
        public final int sampleCount;
        public final boolean valid;
        public final String invalidReason;
        public final int calculationOverride;

        DischargeSession(long startTime, long endTime, int startPercent, int endPercent,
                         double usedMahCounter, double usedMahIntegral, float capacityMah,
                         float efficiencyPercent, boolean screenOffDominant,
                         float tempMin, float tempMax, float tempAvg, int sampleCount) {
            this(startTime, endTime, startPercent, endPercent,
                    usedMahCounter, usedMahIntegral, capacityMah,
                    efficiencyPercent, screenOffDominant,
                    tempMin, tempMax, tempAvg, sampleCount, true, null);
        }

        DischargeSession(long startTime, long endTime, int startPercent, int endPercent,
                         double usedMahCounter, double usedMahIntegral, float capacityMah,
                         float efficiencyPercent, boolean screenOffDominant,
                         float tempMin, float tempMax, float tempAvg, int sampleCount,
                         boolean valid, String invalidReason) {
            this(startTime, endTime, startPercent, endPercent, usedMahCounter,
                    usedMahIntegral, capacityMah, efficiencyPercent, screenOffDominant,
                    tempMin, tempMax, tempAvg, sampleCount, valid, invalidReason, 0);
        }

        DischargeSession(long startTime, long endTime, int startPercent, int endPercent,
                         double usedMahCounter, double usedMahIntegral, float capacityMah,
                         float efficiencyPercent, boolean screenOffDominant,
                         float tempMin, float tempMax, float tempAvg, int sampleCount,
                         boolean valid, String invalidReason, int calculationOverride) {
            this.startTime = startTime;
            this.endTime = endTime;
            this.startPercent = startPercent;
            this.endPercent = endPercent;
            this.usedMahCounter = usedMahCounter;
            this.usedMahIntegral = usedMahIntegral;
            this.capacityMah = capacityMah;
            this.efficiencyPercent = efficiencyPercent;
            this.screenOffDominant = screenOffDominant;
            this.tempMin = tempMin;
            this.tempMax = tempMax;
            this.tempAvg = tempAvg;
            this.sampleCount = sampleCount;
            this.valid = valid;
            this.invalidReason = invalidReason;
            this.calculationOverride = calculationOverride;
        }
    }

    /** Entri sesi gabungan (pengisian/pengosongan) untuk daftar & detail sesi. */
    public static final class SessionEntry {
        public final boolean isCharge;
        public final long startTime;
        public final long endTime;
        public final int startPercent;
        public final int endPercent;
        public final double mAhCounter;
        public final double mAhIntegral;
        public final double deltaChargeMah;
        public final float capacityMah;
        public final float efficiencyPercent;
        public final boolean screenOffDominant;
        public final float tempMin;
        public final float tempMax;
        public final float tempAvg;
        public final int sampleCount;
        public final boolean valid;
        public final String invalidReason;
        public final int calculationOverride;

        SessionEntry(SessionRow r) {
            this.isCharge = true;
            this.startTime = r.startTime;
            this.endTime = r.endTime;
            this.startPercent = r.startPercent;
            this.endPercent = r.endPercent;
            this.mAhCounter = r.mAhCounter;
            this.mAhIntegral = r.mAhIntegral;
            this.deltaChargeMah = r.deltaChargeMah;
            this.capacityMah = r.capacityMah;
            this.efficiencyPercent = -1f;
            this.screenOffDominant = r.mostlyScreenOff;
            this.tempMin = r.tempMin;
            this.tempMax = r.tempMax;
            this.tempAvg = r.tempAvg;
            this.sampleCount = r.sampleCount;
            this.valid = r.valid;
            this.invalidReason = r.invalidReason;
            this.calculationOverride = r.calculationOverride;
        }

        SessionEntry(DischargeSession d) {
            this.isCharge = false;
            this.startTime = d.startTime;
            this.endTime = d.endTime;
            this.startPercent = d.startPercent;
            this.endPercent = d.endPercent;
            this.mAhCounter = d.usedMahCounter;
            this.mAhIntegral = d.usedMahIntegral;
            this.deltaChargeMah = 0d;
            this.capacityMah = d.capacityMah;
            this.efficiencyPercent = d.efficiencyPercent;
            this.screenOffDominant = d.screenOffDominant;
            this.tempMin = d.tempMin;
            this.tempMax = d.tempMax;
            this.tempAvg = d.tempAvg;
            this.sampleCount = d.sampleCount;
            this.valid = d.valid;
            this.invalidReason = d.invalidReason;
            this.calculationOverride = d.calculationOverride;
        }

        public boolean isIncludedInCalculation() {
            return calculationOverride == 1 || (calculationOverride == 0 && valid);
        }

        public long durationMs() {
            return Math.max(0L, endTime - startTime);
        }
    }

    /** Agregasi harian/bulanan untuk grafik batang riwayat sesi (mAh). */
    public static final class BarAggregate {
        public final long bucketStartMs;
        public final float chargeMah;
        public final float dischargeMah;
        public final int chargeCount;
        public final int dischargeCount;

        BarAggregate(long bucketStartMs, float chargeMah, float dischargeMah,
                     int chargeCount, int dischargeCount) {
            this.bucketStartMs = bucketStartMs;
            this.chargeMah = chargeMah;
            this.dischargeMah = dischargeMah;
            this.chargeCount = chargeCount;
            this.dischargeCount = dischargeCount;
        }
    }

    // ---------- sampel metrik ----------

    public synchronized void insertSample(BatteryReading.Snapshot s) {
        ContentValues v = new ContentValues();
        v.put("time", s.time);
        v.put("temp_c", s.tempC);
        v.put("percent", s.percent);
        v.put("voltage_v", s.voltageV);
        v.put("current_ma", s.currentMa);
        v.put("power_w", s.powerW);
        v.put("charge_mah", s.chargeMah);
        v.put("cycle_count", s.cycleCount);
        v.put("status", s.statusInt);
        v.put("plugged", s.pluggedInt);
        if (s.technology != null) v.put("technology", s.technology);
        getWritableDatabase().insert(T_SAMPLES, null, v);
    }

    /** Semua sampel sejak fromMs, urut waktu naik. */
    public BatteryReading.Snapshot[] querySamples(long fromMs) {
        return query(fromMs, Long.MAX_VALUE, -1);
    }

    /** N sampel terakhir, urut waktu naik. */
    public BatteryReading.Snapshot[] queryLastSamples(int limit) {
        return query(Long.MIN_VALUE, Long.MAX_VALUE, limit);
    }

    /**
     * Sampel untuk grafik pada rentang [fromMs, toMs]. Bila jumlah baris melebihi
     * targetPoints, tiap bucket waktu menyumbang baris nyata yang menjadi nilai
     * minimum/maksimum persen, tegangan, dan arus — bukan rata-rata. Dengan begitu
     * lonjakan singkat dan area jeda tidak hilang saat grafik diperkecil.
     */
    public BatteryReading.Snapshot[] queryChart(long fromMs, long toMs, int targetPoints) {
        return queryChartRaw(fromMs, toMs, targetPoints);
    }

    private BatteryReading.Snapshot[] queryChartRaw(long fromMs, long toMs, int targetPoints) {
        SQLiteDatabase db = getReadableDatabase();
        long count = 0;
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + T_SAMPLES
                + " WHERE time >= ? AND time <= ?", new String[]{
                String.valueOf(fromMs), String.valueOf(toMs)});
        if (c.moveToFirst()) count = c.getLong(0);
        c.close();
        if (count <= targetPoints) return query(fromMs, toMs, -1);

        long span = Math.max(1L, toMs - fromMs);
        long bucketMs = Math.max(1000L, span / targetPoints);
        String sql = "SELECT DISTINCT s.id, s.time, s.temp_c, s.percent, s.voltage_v,"
                + " s.current_ma, s.power_w, s.charge_mah, s.cycle_count,"
                + " s.status, s.plugged, s.technology"
                + " FROM " + T_SAMPLES + " s"
                + " JOIN (SELECT (time - ?) / ? AS bucket,"
                + " MIN(time) AS tmin, MAX(time) AS tmax,"
                + " MIN(percent) AS pmin, MAX(percent) AS pmax,"
                + " MIN(voltage_v) AS vmin, MAX(voltage_v) AS vmax,"
                + " MIN(current_ma) AS cmin, MAX(current_ma) AS cmax"
                + " FROM " + T_SAMPLES + " WHERE time >= ? AND time <= ?"
                + " GROUP BY bucket) g ON ((time - ?) / ?) = g.bucket"
                + " WHERE s.id = (SELECT s2.id FROM " + T_SAMPLES
                + " s2 WHERE s2.time = g.tmin LIMIT 1)"
                + " OR s.id = (SELECT s2.id FROM " + T_SAMPLES
                + " s2 WHERE s2.time = g.tmax LIMIT 1)"
                + " OR s.id = (SELECT s2.id FROM " + T_SAMPLES
                + " s2 WHERE s2.time >= g.tmin AND s2.time <= g.tmax"
                + " AND s2.percent = g.pmin LIMIT 1)"
                + " OR s.id = (SELECT s2.id FROM " + T_SAMPLES
                + " s2 WHERE s2.time >= g.tmin AND s2.time <= g.tmax"
                + " AND s2.percent = g.pmax LIMIT 1)"
                + " OR s.id = (SELECT s2.id FROM " + T_SAMPLES
                + " s2 WHERE s2.time >= g.tmin AND s2.time <= g.tmax"
                + " AND s2.voltage_v = g.vmin LIMIT 1)"
                + " OR s.id = (SELECT s2.id FROM " + T_SAMPLES
                + " s2 WHERE s2.time >= g.tmin AND s2.time <= g.tmax"
                + " AND s2.voltage_v = g.vmax LIMIT 1)"
                + " OR s.id = (SELECT s2.id FROM " + T_SAMPLES
                + " s2 WHERE s2.time >= g.tmin AND s2.time <= g.tmax"
                + " AND s2.current_ma = g.cmin LIMIT 1)"
                + " OR s.id = (SELECT s2.id FROM " + T_SAMPLES
                + " s2 WHERE s2.time >= g.tmin AND s2.time <= g.tmax"
                + " AND s2.current_ma = g.cmax LIMIT 1)"
                + " ORDER BY s.time ASC";
        String from = String.valueOf(fromMs);
        String bucket = String.valueOf(bucketMs);
        String[] args = new String[]{from, bucket, from, String.valueOf(toMs),
                from, bucket};
        ArrayList<BatteryReading.Snapshot> out = new ArrayList<>();
        Cursor cur = db.rawQuery(sql, args);
        try {
            while (cur.moveToNext()) {
                out.add(new BatteryReading.Snapshot(
                        cur.getLong(1),
                        cur.getFloat(2),
                        cur.getInt(3),
                        cur.getFloat(4),
                        cur.getInt(5),
                        cur.getDouble(6),
                        cur.getLong(7),
                        cur.getInt(8),
                        cur.getInt(9),
                        cur.getInt(10),
                        cur.isNull(11) ? null : cur.getString(11)));
            }
        } finally {
            cur.close();
        }
        return out.toArray(new BatteryReading.Snapshot[0]);
    }

    /** Ambang gap waktu antar sampel; jeda lebih panjang dianggap periode tanpa data. */
    public static final long GAP_THRESHOLD_MS = 60_000L;

    private BatteryReading.Snapshot[] query(long fromMs, long toMs, int limit) {
        ArrayList<BatteryReading.Snapshot> out = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        StringBuilder sql = new StringBuilder("SELECT time, temp_c, percent, voltage_v,"
                + " current_ma, power_w, charge_mah, cycle_count, status, plugged, technology"
                + " FROM " + T_SAMPLES);
        boolean hasFrom = fromMs != Long.MIN_VALUE;
        boolean hasTo = toMs != Long.MAX_VALUE;
        if (hasFrom || hasTo) {
            sql.append(" WHERE");
            if (hasFrom) sql.append(" time >= ").append(fromMs);
            if (hasFrom && hasTo) sql.append(" AND");
            if (hasTo) sql.append(" time <= ").append(toMs);
        }
        sql.append(" ORDER BY time ");
        sql.append(limit > 0 ? "DESC" : "ASC");
        if (limit > 0) sql.append(" LIMIT ").append(limit);

        Cursor c = db.rawQuery(sql.toString(), null);
        try {
            while (c.moveToNext()) {
                out.add(new BatteryReading.Snapshot(
                        c.getLong(0), c.getFloat(1), c.getInt(2), c.getFloat(3),
                        c.getInt(4), c.getDouble(5), c.getLong(6), c.getInt(7),
                        c.getInt(8), c.getInt(9), c.isNull(10) ? null : c.getString(10)));
            }
        } finally {
            c.close();
        }
        return out.toArray(new BatteryReading.Snapshot[0]);
    }

    // ---------- estimasi waktu ----------

    /**
     * Mengembalikan estimasi waktu (ms) hingga baterai penuh atau habis.
     * Charging: rate dihitung dari 10 menit terakhir, lalu extrapolate ke 100%.
     * Discharging: rate dihitung dari 30 menit terakhir, lalu extrapolate ke 0%.
     * Mengembalikan -1 jika data tidak cukup.
     */
    public long estimateTimeRemaining(boolean charging) {
        long now = System.currentTimeMillis();
        long windowMs = charging ? 600_000L : 1_800_000L;
        BatteryReading.Snapshot[] samples = query(now - windowMs, now, -1);
        if (samples.length < 2) return -1;

        long tFirst = samples[0].time;
        long tLast = samples[samples.length - 1].time;
        long dt = tLast - tFirst;
        if (dt < 30_000L) return -1;

        int pFirst = samples[0].percent;
        int pLast = samples[samples.length - 1].percent;
        double ratePerMs = (double) (pLast - pFirst) / dt;
        if (Math.abs(ratePerMs) < 1e-9) return -1;

        if (charging) {
            int remaining = 100 - pLast;
            if (remaining <= 0) return 0;
            return (long) (remaining / ratePerMs);
        } else {
            if (pLast <= 0) return 0;
            return (long) (pLast / -ratePerMs);
        }
    }

    // ---------- riwayat sesi pengisian ----------

    /** Hasil analisis satu sesi pengisian dari data sampel. */
    public static final class ChargingSession {
        public final long startTime;
        public final long endTime;
        public final int startPercent;
        public final int endPercent;
        public final long durationMs;
        public final String pluggedType;

        ChargingSession(long startTime, long endTime, int startPercent, int endPercent,
                        long durationMs, String pluggedType) {
            this.startTime = startTime;
            this.endTime = endTime;
            this.startPercent = startPercent;
            this.endPercent = endPercent;
            this.durationMs = durationMs;
            this.pluggedType = pluggedType;
        }
    }

    /**
     * Mengembalikan 5 sesi pengisian terakhir dari tabel sampel.
     * Sesi = rangkaian sampel berurutan dengan status charging/full.
     * Sesi baru dimulai setelah gap >15 menit tanpa data charging.
     */
    public ArrayList<ChargingSession> queryChargingSessions(int maxSessions) {
        long now = System.currentTimeMillis();
        long fromMs = now - 7L * 24 * 3600_000L;
        BatteryReading.Snapshot[] all = query(fromMs, now, -1);
        if (all.length < 2) return new ArrayList<>();

        ArrayList<ChargingSession> sessions = new ArrayList<>();
        long segStart = -1;
        int segStartPercent = -1;
        long segLastTime = -1;
        int segLastPercent = -1;
        String segPlugged = null;
        long GAP_MS = 900_000L;

        for (BatteryReading.Snapshot s : all) {
            boolean charging = s.statusInt == android.os.BatteryManager.BATTERY_STATUS_CHARGING
                    || s.statusInt == android.os.BatteryManager.BATTERY_STATUS_FULL;
            if (charging) {
                if (segStart < 0) {
                    segStart = s.time;
                    segStartPercent = s.percent;
                    segPlugged = pluggedLabel(s.pluggedInt);
                }
                segLastTime = s.time;
                segLastPercent = s.percent;
            } else {
                if (segStart >= 0 && segLastPercent > segStartPercent) {
                    sessions.add(new ChargingSession(
                            segStart, segLastTime, segStartPercent, segLastPercent,
                            segLastTime - segStart, segPlugged));
                }
                segStart = -1;
                segStartPercent = -1;
                segPlugged = null;
            }
        }
        if (segStart >= 0 && segLastPercent > segStartPercent) {
            sessions.add(new ChargingSession(
                    segStart, segLastTime, segStartPercent, segLastPercent,
                    segLastTime - segStart, segPlugged));
        }

        ArrayList<ChargingSession> result = new ArrayList<>();
        for (int i = sessions.size() - 1; i >= 0 && result.size() < maxSessions; i--) {
            result.add(sessions.get(i));
        }
        return result;
    }

    private static String pluggedLabel(int plugged) {
        switch (plugged) {
            case android.os.BatteryManager.BATTERY_PLUGGED_AC: return "AC";
            case android.os.BatteryManager.BATTERY_PLUGGED_USB: return "USB";
            case android.os.BatteryManager.BATTERY_PLUGGED_WIRELESS: return "Wireless";
            default: return "";
        }
    }

    // ---------- sesi pengisian (estimator) ----------

    public void insertSession(long time, float capacityMah, boolean mostlyScreenOff, int sampleCount) {
        insertSessionFull(new SessionRow(time, capacityMah, mostlyScreenOff, sampleCount));
    }

    public synchronized void insertSessionFull(SessionRow r) {
        ContentValues v = new ContentValues();
        v.put("time", r.time);
        v.put("capacity_mah", r.capacityMah);
        v.put("mostly_screen_off", r.mostlyScreenOff ? 1 : 0);
        v.put("sample_count", r.sampleCount);
        if (r.startTime > 0) v.put("start_time", r.startTime);
        if (r.endTime > 0) v.put("end_time", r.endTime);
        if (r.startPercent >= 0) v.put("start_percent", r.startPercent);
        if (r.endPercent >= 0) v.put("end_percent", r.endPercent);
        v.put("charge_mah_counter", r.mAhCounter);
        v.put("charge_mah_integral", r.mAhIntegral);
        v.put("delta_charge_mah", r.deltaChargeMah);
        v.put("temp_min", r.tempMin);
        v.put("temp_max", r.tempMax);
        v.put("temp_avg", r.tempAvg);
        v.put("valid", r.valid ? 1 : 0);
        v.put("calculation_override", r.calculationOverride);
        if (r.invalidReason != null) v.put("invalid_reason", r.invalidReason);
        getWritableDatabase().insert(T_SESSIONS, null, v);
    }

    public ArrayList<SessionRow> getSessions() {
        ArrayList<SessionRow> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT time, capacity_mah, mostly_screen_off, sample_count,"
                        + " start_time, end_time, start_percent, end_percent,"
                        + " charge_mah_counter, charge_mah_integral, delta_charge_mah,"
                        + " temp_min, temp_max, temp_avg, valid, invalid_reason, calculation_override"
                        + " FROM " + T_SESSIONS + " ORDER BY time ASC", null);
        try {
            while (c.moveToNext()) {
                out.add(new SessionRow(c.getLong(0), c.getFloat(1),
                        c.getInt(2) == 1, c.getInt(3),
                        c.isNull(4) ? c.getLong(0) : c.getLong(4),
                        c.isNull(5) ? c.getLong(0) : c.getLong(5),
                        c.isNull(6) ? -1 : c.getInt(6),
                        c.isNull(7) ? -1 : c.getInt(7),
                        c.isNull(8) ? 0d : c.getDouble(8),
                        c.isNull(9) ? 0d : c.getDouble(9),
                        c.isNull(10) ? 0d : c.getDouble(10),
                        c.isNull(11) ? 0f : c.getFloat(11),
                        c.isNull(12) ? 0f : c.getFloat(12),
                        c.isNull(13) ? 0f : c.getFloat(13),
                        c.isNull(14) || c.getInt(14) == 1,
                        c.isNull(15) ? null : c.getString(15),
                        c.getInt(16)));
            }
        } finally {
            c.close();
        }
        return out;
    }

    public synchronized void deleteAllSessions() {
        getWritableDatabase().delete(T_SESSIONS, null, null);
    }

    public synchronized void deleteAllDischargeSessions() {
        getWritableDatabase().delete(T_DISCHARGE, null, null);
    }

    /** Hapus sampel metrik yang lebih tua dari {@code beforeMs} (pruning data lama agar riwayat tidak membengkak). */
    public synchronized void deleteSamplesOlderThan(long beforeMs) {
        getWritableDatabase().delete(T_SAMPLES, "time < ?",
                new String[]{String.valueOf(beforeMs)});
    }

    /** Hapus log aktivitas layar/status yang lebih tua dari {@code beforeMs}. */
    public synchronized void deleteActivityLogOlderThan(long beforeMs) {
        getWritableDatabase().delete(T_ACTIVITY, "time < ?",
                new String[]{String.valueOf(beforeMs)});
    }

    /** Sesi pengisian (tabel serial resmi estimator) pada rentang [fromMs, toMs]. */
    public ArrayList<SessionRow> queryChargeSessions(long fromMs, long toMs) {
        ArrayList<SessionRow> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT time, capacity_mah, mostly_screen_off, sample_count,"
                        + " start_time, end_time, start_percent, end_percent,"
                        + " charge_mah_counter, charge_mah_integral, delta_charge_mah,"
                        + " temp_min, temp_max, temp_avg, valid, invalid_reason, calculation_override"
                        + " FROM " + T_SESSIONS
                        + " WHERE end_time >= ? AND start_time <= ?"
                        + " ORDER BY start_time ASC",
                new String[]{String.valueOf(fromMs), String.valueOf(toMs)});
        try {
            while (c.moveToNext()) {
                out.add(new SessionRow(c.getLong(0), c.getFloat(1),
                        c.getInt(2) == 1, c.getInt(3),
                        c.isNull(4) ? c.getLong(0) : c.getLong(4),
                        c.isNull(5) ? c.getLong(0) : c.getLong(5),
                        c.isNull(6) ? -1 : c.getInt(6),
                        c.isNull(7) ? -1 : c.getInt(7),
                        c.isNull(8) ? 0d : c.getDouble(8),
                        c.isNull(9) ? 0d : c.getDouble(9),
                        c.isNull(10) ? 0d : c.getDouble(10),
                        c.isNull(11) ? 0f : c.getFloat(11),
                        c.isNull(12) ? 0f : c.getFloat(12),
                        c.isNull(13) ? 0f : c.getFloat(13),
                        c.isNull(14) || c.getInt(14) == 1,
                        c.isNull(15) ? null : c.getString(15),
                        c.getInt(16)));
            }
        } finally {
            c.close();
        }
        return out;
    }

    // ---------- sesi pengosongan (discharge tracker) ----------

    public synchronized void insertDischargeSession(DischargeSession s) {
        ContentValues v = new ContentValues();
        v.put("start_time", s.startTime);
        v.put("end_time", s.endTime);
        v.put("start_percent", s.startPercent);
        v.put("end_percent", s.endPercent);
        v.put("used_mah_counter", s.usedMahCounter);
        v.put("used_mah_integral", s.usedMahIntegral);
        v.put("capacity_mah", s.capacityMah);
        v.put("efficiency_discharge", s.efficiencyPercent);
        v.put("screen_off_dominant", s.screenOffDominant ? 1 : 0);
        v.put("temp_min", s.tempMin);
        v.put("temp_max", s.tempMax);
        v.put("temp_avg", s.tempAvg);
        v.put("sample_count", s.sampleCount);
        v.put("valid", s.valid ? 1 : 0);
        v.put("calculation_override", s.calculationOverride);
        if (s.invalidReason != null) v.put("invalid_reason", s.invalidReason);
        getWritableDatabase().insert(T_DISCHARGE, null, v);
    }

    /** Sesi pengosongan pada rentang [fromMs, toMs], urut mulai naik. */
    public ArrayList<DischargeSession> queryDischargeSessions(long fromMs, long toMs) {
        ArrayList<DischargeSession> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT start_time, end_time, start_percent, end_percent,"
                        + " used_mah_counter, used_mah_integral, capacity_mah,"
                        + " efficiency_discharge, screen_off_dominant,"
                        + " temp_min, temp_max, temp_avg, sample_count,"
                        + " valid, invalid_reason, calculation_override"
                        + " FROM " + T_DISCHARGE
                        + " WHERE end_time >= ? AND start_time <= ?"
                        + " ORDER BY start_time ASC",
                new String[]{String.valueOf(fromMs), String.valueOf(toMs)});
        try {
            while (c.moveToNext()) {
                out.add(new DischargeSession(c.getLong(0), c.getLong(1),
                        c.getInt(2), c.getInt(3),
                        c.getDouble(4), c.getDouble(5),
                        c.isNull(6) ? 0f : c.getFloat(6),
                        c.isNull(7) ? -1f : c.getFloat(7),
                        c.getInt(8) == 1,
                        c.isNull(9) ? 0f : c.getFloat(9),
                        c.isNull(10) ? 0f : c.getFloat(10),
                        c.isNull(11) ? 0f : c.getFloat(11),
                        c.getInt(12),
                        c.isNull(13) || c.getInt(13) == 1,
                        c.isNull(14) ? null : c.getString(14),
                        c.getInt(15)));
            }
        } finally {
            c.close();
        }
        return out;
    }

    /** Override: 1=ikutkan, -1=keluarkan, 0=kembali ke keputusan otomatis. */
    public synchronized void setCalculationOverride(boolean charge, long startTime,
                                                     long endTime, int override) {
        if (override != -1 && override != 0 && override != 1) return;
        ContentValues values = new ContentValues();
        values.put("calculation_override", override);
        String table = charge ? T_SESSIONS : T_DISCHARGE;
        getWritableDatabase().update(table, values,
                "start_time = ? AND end_time = ?",
                new String[]{String.valueOf(startTime), String.valueOf(endTime)});
    }

    /** Gabungan sesi pengisian & pengosongan pada rentang, urut waktu naik. */
    public ArrayList<SessionEntry> querySessionEntries(long fromMs, long toMs) {
        ArrayList<SessionEntry> out = new ArrayList<>();
        for (SessionRow r : queryChargeSessions(fromMs, toMs)) out.add(new SessionEntry(r));
        for (DischargeSession d : queryDischargeSessions(fromMs, toMs)) out.add(new SessionEntry(d));
        Collections.sort(out, new Comparator<SessionEntry>() {
            @Override
            public int compare(SessionEntry a, SessionEntry b) {
                return Long.compare(b.startTime, a.startTime);
            }
        });
        return out;
    }

/**
      * Agregat sesi per hari (MODE_DAILY), per minggu (MODE_WEEKLY), atau
      * per bulan (MODE_MONTHLY) pada rentang [fromMs, toMs] untuk grafik
      * batang. Urut naik.
      */
    public static final int MODE_DAILY = 0;
    public static final int MODE_WEEKLY = 1;
    public static final int MODE_MONTHLY = 2;

    public ArrayList<BarAggregate> queryBarAggregates(long fromMs, long toMs, int mode) {
        Map<Long, BarAggregate> map = new HashMap<>();
        for (SessionRow r : queryChargeSessions(fromMs, toMs)) {
            if (!(r.calculationOverride == 1 || (r.calculationOverride == 0 && r.valid))) continue;
            long bucket = bucketStart(r.endTime, mode);
            BarAggregate a = map.get(bucket);
            if (a == null) {
                a = new BarAggregate(bucket, 0f, 0f, 0, 0);
                map.put(bucket, a);
            }
            float mAh = r.calculationOverride == 1 && r.mAhIntegral > 0
                    ? (float) r.mAhIntegral
                    : r.mAhCounter > 0 ? (float) r.mAhCounter
                    : r.mAhIntegral > 0 ? (float) r.mAhIntegral : 0f;
            map.put(bucket, new BarAggregate(a.bucketStartMs,
                    a.chargeMah + mAh, a.dischargeMah,
                    a.chargeCount + 1, a.dischargeCount));
        }
        for (DischargeSession d : queryDischargeSessions(fromMs, toMs)) {
            if (!(d.calculationOverride == 1 || (d.calculationOverride == 0 && d.valid))) continue;
            long bucket = bucketStart(d.endTime, mode);
            BarAggregate a = map.get(bucket);
            if (a == null) {
                a = new BarAggregate(bucket, 0f, 0f, 0, 0);
                map.put(bucket, a);
            }
            float mAh = d.calculationOverride == 1 && d.usedMahIntegral > 0
                    ? (float) d.usedMahIntegral
                    : d.usedMahCounter > 0 ? (float) d.usedMahCounter
                    : d.usedMahIntegral > 0 ? (float) d.usedMahIntegral : 0f;
            map.put(bucket, new BarAggregate(a.bucketStartMs, a.chargeMah,
                    a.dischargeMah + mAh,
                    a.chargeCount, a.dischargeCount + 1));
        }
        ArrayList<BarAggregate> out = new ArrayList<>(map.values());
        Collections.sort(out, new Comparator<BarAggregate>() {
            @Override
            public int compare(BarAggregate a, BarAggregate b) {
                return Long.compare(a.bucketStartMs, b.bucketStartMs);
            }
        });
        return out;
    }

    private static long bucketStart(long ms, int mode) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ms);
        if (mode == MODE_MONTHLY) {
            c.set(Calendar.DAY_OF_MONTH, 1);
        } else if (mode == MODE_WEEKLY) {
            int dow = c.get(Calendar.DAY_OF_WEEK);
            if (dow != Calendar.MONDAY) {
                c.add(Calendar.DAY_OF_YEAR, -(dow - Calendar.MONDAY));
            }
        }
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    // ---------- activity log ----------

    /** Akhir sesi pengisian terakhir yang tersimpan (end_time maks); -1 bila kosong/tanpa waktu. */
    public long lastChargeSessionEnd() {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT MAX(end_time) FROM " + T_SESSIONS, null);
        try {
            return c.moveToFirst() && !c.isNull(0) ? c.getLong(0) : -1L;
        } finally {
            c.close();
        }
    }

    /** Akhir sesi pengosongan terakhir yang tersimpan (end_time maks); -1 bila kosong. */
    public long lastDischargeSessionEnd() {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT MAX(end_time) FROM " + T_DISCHARGE, null);
        try {
            return c.moveToFirst() && !c.isNull(0) ? c.getLong(0) : -1L;
        } finally {
            c.close();
        }
    }

    /** Oracle layar-nyala pada rentang [fromMs, toMs] dari activity log. */
    public SessionSegmentBuilder.ScreenOnOracle screenOnOracle(long fromMs, long toMs) {
        ArrayList<ActivityLog> logs = queryActivityLog(fromMs, toMs);
        int n = logs.size();
        long[] times = new long[n];
        boolean[] on = new boolean[n];
        for (int i = 0; i < n; i++) {
            times[i] = logs.get(i).time;
            on[i] = logs.get(i).status == 1;
        }
        return SessionSegmentBuilder.screenOnOracle(times, on);
    }

    public static final class ActivityLog {
        public final long time;
        public final int status;

        public ActivityLog(long time, int status) {
            this.time = time;
            this.status = status;
        }
    }

    public synchronized void insertActivityLog(long time, int status) {
        ContentValues v = new ContentValues();
        v.put("time", time);
        v.put("status", status);
        getWritableDatabase().insert(T_ACTIVITY, null, v);
    }

    /** Query activity log pada rentang [fromMs, toMs], urut waktu naik. */
    public ArrayList<ActivityLog> queryActivityLog(long fromMs, long toMs) {
        ArrayList<ActivityLog> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT time, status FROM " + T_ACTIVITY
                        + " WHERE time >= ? AND time <= ? ORDER BY time ASC",
                new String[]{String.valueOf(fromMs), String.valueOf(toMs)});
        try {
            while (c.moveToNext()) {
                out.add(new ActivityLog(c.getLong(0), c.getInt(1)));
            }
        } finally {
            c.close();
        }
        return out;
    }

    // ---------- anomali ----------

    public static final class AnomalyRow {
        public final long time;
        public final String type;
        public final int severity;
        public final int percentBefore;
        public final int percentAfter;
        public final float voltageBefore;
        public final float voltageAfter;
        public final int currentBefore;
        public final int currentAfter;
        public final int statusBefore;
        public final int statusAfter;
        public final double prevValue;
        public final double newValue;
        public final String reason;

        AnomalyRow(long time, String type, int severity,
                   int percentBefore, int percentAfter,
                   float voltageBefore, float voltageAfter,
                   int currentBefore, int currentAfter,
                   int statusBefore, int statusAfter,
                   double prevValue, double newValue, String reason) {
            this.time = time;
            this.type = type;
            this.severity = severity;
            this.percentBefore = percentBefore;
            this.percentAfter = percentAfter;
            this.voltageBefore = voltageBefore;
            this.voltageAfter = voltageAfter;
            this.currentBefore = currentBefore;
            this.currentAfter = currentAfter;
            this.statusBefore = statusBefore;
            this.statusAfter = statusAfter;
            this.prevValue = prevValue;
            this.newValue = newValue;
            this.reason = reason;
        }
    }

    public synchronized void insertAnomaly(BatteryAnomaly a) {
        if (a == null || a.time <= 0L) return;
        ContentValues v = new ContentValues();
        v.put("time", a.time);
        v.put("type", a.type);
        v.put("severity", a.severity);
        v.put("percent_before", a.percentBefore);
        v.put("percent_after", a.percentAfter);
        v.put("voltage_before", a.voltageBefore);
        v.put("voltage_after", a.voltageAfter);
        v.put("current_before", a.currentBefore);
        v.put("current_after", a.currentAfter);
        v.put("status_before", a.statusBefore);
        v.put("status_after", a.statusAfter);
        v.put("prev_value", a.prevValue);
        v.put("new_value", a.newValue);
        if (a.reason != null) v.put("reason", a.reason);
        v.put("created_at", System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict(T_ANOMALIES, null, v,
                SQLiteDatabase.CONFLICT_IGNORE);
    }

    public ArrayList<AnomalyRow> queryAnomalies(long fromMs, long toMs) {
        ArrayList<AnomalyRow> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT time, type, severity, percent_before, percent_after,"
                        + " voltage_before, voltage_after, current_before, current_after,"
                        + " status_before, status_after, prev_value, new_value, reason"
                        + " FROM " + T_ANOMALIES
                        + " WHERE time >= ? AND time <= ? ORDER BY time ASC",
                new String[]{String.valueOf(fromMs), String.valueOf(toMs)});
        try {
            while (c.moveToNext()) {
                out.add(new AnomalyRow(c.getLong(0), c.getString(1), c.getInt(2),
                        c.getInt(3), c.getInt(4), c.getFloat(5), c.getFloat(6),
                        c.getInt(7), c.getInt(8), c.getInt(9), c.getInt(10),
                        c.getDouble(11), c.getDouble(12),
                        c.isNull(13) ? null : c.getString(13)));
            }
        } finally {
            c.close();
        }
        return out;
    }

    // ---------- meta ----------

    public synchronized void setMeta(String key, String value) {
        ContentValues v = new ContentValues();
        v.put("key", key);
        v.put("value", value);
        getWritableDatabase().insertWithOnConflict(T_META, null, v,
                SQLiteDatabase.CONFLICT_REPLACE);
    }

    public String getMeta(String key) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT value FROM " + T_META + " WHERE key = ?", new String[]{key});
        try {
            return c.moveToFirst() ? c.getString(0) : null;
        } finally {
            c.close();
        }
    }
}
