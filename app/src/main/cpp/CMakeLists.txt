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
    flac_decoder_jni.cpp
    native_decoder_jni.cpp
    ${EBUR128_SRC_DIR}/ebur128.c
)

# Include directories
target_include_directories(ebur128-scanner PRIVATE
    ${EBUR128_SRC_DIR}/include
    ${EBUR128_SRC_DIR}/queue
    ${CMAKE_CURRENT_SOURCE_DIR}/external
)

# Find system libraries
find_library(log-lib log)

# Optimization flags for best performance
# Note: NEON is enabled by default for all ARM ABIs in NDK r21+
target_compile_options(ebur128-scanner PRIVATE
    $<$<COMPILE_LANGUAGE:C>:-w>
    $<$<COMPILE_LANGUAGE:C>:-O3>
    $<$<COMPILE_LANGUAGE:C>:-fvisibility=hidden>
    $<$<COMPILE_LANGUAGE:CXX>:-O3>
    $<$<COMPILE_LANGUAGE:CXX>:-fno-exceptions>
    $<$<COMPILE_LANGUAGE:CXX>:-fno-rtti>
    $<$<COMPILE_LANGUAGE:CXX>:-fvisibility=hidden>
)


target_link_options(ebur128-scanner PRIVATE
    LINKER:-z,max-page-size=16384
    LINKER:-z,relro
    LINKER:-z,now
    LINKER:--version-script=${CMAKE_CURRENT_SOURCE_DIR}/version-script.ld
)

target_link_libraries(ebur128-scanner
    ${log-lib}
    mediandk
)

if(ENABLE_LINKER_WARNINGS_AS_ERROR)
    set(CMAKE_LINK_WARNING_AS_ERROR TRUE)
endif()
