cmake_minimum_required(VERSION 3.0.0)

set(ETH_CMAKE_DIR   "${CMAKE_CURRENT_LIST_DIR}/cmake"   CACHE PATH "The the path to the cmake directory")
list(APPEND CMAKE_MODULE_PATH ${ETH_CMAKE_DIR})

include(EthToolchains)

include(EthPolicy)
eth_policy()

set(PROJECT_VERSION "0.4.15")
project(solidity VERSION ${PROJECT_VERSION} LANGUAGES CXX)

option(SOLC_LINK_STATIC "Link solc executable statically on supported platforms" OFF)

#ccache disabled, not including it in this CMake file
include(EthDependencies)
include(jsoncpp)
include_directories(SYSTEM ${JSONCPP_INCLUDE_DIR})
find_package(Threads)

# Figure out what compiler and system are we using
include(EthCompilerSettings)

# utilities
include(EthUtils)
include(EthOptions)

configure_project()

add_subdirectory(libdevcore)
add_subdirectory(libevmasm)
add_subdirectory(libjulia)
add_subdirectory(libsolidity)
add_subdirectory(libsolc)

if (NOT EMSCRIPTEN)
	add_subdirectory(solc)
endif()