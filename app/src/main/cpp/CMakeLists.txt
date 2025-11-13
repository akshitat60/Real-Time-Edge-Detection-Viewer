cmake_minimum_required(VERSION 3.18.1)

project("native-lib")

# Set C++ standard
set(CMAKE_CXX_STANDARD 14)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Add C++ flags: -std=c++14, -frtti, -fexceptions
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -frtti -fexceptions")

# Get project root directory (go up 4 levels from cpp folder)
get_filename_component(PROJECT_ROOT_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../" ABSOLUTE)

# Set OpenCV directory to where OpenCVConfig.cmake is located
set(OpenCV_DIR "${PROJECT_ROOT_DIR}/opencv/native/jni")

message(STATUS "Project root: ${PROJECT_ROOT_DIR}")
message(STATUS "OpenCV_DIR: ${OpenCV_DIR}")

# Find OpenCV package
find_package(OpenCV REQUIRED)

if(OpenCV_FOUND)
    message(STATUS "OpenCV found: ${OpenCV_VERSION}")
    message(STATUS "OpenCV libraries: ${OpenCV_LIBS}")
    message(STATUS "OpenCV include dirs: ${OpenCV_INCLUDE_DIRS}")
else()
    message(FATAL_ERROR "OpenCV not found!")
endif()

# Include directories
include_directories(${OpenCV_INCLUDE_DIRS})

# Create native library
add_library(
    native-lib
    SHARED
    native-lib.cpp
    opencv_processing.cpp
)

# Find required libraries
find_library(log-lib log)

# Link libraries
target_link_libraries(
    native-lib
    ${OpenCV_LIBS}
    ${log-lib}
    android
    log
    jnigraphics
)