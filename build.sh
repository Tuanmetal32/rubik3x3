#!/bin/bash
# Build Bee_Rubik.apk không cần Gradle: aapt2 -> javac -> d8 -> zipalign -> apksigner
set -e
cd "$(dirname "$0")"
TOOLS=${TOOLS:-$HOME/android-build}
export JAVA_HOME=$(ls -d $TOOLS/jdk-17*)
export PATH=$JAVA_HOME/bin:$PATH
BT=$TOOLS/sdk/build-tools/34.0.0
JAR=$TOOLS/sdk/platforms/android-34/android.jar
rm -rf build && mkdir -p build/gen build/classes build/dex
$BT/aapt2 compile --dir res -o build/res.zip
$BT/aapt2 link -o build/base.apk -I $JAR --manifest AndroidManifest.xml --java build/gen \
  --min-sdk-version 21 --target-sdk-version 34 --version-code 1 --version-name 1.0 build/res.zip
javac -encoding UTF-8 -source 8 -target 8 -nowarn -Xlint:-options -bootclasspath $JAR:$BT/core-lambda-stubs.jar -d build/classes \
  $(find src build/gen -name '*.java')
$BT/d8 --release --min-api 21 --lib $JAR --output build/dex $(find build/classes -name '*.class')
python3 -c "
import zipfile,shutil
shutil.copy('build/base.apk','build/unsigned.apk')
z=zipfile.ZipFile('build/unsigned.apk','a',zipfile.ZIP_DEFLATED); z.write('build/dex/classes.dex','classes.dex'); z.close()"
$BT/zipalign -f -p 4 build/unsigned.apk build/aligned.apk
[ -f bee.keystore ] || keytool -genkeypair -keystore bee.keystore -alias bee -keyalg RSA -keysize 2048 \
  -validity 10000 -storepass beerubik -keypass beerubik -dname "CN=Bee_Rubik" >/dev/null 2>&1
$BT/apksigner sign --ks bee.keystore --ks-pass pass:beerubik --key-pass pass:beerubik --out Bee_Rubik.apk build/aligned.apk
$BT/apksigner verify Bee_Rubik.apk && ls -la Bee_Rubik.apk
