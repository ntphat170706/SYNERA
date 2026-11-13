# Basic script arguments are forwarded verbatim to Maven (including -o/-q).
$ErrorActionPreference='Stop'
$repo=Split-Path -Parent $PSScriptRoot
$portable=Join-Path $repo '.tools/backend'
$jdk=Get-ChildItem -LiteralPath $portable -Directory -Filter 'jdk-25*' -ErrorAction SilentlyContinue | Select-Object -First 1
$maven=Join-Path $portable 'apache-maven-3.9.9/bin/mvn.cmd'
if($jdk){$env:JAVA_HOME=$jdk.FullName;$env:Path=(Join-Path $jdk.FullName 'bin')+';'+$env:Path}
if(!(Test-Path -LiteralPath $maven)){$maven='mvn.cmd'}
Push-Location (Join-Path $repo 'backend')
try { $mavenArguments=if($args.Count){$args}else{@('test')}; & $maven '-Dmaven.repo.local=../.tools/m2' '-B' '-ntp' @mavenArguments;exit $LASTEXITCODE } finally {Pop-Location}
