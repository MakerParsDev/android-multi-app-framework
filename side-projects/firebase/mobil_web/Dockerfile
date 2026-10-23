FROM nginxinc/nginx-unprivileged:1.30-alpine

COPY docker/nginx.conf /etc/nginx/conf.d/default.conf
COPY public /usr/share/nginx/html


HEALTHCHECK --interval=30s --timeout=3s --start-period=15s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/ || exit 1
