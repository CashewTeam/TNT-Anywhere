cmake_minimum_required(VERSION 3.22.1)

project("sunshine")

add_subdirectory(dependencies/moonlight-common-c/enet)
add_subdirectory(dependencies/openssl-cmake)
include(dependencies/Boost_Sunshine.cmake)

add_library(
        sunshine
        SHARED
        sunshine.cpp
        logging.cpp
        network.cpp
        config.cpp
        platform/common.cpp
        crypto.cpp
        dependencies/nanors/rs.c
        stat_trackers.cpp
        globals.cpp
)

target_include_directories(sunshine PRIVATE
        dependencies/moonlight-common-c/enet/include
        dependencies/ffmpeg
        dependencies/nanors/deps/obl
        dependencies/nanors
        dependencies
        ${Boost_INCLUDE_DIRS})

target_link_libraries(
        sunshine
        android
        log
        enet
        ssl
        crypto
        ${Boost_LIBRARIES}
) 