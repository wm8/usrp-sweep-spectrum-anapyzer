# Spectrum Analyzer GUI for hackrf_sweep for Windows/Linux

## Сборка
### WINDOWS
### Зависимости
#### 1. Maven + jdk 21+
Тут про установку mvn
#### 2. Visual Studio 2022 Build Tools
Visual Studio 2022 Build Tools (или полная VS) → компонент Desktop development with C++
В CLion указываем Toolchain = Visual Studio (MSVC x64)
#### 3. UHD Windows Installer
Переходим [сюда](https://files.ettus.com/binaries/uhd/latest_release/Windows10/), ищем билд, оканчивающися на VS2022, скачиваем, устанавливаем. \
Если пакет установлен по стандартому пути, проверить корректность установки можно через:
```
& "C:\Program Files\UHD\bin\uhd_find_devices.exe"
```
#### 4. vcpkg
vcpkg качаем через
```
git clone https://github.com/microsoft/vcpkg C:\tools\vcpkg
cd C:\tools\vcpkg
.\bootstrap-vcpkg.bat
```
При сборке нужно будет указывать 
```
-DCMAKE_TOOLCHAIN_FILE=C:\dev\vcpkg\scripts\buildsystems\vcpkg.cmake
```
Эту опцию можно вписать в CLion в Settings → Build, Execution, Deployment → CMake → CMake options
#### 5. Boost + fftw3
Также для сборки c++ части нужны зависимости, они устанавливаются через
```
C:\tools\vcpkg install fftw3:x64-windows
C:\tools\vcpkg install boost:x64-windows
```