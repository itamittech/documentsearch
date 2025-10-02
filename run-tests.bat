@echo off
set JAVA_HOME=C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.2\jbr
set PATH=%JAVA_HOME%\bin;%PATH%
cd /d "D:\Job\DeepRunner\documentsearch"
"C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.2\plugins\maven\lib\maven3\bin\mvn.cmd" clean test