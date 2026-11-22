# Riset dan Evaluasi Lisensi FTxT

**Status:** KAJIAN — dasar diskusi, bukan lisensi final.
**Tanggal:** 2026/11/22
**Sumber permintaan:** `_temp/Permintaan.txt`
**Ruang lingkup:** riset dan audit repository, perbandingan lisensi, dan evaluasi tujuan lisensi. Tidak mengubah kode, dependency, README, CHANGELOG, versionCode, atau file lisensi apa pun.
**Catatan:** dokumen ini bukan nasihat hukum. Rujukan hukum yang disebut dipakai sebagai kerangka konseptual; keputusan final sebaiknya dikonsultasikan dengan ahli hukum yang kompeten.

---

## A. Ringkasan kondisi project

Repo lokal bersih dengan remote `gaozhan906/FTxT`. HEAD berada di `0ab0684` (`v4.98.0`, versionCode 386), dan entry CHANGELOG teratas masih berstatus `***ONGOING***`. Pemeriksaan environment sesuai prosedur `AGENTS.md` menunjukkan tidak ada drift.

Belum ditemukan `LICENSE`, `NOTICE`, `COPYING`, atau header hak cipta di checkout saat ini. `README.md:62-68` menyatakan "Belum ada lisensi resmi untuk project ini", menyebut bahwa sebagian besar pengembangan dibantu AI, lalu menyatakan "Silakan gunakan, modifikasi, fork, atau kustomisasi sesuai kebutuhan". Kalimat terakhir berpotensi dibaca sebagai izin luas dan akan bertentangan dengan niat membatasi penggunaan komersial.

Dokumen `README.md`, `PANDUAN.md`, `CHANGELOG.md`, dan `STRUKTUR.md` disalin ke `app/src/main/assets/` oleh task `syncDocs` di `app/build.gradle:86-92`. Dengan demikian, pernyataan lisensi di README ikut terdistribusi di dalam APK. `.github/workflows/release.yml` membuild dan mengunggah APK ke GitHub Releases tanpa pemeriksaan lisensi.

## B. Audit asal-usul kode dan materi

**Buatan sendiri — dugaan beralasan, bukan pembuktian per baris.** Modul overlay inti dan dokumentasi internal meliputi `features/floating-text`, `features/clock`, `features/fps`, `features/network`, `features/crosshair`, `shared/config`, `shared/ui`, `shared/color`, `shared/preset`, `core`, serta README/CHANGELOG/PANDUAN/STRUKTUR. Bukti yang dipakai: struktur multi-modul di `settings.gradle`, deskripsi fitur di `README.md`, dan riwayat perubahan di `CHANGELOG.md`.

**Terinspirasi.** App Manager berasal dari dekompilasi APK `com.wssc.appanalyzer` pada `_sample/App Manager/base.apk`, sebagaimana dicatat di `_schedule/KAJIAN/ANALISIS_APP_MANAGER_REFERENSI.md:1-9`. Implementasi FTxT berada di `core/src/main/java/exp/ftxt/core/appmanager/`, `app/src/main/java/exp/ftxt/ui/AppManagerActivity.java`, `AppDetailActivity.java`, dan `AppListAdapter.java`. Kode memakai namespace `exp.ftxt` dan API Android standar. Pencarian paket khas acuan (`wssc`, `mmkv`, `admob`, `applovin`, `firebase`, `lottie`, `xpopup`, `apkparser`, `zip4j`, `com.tencent`) tidak menemukan kecocokan di berkas `.java`, `.xml`, atau `.gradle`. Dokumen rencana di `_schedule/AKTIF/APP_MANAGER.md:11-30` juga eksplisit tidak meniru struktur internal, iklan, telemetri, hidden API reflection, dan otomasi aksesibilitas. Kategori ini didasarkan pada bukti dokumentasi dan inspeksi pola kode, bukan perbandingan forensik menyeluruh.

**Diadaptasi dari cabang internal.** `_schedule/VERIFIKASI/PORTING_FBI_KE_FTXT.md:4-7` menyebut FBI sebagai duplikasi modul baterai FTxT yang dikembangkan lebih lanjut, lalu di-port balik ke FTxT. Jejaknya mencakup `features/memory/`, `core/src/main/java/exp/ftxt/core/CrashLogger.java`, `app/src/main/java/exp/ftxt/ui/BatteryOrderZonesView.java`, serta drawable `mem_*` dan `bat_*`. Menurut dokumen ini, FBI bukan pihak ketiga; namun kepemilikan atas pengembangan cabang FBI tetap perlu dikonfirmasi.

**Pihak ketiga.** Dependency pada `app/build.gradle:114-132` dan `build.gradle:11` meliputi AndroidX, Material, ConstraintLayout, SplashScreen, RecyclerView, ViewPager2, GSON, Markwon, Shizuku, JUnit, Espresso, dan AGP 8.12.0. Lisensinya tetap melekat pada pemberi lisensi masing-masing dan tidak menjadi milik FTxT.

**Belum diketahui.** Aset raster `app/src/main/res/drawable/ic_launcher_foreground.png`, `ic_launcher_foreground_alt.png`, `ic_launcher_bg.png`, serta dua berkas PNG di `_karantina/` tidak memiliki catatan asal-usul yang ditemukan selama audit. Tidak ada kesimpulan bahwa aset tersebut disalin dari aplikasi lain.

**Materi referensi di luar distribusi.** `_sample/` berisi tujuh proyek/aplikasi: App Manager, CrossExp, Data_Monitor_v1.0.223(222)_base_src, DevCheck, Game Launcher, Internet Speed Monitor, dan Touch assistant. Folder ini masuk `.gitignore:17`, sehingga tidak ikut repo maupun APK.

**Kontributor.** Riwayat Git memuat enam identitas: Ellytra, EvGaz, Gao Zhan, GaoZhan, Noura Eliana, dan gaozhan906. Bisa jadi seluruhnya alias satu orang, bisa juga tidak; perlu konfirmasi.

**Kerahasiaan.** `sementara/ks.txt` memuat material signing sensitif dan masuk `.gitignore:12`. Isinya tidak dicatat dalam dokumen ini; keberadaannya hanya menjadi catatan risiko kerahasiaan terpisah dari pembahasan lisensi.

## C. Risiko hak cipta dan lisensi

Tanpa lisensi, kode tetap dilindungi hak cipta sejak dibuat, tetapi publik tidak menerima izin yang jelas. Di sisi lain, pernyataan "Silakan gunakan, modifikasi, fork, atau kustomisasi sesuai kebutuhan" di `README.md:68` dapat dipakai sebagai argumen adanya izin implied yang lebih luas dari niat pemilik.

Keterangan "sebagian besar pengembangan dibantu AI" belum menjelaskan penyedia alat dan ketentuan kepemilikan output. Informasi ini tidak dapat dipastikan dari repository.

Porting FBI bergantung pada identitas pengembang cabang FBI dan ada tidaknya kesepakatan internal. Jika cabang tersebut dikembangkan pihak lain, sebagian kode saat ini tidak dapat dilisensikan secara bebas oleh pemilik FTxT.

App Manager berangkat dari APK pihak ketiga. Copyright tidak melindungi ide, fungsi, algoritma, atau sistem. U.S. Copyright Office dalam `Circular 61`, `Circular 33`, dan laporan "Software-Enabled Consumer Products" menyatakan bahwa copyright tidak tersedia untuk ide, logika program, algoritma, sistem, metode, konsep, atau layout, dan secara eksplisit membolehkan pembacaan kode untuk mempelajari ide serta penulisan program baru dengan fungsi yang sama selama tidak menyalin baris kode. Meski demikian, kemiripan struktur, teks UI, atau aset dapat menjadi isu tersendiri; bukti saat ini tidak menunjukkan hal itu, tetapi tidak ada pernyataan risiko nol.

Dependency tidak boleh ikut diklaim sebagai karya FTxT. Saat lisensi baru diterapkan nanti, perlu dicek apakah masing-masing dependency mewajibkan atribusi atau NOTICE tambahan.

Aset dengan asal-usul belum diketahui sebaiknya tidak diberi pernyataan hak cipta sebelum dikonfirmasi.

## D. Perbandingan lisensi

**PolyForm Noncommercial 1.0.0.** Teks resmi: `https://polyformproject.org/licenses/noncommercial/1.0.0`. Hak diberikan untuk "any permitted purpose"; "any noncommercial purpose is a permitted purpose". Contoh yang disebut mencakup penggunaan personal untuk riset, eksperimen, pengujian, studi, hiburan, hobi, dan kegiatan keagamaan tanpa aplikasi komersial yang diharapkan. Organisasi non-komersial seperti badan amal, institusi pendidikan, organisasi riset publik, keselamatan/kesehatan publik, perlindungan lingkungan, dan institusi pemerintah termasuk tujuan yang diperbolehkan terlepas dari sumber pendanaan. Distribusi, perubahan, dan karya baru diizinkan untuk tujuan yang diperbolehkan. Lisensi ini juga memuat patent license, patent defense, kewajiban meneruskan terms atau Required Notice, serta kesempatan perbaikan 32 hari setelah pemberitahuan tertulis. Ini paling dekat dengan niat Anda. Kekurangannya: tidak mendefinisikan secara rinci iklan, freemium, donasi, APK berbayar, atau pemakaian internal korporasi; tidak memakai istilah "Derivative Work"; tidak memuat klausul eksplisit "ganti nama/refactor tetap turunan"; tidak memuat kalimat "implementasi independen diperbolehkan". Panduan resmi proyek mensyaratkan penghapusan seluruh penyebutan "PolyForm" dan `polyformproject.org` apabila teks lisensi diubah.

**Commons Clause v1.0 + lisensi dasar seperti Apache-2.0 atau MIT.** Teks resmi: `https://commonsclause.com/`. Klausul ini hanya mencabut hak "Sell", yaitu menyediakan kepada pihak ketiga untuk biaya atau imbalan suatu produk atau layanan yang nilainya seluruhnya atau substansial berasal dari fungsi software. FAQ resminya menyebut mengganti nama, mengganti nama API atau fungsi, dan sekadar menyajikan lewat SaaS tetap terlarang bila tidak ada nilai tambah substansial. Kekurangannya untuk kasus ini: klausul tersebut tidak melarang semua penggunaan komersial kode FTxT, hanya "penjualan" versi yang substansial sama. Jadi lebih longgar dari keinginan Anda dan bukan lisensi tunggal yang berdiri sendiri.

**CC BY-NC 4.0.** Teks resmi: `https://creativecommons.org/licenses/by-nc/4.0/legalcode`. NonCommercial didefinisikan sebagai "not primarily intended for or directed towards commercial advantage or monetary compensation", dengan kewajiban atribusi. Tidak disarankan untuk software: FAQ Commons Clause sendiri menyebut CC-NC bukan lisensi software dan lebih restriktif/ambig, sementara legal code CC tidak memberikan patent license. Kurang cocok dengan kebutuhan ini.

**Business Source License 1.1.** Teks resmi: `https://mariadb.com/bsl11/`. Non-production use dibolehkan; production use harus sesuai Additional Use Grant atau membeli lisensi; hak otomatis berubah ke Change License paling lambat pada tahun keempat. Tidak cocok karena Anda tidak ingin kode menjadi open source otomatis dan tidak ingin pembatasan hanya pada "production use".

**Server Side Public License 1.0.** Teks resmi: `https://www.mongodb.com/legal/licensing/server-side-public-license`. Ini copyleft kuat dengan kewajiban membuka source seluruh tumpukan bila fungsi program disediakan sebagai layanan. Tidak relevan untuk aplikasi overlay Android dan terlalu luas untuk tujuan ini.

**Lisensi custom.** Satu-satunya pilihan yang dapat memuat persis seluruh klausul yang diinginkan, tetapi risikonya tertinggi: ambiguitas, sulit ditegakkan, potensi benturan dengan prinsip bahwa ide tidak dapat dimonopoli, dan kemungkinan konflik dengan lisensi dependency. Sesuai instruksi pada tahap riset, draft lisensi custom belum disusun.

## E. Evaluasi tujuan lisensi

Tujuan Anda sebagian besar tercapai oleh PolyForm Noncommercial 1.0.0 tanpa modifikasi: source dapat dilihat dan dipelajari, penggunaan pribadi dan non-komersial dibolehkan, modifikasi serta fork non-komersial dibolehkan, dan ide tidak dikunci.

Bagian yang belum eksplisit: definisi "komersial" untuk iklan, donasi, langganan, penjualan APK/source, penggunaan internal perusahaan, dan pemakaian sebagai komponen aplikasi berbayar; definisi "kode turunan" dan "perubahan superficial"; serta pernyataan bahwa implementasi mandiri diperbolehkan.

Klausul implementasi independen layak ditambahkan sebagai klarifikasi, bukan sebagai larangan baru. Doktrin idea/expression dan penjelasan U.S. Copyright Office mendukung posisi tersebut: ide, fungsi, algoritma, dan cara kerja tidak dilindungi copyright; yang dilindungi adalah ekspresi kode. Klausul yang mencoba melarang orang membuat aplikasi serupa dari ide berisiko tidak efektif dan dapat terlihat sebagai upaya monopoli ide.

Klausul "mengganti nama variabel, package, UI, atau melakukan refactor tidak otomatis menjadi implementasi independen" sejalan dengan penilaian kemiripan kode. Kata "superficial" sebaiknya didefinisikan atau ditempatkan dalam FAQ/NOTICE pendamping, bukan dengan mengubah teks lisensi standar. Membuat lisensi sendiri tanpa bantuan hukum berisiko tinggi, terutama karena pemilik akan menjadi pihak yang harus membuktikan pelanggaran.

## F. Rekomendasi

Rekomendasi utama: pakai **PolyForm Noncommercial 1.0.0 apa adanya**, tambahkan `NOTICE` atau FAQ pendek berisi klarifikasi tanpa mengubah teks lisensi, lalu perbaiki `README.md:62-68` agar tidak lagi memberi kesan izin bebas. Alasannya: teks resminya sudah menyediakan skema source-available, hak modifikasi dan fork non-komersial, patent license, kewajiban notice, serta masa perbaikan pelanggaran; pembatasan komersialnya juga lebih kuat daripada Commons Clause.

Kelebihan: dekat dengan niat Anda, tidak perlu menulis lisensi dari nol, dan cakupan penggunaan non-komersialnya luas. Kekurangan: istilah "noncommercial" tetap perlu diperjelas dalam NOTICE, serta tidak ada definisi eksplisit tentang turunan dan perubahan superficial. Risikonya: perdebatan soal iklan atau donasi tetap mungkin, dan keputusan soal definisi komersial tetap diperlukan.

Alternatif: **Commons Clause + Apache-2.0** hanya jika Anda ternyata ingin mengizinkan penggunaan komersial dengan nilai tambah dan hanya melarang penjualan FTxT itu sendiri. Alternatif itu tidak sepenuhnya sesuai tujuan saat ini. **CC BY-NC**, **BSL 1.1**, dan **SSPL 1.0** tidak direkomendasikan.

## G. Pertanyaan yang perlu dikonfirmasi

1. Siapa pemegang hak atas cabang FBI, dan apakah pengembangannya dilakukan oleh Anda atau pihak lain? Apakah ada perjanjian atau catatan internal yang mendukungnya?
2. Apakah enam identitas Git tersebut semuanya alias Anda, atau ada kontributor nyata yang perlu dimintai persetujuan atau perjanjian kontribusi?
3. Siapa pemilik aset launcher PNG dan dua PNG di `_karantina/`, serta dari mana asalnya?
4. Apakah `sementara/ks.txt` sengaja disimpan lokal saja, dan apakah salinannya sudah diamankan dari backup atau lingkungan lain? Tidak ada tindakan yang disarankan sekarang; yang dibutuhkan hanya statusnya.
5. Penyedia AI apa yang dipakai untuk sebagian besar pengembangan, dan apa ketentuan kepemilikan outputnya?
6. Definisi "komersial" yang diinginkan: apakah iklan, donasi, langganan, APK berbayar, penjualan source, penggunaan internal perusahaan, dan pemakaian sebagai library di aplikasi berbayar semuanya dilarang?
7. Apakah organisasi non-komersial tetap dibolehkan memakai kode meski dananya berasal dari sumber komersial? PolyForm mengizinkan ini secara eksplisit.
8. Apakah lisensi baru akan diterapkan juga ke tag dan APK lama yang sudah dirilis, atau hanya ke versi setelah keputusan ini?
9. Saat lisensi diterapkan nanti, apakah README di `app/src/main/assets/` juga ikut diperbarui melalui `syncDocs`?

## H. Tahap selanjutnya

Belum ada kode, dependency, README, CHANGELOG, versionCode, atau file lisensi yang diubah. Setelah pertanyaan di bagian G dijawab dan arah lisensi dipilih, langkah yang dapat dikerjakan hanya setelah persetujuan eksplisit: menyusun draft `LICENSE`, `NOTICE`, dan revisi README yang konsisten, memeriksa kewajiban atribusi dependency, lalu melakukan self-check. Build, commit, tag, dan push tetap menunggu perintah terpisah.

## Sumber resmi

- PolyForm Noncommercial 1.0.0 — `https://polyformproject.org/licenses/noncommercial/1.0.0`
- The Commons Clause License Condition v1.0 — `https://commonsclause.com/`
- Creative Commons Attribution-NonCommercial 4.0 International Legal Code — `https://creativecommons.org/licenses/by-nc/4.0/legalcode`
- Business Source License 1.1 — `https://mariadb.com/bsl11/`
- Server Side Public License Version 1 — `https://www.mongodb.com/legal/licensing/server-side-public-license`
- U.S. Copyright Office, Circular 61 — `https://www.copyright.gov/circs/circ61.pdf`
- U.S. Copyright Office, Circular 33 — `https://www.copyright.gov/circs/circ33.pdf`
- U.S. Copyright Office, Computer Programs — `https://copyright.gov/register/tx-programs.html`
- U.S. Copyright Office, Software-Enabled Consumer Products — `https://copyright.gov/policy/software/software-full-report.pdf`
