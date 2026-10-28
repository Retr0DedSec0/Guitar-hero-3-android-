#!/bin/bash
# Gera um APK de teste SEM Gradle, usando só ferramentas do Ubuntu (aapt, dx, zipalign, apksigner) + kotlinc.
# Requer: kotlinc, aapt, apksigner, zipalign, dalvik-exchange, libandroid-23-java.
set -euo pipefail
cd "$(dirname "$0")"
KOTLINC=${KOTLINC:-kotlinc}
KLIB=${KLIB:-$(dirname "$(readlink -f "$(command -v $KOTLINC)")")/../lib}
ANDROID_JAR=/usr/lib/android-sdk/platforms/android-23/android.jar
DX="java -jar /usr/share/java/com.android.dx.jar"
OUT=build; rm -rf $OUT; mkdir -p $OUT/classes

echo "[1/5] compilando Kotlin"
$KOTLINC -cp "$ANDROID_JAR" -jvm-target 1.8 -Xlambdas=class -Xsam-conversions=class -nowarn \
  iso/IsoSource.kt iso/XisoReader.kt iso/GameExtractor.kt iso/AndroidIsoSource.kt app/MainActivity.kt \
  -d $OUT/classes

echo "[2/5] convertendo para dex"
# O dx não entende META-INF/versions/9/module-info.class do stdlib; tiramos o META-INF da cópia.
python3 - "$KLIB/kotlin-stdlib.jar" $OUT/stdlib-clean.jar <<'PY'
import sys, zipfile
src, dst = sys.argv[1:3]
with zipfile.ZipFile(src) as zi, zipfile.ZipFile(dst, "w", zipfile.ZIP_DEFLATED) as zo:
    for n in zi.namelist():
        if n.endswith(".class") and not n.startswith("META-INF/") and n != "module-info.class":
            zo.writestr(n, zi.read(n))
PY
$DX --dex --min-sdk-version=26 --output=$OUT/classes.dex $OUT/classes $OUT/stdlib-clean.jar

echo "[3/5] empacotando"
aapt package -f -M app/AndroidManifest.xml -I "$ANDROID_JAR" -F $OUT/unsigned.apk
( cd $OUT && aapt add -f unsigned.apk classes.dex >/dev/null )

echo "[4/5] alinhando"
zipalign -f -p 4 $OUT/unsigned.apk $OUT/aligned.apk

echo "[5/5] assinando (chave de teste)"
[ -f $OUT/../debug.keystore ] || keytool -genkeypair -keystore debug.keystore -storepass android -keypass android \
  -alias debug -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=GH3 Launcher Debug" >/dev/null 2>&1
apksigner sign --ks debug.keystore --ks-pass pass:android --key-pass pass:android --out gh3-launcher-teste.apk $OUT/aligned.apk
apksigner verify --verbose gh3-launcher-teste.apk | head -5
ls -la gh3-launcher-teste.apk
