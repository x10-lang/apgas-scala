#!/usr/bin/env bash

source common.sh

DEPTH=${1?"Usage: $0 depth workers"}
WORKERS=${2?"Usage: $0 depth workers"}

CLASSPATH=''${APGAS_SCALA_EXAMPLES_HOME}'/bin:'${AKKA_HOME}'/lib/akka/*:'${SCALA_HOME}'/lib/scala-library.jar:bin'

for (( WORKER=1; WORKER<$WORKERS; WORKER++ )); do
    echo "Launching worker $WORKER..."
    ${JAVA} -classpath ${CLASSPATH} apgas.scala.examples.uts.UTSAkkaDistributed ${DEPTH} ${WORKERS} ${WORKER} &
done

echo "Sleeping for a second..."

sleep 10

echo "Launching master."

${JAVA} -classpath ${CLASSPATH} apgas.scala.examples.uts.UTSAkkaDistributed ${DEPTH} ${WORKERS} 0

echo "Killing workers."

killall -KILL ${JAVA}
