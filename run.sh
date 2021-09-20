#!/bin/bash

rm ./*.class ./*.interp

export CLASSPATH=".:./antlr-4.8-complete.jar:$CLASSPATH"
alias antlr4='java -Xmx500M org.antlr.v4.Tool'
alias grun='java org.antlr.v4.runtime.misc.TestRig'

#antlr4 LoopNest.g4
java -Xmx500M org.antlr.v4.Tool LoopNest.g4

javac LoopNest*.java

#visualize the parse tree
#grun LoopNest tests -gui < TestCase.java

javac Driver.java
java Driver Testcases.t
