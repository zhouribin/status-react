
# Copyright (C) 2016, Canonical Ltd.
# All rights reserved.

# This source code is licensed under the BSD-style license found in the
# LICENSE file in the root directory of this source tree. An additional grant
# of patent rights can be found in the PATENTS file in the same directory.

cmake_minimum_required(VERSION 2.8.11)

set(APP_NAME Status)
set(JS_APP_NAME StatusIm)
set(REACT_BUILD_STATIC_LIB ON)

set(CMAKE_OSX_DEPLOYMENT_TARGET "10.9")

set(EXTERNAL_MODULES_DIR ../../../realm-js/realm-js/react-native/desktop;node_modules/react-native-i18n/desktop;node_modules/react-native-config/desktop;node_modules/react-native-fs/desktop;node_modules/react-native-http-bridge/desktop;node_modules/react-native-webview-bridge/desktop;node_modules/react-native-keychain/desktop;node_modules/react-native-securerandom/desktop;modules/react-native-status/desktop;node_modules/google-breakpad;modules/react-native-desktop-linking/desktop;modules/react-native-desktop-notification/desktop;)
message(STATUS "EXTERNAL_MODULES_DIR: ${EXTERNAL_MODULES_DIR}")

string(REGEX MATCH "BUILD_FOR_BUNDLE" BUILD_FOR_BUNDLE "${CMAKE_CXX_FLAGS}")

# It is important to distinguish between directory hierarchies
# in release and dev builds. Release contains .env
# in the root dir of the build, while dev build's current dir
# is status-react/desktop/bin, hence the need
# to jump two levels up.
# This is required by react-native-config library which references ENVFILE
# from its own CMakeLists.txt file, so it will read the value set here
if(${BUILD_FOR_BUNDLE} MATCHES "BUILD_FOR_BUNDLE")
  set(ENVFILE "/.env")
else()
  set(ENVFILE "/../../.env")
endif()

foreach(external_module ${EXTERNAL_MODULES_DIR})
  message(STATUS "external_module: ${external_module}")
  add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/../${external_module} ${CMAKE_CURRENT_BINARY_DIR}/${external_module})
endforeach(external_module)

add_subdirectory(reportApp)

# APPLICATION_MAIN_CPP_PATH contains absolute path to generated template copy of main.cpp for application executable
get_filename_component(APPLICATION_MAIN_CPP_PATH main.cpp ABSOLUTE)

include_directories(/home/max/work/react-native-desktop/react-native-desktop/React/Layout)
add_subdirectory(/home/max/work/react-native-desktop/react-native-desktop/ReactQt/runtime/src ${CMAKE_CURRENT_BINARY_DIR}/lib)
add_subdirectory(/home/max/work/react-native-desktop/react-native-desktop/ReactQt/application/src ${CMAKE_CURRENT_BINARY_DIR}/bin)

if (WIN32)
  set(RUN_SCRIPT_FILE_NAME "run-app.bat")
else()
  set(RUN_SCRIPT_FILE_NAME "run-app.sh")
endif()

configure_file(
  ${RUN_SCRIPT_FILE_NAME}.in
  ${CMAKE_CURRENT_BINARY_DIR}/${RUN_SCRIPT_FILE_NAME}
  @ONLY
)

