#!/usr/bin/dumb-init /bin/sh
set -e

function prepend() {
  while read line; do echo "${1}${line}"; done;
}

function wait_for_port() {
  while ! nc -z localhost $1; do
    sleep 1 # every 1 second
  done
}

function curl_with_retries() {
  curl --connect-timeout 5 --max-time 1 --retry 25 "$@"
}

# start EC
START_ARGUMENTS=""
CONFIG_FILE=/etc/envoy-control/application.yaml
if [ -f "$CONFIG_FILE" ]; then
    START_ARGUMENTS="--spring.config.location=file:$CONFIG_FILE "
fi
if [ ! -z "${ENVOY_CONTROL_PROPERTIES}" ]; then
    START_ARGUMENTS="$START_ARGUMENTS $ENVOY_CONTROL_PROPERTIES"
fi
echo "Launching Envoy-control with $START_ARGUMENTS"
wait_for_port 8500 && /bin/envoy-control/envoy-control-runner/bin/envoy-control-runner $START_ARGUMENTS | prepend "ec1: " &

# start envoys
wait_for_port 8080 && \
 ( /usr/local/bin/envoy --base-id 1 --log-path /dev/stdout -c /etc/envoy1.yaml | prepend "envoy1: ") & \
 ( /usr/local/bin/envoy --base-id 2 --log-path /dev/stdout -c /etc/envoy2.yaml | prepend "envoy2: ") &

# run consul
consul agent -server -ui -ui-content-path "/consul/ui" -dev -client 0.0.0.0 &

# register envoys
wait_for_port 8500 && \
 ( curl_with_retries -X PUT -s localhost:8500/v1/agent/service/register -T /etc/envoy-control/register-echo1.json & \
 curl_with_retries -X PUT -s localhost:8500/v1/agent/service/register -T /etc/envoy-control/register-echo2.json & )

# start envoy front-proxy
sed -i "s/{{.IngressListenerPort}}/${PORT:-10000}/g" /etc/envoy-front-proxy.yaml
/usr/local/bin/envoy -c /etc/envoy-front-proxy.yaml
