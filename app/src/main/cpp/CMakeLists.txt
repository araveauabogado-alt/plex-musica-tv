cmake_minimum_required(VERSION 3.22.1)
project(visualizer LANGUAGES C CXX)

# projectM: reimplementación abierta de MilkDrop (LGPL 2.1). Se descarga en la compilación
# y se enlaza de forma estática; el código completo de la app es público.
include(FetchContent)

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(ENABLE_PLAYLIST OFF CACHE BOOL "" FORCE)
set(ENABLE_SYSTEM_PROJECTM_EVAL OFF CACHE BOOL "" FORCE)
set(ENABLE_SYSTEM_GLM OFF CACHE BOOL "" FORCE)
set(ENABLE_SDL_UI OFF CACHE BOOL "" FORCE)
set(BUILD_TESTING OFF CACHE BOOL "" FORCE)
set(BUILD_DOCS OFF CACHE BOOL "" FORCE)
set(ENABLE_INSTALL OFF CACHE BOOL "" FORCE)
set(ENABLE_DEBUG_POSTFIX OFF CACHE BOOL "" FORCE)

FetchContent_Declare(
        projectm
        GIT_REPOSITORY https://github.com/projectM-visualizer/projectm.git
        GIT_TAG v4.1.8
        GIT_SHALLOW TRUE
        GIT_SUBMODULES_RECURSE TRUE
)
FetchContent_MakeAvailable(projectm)

add_library(visualizer SHARED visualizer.cpp)
target_link_libraries(visualizer PRIVATE libprojectM::projectM GLESv3 EGL android log)
