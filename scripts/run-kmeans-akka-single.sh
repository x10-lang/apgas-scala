#!/usr/bin/env bash

source common.sh

POINTS=${1?"Usage: $0 numPoints numWorkers"}
WORKERS=${2?"Usage: $0 numPoints numWorkers"}

${JAVA} -classpath ''${APGAS_SCALA_EXAMPLES_HOME}'/bin:'${AKKA_HOME}'/lib/akka/*:'${SCALA_HOME}'/lib/scala-library.jar:bin' apgas.scala.examples.kmeans.KMeansAkka ${POINTS} ${WORKERS}
