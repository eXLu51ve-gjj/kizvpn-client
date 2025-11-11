@echo off
setlocal

set SOURCE=C:\Users\nml52\Desktop\KIZ_VPN.png
set BASE=app\src\main\res

echo Копирование логотипа KIZ_VPN...
echo.

if not exist "%SOURCE%" (
    echo ОШИБКА: Файл не найден: %SOURCE%
    pause
    exit /b 1
)

for %%f in (mipmap-mdpi mipmap-hdpi mipmap-xhdpi mipmap-xxhdpi mipmap-xxxhdpi) do (
    if not exist "%BASE%\%%f" mkdir "%BASE%\%%f"
    copy "%SOURCE%" "%BASE%\%%f\ic_launcher.png" >nul
    copy "%SOURCE%" "%BASE%\%%f\ic_launcher_foreground.png" >nul
    echo Скопировано в: %%f
)

echo.
echo Готово! Файлы скопированы во все папки.
echo.
echo ВАЖНО: Для создания правильных размеров используйте Android Studio:
echo 1. File -^> New -^> Image Asset
echo 2. Выберите Launcher Icons (Adaptive and Legacy)
echo 3. В Foreground Layer выберите ваш PNG файл
echo 4. Android Studio автоматически создаст все размеры
echo.
pause

