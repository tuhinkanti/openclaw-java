#!/bin/bash
set -e

# Import any custom CA certs mounted at /certs/*.crt into the JVM truststore.
# Handles both single-cert PEM files and multi-cert PEM bundles.
CACERTS="${JAVA_HOME}/lib/security/cacerts"
if [ -d /certs ] && ls /certs/*.crt 2>/dev/null 1>/dev/null; then
  for bundle in /certs/*.crt; do
    csplit -z -f /tmp/cert- -b '%03d.pem' "$bundle" \
      '/-----BEGIN CERTIFICATE-----/' '{*}' 2>/dev/null 1>/dev/null || true
    for pem in /tmp/cert-*.pem; do
      [ -f "$pem" ] || continue
      alias="imported-$(basename "$bundle" .crt)-$i"
      keytool -importcert -noprompt \
        -keystore "$CACERTS" \
        -storepass changeit \
        -alias "$alias" \
        -file "$pem" 2>/dev/null || true
      rm -f "$pem"
      i=$((i+1))
    done
  done
fi

exec java -Duser.home=/home/openclaw -jar /app/openclaw.jar "$@"
