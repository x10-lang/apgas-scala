#!/usr/bin/env bash

source common.sh

POINTS=${1?"Usage: $0 numPoints numWorkers"}
WORKERS=${2?"Usage: $0 numPoints numWorkers"}

for (( WORKER=1; WORKER<$WORKERS; WORKER++ )); do
    echo "Launching worker $WORKER..."
    ${JAVA} -classpath ''${AKKA_HOME}'/lib/akka/*:'${SCALA_HOME}'/lib/scala-library.jar:bin' apgas.scala.examples.kmeans.KMeansAkkaDistributed ${POINTS} ${WORKERS} ${WORKER} &
done

echo "Sleeping for a second..."

sleep 10

echo "Launching master."

${JAVA} -classpath ''${AKKA_HOME}'/lib/akka/*:'${SCALA_HOME}'/lib/scala-library.jar:bin' apgas.scala.examples.kmeans.KMeansAkkaDistributed ${POINTS} ${WORKERS} 0

echo "Killing workers."

killall -KILL ${JAVA}
