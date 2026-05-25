#!/usr/bin/env bash
set -euo pipefail

# WildFly 起動前に CLI を offline 適用
$JBOSS_HOME/bin/jboss-cli.sh --file=/opt/jboss/cli/01-datasource-postgres.cli || true
$JBOSS_HOME/bin/jboss-cli.sh --file=/opt/jboss/cli/02-system-properties.cli || true
$JBOSS_HOME/bin/jboss-cli.sh --file=/opt/jboss/cli/03-logging.cli || true
$JBOSS_HOME/bin/jboss-cli.sh --file=/opt/jboss/cli/04-proxy-forwarding.cli || true

# 起動
exec $JBOSS_HOME/bin/standalone.sh -b 0.0.0.0 -bmanagement 0.0.0.0
