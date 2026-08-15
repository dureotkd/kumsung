#!/bin/sh
set -eu

load_secret() {
  variable_name="$1"
  file_variable_name="${variable_name}_FILE"
  eval "secret_file=\${${file_variable_name}:-}"
  if [ -n "${secret_file}" ] && [ -f "${secret_file}" ]; then
    secret_value="$(cat "${secret_file}")"
    export "${variable_name}=${secret_value}"
    unset secret_value
  fi
}

load_secret MAIL_PASSWORD
load_secret DB_PASSWORD
load_secret ADMIN_PASSWORD

exec java -XX:MaxRAMPercentage=75 -jar /app/app.jar
