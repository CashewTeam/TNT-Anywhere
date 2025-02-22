cmake_minimum_required(VERSION 3.22.1)

project("sunshine")

include(dependencies/Boost_Sunshine.cmake)

add_library(
        sunshine
        SHARED
        sunshine.cpp
        logging.cpp
)

target_include_directories(sunshine PRIVATE
        ${Boost_INCLUDE_DIRS})

target_link_libraries(
        sunshine
        ${log-lib}
        ${Boost_LIBRARIES}
) 