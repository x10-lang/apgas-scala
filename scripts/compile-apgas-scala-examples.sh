#!/usr/bin/env bash

source common.sh

mkdir -p ${APGAS_SCALA_EXAMPLES_HOME}/bin

cd ${APGAS_SCALA_EXAMPLES_HOME} && ${SCALAC} -d bin -classpath "bin:${AKKA_HOME}/lib/akka/*:${APGAS_HOME}/lib/apgas.jar:${APGAS_SCALA_HOME}/bin" `find src -name "*.scala" -type f`  
