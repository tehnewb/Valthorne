cmake_minimum_required(VERSION 3.20)
project(ValthorneBgfx LANGUAGES CXX)

if(NOT MINGW OR NOT DEFINED BGFX_SOURCES OR NOT DEFINED BGFX_GENERATED)
    message(FATAL_ERROR "Use build.mjs with the pinned LLVM-MinGW toolchain.")
endif()

file(GLOB ASTC_SOURCES "${BGFX_SOURCES}/bimg/3rdparty/astc-encoder/source/*.cpp")
add_library(valthorne-bgfx SHARED
    "${BGFX_GENERATED}/amalgamated.cpp"
    "${BGFX_SOURCES}/bx/src/amalgamated.cpp"
    "${BGFX_SOURCES}/bimg/src/image.cpp"
    ${ASTC_SOURCES})
target_compile_features(valthorne-bgfx PRIVATE cxx_std_20)
target_compile_definitions(valthorne-bgfx PRIVATE
    __STDC_LIMIT_MACROS __STDC_FORMAT_MACROS __STDC_CONSTANT_MACROS
    BX_CONFIG_DEBUG=0 BGFX_SHARED_LIB_BUILD=1 ASTCENC_F16C=0 ASTCENC_NEON=0)
target_include_directories(valthorne-bgfx PRIVATE
    "${BGFX_SOURCES}/bx/include/compat/mingw"
    "${BGFX_SOURCES}/bx/include"
    "${BGFX_SOURCES}/bimg/include"
    "${BGFX_SOURCES}/bimg/3rdparty/astc-encoder/include"
    "${BGFX_SOURCES}/bgfx/include"
    "${BGFX_SOURCES}/bgfx/src"
    "${BGFX_SOURCES}/bgfx/3rdparty"
    "${BGFX_SOURCES}/bgfx/3rdparty/khronos"
    "${BGFX_SOURCES}/bgfx/3rdparty/directx-headers/include/directx")
get_filename_component(COMPILER_BIN "${CMAKE_CXX_COMPILER}" DIRECTORY)
get_filename_component(COMPILER_ROOT "${COMPILER_BIN}" DIRECTORY)
target_compile_options(valthorne-bgfx PRIVATE
    -O2 -fno-rtti -fno-exceptions -ffunction-sections -fdata-sections
    "-ffile-prefix-map=${BGFX_SOURCES}=bgfx-sources"
    "-ffile-prefix-map=${BGFX_GENERATED}=bgfx-generated"
    "-ffile-prefix-map=${COMPILER_ROOT}=llvm-mingw")
if(CMAKE_SYSTEM_PROCESSOR STREQUAL "AMD64")
    target_compile_options(valthorne-bgfx PRIVATE -msse4.2)
endif()
target_link_options(valthorne-bgfx PRIVATE -static -Wl,--gc-sections -Wl,--no-insert-timestamp)
target_link_libraries(valthorne-bgfx PRIVATE gdi32 psapi)
set_target_properties(valthorne-bgfx PROPERTIES PREFIX "" OUTPUT_NAME "valthorne-bgfx")
