# Overlay Aplikasi Pengguna Data — Catatan Implementasi

**Status:** Terimplementasi — sub-tab Aplikasi, overlay, konfigurasi, preset, dan integrasi Smart Panel sudah ada; sisa QA lintas versi/perangkat.
**Tanggal:** 2026/11/15
**Diperbarui:** 2026/11/17
**Tujuan:** Menambah sub-tab **Aplikasi** di dalam footer **Overlay** Network untuk menampilkan aplikasi/proses yang sedang memakai data, dengan konfigurasi mengikuti template overlay FTxT (bukan meniru seluruh opsi aplikasi acuan).

---

## 1. Keputusan Desain

- **Penempatan:** sub-tab baru di dalam footer **Overlay** Network, sejajar dengan pengaturan kecepatan. Susunannya menjadi:

  ```
  Panel Network
  └── Footer: Monitor | Overlay
      └── Overlay
          ├── Header tab: Kecepatan | Aplikasi
          ├── Kecepatan  (isi panel_network_overlay.xml saat ini)
          └── Aplikasi   (baru: overlay app yang sedang memakai data)
  ```

  Alasan: pengguna menyebut “subtab”; pola header tab sudah dipakai Monitor Network (Pemakaian | Alat) dan Battery Monitor, sehingga footer tetap dua item.

- **Modul overlay terpisah** (`AppUsageModule`) dari `NetworkModule`. Posisi, lifecycle, preset, dan konfigurasi tidak saling menimpa.
- **Tanpa penyimpanan riwayat baru.** Fitur ini live; riwayat tetap ditangani `UsageHistoryStore` di tab Monitor.
- **Tidak meniru premium/iklan** dan tidak menyalin seluruh opsi “Display settings for app judgment”. Konfigurasi dibatasi pada template umum overlay FTxT.
- **Sumber data:** `UsageStatsManager` untuk aplikasi depan + `NetworkStatsManager` untuk selisih trafik per UID.

---

## 2. Analisis Aplikasi Acuan (Internet Speed Monitor)

**Label yang ditemukan di resource** (`_sample/Internet Speed Monitor/apktool/res/values/strings.xml`):

- `setting_label_detect_traffic_app` — “Determine which communicating app (PRO)”
- `setting_label_detect_traffic_app_available` — “Determine which communicating app (available)”
- `setting_label_connecting_app_new_appearance_setting` — “Display settings for app judgment”
- `setting_label_display_time` — “Display time(%d seconds)”
- `setting_label_monitor_appearance_setting` — “Monitor Appearance Settings”
- `setting_label_monitor_background_color`, `setting_label_monitor_text_color`
- `dialog_message_require_permission_usage_stats` — izin Usage access untuk trafik per aplikasi.

**Mekanisme dari dekompilasi (smali/jadx):**

- `OverlayService` menjalankan loop ±1 detik.
- `UsageStatsManager.queryEvents()` mengambil event jendela terakhir; event `MOVE_TO_FOREGROUND`/`ACTIVITY_RESUMED` dengan timestamp terbaru dipakai sebagai aplikasi aktif.
- `ConnectingAppTrafficQuery(network, startTimeMs, endTimeMs)` + `NetworkStatsManager.querySummary()` mengumpulkan byte per UID.
- Ada baseline (`initializeConnectingAppBaseline`) dan query delta per UID (`queryUidTraffic`); bucket `state` foreground/background dipisahkan.
- Hasil “aplikasi yang sedang berkomunikasi” ditampilkan di overlay, dengan pengaturan tampilan terpisah.

**Yang diadopsi:**

- Deteksi aplikasi depan lewat `UsageEvents`.
- Pengukuran trafik per UID lewat `NetworkStatsManager`.
- Ambang aktivitas + durasi tahan agar overlay tidak berkedip.

**Yang tidak diadopsi:**

- Premium/billing, iklan, ProKey, promosi aplikasi.
- Seluruh variasi appearance acuan.
- Notification listener dan SDK analitik pihak ketiga.

---

## 3. Kondisi FTxT Saat Ini

| Komponen | Peran |
|---|---|
| `features/network/.../NetworkModule.java` | Overlay kecepatan total (`TrafficStats.getTotalRxBytes/TxBytes`), loop `NetworkConfig.updateInterval` |
| `features/network/.../NetworkOverlayView.java` | Badge unduh/unggah, color bar, shadow, background |
| `features/network/.../NetworkConfig.java` | Config statis overlay kecepatan |
| `features/network/.../NetworkStatsSource.java` | Query per UID sederhana (`queryDetails`) |
| `features/network/.../usage/NetworkUsageSource.java` | Sumber data Monitor (per app, per hari, cache + `UsageHistoryStore`) |
| `features/network/.../usage/UsageHistoryStore.java` | SQLite riwayat pemakaian |
| `app/.../ui/NetworkPanelController.java` | Menukar halaman Monitor/Overlay |
| `app/.../ui/NetworkOverlayController.java` | Konfigurasi overlay kecepatan |
| `app/.../ui/NetworkPositionController.java` | D-pad, slider, preset modul `network` |
| `core/.../FloatingService.java` | Registrasi modul overlay + Smart Panel target |
| `core/.../ConfigLoader.java` | Restore config overlay dari prefs |
| `app/.../utils/PermissionHelper.java` | Cek/permintaan Usage access |

Izin `android.permission.PACKAGE_USAGE_STATS` sudah ada di manifest. `minSdk=26`, `targetSdk=35`.

---

## 4. Desain Teknis

### 4.1 Sumber data live (`ConnectingAppSource`)

Lokasi baru: `features/network/src/main/java/exp/ftxt/features/network_stats/app_usage/`.

- Setiap tick (default 1 detik):
  1. `NetworkStatsManager.querySummary(TYPE_WIFI, null, lastQueryMs, now)` dan `TYPE_MOBILE` (subscriberId hanya < Android Q).
  2. Jumlahkan `rxBytes`/`txBytes` per UID untuk state foreground dan background.
  3. Bandingkan dengan snapshot tick sebelumnya → delta byte → Bps.
  4. Ambil aplikasi depan lewat `UsageStatsManager.queryEvents(now - 10s, now)`, event terakhir `MOVE_TO_FOREGROUND`/`ACTIVITY_RESUMED`.
  5. Saring UID yang delta-nya di bawah ambang (default 1 KB/s), lalu urutkan menurun.
  6. Kembalikan maksimal N aplikasi (default 3) berisi: `uid`, `packageName`, `label`, `rxBps`, `txBps`, `foreground`, `lastSeenMs`.
- Resolusi nama paket memakai `PackageManager`; label/ikon di-cache.
- UID tanpa paket diberi label `Android System` (1000), `Google Play Services` (bila terdeteksi), atau `UID <n>`.
- Jika `querySummary` gagal/kosong, pertahankan nilai terakhir maksimal N detik lalu kosongkan.
- Tidak ada akses DB dan tidak ada penulisan prefs per tick.

**Mode tampilan:**

- `APP_FOREGROUND` — hanya aplikasi depan yang sedang memakai data.
- `TOP_TALKERS` — N aplikasi dengan trafik tertinggi, depan atau background (default).

**Threading & beban:**

- Query dijalankan di `ExecutorService` single-thread; view diperbarui di main thread.
- Tick dilewati bila query sebelumnya belum selesai.
- Loop berhenti saat modul `stop()`, view `GONE`, atau layar mati (opsional untuk iterasi pertama: cukup `GONE`).

### 4.2 Overlay (`AppUsageModule` + `AppUsageOverlayView`)

Lokasi baru: `features/network/.../app_usage/`.

- `AppUsageConfig`: enabled, size, color, labelColor, bold, italic, align, vertical, maxRows, mode, interval, thresholdBps, holdMs, showIcon, unitMode, showTotal, shadow, bg, posX/Y, safeArea, touchPassthrough.
- `AppUsageOverlayView`:
  - Baris per aplikasi: `ikon (opsional)  Nama  ↓ 1,2 MB/s  ↑ 30 KB/s`.
  - Maksimal 5 baris; lebar mengikuti layar dengan margin aman.
  - Memakai `ShadowTextView`, `BackgroundConfig`, `ShadowConfig`, dan helper posisi yang sudah ada.
  - Bila tidak ada aktivitas: tampilkan teks `Tidak ada aktivitas data` atau sembunyikan overlay (opsi).
- `AppUsageModule` mengimplementasikan `OverlayModule` + `SmartPanelTarget`, meniru struktur `NetworkModule`:
  - `init/start/stop`, update size/color/shadow/background/layout/position/touch flags.
  - Posisi tersimpan per orientasi (`appdata_pos_x_port/_land`).
  - Judul Smart Panel: `App Data`.
- Durasi tahan (`holdMs`, default 3 detik) mencegah baris hilang saat trafik sesaat turun.

### 4.3 UI konfigurasi (sub-tab Aplikasi)

Layout baru: `app/src/main/res/layout/panel_network_overlay_app.xml`.

Struktur memakai pola section yang sudah ada (`SectionHelper`):

1. **Aktif**
   - `CheckBox` “Overlay Aplikasi”
   - `CheckBox` “Kunci Posisi”
   - Kartu izin Usage access (muncul bila `PermissionHelper.hasUsageAccess()` false) dengan tombol menuju pengaturan.
2. **Tampilan**
   - Ukuran teks, Bold, Italic, jumlah baris (1–5), tampilkan ikon.
   - Warna nilai, warna label, background, shadow (warna/blur/offset).
   - Satuan: kecepatan (`KB/s` / `bps`) atau total byte interval.
3. **Perilaku**
   - Mode: `Aplikasi Depan` / `Top Talkers`.
   - Interval pembaruan: 1–5 detik.
   - Ambang aktivitas: 0 / 1 / 5 / 10 / 50 KB/s.
   - Durasi tahan setelah trafik berhenti: 0–10 detik.
   - Sembunyikan bila tidak ada aktivitas.
4. **Posisi**
   - D-pad + slider X/Y, area aman, serta muat/simpan preset.

Konfigurasi disimpan dengan prefiks `appdata_*` dan dipulihkan di `ConfigLoader`.

### 4.4 Preset & Smart Panel

- Delegate preset baru dengan `moduleType = "appdata"` dan label modul `App Data`.
- Target Smart Panel baru di `FloatingService.registerAppDataTarget()`.
- Preset lama tidak berubah; preset `appdata` hanya menyimpan field yang relevan.

### 4.5 Perubahan navigasi

- `panel_network_overlay.xml` dibungkus:
  - header sub-tab `Kecepatan | Aplikasi` (pola header Monitor Network / Battery Monitor),
  - `FrameLayout` berisi halaman kecepatan (isi lama) dan halaman aplikasi (baru).
- `NetworkPanelController` meneruskan sub-tab ke `NetworkOverlayController`; controller baru `NetworkAppDataController` menangani halaman aplikasi.
- `menu_network_bottom_nav.xml` tetap dua item.

---

## 5. File Kandidat

| File | Aksi |
|---|---|
| `features/network/.../app_usage/AppUsageConfig.java` | baru |
| `features/network/.../app_usage/AppUsageModule.java` | baru |
| `features/network/.../app_usage/AppUsageOverlayView.java` | baru |
| `features/network/.../app_usage/ConnectingAppSource.java` | baru |
| `app/.../ui/NetworkAppDataController.java` | baru |
| `app/src/main/res/layout/panel_network_overlay_app.xml` | baru |
| `app/src/main/res/layout/panel_network_overlay.xml` | tambah header sub-tab + container dua halaman |
| `app/.../ui/NetworkPanelController.java` | teruskan sub-tab |
| `app/.../ui/NetworkOverlayController.java` | sub-tab Kecepatan tetap, delegasi halaman aplikasi |
| `app/.../ui/NetworkAppPositionController.java` | baru (pola `NetworkPositionController`) |
| `core/.../FloatingService.java` | field/getter/ensure module, registrasi Smart Panel |
| `core/.../ConfigLoader.java` | restore `appdata_*` |
| `app/src/main/java/exp/ftxt/SettingsActivity.java` | restore/tampilkan opsi yang perlu (bila ada) |
| `app/src/main/res/menu/menu_network_bottom_nav.xml` | tidak berubah |

Catatan: tidak perlu modul Gradle baru; semua masuk `features/network` + `app`.

---

## 6. Tahapan Pengerjaan

1. **Sumber data & verifikasi** (±3–4 jam)
   - `ConnectingAppSource`, log hasil per tick, uji WiFi/seluler, uji izin mati.
2. **Modul overlay** (±4–6 jam)
   - `AppUsageConfig`, `AppUsageModule`, `AppUsageOverlayView`.
   - Uji posisi, stop/start, rotasi, background/shadow.
3. **UI sub-tab & konfigurasi** (±5–7 jam)
   - Layout halaman aplikasi, controller, sub-tab di Overlay, izin Usage access.
4. **Integrasi service & preset** (±2–3 jam)
   - `FloatingService`, `ConfigLoader`, Smart Panel, preset `appdata`.
5. **QA** (±4–6 jam)
   - Android 8, 10, 13, 15; WiFi/seluler; tanpa Usage access; layar mati/nyala; overlay lain aktif bersamaan; beban baterai.

Estimasi total: **±18–26 jam** (2–3 hari kerja).

---

## 7. Risiko & Batasan

- **Bukan real-time absolut.** `NetworkStatsManager` dapat tertunda beberapa detik/menit; angka adalah estimasi antar-interval.
- **Query per detik mahal.** Batasi interval minimal 1 detik, lewati tick yang tumpang tindih, dan hentikan saat overlay tidak terlihat.
- **Wajib Usage access.** Tanpa izin, mode aplikasi depan tidak tersedia; Top Talkers masih bisa memakai data per UID, tetapi nama paket mungkin terbatas.
- **Android 8–9.** Query seluler per UID memerlukan `subscriberId`; tanpa `READ_PHONE_STATE`, tampilkan hanya WiFi atau pesan terbatas.
- **UID sistem.** Banyak trafik berasal dari `Android System`/Play Services; sediakan label jelas dan opsi menyembunyikan sistem.
- **VPN/tethering.** Trafik dapat tidak terhitung atau terhitung ganda tergantung perangkat.
- **Overlay banyak baris.** Maksimal 5 baris dan hormati area aman agar tidak menutupi layar.
- **Ikon aplikasi.** Memuat banyak ikon dapat menambah beban; cache dan batasi pada aplikasi yang tampil.

---

## 8. Kriteria Penerimaan

- [ ] Sub-tab `Aplikasi` muncul di dalam footer Overlay Network.
- [ ] Overlay menampilkan 1–5 aplikasi yang sedang memakai data.
- [ ] Mode Aplikasi Depan dan Top Talkers berfungsi.
- [ ] Konfigurasi ukuran, warna, background, shadow, posisi, preset, interval, ambang, dan durasi tahan tersimpan setelah aplikasi dibuka ulang.
- [ ] Tanpa Usage access, muncul petunjuk izin dan overlay tidak crash.
- [ ] Tidak ada query DB di main thread; overlay tetap halus saat panel ditutup.
- [ ] Smart Panel dapat memindahkan, menyembunyikan, dan mengubah ukuran overlay aplikasi.
- [ ] Tidak mengganggu overlay kecepatan Network yang sudah ada.

---

## 9. Status

- [x] `ConnectingAppSource`
- [x] `AppUsageConfig`
- [x] `AppUsageOverlayView`
- [x] `AppUsageModule`
- [x] Sub-tab `Kecepatan | Aplikasi`
- [x] `NetworkAppDataController`
- [x] Posisi + preset `appdata`
- [x] Registrasi `FloatingService` + Smart Panel
- [x] Restore `ConfigLoader`
- [ ] QA lintas versi Android

---

## 10. Referensi

- APK acuan: `_sample/Internet Speed Monitor/` (produk: `com.andcreate.app.internetspeedmonitor`, v1.2.1).
- String acuan: `apktool/res/values/strings.xml` baris 238, 301–306, 342.
- Logika acuan: `apktool/smali/ll.smali` (loop foreground), `apktool/smali/vy6.smali` (querySummary per UID), `apktool/smali/com/andcreate/app/internetspeedmonitor/overlay/service/OverlayService.smali`.
- Riset terkait: `_schedule/KAJIAN/DATA_USAGE_NETWORK.md`, `_schedule/KAJIAN/ANALISIS_PENANGANAN_DATA_INTERNET.md`.
- Catatan rilis: fitur baru ini termasuk perubahan **minor**; entry CHANGELOG baru dibuat saat implementasi dimulai.
