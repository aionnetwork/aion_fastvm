#!/bin/bash

print_config() {
	echo "---------------- Aion Solidity Emscripten Build ----------------"
	echo "SOLIDITY_DIR=$SOLIDITY_DIR"
	echo "BOOST_WORKSPACE=$BOOST_WORKSPACE"
	echo "Boost ----------------------------------------------------------"
	echo "BOOST_SRC=$BOOST_SRC"
	echo "BOOST_SRC_REMOTE=$BOOST_SRC_REMOTE"
	echo "BOOST_SRC_COMPRESSED=$BOOST_SRC_COMPRESSED"
	echo "Emscripten -----------------------------------------------------"
	echo "BOOST_EMSCRIPTEN_JAM_REMOTE=$BOOST_EMSCRIPTEN_JAM_REMOTE"
	echo "BOOST_EMSCRIPTEN_JAM=$BOOST_EMSCRIPTEN_JAM"
	echo "----------------------------------------------------------------"
}

test_command() {
	if ! [ -x "$(command -v $1)" ]; then
		echo "requires $1. Aborting." >&2
		exit 1
	else
		echo "$1 found ✔"
	fi
}

test_emscripten() {
	if ! [ -x "$(command -v em++)" ]; then
		echo "requires em++. Aborting." >&2
		exit 1
	else
		echo "em++ found ✔"
	fi
}

test_cmake() {
	if ! [ -x "$(command -v cmake)" ]; then
		echo "requires cmake. Aborting." >&2
		exit 1
	else
		echo "cmake found ✔"
	fi
}


# check if dependency is downloaded, if not, download
dl_dep() {
	# $1 = remote location
	# $2 = local file name
	echo "checking source dependency $2"
	if [ ! -f $2 ]; then
		echo "$2 not found... retrieving from $1"
		wget -q -O $2 $1
	else
		echo "$2 found ✔"
	fi
}

# assume:
# 1) CMAKE is already installed
# 2) emscripten is installed and activated

# general idea:
# 1) build BOOST using emscripten
# 2) execute solidity CMAKE with emscripten boost
echo "checking environmental dependencies..."
test_command wget
test_command cmake
test_command em++

# solidity source dir
SOLIDITY_DIR=$(realpath ..)
BOOST_WORKSPACE=$(realpath .)

BOOST_SRC="boost_1_68_0"
BOOST_SRC_REMOTE="https://dl.bintray.com/boostorg/release/1.68.0/source/boost_1_68_0.tar.gz"
BOOST_SRC_COMPRESSED="boost.tar.gz"

BOOST_EMSCRIPTEN_JAM_REMOTE="https://gist.githubusercontent.com/qoire/fca16b59c025f31d1ec3086f73ba22b5/raw/4cef9d0ea7ff891f9517c45afdb00a816464ade6/emscripten.jam"
BOOST_EMSCRIPTEN_JAM="emscripten.jam"

print_config

# emscripten related
if [ ! -d $BOOST_WORKSPACE/$BOOST_SRC ]; then
	dl_dep $BOOST_WORKSPACE/$BOOST_SRC_REMOTE $BOOST_WORKSPACE/$BOOST_SRC_COMPRESSED
	tar -xzf $BOOST_WORKSPACE/$BOOST_SRC_COMPRESSED
else
	echo "boost source found (if you want to redownload, delete boost source folder)"
fi

cd $BOOST_WORKSPACE/$BOOST_SRC
if [ ! -f build_complete.txt ]; then
	./bootstrap.sh

	if [ ! -f $BOOST_EMSCRIPTEN_JAM ]; then
		wget -O $BOOST_EMSCRIPTEN_JAM $BOOST_EMSCRIPTEN_JAM_REMOTE
	fi

	echo "using emscripten : : em++ ;" >> project-config.jam

	echo "begin boost build..."

	./b2 --debug toolset=emscripten link=static variant=release threading=single runtime-link=static system regex filesystem unit_test_framework program_options cxxflags="-Wno-unused-local-typedef -Wno-variadic-macros -Wno-c99-extensions -Wno-all"
	find . -name 'libboost*.a' -exec cp {} . \;
	touch build_complete.txt
fi

cd $SOLIDITY_DIR

# remove if exists
if [ -d build ]; then
	rm -rf build
fi

mkdir build
cd build

cmake \
	-DCMAKE_TOOLCHAIN_FILE=$SOLIDITY_DIR/cmake/toolchains/emscripten.cmake \
	-DCMAKE_BUILD_TYPE=Release \
	-DBoost_FOUND=1 \
	-DBoost_USE_STATIC_LIBS=1 \
	-DBoost_USE_STATIC_RUNTIME=1 \
	-DBoost_INCLUDE_DIR=$BOOST_WORKSPACE/$BOOST_SRC/ \
	-DBoost_FILESYSTEM_LIBRARY_RELEASE=$BOOST_WORKSPACE/$BOOST_SRC/libboost_filesystem.a \
	-DBoost_PROGRAM_OPTIONS_LIBRARY_RELEASE=$BOOST_WORKSPACE/$BOOST_SRC/libboost_program_options.a \
	-DBoost_REGEX_LIBRARY_RELEASE=$BOOST_WORKSPACE/$BOOST_SRC/libboost_regex.a \
	-DBoost_SYSTEM_LIBRARY_RELEASE=$BOOST_WORKSPACE/$BOOST_SRC/libboost_system.a \
	-DBoost_UNIT_TEST_FRAMEWORK_LIBRARY_RELEASE=$BOOST_WORKSPACE/$BOOST_SRC/libboost_unit_test_framework.a \
	-DTESTS=0 \
$SOLIDITY_DIR

make