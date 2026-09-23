@echo off
chcp 65001 >nul
title 管理端 - 智学笔记教师管理平台

cd /d "%~dp0course-note-teacher"

echo.
echo ╔════════════════════════════════════╗
echo ║   智学笔记 - 教师管理平台          ║
echo ║   启动中...                        ║
echo ╚════════════════════════════════════╝
echo.

:: 检查 node_modules
if not exist "node_modules\" (
    echo [1/2] 检测到依赖未安装，正在安装...
    call npm install
    if errorlevel 1 (
        echo ❌ 依赖安装失败，请检查网络或 npm 配置
        pause
        exit /b 1
    )
    echo ✅ 依赖安装完成
) else (
    echo [1/2] 依赖已就绪
)

:: 启动开发服务器
echo [2/2] 启动开发服务器 (http://localhost:3000)
echo.
start http://localhost:3000
call npm run dev

pause
