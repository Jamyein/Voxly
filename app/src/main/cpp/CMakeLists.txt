cmake_minimum_required(VERSION 4.1.2)
project("ebur128-scanner")

option(ENABLE_LINKER_WARNINGS_AS_ERROR "Treat linker warnings as errors" OFF)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)

# libebur128 source directory
set(EBUR128_SRC_DIR ${CMAKE_CURRENT_SOURCE_DIR}/external/libebur128)

# Build shared library
add_library(ebur128-scanner SHARED
    ebur128_scanner.cpp
    ${EBUR128_SRC_DIR}/ebur128.c
)

# Include directories
target_include_directories(ebur128-scanner PRIVATE
    ${EBUR128_SRC_DIR}/include
    ${EBUR128_SRC_DIR}/queue
)

# Find system libraries
find_library(log-lib log)

# Suppress #warning for ebur128.c on aarch64 (manual FTZ is correct for ARM)
# Optimization flags for best performance
target_compile_options(ebur128-scanner PRIVATE
    $<$<COMPILE_LANGUAGE:C>:-w>
    $<$<COMPILE_LANGUAGE:CXX>:-O3>
    $<$<COMPILE_LANGUAGE:CXX>:-fno-exceptions>
    $<$<COMPILE_LANGUAGE:CXX>:-fno-rtti>
)

# 16 KB page size alignment for Android 15+ compatibility
# LINKER: prefix used for clarity with CMake 4.x linker flag separation
target_link_options(ebur128-scanner PRIVATE
    LINKER:-z,max-page-size=16384
    LINKER:-z,relro
    LINKER:-z,now
)

target_link_libraries(ebur128-scanner
    ${log-lib}
)

if(ENABLE_LINKER_WARNINGS_AS_ERROR)
    set(CMAKE_LINK_WARNING_AS_ERROR TRUE)
endif()
