@echo off

set DESTINATION_DIR=D:\ITU\S4\Web_Service\framework\test_framework\WEB-INF\lib
set LIB_SERVLET=D:\apache-tomcat-9.0.74-windows-x64\apache-tomcat-9.0.74\lib

if not exist "%DESTINATION_DIR%" (
    mkdir "%DESTINATION_DIR%"
)
cd src
javac -cp "%LIB_SERVLET%/*" -d ..\..\classes *.java

jar -cf ..\..\libFrameWork\framework.jar -C ..\..\classes .

copy ..\..\libFrameWork\framework.jar "%DESTINATION_DIR%"