set(sources
	ABI.h
	Assertions.h
	Common.h
	CommonData.cpp
	CommonData.h
	CommonIO.cpp
	CommonIO.h
	Exceptions.cpp
	Exceptions.h
	FixedHash.h
	JSON.h
	SHA3.cpp
	SHA3.h
	SwarmHash.cpp
	SwarmHash.h
	UTF8.cpp
	UTF8.h
	vector_ref.h
	boost_multiprecision_number_compare_bug_workaround.hpp
	Whiskers.cpp
	Whiskers.h
)

add_library(devcore ${sources})
target_link_libraries(devcore PUBLIC jsoncpp ${Boost_FILESYSTEM_LIBRARIES} ${Boost_REGEX_LIBRARIES} ${Boost_SYSTEM_LIBRARIES} Threads::Threads)
target_include_directories(devcore PUBLIC "${CMAKE_SOURCE_DIR}")
target_include_directories(devcore SYSTEM PUBLIC ${Boost_INCLUDE_DIRS})