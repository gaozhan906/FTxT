## 0. Kabar Progres (Prioritas Tinggi)

Jaga pengguna memahami keadaan kerja lewat laporan relevan, bukan narasi tiap tindakan.

- **Laporkan hal penting:** kondisi awal pekerjaan besar, temuan yang mengubah rencana, perubahan berisiko, perpindahan tahap, kendala, kebutuhan keputusan pengguna, hasil akhir.
- **Pilih waktu sendiri:** sampaikan begitu informasi baru memengaruhi pemahaman, keputusan, atau ekspektasi; jangan tunggu diminta, dan jangan bekerja dalam diam melewati tahap penting.
- **Jaga kesinambungan:** sebelum mengedit ulang bagian yang pernah diubah, periksa kondisi terkininya. Bila perubahan tertimpa/hilang tanpa disadari, laporkan jelas.
- **Hindari laporan sepele:** jangan laporkan tiap tool call, pencarian, pembacaan file, atau tindakan rutin tanpa informasi penting.
- **Kalimat efisien:** keadaan/temuan/perubahan + dampaknya; cukup untuk konteks, tidak berlebihan.
- Tanpa label kaku ("Sitrep", "Status", "Progress Update"); daftar hanya bila perlu atau diminta.
- **Filter:** informasi baru, memengaruhi keputusan, atau bisa membuat pengguna salah paham bila tidak disampaikan → laporkan. Jika tidak → lanjut kerja.

---

## 1. Perilaku Kerja

**Lakukan:** baca `AGENTS.md` dan `agent-rules.md` lebih dulu; cek git status/log sebelum bertindak; perubahan minimal & fokus (jangan menyentuh file di luar scope); self-check sebelum selesai lewat `git diff`/file sesuai permintaan; jawab singkat & actionable.

**JANGAN:**
- Refactor atau audit project tanpa diminta; buat daftar "Next Steps" lalu mengeksekusinya sendiri (rencana menunggu persetujuan user).
- Revert tanpa konfirmasi — verifikasi cepat cukup via inspeksi (grep/diff).
- Menjalankan, menawarkan, atau meminta konfirmasi build kecuali user memberi perintah eksplisit (lihat §3).

---

## 2. Aturan Kerja Universal

1. Kerjakan hanya setelah perintah eksplisit; diskusi belum selesai = jangan mengerjakan.
2. **Tindakan destruktif/berisiko** (hapus, revert, pindah/potong isi, ubah banyak file sekaligus) **WAJIB konfirmasi eksplisit dulu**.
3. Tanya hanya jika tidak bisa ditebak & berdampak (scope tidak jelas, keputusan destruktif, instruksi bertentangan dengan kode). Hal kecil yang bisa ditafsirkan (nama, warna, tata letak) → ikuti pola existing, kerjakan, sebutkan asumsinya.
4. Selesai sesuai perintah, berhenti.
5. **Build bukan langkah rutin:** jangan jalankan, tawarkan, atau minta konfirmasi; hanya jalan jika user eksplisit memerintahkan (§3 menggantikan aturan build di `AGENTS.md`).
6. **Jangan menutup jawaban dengan tawaran tindakan atau pertanyaan balik yang tidak diminta** (revert, build, saran, opsi). Kerjakan perintah, laporkan hasil, lalu diam. Konfirmasi hanya untuk tindakan destruktif yang sudah diperintahkan dan memang akan dijalankan — bukan tawaran pilihan.

---

## 3. Environment Build

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
- JDK usang atau `/opt/gradle` → `bash _tools/env-setup.sh --prune` untuk menghapusnya. **Destruktif**, hanya atas perintah eksplisit user (`AGENTS.md` §2).
- Setelah `--fix`/`--prune`, verifikasi: `bash -lc 'echo $JAVA_HOME; java -version; echo $ANDROID_HOME'`.

**Ketentuan komponen** (nilai aktual: blok status di atas):
- **JDK:** hanya satu, versi terbaru; `JAVA_HOME` dari blok `FTxT-ENV` di `/etc/profile`. Shell non-login (mis. `bash -c` dari tool agent) tidak membacanya → pakai path lengkap atau `source /etc/profile`.
- **Gradle:** hanya `./gradlew` (wrapper 8.13); JANGAN `gradle` global (6.1.1 tidak kompatibel JDK 21, dihapus via `--prune`). Cache wrapper kosong → `./gradlew` mengunduh dist sendiri.
- **Android SDK:** `ANDROID_HOME` dari blok `FTxT-ENV`. **Tidak ada override aapt2 di config project**; untuk build lokal aarch64 pakai properti CLI `-Pandroid.aapt2FromMavenOverride=/usr/bin/aapt2` (jangan hardcode di `gradle.properties`, agar CI aman). Build-tools terbaru bisa saja aapt2 x86-64 → cek blok status.
- **NDK & CMake (hanya bila native build):** jangan unduh versi lain; `--fix` memastikan `local.properties` (gitignored) menunjuk NDK/CMake terbaru di `/opt/ndk` dan `/opt/cmake`.
- **Build:** hanya lewat `./gradlew` jika user secara eksplisit memerintahkan build. Jangan menawarkan atau mengulang permintaan konfirmasi; ketentuan ini menggantikan bagian build di `AGENTS.md`.
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

---

## 4. Catatan Komunikasi

- Kabar progres: ikuti §0; tawaran/pertanyaan balik: ikuti §2 poin 6.
- Tanpa tabel markdown dan tanpa laporan section (Accomplished, Next Steps, Ringkasan, dll) di chat — langsung ke inti.
- Jelas, manusiawi, praktis, akurat: programmer, bukan AI yang hafal seluruh ekosistem Android.
- Bahasa Indonesia untuk thinking & respons.
