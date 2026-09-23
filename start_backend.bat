@echo off
set JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.16.8-hotspot
cd /D c:\Users\jay\CodeBuddy\20260510225454\course-note-backend
echo ==================================
echo 正在启动后端服务...
echo ==================================
echo.
echo 注意：记得先执行 SQL 迁移！
echo ALTER TABLE course MODIFY COLUMN weeks TEXT;
echo.
mvn spring-boot:run
pause
