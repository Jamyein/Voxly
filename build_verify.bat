@echo off
call ./gradlew compileGithubDebugKotlin 2>&1 | more +5
