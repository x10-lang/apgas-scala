APGAS for Scala
===

An implementation of the Asynchronous Partitioned Global Address Space (APGAS) programming model as an embedded domain-specific language for Scala, based on the [APGAS (Java) library](http://x10-lang.org/software/download-apgas/latest-apgas-release.html).

Requirements
---

Things work better if you use Eclipse. Somehow, the classpaths don't get set up
in the same way when using sbt or starting from a shell.

- Eclipse >= 4.4 ("Luna")
- The `apgas` project from the [X10 Git repository](https://github.com/x10-lang/x10).
- Hazelcast (will be installed in apgas/lib by running [Ant](http://ant.apache.org) in the apgas project).
- The [Scala IDE](http://scala-ide.org/download/current.html) Eclipse plugins.
- (Optional for Akka examples) [Akka](http://akka.io/) 2.4.1

Terminology
---

A *Place* is a mutable, shared-memory region combined with a set of worker threads operating on this memory. A single application typically runs over a collection of places, where each place is implemented as a separate JVM.

A *task* is a sequence of computations, specified as a block. Each task is bound to a particular place. A task can spawn local and remote tasks, i.e. tasks to be executed in the same place or elsewhere.

A local task shares the heap of the parent task. A remote task executes on a snapshot of the parent task’s heap captured when the task is spawned. Global references are copied as part of the snapshot but the target objects are not copied. A global reference can only be dereferenced at the place of the target object where it resolves to the original object.

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

Running APGAS Scala Programs
---

The `scripts` directory contains shell scripts to compile the library and example programs, and to run the examples.

Projects that only use `async` and `finish` (i.e. no distribution) will work just fine. Projects running distributed computations must ensure that the `java` command starting the other processes includes `scala-library.jar` in the classpath.

Licensing Terms
---------------
(C) Copyright IBM Corporation 2015-2016.

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
