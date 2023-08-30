# JavaSolCheckCOJU
JUnit free checks of Java solutions provided by students in the context of CodeOcean (with a JUnit adapter)

## Context
Our "Algorithms and Programming" students (usually programming novices) from various engineering programs need feedback for their solutions of
  - programming tasks (given in PDFs and solved at home or during exercises in our lab) and
  - short graded tests (twice in a semester in our lab during exercises).

The solutions are implemented in Java.
In the first 6 weeks a typical solution of a certain sub task results in a single method.
Later object oriented features (constructors, member variables, sub classes, modifiers etc) are to be implemented too.

To get feedback the students have to enter their solutions into a [CodeOcean](https://github.com/openHPI/codeocean) instance, which is hosted at TU Ilmenau.
After typing the solution or copy pasting it (e.g. from Eclipse) a click on the [Score] button starts the evaluation process. 
In this process a docker container is started - one per student and task.

### The general evaluation process
1. The solution (one or more Java files), a makefile as well as the evaluation code for the current task is transferred into the docker image.
2. The Java code (solution and evaluation) is compiled per makefile. Note: sometimes the solution code does not compile or it compiles but it does not include a complete solution.
3. The evaluation code is started and generates textual feedback for each checked sub task. 

### The given feedback
In first versions of the evaluation code the given feedback was based on typical JUnit output, which described in general failed assertions.
Often the students did not understand the meaning of such output - i.e. the feedback was useless. 
On the other hand in the JUnit based evaluation it was necessary that the students uploaded complete solutions - including all methods, member variables etc, 
else the evaluation code did not compile and the students got no readable output at all.

Therefore a reflection based approach for the evaluation was implemented. 
Before the evaluation of a subtask it first checks if the needed members exist and gives according feedback if not.
If the evaluation is possible, then 
  - modifiers,
  - variable values or
  - the results of method calls (output, return values, changes in member variables)
are checked and feedback texts generated.
The feedback texts usually include all data needed by the students to reproduce the error.

## Current structure of the evaluation code
  - task dependent part: XYZ_refSolClass.java
  - task independent part: Check.java
    From software quality point of view it is too large but it is easy to copy-paste during each single exercise generation.

## Functional parts of the Check.java
  - general check description, realised using annotations
  - modifier checks
  - range handling (e.g. to describe ranges of random test parameters)
  - timeout handling, i.e. cancelable code execution (replaces the last JUnit functionality we used in our evaluation code, now we are JUnit free)
  - Java byte code and Java source code analysis, e.g. to detect loops or position of methods in the student solutions
  - general context information, e.g. epsilons, debug level, timeout, output control
  - error text generation based on the context in which the error occurred
  - parsing parameter lists (e.g. because of the limited complexity of annotations in Java)
  - output generation in JUnit style to stay compatible to the [JunitAdapter](https://github.com/openHPI/codeocean/blob/master/lib/junit_adapter.rb) used in CodeOcean (may be replaced by an own adapter in a next version)
  - 
