# Shizuku FPS — Daftar Isu & Diskusi Solusi

**Status:** Seluruh isu sudah diimplementasikan (menunggu verifikasi build)
**Konteks kode:** `main` di `f5a3b60` (`v4.96.0 Shizuku FPS`)
**Dokumen terkait:** `_schedule/SELESAI/SHIZUKU_FPS_READER.md`

Dokumen ini mencatat temuan yang perlu dibahas sebelum fitur Game FPS dianggap final. Solusi dibahas satu per satu; bagian **Keputusan** diisi setelah tiap isu disepakati. Nomor baris dapat bergeser setelah kode diubah.

Urutan pembahasan mengikuti prioritas: P0 (keamanan/kestabilan), P1 (perilaku fitur), P2 (kualitas dan UX).

## Aturan kerja sesi

- Fokus sesi ini adalah **diskusi dan analisis solusi**, bukan implementasi.
- Solusi yang disepakati dicatat di dokumen ini pada bagian **Keputusan**.
- Jangan mengubah kode, layout, build, commit, atau push sampai ada perintah eksplisit untuk mengerjakan.
- Jangan menganggap kalimat diskusi, usulan, atau persetujuan rencana sebagai perintah implementasi.
- Setelah satu isu selesai dibahas, lanjutkan membahas isu berikutnya untuk dicari solusinya.

---

## I-01 — Input package manual masuk mentah ke perintah shell

**Prioritas:** P0
**Status:** Disepakati
**Lokasi:**
- `app/src/main/java/exp/ftxt/ui/FpsPanelController.java:301-319` (input manual)
- `app/src/main/java/exp/ftxt/ui/FpsPanelController.java:414` dan `:459-465` (target disimpan)
- `app/src/main/java/exp/ftxt/features/gamefps/poc/GameFpsPoC.java:243` (perintah dibentuk)
- `app/src/main/java/exp/ftxt/features/gamefps/poc/ShizukuShell.java:132` (perintah ditulis ke shell)

**Temuan:**
Input manual yang tidak ditemukan di daftar pencarian dipakai apa adanya sebagai package. Nilainya disimpan sebagai target, lalu `GameFpsPoC` membentuk string `"dumpsys gfxinfo " + pkg`. `ShizukuShell` menulis string itu ke sesi shell (`{ <cmd> ; }`) tanpa validasi. `SurfaceFlingerFps` sudah memakai `execArgs` dengan quoting, tetapi jalur `gfxinfo` belum.

**Dampak:**
Teks berbahaya atau salah ketik dapat menjadi bagian perintah yang dijalankan dengan identitas Shizuku. Selain risiko keamanan, package tidak valid membuat pemantauan gagal berulang tanpa pesan yang jelas.

**Opsi solusi:**
1. Validasi nama package sebelum disimpan: cek keberadaan lewat `PackageManager.getPackageInfo` dan tolak karakter di luar `[A-Za-z0-9._]`; tampilkan pesan kesalahan.
2. Ganti pembentukan perintah menjadi `ShizukuShell.execArgs(new String[]{"dumpsys", "gfxinfo", pkg}, timeout)` agar argumen di-quote.
3. Batasi `ShizukuShell.exec(String)` hanya untuk perintah internal tetap, atau hapus jalur string dan wajibkan `execArgs` untuk semua perintah yang memuat data dari user.
4. Bersihkan target tersimpan jika package sudah tidak terpasang.

**Yang perlu diputuskan:**
Apakah validasi package cukup, atau jalur `exec(String)` juga ditutup total. Opsi 1 + 2 + 4 menutup masalah tanpa mengubah banyak kode.

**Keputusan:** Diimplementasikan:
- Validasi package sebelum disimpan: cek keberadaan lewat `PackageManager.getPackageInfo` dan tolak karakter di luar `[A-Za-z0-9._]`; tampilkan pesan kesalahan.
- Perintah `gfxinfo` wajib memakai `ShizukuShell.execArgs(new String[]{"dumpsys", "gfxinfo", pkg}, timeout)`; jalur string yang memuat data user dihapus.
- `ShizukuShell.exec(String)` dipertahankan hanya untuk perintah internal tetap (string konstanta), bukan untuk data dari user.
- Target tersimpan dibersihkan jika package sudah tidak terpasang.

Implementasi menunggu perintah terpisah.

---

## I-02 — Deteksi foreground fail-open dan jendela waktu 60 detik

**Prioritas:** P0
**Status:** Disepakati
**Lokasi:** `app/src/main/java/exp/ftxt/features/gamefps/poc/GameFpsPoC.java:302-344`

**Temuan:**
`isTargetForeground()` mengembalikan `true` ketika izin usage access belum ada, service `UsageStatsManager` null, `queryEvents` null, tidak ada event dalam 60 detik terakhir, atau terjadi exception. Artinya tanpa izin usage access, polling tetap berjalan seolah-olah game selalu di depan. Jendela 60 detik juga membuat hasil bisa keliru ketika target sudah lama dibuka atau ketika event terakhir sudah keluar dari jendela.

**Dampak:**
Pemantauan dan overlay tetap aktif saat game tidak di depan: boros baterai, angka FPS tidak relevan, dan status "menunggu game dibuka" tidak pernah muncul. Perilaku ini juga membuat penghentian otomatis saat keluar game tidak bisa diandalkan.

**Opsi solusi:**
1. Fail-closed: jika izin usage access tidak ada, jangan mulai pemantauan dan tampilkan status yang mengarahkan user memberi izin.
2. Tetap izinkan mode tanpa izin, tetapi hanya saat user menyalakan secara manual, dengan interval lebih lambat dan status peringatan yang jelas.
3. Perpanjang/ubah strategi query: simpan event foreground terakhir dari jendela yang lebih panjang, atau pakai `queryUsageStats` untuk melihat pemakaian terakhir sebagai pembanding.
4. Tambahkan jalur pengecekan alternatif lewat Shizuku (mis. aktivitas teratas) bila izin usage access tidak tersedia.

**Yang perlu diputuskan:**
Perilaku `isTargetForeground()` saat deteksi gagal: fail-closed, tetap memakai target terakhir dengan batas waktu, atau fallback ke detektor Shizuku.

**Keputusan:** Disepakati:
- Izin usage access bukan syarat mutlak; jalur utama tetap `UsageStatsManager`, dengan fallback deteksi lewat Shizuku (lihat I-08).
- Urutan deteksi: `UsageStatsManager` dengan cache singkat (~1 detik) → fallback Shizuku `dumpsys activity top`/`dumpsys window` via `execArgs` → jika keduanya gagal, `isTargetForeground()` mengembalikan `false` (fail-closed).
- Event foreground dicari pada jendela lebih panjang (mis. 10 menit) atau dibandingkan dengan `queryUsageStats`; event terakhir tidak ditemukan berarti tidak terdeteksi, bukan dianggap foreground.
- `null`, exception, timeout, atau izin yang belum ada tidak pernah dianggap foreground.
- Saat target tidak terdeteksi: reset cache frame, gunakan interval idle, dan tampilkan status jelas; overlay kembali ke FPS aplikasi normal.
- Detektor yang sama menjadi dasar mode Otomatis (I-08).

Implementasi menunggu perintah terpisah.

---

## I-03 — Service bisa hidup lagi meski toggle fitur dimatikan

**Prioritas:** P1
**Status:** Disepakati
**Lokasi:**
- `app/src/main/java/exp/ftxt/features/gamefps/GameFpsService.java:117` (retry dimulai di `onCreate`)
- `app/src/main/java/exp/ftxt/features/gamefps/GameFpsService.java:122-133` (`onStartCommand`)
- `app/src/main/java/exp/ftxt/features/gamefps/GameFpsService.java:161-175` (`retryRunnable`)

**Temuan:**
Service mengembalikan `START_STICKY`, memulai `retryRunnable` di `onCreate`, dan memulai `GameFpsPoC` hanya berdasarkan binder/izin Shizuku. Preferensi `game_fps_overlay_on` tidak dicek di jalur start maupun retry. Jika proses aplikasi dibunuh sistem, service dapat dibuat ulang dan memulai pemantauan lagi walau toggle sudah dimatikan.

**Dampak:**
Overlay dan polling bisa muncul kembali tanpa sepengetahuan user, sehingga membingungkan dan memakan daya. Ini juga membuat status toggle di panel tidak lagi mencerminkan kondisi sebenarnya.

**Opsi solusi:**
1. Tambahkan satu helper `shouldRun()` yang mengecek preferensi toggle + izin overlay + target; pakai di `onCreate`, `onStartCommand`, dan `retryRunnable`.
2. Ubah `START_STICKY` menjadi `START_NOT_STICKY` agar service tidak dihidupkan ulang otomatis.
3. Jika toggle mati, panggil `stopSelf()` dan lepaskan overlay/sesi shell.

**Yang perlu diputuskan:**
Apakah fitur memang ingin tetap hidup setelah proses mati (mis. demi pengalaman game), atau cukup berhenti dan menunggu user menyalakan lagi.

**Keputusan:** Disepakati:
- Tambah `shouldRun()` yang mengecek pref `game_fps_overlay_on` aktif dan target tersedia; cakupan mode Otomatis mengikuti I-08. Status Shizuku tidak dicek di sini karena ditangani `retryRunnable`.
- Panggil `shouldRun()` di `onCreate` sebelum retry dipasang, di `onStartCommand`, dan di awal `retryRunnable`. Jika `false`, panggil `stopSelf()`; `onDestroy` membersihkan sesi shell dan overlay.
- `START_STICKY` dipertahankan agar service pulih saat proses dibunuh selama toggle aktif, tetapi recreate langsung berhenti bila toggle mati.
- Saat toggle dimatikan, `stop()` tetap dipanggil dan retry tidak boleh menyalakan ulang PoC.

Implementasi menunggu perintah terpisah.

---

## I-04 — Overlay Game FPS masih terpisah dan hardcoded

**Prioritas:** P1
**Status:** Disepakati
**Lokasi:** `app/src/main/java/exp/ftxt/features/gamefps/GameFpsService.java:200-260`

**Temuan:**
Overlay dibuat sebagai `TextView` baru dengan warna putih, ukuran 14, latar `0xB3000000`, dan posisi awal `dp(16), dp(96)`. Tidak ada penyimpanan posisi/ukuran/warna, penanganan rotasi, safe area, snap, atau integrasi dengan pengaturan FPS yang sudah ada. Overlay ini juga tidak terdaftar di Smart Panel.

**Dampak:**
Pengaturan tidak bertahan setelah service dibuat ulang, posisi bisa keluar layar saat rotasi, dan tampilannya tidak konsisten dengan overlay FPS lain. User juga tidak bisa mengatur overlay Game FPS dari panel yang sama.

**Keputusan yang disepakati:**
- Overlay FPS tetap satu dan memakai `FpsModule` yang sudah ada. Overlay terpisah di `GameFpsService` dihapus.
- `FpsModule` memiliki dua sumber nilai: FPS aplikasi sendiri (`Choreographer`, seperti sekarang) dan FPS game dari `GameFpsPoC` (nilai eksternal).
- `GameFpsService` tidak lagi membuat `TextView`/`WindowManager`; tugasnya hanya membaca FPS, mengatur status/notifikasi, dan mengirim nilai ke `FpsModule` lewat bridge `FloatingService`.
- Warna indikator game hanya diterapkan pada **angka FPS**, bukan label `FPS`. Label tetap memakai `FpsConfig.labelColor` di semua mode.
- Ikon aplikasi ditampilkan di kiri angka saat sumber game/otomatis aktif, mis. `[icon] 60.0 FPS`. Ikon berasal dari package yang sedang dibaca, bukan nama game. Nama game tidak ditampilkan lagi.
- Ikon dan warna indikator aktif hanya saat FPS dari Shizuku benar-benar terbaca (target di depan dan ada sampel FPS > 0). Saat mode game aktif tetapi game belum terbuka atau FPS game belum tersedia, overlay tetap menampilkan FPS aplikasi dengan warna normal dan tanpa ikon, tanpa status "menunggu game" di overlay.
- Tambah `FpsConfig.gameColor` dan opsi panel **Warna Indikator Mode Game**; disimpan sebagai `fps_game_color` di `ftxt_prefs` dan dimuat oleh `ConfigLoader`.
- Saat FPS game berhenti (game keluar/latar, Shizuku mati, atau toggle game dimatikan), angka kembali memakai `FpsConfig.color` dan sumber tampilan kembali ke FPS aplikasi.
- `Choreographer` tetap berjalan sebagai sumber cadangan agar peralihan kembali ke FPS normal mulus; nilai game hanya menimpa tampilan selama FPS game tersedia.
- Overlay hidup jika FPS Display aktif **atau** mode game aktif. Asumsi: jika FPS Display sedang nonaktif tetapi mode game diaktifkan, overlay tetap ditampilkan untuk menunjukkan FPS aplikasi sampai FPS game tersedia.
- Posisi, ukuran, shadow, background, safe area, touch passthrough, dan Smart Panel tetap memakai `FpsModule` yang sama; tidak ada penyimpanan posisi baru.

**Rincian implementasi (belum dikerjakan):**
1. `FpsConfig.java` — tambah `public static int gameColor = 0xFFFF9800;` (default sementara oranye).
2. `ConfigLoader.java` — muat `fps_game_color` dari `ftxt_prefs`.
3. `FpsModule.java` — tambah `setGameFps(float, String packageName)` dan `setGameActive(boolean, String packageName)`; `updateDisplay()` memilih warna angka: game aktif memakai `FpsConfig.gameColor`, selain itu `FpsConfig.color`; label selalu `FpsConfig.labelColor`. `frameCallback` tetap mengisi FPS aplikasi, sedangkan nilai game menimpa angka selama aktif. Kondisi repost `Choreographer` menjadi `running && (FpsConfig.enabled || gameModeAktif)`.
4. `FpsModule.java` — tampilkan ikon aplikasi lewat compound drawable di kiri teks: muat `PackageManager.getApplicationIcon(packageName)`, cache per package, skala ikon mengikuti ukuran teks (sekitar 1,2x), beri jarak 4dp, dan hapus ikon saat fallback ke FPS normal. Panggil ulang pengaturan ikon saat `updateSize()` berubah.
5. `FloatingService.java` — tambah bridge `setGameFps(float, String packageName)` dan `setGameActive(boolean, String packageName)` yang diteruskan ke `fpsModule`; pastikan modul berjalan saat mode game aktif.
6. `GameFpsService.java` — hapus pembuatan/penghapusan overlay serta seluruh field `WindowManager`/`TextView`; panggil bridge saat FPS berhasil dibaca, saat FPS game tidak tersedia, saat service berhenti, dan saat izin/Shizuku hilang. Sertakan package yang sedang dibaca agar ikon bisa diperbarui.
7. `FpsPanelController.java` dan layout panel FPS — tambah baris **Warna Indikator Mode Game** di dekat Warna FPS; simpan `fps_game_color`; preview dan color picker mengikuti pola `fps_color`.
8. `FpsPositionController.java` dan Smart Panel — tidak perlu perubahan; modul dan posisi tetap sama.

**Catatan teknis ikon:**
- Ikon memakai `ShadowTextView` yang sudah ada melalui compound drawable, sehingga background, shadow teks, drag, posisi, safe area, dan Smart Panel tidak perlu dirombak.
- Ikon tidak diberi tint warna indikator; warna asli ikon aplikasi tetap dipertahankan.
- Jika ikon tidak tersedia, overlay tetap menampilkan angka dan label tanpa ikon.
- Tidak ada toggle tambahan; ikon muncul otomatis saat sumber Shizuku aktif dan hilang saat fallback ke FPS normal.

**Perilaku yang diharapkan:**
- FPS Display aktif, mode game nonaktif: `12.3 FPS`, angka hijau, label cyan, tanpa ikon (seperti sekarang).
- Mode game aktif, game belum terbuka: `12.3 FPS` (FPS aplikasi), angka hijau, label cyan, tanpa ikon.
- Mode game aktif, game terdeteksi dan FPS terbaca: `[icon] 60.0 FPS`, angka memakai warna indikator game, label tetap cyan, ikon aplikasi di kiri.
- `Sembunyikan Label` aktif saat FPS game terbaca: `[icon] 60.0` dengan warna indikator.
- Game keluar, Shizuku mati, atau toggle game dimatikan: kembali ke `12.3 FPS`, angka hijau, ikon hilang.
- FPS Display nonaktif, mode game aktif: overlay muncul; sebelum FPS game terbaca menampilkan FPS aplikasi tanpa ikon, setelah terbaca memakai warna indikator dan ikon. Setelah mode game mati, overlay hilang.

**Catatan:** notifikasi foreground `GameFpsService` tetap ada; yang dihapus hanya overlay-nya. Untuk mode otomatis, istilah "FPS game" pada bagian ini mengikuti I-08, yaitu nilai FPS dari aplikasi depan yang dibaca lewat Shizuku.

**Keputusan:** Disepakati untuk direncanakan; implementasi menunggu perintah terpisah.

---

## I-05 — Arsitektur ganda dan penamaan masih PoC

**Prioritas:** P2
**Status:** Disepakati
**Lokasi:**
- `features/fps/src/main/java/exp/ftxt/features/fps_display/FpsModule.java` (FPS aplikasi sendiri)
- `app/src/main/java/exp/ftxt/features/gamefps/poc/` (FPS game via Shizuku)
- `app/src/main/java/exp/ftxt/features/gamefps/GameFpsService.java`

**Temuan:**
Ada dua sistem FPS: modul FPS lama mengukur rendering aplikasi sendiri, sedangkan Game FPS membaca SurfaceFlinger/gfxinfo untuk aplikasi lain. Package baru masih bernama `poc` dan layanan berada di module `app`, bukan di module fitur. Keduanya punya overlay dan pengaturan sendiri.

**Dampak:**
Perawatan menjadi lebih berat, istilah "FPS" bisa membingungkan, dan kualitas kode masih terlihat seperti eksperimen. Menyatukan atau setidaknya menamai ulang akan memengaruhi struktur rilis.

**Opsi solusi:**
1. Promosikan package `poc` menjadi package fitur final (mis. `features/gamefps`) tanpa mengubah perilaku.
2. Gabungkan sebagai mode di modul FPS lama: satu panel, satu config, dua sumber data.
3. Pertahankan sebagai eksperimen, tetapi sembunyikan di balik toggle eksperimental dan dokumentasikan batasannya.

**Yang perlu diputuskan:**
Apakah fitur ini akan dianggap fitur stabil pada rilis berikutnya, atau tetap eksperimental untuk sementara.

**Keputusan:** Disepakati:
- Fitur dianggap fitur stabil, bukan eksperimental; tidak ada toggle eksperimental tersembunyi.
- Tanggung jawab dipisah: mesin pembaca Shizuku/parser tetap package sendiri; overlay, config, dan panel menyatu di `FpsModule` mengikuti I-04.
- Rename `features/gamefps/poc` menjadi `features/gamefps/reader` dan `GameFpsPoC` menjadi `GameFpsReader`; `ShizukuShell`, `GfxInfoParser`, dan `SurfaceFlingerFps` tetap. Tanpa perubahan perilaku.
- `GameFpsService` tetap di module `app` karena butuh foreground service, manifest, dan `ShizukuProvider`.
- Batasan kompatibilitas perangkat didokumentasikan lewat I-06.

Implementasi menunggu perintah terpisah.

---

## I-06 — Beban main thread dan kompatibilitas perangkat

**Prioritas:** P2
**Status:** Disepakati
**Lokasi:**
- `app/src/main/java/exp/ftxt/ui/FpsPanelController.java:467-483`
- `app/src/main/java/exp/ftxt/ui/GameAppListAdapter.java:38-46`
- `app/src/main/java/exp/ftxt/ui/GameSearchAdapter.java:53-69`
- `app/src/main/java/exp/ftxt/features/gamefps/poc/SurfaceFlingerFps.java:16-80`
- `app/src/main/java/exp/ftxt/features/gamefps/poc/GfxInfoParser.java:24-77`

**Temuan:**
Pengambilan daftar aplikasi sudah di background, tetapi pemanggilan `getApplicationLabel` dan sorting tetap dilakukan di main thread saat `setApps`. Deteksi layer SurfaceFlinger dan pembacaan `--latency` sangat bergantung pada format/OEM, sedangkan `GfxInfoParser` memakai regex heuristik yang belum tentu cocok dengan output `framestats` tiap perangkat.

**Dampak:**
Panel bisa tersendat saat membuka daftar aplikasi di perangkat dengan banyak aplikasi. Di sebagian perangkat, FPS bisa tidak terbaca atau salah karena nama layer/format `dumpsys` berbeda.

**Opsi solusi:**
1. Pindahkan resolusi label dan sorting ke executor background; kirim hasil yang sudah siap ke adapter. Cache label/ikon bila perlu.
2. Tambahkan deteksi kegagalan yang jelas per perangkat dan pesan status yang bisa dilaporkan user.
3. Kumpulkan sample output `dumpsys SurfaceFlinger --list`/`--latency` dan `gfxinfo` dari beberapa perangkat, lalu sesuaikan parser.
4. Sesuaikan interval polling secara adaptif dan hindari pemanggilan `--list` berulang saat layer belum ditemukan.

**Yang perlu diputuskan:**
Sejauh mana dukungan perangkat yang ingin diklaim, dan apakah optimasi daftar aplikasi masuk sebelum rilis atau setelah fitur stabil.

**Keputusan:** Disepakati:
- Resolusi label dan sorting daftar aplikasi dipindahkan ke executor background; adapter hanya menerima data siap. Label/ikon di-cache per package.
- Hasil `SurfaceFlinger --list` di-cache dan tidak dipanggil ulang selama layer masih valid; polling memakai interval aktif/idle yang ada, dengan backoff saat gagal berulang.
- Parser memakai fallback berlapis (SurfaceFlinger → `gfxinfo` total frame → `framestats`); setiap kegagalan mencatat potongan output dan versi Android ke `GameFpsLog` untuk diagnosis.
- Klaim dukungan: teruji di perangkat tes; OEM/versi Android lain bersifat best-effort, bukan jaminan. Status kegagalan ditampilkan agar bisa dilaporkan user.
- Optimasi daftar aplikasi masuk sebelum rilis; penyesuaian parser per OEM menyusul setelah ada sample perangkat lain.

Implementasi menunggu perintah terpisah.

---

## I-07 — Alur izin dan onboarding Shizuku

**Prioritas:** P2
**Status:** Disepakati
**Lokasi:**
- `app/src/main/java/exp/ftxt/ui/FpsPanelController.java:360-368`
- `app/src/main/java/exp/ftxt/ui/FpsPanelController.java:443-454`
- `app/src/main/java/exp/ftxt/features/gamefps/reader/ShizukuHelper.java:11-40`
- `app/src/main/AndroidManifest.xml:16-21` (queries)

**Temuan:**
Fitur membutuhkan Shizuku aktif, izin Shizuku, izin overlay, dan idealnya izin usage access. Permintaan izin Shizuku baru dipicu saat toggle dinyalakan dan panel dibuka. Status hanya berupa teks; belum ada tombol untuk membuka Shizuku, pengaturan izin usage access, atau penjelasan langkah yang harus dilakukan.

**Dampak:**
User bisa menyalakan toggle tetapi tidak melihat apa pun karena salah satu prasyarat belum terpenuhi, lalu menganggap fitur rusak.

**Opsi solusi:**
1. Tampilkan checklist prasyarat dengan status dan tombol aksi: pasang/buka Shizuku, beri izin Shizuku, izin overlay, izin usage access.
2. Nonaktifkan toggle sampai prasyarat minimum terpenuhi, dengan penjelasan di sampingnya.
3. Pertahankan teks status, tetapi tambahkan tombol bantuan dan pesan yang menyebut langkah berikutnya.

**Yang perlu diputuskan:**
Apakah izin usage access dijadikan prasyarat wajib (terkait I-02), dan seberapa banyak bantuan yang ditampilkan di panel.

**Keputusan:** Disepakati:
- Panel Game FPS hanya menampilkan ringkasan status prasyarat: Shizuku terpasang/aktif, izin Shizuku, izin overlay, dan izin usage access (opsional), dengan arahan ke Konfigurasi.
- Pengaturan izin dipusatkan di Konfigurasi > Akses Izin: switch overlay, notifikasi, baterai, akses penggunaan, dan tombol Atur untuk izin Shizuku.
- Tidak ada tombol aksi izin di panel Game FPS.
- Toggle fitur hanya bisa dinyalakan jika izin overlay dan Shizuku aktif/izin terpenuhi. Usage access tidak wajib, konsisten dengan I-02.
- Jika prasyarat belum lengkap, panel menampilkan langkah berikutnya, bukan sekadar teks status.
- Mode Otomatis menampilkan status "Deteksi otomatis tidak tersedia" bila usage access dan fallback Shizuku gagal; mode Manual tetap bisa dipakai.
- Tidak ada dialog onboarding terpisah; pengaturan izin berada di Konfigurasi dan panel Game FPS hanya menampilkan ringkasan.

Implementasi menunggu perintah terpisah.

---

## I-08 — Mode otomatis mengikuti aplikasi depan

**Prioritas:** P1
**Status:** Disepakati
**Asal:** Menyerap item Fase 3.5 rencana `_schedule/SELESAI/SHIZUKU_FPS_READER.md` (auto-detect foreground).
**Lokasi terkait:**
- `app/src/main/java/exp/ftxt/features/gamefps/poc/GameFpsPoC.java`
- `app/src/main/java/exp/ftxt/features/gamefps/GameFpsService.java`
- `app/src/main/java/exp/ftxt/ui/FpsPanelController.java`
- layout panel FPS bagian Game FPS

**Tujuan:**
Menambahkan mode target **Otomatis** agar FPS mengikuti aplikasi yang sedang berada di depan, tanpa user memilih package game secara manual. Mode **Manual** tetap tersedia sebagai override dan fallback.

**Keputusan yang disepakati:**
- Deteksi memakai **aktivitas teratas**, bukan window yang sedang fokus. Keyboard/IME, chat bubble, dan dialog adalah window, bukan activity, sehingga tidak menggantikan game sebagai target.
- Jalur utama deteksi: `UsageStatsManager` (butuh izin usage access). Jalur cadangan: Shizuku `dumpsys activity`/`dumpsys window` bila izin usage access tidak tersedia atau gagal.
- Saat package target berubah: beri jeda singkat (sekitar 500 ms), lalu reset cache layer, indeks layer, dan counter frame sebelum membaca FPS package baru.
- Pengecualian target: package FTxT sendiri, SystemUI, launcher/home, dan IME. Aplikasi chat tidak otomatis dikecualikan.
- Jika aplikasi yang dikecualikan benar-benar menjadi aktivitas teratas (mis. launcher atau SystemUI), tahan FPS terakhir selama sekitar 2 detik; setelah itu kembali ke FPS aplikasi normal dengan warna normal. Jika FPS Display sedang nonaktif, overlay disembunyikan. Tidak ada teks "menunggu game" di overlay.
- Jika game masih menjadi aktivitas teratas saat chat bubble atau keyboard muncul, FPS game tetap dibaca dan warna indikator tetap aktif.
- Jika chat membuka activity penuh, chat menjadi aktivitas teratas. Mode otomatis akan mengikuti chat sebagai aplikasi depan (bukan game) karena mode ini memang mendeteksi aplikasi apa pun. Tidak ada opsi Kunci Target; mode Manual tetap tersedia bagi user yang ingin mengunci target.
- Warna indikator pada mode otomatis berarti "FPS dari sumber Shizuku sedang tampil", bukan khusus kategori game. Angka memakai `FpsConfig.gameColor`; label tetap `FpsConfig.labelColor`. Ikon aplikasi mengikuti package yang sedang dibaca dan hilang saat fallback ke FPS aplikasi normal. Saat fallback, angka kembali memakai `FpsConfig.color`.
- Izin usage access tidak dijadikan syarat mutlak: jika tidak ada, coba jalur Shizuku. Jika keduanya gagal, mode otomatis tidak tersedia dan panel menampilkan status; mode manual tetap bisa dipakai.
- Item Fase 3.5 rencana awal (auto-detect foreground) disatukan ke isu ini; tidak dibuat isu terpisah.

**Perilaku yang diharapkan:**
- Game di depan, tanpa aplikasi lain: `[icon game] FPS game`, angka warna indikator.
- Game di depan + chat bubble + keyboard: game tetap aktivitas teratas, `[icon game] FPS game` dan warna indikator tidak berubah.
- Game + chat full-screen: target berpindah ke chat; `[icon chat] FPS chat` ditampilkan.
- Launcher/SystemUI/FTxT di depan: tahan FPS dan ikon terakhir sebentar, lalu kembali ke FPS aplikasi normal dengan warna normal tanpa ikon.
- Aplikasi lain yang bukan pengecualian di depan: target berpindah ke aplikasi itu, cache di-reset, ikon diperbarui, lalu FPS-nya ditampilkan.

**Rincian implementasi (belum dikerjakan):**
1. Tambah preferensi mode target (Otomatis/Manual) dan simpan di `ftxt_prefs`.
2. Buat detektor foreground dengan `UsageStatsManager`; sediakan fallback Shizuku dan daftar pengecualian.
3. `GameFpsPoC` menerima package dari detektor, bukan hanya dari target manual; reset state saat package berubah.
4. Panel Game FPS menampilkan pilihan mode, aplikasi depan saat ini, dan target manual yang tersimpan.
5. Saat package berubah, perbarui ikon aplikasi di `FpsModule` bersamaan dengan nilai FPS; tahan ikon terakhir selama masa tenggang saat aplikasi dikecualikan, lalu hapus saat fallback.
6. Pastikan semua perintah `dumpsys` tetap memakai `execArgs` dan package hasil deteksi divalidasi.

**Keputusan:** Mode otomatis disepakati untuk direncanakan, mengikuti aplikasi depan tanpa opsi Kunci Target. Mode Manual tetap tersedia untuk mengunci target.

---

## I-09 — Susunan panel Game FPS terlalu padat

**Prioritas:** P2
**Status:** Disepakati
**Lokasi:**
- `app/src/main/res/layout/panel_fps.xml:658-786` (sub-tab Game FPS)
- `app/src/main/java/exp/ftxt/ui/FpsPanelController.java:214-222` (binding view)
- `app/src/main/java/exp/ftxt/ui/FpsPanelController.java:237-250` (status saat toggle)
- `app/src/main/java/exp/ftxt/ui/FpsPanelController.java:321-330` (aksi log)
- `app/src/main/java/exp/ftxt/ui/FpsPanelController.java:360-431` (refresh status/target)

**Temuan:**
Panel Game FPS menampilkan Target, nilai FPS, status, teks bantuan, dan dua tombol log di bawah RecyclerView. Karena `rvFpsGameApps` memakai `0dp` + `weight=1`, semua elemen setelahnya terdorong ke bawah dan daftar aplikasi ikut menyempit. Nilai FPS di panel dianggap redundan karena FPS baru berguna saat game berada di depan, sementara panel tidak terlihat dan overlay sudah menampilkan FPS. Status teks di bawah FPS juga dianggap tidak penting. Dua tombol log selebar layar membuat panel sesak dan terlihat seperti aksi utama.

**Arahan yang disepakati:**
- Hapus `txtFpsGameValue` dan `txtFpsGameStatus` dari panel Game FPS; jangan tampilkan FPS/status di panel.
- Sediakan satu indikator ringkas mode game: aktif atau nonaktif, mis. `● Aktif` / `● Nonaktif`, dengan warna berbeda.
- Pindahkan `txtFpsGameTarget` ke bawah `txtFpsGameShizuku`; indikator mode diletakkan sejajar dengan baris target.
- Header: ubah `btnFpsGameRefreshApps` dari pill button menjadi text button; tambah tombol text `Log` di sebelahnya.
- Hapus baris `btnFpsGameViewLog` dan `btnFpsGameSaveLog` di bagian bawah.
- Tombol `Log` membuka dialog log yang berisi isi log dan aksi **Simpan** serta **Tutup**.
- Aksi **Simpan** memanggil `GameFpsLog.save()` dan menampilkan hasil path/gagal; **Tutup** menutup dialog.

**Alasan:**
Target, mode aktif, dan kontrol utama sebaiknya terlihat sebelum daftar aplikasi. FPS dan status rinci tidak perlu memakan ruang panel karena sudah diwakili overlay atau tidak memengaruhi keputusan user. Log adalah aksi diagnostik sekunder, jadi cukup satu tombol di header dan aksi simpan di dalam dialog.

**Rincian implementasi (belum dikerjakan):**
1. Layout — hapus `txtFpsGameValue`, `txtFpsGameStatus`, dan baris dua tombol log; pindahkan target ke bawah status Shizuku; tambah indikator mode sejajar target.
2. Header — ubah Refresh menjadi text button dan tambah tombol text `Log`; pertahankan judul sebagai `weight=1`.
3. Controller — hapus binding/update FPS dan status; perbarui indikator mode dari preferensi `game_fps_overlay_on` saat checkbox berubah, panel dibuka, dan tab Game FPS dipilih.
4. Dialog log — ganti aksi tunggal `Tutup` menjadi `Simpan` + `Tutup`; `Simpan` memakai `GameFpsLog.save(activity)` dan menampilkan hasilnya lewat toast.
5. Hapus referensi ke `btnFpsGameViewLog`/`btnFpsGameSaveLog`; gunakan satu id tombol `Log` di header.

**Keputusan detail:**
- Indikator memakai teks saja: `● Aktif` (hijau) / `● Nonaktif` (abu-abu), tanpa badge/background.
- Teks bantuan diringkas menjadi satu baris, mis. "Pilih game lalu buka game-nya."
- Pemilih mode Otomatis/Manual diletakkan di atas daftar. Saat Otomatis, pencarian dan daftar manual disembunyikan; saat Manual, keduanya tampil dan target tersimpan tetap ditampilkan.
- Tombol `Log` dan `Refresh` tetap text button di header dengan judul `weight=1`; di layar sempit cukup teks singkat, tanpa overflow.

**Keputusan:** Arahan dan detail di atas disepakati; implementasi menunggu perintah terpisah.

---

## Catatan Pembahasan

- Isu P0 sebaiknya diputuskan lebih dulu karena menyangkut keamanan dan perilaku dasar.
- Setelah tiap isu disepakati, isi bagian **Keputusan** dan ubah **Status** menjadi `Disepakati` atau `Ditunda`.
- Perubahan kode baru dikerjakan setelah keputusan tiap isu jelas.
