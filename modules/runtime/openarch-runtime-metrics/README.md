# About openarch-runtime-metrics

This module is here to configure the metrics reporter and add
default metrics.

For testing, you can run a graphite/grafana stack using the provided [docker compose](./docker/graphite-grafana):
```bash
cd ./docker/graphite-grafana
docker compose up -d
```

Access Grafana using [http://localhost:3000](http://localhost:3000) with user: admin pwd: admin,
you should have an OpenArch Grafana dashboard up and running.

Graphite is also reachable [http://localhost:8000](http://localhost:8000)

To stop Grafana (loosing all data):
```bash
docker compose down
```

For production, you should use different retentions and docker volumes to persist data.

You can also find old example of json graphite dashboard [graphite](./graphite) directory.
To use it from Graphite Dashboard > Edit Dashboard, paste the content

See <http://metrics.codahale.com/> for Metrics documentation.
