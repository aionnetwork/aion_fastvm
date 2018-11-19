# Please build the Aion FastVM project and copy dist/libevmjit.so to this folder,
# before you run the script.

all:
	g++ -O3 -I. main.cpp -L. -levmjit -L/usr/lib/llvm-4.0 -lLLVM-4.0
	LD_LIBRARY_PATH=. ./a.out
