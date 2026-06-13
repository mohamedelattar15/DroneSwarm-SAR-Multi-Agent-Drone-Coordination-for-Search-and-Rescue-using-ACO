@echo off
SETLOCAL ENABLEDELAYEDEXPANSION

SET JAVA_HOME=C:\Program Files\Java\jdk-17
SET M2_REPO=%USERPROFILE%\.m2\repository

SET JADE=%M2_REPO%\com\tilab\jade\jade\4.6.0\jade-4.6.0.jar
SET LOGBACK=%M2_REPO%\ch\qos\logback\logback-classic\1.5.3\logback-classic-1.5.3.jar
SET LOGBACK_CORE=%M2_REPO%\ch\qos\logback\logback-core\1.5.3\logback-core-1.5.3.jar
SET SLF4J=%M2_REPO%\org\slf4j\slf4j-api\2.0.12\slf4j-api-2.0.12.jar
SET CLASSES=target\classes

SET CP=%JADE%;%LOGBACK%;%LOGBACK_CORE%;%SLF4J%;%CLASSES%

"%JAVA_HOME%\bin\java.exe" -cp "%CP%" main.LauncherMain %*
