LLVM_INCLUDE=/usr/include/llvm-5.0
LLVM_C_INCLUDE=/usr/include/llvm-c-5.0
LLVM_LIB_DIR=/usr/lib/llvm-5.0/lib
LLVM_LIB_NAME=LLVM-5.0

JDK_INC=$(JAVA_HOME)/include

DIST=./dist

EVMJIT_NAME=libevmjit.so
EVMJIT_TARGET=$(DIST)/$(EVMJIT_NAME)

JNI_NAME=libfastvm.so
JNI_TARGET=$(DIST)/$(JNI_NAME)

.PHONY: all evmjit jni clean

all: evmjit

evmjit:
	g++ -std=c++0x -I. -I./include -I./libevmjit -I$(LLVM_INCLUDE) -I$(LLVM_C_INCLUDE) -O3 -Wall -fPIC \
./libevmjit/Arith128.cpp \
./libevmjit/Array.cpp \
./libevmjit/BasicBlock.cpp \
./libevmjit/Cache.cpp \
./libevmjit/Compiler.cpp \
./libevmjit/CompilerHelper.cpp \
./libevmjit/Endianness.cpp \
./libevmjit/ExecStats.cpp \
./libevmjit/Ext.cpp \
./libevmjit/GasMeter.cpp \
./libevmjit/Instruction.cpp \
./libevmjit/JIT.cpp \
./libevmjit/Memory.cpp \
./libevmjit/Optimizer.cpp \
./libevmjit/RuntimeManager.cpp \
./libevmjit/Type.cpp \
./libevmjit/Utils.cpp \
-L$(LLVM_LIB_DIR) -l$(LLVM_LIB_NAME) -Wl,--no-undefined -Wl,-soname=$(EVMJIT_NAME) -shared \
-o $(EVMJIT_TARGET)

jni:
	g++ -std=c++0x -I. -I./include -I$(JDK_INC) -I$(JDK_INC)/linux -O3 -Wall -fPIC \
./jni/org_aion_fastvm_FastVM.cpp \
-L${DIST} -levmjit -Wl,--no-undefined -Wl,-soname=$(JNI_NAME) -shared \
-o $(JNI_TARGET)

clean:
	rm -rf $(EVMJIT_TARGET)
	rm -rf $(JNI_TARGET)
