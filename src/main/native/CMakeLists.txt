
cmake_minimum_required(VERSION 3.20)

project(TimelyFinancing LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

find_package(JNI REQUIRED COMPONENTS JVM)
find_package(OpenSSL REQUIRED)
find_package(Threads REQUIRED)

# Generar DLL en Windows o SO en Linux
add_library(TimelyFinancing SHARED
        TimelyFinancing.cpp
)

target_include_directories(TimelyFinancing PRIVATE
        ${JNI_INCLUDE_DIRS}
)

target_link_libraries(TimelyFinancing PRIVATE
        OpenSSL::SSL
        OpenSSL::Crypto
        Threads::Threads
)

# Opciones específicas para MSVC
if(MSVC)
    target_compile_options(TimelyFinancing PRIVATE
            /O2
            /W4
    )
else()
    # GCC / Clang
    target_compile_options(TimelyFinancing PRIVATE
            -O3
            -march=native
            -fvisibility=hidden
            -Wall
            -Wextra
            -Wno-deprecated-declarations
            -Wno-unused-parameter
    )
endif()
