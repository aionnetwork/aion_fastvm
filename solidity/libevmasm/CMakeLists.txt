set(sources
	Assembly.cpp
	Assembly.h
	AssemblyItem.cpp
	AssemblyItem.h
	BlockDeduplicator.cpp
	BlockDeduplicator.h
	CommonSubexpressionEliminator.cpp
	CommonSubexpressionEliminator.h
	ConstantOptimiser.cpp
	ConstantOptimiser.h
	ControlFlowGraph.cpp
	ControlFlowGraph.h
	EVMSchedule.h
	Exceptions.h
	ExpressionClasses.cpp
	ExpressionClasses.h
	GasMeter.cpp
	GasMeter.h
	Instruction.cpp
	Instruction.h
	KnownState.cpp
	KnownState.h
	LinkerObject.cpp
	LinkerObject.h
	PathGasMeter.cpp
	PathGasMeter.h
	PeepholeOptimiser.cpp
	PeepholeOptimiser.h
	SemanticInformation.cpp
	SemanticInformation.h
	SimplificationRules.cpp
	SimplificationRules.h
	SourceLocation.h
)

add_library(evmasm ${sources})
target_link_libraries(evmasm PUBLIC devcore)