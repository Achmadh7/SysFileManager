# SysFileManager tidak memakai refleksi/serialisasi khusus; aturan bawaan cukup.
# Coil membawa aturan konsumennya sendiri di dalam AAR.

# Perpustakaan arsip (Commons Compress, XZ, zip4j): cegah penyusutan nama yang mereka andalkan.
-keep class org.apache.commons.compress.** { *; }
-keep class org.tukaani.xz.** { *; }
-keep class net.lingala.zip4j.** { *; }
-dontwarn org.apache.commons.compress.**
-dontwarn org.tukaani.xz.**
-dontwarn net.lingala.zip4j.**
