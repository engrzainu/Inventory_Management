#!/usr/bin/env bash
# Daily database backup for the online server. Keeps the newest 30. Run from the deploy folder.
# Add to cron, for example:  0 2 * * * cd /opt/inventory/deploy && ./backup.sh
cd "$(dirname "$0")" || exit 1
mkdir -p backups
FILE="backups/inventory-$(date +%F-%H%M).sql.gz"
docker compose exec -T db sh -c 'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --routines inventory_db' | gzip > "$FILE" \
  && echo "Saved $FILE" || { echo "Backup FAILED"; rm -f "$FILE"; exit 1; }
ls -1t backups/inventory-*.sql.gz | tail -n +31 | xargs -r rm --
