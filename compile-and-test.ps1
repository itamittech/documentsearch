$env:JAVA_HOME="C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.2\jbr"
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$MAVEN="C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.2\plugins\maven\lib\maven3\bin\mvn.cmd"

Set-Location "D:\Job\DeepRunner\documentsearch"
& $MAVEN clean test -e -fn