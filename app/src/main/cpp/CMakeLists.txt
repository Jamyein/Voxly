cmake_minimum_required(VERSION 3.10.2)
project("ebur128-jni")

# Set C++ standard
set(CMAKE_CXX_STANDARD 11)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Add libebur128 source files
add_library(
    ebur128
    STATIC
    libebur128/ebur128.c
)

# Include directories for libebur128
target_include_directories(ebur128 PRIVATE libebur128)

# Create JNI wrapper library
add_library(
    ebur128-jni
    SHARED
    Ebur128Jni.cpp
)

# Link libraries
target_link_libraries(
    ebur128-jni
    ebur128
    log
)

# Include directories
target_include_directories(
    ebur128-jni
    PRIVATE
    libebur128
)

# Compiler flags for optimization
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O3 -ffast-math")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -ffast-math")

# Android specific settings if needed
if(ANDROID)
    target_compile_definitions(ebur128 PRIVATE __ANDROID__)
    target_compile_definitions(ebur128-jni PRIVATE __ANDROID__)
endif()
