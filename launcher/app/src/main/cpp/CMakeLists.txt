cmake_minimum_required(VERSION 3.22)
project(vodlauncherfix CXX)
add_library(vodlauncherfix SHARED launcherfix.cpp)
target_link_libraries(vodlauncherfix log dl)
