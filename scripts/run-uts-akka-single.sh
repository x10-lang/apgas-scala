#!/usr/bin/env bash

source common.sh

DEPTH=${1?"Usage: $0 depth workers"}
WORKERS=${2?"Usage: $0 depth workers"}

${JAVA} -classpath ''${APGAS_SCALA_EXAMPLES_HOME}'/bin:'${AKKA_HOME}'/lib/akka/*:'${SCALA_HOME}'/lib/scala-library.jar:bin' apgas.scala.examples.uts.UTSAkka ${DEPTH} ${WORKERS}
