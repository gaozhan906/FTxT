Implementasi Fitur: Voltage Stability Monitor

Saya ingin menambahkan fitur baru bernama Voltage Stability Monitor ke aplikasi pemantau baterai.

Tujuan fitur ini adalah memantau dan menganalisis kestabilan tegangan baterai berdasarkan kondisi operasi perangkat, terutama ketika baterai sedang digunakan (Discharging) dan ketika beban dilepaskan (Idle).

1. Analisis kode yang sudah ada

Sebelum melakukan perubahan:

- Periksa arsitektur aplikasi saat ini.
- Identifikasi sistem pengambilan data BatteryManager.
- Periksa mekanisme pencatatan sampel Voltage, Current, Temperature, dan Battery Status.
- Periksa database SQLite dan struktur penyimpanannya.
- Gunakan kembali sistem pencatatan yang sudah tersedia jika memungkinkan.
- Jangan membuat sistem pengambilan data duplikat.
- Jangan mengubah atau merusak fitur yang sudah berjalan.

2. Klasifikasi kondisi baterai

Pisahkan analisis menjadi tiga kondisi:

A. Discharging

- Ketika perangkat menggunakan daya baterai.
- Rekam Voltage, Current, Temperature, dan Timestamp.
- Analisis perubahan tegangan terhadap perubahan arus.
- Deteksi Voltage Sag.

B. Idle / Load Recovery

- Ketika perangkat tidak sedang charging.
- Amati perubahan tegangan setelah beban penggunaan berkurang.
- Hitung Voltage Recovery berdasarkan perbedaan tegangan sebelum dan sesudah beban berkurang.
- Catat waktu yang diperlukan untuk pemulihan tegangan.

C. Charging

- Pisahkan dari analisis Discharging.
- Tetap rekam data tegangan dan arus untuk kemungkinan analisis charging di kemudian hari.
- Jangan mencampurkan data charging dengan perhitungan Voltage Sag atau Recovery saat discharge.

3. Parameter analisis

Sediakan parameter berikut:

- Voltage: tegangan baterai saat ini.
- Current: arus baterai saat ini.
- Voltage Drop: perubahan tegangan selama discharge.
- Voltage Recovery: kenaikan tegangan setelah beban berkurang.
- Voltage Variance: variasi tegangan dalam periode pengamatan.
- Recovery Time: waktu pemulihan tegangan.
- Estimated Effective Resistance: estimasi resistansi efektif berdasarkan perubahan tegangan dan arus.

Gunakan rumus dasar:

Voltage Drop:

ΔV = V_awal - V_akhir

Voltage Recovery:

ΔV_recovery = V_setelah - V_sebelum

Estimasi resistansi efektif:

R = ΔV / ΔI

Hasil resistansi harus ditampilkan dalam mΩ.

PENTING:

- Estimasi resistansi hanya boleh dilakukan ketika perubahan arus dan tegangan memenuhi kondisi pengukuran yang valid.
- Jangan menganggap setiap perubahan tegangan sebagai Voltage Sag.
- Pertimbangkan perubahan beban, temperatur, dan interval sampel.
- Jangan menyebut estimasi resistansi sebagai pengukuran resistansi internal baterai yang sesungguhnya.

4. Dashboard dan visualisasi

Buat halaman khusus bernama Voltage Stability Monitor.

Tampilkan:

1. Tegangan baterai saat ini.
2. Arus baterai saat ini.
3. Voltage Drop.
4. Voltage Recovery.
5. Recovery Time.
6. Estimated Effective Resistance.
7. Status kondisi pengukuran.

Sediakan grafik historis yang membedakan:

- Discharging.
- Idle / Recovery.
- Charging.

Gunakan warna grafik yang berbeda agar ketiga kondisi mudah dibedakan.

5. Sistem penyimpanan

- Periksa apakah tabel samples yang sudah ada dapat digunakan.
- Jika diperlukan tabel atau kolom baru, gunakan migrasi database yang aman.
- Jangan menghapus riwayat pengukuran lama.
- Simpan timestamp setiap pengukuran.
- Tangani data yang tidak tersedia atau tidak valid.
- Hindari pencatatan duplikat.

6. Validasi dan akurasi

Fitur harus mampu membedakan perubahan tegangan akibat perubahan beban dengan indikasi ketidakstabilan baterai.

Jangan memberikan diagnosis kerusakan baterai hanya berdasarkan tegangan rendah atau perubahan tegangan sesaat.

Jika data belum mencukupi, tampilkan status bahwa pengukuran belum tersedia atau belum valid.

7. Batasan pengerjaan

- Pertahankan arsitektur dan gaya kode aplikasi saat ini.
- Jangan melakukan refactoring pada bagian yang tidak berkaitan.
- Jangan menghapus atau mengubah fitur lama.
- Jangan mengubah algoritma estimasi kapasitas baterai yang sudah ada.
- Jangan membuat asumsi mengenai struktur kode sebelum memeriksanya.

8. Tahapan pengerjaan

Tahap pertama:
Lakukan inspeksi source code dan buat laporan singkat berisi:

1. Arsitektur implementasi yang sudah ada.
2. File yang perlu diubah.
3. File baru yang diperlukan.
4. Mekanisme pengambilan dan analisis data.
5. Rencana implementasi database dan UI.

Jangan langsung mengimplementasikan seluruh fitur sebelum menyampaikan hasil inspeksi dan rencana perubahan.

Setelah rencana disetujui, implementasikan secara bertahap dan pastikan aplikasi tetap dapat dikompilasi.