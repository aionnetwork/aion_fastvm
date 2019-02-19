set(sources
	backends/evm/AbstractAssembly.h
	backends/evm/EVMAssembly.cpp
	backends/evm/EVMAssembly.h
	backends/evm/EVMCodeTransform.cpp
	backends/evm/EVMCodeTransform.h
)

add_library(julia ${sources})

target_link_libraries(julia PUBLIC evmasm devcore)