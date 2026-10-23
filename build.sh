#!/data/data/com.termux/files/usr/bin/bash
set -e

PREFIX=/data/data/com.termux/files/usr
ANDROID_JAR=$HOME/android-sdk/android.jar
BUILD=build
PROJECT_NAME=$(basename $(pwd))
rm -rf $BUILD
mkdir -p $BUILD/gen $BUILD/classes $BUILD/apk

echo "[1/5] Компиляция ресурсов..."
aapt2 compile --dir res -o $BUILD/res.zip

echo "[2/5] Линковка ресурсов..."
aapt2 link -o $BUILD/base.apk -I $ANDROID_JAR \
    --manifest AndroidManifest.xml \
    $BUILD/res.zip \
    --java $BUILD/gen \
    --auto-add-overlay

echo "[3/5] Компиляция Java..."
javac -source 8 -target 8 \
    -bootclasspath $ANDROID_JAR \
    -classpath $BUILD/gen \
    -d $BUILD/classes \
    $(find src -name "*.java") \
    $(find $BUILD/gen -name "*.java")

echo "[4/5] Конвертация в dex..."
d8 --lib $ANDROID_JAR --output $BUILD/ \
    $(find $BUILD/classes -name "*.class")

echo "[5/5] Упаковка и подпись APK..."
cd $BUILD
cp base.apk app-unsigned.apk
zip -uj app-unsigned.apk classes.dex
apksigner sign --ks ~/.keystore --ks-pass pass:android123 \
    --out app.apk app-unsigned.apk 2>/dev/null || {
    keytool -genkeypair -keystore ~/.keystore -alias androiddebugkey \
        -storepass android123 -keypass android123 -keyalg RSA -keysize 2048 \
        -validity 10000 -dname "CN=Debug,O=Android,C=US"
    apksigner sign --ks ~/.keystore --ks-pass pass:android123 \
        --out app.apk app-unsigned.apk
}
cd ..

# Копируем готовый файл в общую папку APKs с именем проекта
cp $BUILD/app.apk ~/storage/shared/APKs/${PROJECT_NAME}.apk

echo "✅ Готово: $BUILD/app.apk"
echo "📲 Скопировано в память телефона: папка APKs / ${PROJECT_NAME}.apk"
