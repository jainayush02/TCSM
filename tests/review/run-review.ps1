param([switch]$MySql,[string]$Filter)
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$buildDir = Join-Path $projectRoot $(if ($MySql) { 'target/independent-review-mysql' } else { 'target/independent-review' })
$classesDir = Join-Path $buildDir 'classes'
New-Item -ItemType Directory -Force -Path $classesDir | Out-Null
Copy-Item (Join-Path $projectRoot 'src/main/resources/*.sql') $classesDir
# Override the classpath configuration; never load .env or connect to the user's database.
@'
db.driver=org.h2.Driver
db.url=jdbc:h2:mem:independent_review;MODE=MySQL;DB_CLOSE_DELAY=-1
db.user=sa
db.password=
h2.driver=org.h2.Driver
h2.url=jdbc:h2:mem:independent_review;MODE=MySQL;DB_CLOSE_DELAY=-1
h2.user=sa
h2.password=
'@ | Set-Content -Encoding ascii (Join-Path $classesDir 'db.properties')
if ($MySql) {
    $env:REVIEW_MYSQL_DATABASE = 'telecom_review_' + [guid]::NewGuid().ToString('N').Substring(0,12)
    if (Test-Path (Join-Path $projectRoot '.env')) {
        foreach ($line in Get-Content (Join-Path $projectRoot '.env')) {
            if ($line -match '^\s*(TCSMS_DB_USER|TCSMS_DB_PASSWORD)\s*=\s*(.*)$') {
                [Environment]::SetEnvironmentVariable($matches[1],$matches[2].Trim().Trim('"').Trim("'"),'Process')
            }
        }
    }
    @"
db.driver=com.mysql.cj.jdbc.Driver
db.url=jdbc:mysql://localhost:3306/$($env:REVIEW_MYSQL_DATABASE)?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&forceConnectionTimeZoneToSession=true
db.user=root
db.required=true
"@ | Set-Content -Encoding ascii (Join-Path $classesDir 'db.properties')
}
$sources = @(Get-ChildItem (Join-Path $projectRoot 'src/main/java') -Recurse -Filter '*.java' | ForEach-Object { $_.FullName })
$sources += Join-Path $PSScriptRoot 'IndependentReview.java'
$dependencyPath = Join-Path $projectRoot 'lib/*'
& javac --release 17 -encoding UTF-8 -cp $dependencyPath -d $classesDir $sources
if ($LASTEXITCODE -ne 0) { throw 'Review compilation failed.' }
Push-Location $buildDir
try {
    if ($Filter) { $env:REVIEW_TEST_FILTER=$Filter }
    & java '-Dfile.encoding=UTF-8' -cp "$classesDir;$dependencyPath" IndependentReview
    $reviewExitCode = $LASTEXITCODE
} finally {
    Remove-Item Env:\REVIEW_TEST_FILTER -ErrorAction SilentlyContinue
    Pop-Location
}
exit $reviewExitCode
