#!/bin/sh

cd "$(dirname "$0")/.."

# build fastvm and solidity compiler
make
make jni
cd solidity && make && cd ../dist

# copy LLVM library
cp /usr/lib/x86_64-linux-gnu/libLLVM-4.0.so.1 .

# construct the file list
tee file.list << END
libLLVM-4.0.so.1
libevmjit.so
libfastvm.so
END

# make archieve
tar -czf fastvm_v0.0.0.tar.gz file.list libLLVM-4.0.so.1 libevmjit.so libfastvm.so


# copy libraries
cp /usr/lib/x86_64-linux-gnu/libboost_filesystem.so.1.58.0 .
cp /usr/lib/x86_64-linux-gnu/libboost_program_options.so.1.58.0 .
cp /usr/lib/x86_64-linux-gnu/libboost_regex.so.1.58.0 .
cp /usr/lib/x86_64-linux-gnu/libboost_system.so.1.58.0 .
cp /usr/lib/x86_64-linux-gnu/libjsoncpp.so.1 .

# construct the file list
tee file.list << END
libboost_filesystem.so.1.58.0
libboost_program_options.so.1.58.0
libboost_regex.so.1.58.0
libboost_system.so.1.58.0
libjsoncpp.so.1
END

# make archieve
tar -czf solidity_v0.0.0.tar.gz file.list libboost* libjsoncpp.so.1 solc
