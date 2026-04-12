cmake_minimum_required(VERSION 3.22.1)
project("ebur128-scanner")

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
target_compile_options(ebur128-scanner PRIVATE
    $<$<COMPILE_LANGUAGE:C>:-w>
)

# 16 KB page size alignment for Android 15+ compatibility
target_link_options(ebur128-scanner PRIVATE
    "-Wl,-z,max-page-size=16384"
    "-Wl,-z,relro"
    "-Wl,-z,now"
)

target_link_libraries(ebur128-scanner
    ${log-lib}
)
