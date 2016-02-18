# Make sure this is Java 8
JAVA=java
JAVAC=javac
SCALAC=scalac

# Where you keep you apgas Git checkout (see 
APGAS_COMMON_HOME=${X10_HOME}

# Where you keep you apgas-scala Git checkout.
APGAS_SCALA_COMMON_HOME=${HOME}/apgas-scala

# Where you keep Scala. Should have a lib, bin, etc. subdir.
SCALA_HOME=${HOME}/scala
# Where you keep Akka, should have a lib/akka subdir.
AKKA_HOME=${HOME}/opt/akka-2.4.1

# The rest depends only on the above.
APGAS_HOME=${APGAS_COMMON_HOME}/apgas
APGAS_EXAMPLES_HOME=${APGAS_COMMON_HOME}/apgas.examples

APGAS_SCALA_HOME=${APGAS_SCALA_COMMON_HOME}/apgas.scala
APGAS_SCALA_EXAMPLES_HOME=${APGAS_SCALA_COMMON_HOME}/apgas.scala.examples
