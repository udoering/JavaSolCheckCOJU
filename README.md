# JavaSolCheckCOJU
JUnit free checks of Java solutions provided by students in the context of CodeOcean (with a JUnit adapter)

## Context
Our "Algorithms and Programing" students (usually programming novices) from various engineering programs need feedback for their solutions of
  - programing exercises and
  - short tests.

The solutions are implemented in Java.
In the first 6 weeks a typical solution of a certain sub task results in a single method.
Later object oriented features (constructors, member variables, sub classes, modifiers etc) are to be impleneted too.

To get feedback the students have to enter their solutions into a [CodeOcean](https://github.com/openHPI/codeocean) instance, which is hosted at TU Ilmenau.
After typing the solution or copy pasting it (e.g. from Eclipse) a click on the [Score] button starts the evaluation process. 
In this process a docker container is started - one per student and task.

### The general evaluation process
1. The solution (one or more Java files), a makefile as well as the evaluation code for the current task is transfered into the docker image.
2. The Java code is compiled per makefile. Note: sometimes the solution code does not compile or it compiles but it does not include a complete solution.
3. The evaluation code is started and generates textual feedback for each checked sub task. 

### The given feedback
In first versions of the evaluation code the given feedback was based on typical JUnit output, which described in general failed assertions.
Often the students did not understand the meaning of such output - i.e. the feedback was useless. Therefore
