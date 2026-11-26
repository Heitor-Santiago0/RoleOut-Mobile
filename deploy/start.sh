#!/bin/sh
set -eu
: "${DB_URL:?Configure DB_URL before starting the public API}"
: "${DB_USER:?Configure DB_USER before starting the public API}"
: "${DB_PASSWORD:?Configure DB_PASSWORD before starting the public API}"
# Providers route HTTPS to this internal HTTP port.
port="${PORT:-8080}"
case "$port" in ''|*[!0-9]*) echo 'Invalid PORT' >&2; exit 1;; esac
if [ "$port" -lt 1024 ] || [ "$port" -gt 65535 ]; then
    echo 'PORT must be between 1024 and 65535' >&2; exit 1
fi
sed -i "s/port=\"8080\"/port=\"$port\"/" "$CATALINA_HOME/conf/server.xml"
exec catalina.sh run
