# agent-rules.md — Aturan Perilaku & Komunikasi Agent

Aturan ini mengatur **perilaku kerja** dan **cara komunikasi** agent di chat. Berlaku umum (lintas proyek), terpisah dari aturan teknis proyek yang ada di `AGENTS.md`.

---

## 1. Perilaku Kerja

**Lakukan:**
- Baca `AGENTS.md` (dan `agent-rules.md`) dulu sebelum mulai bekerja.
- Cek git status / log sebelum bertindak.
- Perubahan minimal & fokus — jangan menyentuh file di luar scope.
- Self-check sebelum selesai: `git diff`/file — sesuai permintaan, tidak ada yang terlewat.
- Jawab singkat & actionable.

**JANGAN:**
- Refactor tanpa diminta.
- Audit project tanpa diminta.
- Buat daftar "Next Steps" lalu mengeksekusinya sendiri — rencana menunggu persetujuan user.
- Revert tanpa konfirmasi. Verifikasi cepat via inspeksi (grep/diff). Build tidak dijalankan, ditawarkan, atau dimintakan konfirmasi kecuali user memberi perintah eksplisit untuk build.

---

## 2. Aturan Kerja Universal

1. **Kerjakan hanya setelah perintah eksplisit.**
2. **Diskusi belum selesai = JANGAN mengerjakan.**
3. **Tindakan destruktif/berisiko** (hapus, revert, pindah/potong isi, ubah banyak file sekaligus) **WAJIB konfirmasi eksplisit dulu**.
4. **Tanya hanya jika tidak bisa ditebak & berdampak** — scope tidak jelas, keputusan destruktif, atau instruksi bertentangan dengan kode.
5. **Hal kecil yang bisa ditafsirkan** (nama, warna, tata letak) → ambil yang paling konsisten dengan pola existing, kerjakan, sebutkan asumsinya.
6. **Selesai sesuai perintah, berhenti.**
7. **Build bukan langkah rutin.** Jangan menjalankan, menawarkan, atau meminta konfirmasi build; build hanya dijalankan jika user secara eksplisit memerintahkannya.
8. **Jangan menawarkan revert, build, saran, atau opsi tindakan apa pun yang tidak diminta.** Jangan menutup jawaban dengan pertanyaan penawaran ("mau saya revert?", "mau saya build?", "mau pakai yang mana?"). Kerjakan perintah, laporkan hasil, lalu diam. Konfirmasi hanya diberikan untuk tindakan destruktif yang sudah diperintahkan dan akan benar-benar dijalankan — bukan sebagai tawaran pilihan.

---

## 3. Environment Build

Nilai environment aktual **dihasilkan otomatis** oleh `_tools/env-setup.sh` ke blok di bawah. Jangan menjadikan path/versi di luar blok sebagai sumber kebenaran, dan jangan edit blok itu manual.

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
- Di awal sesi (atau setelah reset mesin), jalankan `bash _tools/env-setup.sh --check`.
- Jika `--check` melaporkan drift, jalankan `bash _tools/env-setup.sh --fix`. Ini aman/non-destruktif: menulis ulang blok `/etc/profile` (backup ke `/etc/profile.ftxt.bak`), `local.properties`, dan blok status di dokumen ini.
- Jika melaporkan JDK usang atau `/opt/gradle`, jalankan `bash _tools/env-setup.sh --prune` untuk menghapusnya. Ini **destruktif**, jadi hanya jalankan atas perintah eksplisit user (`AGENTS.md` §2).
- Setelah `--fix`/`--prune`, verifikasi: `bash -lc 'echo $JAVA_HOME; java -version; echo $ANDROID_HOME'`.
- `--check` keluar dengan kode 1 bila ada drift.

**Ketentuan per komponen (nilai aktual lihat blok status di atas):**
- **JDK:** hanya boleh ada satu, versi terbaru; `JAVA_HOME` di-set lewat blok `FTxT-ENV` di `/etc/profile`. Shell non-login (mis. `bash -c` dari tool agent) tidak membaca `/etc/profile`, jadi pakai path lengkap atau `source /etc/profile`.
- **Gradle:** hanya `./gradlew` (wrapper 8.13). JANGAN pakai `gradle` global; versi 6.1.1 tidak kompatibel JDK 21 dan dihapus lewat `--prune`. Bila cache wrapper kosong, `./gradlew` mengunduh dist sendiri.
- **Android SDK:** `ANDROID_HOME` di-set lewat blok `FTxT-ENV`. **Tidak ada override aapt2 di config project**; untuk build lokal aarch64 tambahkan properti CLI `-Pandroid.aapt2FromMavenOverride=/usr/bin/aapt2` (jangan hardcode di `gradle.properties` agar CI aman). Build-tools terbaru bisa saja aapt2 x86-64; cek blok status.
- **NDK & CMake (hanya bila native build):** jangan unduh versi lain; `--fix` memastikan `local.properties` (gitignored) menunjuk NDK/CMake terbaru yang terpasang di `/opt/ndk` dan `/opt/cmake`.
- **Build:** hanya dijalankan lewat `./gradlew` jika user secara eksplisit memerintahkan build. Jangan menawarkan build atau mengulang permintaan konfirmasi; ketentuan ini menggantikan bagian build di `AGENTS.md`.
- **Pembaca SQLite (DB):** jangan mengunduh tool sqlite3/python apa pun. `sqlite3`/`python3`/`node` tidak tersedia, binary `sqlite3` platform-tools bersifat x86-64, dan `apt-get install sqlite3` gagal 404. Gunakan `_tools/sqlite-jdbc/` (`sqlite-jdbc.jar` + `slf4j-api.jar` + `slf4j-nop.jar` + `Query.java`, read-only). Contoh:

  ```bash
  source /etc/profile   # shell non-login agent belum membaca /etc/profile
  cd _tools/sqlite-jdbc
  "$JAVA_HOME/bin/javac" -d /tmp/sqltool -cp sqlite-jdbc.jar Query.java
  "$JAVA_HOME/bin/java" -cp "/tmp/sqltool:sqlite-jdbc.jar:slf4j-api.jar:slf4j-nop.jar" \
    Query /path/ke/database.db "SELECT * FROM meta"
  ```

  Tanpa argumen SQL, `Query` menampilkan daftar tabel + jumlah baris. Selalu buka dengan URL `jdbc:sqlite:file:/path/db?mode=ro` agar file tidak berubah; verifikasi hash sebelum/sesudah bila perlu. `_tools/` tidak ikut build Gradle.
- **Decompile APK:** tool sudah tersedia di `_tools/`, JANGAN unduh ulang dari internet.

  ```bash
  source /etc/profile   # shell non-login agent belum membaca /etc/profile
  "$JAVA_HOME/bin/java" -jar _tools/apktool/apktool.jar d -f -o <outdir> <file.apk>
  _tools/jadx/bin/jadx -d <outdir> --no-res <file.apk>
  ```

  - `_tools/apktool/apktool.jar` — apktool 2.11.1 (resource, manifest, assets, smali).
  - `_tools/jadx/` — jadx 1.5.2 (source Java); butuh `JAVA_HOME` atau `java` di PATH — `source /etc/profile` sudah memenuhinya.
  - `--no-res` pada jadx dipakai bila resource sudah didecode apktool; hilangkan bila ingin resource ikut.

---

## 4. Catatan Komunikasi

- **Selalu beri kabar di tengah proses.** Jangan bekerja dalam diam: sebelum/langkah awal sebutkan sedang apa, saat berpindah tahap (mis. decode selesai, lanjut verifikasi) beri update singkat, dan jika ada langkah panjang atau error jelaskan saat itu juga — jangan menunggu sampai akhir.
- JANGAN gunakan tabel markdown di chat.
- DILARANG tulis laporan section (Accomplished, Next Steps, Ringkasan, dll) di chat — langsung ke inti.
- DILARANG menutup jawaban dengan tawaran tindakan atau pertanyaan balik yang tidak diminta (revert, build, saran, pilihan opsi).
- Bicaralah yang jelas, manusiawi & praktis namun akurat (saya programmer, bukan AI yang hafal seluruh ekosistem Android).
- Gunakan Bahasa Indonesia untuk thinking & respons.
