
# Sets the minimum CMake version required for this project.
cmake_minimum_required(VERSION 3.22.1)

# Project name
project("unisign")

file(GLOB_RECURSE SCREEN_LOGIC_FILES CONFIGURE_DEPENDS "screens/*.cpp")
file(GLOB_RECURSE CORE_LOGIC_FILES CONFIGURE_DEPENDS "core/*.cpp")

add_library(${CMAKE_PROJECT_NAME} SHARED
    # List C/C++ source files with relative paths to this CMakeLists.txt.
    native-lib.cpp
    ${SCREEN_LOGIC_FILES}
    ${CORE_LOGIC_FILES}
)

target_link_libraries(${CMAKE_PROJECT_NAME}
    # List libraries link to the target library
    android
    log)