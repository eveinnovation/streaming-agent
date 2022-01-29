#!/usr/bin/env bash
echo 'Build streaming-platform...'
mvn clean install
echo 'Starting Spring server...'
kill -9 `lsof -t -i:8000`
mvn spring-boot:run