# [4.97.1] 2026/11/14 20:50 WITA 361 ***RELEASE***
### 🔖 Deskripsi
> Memperbarui modul Network Usage agar riwayat penggunaan data dari aplikasi yang telah di-uninstall tetap tersimpan dengan label tambahan `(Uninstalled)`.

### 🔧 Optimasi & Penyesuaian
- **Riwayat aplikasi uninstalled** — Catatan penggunaan data untuk aplikasi yang sudah dihapus dari perangkat tetap dipertahankan dan diberi label nama aplikasi/paket disertai keterangan `(Uninstalled)`.

---

# [4.97.0] 2026/11/14 12:26 WITA 359 ***RELEASE***
> Pembaruan utama rilis ini berpusat pada pemantauan kestabilan tegangan, deteksi bypass charging, dan penyempurnaan overlay baterai, disertai perbaikan kualitas data serta grafik riwayat.

### ✨ Fitur Baru
- **Voltage Stability Monitor** — Kartu di Monitor > Info & Grafik yang menampilkan kondisi, voltage drop, recovery, variasi, jumlah sampel, dan estimasi resistansi efektif dengan pembaruan live ±1 detik.
- **Grafik tegangan berkondisi** — Riwayat tegangan diwarnai per kondisi (charging/discharging/idle) dengan rentang 5m/30m/1j/6j/24j.
- **Deteksi Bypass Charging** — Status bypass, indikasi sementara, dan batas cas tampil di overlay serta ring Monitor; akurasi penuh memakai node sistem dan Shizuku.
- **Ikon Status Pengisian** — Ikon dinamis untuk charging, fast charging, USB, wireless, penuh, bypass, batas cas, jeda, dan discharge, mengikuti warna label dan ukuran teks.
- **Ukuran label overlay** — Ukuran satuan dan label dapat diatur terpisah dari ukuran nilai, serta ikut tersimpan di preset.

### ♻️ Perubahan Fitur
- **Deteksi Bypass Charging** — Status mengikuti Smart Panel bila tersedia; heuristik sensor, node sistem, dan Shizuku menjadi cadangan.
- **Urutan item baterai** — Item status pengisian (`Cas`) dapat diurutkan dan diaktifkan/nonaktifkan; urutan lama dimigrasikan dengan `Cas` di depan.

### 🐞 Bug Fixes
- **Validasi data baterai** — Sampel rusak tidak disimpan; jeda, lonjakan, dan counter tidak wajar dicatat sebagai anomali.
- **Estimasi dan riwayat sesi** — Sesi dengan jeda ≥60 detik, data tidak lengkap, counter reset, atau estimasi tidak wajar tidak lagi dipakai.
- **Grafik riwayat** — Lonjakan singkat tetap terlihat dan jeda data ditandai dengan benar pada data hasil downsampling.
- **Sumber data** — Pembacaan hanya memakai sensor baterai yang sesuai, bukan adaptor; nilai di luar rentang wajar dianggap tidak tersedia.
- **Nilai arus dan daya overlay** — Item mA/W tetap tampil dengan placeholder `--` tanpa menggeser susunan; nilai valid terakhir ditahan ±15 detik dan daya dapat dihitung dari tegangan × arus.
- **Penyimpanan pengaturan baterai** — Urutan, visibilitas, ukuran teks, ukuran label, dan toggle bypass tetap tersimpan setelah aplikasi dibuka ulang.

### 💡 Memo
- Deteksi bypass bersifat heuristik; konfirmasi dapat memerlukan ±45 detik dan akurasi penuh membutuhkan Shizuku.
- Estimasi resistansi efektif berasal dari perubahan arus dan tegangan yang memenuhi syarat; nilainya bukan resistansi internal baterai yang sesungguhnya.
- Batas cas memakai ambang 80%; kebijakan vendor dapat berbeda.
- Catatan anomali sampel disimpan untuk validasi dan belum memiliki tampilan atau pemangkasan otomatis.

---

# [4.96.0] 2026/11/06 08:31 WITA 349 ***RELEASE***
### 🔖 Deskripsi
> Memusatkan pemantauan penggunaan data Network dalam satu layar, sekaligus menambah pemantauan FPS game dan memperkaya pengaturan serta efek Battery Strip.

### ✨ Fitur Baru
- **FPS Game via Shizuku** — Sub-tab Game FPS memantau FPS melalui overlay, dengan mode Otomatis mengikuti aplikasi depan atau mode Manual untuk memilih game.
- **Pemakaian data terpadu** — Ringkasan, grafik, riwayat, komposisi, dan daftar aplikasi kini tersedia dalam satu sub-tab dengan pilihan periode.
- **Kontrol overlay Network** — Mengatur angka desimal, satuan KB/s atau bps dengan konversi otomatis, serta pemangkasan angka.
- **Animasi Battery Strip** — Menambahkan Edge Glow saat pengisian, Startup Fill Sweep saat overlay muncul, dan Drain Tick saat baterai berkurang.
- **Peringatan Battery Strip** — Menambahkan bunyi dan/atau getar saat baterai turun, dengan pilihan tiap 1% atau persentase kustom.

### 🚮 Fitur Dihapus
- **Tab Aplikasi dan Reset Hari Ini** — Fungsi aplikasi digabung ke sub-tab Pemakaian; reset dihapus karena rekap mengikuti periode pilihan.

### ♻️ Perubahan Fitur
- **Pengaturan Battery Strip** — Dipisah menjadi subtab Tampilan, Animasi, dan Suara.
- **Animasi Wave** — Kini diterapkan saat pengisian maupun pengosongan.

### 🔧 Optimasi & Penyesuaian
- **Panel FPS** — Game FPS dipindah ke kiri, sedangkan Display berganti nama menjadi Overlay dan dipindah ke kanan.

### 🐞 Bug Fixes
- **Tautan Tentang Aplikasi dan cek pembaruan mengikuti repo baru** — Tautan GitHub/Issue dan endpoint pemeriksaan pembaruan kini menunjuk `gaozhan906/FTxT`, menggantikan alamat repo lama yang tidak lagi tersedia.

### 💡 Memo
- Deskripsi changelog kini dibuat lebih ringkas agar mudah dibaca; detail teknis tidak lagi dijabarkan.
- Data penggunaan per aplikasi berasal dari sistem dan dapat terlambat beberapa menit; data seluler per aplikasi di Android 8–9 memerlukan izin telepon.

---

# [4.95.0] 2026/10/27 14:39 WITA 310 ***PUSH***
### 🔖 Deskripsi
> Memperluas pemantauan Network di Monitor dan memperbarui tampilan overlay kecepatan.

### ✨ Fitur Baru
- **Monitor Network** — Menambahkan header Pemakaian, Aplikasi, dan Alat. Pemakaian menampilkan total unduh/unggah hari ini, rata-rata per jam, kecepatan puncak, dan grafik 7 hari.

### ♻️ Perubahan Fitur
- **Overlay Network** — Kecepatan unduh dan unggah kini ditampilkan sebagai dua badge terpisah dengan color bar yang mengikuti tingkat kecepatan.

### 🐞 Bug Fixes
- Memperbaiki color bar yang tidak terlihat dan memastikan konfigurasi overlay tetap tersimpan setelah reboot.
- Grafik kini menandai periode tanpa data ketika terdapat jeda pengamatan yang panjang.

### 💡 Memo
- Data merupakan total perangkat, bukan per aplikasi atau jenis jaringan; hari tanpa observasi dapat kosong dan traffic sebelum reboot tidak dapat dipulihkan.

---

# [4.94.0] 2026/09/10 17:33 WITA 290 ***RELEASE***
### 🔖 Deskripsi
> Membangun fondasi Smart Panel, panel kontrol mengambang untuk mengatur overlay tanpa membuka aplikasi.

### ✨ Fitur Baru
- **Smart Panel** — Ikon mengambang dapat digeser dan ditempelkan ke sisi layar. Ketika diketuk, panel kontrol muncul untuk mengatur posisi dan pengaturan modul overlay.

---

# [4.93.0] 2026/09/10 03:24 WITA 284 ***RELEASE***
### 🔖 Deskripsi
> Menambahkan halaman Tentang Aplikasi dan sistem pemeriksaan pembaruan.

### ✨ Fitur Baru
- **Tentang Aplikasi** — Menampilkan identitas aplikasi, status Beta, versi, pengembang, tautan, dan lisensi.
- **Pembaruan GitHub Releases** — Mendukung pemeriksaan dan unduhan pembaruan secara manual maupun otomatis.

---

# [4.92.1] 2026/09/09 05:59 WITA 278 ***RELEASE***
### 🔖 Deskripsi
> Meningkatkan akurasi data dan kenyamanan pemantauan baterai.

### ♻️ Perubahan Fitur
- **Notifikasi Kustom** — Nonaktif secara default; interval judul dapat diatur setelah fitur diaktifkan.
- **Kapasitas desain** — Nilai dapat diperiksa dan diubah lewat kolom input dengan pilihan Simpan atau Batal.
- **Sesi singkat** — Sesi colok/lepas singkat tetap tercatat sebagai tidak valid, tetapi tidak disertakan dalam ringkasan dan grafik.

### 🔧 Optimasi & Penyesuaian
- Sesi terbaru kini ditampilkan di urutan paling atas.

### 🐞 Bug Fixes
- Memperbaiki posisi tracker grafik saat zoom, skala suhu di bawah 35°C, dan perhitungan mAh sesi berjalan.

---

# [4.92.0] 2026/09/07 18:11 WITA 269 ***RELEASE***
### 🔖 Deskripsi
> Mengunci pemilih ikon launcher dengan Fitur Developer dan memperbaiki estimasi kapasitas pengosongan.

### ♻️ Perubahan Fitur
- Pemilih ikon launcher hanya dapat digunakan saat Fitur Developer terbuka.

### 🐞 Bug Fixes
- Estimasi kapasitas pengosongan kini dihitung dari integrasi arus dan rekonstruksi sesi baterai.

---

# [4.91.0] 2026/09/06 08:36 WITA 265 ***RELEASE***
### 🔖 Deskripsi
> Menambahkan pengaturan Notifikasi Kustom, mengoptimalkan Monitor, dan memperbaiki deteksi ANR palsu.

### ✨ Fitur Baru
- **Notifikasi Kustom** — Judul informasi baterai dapat disembunyikan dan interval pembaruannya dapat diatur.

### 🔧 Optimasi & Penyesuaian
- Query grafik dan riwayat sesi dibatasi agar pemantauan lebih ringan; informasi ringan tetap diperbarui lebih sering.

### 🐞 Bug Fixes
- Log ANR palsu dikurangi dengan membatasi deteksi saat aplikasi berada di latar depan dan mengabaikan jeda detak yang terlalu lama.

---

# [4.90.0] 2026/09/06 06:54 WITA 263 ***RELEASE***
### 🔖 Deskripsi
> Menambahkan pilihan isi ikon notifikasi status bar, termasuk suhu, persentase, dan tanggal.

### ✨ Fitur Baru
- **Ikon status bar** — Memilih tampilan suhu, persentase, atau tanggal dua baris, beserta ukuran, format tanggal, dan bahasa.

### 🔧 Optimasi & Penyesuaian
- Notifikasi overlay hanya diperbarui ketika nilai berubah. Ukuran ikon launcher default disamakan dengan ikon alternatif.

---

# [4.89.0] 2026/09/05 12:12 WITA 244 ***RELEASE***
### 🔖 Deskripsi
> Memperluas pemantauan baterai dan menambahkan overlay bidikan Crosshair.

### ✨ Fitur Baru
- **Monitor baterai** — Menambahkan sesi real-time, riwayat pengosongan, grafik metrik, ringkasan, dan halaman detail grafik interaktif.
- **Crosshair** — Overlay bidikan game dengan pilihan gaya, warna, ukuran, opasitas, rotasi, posisi, dan preset.
- **Kesehatan baterai** — Menambahkan estimasi kapasitas, ringkasan kesehatan, serta informasi kapasitas pengisian dan pengosongan.
- **Dokumentasi dan alat** — Changelog lama dapat dibaca di aplikasi; tersedia ekstraksi database dan penampil dokumen yang lebih nyaman.
- **Pemantauan lebih andal** — Riwayat dan notifikasi diperbarui, operasi berat dipindahkan ke thread latar, serta log ANR disimpan otomatis.

### ♻️ Perubahan Fitur
- Navigasi modul, grafik, rentang waktu, pengaturan Developer, dan posisi default Battery Strip dirapikan.

### 🔧 Optimasi & Penyesuaian
- Mengurangi beban penulisan database dan grafik, serta menyempurnakan tampilan kartu dan data aktivitas.

### 🐞 Bug Fixes
- Memperbaiki label dan warna grafik, skala suhu, garis yang terputus, serta masalah Monitor yang crash atau macet.

### 💡 Memo
> CHANGELOG mencatat poin perubahan, bukan detail file.

---

# [4.88.0] 2026/08/23 21:56 WITA 185 ***PUSH***
### ✨ Fitur Baru
- **Monitor baterai** — Menambahkan pemantauan real-time, grafik riwayat, dan estimasi kapasitas serta kesehatan baterai.
- **Pencatatan otomatis** — Riwayat baterai disimpan di database SQLite melalui layanan latar; data lama dimigrasikan otomatis.

### 🔧 Optimasi & Penyesuaian
- Overlay dan tab Monitor kini menggunakan sumber pembacaan baterai yang sama.

---

# [4.87.0] 2026/08/21 20:28 WITA 184 ***RELEASE***
### ✨ Fitur Baru
- Menambahkan sakelar untuk menampilkan atau menyembunyikan panel Debugging dan Info Memori, serta proteksi password untuk Debugging.

### 🔧 Optimasi & Penyesuaian
- Merapikan ejaan Debugging, tombol Relock, dan urutan bagian di halaman Konfigurasi.

### ♻️ Perubahan Fitur
- Urutan item Memory Stats kini dapat diatur dengan drag-and-drop.

### 🐞 Bug Fixes
- Memperbaiki drag-and-drop sidebar, kedipan Battery Stats, penghentian overlay saat panel dimatikan, dan akses ke panel tersembunyi.

### 💡 Memo
> Seluruh entry log lama telah dipindahkan ke old-CHANGELOG.
