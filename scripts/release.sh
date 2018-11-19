#!/bin/sh

if [ $# -eq 2 ]; then
    cd aion_fastvm
elif [ $# -eq 1 ]; then
    cd "$(dirname "$0")/.."
else
    echo "Usage: ./release.sh [VERSION]"
    exit 1
fi

version="$1"
dist_dir="dist"
fastvm_dir="${dist_dir}/fastvm_v${version}"
solidity_dir="${dist_dir}/solidity_v${version}"

# build fastvm
(cd fastvm; make evmjit jni)

# copy libraries
mkdir -p ${fastvm_dir}
cp ${dist_dir}/libfastvm.so ${fastvm_dir} || exit 2
cp ${dist_dir}/libevmjit.so ${fastvm_dir} || exit 2
cp /usr/lib/x86_64-linux-gnu/libLLVM-4.0.so.1 ${fastvm_dir} || exit 2
# included in the `common` library
# cp /usr/lib/x86_64-linux-gnu/libstdc++.so.6 ${fastvm_dir} || exit 2
# cp /lib/x86_64-linux-gnu/libgcc_s.so.1 ${fastvm_dir} || exit 2
cp /usr/lib/x86_64-linux-gnu/libffi.so.6 ${fastvm_dir} || exit 2
cp /usr/lib/x86_64-linux-gnu/libedit.so.2 ${fastvm_dir} || exit 2
cp /lib/x86_64-linux-gnu/libtinfo.so.5 ${fastvm_dir} || exit 2
cp /lib/x86_64-linux-gnu/libz.so.1 ${fastvm_dir} || exit 2
cp /lib/x86_64-linux-gnu/libbsd.so.0 ${fastvm_dir} || exit 2

# construct the file list
tee ${fastvm_dir}/file.list << END
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
(cd ${dist_dir}; tar -czf fastvm_v${version}.tar.gz fastvm_v${version})

# compile solidity
(cd solidity; make)

# copy libraries
mkdir -p ${solidity_dir}
cp ${dist_dir}/solc ${solidity_dir} || exit 3
cp /usr/lib/x86_64-linux-gnu/libboost_filesystem.so.1.58.0 ${solidity_dir} || exit 3
cp /usr/lib/x86_64-linux-gnu/libboost_program_options.so.1.58.0 ${solidity_dir} || exit 3
cp /usr/lib/x86_64-linux-gnu/libboost_regex.so.1.58.0 ${solidity_dir} || exit 3
cp /usr/lib/x86_64-linux-gnu/libboost_system.so.1.58.0 ${solidity_dir} || exit 3
cp /usr/lib/x86_64-linux-gnu/libjsoncpp.so.1 ${solidity_dir} || exit 3
# included in the `common` library
# cp /usr/lib/x86_64-linux-gnu/libstdc++.so.6 ${solidity_dir} || exit 3
# cp /lib/x86_64-linux-gnu/libgcc_s.so.1 ${solidity_dir} || exit 3
cp /usr/lib/x86_64-linux-gnu/libicui18n.so.55 ${solidity_dir} || exit 3
cp /usr/lib/x86_64-linux-gnu/libicuuc.so.55 ${solidity_dir} || exit 3
cp /usr/lib/x86_64-linux-gnu/libicudata.so.55 ${solidity_dir} || exit 3

# construct the file list
tee ${solidity_dir}/file.list << END
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
(cd ${dist_dir}; tar -czf solidity_v${version}.tar.gz solidity_v${version})
