cmake_minimum_required(VERSION 3.22.1)

project("sunshine")

add_library(
        sunshine
        SHARED
        sunshine.cpp
)

find_library(
        log-lib
        log
)

target_link_libraries(
        sunshine
        ${log-lib}
) 