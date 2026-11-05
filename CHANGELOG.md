# [4.96.0] 2026/11/06 06:40 WITA 346 ***ONGOING***
### 🔖 Deskripsi
> Rilis ini memusatkan tab Monitor Network pada satu layar Pemakaian data yang lebih lengkap, disertai kontrol kecepatan overlay yang lebih kaya, animasi dan peringatan pada Battery Strip, serta pemantau FPS game berbasis Shizuku.

### ✨ Fitur Baru
- **FPS Game via Shizuku** — Sub-tab **Game FPS** di panel FPS Display memantau FPS game lewat overlay FPS Display. Mode **Otomatis** mengikuti aplikasi depan, sedangkan mode **Manual** memakai game pilihan (daftar dan pencarian hanya tampil di mode Manual). Deteksi memakai akses penggunaan dengan fallback Shizuku, mengecualikan aplikasi sistem dan FTxT, dan hanya memantau saat game di depan. FPS game memakai warna khusus dan ikon aplikasi, lalu kembali ke FPS aplikasi setelah game ditinggalkan. Pembacaan memakai SurfaceFlinger dengan fallback gfxinfo (dukungan OEM best-effort), dan panel menampilkan status prasyarat dengan arahan ke **Konfigurasi > Akses Izin**.
- **Pemakaian data terpadu di Monitor Network** — Sub-tab **Pemakaian** menggabungkan ringkasan, grafik, riwayat, komposisi aplikasi, dan daftar aplikasi dalam satu layar, dengan filter periode (Hari ini/7/30 hari) dan jaringan (Semua/WiFi/Seluler). Ringkasan menampilkan total, rata-rata, dan puncak; grafik per jam atau per hari; donut top 7 aplikasi + Lainnya; daftar aplikasi berisi ikon, total, RX/TX, dan persen. Data memadukan `NetworkStatsManager` dengan cache dan riwayat SQLite 90 hari, dan layar tetap menampilkan nilai nol saat data kosong. Tautan **Riwayat ›** membuka daftar hari beserta rinciannya tanpa mengubah filter global.
- **Kontrol kecepatan overlay Network** — Opsi **Desimal** (1–3 angka diatur di Developer), unit **KB/s** atau **bps** dengan kenaikan otomatis (MB/s atau Kbps/Mbps/Gbps), serta checkbox **Pangkas** untuk label pendek. Perataan teks Kiri/Tengah/Kanan dan kontrol Bold, Italic, Vertikal, Color Bar, Invert, serta Panjang Badge ditata ringkas; pilihan tersimpan.
- **Animasi Battery Strip** — **Edge Glow** berdenyut di ujung fill saat charging; **Startup Fill Sweep** menggerakkan fill dari 0 ke level baterai saat overlay muncul; **Drain Tick** memberi kilau di ujung fill setiap persentase turun saat discharge. Kecepatan dan intensitas dapat diatur.
- **Beep & Getar Pengosongan Battery Strip** — Bunyi dan/atau getar saat baterai turun, dengan mode **Tiap 1%** atau **Manual** (daftar persen kustom). Pitch, durasi, jumlah ulangan, volume, dan durasi getar dapat diatur; tersedia tombol **Cek Suara**. Default nonaktif dan hanya aktif saat discharge.

### 🚮 Fitur Dihapus
- **Tab Aplikasi dan tombol Reset Hari Ini dihapus** — Fungsi tab Aplikasi dilebur ke sub-tab Pemakaian; Reset Hari Ini dihilangkan karena rekap mengikuti periode yang dipilih.

### ♻️ Perubahan Fitur
- **Pengaturan Battery Strip dipisah menjadi subtab Tampilan, Animasi, dan Suara** — Tampilan memuat mode, posisi, warna, dan ukuran; Animasi memuat Transisi, Pengisian, Pengosongan, dan Baterai Kritis (opsi Wave dipindah ke Pengosongan); Suara memuat Beep & Getar. Header dan Kunci Posisi tetap di atas subtab.
- **Animasi Wave aktif di pengosongan dan pengisian** — Wave pengosongan berjalan berapa pun persentasenya selama baterai tidak mengisi, dan Wave pengisian juga diterapkan saat mengisi; perubahan kecepatan langsung berlaku pada animasi berjalan.

### 🔧 Optimasi & Penyesuaian
- **Sub-tab panel FPS ditukar dan diganti nama** — **Display** menjadi **Overlay** dan dipindah ke kanan, sedangkan **Game FPS** di kiri.

### 💡 Memo
- Angka per aplikasi berasal dari data sistem sehingga bisa terlambat beberapa menit; data seluler per aplikasi di Android 8–9 memerlukan izin telepon.

---

# [4.95.0] 2026/10/27 14:39 WITA 310 ***PUSH***
### 🔖 Deskripsi
> Memperluas panel Network dengan header tab di Monitor serta merombak overlay Network menjadi dua badge kecepatan dengan color bar.

### ✨ Fitur Baru
- **Header tab di Monitor panel Network** — Tab Monitor memiliki tiga header: Pemakaian, Aplikasi, dan Alat. Pemakaian memuat rekap usage, sedangkan Aplikasi dan Alat masih halaman "Sedang dikerjakan".
- **Data Usage di tab Monitor** — Menampilkan total unduh/unggah hari ini, rata-rata per jam, kecepatan puncak, dan grafik batang 7 hari, lengkap dengan Reset Hari Ini. Angka dihitung dari selisih counter `TrafficStats` sehingga pemakaian yang terlewat tetap tercatat dan aman terhadap reboot.

### ♻️ Perubahan Fitur
- **Overlay Network memakai dua badge dengan color bar** — Baris kecepatan tunggal diganti badge unduh dan unggah terpisah. Color bar setinggi badge mengikuti kecepatan (skala logaritmik, penuh di 10 MB/s) dan selalu aktif. Susunan, panjang badge, ukuran/gaya teks, warna, serta arah isian bar dapat diatur; posisi overlay tetap tersimpan per orientasi.

### 🐞 Bug Fixes
- **Color bar overlay Network tidak terlihat** — Color bar kini digambar lapisan induk di belakang teks badge, dan track latar dihapus.
- **Konfigurasi overlay dipertahankan setelah reboot** — Pengaturan overlay dimuat dari penyimpanan sebelum layanan overlay berjalan.
- **Periode tanpa data pada grafik tab Monitor terlihat jelas** — Garis diputus saat jeda di atas 5 menit dan ditandai label "Tanpa data".

### 💡 Memo
- Angka bersifat total perangkat (semua aplikasi), bukan per aplikasi atau jenis jaringan; hari tanpa observasi bisa bolong dan traffic sampai reboot tidak dapat dipulihkan.

---

# [4.94.0] 2026/09/10 17:33 WITA 290 ***RELEASE***
### 🔖 Deskripsi
> Membangun fondasi Smart Panel — panel kontrol mengambang untuk mengatur posisi dan pengaturan modul overlay tanpa membuka aplikasi.

### ✨ Fitur Baru
- **Smart Panel — kontrol overlay tanpa buka aplikasi** — Ikon bulat mengambang yang bisa digeser dan snap ke sisi layar; saat diketuk menampilkan panel kontrol di sisi yang tersedia tanpa menggeser ikon. Panel berisi pemilih modul, D-Pad dengan repeat dan Reset posisi, tombol Aktifkan/Nonaktifkan serta Kunci Posisi, dan kontrol spesifik per modul (Opacity/Size/Reset). Panel tertutup saat mengetuk di luar, ikon bisa disembunyikan dengan double-tap dan dimunculkan lewat tombol di notifikasi. Fitur diaktifkan lewat toggle **Smart Panel** di Konfigurasi, posisi ikon tersimpan, dan ikon hanya muncul saat overlay aktif. Mendukung 8 modul overlay; pada Floating Text status aktif mengikuti pengaturan utama dan kontrol posisi nonaktif saat Mode Segel.

---

# [4.93.0] 2026/09/10 03:24 WITA 284 ***RELEASE***
### 🔖 Deskripsi
> Menambahkan halaman "Tentang Aplikasi" beserta sistem pembaruan aplikasi.

### ✨ Fitur Baru
- **Halaman "Tentang Aplikasi"** — Halaman fullscreen dari popup ikon gear dan header sidebar, menampilkan identitas aplikasi, status Beta, versionName/versionCode, pengembang, tautan, dan lisensi.
- **Cek dan unduh pembaruan GitHub Releases** — Pemeriksaan manual maupun otomatis (toggle default nonaktif; jadwal setiap buka aplikasi hingga 7 hari). Dialog pembaruan menyediakan Lihat Informasi, Unduh (APK universal dengan progress), dan Nanti Saja, lalu membuka prompt instalasi Android. Versi dibandingkan berdasarkan semver.

---

# [4.92.1] 2026/09/09 05:59 WITA 278 ***RELEASE***
### 🔖 Deskripsi
> Rilis ini fokus pada akurasi data dan kenyamanan pemantauan baterai di tab Monitor, disertai penyesuaian perilaku dan pengaturan yang lebih praktis.

### ♻️ Perubahan Fitur
- **Notifikasi Kustom default nonaktif dengan interval judul 5 detik** — Saat belum diatur, notifikasi hanya menampilkan tombol aksi tanpa judul info baterai; setelah diaktifkan interval judul otomatis 5 detik dan tetap bisa diubah.
- **Kapasitas desain diatur lewat kolom input dengan dialog Simpan/Batal** — Nilai awal diisi otomatis dari deteksi perangkat (dinormalisasi ke mAh) sehingga skor kesehatan bisa dihitung; nilai bisa diubah/dikosongkan dan hasil langsung tampil tanpa menunggu cache.
- **Sesi colok/lepas singkat direkam sebagai "Tidak Valid"** — Sesi singkat tetap tercatat di Riwayat Sesi dengan label "Tidak Valid" dan keterangan, tetapi tidak dihitung dalam ringkasan, grafik, maupun estimasi kapasitas.

### 🔧 Optimasi & Penyesuaian
- **Urutan daftar sesi dibalik** — Sesi terbaru tampil di paling atas agar mudah dilihat tanpa menggulir.

### 🐞 Bug Fixes
- **Tracker grafik menempel tepat di posisi ketukan saat zoom** — Crosshair tidak lagi meleset karena pergeseran jendela zoom dihitung ganda.
- **Garis grafik Suhu ikut turun di bawah 35°C** — Batas bawah skala melebar mengikuti suhu terendah, sementara rentang normal 35–40°C tetap dipertahankan.
- **mAh sesi berjalan tidak lagi selalu 0** — Interval antar-sampel hingga 1 menit kini diterima sehingga nilai mAh terhitung benar.

---

# [4.92.0] 2026/09/07 18:11 WITA 269 ***RELEASE***
### 🔖 Deskripsi
> Mengunci pemilih ikon launcher dengan Fitur Developer serta memperbaiki estimasi kapasitas pengosongan pada Kesehatan Baterai.

### ♻️ Perubahan Fitur
- **Pemilih ikon launcher ikut terkunci Fitur Developer** — Baris pemilih ikon hanya dapat dibuka saat Fitur Developer terbuka; saat terkunci pemilih ikon di-disable.

### 🐞 Bug Fixes
- **Estimasi kapasitas pengosongan kini menghasilkan nilai** — Nilai dihitung dari integral arus pengosongan memakai nilai mutlak, dan rekonstruksi sesi dari database diperluas hingga interval 1 menit.

---

# [4.91.0] 2026/09/06 08:36 WITA 265 ***RELEASE***
### 🔖 Deskripsi
> Menambahkan pengaturan Notifikasi Kustom dan mengoptimalkan Monitor, serta memperbaiki deteksi ANR palsu.

### ✨ Fitur Baru
- **Notifikasi kustom bisa dimatikan & interval judul bisa diatur** — Section "Notifikasi Kustom" di Konfigurasi dapat mematikan judul notifikasi sehingga hanya tombol aksi yang tampil. Interval pembaruan tersedia pada 1/3/5/10 detik (default 3 detik) dan langsung diterapkan tanpa restart.

### 🔧 Optimasi & Penyesuaian
- **Query grafik tab Monitor dibatasi** — Query database grafik dan riwayat sesi dibatasi setiap 5 detik, sedangkan informasi ringan tetap diperbarui setiap detik; cache warna gradien grafik Suhu dipakai bersama semua chart.

### 🐞 Bug Fixes
- **Log ANR palsu tidak lagi membanjiri Documents/FTxT/Log_ANR** — AnrWatcher hanya mendeteksi saat aplikasi di latar depan dan mengabaikan selang detak di atas 60 detik.

---

# [4.90.0] 2026/09/06 06:54 WITA 263 ***RELEASE***
### 🔖 Deskripsi
> Fitur utama rilis ini adalah isi ikon notifikasi status bar yang bisa diatur — suhu, persen, atau tanggal dua baris — lengkap dengan ukuran, format, dan bahasa nama hari/bulan. Pembaruan notifikasi kini hanya dikirim saat nilainya berubah.

### ✨ Fitur Baru
- **Isi ikon status bar bisa diatur** — Section "Status Bar" di Konfigurasi memilih isi ikon: Suhu, Persen (ring lingkaran dengan nilai di tengah), atau Tanggal dua baris. Ukuran isi, ketebalan ring, dan ukuran angka diatur lewat slider terpisah per mode, tersimpan otomatis, dengan pratinjau live.
- **Format & bahasa tanggal ikon status bar bisa dipilih** — Mode Tanggal menyediakan bahasa Indonesia/Inggris dan 8 kombinasi format dua baris, dengan ukuran baris tanggal dan hari yang bisa diatur terpisah.
- **Tata letak dua baris mode Tanggal rapi & seimbang** — Blok tanggal + hari dipusatkan di bitmap sehingga perbesaran simetris dan jarak antar baris konsisten.

### 🔧 Optimasi & Penyesuaian
- **Notifikasi overlay hanya di-refresh saat nilainya berubah** — Ikon dan judul notifikasi hanya diperbarui saat nilai yang tampil benar-benar berbeda, menghentikan pembaruan sia-sia tiap detik.
- **Ikon launcher rilis disamakan ukurannya dengan ikon alternatif** — Ikon default dibuat ulang 1024×1024 dengan area aman yang sama sehingga proporsi dan ketajamannya pas.

---

# [4.89.0] 2026/09/05 12:12 WITA 244 ***RELEASE***
### 🔖 Deskripsi
> Pembaruan utama rilis ini berpusat pada pemantauan baterai dan overlay bidikan. Battery Info menjadi pemantau baterai lengkap yang berjalan otomatis, dan Crosshair menghadirkan modul bidikan game.

### ✨ Fitur Baru
- **Tab Monitor disusun ulang jadi tiga sub-tab** — Info & Grafik, Sesi Berjalan, serta Kondisi & Riwayat; bisa digeser atau diketuk, tiap sub-tab digulir independen dan disegarkan otomatis.
- **Sesi Berjalan real-time** — Menampilkan sesi aktif dengan jenis, waktu mulai, durasi, level awal→sekarang, arus, mAh, kecepatan, sisa waktu, daya, tegangan, suhu, colokan, dan siklus; berganti mengikuti status colok/cabut Android.
- **Riwayat sesi pengosongan** — Mencatat sesi pengosongan (waktu, level awal-akhir, mAh, efisiensi, layar mati dominan, suhu) dengan minimal penurunan 1% dan durasi 1 menit.
- **Grafik batang, ringkasan & detail riwayat sesi** — Grafik batang harian/mingguan/bulanan dengan jendela 12 unit bergulir, label sumbu lengkap, ringkasan periode, filter jenis sesi, dan halaman Detail Sesi.
- **Halaman detail grafik fullscreen interaktif** — Kelima grafik bisa dibuka besar dengan crosshair, statistik Min/Max/Rata-rata/Δ, pinch zoom, pan, dan data mengikuti jendela tampil.
- **Modul Crosshair: overlay bidikan game** — 44 gaya bidikan, warna, ukuran, opasitas, rotasi, posisi bebas (geser/slider/D-Pad), kunci posisi tembus sentuhan, area aman, preset, dan status di notifikasi.
- **Tooltip ℹ️ kustom di kartu Kesehatan & Riwayat** — Membuka tooltip melayang dekat ikon yang menjelaskan perbedaan Coulomb dan integrasi arus.
- **Slider ukuran tanggal terpisah di Jam Digital** — Ukuran jam dan tanggal dapat diatur independen, rata tengah, tersimpan dan ikut preset.
- **Tombol ekstrak database di Fitur Developer** — Menyalin isi database aplikasi ke `Documents/FTxT/extract/db`.
- **Changelog versi lama bisa dibaca di aplikasi** — Entri OLD-CHANGELOG di halaman Dokumentasi dengan pembaca markdown dan zoom.
- **Penampil dokumen lebih nyaman dibaca** — Jarak baris lebih lega, tautan bisa diketuk, dan teks bisa dipilih/disalin.
- **Ring level sesi memakai skema putih–hijau–merah** — Putih level saat ini, hijau bagian bertambah saat pengisian, merah bagian terpakai saat pengosongan.
- **Notifikasi overlay selalu ter-refresh** — Judul menampilkan level, status+arus, tegangan, daya, kapasitas terisi, dan suhu yang dipisah agar tetap terbaca.
- **Indikator loading saat membuka tab Monitor** — Overlay "Memuat data…" dengan spinner hingga data grafik pertama siap.
- **Aplikasi bebas ANR: operasi berat dipindah ke thread latar** — Rekonstruksi sesi, estimasi kesehatan, penyimpanan preset, dan penulisan database tidak lagi memblokir UI; database memakai WAL.
- **Penyimpanan riwayat baterai dihemat 5×** — Pembacaan tetap 1 detik, tetapi sampel ditulis ke database tiap 5 detik sehingga query lebih ringan tanpa kehilangan real-time.
- **Riwayat metrik & log aktivitas dipangkas setelah 7 hari** — Data lama dihapus otomatis agar database tidak membengkak.
- **Log ANR otomatis ke Documents/FTxT/Log_ANR** — Trace seluruh thread ditulis saat main thread macet ≥5 detik.

### ♻️ Perubahan Fitur
- **Daftar navigasi modul: ikon, sorotan aktif & ikon drag** — Item menampilkan ikon, item aktif disorot, dan ikon grip bisa ditekan-tahan untuk memindah urutan modul lewat drag.
- **Indikator "• Eksperimental" di header tab Monitor Battery Info** — Muncul hanya saat tab Monitor aktif.
- **Teks semua modul overlay rata tengah** — Berlaku untuk Jam Digital, Floating Text, FPS Display, Battery Info, Network Speed, dan Memory Stats.
- **Warna garis grafik mengikuti kondisi & nilai metrik** — Suhu memakai gradien warna, sedangkan Persentase/Tegangan/Arus berwarna hijau saat mengisi dan merah saat tidak.
- **Grafik riwayat tersusun grid 2×2** — Suhu & Daya di baris atas, Tegangan & Arus di bawah.
- **Pemilih rentang waktu grafik jadi baris label interaktif** — Rentang 5m hingga 48j (2 dan 15 menit dihapus, 36 dan 48 jam ditambahkan).
- **Kunci password jadi "Fitur Developer"** — Satu saklar mengunci panel Info Memori, Debugging, dan tombol Salin/Simpan Snapshot; section "Modul" diganti "Developer".
- **D-Pad posisi memakai pixel** — Bergerak 1–20 px per langkah dengan pengaturan interval, default 2 px.
- **Overlay Battery Info: posisi default ke kiri bawah** — Posisi awal (0.04, 1.0); pengguna yang sudah menggeser tidak terdampak.
- **Battery Strip: default lebih tipis & Hue Gradien** — Ketebalan 8→2 px dan warna default Hue Gradien.
- **Reset Data Estimasi perlu konfirmasi ketik "RESET"** — Tombol reset tidak aktif sampai kata kunci diketik.
- **Kartu Kesehatan menampilkan tiga nilai kapasitas** — Kapasitas pengisian, pengosongan, dan gabungan dengan persentase; warna mengikuti kesehatan. Reset menghapus seluruh sesi.

### 🔧 Optimasi & Penyesuaian
- **Sesi Berjalan: view kartu statis tanpa rebuild berulang** — Hanya dibangun ulang saat arah sesi berubah.
- **Grafik Suhu: warna garis di-cache** — Warna dihitung sekali via lookup table sehingga grafik lebih ringan.
- **Pencatatan sampel baterai dirapatkan ke ±1 detik** — Berlaku di semua kondisi layar dan pengisian.
- **Angka nilai di ujung garis diberi chip latar** — Agar tetap terbaca saat garis menumpuk.
- **Bar Aktivitas di halaman detail grafik menampilkan data benar** — Dihitung dari tabel aktivitas dan selaras dengan area plot.
- **Tombol Salin/Simpan/Reset di kartu Kesehatan lebih ramping** — Jarak dan padding dikecilkan.

### 🐞 Bug Fixes
- **Label "mAh" pada grafik batang sesi tumpang tindih** — Sumbu Y kini hanya menampilkan angka persen.
- **Chevron slider RGB ikut berubah saat dibuka/ditutup** — Panah berganti ▲/▼ sesuai keadaan.
- **Warna status pengisian grafik riwayat benar di semua rentang** — Segmen hijau/merah kembali sesuai kondisi asli.
- **Skala grafik Suhu default 35–40°C** — Melebar otomatis bila suhu di luar rentang.
- **Garis grafik terputus saat gap waktu lama** — Garis diputus saat jeda melebihi 5 menit.
- **Tab Monitor tidak lagi crash & macet** — Penyebab force close dihilangkan dan operasi database berat dipindah ke thread latar.
- **Sub-tab Sesi Berjalan & Kondisi & Riwayat kembali menampilkan isi** — Panel root tidak lagi ikut tersembunyi.

### 💡 Memo
> Tidak ada pencatatan per-file — CHANGELOG hanya mencatat **poin perubahan**, bukan detail file.

---

# [4.88.0] 2026/08/23 21:56 WITA 185 ***PUSH***
### ✨ Fitur Baru
- **Tab Monitor Battery Info: pemantauan real-time dengan ring gauge** — Kartu metrik real-time berisi ring gradien level, persen, kapasitas tersisa, status pengisian, grafik Persentase, grid suhu/voltase/arus/daya/cycle, dan badge kondisi suhu.
- **Grafik Riwayat: satu kartu empat chart yang bisa dilipat** — Line chart Canvas untuk Suhu, Daya, Tegangan, dan Arus dengan pemilih rentang global dan chevron pelipat; data bersumber dari database.
- **Estimasi Kapasitas & Kesehatan Baterai** — Mengestimasi kapasitas penuh dari sesi pengisian, diagregasi median dengan prioritas sesi layar mati; kartu menampilkan kapasitas, skor kesehatan, jumlah sesi, keyakinan, dan status pengumpulan. Kapasitas desain diinput manual; Salin/Simpan Snapshot menyertakan catatan kesehatan.
- **Pemantauan baterai full-aktif** — Merekam otomatis lewat foreground service ringan, nyala saat aplikasi dibuka dan setelah boot, dengan sampling dinamis hemat baterai.
- **Database riwayat baterai lokal (SQLite)** — Riwayat metrik dan sesi disimpan di SQLite tanpa Room; grafik, ekspor, dan estimasi membaca database, dan data JSON lama dimigrasikan otomatis.

### 🔧 Optimasi & Penyesuaian
- **Pembacaan data baterai disatukan ke satu sumber** — Overlay dan tab Monitor memakai util `BatteryReading` yang sama sehingga tidak ada lagi perbedaan angka daya.

---

# [4.87.0] 2026/08/21 20:28 WITA 184 ***RELEASE***
### ✨ Fitur Baru
- **Toggle "Tampilkan panel Debugging" & "Tampilkan panel Info Memori"** — Dua switch di Konfigurasi section Modul untuk menampilkan/menyembunyikan panel dari sidebar; default OFF dan tersimpan otomatis.
- **Proteksi password panel Debugging** — Switch Debugging terkunci sampai password benar dimasukkan, dengan tombol Relock; status unlock tersimpan.

### 🔧 Optimasi & Penyesuaian
- **Perbaikan typo "Debuging" → "Debugging"** — Seluruh label navigasi, judul panel, dan string resource diperbaiki.
- **Tombol Relock diperkecil** — Diubah menjadi TextView 11sp warna merah agar konsisten dengan switch.
- **Urutan section halaman Konfigurasi ditata ulang** — Ikon Aplikasi paling atas, Akses Izin di bawahnya, Info Memori di atas Debugging.

### ♻️ Perubahan Fitur
- **Urutan item Memory Stats via OrderZones drag-and-drop** — Pemilihan item diubah dari checkbox menjadi drag-and-drop dua zona (Aktif/Nonaktif), konsisten dengan Battery Info.

### 🐞 Bug Fixes
- **Sidebar drag-and-drop tidak stabil** — Setup sidebar kini dipisah dari refresh data sehingga dekorasi tidak menumpuk dan drag state tidak hilang; API posisi diperbarui.
- **Battery Stats overlay berkedip** — Pembacaan baterai dipindah ke background thread dan UI hanya diperbarui dari main thread.
- **Switch "Tampilkan panel Info Memori" tidak menghentikan overlay & background monitor** — Saat OFF, overlay dihentikan, config dimatikan, dan monitor dihentikan.
- **Panel Memory/Debugging masih bisa diakses saat switch dimatikan** — Panel aktif dialihkan ke Floating Text dan validitas panel dicek saat resume.

### 💡 Memo
> Seluruh entry log lama telah dipindahkan ke old-CHANGELOG.
