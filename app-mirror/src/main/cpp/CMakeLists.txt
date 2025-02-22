cmake_minimum_required(VERSION 3.22.1)

project("sunshine")

add_subdirectory(dependencies/moonlight-common-c/enet)
include(dependencies/Boost_Sunshine.cmake)

add_library(
        sunshine
        SHARED
        sunshine.cpp
        logging.cpp
)

target_include_directories(sunshine PRIVATE
        dependencies/moonlight-common-c/enet/include
        ${Boost_INCLUDE_DIRS})

target_link_libraries(
        sunshine
        android
        log
        enet
        ${Boost_LIBRARIES}
) 