APGAS for Scala
===

An implementation of the Asynchronous Partitioned Global Address Space (APGAS) programming model as an embedded domain-specific language for Scala, based on the [APGAS (Java) library](http://x10-lang.org/software/download-apgas/latest-apgas-release.html).

Requirements
---

Things work better if you use Eclipse. Somehow, the classpaths don't get set up
in the same way when using sbt or starting from a shell.

- Eclipse >= 4.4 ("Luna")
- The `apgas` and `apgas.impl` projects from the [X10 SVN
  repository](http://sourceforge.net/p/x10/code/HEAD/tree/trunk/).
- Hazelcast (see `INSTALL.txt` in `apgas.impl`).
- The [Scala IDE](http://scala-ide.org/download/current.html) Eclipse plugins.

Constructs
---

The two fundamental control structures in APGAS are `asyncAt`, and `finish`:

    def asyncAt(place: Place)(body: =>Unit) : Unit
    def finish(body: =>Unit) : Unit

The `asyncAt` construct spawns an asynchronous task at place `p` and returns immediately. It is therefore the primitive construct for both concurrency and distribution. The `finish` construct detects termination: an invocation of `finish` will execute its body and then block until all nested invocations of `asyncAt` have completed.

Because spawning local tasks is so common, the library defines an optimized version of `asyncAt` for this purpose with the signature:

    def async(body: =>Unit) : Unit

The `PlaceLocal` trait defines a *global* name that is resolved to a separate *local* instance at each place. In an application that defines one `Worker` object per place, for instance, we can write:

    class Worker(...) extends PlaceLocal

Initializing an independent object at each place is achieved using the `forPlaces` helper:

    val w = PlaceLocal.forPlaces(places) { new Worker() }

For a type `T` that cannot extend `PlaceLocal`, the library defines `GlobalRef[T]`, which acts as a wrapper.

For full details of the APGAS programming model, see the reference paper below.

Notes
---

Projects that only use `async` and `finish` (i.e. no distribution) will work just fine. Projects running distributed computations must ensure that the `java` command starting the other processes includes `scala-library.jar` in the classpath.

Licensing Terms
---------------
(C) Copyright IBM Corporation 2015.

This program is controlled by the Eclipse Public Licence v1.0.
You may obtain a copy of the License at
    http://www.opensource.org/licenses/eclipse-1.0.php

Referencing APGAS for Scala
---

Please cite the following paper if APGAS for Scala helps you in your research:

P. Suter, O. Tardieu and J. Milthorpe (2015)
[Distributed Programming in Scala with APGAS](http://dl.acm.org/citation.cfm?doid=2774975.2774977)
Proceedings of the 6th ACM SIGPLAN Symposium on Scala
DOI:10.1145/2774975.2774977
