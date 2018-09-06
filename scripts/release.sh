#!/bin/sh

if [ $# -eq 2 ]; then
    cd aion_fastvm
elif [ $# -eq 1 ]; then
    cd "$(dirname "$0")/.."
else
    echo "Usage: ./release.sh [VERSION]"
    exit 1
fi

fastvm_dir="fastvm_v$1"
solidity_dir="solidity_v$1"

# build fastvm
make evmjit jni

# copy libraries
cd dist
mkdir -p ${fastvm_dir} && cd ${fastvm_dir}
cp ../libfastvm.so . || exit 2
cp ../libevmjit.so . || exit 2
cp /usr/lib/x86_64-linux-gnu/libLLVM-4.0.so.1 . || exit 2
# included in the `common` library
# cp /usr/lib/x86_64-linux-gnu/libstdc++.so.6 . || exit 2
# cp /lib/x86_64-linux-gnu/libgcc_s.so.1 . || exit 2
cp /usr/lib/x86_64-linux-gnu/libffi.so.6 . || exit 2
cp /usr/lib/x86_64-linux-gnu/libedit.so.2 . || exit 2
cp /lib/x86_64-linux-gnu/libtinfo.so.5 . || exit 2
cp /lib/x86_64-linux-gnu/libz.so.1 . || exit 2
cp /lib/x86_64-linux-gnu/libbsd.so.0 . || exit 2

# construct the file list
tee file.list << END
libbsd.so.0
libz.so.1
libtinfo.so.5
libedit.so.2
libffi.so.6
libLLVM-4.0.so.1
libevmjit.so
libfastvm.so
END

# make archieve
cd ..
tar -czf ${fastvm_dir}.tar.gz ${fastvm_dir}

# compile solidity
cd ../solidity && make && cd ../dist

# copy libraries
mkdir -p ${solidity_dir} && cd ${solidity_dir}
cp ../solc . || exit 3
cp /usr/lib/x86_64-linux-gnu/libboost_filesystem.so.1.58.0 . || exit 3
cp /usr/lib/x86_64-linux-gnu/libboost_program_options.so.1.58.0 . || exit 3
cp /usr/lib/x86_64-linux-gnu/libboost_regex.so.1.58.0 . || exit 3
cp /usr/lib/x86_64-linux-gnu/libboost_system.so.1.58.0 . || exit 3
cp /usr/lib/x86_64-linux-gnu/libjsoncpp.so.1 . || exit 3
# included in the `common` library
# cp /usr/lib/x86_64-linux-gnu/libstdc++.so.6 . || exit 3
# cp /lib/x86_64-linux-gnu/libgcc_s.so.1 . || exit 3
cp /usr/lib/x86_64-linux-gnu/libicui18n.so.55 . || exit 3
cp /usr/lib/x86_64-linux-gnu/libicuuc.so.55 . || exit 3
cp /usr/lib/x86_64-linux-gnu/libicudata.so.55 . || exit 3

# construct the file list
tee file.list << END
libicudata.so.55
libicuuc.so.55
libicui18n.so.55
libjsoncpp.so.1
libboost_system.so.1.58.0
libboost_regex.so.1.58.0
libboost_program_options.so.1.58.0
libboost_filesystem.so.1.58.0
END

# make archieve
cd ..
tar -czf ${solidity_dir}.tar.gz ${solidity_dir}

if [ $# -eq 2 ]; then
  cp ${solidity_dir}.tar.gz ../../
  cp ${fastvm_dir}.tar.gz ../../
fi
