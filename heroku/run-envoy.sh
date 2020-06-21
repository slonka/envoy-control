#!/usr/bin/dumb-init /bin/sh
set -e

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
/bin/envoy-control/envoy-control-runner/bin/envoy-control-runner $START_ARGUMENTS &

# start envoys
sed -i "s/{{.IngressListenerPort}}/${PORT:-10000}/g" /etc/envoy-front-proxy.yaml
/usr/local/bin/envoy -c /etc/envoy-front-proxy.yaml &

sh -c 'curl --max-time 1 --retry 25 -X PUT -s localhost:8500/v1/agent/service/register -T /etc/envoy-control/register-echo1.json' &
sh -c 'curl --max-time 1 --retry 25 -X PUT -s localhost:8500/v1/agent/service/register -T /etc/envoy-control/register-echo2.json' &

sh -c 'while ! nc -z localhost 8080; do
  sleep 1 #
done && /usr/local/bin/envoy --base-id 1 -c /etc/envoy1.yaml &; /usr/local/bin/envoy --base-id 2 -c /etc/envoy2.yaml &' &

consul agent -server -ui -ui-content-path "/consul/ui" -dev -client 0.0.0.0
