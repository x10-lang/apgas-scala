#!/usr/bin/env bash

source common.sh

mkdir -p bin

${SCALAC} -d bin -classpath "bin:${AKKA_HOME}/lib/akka/*" `find ${APGAS_SCALA_COMMON_HOME} -name "*.scala" -type f`  
