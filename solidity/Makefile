JSONCPP_INC=/usr/include/jsoncpp

DIST=../dist

SOLC_NAME=solc
SOLC_TARGET=$(DIST)/$(SOLC_NAME)

SRC := ./libjulia/backends/evm/EVMAssembly.cpp \
	./libjulia/backends/evm/EVMCodeTransform.cpp \
	./libevmasm/BlockDeduplicator.cpp \
	./libevmasm/LinkerObject.cpp \
	./libevmasm/Instruction.cpp \
	./libevmasm/ControlFlowGraph.cpp \
	./libevmasm/ExpressionClasses.cpp \
	./libevmasm/SemanticInformation.cpp \
	./libevmasm/AssemblyItem.cpp \
	./libevmasm/GasMeter.cpp \
	./libevmasm/CommonSubexpressionEliminator.cpp \
	./libevmasm/PathGasMeter.cpp \
	./libevmasm/PeepholeOptimiser.cpp \
	./libevmasm/Assembly.cpp \
	./libevmasm/KnownState.cpp \
	./libevmasm/ConstantOptimiser.cpp \
	./libevmasm/SimplificationRules.cpp \
	./libsolidity/inlineasm/AsmAnalysis.cpp \
	./libsolidity/inlineasm/AsmParser.cpp \
	./libsolidity/inlineasm/AsmScopeFiller.cpp \
	./libsolidity/inlineasm/AsmScope.cpp \
	./libsolidity/inlineasm/AsmPrinter.cpp \
	./libsolidity/inlineasm/AsmAnalysisInfo.cpp \
	./libsolidity/inlineasm/AsmCodeGen.cpp \
	./libsolidity/parsing/Parser.cpp \
	./libsolidity/parsing/Token.cpp \
	./libsolidity/parsing/Scanner.cpp \
	./libsolidity/parsing/DocStringParser.cpp \
	./libsolidity/parsing/ParserBase.cpp \
	./libsolidity/interface/SourceReferenceFormatter.cpp \
	./libsolidity/interface/Exceptions.cpp \
	./libsolidity/interface/Natspec.cpp \
	./libsolidity/interface/Version.cpp \
	./libsolidity/interface/ABI.cpp \
	./libsolidity/interface/ErrorReporter.cpp \
	./libsolidity/interface/AssemblyStack.cpp \
	./libsolidity/interface/StandardCompiler.cpp \
	./libsolidity/interface/CompilerStack.cpp \
	./libsolidity/interface/GasEstimator.cpp \
	./libsolidity/ast/ASTPrinter.cpp \
	./libsolidity/ast/ASTJsonConverter.cpp \
	./libsolidity/ast/Types.cpp \
	./libsolidity/ast/ASTAnnotations.cpp \
	./libsolidity/ast/AST.cpp \
	./libsolidity/ast/ASTUtils.cpp \
	./libsolidity/analysis/PostTypeChecker.cpp \
	./libsolidity/analysis/DeclarationContainer.cpp \
	./libsolidity/analysis/TypeChecker.cpp \
	./libsolidity/analysis/ReferencesResolver.cpp \
	./libsolidity/analysis/DocStringAnalyser.cpp \
	./libsolidity/analysis/SemVerHandler.cpp \
	./libsolidity/analysis/GlobalContext.cpp \
	./libsolidity/analysis/ConstantEvaluator.cpp \
	./libsolidity/analysis/SyntaxChecker.cpp \
	./libsolidity/analysis/NameAndTypeResolver.cpp \
	./libsolidity/analysis/StaticAnalyzer.cpp \
	./libsolidity/codegen/ExpressionCompiler.cpp \
	./libsolidity/codegen/CompilerUtils.cpp \
	./libsolidity/codegen/LValue.cpp \
	./libsolidity/codegen/ContractCompiler.cpp \
	./libsolidity/codegen/ArrayUtils.cpp \
	./libsolidity/codegen/Compiler.cpp \
	./libsolidity/codegen/CompilerContext.cpp \
	./libdevcore/SwarmHash.cpp \
	./libdevcore/UTF8.cpp \
	./libdevcore/Exceptions.cpp \
	./libdevcore/CommonIO.cpp \
	./libdevcore/Whiskers.cpp \
	./libdevcore/CommonData.cpp \
	./libdevcore/SHA3.cpp \
	./solc/jsonCompiler.cpp \
	./solc/main.cpp \
	./solc/CommandLineInterface.cpp

OBJS := $(patsubst %.cpp, %.o, $(SRC))

.PHONY: all

all: $(SOLC_TARGET)

$(SOLC_TARGET): $(OBJS)
	@echo "Generating $@"
	@g++ $(OBJS) -lboost_filesystem -lboost_program_options -lboost_regex -lboost_system -ljsoncpp -Wl,--no-undefined -o $@

%.o:%.cpp
	@echo "Compiling $^"
	@g++ -std=c++0x -I. -I$(JSONCPP_INC) -O0 -Wall -fPIC -c -g $^ -o $@

clean:
	rm -rf $(SOLC_TARGET) $(OBJS)
