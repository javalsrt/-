@echo off
chcp 65001 >nul
title 教师管理端 前端启动器
color 0B

echo ======================================================
echo    教师管理端前端 启动器 ^(Vue3 + Vite^)
echo ======================================================
echo.

cd /d "%~dp0"

where node >nul 2>nul
if errorlevel 1 (
    echo [错误] 未检测到 Node.js 请先安装并勾选 Add to PATH
    echo 下载地址 nodejs.org/zh-cn/download
    pause
    exit /b 1
)
echo [通过] Node.js 版本:
node -v
echo.

if not exist "node_modules" (
    echo [提示] 首次运行 正在安装依赖 npm install 请稍候...
    echo.
    call npm install
    if errorlevel 1 (
        echo.
        echo [错误] 依赖安装失败 请检查网络后手动执行 npm install
        pause
        exit /b 1
    )
    echo.
    echo [成功] 依赖安装完成
)
echo.
echo [提示] 正在启动开发服务器 端口 3000 ...
echo.
echo  访问地址: http://localhost:3000
echo  停止服务: 直接关闭本窗口即可
echo ------------------------------------------------------
echo.

start "" http://localhost:3000
call npm run dev

echo.
echo 服务已停止 按任意键关闭窗口...
pause