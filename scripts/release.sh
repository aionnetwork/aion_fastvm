#!/bin/sh

if [ $# -eq 2 ]; then
    cd aion_fastvm 
else
    cd "$(dirname "$0")/.."
fi

fastvm_dir="fastvm_v$1"
solidity_dir="solidity_v$1"

# build fastvm and solidity compiler
make
make jni
cd solidity && make  && cd ../dist

# copy libraries
rm -r ${fastvm_dir}
mkdir ${fastvm_dir} && cd ${fastvm_dir}
cp /usr/lib/x86_64-linux-gnu/libLLVM-4.0.so.1 .
cd ..
cp libevmjit.so ${fastvm_dir}
cp libfastvm.so ${fastvm_dir}
cd ${fastvm_dir}

# construct the file list
tee file.list << END
libLLVM-4.0.so.1
libevmjit.so
libfastvm.so
END

# make archieve
cd ..
tar -czf ${fastvm_dir}.tar.gz ${fastvm_dir}


# copy libraries
rm -r ${solidity_dir} 
mkdir ${solidity_dir} && cd ${solidity_dir}
cp /usr/lib/x86_64-linux-gnu/libboost_filesystem.so.1.58.0 .
cp /usr/lib/x86_64-linux-gnu/libboost_program_options.so.1.58.0 .
cp /usr/lib/x86_64-linux-gnu/libboost_regex.so.1.58.0 .
cp /usr/lib/x86_64-linux-gnu/libboost_system.so.1.58.0 .
cp /usr/lib/x86_64-linux-gnu/libjsoncpp.so.1 .
cd ..
cp solc ${fastvm_dir}
cd  ${fastvm_dir}

# construct the file list
tee file.list << END
libboost_filesystem.so.1.58.0
libboost_program_options.so.1.58.0
libboost_regex.so.1.58.0
libboost_system.so.1.58.0
libjsoncpp.so.1
END

# make archieve
cd ..
tar -czf ${solidity_dir}.tar.gz ${solidity_dir}

if [ $# -eq 2 ]; then
  cp ${solidity_dir}.tar.gz ../../
  cp ${fastvm_dir}.tar.gz ../../
fi
