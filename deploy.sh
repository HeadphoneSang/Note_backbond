#!/bin/bash

cd /root/note_backbond

echo "========== Build =========="
mvn clean package -DskipTests

echo "========== Stop =========="

PID=$(ps -ef | grep note_backend | grep jar | grep -v grep | awk '{print $2}')

if [ ! -z "$PID" ]; then
    kill -9 $PID
fi

echo "========== Start =========="

java -jar target/note_backbond-1.0-SNAPSHOT.jar

echo "Done."
