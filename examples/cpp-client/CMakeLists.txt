cmake_minimum_required(VERSION 3.20)
project(queueforge_submit LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
find_package(CURL REQUIRED)
find_package(nlohmann_json CONFIG REQUIRED)

add_executable(queueforge-submit main.cpp)
target_link_libraries(queueforge-submit PRIVATE CURL::libcurl nlohmann_json::nlohmann_json)

