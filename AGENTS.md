# AGENTS.md — Aturan AI Project FTxT

> Baca ulang file ini di awal sesi. Eksekusi hanya setelah perintah eksplisit; jangan commit/tag/push tanpa perintah; tindakan destruktif wajib konfirmasi.

## 0. Kabar Progres (Prioritas Tinggi)

Jaga pengguna memahami keadaan kerja lewat laporan relevan, bukan narasi tiap tindakan. Kabar ini **wajib**, bukan tambahan opsional: pengguna mengawasi jalannya kerja, bukan hanya hasil akhir.

### Wajib dikabar segera, sebelum lanjut

- **Arah/keputusan baru:** begitu instruksi mengubah rencana yang sudah berjalan, sampaikan pemahaman + arah baru + cakupan yang terkena (contoh: bottom nav yang semula tidak ditiru lalu diminta dipakai).
- **Temuan yang menyentuh kode:** asumsi yang ternyata salah (kelas/API/konstanta tidak ada, pola modul berbeda, file hilang di luar perintah) → laporkan temuan, dampak, dan tindakan sebelum mengubah banyak file.
- **Risiko regresi:** sebelum atau saat menimpa logika lama, sebut bagian yang bisa berubah; bandingkan dengan `HEAD`/kondisi sebelumnya, lalu laporkan bila ada perilaku lama yang perlu dikembalikan.
- **Perpindahan tahap:** analisis → aset → layout → wiring Java → dokumen/versi → verifikasi; tiap perpindahan cukup satu kalimat.
- **Kendala dan kegagalan:** error build/test, tool tidak tersedia, atau verifikasi gagal → laporkan error ringkas + dugaan penyebab + rencana, lalu laporkan hasil perbaikannya. Jangan memperbaiki sambil diam.
- **Kebutuhan keputusan/blokir:** bila ada pilihan berdampak yang tidak bisa ditebak, tanya sesuai §1.3; bila tidak ada, sebut asumsi dan lanjut.
- **Kondisi awal dan hasil akhir:** ringkas kondisi saat menerima pekerjaan besar dan kondisi akhir saat selesai.

### Cara menyampaikan

- Sisipkan satu pesan teks di antara tool calls saat memasuki tahap baru atau menemukan hal di atas; jangan menimbun sampai akhir.
- Urutan: **keadaan/temuan → dampak → tindakan/keputusan**. Tambahkan pertanyaan hanya bila benar-benar diperlukan.
- Kalimat efisien dan manusiawi; cukup untuk konteks, tidak berlebihan. Daftar hanya bila perlu.
- Tanpa label atau embel-embel di awal kalimat ("Sitrep", "Status", "Progress Update", "Kabar:"); tanpa tabel markdown di chat.
- Sebelum mengedit ulang bagian yang pernah disentuh, periksa kondisi terkininya. Bila perubahan tertimpa/hilang tanpa disadari, laporkan jelas.
- Kabar bukan permisi: aturan commit/push/tag/build/destruktif di §1 dan §1b tetap berlaku.
- Hindari laporan sepele: jangan laporkan tiap tool call, pencarian, pembacaan file, atau tindakan rutin tanpa informasi penting.
- **Filter akhir:** informasi baru, mengubah keputusan/ekspektasi, atau bisa membuat pengguna salah paham bila tidak disampaikan → laporkan. Jika tidak → lanjut kerja.

### Kenapa wajib dipatuhi

- Pengguna tidak melihat tool call maupun layar kerja; tanpa kabar, mereka tidak bisa mengawasi arah, menangkap asumsi yang melenceng, atau menghentikan pekerjaan sebelum terlalu jauh.
- Kesalahan yang baru ketemu di akhir menumpuk: biaya perbaikannya naik, perubahan lama dan baru sulit dibedakan, dan regresi fitur yang sudah ada lebih mudah lolos (contoh kasus: konstanta filter, `AppShareHelper`, reload `onResume`).
- Kabar di tengah memberi pengguna kesempatan mengoreksi sebelum agent mengubah banyak file; ini mekanisme pengaman, bukan sekadar etiket.
- Mencegah agent bekerja diam melewati tahap penting, mengabaikan aturan §1, atau mengira pekerjaan sudah sesuai padahal arahnya berubah.
- Hasil akhir yang benar tidak cukup jika prosesnya tidak bisa diawasi; kepercayaan kerja bergantung pada kabar yang tepat waktu.

## 1. Perilaku Kerja & Aturan Universal

**Lakukan:** baca file ini lebih dulu; cek git status/log sebelum bertindak; perubahan minimal & fokus (jangan menyentuh file di luar scope); self-check sebelum selesai lewat `git diff`/file sesuai permintaan; jawab singkat & actionable.

**JANGAN:**
- Refactor atau audit project tanpa diminta; buat daftar "Next Steps" lalu mengeksekusinya sendiri (rencana menunggu persetujuan user).
- Revert tanpa konfirmasi — verifikasi cepat cukup via inspeksi (grep/diff).
- Menjalankan, menawarkan, atau meminta konfirmasi build kecuali user memberi perintah eksplisit (lihat §1b).

1. Kerjakan hanya setelah perintah eksplisit; diskusi belum selesai = jangan mengerjakan.
2. **Tindakan destruktif/berisiko** (hapus, revert, pindah/potong isi, ubah banyak file sekaligus) **WAJIB konfirmasi eksplisit dulu**.
3. Tanya hanya jika tidak bisa ditebak & berdampak (scope tidak jelas, keputusan destruktif, instruksi bertentangan dengan kode). Hal kecil yang bisa ditafsirkan (nama, warna, tata letak) → ikuti pola existing, kerjakan, sebutkan asumsinya.
4. Selesai sesuai perintah, berhenti.
5. **Build bukan langkah rutin:** jangan jalankan, tawarkan, atau minta konfirmasi; hanya jalan jika user eksplisit memerintahkan (lihat §1b).
6. **Jangan menutup jawaban dengan tawaran tindakan atau pertanyaan balik yang tidak diminta** (revert, build, saran, opsi). Kerjakan perintah, laporkan hasil, lalu diam. Konfirmasi hanya untuk tindakan destruktif yang sudah diperintahkan dan memang akan dijalankan — bukan tawaran pilihan.

## 1b. Environment build

Nilai environment aktual **dihasilkan otomatis** oleh `_tools/env-setup.sh` ke blok di bawah. Jangan menjadikan path/versi di luar blok sebagai sumber kebenaran; jangan edit blok itu manual.

<!-- BEGIN FTxT-ENV-STATE (dihasilkan _tools/env-setup.sh; jangan edit manual) -->
- **JDK:** `jdk-21.0.13+9` (OpenJDK `21.0.13`) di `/opt/java/jdk-21.0.13+9`
- **Gradle:** wrapper `8.13` (hanya `./gradlew`); `/opt/gradle` tidak ada
- **Android SDK:** `/opt/android_sdk` — build-tools: 29.0.3, 35.0.0; platforms: android-29, android-35; `compileSdk=35`
- **aapt2:** `/usr/bin/aapt2` aarch64 (2.19-250217V908); aapt2 di build-tools terbaru: x86-64/other
- **NDK:** `29.0.14206865` di `/opt/ndk/android-ndk-r29`
- **CMake:** `3.30.3` di `/opt/cmake/cmake-3.30.3`
- **SQLite reader:** `_tools/sqlite-jdbc` lengkap (sqlite-jdbc `3.45.3.0`)
<!-- END FTxT-ENV-STATE -->

**Prosedur wajib:**
- Di awal sesi atau setelah reset mesin: `bash _tools/env-setup.sh --check` (keluar kode 1 bila ada drift).
- Drift → `bash _tools/env-setup.sh --fix`. Aman/non-destruktif: menulis ulang blok `/etc/profile` (backup `/etc/profile.ftxt.bak`), `local.properties`, dan blok status di dokumen ini.
- JDK usang atau `/opt/gradle` → `bash _tools/env-setup.sh --prune` untuk menghapusnya. **Destruktif**, hanya atas perintah eksplisit user (§1 poin 2).
- Setelah `--fix`/`--prune`, verifikasi: `bash -lc 'echo $JAVA_HOME; java -version; echo $ANDROID_HOME'`.

**Ketentuan komponen** (nilai aktual: blok status di atas):
- **JDK:** hanya satu, versi terbaru; `JAVA_HOME` dari blok `FTxT-ENV` di `/etc/profile`. Shell non-login (mis. `bash -c` dari tool agent) tidak membacanya → pakai path lengkap atau `source /etc/profile`.
- **Gradle:** hanya `./gradlew` (wrapper 8.13); JANGAN `gradle` global (6.1.1 tidak kompatibel JDK 21, dihapus via `--prune`). Cache wrapper kosong → `./gradlew` mengunduh dist sendiri.
- **Android SDK:** `ANDROID_HOME` dari blok `FTxT-ENV`. **Tidak ada override aapt2 di config project**; untuk build lokal aarch64 pakai properti CLI `-Pandroid.aapt2FromMavenOverride=/usr/bin/aapt2` (jangan hardcode di `gradle.properties`, agar CI aman). Build-tools terbaru bisa saja aapt2 x86-64 → cek blok status.
- **NDK & CMake (hanya bila native build):** jangan unduh versi lain; `--fix` memastikan `local.properties` (gitignored) menunjuk NDK/CMake terbaru di `/opt/ndk` dan `/opt/cmake`.
- **Build:** hanya lewat `./gradlew` jika user secara eksplisit memerintahkan build. Jangan menawarkan atau mengulang permintaan konfirmasi (lihat §1 poin 5).
- **Pembaca SQLite (DB):** jangan unduh tool sqlite3/python. `sqlite3`/`python3`/`node` tidak tersedia, binary sqlite3 platform-tools x86-64, `apt-get install sqlite3` gagal 404. Gunakan `_tools/sqlite-jdbc/` (`sqlite-jdbc.jar` + `slf4j-api.jar` + `slf4j-nop.jar` + `Query.java`, read-only). Contoh:

  ```bash
  source /etc/profile   # shell non-login agent belum membaca /etc/profile
  cd _tools/sqlite-jdbc
  "$JAVA_HOME/bin/javac" -d /tmp/sqltool -cp sqlite-jdbc.jar Query.java
  "$JAVA_HOME/bin/java" -cp "/tmp/sqltool:sqlite-jdbc.jar:slf4j-api.jar:slf4j-nop.jar" \
    Query /path/ke/database.db "SELECT * FROM meta"
  ```

  Tanpa argumen SQL, `Query` menampilkan daftar tabel + jumlah baris. Selalu buka URL `jdbc:sqlite:file:/path/db?mode=ro` agar file tidak berubah; verifikasi hash sebelum/sesudah bila perlu. `_tools/` tidak ikut build Gradle.
- **Decompile APK:** tool sudah tersedia di `_tools/`, JANGAN unduh ulang dari internet.

  ```bash
  source /etc/profile   # shell non-login agent belum membaca /etc/profile
  "$JAVA_HOME/bin/java" -jar _tools/apktool/apktool.jar d -f -o <outdir> <file.apk>
  _tools/jadx/bin/jadx -d <outdir> --no-res <file.apk>
  ```

  - `_tools/apktool/apktool.jar` — apktool 2.11.1 (resource, manifest, assets, smali).
  - `_tools/jadx/` — jadx 1.5.2 (source Java); butuh `JAVA_HOME` atau `java` di PATH (`source /etc/profile` cukup).
  - `--no-res` dipakai bila resource sudah didecode apktool; hilangkan bila ingin resource ikut.

## 1c. Versioning (`app/build.gradle`)

Semver `major.minor.patch`:

- **major** — milestone/arsitektur/breaking change → `minor=0, patch=0`
- **minor** — fitur baru/dihapus/dipulihkan → `patch=0`
- **patch** — bugfix/optimasi/maintenance

## 2. CHANGELOG

### 2.1 Entry & judul

- Satu entry untuk versi berjalan, bukan per file; CHANGELOG berisi poin perubahan, bukan detail file. Urutan wajib: `🔖 Deskripsi` → `✨ Fitur Baru` (baru) → `🚮 Fitur Dihapus` (dihapus/dinonaktifkan) → `♻️ Perubahan Fitur` (rombakan existing) → `🔧 Optimasi & Penyesuaian` (kecil) → `🐞 Bug Fixes` → `💡 Memo`.
- Judul: `# [major.minor.patch] yyyy/MM/dd HH:mm WITA versionCode ***STATUS***`, mis. `# [4.90.0] 2026/08/28 05:00 WITA 188 ***ONGOING***`. Jam 24 jam, WITA, dari `TZ=Asia/Makassar date`. Cek git log dulu; jangan buat entry sebelum versi sebelumnya di-push.
- STATUS: `***ONGOING***` — berjalan, tetap dipakai setelah push; `***PUSH***` — ditutup saat commit+push tanpa tag; `***RELEASE***` — commit+tag, langsung menutup entry.

### 2.1b Kategori section

- Section mengikuti **natur perubahan**, bukan status; tulis langsung, jangan ditunda sampai rilis. Status tidak menentukan section.
- Satu perubahan = satu poin hasil akhir (kondisi final), bukan jejak tahapan. Saat merapikan entry lama, poin boleh dipindah antar section atau digabung tanpa mengubah fakta.

### 2.2 Waktu judul

- Waktu judul = saat seluruh perubahan entry selesai; ditetapkan di akhir setelah poin dirampungkan, diperbarui pada iterasi berikutnya yang menyentuh entry sama. Jangan update per poin/edit.

### 2.3 Alur penulisan

1. Kerjakan seluruh perubahan dulu; tidak perlu mencatat per file.
2. Setelah selesai, rampungkan isi entry, lalu tetapkan tanggal & jam judul. Jangan menulis deskripsi per tahap kerja.

### 2.3b VersionCode

- +1 setiap entry `ONGOING` diperbarui; +1 lagi saat status berubah ke `PUSH`/`RELEASE`. Push yang tidak menutup entry tidak menaikkan versionCode. Hanya versionCode yang naik; versionName mengikuti aturan versi (§1c).

### 2.4 Isi entry

- `🔖`: 1–2 kalimat pengantar tema versi (boleh menyebut teknik/struktur untuk arah), tanpa mengulang detail section; pola baiknya kalimat inti + ajakan membaca section.
- Poin: satu baris pendek, sudut pandang user (nama fitur & perilaku), bukan kelas/method/tabel/parameter/angka internal — kecuali memengaruhi perilaku user.
- Jangan menumpuk poin yang saling menimpa; perbarui/tulis ulang, lalu cek konsistensi (tidak boleh bertentangan/duplikat).

### 2.5 Entry yang di-merge

- Pindah seluruhnya ke `app/src/main/assets/old-CHANGELOG.md` (dibaca aplikasi di Dokumentasi; tidak ada salinan di root). Koreksi versi lama diedit langsung di sana — pengecualian eksplisit dari aturan §2.8.

### 2.6 Verifikasi

- Wajib cek git log untuk status commit; wajib ambil waktu via `TZ=Asia/Makassar date` (jangan asal pilih).

### 2.7 Dilarang dicatat di CHANGELOG

- Build system: `build.gradle` (root/app/`shared/*`), `settings.gradle`, `gradle.properties`, `gradle/wrapper/`, `gradlew*`, `proguard-rules.pro`, `build/`.
- CI/signing: `.github/workflows/`, `keystore.properties`, `key/`.
- Root/internal: `AGENTS.md`, `README.md`, `CHANGELOG.md`, `PANDUAN.md`, `STRUKTUR.md`, `_schedule/`, `_temp/`, `local.properties`.
- Topik yang lahir dari situ (build/CI/signing/rilis, bump version, restrukturisasi internal/ganti import) juga tidak diumbar.

### 2.8 Sumber dokumen

- Edit dokumen hanya di root (`README.md`, `CHANGELOG.md`, `PANDUAN.md`, `STRUKTUR.md`). Salinan di `assets/` ditimpa `syncDocs` saat build — jangan disentuh manual.

## 3. Workflow

- **Edit biasa:** update kode → rampungkan entry + naikkan versionCode → self-check (`git diff`) → jangan commit/tag/push; ulang sampai perintah commit.
- **Pre-release:** perbarui Last Updated dokumen terkait, rapikan poin CHANGELOG (§2.1b, §2.4), pastikan `ONGOING` + waktu WITA terbaru, bilang siap; tetap jangan commit/tag/push.
- **Commit (jika diperintah):** `git add -A && git commit -m "vX.X.X deskripsi"`; status tetap `ONGOING` kecuali perintah penutupan/tag; jangan push/tag.
- **Push (jika diperintah):** status tetap `ONGOING`, +1 versionCode bila entry diperbarui, commit lalu push.
- **Tag (jika diperintah):** status → `RELEASE`, +1 versionCode, perbarui waktu, commit + `git tag vX.X.X` (push bila diminta); entry langsung tutup, buat entry baru `ONGOING`.
- **Tutup entry (jika diperintah):** status → `PUSH`, +1 versionCode, perbarui waktu, commit + push; segera buat entry baru teratas `ONGOING` mengikuti format judul yang ada (`[x.xx.x]` dulu, disesuaikan saat ada perubahan).
- **Setelah `RELEASE`:** buat entry baru `ONGOING` teratas dengan format judul sama, lalu kembali ke edit biasa.

## 4. Catatan Komunikasi

- Kabar progres: ikuti §0; tawaran/pertanyaan balik: ikuti §1 poin 6.
- Tanpa tabel markdown dan tanpa laporan section (Accomplished, Next Steps, Ringkasan, dll) di chat — langsung ke inti.
- Jelas, manusiawi, praktis, akurat: programmer, bukan AI yang hafal seluruh ekosistem Android.
- Bahasa Indonesia untuk thinking & respons.
