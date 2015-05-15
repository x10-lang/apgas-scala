# Make sure this is Java 8
JAVA=java8
JAVAC=javac8
SCALAC=scalac

# Where you keep you apgas SVN checkout.
APGAS_COMMON_HOME=${HOME}/apgas

# Where you keep you apgas-scala Git checkout.
APGAS_SCALA_COMMON_HOME=${HOME}/workspace

# Where you keep Scala. Should have a lib, bin, etc. subdir.
SCALA_HOME=${HOME}/software/scala/current
# Where you keep Akka, should have a lib/akka subdir.
AKKA_HOME=${HOME}/software/akka/akka-2.3.9



# The rest depends only on the above.
APGAS_HOME=${APGAS_COMMON_HOME}/apgas
APGAS_EXAMPLES_HOME=${APGAS_COMMON_HOME}/apgas.examples
APGAS_IMPL_HOME=${APGAS_COMMON_HOME}/apgas.impl

APGAS_SCALA_HOME=${APGAS_SCALA_COMMON_HOME}/apgas.scala
APGAS_SCALA_EXAMPLES_HOME=${APGAS_SCALA_COMMON_HOME}/apgas.scala.examples
