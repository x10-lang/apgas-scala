APGAS for Scala
===

Scala adapter for the APGAS (Java) library.

Requirements
---

Things work better if you use Eclipse. Somehow, the classpaths don't get set up
in the same way when using sbt or starting from a shell.

- Eclipse >= 4.4 ("Luna")
- The `apgas` and `apgas.impl` projects from the [X10 SVN
  repository](http://sourceforge.net/p/x10/code/HEAD/tree/trunk/).
- Hazelcast (see `INSTALL.txt` in `apgas.impl`).
- The [Scala IDE](http://scala-ide.org/download/current.html) Eclipse plugins.

Notes
---

Projects that only use `async` and `finish` (i.e. no distribution) will work
just fine. Projects running distributed computations need to make sure that the
`java` command starting the other processes will include `scala-library.jar` in
their classpath.
