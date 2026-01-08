@echo off
setlocal

set "APP_HOME=%~dp0"
set "WRAPPER_JAR=%APP_HOME%gradle\\wrapper\\gradle-wrapper.jar"

if not exist "%WRAPPER_JAR%" (
  echo Missing %WRAPPER_JAR%. Run "gradle wrapper" to generate it. 1>&2
  exit /b 1
)

if defined JAVA_HOME (
  set "JAVA_CMD=%JAVA_HOME%\\bin\\java.exe"
) else (
  set "JAVA_CMD=java"
)

"%JAVA_CMD%" %JAVA_OPTS% -classpath "%WRAPPER_JAR%" org.gradle.wrapper.GradleWrapperMain %*
