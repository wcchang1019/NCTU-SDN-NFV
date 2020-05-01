onos localhost app activate proxyarp
mvn clean install -DskipTests
onos-app localhost install! target/path-app-1.0-SNAPSHOT.oar
