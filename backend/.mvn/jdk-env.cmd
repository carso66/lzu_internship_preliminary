@REM ----------------------------------------------------------------------------
@REM Resolve a usable JDK for the Maven wrapper.
@REM
@REM mvnw.cmd only checks JAVA_HOME / java.exe on PATH and fails with
@REM "The JAVA_HOME environment variable is not defined correctly" when neither
@REM is visible to the current shell (e.g. the terminal was started before the
@REM environment variable was created, or PATH never had a JDK). This script sets
@REM JAVA_HOME for the calling process so `mvnw` keeps working, without editing
@REM the wrapper itself (the Maven wrapper regenerates it).
@REM
@REM Resolution order:
@REM   1. an already valid JAVA_HOME
@REM   2. a JDK 17 install, the target of this project
@REM   3. the persisted user / machine JAVA_HOME (the registry stays current even
@REM      when the caller inherited a stale environment)
@REM   4. any other JDK found in the well-known install locations
@REM ----------------------------------------------------------------------------

@set "MVNW_JDK="
@if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javac.exe" @exit /b 0

@for /d %%D in (
  "%ProgramFiles%\Java\jdk-17*"
  "%ProgramFiles%\Eclipse Adoptium\jdk-17*"
  "%ProgramFiles%\Microsoft\jdk-17*"
  "%ProgramFiles%\Amazon Corretto\jdk17*"
  "%ProgramFiles%\Zulu\zulu-17*"
  "%ProgramFiles%\BellSoft\LibericaJDK-17*"
  "%ProgramFiles%\Semeru\jdk-17*"
  "C:\Program Files (x86)\Android\openjdk\jdk-17*"
  "%LOCALAPPDATA%\Programs\Eclipse Adoptium\jdk-17*"
  "%USERPROFILE%\.jdks\*17*"
) do @if not defined MVNW_JDK @call :accept "%%~fD"

@if not defined MVNW_JDK @call :from_registry HKCU\Environment
@if not defined MVNW_JDK @call :from_registry "HKLM\SYSTEM\CurrentControlSet\Control\Session Manager\Environment"

@for /d %%D in (
  "%ProgramFiles%\Java\jdk*"
  "%ProgramFiles%\Eclipse Adoptium\jdk*"
  "%ProgramFiles%\Microsoft\jdk*"
  "%ProgramFiles%\Amazon Corretto\jdk*"
  "%ProgramFiles%\Zulu\zulu-*"
  "C:\Program Files (x86)\Android\openjdk\jdk*"
  "%LOCALAPPDATA%\Programs\Eclipse Adoptium\jdk*"
  "%USERPROFILE%\.jdks\*"
) do @if not defined MVNW_JDK @call :accept "%%~fD"

@if not defined MVNW_JDK @exit /b 1

@set "JAVA_HOME=%MVNW_JDK%"
@set "PATH=%JAVA_HOME%\bin;%PATH%"
@exit /b 0

:from_registry
@for /f "tokens=2,*" %%A in ('reg query "%~1" /v JAVA_HOME 2^>nul ^| findstr /i "REG_SZ"') do @call :accept "%%~B"
@exit /b 0

:accept
@if exist "%~1\bin\javac.exe" @set "MVNW_JDK=%~1"
@exit /b 0
