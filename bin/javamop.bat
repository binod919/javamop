@echo off

set SRC_ROOT=%~dp0..

java -cp "%CLASSPATH%;%SRC_ROOT%\lib\rvmonitor.jar;%SRC_ROOT%\lib\rvmonitorrt.jar;%SRC_ROOT%\lib\external\jcommander-1.36-SNAPSHOT.jar;%SRC_ROOT%\lib\external\commons-io-2.4.jar;%SRC_ROOT%\lib\javamop.jar;%SRC_ROOT%\lib\logicrepository.jar;%SRC_ROOT%\lib\plugins\*.jar;%SRC_ROOT%\lib\mysql-connector-java-3.0.9-stable-bin.jar" javamop.JavaMOPMain %*
