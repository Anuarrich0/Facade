Set-Location $PSScriptRoot
mvn -q -DskipTests package
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
java -jar target/matchmaking-facade-1.0.0.jar
