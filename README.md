Csc 435 (Compilers) Uvic Assignment 2 (University of Victoria).
  Brett Binnersley, V00776751
  Sebastien Guillemot, V00741620

The project uses Antlr4

To use:
% antlr4 -visitor -no-listener Goo.g4
% javac -g -d bin *.java
% cd bin
% java GooMain -dsym <GoProgram>.go
