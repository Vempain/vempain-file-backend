#!/bin/sh
# Entrypoint of the backend image: builds the private JVM truststore from the mounted admin certificate and starts the service as the
# runtime user (see below).
set -eu

# The service runs as VEMPAIN_UID:VEMPAIN_GID (default 6666:6666), chosen when the container is created so that the mounted host
# directories can be owned by an existing host user. Started as root (the stacks run the service with user "0:0") the entrypoint gives
# the vempain account of the image the requested ids and drops the privileges with su-exec; started as a non-root user (docker --user)
# it keeps that user.
uid="${VEMPAIN_UID:-6666}"
gid="${VEMPAIN_GID:-6666}"
case "${uid}${gid}" in
	*[!0-9]*)
		echo "VEMPAIN_UID and VEMPAIN_GID must be numeric, got ${uid}:${gid}" >&2
		exit 1
		;;
esac

# Renames nothing: the vempain account of the image takes the requested ids, so that the process has a user name and a home directory
adopt_vempain_ids() {
	sed -i "s/^vempain:x:[0-9]*:[0-9]*:/vempain:x:${uid}:${gid}:/" /etc/passwd
	sed -i "s/^vempain:x:[0-9]*:/vempain:x:${gid}:/" /etc/group
}

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
chmod 600 "${truststore}"

export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Djavax.net.ssl.trustStore=${truststore} -Djavax.net.ssl.trustStorePassword=${truststore_password} -Djavax.net.ssl.trustStoreType=PKCS12"

if [ "$(id -u)" -ne 0 ]; then
	# Started as a fixed non-root user: there are no privileges to drop, the service runs as that user
	exec java "$@"
fi

adopt_vempain_ids
chown "${uid}:${gid}" "${truststore}"
exec su-exec "${uid}:${gid}" java "$@"
