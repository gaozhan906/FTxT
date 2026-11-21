# Data Usage Hari Ini — Rekap Konsumsi Data

**Status:** Sebagian terimplementasi — rekap, riwayat, filter, grafik, dan daftar aplikasi sudah berjalan; opsi ringkas harian di overlay belum.
**Terakhir diverifikasi:** 2026/11/21
**Tujuan:** Menampilkan pemakaian data harian di panel Network, termasuk rincian per jaringan dan per aplikasi, tanpa mengganggu perilaku overlay kecepatan.

---

## 1. Hasil Implementasi

Fitur inti sudah tersedia di tab **Pemakaian** pada panel Network:

- Ringkasan total RX/TX, rata-rata, dan puncak sesuai periode.
- Periode **Hari ini**, **7 hari**, dan **30 hari**.
- Filter **Semua**, **WiFi**, dan **Seluler**.
- Grafik pemakaian adaptif: per jam untuk hari ini, per hari untuk 7/30 hari.
- Donut komposisi aplikasi: top 7 aplikasi + **Lainnya**.
- Daftar aplikasi dengan persentase terhadap total periode.
- Riwayat harian dan rincian per hari.
- Penanda sumber data `LIVE`, `CACHE`, atau `MIXED`.
- Penanganan izin penggunaan akses yang belum diberikan.

Implementasi tidak memakai `DataUsageTracker`, `TrafficStats`, atau `SharedPreferences usage_*` seperti draf awal.

## 2. Sumber Data Aktual

- **Live:** `NetworkStatsManager` untuk data per jaringan dan per UID.
- **Riwayat:** `UsageHistoryStore` berbasis SQLite (`network_usage_history.db`).
- **Overlay aplikasi:** `ConnectingAppSource` memakai `NetworkStatsManager` + `UsageStatsManager`.
- **Retensi riwayat:** 90 hari; sinkronisasi penuh maksimal setiap 5 menit atau saat hari berganti.
- **Izin:** akses penggunaan aplikasi diperlukan untuk angka per aplikasi; tanpa izin, tab menampilkan state izin.

## 3. Sisa Pekerjaan

- Opsi overlay dua baris untuk ringkasan harian; saat ini overlay Network hanya berisi kecepatan dan overlay aplikasi terpisah.
- Verifikasi perangkat untuk state izin ditolak, seluler tidak tersedia, cache/live campuran, dan retensi 90 hari.
- Pastikan label periode serta sumber data konsisten pada semua state.

## 4. File Utama

- `features/network/src/main/java/exp/ftxt/features/network_stats/usage/`
- `features/network/src/main/java/exp/ftxt/features/network_stats/app_usage/`
- `app/src/main/java/exp/ftxt/ui/NetworkUsageController.java`
- `app/src/main/java/exp/ftxt/ui/NetworkAppDataController.java`
- `app/src/main/res/layout/panel_network_usage.xml`
- `app/src/main/res/layout/panel_network_overlay_app.xml`

## 5. Batasan

- Angka mengikuti data yang masih disediakan `NetworkStatsManager`; SQLite hanya mengisi bagian yang sudah tidak tersedia.
- Data hari berjalan masih bisa berubah dan disimpan dengan replace, bukan penambahan.
- Perbedaan retensi sistem antarperangkat dapat membuat sebagian riwayat hanya tersedia dari cache.

## 6. Catatan Overlay

- Posisi hasil drag pada overlay Network dan App Data kini disimpan di `ftxt_prefs` memakai kunci `network_pos_*` dan `appdata_pos_*` per orientasi, sejajar dengan slider dan D-pad.
- Query overlay App Data memakai penanda generasi agar hasil lama tidak merender setelah modul dimulai ulang saat interval diubah.
- `show()`/`hide()` overlay App Data hanya mereset daftar saat ada transisi state, sehingga start ulang dan perubahan inset keyboard tidak mengosongkan hasil terakhir.
- Perbaikan ini tidak mengubah sumber data, retensi, maupun mode `TOP_TALKERS` yang memang ditetapkan di `ConfigLoader`.
