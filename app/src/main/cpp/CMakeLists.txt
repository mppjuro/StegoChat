cmake_minimum_required(VERSION 4.1.0)
project("stegochat_native")

add_library(
        stegochat_native
        SHARED
        native-lib.cpp)

find_library(
        log-lib
        log)

target_link_libraries(
        stegochat_native
        ${log-lib})