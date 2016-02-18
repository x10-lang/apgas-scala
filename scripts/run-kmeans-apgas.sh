#!/usr/bin/env bash

source common.sh

POINTS=${1?"Usage: $0 numPoints numPlaces"}
PLACES=${2?"Usage: $0 numPoints numPlaces"}

${JAVA} -classpath ''${APGAS_SCALA_EXAMPLES_HOME}'/bin:'${APGAS_SCALA_HOME}'/bin:'${APGAS_HOME}'/lib/*:'${SCALA_HOME}'/lib/scala-library.jar:bin' apgas.scala.examples.kmeans.KMeans ${POINTS} ${PLACES}
