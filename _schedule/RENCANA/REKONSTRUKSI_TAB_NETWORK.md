# Rekonstruksi Tab Network — Rencana Kerja

**Status:** Rencana — desain final, belum dikerjakan  
**Dibuat:** 2026/10/31 07:47 WITA  
**Diperbarui:** 2026/10/31 08:30 WITA — seluruh keputusan D1–D12 dan wireframe sudah final  
**Baseline:** working tree 4.96.0 (versionCode 331), belum di-commit  
**Cakupan:** panel Network — footer Monitor/Overlay, khususnya tab Monitor bagian pemakaian data.  
**Hubungan dengan dokumen lain:** dokumen ini menjadi acuan rekonstruksi UI/UX dan struktur kode. `DATA_USAGE_NETWORK.md` tetap disimpan sebagai riset fitur, tetapi bukan lagi acuan tata letak final.

> **Aturan kerja dokumen ini:** tidak ada perubahan kode sampai user memberi perintah **"kerjakan"**. Setiap fase harus disetujui sebelum lanjut. Jangan menambah fitur, mengubah nama, atau memindahkan file di luar fase yang sedang dikerjakan.

---

## 1. Latar Belakang

Tab Network saat ini tumbuh dari beberapa iterasi terpisah:

- Footer **Monitor / Overlay** sudah ada.
- Di dalam Monitor ada tiga header: **Pemakaian / Aplikasi / Alat**.
- Tab Pemakaian memakai `TrafficStats` total perangkat + riwayat prefs 7 hari.
- Tab Aplikasi memakai `NetworkStatsManager` per UID untuk hari ini.
- Tab Alat masih placeholder.
- Donut per aplikasi baru ditambahkan dan hanya mencakup hari ini.

Akibatnya informasi terpecah, periode tidak seragam, dan sumber data berbeda. Ini bukan sekadar masalah tampilan: angka ringkasan, donut, dan daftar aplikasi bisa tidak saling cocok, sementara tombol reset hanya memengaruhi sebagian data.

Rekonstruksi ini bertujuan menyatukan informasi pemakaian menjadi satu layar yang konsisten, dengan satu periode, satu filter jaringan, dan satu sumber data.

---

## 2. Masalah yang Harus Diselesaikan

### 2.1 Informasi terpecah

- Ringkasan total ada di tab **Pemakaian**.
- Rincian aplikasi ada di tab **Aplikasi**.
- Pengguna harus berpindah tab untuk melihat total dan penyebab pemakaian, padahal keduanya satu kesatuan.

### 2.2 Periode tidak konsisten

- Tab Pemakaian menampilkan hari ini + grafik 7 hari.
- Tab Aplikasi hanya menampilkan hari ini.
- Grafik 7 hari bersifat tetap; tidak ada pilihan 30 hari atau rentang lain.
- Judul periode donut tidak terlihat jelas.

### 2.3 Sumber data dan semantik berbeda

- Pemakaian: `TrafficStats.getTotalRxBytes()/getTotalTxBytes()` — total perangkat, disimpan sebagai baseline di `SharedPreferences`.
- Aplikasi: `NetworkStatsManager.queryDetails()` — per UID, langsung dari sistem.
- Angka total dan jumlah per aplikasi tidak dijamin sama.
- Data sistem bisa terlambat; data `TrafficStats` bergantung pada sampling aplikasi.
- Reset hanya bekerja pada `DataUsageTracker`, tidak pada data per aplikasi.

### 2.4 Metrik tidak seragam

- "Rata-rata" di tab Pemakaian adalah per jam.
- "Puncak" saat ini adalah puncak kecepatan sampling, bukan puncak pemakaian harian.
- Untuk periode 7/30 hari, metrik per jam tidak relevan.
- Donut dan daftar tidak menampilkan persentase yang konsisten satu sama lain.

### 2.5 Struktur kode terlalu besar

- `NetworkPanelController.java` ±1.133 baris: mengurus overlay, penggunaan data, aplikasi, alat, izin, dan konfigurasi sekaligus.
- `panel_network.xml` ±1.222 baris: dua halaman besar dalam satu file.
- Navigasi internal memakai nama yang menyesatkan: `networkSubSpeedPage` adalah halaman Overlay, bukan "speed page".
- Sulit diuji, sulit diubah, dan mudah menimbulkan regresi.

### 2.6 Tidak ada rencana menyeluruh

Pekerjaan sebelumnya dilakukan per potongan tanpa urutan yang disepakati. Dokumen ini menggantinya dengan urutan fase, keputusan yang perlu disetujui, dan kriteria selesai.

---

## 3. Tujuan dan Non-Tujuan

### 3.1 Tujuan

1. Menyatukan ringkasan, grafik, riwayat, donut, dan daftar aplikasi dalam satu layar **Pemakaian**.
2. Menyediakan satu **pemilih periode** dan satu **pemilih jenis jaringan** yang berlaku untuk semua komponen.
3. Memakai satu sumber data utama agar total dan rincian aplikasi konsisten.
4. Menghilangkan metrik yang menyesatkan dan memberi label periode/rentang secara eksplisit.
5. Memecah controller dan layout besar menjadi bagian yang lebih fokus.
6. Menyimpan riwayat harian di SQLite agar tampilan 7/30 hari tetap tersedia saat retensi sistem terbatas.
7. Menyediakan rencana uji dan kriteria selesai sebelum build/rilis.

### 3.2 Non-tujuan (belum termasuk)

- Implementasi tab **Alat** (ping/DNS/WiFi/SIM).
- Kuota, siklus tagihan, notifikasi ambang, widget.
- Perubahan visual overlay kecepatan, kecuali perapian internal tanpa mengubah perilaku.
- Sinkronisasi cloud atau ekspor data.
- Dukungan per-SIM yang detail.
- Menambah izin baru di luar `PACKAGE_USAGE_STATS`, deklarasi `<queries>`, dan `READ_PHONE_STATE` yang mungkin diperlukan untuk query seluler Android 8–9 (saat ini belum dideklarasikan).

---

## 4. Keputusan Desain yang Diusulkan

Seluruh keputusan **D1–D12 sudah final** per 2026/10/31 08:30 WITA. Implementasi tetap belum dimulai sampai ada perintah **"kerjakan"**.

| No | Keputusan | Rekomendasi | Alternatif |
|----|-----------|-------------|------------|
| D1 | Tab Pemakaian vs Aplikasi | **Gabung** menjadi satu tab **Pemakaian**; tab **Alat** tetap terpisah | Pertahankan dua tab, tetapi satu filter global |
| D2 | Periode | **Hari ini / 7 hari terakhir / 30 hari terakhir** (rolling, termasuk hari ini) | Kalender: Hari ini / Minggu ini / Bulan ini |
| D3 | Filter jaringan | **Semua / WiFi / Seluler** | Tetap **Total / WiFi / Seluler** |
| D4 | Sumber data | **Satu sumber: `NetworkStatsManager`** untuk seluruh layar; wajib akses penggunaan | `TrafficStats` sebagai fallback total-only tanpa izin |
| D5 | Tombol Reset | **Hapus** dari layar pemakaian; data sistem adalah sumber kebenaran | Pertahankan sebagai "hitung dari sekarang" dengan baseline per filter |
| D6 | Metrik ringkasan | Total, rata-rata per jam (hari ini) atau per hari (7/30), puncak per bucket | Hanya total + grafik |
| D7 | Grafik | Adaptif: per jam untuk hari ini, per hari untuk 7/30 | Grafik 7 hari tetap |
| D8 | Donut | Top 7 aplikasi + **Lainnya**; legenda persen + ukuran; judul periode jelas | Tampilkan semua aplikasi (tidak disarankan) |
| D9 | Persentase daftar | Setiap baris aplikasi menampilkan persen terhadap total periode | Hanya di donut |
| D10 | Struktur kode | Pisahkan controller usage/overlay dan layout per halaman | Biarkan monolitik (tidak disarankan) |
| D11 | Riwayat persisten | **SQLite riwayat** dibuat sejak fase data; `NetworkStatsManager` tetap sumber utama untuk rentang yang tersedia | Data sistem on-demand tanpa DB (tidak dipakai) |
| D12 | Tampilan riwayat | **Daftar harian di tab Pemakaian**; tap tanggal membuka rincian hari itu | Halaman riwayat penuh; layar terpisah seperti Data Monitor |

### 4.1 Sumber data (D4 — final)

Pakai `NetworkStatsManager` untuk semuanya:

- Satu query detail untuk periode + jenis jaringan.
- Ringkasan total dihitung dari hasil query yang sama.
- Daftar aplikasi dihitung dari bucket yang sama.
- Donut dihitung dari total yang sama.

Dengan cara ini, `total ringkasan = jumlah baris aplikasi` (setelah pembulatan format), sehingga tidak ada dua angka yang saling bertentangan.

Konsekuensi:

- Jika akses penggunaan belum diberikan, tab Pemakaian menampilkan state izin, bukan angka total dari sumber lain.
- Fallback `TrafficStats` **tidak dipakai** pada rekonstruksi ini; jangan menampilkan total dari sumber berbeda di layar yang sama.

### 4.2 Periode (D2 — final)

Gunakan periode rolling agar batas waktu jelas dan tidak bergantung awal minggu/bulan:

- **Hari ini:** 00:00 waktu lokal sampai sekarang.
- **7 hari terakhir:** hari ini + 6 hari sebelumnya.
- **30 hari terakhir:** hari ini + 29 hari sebelumnya.

Selalu tampilkan label rentang aktual, misalnya `25 Okt – 31 Okt` atau `7 hari terakhir`.

Alternatif kalender (Minggu ini/Bulan ini) **tidak dipakai** pada rekonstruksi ini.

### 4.3 Metrik ringkasan (D6 — final)

- **Total:** RX + TX untuk periode/filter terpilih.
- **Rata-rata:**
  - Hari ini: total / jam berjalan (minimal 1 jam) → label `Rata-rata/jam`.
  - 7/30 hari: total / jumlah hari periode → label `Rata-rata/hari`.
- **Puncak:** bucket dengan total terbesar.
  - Hari ini: puncak per jam, label jam.
  - 7/30 hari: puncak per hari, label tanggal.
- **Puncak kecepatan dihapus dari layar usage.** Kecepatan live tetap ada di overlay dan pengaturan Overlay.

### 4.4 Donut (D8 — final)

- 7 aplikasi terbesar + `Lainnya` (jika ada).
- Legenda: titik warna, ikon aplikasi, nama, persentase, dan ukuran.
- Tengah donut: total periode/filter.
- Judul: `Komposisi · 7 hari terakhir · Semua` (periode dan filter selalu terlihat).
- Jika satu aplikasi mendominasi, donut tetap terbaca; jangan memaksa label persen di dalam potongan.
- Persentase dihitung dari total yang sama dengan ringkasan.

### 4.5 Reset (D5 — final)

Keputusan: hapus tombol **Reset Hari Ini**.

Alasan:

- Data `NetworkStatsManager` adalah catatan sistem, bukan counter milik aplikasi.
- Reset lokal membuat ringkasan, donut, dan daftar tidak lagi merujuk ke periode yang sama.
- Alternatif "hitung dari sekarang" **tidak dipakai** karena menambah state dan kasus tepi (reboot, ganti hari, ganti filter).

### 4.6 Riwayat SQLite (D11 — final)

SQLite dipakai sejak fase data agar tampilan 7/30 hari tidak hilang saat retensi `NetworkStatsManager` terbatas. `NetworkStatsManager` tetap sumber utama; SQLite adalah riwayat/cache yang mengisi tanggal yang tidak lagi disediakan sistem.

Rancangan awal:

- Tabel `usage_daily`: `day` (yyyy-MM-dd lokal), `network` (`WIFI`/`MOBILE`), `uid`, `package`, `rx`, `tx`, `updated_at`. Kunci unik `(day, network, uid)`.
- Tabel `usage_meta`: versi skema, waktu sinkron terakhir, dan status sinkron.
- Sinkronisasi: saat panel Pemakaian dibuka atau minimal sekali sehari, query `NetworkStatsManager` untuk 30 hari terakhir, agregasi per hari/UID/jaringan, lalu upsert. Untuk hari berjalan, baris hari ini diperbarui, bukan ditambah.
- Grafik Hari ini tetap memakai bucket per jam dari query live; grafik 7/30 hari memakai agregat harian.
- Prioritas data: live `NetworkStatsManager` lebih tinggi daripada SQLite. Jika tanggal tersedia di kedua sumber, pakai live dan perbarui DB. Jangan menjumlahkan keduanya untuk tanggal/UID yang sama.
- Retensi default: 90 hari; baris lebih lama dihapus saat sinkronisasi. Nilai ini bisa diubah tanpa mengubah skema.
- Tulis DB di thread background, memakai transaksi batch dan WAL. Jangan menulis per baris di main thread.
- Sertakan versi skema dan jalur migrasi; DB rusak/versi tidak dikenal harus bisa dibuat ulang tanpa crash.
- Tandai sumber snapshot: `LIVE`, `CACHE`, atau `MIXED`. Jika ada bagian yang berasal dari cache, tampilkan catatan kecil, bukan diam-diam mencampur.

Konsekuensi:

- Ukuran DB dibatasi retensi 90 hari dan agregasi harian, bukan per jam per aplikasi.
- Data hari ini yang belum final bisa berubah; update memakai replace, bukan penambahan.
- Jika DB dihapus (clear data), riwayat lama di luar retensi sistem tidak bisa dipulihkan. Itu diterima.

### 4.7 Tampilan riwayat harian (D12 — final)

Mengikuti pola Data Monitor, tetapi tetap di dalam tab Pemakaian:

- Section **Riwayat harian** berada di bawah grafik tren.
- Setiap baris berisi tanggal, total, WiFi, seluler, dan persen terhadap total periode. Baris terbaru di atas.
- Untuk periode 7/30 hari, section menampilkan daftar hari sesuai periode. Default tampil 7 baris terbaru; tombol **Lihat semua** memperluas daftar inline, bukan membuka tab baru.
- Untuk periode Hari ini, section disembunyikan karena hanya ada satu hari.
- Tap satu baris membuka **bottom sheet rincian hari**: judul tanggal, ringkasan total/WiFi/seluler, donut top 7 + Lainnya untuk hari itu, lalu daftar aplikasi hari itu.
- Membuka rincian hari tidak mengubah filter periode global. Tutup sheet dan kembali ke periode yang sama.
- Jika data satu hari tidak tersedia, baris ditandai "tidak ada data"; jangan tampilkan 0 seolah terukur.
- Jika baris berasal dari cache, beri penanda kecil yang konsisten dengan status `CACHE`/`MIXED`.
- Kolom **Quota** milik Data Monitor tidak ditiru karena kuota bukan scope; persen dihitung dari total periode.

---

## 5. Arsitektur Informasi Target

### 5.1 Struktur panel

```
Panel Network
├─ Footer: [ Monitor ] [ Overlay ]
├─ Monitor
│  ├─ Header: [ Pemakaian ] [ Alat ]
│  └─ Pemakaian
│     ├─ Filter periode: [ Hari ini ] [ 7 hari ] [ 30 hari ]
│     ├─ Filter jaringan: [ Semua ] [ WiFi ] [ Seluler ]
│     ├─ Ringkasan
│     ├─ Grafik tren
│     ├─ Riwayat harian (7/30 hari)
│     ├─ Donut komposisi + legenda
│     └─ Daftar aplikasi
└─ Overlay
   └─ Pengaturan kecepatan (perilaku existing dipertahankan)
```

Tab **Aplikasi** dihapus. Tab **Alat** tetap ada sebagai placeholder sampai ada rencana terpisah.

### 5.2 Wireframe tab Pemakaian

```
┌────────────────────────────────────────┐
│ [ Hari ini ] [ 7 hari ] [ 30 hari ]    │
│ [ Semua ] [ WiFi ] [ Seluler ]         │
├────────────────────────────────────────┤
│ Total              8,42 GB             │
│ ↓ 7,10 GB   ↑ 1,32 GB                  │
│ Rata-rata/hari 1,20 GB  Puncak 28 Okt  │
├────────────────────────────────────────┤
│ Tren pemakaian · 7 hari terakhir       │
│ ▁ ▃ ▅ ▂ ▇ ▄ ▆ ▃ ▅ ▂ ▇ ▄ ▆ ▃ ▅ ▂ ▇ ▄  │
│ 25  26  27  28  29  30  31             │
├────────────────────────────────────────┤
│ Riwayat harian             Lihat semua │
│ 31 Okt  1,42 GB   W 0,9 G  M 0,5 G 17% │
│ 30 Okt  1,10 GB   W 0,8 G  M 0,3 G 13% │
│ 29 Okt  1,38 GB   W 1,0 G  M 0,4 G 16% │
├────────────────────────────────────────┤
│ Komposisi · 7 hari terakhir · Semua    │
│      ◕            YouTube   45% 3,8 GB │
│    donut          Chrome    20% 1,7 GB │
│                   WhatsApp  15% 1,3 GB │
│                   Lainnya   20% 1,6 GB │
├────────────────────────────────────────┤
│ Aplikasi (12)                          │
│ [icon] YouTube   45%   3,8 GB  ↓ ↑     │
│ [icon] Chrome    20%   1,7 GB  ↓ ↑     │
│ ...                                    │
└────────────────────────────────────────┘
```

Catatan wireframe:

- Filter berada di atas dan tetap terlihat saat daftar digulir.
- Ringkasan, grafik, riwayat, donut, dan daftar adalah satu halaman; tidak ada tab Aplikasi terpisah.
- Riwayat harian muncul untuk periode 7/30 hari; default 7 baris terbaru, tombol **Lihat semua** memperluas daftar inline.
- Tap baris riwayat membuka bottom sheet rincian hari tanpa mengubah filter periode global.
- Donut dan legenda berada dalam satu kartu; jika lebar layar sempit, legenda boleh pindah ke bawah donut.
- Daftar aplikasi memakai RecyclerView agar tetap ringan.

### 5.3 Perilaku filter

- Mengubah periode atau jenis jaringan memicu satu permintaan data baru.
- Selama permintaan berjalan, tampilkan indikator loading; jangan tampilkan campuran data lama dan baru.
- Permintaan lama yang belum selesai dibatalkan/diabaikan memakai request id.
- Pilihan filter dipertahankan selama panel hidup. **Tidak disimpan antar sesi**: saat panel dibuka kembali, default adalah Hari ini + Semua.
- Jika data untuk filter tertentu tidak tersedia, tampilkan state kosong yang menjelaskan sebabnya, bukan angka 0 yang menyesatkan.

### 5.4 State yang wajib ada

1. **Izin belum ada** — penjelasan + tombol menuju pengaturan akses penggunaan.
2. **Loading** — indikator, tanpa data lama.
3. **Kosong** — belum ada pemakaian pada periode/filter.
4. **Parsial** — misalnya data seluler tidak terbaca; tampilkan catatan.
5. **Error** — pesan singkat + tombol coba lagi.
6. **Normal** — ringkasan, grafik, riwayat, donut, daftar.

---

## 6. Model Data dan Sumber

### 6.1 Tipe data

- `UsagePeriod`: `TODAY`, `DAYS_7`, `DAYS_30`.
- `UsageNetwork`: `ALL`, `WIFI`, `MOBILE`.
- `UsageQuery`: periode, jaringan, zona waktu, waktu sekarang.
- `UsageBucket`: awal, akhir, RX, TX.
- `UsageDay`: tanggal, RX/TX total, RX/TX per jaringan, persen terhadap periode, sumber (`LIVE`/`CACHE`), status data tersedia.
- `UsageApp`: UID, package name, label, ikon (di layer UI), RX, TX, persen.
- `UsageSnapshot`: query, rentang, total RX/TX, rata-rata, puncak, daftar bucket, daftar hari, daftar aplikasi, flag parsial, sumber data.

### 6.2 Sumber utama

`NetworkUsageSource` (baru atau hasil generalisasi `NetworkStatsSource`):

- `query(Context, UsageQuery)` → `UsageSnapshot`.
- Query detail per jenis jaringan, gabungkan bucket per UID dan per waktu.
- Ringkasan dihitung dari hasil yang sama.
- Urutkan aplikasi menurun berdasarkan total.
- Tutup `NetworkStats` di `finally`.
- API 29+: `subscriberId = null` untuk seluler.
- API 26–28: butuh `READ_PHONE_STATE` + `TelephonyManager.getSubscriberId()`; jika tidak ada, tandai `mobileUnavailable`.
- Jangan memakai `QUERY_ALL_PACKAGES`. Gunakan `<queries>` launcher + fallback `getNameForUid()`.

### 6.3 Konsistensi angka

Aturan:

- Ringkasan total = jumlah RX+TX seluruh bucket yang dikembalikan untuk periode/filter.
- Daftar aplikasi = agregasi bucket yang sama per UID.
- Donut = total per aplikasi dari daftar yang sama.
- Jangan mencampur `querySummaryForDevice` dengan `queryDetails` untuk angka yang ditampilkan berdampingan, kecuali selisihnya dijelaskan.

### 6.4 Retensi dan cache

- Data sistem dapat memiliki retensi terbatas dan berbeda per ROM. SQLite riwayat mengisi tanggal yang tidak lagi tersedia; jika cache juga kosong, tampilkan catatan data tidak lengkap.
- Cache hasil query di memori selama panel terbuka, misalnya 15–30 detik per kombinasi periode/jaringan.
- Refresh otomatis saat panel dibuka, filter berubah, atau tab Pemakaian dipilih. Hindari polling saat panel tersembunyi.
- Jangan menyimpan snapshot per aplikasi di `SharedPreferences`.
- SQLite menyimpan agregat harian per UID/jaringan sesuai §4.6, bukan snapshot per jam. Sinkronisasi dan retensi dijalankan di background.

### 6.5 Threading

- Query di executor single-thread.
- Request id untuk membuang hasil lama.
- Hentikan executor saat panel di-cleanup.
- Jangan melakukan I/O atau query statistik di main thread.

---

## 7. Struktur Kode Target

### 7.1 Controller

| File | Peran |
|------|-------|
| `app/.../ui/NetworkPanelController.java` | Shell panel: footer Monitor/Overlay, lifecycle, delegasi ke controller halaman |
| `app/.../ui/NetworkUsageController.java` (baru) | Seluruh tab Pemakaian: filter, query, ringkasan, grafik, riwayat, donut, daftar |
| `app/.../ui/NetworkOverlayController.java` (baru) | Pindahkan logika pengaturan Overlay dari controller lama |
| `app/.../ui/NetworkToolsController.java` (baru, opsional) | Placeholder tab Alat; implementasi alat di rencana terpisah |

Target: `NetworkPanelController` kembali fokus pada shell dan navigasi, bukan seluruh detail.

### 7.2 Data dan model

| File | Peran |
|------|-------|
| `features/network/.../usage/NetworkUsageSource.java` (baru/generalisasi) | Query periode/jaringan, agregasi bucket |
| `features/network/.../usage/UsageModels.java` (baru) | `UsagePeriod`, `UsageNetwork`, `UsageQuery`, `UsageSnapshot`, `UsageBucket`, `UsageApp` |
| `features/network/.../usage/UsageHistoryStore.java` (baru) | SQLite riwayat harian: skema, upsert, sinkronisasi, retensi, migrasi |
| `features/network/.../usage/UsageCache.java` (opsional) | Cache memori per kombinasi filter |

`NetworkStatsSource.java` yang sekarang bisa dijadikan cikal bakal, lalu diganti nama agar tidak menyiratkan "hari ini" saja.

### 7.3 UI

| File | Peran |
|------|-------|
| `app/.../ui/UsageAppAdapter.java` (ganti nama `AppUsageAdapter`) | Baris aplikasi + persentase |
| `app/.../ui/UsageHistoryAdapter.java` (baru) | Baris daftar hari: tanggal, total, WiFi, seluler, persen |
| `app/.../ui/widget/UsageDonutView.java` (ganti nama `AppUsageDonutView`) | Donut generik + teks tengah |
| `app/.../ui/widget/UsageChartView.java` (baru, ganti `UsageBarsView`) | Grafik batang adaptif per jam/hari |
| `app/.../res/layout/panel_network.xml` | Menjadi shell tipis + include halaman |
| `app/.../res/layout/panel_network_usage.xml` (baru) | Isi tab Pemakaian, termasuk section Riwayat harian |
| `app/.../res/layout/panel_network_overlay.xml` (baru) | Isi halaman Overlay |
| `app/.../res/layout/panel_network_tools.xml` (baru) | Placeholder tab Alat |
| `app/.../res/layout/item_usage_app.xml` (ganti nama `item_app_usage.xml`) | Baris aplikasi |
| `app/.../res/layout/item_usage_day.xml` (baru) | Baris riwayat harian |
| `app/.../res/layout/item_usage_legend.xml` (ganti nama `item_app_usage_legend.xml`) | Baris legenda donut |
| `app/.../res/layout/sheet_usage_day.xml` (baru) | Bottom sheet rincian satu hari + donut + daftar aplikasi |

Nama file final boleh disesuaikan saat implementasi, tetapi pemisahan tanggung jawab tidak boleh dibatalkan tanpa persetujuan.

### 7.4 Yang dipertahankan

- `NetworkConfig`, `NetworkModule`, overlay kecepatan, dan pengaturan posisi tidak diubah perilakunya.
- `DataUsageTracker` dipertahankan sementara jika masih dipakai `MainActivity`/`NetworkModule`; karena D4 sudah final (satu sumber), pensiunkan setelah tidak ada pemakai lain.
- `PermissionHelper.hasUsageAccess()` dan `requestUsageAccess()` dipertahankan.
- `<queries>` launcher dan `PACKAGE_USAGE_STATS` dipertahankan.

### 7.5 Yang dihapus setelah migrasi berhasil

- Header tab **Aplikasi** dan seluruh ID `networkApps*` yang hanya dipakai untuk tab terpisah.
- `AppUsageAdapter` lama setelah digantikan `UsageAppAdapter`.
- `UsageBarsView` lama setelah digantikan `UsageChartView`.
- Jalur tampilan lama di `NetworkPanelController` setelah tab Pemakaian baru stabil.
- Tombol dan logika `Reset Hari Ini` setelah migrasi berhasil (D5 final).

---

## 8. Rencana Tahapan

Setiap fase berakhir dengan laporan singkat dan persetujuan sebelum fase berikutnya. Tidak ada lompat fase.

### Fase 0 — Pembekuan dan inventaris

**Tujuan:** memastikan titik awal jelas.

- Catat status git, file yang berubah, dan versi berjalan.
- Inventaris semua ID/layout/controller yang terlibat.
- Identifikasi pemakaian `DataUsageTracker` di luar panel.
- Tidak mengubah kode.

**Keluaran:** daftar baseline dan risiko.

### Fase 1 — Persetujuan desain

**Tujuan:** mengunci keputusan yang tersisa.

- Seluruh keputusan D1–D12 sudah final.
- Wireframe dan label teks sudah difinalkan, termasuk riwayat harian.
- Tidak mengubah kode.

**Keluaran:** dokumen ini diperbarui dengan keputusan final.

### Fase 2 — Lapisan data

**Tujuan:** satu sumber data live yang bisa diuji, dengan riwayat lokal.

- Buat model `UsagePeriod`/`UsageNetwork`/`UsageSnapshot`.
- Generalisasi `NetworkStatsSource` menjadi `NetworkUsageSource`.
- Implementasi query periode 1/7/30 hari dan filter jaringan.
- Implementasi agregasi bucket per jam/hari, total, rata-rata, puncak.
- Buat `UsageHistoryStore` SQLite: skema `usage_daily`/`usage_meta`, upsert, retensi 90 hari, dan migrasi versi.
- Sinkronkan hasil live ke SQLite per hari/UID/jaringan; jangan menambah dua kali untuk kunci yang sama.
- Terapkan prioritas live > cache dan tandai snapshot `LIVE`/`CACHE`/`MIXED`.
- Tangani izin, API 26–28, seluler tidak tersedia, dan error.
- Tambahkan cache memori dan request id.

**Keluaran:** data layer + riwayat lokal, tanpa perubahan UI.

### Fase 3 — Shell dan layout

**Tujuan:** memecah panel tanpa mengubah perilaku overlay.

- Pisahkan `panel_network.xml` menjadi shell + halaman usage/overlay/tools.
- Buat `NetworkUsageController` dan `NetworkOverlayController`.
- Pindahkan binding lama ke controller masing-masing.
- Hapus tab Aplikasi dari header Monitor; sisakan Pemakaian dan Alat.
- Pastikan footer Monitor/Overlay tetap bekerja.

**Keluaran:** panel bisa dibuka, halaman Overlay berfungsi seperti sebelumnya.

### Fase 4 — Ringkasan dan grafik

**Tujuan:** tampilan data utama.

- Filter periode dan jaringan.
- Kartu ringkasan dengan label metrik yang adaptif.
- Grafik batang per jam/hari.
- State loading/kosong/error/parsial.
- Hapus metrik puncak kecepatan dari layar usage.

**Keluaran:** ringkasan dan grafik mengikuti filter yang sama.

### Fase 5 — Riwayat, donut, dan daftar aplikasi

**Tujuan:** rincian per hari dan per aplikasi yang konsisten.

- Section Riwayat harian untuk periode 7/30 hari: tanggal, total, WiFi, seluler, persen.
- Default 7 baris terbaru; tombol **Lihat semua** memperluas daftar inline.
- Bottom sheet rincian hari: ringkasan, donut top 7 + Lainnya, dan daftar aplikasi hari itu.
- Donut top 7 + Lainnya, legenda persen + ukuran.
- Judul periode/filter pada donut.
- Daftar aplikasi dengan persen, ikon, nama, total, RX/TX.
- Sinkron dengan ringkasan, grafik, dan riwayat.
- Empty/partial state, termasuk hari tanpa data.

**Keluaran:** satu layar lengkap tanpa tab Aplikasi.

### Fase 6 — Perapian dan aksesibilitas

- Label konten untuk donut/legenda.
- Kontras warna di tema terang/gelap.
- Font scale dan layar sempit.
- Touch target filter minimal 48dp.
- Pastikan persentase tidak hanya bergantung pada warna.

### Fase 7 — Migrasi dan pembersihan

- Hapus kode/ID tab Aplikasi yang tidak terpakai.
- Hapus `AppUsageAdapter`, `UsageBarsView`, dan jalur usage lama setelah tidak ada referensi.
- Putuskan nasib `DataUsageTracker` sesuai D4.
- Perbarui `STRUKTUR.md`/`README.md`/`PANDUAN.md` jika perilaku berubah.

### Fase 8 — Verifikasi

- Build/lint hanya setelah user memberi perintah.
- Uji manual sesuai matriks di §10.
- Perbaiki temuan; ulangi sampai kriteria selesai terpenuhi.
- Baru kemudian update CHANGELOG sesuai aturan AGENTS.

---

## 9. Kriteria Selesai

Rekonstruksi dianggap selesai jika:

1. Hanya ada satu tab **Pemakaian** di Monitor; tidak ada tab Aplikasi terpisah.
2. Satu pemilih periode dan satu pemilih jaringan mengendalikan ringkasan, grafik, riwayat, donut, dan daftar.
3. Judul periode/rentang terlihat jelas di ringkasan, grafik, riwayat, dan donut.
4. Total ringkasan sama dengan jumlah aplikasi (setelah pembulatan format) untuk filter yang sama.
5. Donut menampilkan 7 aplikasi teratas + Lainnya, dengan persentase yang dihitung dari total yang sama.
6. Daftar aplikasi menampilkan persentase dan total yang konsisten dengan donut.
7. Tidak ada tombol/metrik reset atau puncak kecepatan yang membingungkan di layar usage.
8. State izin, loading, kosong, parsial, dan error tampil jelas.
9. Overlay kecepatan dan pengaturannya tidak mengalami regresi.
10. Tidak ada query statistik di main thread; tidak ada kebocoran executor.
11. Build/lint bersih (saat dijalankan atas perintah).
12. Dokumentasi dan CHANGELOG diperbarui sesuai aturan proyek.
13. Riwayat SQLite terisi, dipakai untuk periode 7/30 hari, tidak menghitung ganda, dan membersihkan data di luar retensi.
14. Sinkronisasi SQLite idempoten; upgrade skema dan clear data tidak menyebabkan crash.
15. Riwayat harian hanya muncul untuk periode 7/30 hari dan menampilkan tanggal, total, WiFi, seluler, serta persen.
16. Tap baris riwayat membuka rincian hari yang angkanya konsisten dengan baris tersebut, tanpa mengubah filter global.
17. Hari tanpa data ditandai "tidak ada data", bukan 0; tombol **Lihat semua** memperluas daftar inline.

---

## 10. Matriks Pengujian

| Skenario | Yang dicek |
|----------|------------|
| Android 8–9 | WiFi, seluler dengan/tanpa `READ_PHONE_STATE`, pesan parsial |
| Android 10–15 | Query seluler `subscriberId=null`, izin usage, data per aplikasi |
| Tanpa akses penggunaan | State izin, tombol pengaturan, tidak ada angka palsu |
| Izin diberikan saat panel terbuka | Data termuat setelah kembali dari pengaturan |
| Periode Hari ini / 7 / 30 | Rentang label, bucket grafik, rata-rata, puncak |
| Filter Semua / WiFi / Seluler | Semua komponen berubah serempak; total = jumlah aplikasi |
| Perubahan filter cepat | Tidak ada hasil lama yang menimpa hasil baru |
| Riwayat 7/30 hari | Daftar hari sesuai periode, urut terbaru, total/persen benar |
| Riwayat Hari ini | Section riwayat disembunyikan |
| Lihat semua | Daftar 30 hari diperluas inline, tidak membuka tab baru |
| Tap baris riwayat | Bottom sheet rincian hari konsisten; filter global tidak berubah |
| Hari tanpa data | Baris ditandai "tidak ada data", bukan 0 |
| Riwayat dari cache | Penanda `CACHE`/`MIXED` terlihat dan konsisten |
| Aplikasi banyak | Daftar tetap ringan, donut tetap top 7 + Lainnya |
| Aplikasi tanpa ikon/label | Fallback nama paket/UID, tidak crash |
| Aplikasi di-uninstall | Tidak muncul atau ditandai dengan benar |
| VPN/tethering | Angka wajar; catatan jika atribusi tidak jelas |
| Ganti hari saat panel terbuka | Rentang dan data ikut berganti |
| Reboot | Tidak ada crash; data sistem dimuat ulang |
| Rotasi/layar sempit | Filter, donut, legenda, daftar tetap terbaca |
| Font scale besar | Tidak ada teks terpotong parah |
| Tema terang/gelap | Kontras teks, donut, legenda |
| Tutup/buka panel | Filter kembali ke default Hari ini + Semua (tidak disimpan) |
| Buka Overlay setelah Monitor | Pengaturan overlay tetap dan tidak ter-reset |
| Sinkronisasi SQLite berulang | Buka panel berkali-kali tidak membuat baris duplikat; nilai hari ini diperbarui |
| Data sistem terbatas | Periode 30 hari tetap terisi dari SQLite; sumber `CACHE`/`MIXED` ditandai |
| Live dan cache tumpang tindih | Tidak ada penjumlahan ganda untuk hari/UID/jaringan yang sama |
| Retensi SQLite | Baris lebih dari 90 hari terhapus saat sinkronisasi |
| Upgrade skema SQLite | DB versi lama dimigrasi atau dibuat ulang tanpa crash |
| Clear data aplikasi | DB dibuat ulang; state kosong/izin tampil benar |

---

## 11. Risiko dan Mitigasi

| Risiko | Dampak | Mitigasi |
|--------|--------|----------|
| Data `NetworkStatsManager` tidak lengkap di sebagian ROM | Angka 30 hari parsial | Tandai `partial`, jelaskan; jangan tampilkan 0 seolah lengkap |
| Retensi sistem berbeda-beda | Riwayat 30 hari hilang | SQLite riwayat sejak fase data; tandai bagian yang berasal dari cache |
| Query 30 hari berat | UI lambat | Executor, cache, request id, agregasi sekali jalan |
| VPN/tethering | Atribusi per aplikasi tidak akurat | Catatan; jangan klaim absolut |
| Package visibility | Nama/ikon tidak muncul | `<queries>` launcher + fallback nama paket |
| Seluler Android 8–9 | Data seluler tidak tersedia | State parsial + penjelasan izin |
| Dua sumber data | Angka tidak cocok | Live `NetworkStatsManager` utama; SQLite hanya pengisi; prioritas live > cache (D4/D11) |
| Reset lokal | Ringkasan dan daftar tidak sinkron | Hapus reset (D5) |
| SQLite dobel hitung | Total membengkak | Kunci unik hari/UID/jaringan + upsert replace, bukan insert |
| SQLite tumbuh besar | Penyimpanan boros | Agregat harian + retensi 90 hari |
| Migrasi/kerusakan SQLite | Crash saat buka panel | Versi skema, migrasi teruji, fallback buat ulang DB |
| Sinkronisasi bersamaan | Data tidak konsisten | Transaksi batch + single-thread executor |
| Riwayat 30 hari panjang | Halaman terasa berat | Default 7 baris + perluasan inline; RecyclerView; agregat dari DB |
| Rincian hari beda angka | User kehilangan kepercayaan | Rincian memakai agregat hari yang sama; uji konsistensi baris vs sheet |
| Bottom sheet menutupi filter | Navigasi membingungkan | Sheet tidak mengubah filter; tutup kembali ke posisi semula |
| Refactor besar | Regresi overlay | Pisahkan controller; uji overlay setiap fase |
| Monolit | Sulit dirawat | Pecah layout/controller sebelum menambah fitur |

---

## 12. Keputusan Final

**Final per 2026/10/31 08:30 WITA. Tidak ada pertanyaan desain yang tersisa.**

1. Tab **Pemakaian** dan **Aplikasi** digabung menjadi satu tab Pemakaian; tab Alat tetap terpisah.
2. Periode memakai **rolling**: Hari ini / 7 hari terakhir / 30 hari terakhir.
3. Filter jaringan memakai **Semua / WiFi / Seluler**.
4. Sumber data live tunggal: **`NetworkStatsManager`**; tanpa fallback `TrafficStats`.
5. Tombol **Reset Hari Ini dihapus**.
6. Metrik ringkasan: **total + rata-rata adaptif + puncak bucket**; puncak kecepatan dihapus dari layar usage.
7. Grafik **adaptif**: per jam untuk Hari ini, per hari untuk 7/30 hari.
8. Donut: **top 7 + Lainnya**.
9. Persentase ditampilkan **di setiap baris daftar** aplikasi.
10. Controller usage/overlay dan layout **dipisah per halaman**.
11. **SQLite riwayat** dibuat sejak fase data, dengan `NetworkStatsManager` sebagai sumber live utama dan retensi awal 90 hari.
12. **Riwayat harian** tampil di bawah grafik pada tab Pemakaian untuk periode 7/30 hari; tap tanggal membuka bottom sheet rincian hari tanpa mengubah filter global.
13. Filter **tidak disimpan antar sesi**; default Hari ini + Semua.
14. Wireframe §5.2 dan label teks disetujui.

Langkah berikutnya: menunggu perintah **"kerjakan"** untuk memulai Fase 0.

---

## 13. Aturan Kerja untuk Eksekusi

- Jangan mengubah kode sebelum perintah **"kerjakan"**.
- Kerjakan satu fase per perintah; jangan lanjut tanpa persetujuan.
- Jangan mengubah desain/keputusan yang sudah disetujui tanpa bertanya.
- Jangan menambah fitur di luar dokumen ini.
- Build/lint/commit/tag/push hanya atas perintah eksplisit.
- Laporkan file yang berubah dan hasil self-check di akhir setiap fase.
- Jika ada temuan yang mengubah rencana, berhenti dan tulis usulan perubahan, bukan langsung mengeksekusi.
