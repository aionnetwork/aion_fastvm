# Please build the Ethereum EVMJIT project and copy libevmjit.a file to this folder,
# before you run the script.

all:
	g++ -O3 -I. main.cpp -L. -levmjit -L/usr/lib/llvm-5.0 -lLLVM-5.0
	LD_LIBRARY_PATH=. ./a.out
