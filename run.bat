@echo off
SETLOCAL ENABLEDELAYEDEXPANSION

REM --- Détection automatique de JAVA_HOME (portable) ---
IF "%JAVA_HOME%"=="" (
    FOR /F "delims=" %%i IN ('where javac 2^>NUL') DO (
        SET "JAVAC_PATH=%%i"
        GOTO :found_javac
    )
    echo [ERREUR] JAVA_HOME non defini et javac introuvable dans le PATH.
    echo Installez un JDK 17+ ou definissez JAVA_HOME.
    EXIT /B 1
    :found_javac
    FOR %%j IN ("!JAVAC_PATH!") DO SET "JAVA_HOME=%%~dpj.."
)

SET "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
IF NOT EXIST "%JAVA_EXE%" (
    echo [ERREUR] java.exe introuvable dans JAVA_HOME=%JAVA_HOME%
    EXIT /B 1
)

SET "M2_REPO=%USERPROFILE%\.m2\repository"
SET "JADE=%M2_REPO%\com\tilab\jade\jade\4.6.0\jade-4.6.0.jar"
SET "LOGBACK=%M2_REPO%\ch\qos\logback\logback-classic\1.5.3\logback-classic-1.5.3.jar"
SET "LOGBACK_CORE=%M2_REPO%\ch\qos\logback\logback-core\1.5.3\logback-core-1.5.3.jar"
SET "SLF4J=%M2_REPO%\org\slf4j\slf4j-api\2.0.12\slf4j-api-2.0.12.jar"
SET "CLASSES=target\classes"

SET "CP=%JADE%;%LOGBACK%;%LOGBACK_CORE%;%SLF4J%;%CLASSES%"

REM --- Compilation si target\classes absent ---
IF NOT EXIST "%CLASSES%" (
    echo Compilation des sources...
    IF NOT EXIST "target\classes" MKDIR "target\classes"
    "%JAVA_HOME%\bin\javac.exe" -encoding UTF-8 -cp "%CP%" -d "%CLASSES%" ^
        src\main\java\agents\*.java src\main\java\environment\*.java ^
        src\main\java\main\*.java src\main\java\utils\*.java
    IF ERRORLEVEL 1 (
        echo [ERREUR] Echec de la compilation.
        EXIT /B 1
    )
)

"%JAVA_EXE%" -cp "%CP%" main.LauncherMain %*
