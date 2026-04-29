@echo off
call ./gradlew compileGithubDebugKotlin 2>&1 | more +5

if %ERRORLEVEL% neq 0 (
    echo.
    echo [REMINDER] Build failed. Did you add a lesson.md entry?
    echo  If this is a new error, append: N. [Root cause]. Rule: [Preventive principle].
    echo.
)
