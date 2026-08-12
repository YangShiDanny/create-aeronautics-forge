@echo off
setlocal EnableExtensions EnableDelayedExpansion
chcp 65001 >nul

set "PROJECT_DIR=%~dp0"
if "%PROJECT_DIR:~-1%"=="\" set "PROJECT_DIR=%PROJECT_DIR:~0,-1%"
cd /d "%PROJECT_DIR%"

set "LOG=%PROJECT_DIR%\build_output.txt"
echo ============================================ > "%LOG%"
echo  Aeronautics Forge 1.20.1 build > "%LOG%"
echo  started: %date% %time% >> "%LOG%"
echo ============================================ >> "%LOG%"

echo.
echo ============================================
echo   Aeronautics Forge 1.20.1  -  one-click build
echo ============================================
echo.
echo   This tool will automatically:
echo     1) find or download JDK 17
echo     2) find or download Gradle 7.6.4
echo     3) compile the mod and write a log to build_output.txt
echo.
echo   The first run needs internet to download things, about 300 MB.
echo   (If you already built Sable on this PC, JDK and Gradle are reused.)
echo   Please be patient for a few minutes.
echo.

REM ---------- 1. find JDK 17 ----------
set "JDK_DIR="
echo [1/3] Looking for JDK 17 ...
if exist "C:\Program Files\Java\jdk-17.0.4.1\bin\java.exe" set "JDK_DIR=C:\Program Files\Java\jdk-17.0.4.1"
if not defined JDK_DIR if exist "C:\Program Files\Java\jdk-17.0.2\bin\java.exe" set "JDK_DIR=C:\Program Files\Java\jdk-17.0.2"
if not defined JDK_DIR if exist "C:\Users\18369\AppData\Local\Programs\Eclipse Adoptium\jdk-17.0.4.1\bin\java.exe" set "JDK_DIR=C:\Users\18369\AppData\Local\Programs\Eclipse Adoptium\jdk-17.0.4.1"
REM reuse a JDK previously downloaded by the Sable build script
if not defined JDK_DIR if exist "%LOCALAPPDATA%\sable_build\jdk17\jdk-17.0.4.1\bin\java.exe" set "JDK_DIR=%LOCALAPPDATA%\sable_build\jdk17\jdk-17.0.4.1"
if not defined JDK_DIR goto :jdk_download
goto :jdk_found

:jdk_download
echo   JDK 17 not found at the usual spots - downloading it now. Needs internet.
call :download_jdk
if not defined JDK_DIR goto :jdk_error
goto :jdk_found

:jdk_error
echo.
echo   ERROR: Could not get JDK 17. Please install it manually:
echo   https://adoptium.net/temurin17/
echo   Then run this file again.
pause
exit /b 1

:jdk_found
echo   Using JDK: %JDK_DIR%

REM ---------- 2. find or download Gradle 7.6.4 ----------
set "GRADLE_BIN="
echo [2/3] Looking for Gradle 7.6.4 ...
if exist "C:\Gradle\gradle-7.6.4\bin\gradle.bat" set "GRADLE_BIN=C:\Gradle\gradle-7.6.4\bin\gradle.bat"
if not defined GRADLE_BIN if exist "%LOCALAPPDATA%\sable_build\gradle-7.6.4\bin\gradle.bat" set "GRADLE_BIN=%LOCALAPPDATA%\sable_build\gradle-7.6.4\bin\gradle.bat"
if not defined GRADLE_BIN goto :gradle_setup
goto :gradle_found

:gradle_setup
echo   Gradle not installed yet. Looking for a local copy or downloading.
call :setup_gradle
if not defined GRADLE_BIN goto :gradle_error
goto :gradle_found

:gradle_error
echo.
echo   ERROR: Could not get Gradle 7.6.4. Please install it manually:
echo   https://gradle.org/install/
echo   Then run this file again.
pause
exit /b 1

:gradle_found
echo   Using Gradle: %GRADLE_BIN%

REM ---------- 3. build ----------
REM ForgeGradle caches AT-patched Minecraft jars per 1.20.1 version in the shared
REM .gradle cache. Other Forge 1.20.1 projects (e.g. Sable) may have generated them
REM with a DIFFERENT Access Transformer. Wipe the patched jars (NOT mcp/mappings) so
REM this project regenerates them from its own AT. Offline-safe (base jar is cached).
set "ATCACHE=%USERPROFILE%\.gradle\caches\forge_gradle\minecraft_repo\versions\1.20.1"
if exist "%ATCACHE%" (
    echo [pre] Clearing stale AT-patched jars so this project's Access Transformer applies ...
    for %%F in (client.jar client-extra.jar server.jar client.jar.input client-extra.jar.input server.jar.input) do (
        if exist "%ATCACHE%\%%F" del /q "%ATCACHE%\%%F"
    )
)
if exist "%PROJECT_DIR%\build\fg_cache" (
    echo [pre] Wiping build/fg_cache so Access Transformer re-applies with current AT ...
    rmdir /s /q "%PROJECT_DIR%\build\fg_cache"
)
REM Force a FULL recompile of all sources. Gradle's incremental compiler once
REM left the mixin package end_sea OUT of build/classes (its .class files were
REM missing while Gradle still reported compileJava UP-TO-DATE), so the jar was
REM missing dev.simulated_team.simulated.mixin.end_sea.* and the game crashed
REM with "The specified mixin ... was not found". Deleting build/classes +
REM build/libs makes compileJava/jar run every time. fg_cache (the mapped
REM Minecraft jar) is preserved above, so this stays offline-safe and avoids
REM the 3000 cannot-access errors that a full "clean" would cause.
if exist "%PROJECT_DIR%\build\classes" (
    echo [pre] Removing build/classes to force a full recompile ...
    rmdir /s /q "%PROJECT_DIR%\build\classes"
)
if exist "%PROJECT_DIR%\build\libs" rmdir /s /q "%PROJECT_DIR%\build\libs"
if exist "%PROJECT_DIR%\build\tmp" rmdir /s /q "%PROJECT_DIR%\build\tmp"
echo [3/3] Starting the build. This can take several minutes, please wait.
echo.
set "JAVA_HOME=%JDK_DIR%"
set "PATH=%JDK_DIR%\bin;%PATH%"
REM ---------- 3b. generate data (blockstates / block + item models) ----------
REM The data generators (AeroBlockStateGen / SimBlockStateGen / OffroadDatagen etc.)
REM produce all blockstate + block/item model JSON into src/generated/resources/.
REM Forge nests each mod under an extra <modId>/ dir, so we flatten it afterwards.
REM That dir is registered as a resource source in build.gradle and gets packaged into the jar.
REM Skipping runData leaves those JSON out, so the game falls back to the purple/black missing texture.
REM If the (flattened) generated assets already exist, skip the heavy/fragile runData step.
set "GEN_DONE=0"
REM GEN_DONE requires BOTH: generated assets (blockstates/models) exist AND custom advancements are at the correct plural path.
REM An earlier build wrote achievements to the singular 'advancement/' folder (the game does not read it). We use the
REM correct plural path as a second check; if it is missing, force runData so the fixed SimAdvancements regenerates them.
if exist "%PROJECT_DIR%\src\generated\resources\assets\aeronautics\blockstates" if exist "%PROJECT_DIR%\src\generated\resources\data\aeronautics\advancements\root.json" set "GEN_DONE=1"
if "%GEN_DONE%"=="1" echo [3b] Generated data already present - skipping runData to save time and avoid the fragile daemon.
if "%GEN_DONE%"=="1" echo.
if "%GEN_DONE%"=="1" goto :build_start
echo [3b] Generating data (blockstates + models). This can take a few minutes ...
echo.
REM Remove any previously-flattened output so stale files do not persist.
if exist "%PROJECT_DIR%\src\generated\resources\assets" rmdir /s /q "%PROJECT_DIR%\src\generated\resources\assets"
if exist "%PROJECT_DIR%\src\generated\resources\data" rmdir /s /q "%PROJECT_DIR%\src\generated\resources\data"
call "%GRADLE_BIN%" --no-daemon runData >> "%LOG%" 2>&1
set "RC_DATA=%errorlevel%"
REM runData may report a daemon-dispatch error AFTER it has already written all
REM generated files. Flatten first, then decide based on the actual artifacts.
call :flatten_gen
set "GEN_OK=1"
for %%M in (aeronautics simulated offroad) do (
    set "GEN_BS=%PROJECT_DIR%\src\generated\resources\assets\%%M\blockstates"
    if not exist "!GEN_BS!" (
        echo   [DATA GEN WARNING] %%M blockstates not generated - textures may stay purple/black.
        set "GEN_OK=0"
    ) else (
        echo   [DATA GEN OK] %%M blockstates generated.
    )
)
echo.
if "%GEN_OK%"=="0" (
    echo   [DATA GEN FAILED] runData did not produce blockstates. See build_output.txt
    goto :build_failed
)
REM Reached here: artifacts exist (even if runData's daemon died post-write). Continue to build.
goto :build_start
REM --no-daemon: avoid Gradle daemon caching stale resolution metadata.
REM Removed "clean": the clean task deleted build\fg_cache (the mapped Minecraft jar)
REM AFTER the Access Transformer wrote it, so compileJava could not find net.minecraft.*
REM classes (3000 cannot-access errors). Use incremental build, which reuses the
REM already-generated mapped jar. Code is finalized, so no stale-class problem.
REM Pure cmd call (no PowerShell pipe). The old PowerShell Tee-Object / ForEach
REM approach crashed on Windows PowerShell 5.1 and made the window flash-close.
REM --no-daemon avoids stale daemon metadata.
REM No "clean": clean wipes build\fg_cache (the mapped Minecraft jar) and causes 3000 cannot-access errors.
REM Incremental build reuses the already-generated mapped jar.
REM All Gradle output goes to the log file, then the full log is printed to this window at the end.
:build_start
set "JAVA_HOME=%JDK_DIR%"
set "PATH=%JDK_DIR%\bin;%PATH%"
REM --rerun-tasks: force a full rebuild, bypassing the Gradle build-cache that packs stale classes into the jar
REM (previously this caused edited code to keep running the old version; full recompile is slower but guarantees a fresh jar)
call "%GRADLE_BIN%" --no-daemon --rerun-tasks build > "%LOG%" 2>&1
set "RC=%errorlevel%"
echo.
echo ============================================
echo   Build finished. Checking result ...
echo ============================================
echo.
if %RC% neq 0 (
    echo   [BUILD FAILED] Full log is in build_output.txt
    goto :build_failed
) else (
    echo   [BUILD SUCCESSFUL] jar is in build/libs/
    goto :build_ok
)

:build_failed
echo   The build failed. BUILD FAILED.
echo   Please send the file build_output.txt from this folder to the developer.
goto :build_done

:build_ok
echo   BUILD SUCCESSFUL - done!
echo   The mod jar is in the build/libs/ folder.
REM ---- auto-deploy the jar into the game's mods folder (avoids stale code from a missed manual copy) ----
set "MODS_DIR=C:\Users\18369\Desktop\1.20.1\.minecraft\mods"
if exist "%MODS_DIR%" (
    copy /Y "%PROJECT_DIR%\build\libs\create-aeronautics-forge-1.20.1-1.3.0.jar" "%MODS_DIR%\" >nul
    echo   [AUTO-DEPLOY] jar 已自动复制到 mods 文件夹（覆盖旧版）。
) else (
    echo   [NOTE] 未找到 mods 文件夹 %MODS_DIR%，请手动复制 build/libs 下的 jar。
)

:build_done
echo ============================================
echo   Full log saved to: %LOG%
echo   Printing full log below (also saved to the file):
echo ============================================
echo.
type "%LOG%"
echo.
pause
exit /b 0

REM ================= subroutine: download JDK 17 =================
:download_jdk
set "DLDIR=%LOCALAPPDATA%\sable_build"
if not exist "%DLDIR%" mkdir "%DLDIR%"
set "JDK_ZIP=%DLDIR%\jdk17.zip"
set "JDK_HOME=%DLDIR%\jdk17"
if exist "%JDK_ZIP%" goto :jdk_zip_ok
powershell -NoProfile -Command "Invoke-WebRequest -Uri 'https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse' -OutFile '%JDK_ZIP%' -UseBasicParsing"
:jdk_zip_ok
if exist "%JDK_ZIP%" goto :jdk_extract
echo   Download failed.
goto :eof
:jdk_extract
echo   Extracting JDK ...
powershell -NoProfile -Command "Expand-Archive -Path '%JDK_ZIP%' -DestinationPath '%JDK_HOME%' -Force"
if exist "%JDK_HOME%\jdk-17.0.4.1\bin\java.exe" set "JDK_DIR=%JDK_HOME%\jdk-17.0.4.1"
goto :eof

REM ================= subroutine: setup Gradle 7.6.4 =================
:setup_gradle
set "DLDIR=%LOCALAPPDATA%\sable_build"
if not exist "%DLDIR%" mkdir "%DLDIR%"
set "GRADLE_HOME=%DLDIR%\gradle-7.6.4"

if exist "%GRADLE_HOME%\bin\gradle.bat" set "GRADLE_BIN=%GRADLE_HOME%\bin\gradle.bat"
if exist "%GRADLE_HOME%\bin\gradle.bat" goto :eof

REM look for a zip the user may have already downloaded
set "ZIP="
if not defined ZIP if exist "%USERPROFILE%\Downloads\gradle-7.6.4-bin.zip" set "ZIP=%USERPROFILE%\Downloads\gradle-7.6.4-bin.zip"
if not defined ZIP if exist "%USERPROFILE%\Downloads\gradle-7.6.4-all.zip" set "ZIP=%USERPROFILE%\Downloads\gradle-7.6.4-all.zip"
if not defined ZIP if exist "%PROJECT_DIR%\gradle-7.6.4-bin.zip" set "ZIP=%PROJECT_DIR%\gradle-7.6.4-bin.zip"
if not defined ZIP if exist "%DLDIR%\gradle-7.6.4-bin.zip" set "ZIP=%DLDIR%\gradle-7.6.4-bin.zip"

if not defined ZIP call :download_gradle_zip

if defined ZIP if exist "%ZIP%" goto :gradle_extract
goto :eof

:download_gradle_zip
set "ZIP=%DLDIR%\gradle-7.6.4-bin.zip"
powershell -NoProfile -Command "Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-7.6.4-bin.zip' -OutFile '%ZIP%' -UseBasicParsing"
goto :eof

:gradle_extract
echo   Extracting Gradle from the zip ...
powershell -NoProfile -Command "Expand-Archive -Path '%ZIP%' -DestinationPath '%DLDIR%' -Force"
if exist "%GRADLE_HOME%\bin\gradle.bat" set "GRADLE_BIN=%GRADLE_HOME%\bin\gradle.bat"
goto :eof

REM ================= subroutine: flatten Forge's nested <modId>/assets|data/<modId> =================
REM Forge data gen with multiple mods writes each mod under an extra <modId>/ dir
REM (e.g. src/generated/resources/aeronautics/assets/aeronautics/...). The game
REM expects assets/<modId>/... , so move the inner tree up one level.
:flatten_gen
for %%M in (aeronautics simulated offroad sable) do (
    if exist "%PROJECT_DIR%\src\generated\resources\%%M\assets" (
        xcopy "%PROJECT_DIR%\src\generated\resources\%%M\assets" "%PROJECT_DIR%\src\generated\resources\assets\" /E /Y /I >nul
        rmdir /s /q "%PROJECT_DIR%\src\generated\resources\%%M\assets"
    )
    if exist "%PROJECT_DIR%\src\generated\resources\%%M\data" (
        xcopy "%PROJECT_DIR%\src\generated\resources\%%M\data" "%PROJECT_DIR%\src\generated\resources\data\" /E /Y /I >nul
        rmdir /s /q "%PROJECT_DIR%\src\generated\resources\%%M\data"
    )
    if exist "%PROJECT_DIR%\src\generated\resources\%%M" rmdir /s /q "%PROJECT_DIR%\src\generated\resources\%%M"
)
goto :eof
