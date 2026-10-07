#!/bin/sh
set -eu

if [ "$(id -u)" -ne 0 ]; then
	echo "container-entrypoint.sh must start as root so the runtime truststore can be created" >&2
	exit 1
fi

admin_certificate="${VEMPAIN_ADMIN_CERTIFICATE:-/certs/admin-backend.crt}"
truststore="${VEMPAIN_TRUSTSTORE:-/tmp/vempain-truststore.p12}"
truststore_password="${VEMPAIN_TRUSTSTORE_PASSWORD:-changeit}"
java_home="${JAVA_HOME:-/opt/java/openjdk}"

if [ ! -r "${admin_certificate}" ]; then
	echo "Admin certificate is missing or unreadable: ${admin_certificate}" >&2
	exit 1
fi

rm -f "${truststore}"
keytool -importkeystore -noprompt \
	-srckeystore "${java_home}/lib/security/cacerts" \
	-srcstorepass changeit \
	-destkeystore "${truststore}" \
	-deststoretype PKCS12 \
	-deststorepass "${truststore_password}"
keytool -importcert -noprompt \
	-alias vempain-admin-backend \
	-file "${admin_certificate}" \
	-keystore "${truststore}" \
	-storepass "${truststore_password}"
chown vempain:vempain "${truststore}"
chmod 600 "${truststore}"

export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Djavax.net.ssl.trustStore=${truststore} -Djavax.net.ssl.trustStorePassword=${truststore_password} -Djavax.net.ssl.trustStoreType=PKCS12"

exec su-exec vempain java "$@"
