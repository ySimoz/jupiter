cmake_minimum_required(VERSION 3.22.1)
project(jupitermpv C)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)

add_library(mpv SHARED IMPORTED)
set_target_properties(mpv PROPERTIES
    IMPORTED_LOCATION "${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libmpv.so"
)

add_library(jupitermpv SHARED
    mpv_bridge.c
)

target_compile_options(jupitermpv PRIVATE
    -std=c11
    -Wall
    -Wextra
    -Werror
)

target_include_directories(jupitermpv PRIVATE
    "${CMAKE_CURRENT_SOURCE_DIR}"
    "${CMAKE_CURRENT_SOURCE_DIR}/include"
)

find_library(android-lib android)
find_library(log-lib log)

target_link_libraries(jupitermpv
    mpv
    ${android-lib}
    ${log-lib}
    dl
)
