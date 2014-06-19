#!/bin/sh

# http://blog.crazybob.org/2010/02/android-trusting-ssl-certificates.html
echo | openssl s_client -connect localhost:8443 2>&1 | \
	sed -ne '/-BEGIN CERTIFICATE-/,/-END CERTIFICATE-/p' > mycert.pem

export CLASSPATH=bcprov-jdk15on-146.jar
CERTSTORE=../res/raw/kayateia.bks
if [ -a $CERTSTORE ]; then
    rm $CERTSTORE || exit 1
fi

keytool \
      -import \
      -v \
      -trustcacerts \
      -alias 0 \
      -file mycert.pem \
      -keystore $CERTSTORE \
      -storetype BKS \
      -provider org.bouncycastle.jce.provider.BouncyCastleProvider \
      -providerpath /usr/share/java/bcprov.jar \
      -storepass storepass

