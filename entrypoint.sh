#!/bin/sh
set -e
envsubst < /app/hibernate.cfg.xml.template > /app/hibernate.cfg.xml
exec "$@"