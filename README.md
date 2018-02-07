# Aion FastVM

Aion FastVM is an enhanced Ethereum Virtual Machine (EVM), featuring **128-bit** data word size for better performance. It uses LLVM JIT as execution engine and runs decentralized application at native speed.

Due to the architecture change, the instruction set and the energy cost of each instruction has been modified. In addition, we've updated the solidity compiler so that it can generate code for Aion FastVM.

See more at the [Wiki page](https://github.com/aionnetwork/aion_fastvm/wiki).

### Prerequisites

```
Operating system: Ubuntu 16.04 or above
```

### Build FastVM

```bash
sudo apt install build-essential llvm-4.0-dev
make
```

### Build Solidity compiler

```bash
cd solidity
sudo apt install build-essential libboost-all-dev libjsoncpp-dev
make
```

### Benchmark

Test code:

```
/*
 * trivial code to compute the sum of 1, 2, ..., 1024.
 */
uint8_t const code[] = {
    0x60, 0x00, // push i

    0x5b,
    0x80, // copy i
    0x61, 0x04, 0x00, // push 1024
    0x10, // 1024 < i
    0x60, 0x19, 0x57, // jump if true

    0x80, // copy i
    0x60, 0xE0, 0x51, // mload sum
    0x01, // sum += i
    0x60, 0xE0, 0x52, // mstore sum
    0x60, 0x01, // push 1
    0x01, // i += 1
    0x60, 0x02, 0x56, // jump

    0x5b,
    0x60, 0x10, 0x60, 0xE0, 0xF3 // RETURN (change 0x10 to 0x20 for 256-bit vm)
};
```

Environment:

```
OS: Linux ubuntu 16.04 amd64
Kernel: 4.13.0-32-generic
Memory: 15.7 GB
Processor: AMD Ryzen 7 1700X Eight-Core Processor x 16
```

Results:

```
Ethereum EVMJIT (revision: 34f15d95bd95232a91a61b0d98d17ded814ab074)

Gas used: 59507
Gas left: 4940493
Output size: 32
Output: 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 08 02 00
Time elapsed: 54 μs per execution


Aion FastVM

Energy used: 16418
Energy left: 4983582
Output size: 16
Output: 00 00 00 00 00 00 00 00 00 00 00 00 00 08 02 00
Time elapsed: 34 μs per execution
```

*More details can be found [here](https://github.com/aionnetwork/aion_fastvm/tree/master/bench).*
