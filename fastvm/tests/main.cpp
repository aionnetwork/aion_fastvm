#include <ctime>
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>
#include <inttypes.h>
#include <string.h>

#include <gtest/gtest.h>

#include <evm.h>
#include <evmjit.h>
#include <Utils.h>

using namespace std;

int zero = 0;
int base = 1;
int verylow = 1;
int low = 1;
int mid = 1;
int high = 1;
int extcode = 1000;
int gbalance = 1000;
int sload = 1000;
int jumpdest = 1;
int sset = 20000;
int sreset = 8000;
int sclear = 15000;
int suicide = 5000;
int create = 200000;
int codedeposit = 1000;
int gcall = 1000;
int callvalue = 15000;
int callstipend = 2300;
int newaccount = 25000;
int exp = 1;
int expbyte = 1;
int memory = 1;
int txcreate = 200000;
int txdatazero = 4;
int txdatanonzero = 64;
int transaction = 21000;
int glog = 500;
int logdata = 20;
int logtopic = 500;
int sha3 = 30;
int sha3word = 6;
int gcopy = 3;
int blockhash = 20;

struct evm_instance* instance;
struct evm_message msg;

struct evm_address address = { 1, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f, 0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18, 0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E };
struct evm_address caller = { 2, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f, 0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18, 0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E };
struct evm_word balance = { 0, 0, 0, 0, 0, 0, 0, 0, 0x0D, 0xE0, 0xB6, 0xB3, 0xA7, 0x64, 0x00, 0x00 };
struct evm_word value = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x01, 0, 0, 0, 0 };
struct evm_hash block_hash = { 3, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f, 0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18, 0x19, 0x1a, 0x1b, 0x1c, 0x1d, 0x1e };


struct evm_tx_context tx_context = {
        { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x01, 0, 0, 0 }, // tx_gas_price
        { 4, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f, 0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18, 0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E }, // tx_origin
        { 5, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f, 0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18, 0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E }, // block_coinbase
        16, // block_number
        1501267050506L, // block_timestamp
        1024 * 1024L, // block_gas_limit
        { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x01, 0, 0, 0, 0, 0, 0 }, // block_difficulty
};

struct evm_word storage[0x1000000] = {};
bool storage_debug = false;

struct evm_address expected_code_addr = { 6, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f, 0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18, 0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E };
uint8_t expected_code_data[] = { 0x11, 0x22, 0x33, 0x44 };

struct evm_word log_topics[8] = {};
size_t log_topics_count = 0;
uint8_t log_data[1024];
size_t log_data_size;


struct evm_message call_msg;
struct evm_address call_output_addr = { 11, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f, 0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18, 0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E };
struct evm_word call_output = { 0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88, 0x99, 0xaa, 0xbb, 0xcc, 0xdd, 0xee, 0xff };

struct evm_address self_destruct_addr;
struct evm_address self_destruct_bene;

/**
 * evm_account_exists_fn mock
 */
int account_exists(struct evm_context* context,
                   const struct evm_address* address)
{
    if(address->bytes[7] == 0x0f) {
        return 0;
    } else {
        return 1;
    }
}

/**
 * evm_get_balance_fn mock
 */
void get_balance(struct evm_word* result,
                 struct evm_context* context,
                 const struct evm_address* addr)
{
    if (0 == memcmp(address.bytes, addr->bytes, sizeof(evm_address))) {
        *result = balance;
    } else {
        *result = {};
    }
}


/**
 * evm_get_code_fn mock
 */
size_t get_code(const uint8_t** result_code,
                struct evm_context* context,
                const struct evm_address* address)
{
    if (0 == memcmp(&expected_code_addr, address, sizeof(expected_code_addr))) {
        if (result_code)
            *result_code = (uint8_t const*) &expected_code_data;
        return sizeof(expected_code_data);
    } else {
        if (result_code)
            *result_code = nullptr;
        return 0;
    }
}

/**
 * evm_get_storage_fn mock
 */
void get_storage(struct evm_word* result,
                 struct evm_context* context,
                 const struct evm_address* address,
                 const struct evm_word* key)
{
    if (storage_debug) {
        printf("SLOAD: ");
        size_t i = 0;
        for (i = 0; i < sizeof(evm_word); i++) {
            printf("%02x ", key->bytes[i]);
        }
    }

    int x = ((key->bytes[13]) << 16) + ((key->bytes[14]) << 8) + key->bytes[15];
    *result = storage[x];

    if (storage_debug) {
        printf("= ");
        size_t i = 0;
        for (i = 0; i < sizeof(evm_word); i++) {
            printf("%02x ", storage[x].bytes[i]);
        }
        printf("\n");
    }
}

/**
 * evm_set_storage_fn mock
 */
void set_storage(struct evm_context* context,
                 const struct evm_address* address,
                 const struct evm_word* key,
                 const struct evm_word* value)
{
    if (storage_debug) {
        printf("SSTORE: ");
        size_t i = 0;
        for (i = 0; i < sizeof(evm_word); i++) {
            printf("%02x ", key->bytes[i]);
        }
        printf("= ");
        i = 0;
        for (i = 0; i < sizeof(evm_word); i++) {
            printf("%02x ", value->bytes[i]);
        }
        printf("\n");
    }

    int x = ((key->bytes[13]) << 16) + ((key->bytes[14]) << 8) + key->bytes[15];
    storage[x] = *value;
}

/**
 * evm_selfdestruct_fn mock
 */
void selfdestruct(struct evm_context* context,
                  const struct evm_address* address,
                  const struct evm_address* beneficiary)
{
    self_destruct_addr = *address;
    self_destruct_bene = *beneficiary;
}

/**
 * evm_call_fn mock
 */
void call(struct evm_result* result,
          struct evm_context* context,
          const struct evm_message* msg)
{
    call_msg = *msg;

    // memory leak here, but it's fine since we're only testing
    call_msg.input = (const uint8_t*) malloc(msg->input_size);
    memcpy((void*) call_msg.input, msg->input, msg->input_size);

    if(msg->input[14] == 0xfd) {
        result->status_code = EVM_REVERT;
    } else {
        result->status_code = EVM_SUCCESS;
    }

    result->gas_left = msg->gas;


    if (msg->kind == EVM_CREATE) {
        result->output_data = (const uint8_t*) &call_output_addr;
        result->output_size = sizeof(call_output_addr);
    } else {
        result->output_data = (const uint8_t*) &call_output;
        result->output_size = sizeof(call_output);
    }
    result->release = NULL;
    result->reserved.context = NULL;
}

/**
 * evm_get_tx_context_fn mock
 */
void get_tx_context(struct evm_tx_context* result,
                    struct evm_context* context)
{
    memcpy(result, &tx_context, sizeof(evm_tx_context));
}

/**
 * evm_get_block_hash_fn mock
 */
void get_block_hash(struct evm_hash* result,
                    struct evm_context* context,
                    int64_t number)
{
    memcpy(result, &block_hash, sizeof(block_hash));
}

/**
 * evm_log_fn mock
 */
void log(struct evm_context* context,
         const struct evm_address* address,
         const uint8_t* data,
         size_t data_size,
         const struct evm_word topics[],
         size_t topics_count)
{
    log_topics_count = topics_count;
    for (size_t i = 0; i < topics_count; i++) {
        log_topics[i] = topics[i];
    }
    log_data_size = data_size;
    memcpy(log_data, data, min(data_size, sizeof(log_data)));
}

static const struct evm_context_fn_table ctx_fn_table = {
    account_exists,
    get_storage,
    set_storage,
    get_balance,
    get_code,
    selfdestruct,
    call,
    get_tx_context,
    get_block_hash,
    log
};

struct evm_context context = { &ctx_fn_table };

int main(int argc, char **argv)
{
    instance = evmjit_create();

    ::testing::InitGoogleTest(&argc, argv);
    return RUN_ALL_TESTS();

    instance->destroy(instance);
}

void setup_message(const uint8_t *code,
                   size_t code_size,
                   const uint8_t *input,
                   size_t input_size,
                   int64_t gas,
                   struct evm_word _value = value)
{
    msg.address = address;
    msg.caller = caller;
    msg.value = _value;
    msg.input = input;
    msg.input_size = input_size;
    dev::evmjit::keccak(code, code_size, msg.code_hash.bytes);
    msg.gas = gas;
    msg.flags = 0;
}

void print_result(struct evm_result *result)
{
    printf("\n  Gas left: %ld\n", result->gas_left);
    printf("  Output size: %zd\n", result->output_size);
    printf("  Output: ");
    size_t i = 0;
    for (i = 0; i < result->output_size; i++) {
        printf("%02x ", result->output_data[i]);
    }
    printf("\n\n");
}

void release_result(struct evm_result *result, bool reset_storage = true)
{
    if (result->release) {
        result->release(result);
    }

    if (reset_storage) {
        memset(&storage, 0, sizeof(storage));
    }
    memset(&log_topics, 0, sizeof(log_topics));
    memset(&log_data, 0, sizeof(log_data));
    memset(&log_data_size, 0, sizeof(log_data_size));
    memset(&call_msg, 0, sizeof(call_msg));
    memset(&self_destruct_addr, 0, sizeof(self_destruct_addr));
    memset(&self_destruct_bene, 0, sizeof(self_destruct_bene));
}

void address2hash(struct evm_address *address, evm_hash *hash)
{
    memcpy((uint8_t *)(hash), address, sizeof(evm_address));
}

int char2int(char input)
{
    if (input >= '0' && input <= '9')
        return input - '0';
    if (input >= 'A' && input <= 'F')
        return input - 'A' + 10;
    if (input >= 'a' && input <= 'f')
        return input - 'a' + 10;
    throw std::invalid_argument("Invalid input string");
}

void hex2bin(const char* src, char* target)
{
    while(*src && src[1]) {
        *(target++) = char2int(*src)*16 + char2int(src[1]);
        src += 2;
    }
}

//======================================
// 0s: Stop and Arithmetic Operations
//======================================

TEST(instructions, testSTOP) {
    uint8_t const code[] = {
            0x60, 0x00, 0x00, 0x60, 0x00
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));

    print_result(&result);
    ASSERT_EQ(gas - verylow, result.gas_left);
    release_result(&result);
}

TEST(instructions, testSTOPCREATE) {
    uint8_t const code[] = {
            0x60, 0x00,

            0x60, 0x10, // size PUSH
            0x60, 0xE0, // offset PUSH
            0x60, 0x05, // value PUSH
            0x00, // STOP
            0xF0, // CREATE

            0x60, 0x00
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));

    print_result(&result);
    ASSERT_EQ(gas - ((verylow*4) + zero), result.gas_left);
    release_result(&result);
}

TEST(instructions, testSTOPCALL) {
    uint8_t const code[] = {
            0x60, 0x00,
            0x60, 0xF0, // output offset
            0x60, 0x10, // input size
            0x60, 0xE0, // input offset
            0x60, 0x05, // value
            0x6F, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88, //
                  0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88, //
            0x00,
            0x6F, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, //
                  0x00, 0x00, 0x00, 0x00, 0x11, 0x22, 0x33, 0x44, // code address
            0x61, 0x13, 0x88, // gas (5000)
            0xF1, // CALL

            0x60, 0x00
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));

    print_result(&result);
    ASSERT_EQ(gas - ((verylow*6) + zero), result.gas_left);
    release_result(&result);
}

TEST(instructions, testADD) {
    uint8_t const code[] = {
            0x6F, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, //
                  0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, //PUSH max_int
            0x60, 0x02, // PUSH 0x02
            0x01, // ADD

            0x60, 0xE0, // PUSH1 0xE0
            0x52, // MSTORE
            0x60, 0x10, // PUSH1 0x10
            0x60, 0xE0, // PUSH1 0xE0
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + (words*words)/512 + 1;
    ASSERT_EQ(gas - ((verylow*6) + mstore_size), result.gas_left);
    print_result(&result);
    struct evm_word gt = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x01};

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testADD2) {
    uint8_t const code[] = {
            0x60, 0x02, // PUSH
            0x60, 0x04, // PUSH
            0x01, // ADD
            0x60, 0xE0, //PUSH
            0x52, //MSTORE
            0x60, 0x10, //PUSH
            0x60, 0xE0, //PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + (words*words) / 512 + 1;
    ASSERT_EQ(gas - ((verylow*6) + mstore_size + zero), result.gas_left);
    print_result(&result);
    struct evm_word gt = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x06};
    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testADDZERO) {
    uint8_t const code[] = {
            0x60, 0x02, // PUSH
            0x60, 0x00, // PUSH
            0x01, // ADD
            0x60, 0xE0, //PUSH
            0x52, //MSTORE
            0x60, 0x10, //PUSH
            0x60, 0xE0, //PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + (words*words) / 512 + 1;
    ASSERT_EQ(gas - ((verylow*6) + mstore_size + zero), result.gas_left);
    print_result(&result);
    struct evm_word gt = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x02};
    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testMUL) {
    uint8_t const code[] = {
            0x60, 0x04, // PUSH 0x04
            0x60, 0x04, // PUSH 0x04
            0x02, // MUL

            0x60, 0xE0,
            0x52,
            0x60, 0x10,
            0x60, 0xE0,
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + (words*words)/512 + 1;
    ASSERT_EQ(gas - ((verylow*5) + low + mstore_size), result.gas_left);
    print_result(&result);
    struct evm_word gt = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x10};

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testMULZERO) {
    uint8_t const code[] = {
            0x60, 0x04, // PUSH 0x04
            0x60, 0x00, // PUSH 0x00
            0x02, // MUL

            0x60, 0xE0,
            0x52,
            0x60, 0x10,
            0x60, 0xE0,
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + (words*words)/512 + 1;
    ASSERT_EQ(gas - ((verylow*5) + low + mstore_size), result.gas_left);
    print_result(&result);
    struct evm_word gt = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x00};

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testSUB) {
    uint8_t const code[] = {
            0x60, 0x04, // PUSH 0x04
            0x60, 0x08, // PUSH 0x08
            0x03, // SUB

            0x60, 0xE0, 0x52, 0x60, 0x10, 0x60, 0xE0, 0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + (words*words)/512 + 1;
    ASSERT_EQ(gas - ((verylow*6) + mstore_size), result.gas_left);
    print_result(&result);
    struct evm_word gt = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x04};

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testSUBNEG) {
    uint8_t const code[] = {
            0x60, 0x08, // PUSH 0x08
            0x60, 0x04, // PUSH 0x04
            0x03, // SUB

            0x60, 0xE0, 0x52, 0x60, 0x10, 0x60, 0xE0, 0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + (words*words)/512 + 1;
    ASSERT_EQ(gas - ((verylow*6) + mstore_size), result.gas_left);
    print_result(&result);
    struct evm_word gt = {0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
                          0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFC};

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testDIV) {
    uint8_t const code[] = {
            0x60, 0x04, // PUSH 0x04
            0x60, 0x08, // PUSH 0x08
            0x04, // DIV

            0x60, 0xE0, 0x52, 0x60, 0x10, 0x60, 0xE0, 0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + (words*words)/512 + 1;
    ASSERT_EQ(gas - ((verylow*5) + low + mstore_size), result.gas_left);
    print_result(&result);
    struct evm_word gt = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x02};

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testDIVZERO) {
    uint8_t const code[] = {
            0x60, 0x00, // PUSH 0x00
            0x60, 0x08, // PUSH 0x08
            0x04, // DIV

            0x60, 0xE0, 0x52, 0x60, 0x10, 0x60, 0xE0, 0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + (words*words)/512 + 1;
    ASSERT_EQ(gas - ((verylow*5) + low + mstore_size), result.gas_left);
    print_result(&result);
    struct evm_word gt = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x00};

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testSDIV) {
    uint8_t const code[] = {
            0x60, 0x02, // PUSH 0x02
            0x6F, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, //
                  0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFC, // PUSH -4
            0x05, // SDIV

            0x60, 0xE0,
            0x52,
            0x60, 0x10,
            0x60, 0xE0,
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + (words*words)/512  + 1;
    ASSERT_EQ(gas - ((verylow*6) + mstore_size), result.gas_left);
    print_result(&result);
    struct evm_word gt = {0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
                          0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFE};

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testSDIVZERO) {
    uint8_t const code[] = {
            0x60, 0x00, // PUSH 0x00
            0x6F, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, //
                  0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFC, // PUSH -4
            0x05, // SDIV

            0x60, 0xE0,
            0x52,
            0x60, 0x10,
            0x60, 0xE0,
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + (words*words)/512  + 1;
    ASSERT_EQ(gas - ((verylow*6) + mstore_size), result.gas_left);
    print_result(&result);
    struct evm_word gt = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x00};

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testSDIVNEG) {
    uint8_t const code[] = {
            0x60, 0x00, // PUSH 0x00
            0x6F, 0x08, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, //
                  0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, // PUSH -4
            0x05, // SDIV

            0x60, 0xE0, 0x52, 0x60, 0x10, 0x60, 0xE0, 0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + (words*words)/512 + 1;
    ASSERT_EQ(gas - ((verylow*5) + low + mstore_size), result.gas_left);
    print_result(&result);
    struct evm_word gt = {0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, //
                          0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testMOD) {
    uint8_t const code[] = {
            0x60, 0x03, // PUSH 0x03
            0x60, 0x08, // PUSH 0x08
            0x06, // MOD

            0x60, 0xE0, 0x52, 0x60, 0x10, 0x60, 0xE0, 0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words) / 512) + 1;
    ASSERT_EQ(gas - ((verylow*5) + low + mstore_size), result.gas_left);
    print_result(&result);
    struct evm_word gt = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x02};

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testMODZERO) {
    uint8_t const code[] = {
            0x60, 0x00, // PUSH 0x00
            0x60, 0x08, // PUSH 0x08
            0x06, // MOD

            0x60, 0xE0, 0x52, 0x60, 0x10, 0x60, 0xE0, 0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words) / 512) + 1;
    ASSERT_EQ(gas - ((verylow*5) + low + mstore_size), result.gas_left);
    print_result(&result);
    struct evm_word gt = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x00};

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testSMOD) {
    uint8_t const code[] = {
            0x60, 0x03, // PUSH 0x03
            0x6F, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, //
                  0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xF8, // PUSH -8
            0x07, // SMOD

            0x60, 0xE0, 0x52, 0x60, 0x10, 0x60, 0xE0, 0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words) / 512) + 1;
    ASSERT_EQ(gas - ((verylow*5) + low + mstore_size), result.gas_left);
    print_result(&result);
    struct evm_word gt = {0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
                          0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFE};

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testSMODZERO) {
    uint8_t const code[] = {
            0x60, 0x00, // PUSH 0x00
            0x6F, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, //
                  0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xF8, // PUSH -8
            0x07, // SMOD

            0x60, 0xE0, 0x52, 0x60, 0x10, 0x60, 0xE0, 0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words) / 512) + 1;
    ASSERT_EQ(gas - ((verylow*5) + low + mstore_size), result.gas_left);
    print_result(&result);
    struct evm_word gt = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x00};

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testADDMOD) {
    uint8_t const code[] = {
            0x60, 0x03, // PUSH 0x03
            0x60, 0x04, // PUSH 0x04
            0x60, 0x04, // PUSH 0x04
            0x08, // ADDMOD

            0x60, 0xE0, //PUSH 0xE0
            0x52, // MSTORE
            0x60, 0x10, 0x60, 0xE0, // PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words) / 512) + 1;
    ASSERT_EQ(gas - ((verylow*6) + mid + mstore_size), result.gas_left);
    print_result(&result);
    struct evm_word gt = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x02};

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testADDMODZERO) {
    uint8_t const code[] = {
            0x60, 0x00, // PUSH 0x00
            0x60, 0x04, // PUSH 0x04
            0x60, 0x04, // PUSH 0x04
            0x08, // ADDMOD

            0x60, 0xE0, //PUSH 0xE0
            0x52, // MSTORE
            0x60, 0x10, 0x60, 0xE0, // PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words) / 512) + 1;
    ASSERT_EQ(gas - ((verylow*6) + mid + mstore_size), result.gas_left);
    print_result(&result);
    struct evm_word gt = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x00};

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testADDMOD_fromCallData) {
    uint8_t const code[] = {
            0x60, 0x20, 0x35, // CALLDATALOAD
            0x60, 0x10, 0x35, // CALLDATALOAD
            0x60, 0x00, 0x35, // CALLDATALOAD
            0x08, // ADDMOD

            0x60, 0xE0, //PUSH
            0x52, // MSTORE
            0x60, 0x10, 0x60, 0xE0, 0xF3 // RETURN
    };
    uint8_t const input[] = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3,
                              0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 4,
                              0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 5,
    };
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));

    print_result(&result);
    struct evm_word gt = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x02};

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testMULMOD) {
    uint8_t const code[] = {
            0x60, 0x03, // PUSH 0x03
            0x60, 0x04, // PUSH 0x04
            0x60, 0x04, // PUSH 0x04
            0x09, // MULMOD

            0x60, 0xE0, // PUSH 0xE0
            0x52, // MSTORE
            0x60, 0x10, 0x60, 0xE0, // PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words) / 512) + 1;
    ASSERT_EQ(gas - ((verylow*6) + mid + mstore_size), result.gas_left);
    print_result(&result);
    struct evm_word gt = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x01};

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testMULMODZERO) {
    uint8_t const code[] = {
            0x60, 0x00, // PUSH 0x03
            0x60, 0x04, // PUSH 0x04
            0x60, 0x04, // PUSH 0x04
            0x09, // MULMOD

            0x60, 0xE0, // PUSH 0xE0
            0x52, // MSTORE + MEM
            0x60, 0x10, 0x60, 0xE0, // PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words) / 512) + 1;
    ASSERT_EQ(gas - ((verylow*6) + mid + mstore_size), result.gas_left);
    print_result(&result);
    struct evm_word gt = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x00};

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}


TEST(instructions, testEXP) {
    uint8_t const code[] = {
            0x60, 0x03, // PUSH 0x03
            0x60, 0x04, // PUSH 0x04
            0x0A, // EXP

            0x60, 0xE0,  // PUSH
            0x52, // MSTORE
            0x60, 0x10,
            0x60, 0xE0, // PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words) / 512) + 1;
    int32_t logexpval = 0.25;
    ASSERT_EQ(gas - ((verylow*5) + exp + (expbyte*(1+logexpval)) + mstore_size), result.gas_left);
    print_result(&result);
    struct evm_word gt = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x40};

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testEXPZERO) {
    uint8_t const code[] = {
            0x60, 0x00, // PUSH 0x03
            0x60, 0x04, // PUSH 0x04
            0x0A, // EXP

            0x60, 0xE0,  // PUSH
            0x52, // MSTORE
            0x60, 0x10, 0x60, 0xE0, // PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words) / 512) + 1;
    ASSERT_EQ(gas - ((verylow*5) + exp + mstore_size), result.gas_left);
    print_result(&result);
    struct evm_word gt = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x01};

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testEXPZEROBASE) {
    uint8_t const code[] = {
            0x60, 0x03, // PUSH 0x03
            0x60, 0x00, // PUSH 0x00
            0x0A, // EXP

            0x60, 0xE0,  // PUSH
            0x52, // MSTORE
            0x60, 0x10, 0x60, 0xE0, // PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words) / 512) + 1;
    int32_t logexpval = 0.5;
    ASSERT_EQ(gas - ((verylow*5) + exp + (expbyte*(1+logexpval)) + mstore_size), result.gas_left);
    print_result(&result);
    struct evm_word gt = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x00};

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testSIGNEXTEND) {
    uint8_t const code[] = {
            0x61, 0x80, 0x00, // PUSH 0x80
            0x60, 0x01, // PUSH 0x01 (2 bytes => 16 bytes)
            0x0B, // SIGNEXTEND

            0x60, 0xE0, // PUSH 0xE0
            0x52, // MSTORE
            0x60, 0x10, 0x60, 0xE0, // PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words) / 512) + 1;
    ASSERT_EQ(gas - ((verylow*5) + low + mstore_size), result.gas_left);
    print_result(&result);
    struct evm_word gt = {0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
                          0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0x80, 0x00};

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testSIGNEXTEND2) {
    uint8_t const code[] = {
            0x6F, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, //
                  0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, // PUSH
            0x60, 0x01, // PUSH 0x01 (2 bytes => 16 bytes)
            0x0B, // SIGNEXTEND

            0x60, 0xE0, // PUSH 0xE0
            0x52, // MSTORE
            0x60, 0x10, 0x60, 0xE0, // PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words) / 512) + 1;
    ASSERT_EQ(gas - ((verylow*5) + low + mstore_size), result.gas_left);
    print_result(&result);
    struct evm_word gt = {0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
                          0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF};

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

//======================================
// 10s: Comparison & Bitwise Logic Operations
//======================================

TEST(instructions, testLT) {
    uint8_t const code[] = {
            0x6F, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, //
                  0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, // PUSH
            0x60, 0x01, // PUSH 0x01
            0x10, // LT

            0x60, 0xE0, //PUSH 0xE0
            0x52, //MSTORE
            0x60, 0x10, 0x60, 0xE0, // PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words) / 512) + 1;
    ASSERT_EQ(gas - ((verylow*6) + mstore_size), result.gas_left);
    print_result(&result);
    struct evm_word gt = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x01};

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testLTFALSE) {
    uint8_t const code[] = {
            0x60, 0x01, // PUSH
            0x6F, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, //
                  0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, // PUSH
            0x10, // LT

            0x60, 0xE0, //PUSH 0xE0
            0x52, //MSTORE
            0x60, 0x10, 0x60, 0xE0, // PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words) / 512) + 1;
    ASSERT_EQ(gas - ((verylow*6) + mstore_size), result.gas_left);
    print_result(&result);
    struct evm_word gt = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x00};

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testGT) {
    uint8_t const code[] = {
            0x60, 0x01, // PUSH 0x01
            0x6F, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, //
                  0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, // PUSH
            0x12, // GT

            0x60, 0xE0, // PUSH 0xE0
            0x52, // MSTORE
            0x60, 0x10, 0x60, 0xE0, // PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words) / 512) + 1;
    ASSERT_EQ(gas - ((verylow*6) + mstore_size), result.gas_left);
    print_result(&result);
    struct evm_word gt = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x01};

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testGTFALSE) {
    uint8_t const code[] = {
            0x6F, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, //
                  0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, // PUSH
            0x60, 0x01, // PUSH
            0x12, // GT

            0x60, 0xE0, // PUSH 0xE0
            0x52, // MSTORE
            0x60, 0x10, 0x60, 0xE0, // PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words) / 512) + 1;
    ASSERT_EQ(gas - ((verylow*6) + mstore_size), result.gas_left);
    print_result(&result);
    struct evm_word gt = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x00};

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testSLT) {
    uint8_t const code[] = {
            0x60, 0x01, // PUSH 0x01
            0x6F, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, //
                  0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, // PUSH
            0x12, // SLT

            0x60, 0xE0, 0x52, 0x60, 0x10, 0x60, 0xE0, 0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words) / 512) + 1;
    ASSERT_EQ(gas - ((verylow*6) + mstore_size), result.gas_left);
    print_result(&result);
    struct evm_word gt = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x01};

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testSLTFALSE) {
    uint8_t const code[] = {
            0x6F, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, //
                  0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, // PUSH
            0x60, 0x01, // PUSH 0x01
            0x12, // SLT

            0x60, 0xE0, 0x52, 0x60, 0x10, 0x60, 0xE0, 0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words) / 512) + 1;
    ASSERT_EQ(gas - ((verylow*6) + mstore_size), result.gas_left);
    print_result(&result);
    struct evm_word gt = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x00};

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testSGT) {
    uint8_t const code[] = {
            0x6F, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, //
                  0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, // PUSH
            0x60, 0x01, // PUSH 0x01
            0x13, // SGT

            0x60, 0xE0, 0x52, 0x60, 0x10, 0x60, 0xE0, 0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words) / 512) + 1;
    ASSERT_EQ(gas - ((verylow*6) + mstore_size), result.gas_left);
    print_result(&result);
    struct evm_word gt = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x01};

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testSGTFALSE) {
    uint8_t const code[] = {
            0x60, 0x01, // PUSH 0x01
            0x6F, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, //
                  0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, // PUSH

            0x13, // SGT

            0x60, 0xE0, 0x52, 0x60, 0x10, 0x60, 0xE0, 0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words) / 512) + 1;
    ASSERT_EQ(gas - ((verylow*6) + mstore_size), result.gas_left);
    print_result(&result);
    struct evm_word gt = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x00};

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testEQ) {
    uint8_t const code[] = {
            0x6F, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, //
                  0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, // PUSH
            0x6F, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, //
                  0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, // PUSH
            0x14, // EQ

            0x60, 0xE0, 0x52, 0x60, 0x10, 0x60, 0xE0, 0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words) / 512) + 1;
    ASSERT_EQ(gas - ((verylow*6) + mstore_size), result.gas_left);
    print_result(&result);
    struct evm_word gt = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x01};

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testNOTEQ) {
    uint8_t const code[] = {
            0x6F, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, //
                  0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, // PUSH
            0x60, 0x01, // PUSH
            0x14, // EQ

            0x60, 0xE0, 0x52, 0x60, 0x10, 0x60, 0xE0, 0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words) / 512) + 1;
    ASSERT_EQ(gas - ((verylow*6) + mstore_size), result.gas_left);
    print_result(&result);
    struct evm_word gt = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x00};

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testISZERO) {
    uint8_t const code[] = {
            0x60, 0x00, // PUSH 0x00
            0x15, // ISZERO

            0x60, 0xE0, //PUSH 0xE0
            0x52,  // MSTORE
            0x60, 0x10, 0x60, 0xE0, // PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words) / 512) + 1;
    ASSERT_EQ(gas - ((verylow*5) + mstore_size), result.gas_left);
    print_result(&result);
    struct evm_word gt = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x01};

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testNOTISZERO) {
    uint8_t const code[] = {
            0x60, 0x01, // PUSH 0x00
            0x15, // ISZERO 3

            0x60, 0xE0, //PUSH 0xE0
            0x52,  // MSTORE
            0x60, 0x10, 0x60, 0xE0, // PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words) / 512) + 1;
    ASSERT_EQ(gas - ((verylow*5) + mstore_size), result.gas_left);
    print_result(&result);
    struct evm_word gt = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x00};

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testAND) {
    uint8_t const code[] = {
            0x60, 0x55, // PUSH 0x55
            0x60, 0xAA, // PUSH 0xAA
            0x16, // AND

            0x60, 0xE0, 0x52, 0x60, 0x10, 0x60, 0xE0, 0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words) / 512) + 1;
    ASSERT_EQ(gas - ((verylow*6) + mstore_size), result.gas_left);
    print_result(&result);
    struct evm_word gt = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x00};

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testANDMAX) {
    uint8_t const code[] = {
            0x6F, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, //
                  0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, // PUSH 0x55
            0x60, 0xAA, // PUSH 0xAA
            0x16, // AND

            0x60, 0xE0, 0x52, 0x60, 0x10, 0x60, 0xE0, 0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words) / 512) + 1;
    ASSERT_EQ(gas - ((verylow*6) + mstore_size), result.gas_left);
    print_result(&result);
    struct evm_word gt = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0xAA};

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testOR) {
    uint8_t const code[] = {
            0x60, 0x55, // PUSH 0x55
            0x60, 0xAA, // PUSH 0xAA
            0x17, // OR

            0x60, 0xE0, 0x52, 0x60, 0x10, 0x60, 0xE0, 0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words) / 512) + 1;
    ASSERT_EQ(gas - ((verylow*6) + mstore_size), result.gas_left);
    print_result(&result);
    struct evm_word gt = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0xFF};

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testORMAX) {
    uint8_t const code[] = {
            0x60, 0x55, // PUSH 0x55
            0x6F, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, //
                  0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, // PUSH 0xAA
            0x17, // OR

            0x60, 0xE0, 0x52, 0x60, 0x10, 0x60, 0xE0, 0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words) / 512) + 1;
    ASSERT_EQ(gas - ((verylow*6) + mstore_size), result.gas_left);
    print_result(&result);
    struct evm_word gt = {0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, //
                          0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF};

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testXOR) {
    uint8_t const code[] = {
            0x60, 0x55, // PUSH 0x55
            0x60, 0xAA, // PUSH 0xAA
            0x18, // XOR

            0x60, 0xE0, 0x52, 0x60, 0x10, 0x60, 0xE0, 0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words) / 512) + 1;
    ASSERT_EQ(gas - ((verylow*6) + mstore_size), result.gas_left);
    print_result(&result);
    struct evm_word gt = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0xFF};

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testXORMAX) {
    uint8_t const code[] = {
            0x6F, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, //
                  0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, // PUSH 0x55
            0x60, 0xAA, // PUSH 0xAA
            0x18, // XOR

            0x60, 0xE0, 0x52, 0x60, 0x10, 0x60, 0xE0, 0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 200000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words) / 512) + 1;
    ASSERT_EQ(gas - ((verylow*6) + mstore_size), result.gas_left);
    print_result(&result);
    struct evm_word gt = { 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, //
                           0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0x55 };

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testNOT) {
    uint8_t const code[] = {
            0x60, 0x55, // PUSH 0x55
            0x19, // NOT

            0x60, 0xE0, 0x52, 0x60, 0x10, 0x60, 0xE0, 0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words) / 512) + 1;
    ASSERT_EQ(gas - ((verylow*5) + mstore_size), result.gas_left);
    print_result(&result);
    struct evm_word gt = {0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xAA};

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testBYTE) {
    uint8_t const code[] = {
            0x6F, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88, //
                  0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88, // PUSH
            0x60, 0x02,
            0x1A, // BYTE

            0x60, 0xE0, 0x52, 0x60, 0x10, 0x60, 0xE0, 0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words) / 512) + 1;
    ASSERT_EQ(gas - ((verylow*6) + mstore_size), result.gas_left);
    print_result(&result);
    struct evm_word gt = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x33};

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

//======================================
// 20s: SHA3
//======================================

TEST(instructions, testSHA3) {
    uint8_t const code[] = {
            0x6F, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88, //
                  0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88, // PUSH
            0x60, 0xE0, 0x52, // MSTORE to 0xE0
            0x6F, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88, //
                  0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88, // PUSH
            0x60, 0xF0, 0x52, // MSTORE to 0xF0
            0x60, 0x20, // size
            0x60, 0xE0, // offset
            0x20, // SHA3

            0x60, 0xE0, 0x52, // PUSH 0xE0
            0x60, 0xF0, 0x52, // PUSH 0xF0
            0x60, 0x20, 0x60, 0xE0, // PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words) / 512) + 1;
    int32_t extra_mstore = 1;
    ASSERT_EQ(gas - ((verylow*10) + mstore_size + (extra_mstore*3) + sha3 + (sha3word*1)), result.gas_left);
    print_result(&result);
    struct evm_hash gt = {};
    uint8_t const data[] = {
            0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88,
            0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88,
            0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88,
            0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88
    };
    dev::evmjit::keccak(data, 32, (uint8_t *)&gt);

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

//======================================
// 30s: Environmental Information
//======================================

TEST(instructions, testADDRESS) {
    uint8_t const code[] = {
            0x30, // ADDRESS

            0x60, 0xE0, 0x52, // PUSH and MSTORE
            0x60, 0xF0, 0x52, // PUSH and MSTORE
            0x60, 0x20, // PUSH
            0x60, 0xE0, // PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words) / 512) + 1;
    int32_t extra_mstore = 1;
    ASSERT_EQ(gas - (base + (verylow*4) + mstore_size + (extra_mstore*1)), result.gas_left);
    print_result(&result);
    struct evm_hash gt;
    address2hash(&address, &gt);

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testBALANCE) {
    uint8_t const code[] = {
            0x6F, 0x0f, 0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, //
                  0x17, 0x18, 0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E, //
            0x6F, 0x01, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, //
                  0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, // PUSH an address
            0x31, // BALANCE 400

            0x60, 0xE0, // PUSH
            0x52, // MSTORE
            0x60, 0x10, // PUSH
            0x60, 0xE0, // PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words) / 512) + 1;
    ASSERT_EQ(gas - ((verylow*5) + gbalance + mstore_size), result.gas_left);
    print_result(&result);
    struct evm_word &gt = balance;

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testNOTBALANCE) {
    uint8_t const code[] = {
            0x60, 0x00, // PUSH
            0x60, 0x00, // PUSH
            0x31, // BALANCE

            0x60, 0x11,
            0x60, 0xE0, // PUSH
            0x52, // MSTORE
            0x60, 0x10, // PUSH
            0x60, 0xE0, // PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words) / 512) + 1;
    ASSERT_EQ(gas - ((verylow*6) + gbalance + mstore_size), result.gas_left);
    print_result(&result);
    struct evm_word gt = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x11 };

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}


TEST(instructions, testORIGIN) {
    uint8_t const code[] = {
            0x32, // ORIGIN

            0x60, 0xE0, 0x52, // PUSH and MSTORE
            0x60, 0xF0, 0x52, // PUSH and MSTORE
            0x60, 0x20, // PUSH
            0x60, 0xE0, // PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words) / 512) + 1;
    int32_t extra_memory = 1;
    ASSERT_EQ(gas - (base + (verylow*4) + mstore_size + (extra_memory*1)), result.gas_left);
    print_result(&result);
    struct evm_hash gt;
    address2hash(&tx_context.tx_origin, &gt);

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testCALLER) {
    uint8_t const code[] = {
            0x33, // CALLER

            0x60, 0xE0, 0x52, // PUSH and MSTORE
            0x60, 0xF0, 0x52, // PUSH and MSTORE
            0x60, 0x20, // PUSH
            0x60, 0xE0, // PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words) / 512) + 1;
    int32_t extra_memory = 1;
    ASSERT_EQ(gas - (base + (verylow*4) + mstore_size + (extra_memory*1)), result.gas_left);
    print_result(&result);
    struct evm_hash gt;
    address2hash(&caller, &gt);

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testCALLVALUE) {
    uint8_t const code[] = {
            0x34, // CALLVALUE

            0x60, 0xE0, // PUSH
            0x52, // MSTORE
            0x60, 0x10, // PUSH
            0x60, 0xE0, // PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words) / 512) + 1;
    ASSERT_EQ(gas - (base + (verylow*3) + mstore_size), result.gas_left);
    print_result(&result);
    struct evm_word &gt = value;

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testCALLVALUEPUSH) {
    uint8_t const code[] = {
            0x60, 0x01,
            0x61, 0x01, 0x11,
            0x34, // CALLVALUE

            0x60, 0xE0, // PUSH
            0x52, // MSTORE
            0x60, 0x10, // PUSH
            0x60, 0xE0, // PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words) / 512) + 1;
    ASSERT_EQ(gas - (base + (verylow*5) + mstore_size), result.gas_left);
    print_result(&result);
    struct evm_word &gt = value;

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testCALLDATALOAD) {
    uint8_t const code[] = {
            0x60, 0x00, // PUSH 0x00
            0x35, // CALLDATALOAD

            0x60, 0xE0, // PUSH
            0x52, // MSTORE
            0x60, 0x10, // PUSH
            0x60, 0xE0, // PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {0x01, 0x02, 0x03};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words)/512) + 1;
    ASSERT_EQ(gas - ((verylow*5) + mstore_size), result.gas_left);
    print_result(&result);
    struct evm_word gt = {0x01, 0x02, 0x03, /* zeros */};

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testCALLDATALOADPUSHMAX) {
    uint8_t const code[] = {
            0x60, 0x00, // PUSH 0x00
            0x6F, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, //
                  0x0b, 0x0c, 0x0d, 0x0e, 0x0f, 0x10, 0x11, 0x12, //
            0x35, // CALLDATALOAD

            0x60, 0xE0, // PUSH
            0x52, // MSTORE
            0x60, 0x10, // PUSH
            0x60, 0xE0, // PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {0x01, 0x02, 0x03};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words)/512) + 1;
    ASSERT_EQ(gas - ((verylow*6) + mstore_size), result.gas_left);
    print_result(&result);
    struct evm_word gt = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testCALLDATALOADCREATE) {
    uint8_t const code[] = {
            0x60, 0x00, // PUSH 0x00

            0x60, 0x10, // size PUSH
            0x60, 0xE0, // offset PUSH
            0x60, 0x05, // value PUSH
            0xF0, // CREATE

            0x35, // CALLDATALOAD

            0x60, 0xE0, // PUSH
            0x52, // MSTORE
            0x60, 0x10, // PUSH
            0x60, 0xE0, // PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {0x01, 0x02, 0x03};
    int64_t gas = 2000000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words)/512) + 1;
    ASSERT_EQ(gas - ((verylow*8) + mstore_size + create + zero), result.gas_left);
    print_result(&result);
    struct evm_word gt = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testCALLDATASIZE) {
    uint8_t const code[] = {
            0x60, 0x00, // PUSH 0x00
            0x36, // CALLDATASIZE

            0x60, 0xE0, // PUSH
            0x52, // MSTORE
            0x60, 0x10, // PUSH
            0x60, 0xE0, // PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {0x01, 0x02, 0x03};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words)/512) + 1;
    ASSERT_EQ(gas - ((verylow*4) + base + mstore_size), result.gas_left);
    print_result(&result);
    struct evm_word gt = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x03};

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testCALLDATACOPY) {
    uint8_t const code[] = {
            0x60, 0x20, // 32 bytes PUSH
            0x60, 0x00, // src index = 0x00 PUSH
            0x60, 0xE0, // dst index = 0xE0 PUSH
            0x37, // CALLDATACOPY

            0x60, 0x20, // PUSH
            0x60, 0xE0, // PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {0x01, 0x02, 0x03};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t calldatacopyval = 32;
    int32_t words = ((0xe0 + 0x20) + 31)/32;
    int32_t mstore_size = (words*1) + (words*words)/512;
    ASSERT_EQ(gas - ((verylow*5) + verylow + (gcopy*((calldatacopyval+31)/32)) + mstore_size), result.gas_left);
    print_result(&result);
    struct evm_hash gt = { 0x01, 0x02, 0x03 /* zeros */ };

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testCALLDATACOPYCREATE) {
    uint8_t const code[] = {
            0x60, 0x10, // size PUSH
            0x60, 0xE0, // offset PUSH
            0x60, 0x05, // value PUSH
            0xF0, // CREATE
            0x60, 0x03, // 32 bytes PUSH
            0x60, 0x00, // src index = 0x00 PUSH
            0x60, 0xE0, // dst index = 0xE0 PUSH
            0x37, // CALLDATACOPY

            0x60, 0x20, // PUSH
            0x60, 0xE0, // PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {0x01, 0x02, 0x03};
    int64_t gas = 2000000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t calldatacopyval = 3;
    int32_t words = ((0xe0 + 0x03) + 31)/32;
    int32_t mstore_size = (words*1) + (words*words)/512;
    ASSERT_EQ(gas - ((verylow*8) + create + verylow + (gcopy*((calldatacopyval + 31)/32)) + mstore_size), result.gas_left);
    print_result(&result);
    struct evm_hash gt = { 0x01, 0x02, 0x03 /* zeros */ };

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testCALLDATACOPYZERO) {
    uint8_t const code[] = {
            0x60, 0x20, // 32 bytes PUSH
            0x60, 0x04, // src index = 0x00 PUSH
            0x60, 0xE0, // dst index = 0xE0 PUSH
            0x37, // CALLDATACOPY
            0x60, 0x20, // PUSH
            0x60, 0xE0, // PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {0x01, 0x02, 0x03};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t calldatacopyval = 32;
    int32_t words = ((0xe0 + 0x20) + 31)/32;
    int32_t mstore_size = (words*1) + (words*words)/512;
    ASSERT_EQ(gas - ((verylow*5) + verylow + (gcopy*((calldatacopyval+31)/32)) + mstore_size), result.gas_left);
    print_result(&result);
    struct evm_hash gt = { 0x00, 0x00, 0x00 /* zeros */ };

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testCODESIZE) {
    uint8_t const code[] = {
            0x60, 0x00, // PUSH 0x00
            0x38, // CODESIZE

            0x60, 0xE0, // PUSH
            0x52, // MSTORE
            0x60, 0x10, // PUSH
            0x60, 0xE0, // PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words)/512) + 1;
    ASSERT_EQ(gas - ((verylow*4) + base + mstore_size), result.gas_left);
    print_result(&result);
    struct evm_word gt = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, sizeof(code)};

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testCODESIZE2) {
    uint8_t const code[] = {
            0x60, 0x02, // PUSH 0x00
            0x60, 0x04,
            0x38, // CODESIZE

            0x60, 0xE0, // PUSH
            0x52, // MSTORE
            0x60, 0x10, // PUSH
            0x60, 0xE0, // PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words)/512) + 1;
    ASSERT_EQ(gas - ((verylow*5) + base + mstore_size), result.gas_left);
    print_result(&result);
    struct evm_word gt = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, sizeof(code)};

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testCODESIZEEMPTY) {
    uint8_t const code[] = {
            0x38, // CODESIZE
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    print_result(&result);

    ASSERT_EQ(0, result.gas_left);
    ASSERT_EQ(0, result.output_size);
    ASSERT_EQ(NULL, result.output_data);
    release_result(&result);
}

TEST(instructions, testCODECOPY) {
    uint8_t const code[] = {
            0x60, 0x20, // bytes: 32 PUSH
            0x60, 0x00, // src index = 0x00  PUSH
            0x60, 0xE0, // dst index = 0xE0 PUSH
            0x39, // CODECOPY

            0x60, 0x20, // PUSH
            0x60, 0xE0, // PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {0x01, 0x02, 0x03};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t codevalue = 32;
    int32_t words = ((0xe0 + 0x20) + 31)/32;
    int32_t mstore_size = (words*1) + (words*words)/512;
    ASSERT_EQ(gas - ((verylow*5) + verylow + (gcopy*((codevalue + 31)/32)) + mstore_size + zero), result.gas_left);
    print_result(&result);
    struct evm_hash gt = {};
    memcpy(&gt, code, sizeof(code));

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testCODECOPY2) {
    uint8_t const code[] = {
            0x60, 0x40, // bytes: 64 PUSH
            0x60, 0x00, // src index = 0x00  PUSH
            0x60, 0xE0, // dst index = 0xE0 PUSH
            0x39, // CODECOPY

            0x60, 0x20, // PUSH
            0x60, 0xE0, // PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t codevalue = 64;
    int32_t codecopy = 32;
    int32_t words = ((0xe0 + 16) + 31)/32;
    int32_t mstore_size = (words*1) + (words*words)/512 + 1;
    ASSERT_EQ(gas - ((verylow*5) + verylow + (gcopy*(codevalue/codecopy)) + mstore_size + zero), result.gas_left);
    print_result(&result);
    struct evm_hash gt = {};
    memcpy(&gt, code, sizeof(code));

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testGASPRICE) {
    uint8_t const code[] = {
            0x3a, // GASPRICE

            0x60, 0xE0, // PUSH
            0x52, // MSTORE
            0x60, 0x10, // PUSH
            0x60, 0xE0, // PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words)/512) + 1;
    ASSERT_EQ(gas - (base + (verylow*3) + mstore_size), result.gas_left);
    print_result(&result);
    struct evm_word gt = tx_context.tx_gas_price;

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testEXTCODESIZE) {
    uint8_t const code[] = {
            0x6F, 0x0f, 0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, //
                  0x17, 0x18, 0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E, //
            0x6F, 0x06, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, //
                  0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, // PUSH an address

            0x3B, // EXTCODESIZE

            0x60, 0xE0, // PUSH
            0x52, // MSTORE
            0x60, 0x10, // PUSH
            0x60, 0xE0, // PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words)/512) + 1;
    ASSERT_EQ(gas - ((verylow*5) + extcode + mstore_size), result.gas_left);
    print_result(&result);
    struct evm_word gt = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, sizeof(expected_code_data) };

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testEXTCODESIZECREATE) {
    uint8_t const code[] = {
            0x6F, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f, 0x10, 0x11, 0x12, // 3
            0x60, 0x10, // size PUSH
            0x60, 0xE0, // offset PUSH
            0x60, 0x05, // value PUSH
            0xF0, // CREATE
            0x3B, // EXTCODESIZE

            0x60, 0xE0, // PUSH
            0x52, // MSTORE
            0x60, 0x10, // PUSH
            0x60, 0xE0, // PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 2000000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words)/512) + 1;
    ASSERT_EQ(gas - ((verylow*7) + extcode + mstore_size + create + zero), result.gas_left);
    print_result(&result);
    struct evm_word gt = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testEXTCODESIZEMAX) {
    uint8_t const code[] = {
            0x7f, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f,
                  0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18, 0x19, 0x20, 0x21, 0x22, 0x23, 0x24, 0x25, // PUSH an address
            0x3B, // EXTCODESIZE

            0x60, 0xE0, // PUSH
            0x52, // MSTORE
            0x60, 0x10, // PUSH
            0x60, 0xE0, // PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words)/512) + 1;
    ASSERT_EQ(gas - ((verylow*4) + extcode + mstore_size), result.gas_left);
    print_result(&result);
    struct evm_word gt = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testEXTCODECOPY) {
    uint8_t const code[] = {
            0x60, 0x20, // total 32 bytes
            0x60, 0x00, // src index = 0x00
            0x60, 0xE0, // dst index = 0xE0
            0x6F, 0x0F, 0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, //
                  0x17, 0x18, 0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E, // PUSH
            0x6F, 0x06, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06,
                  0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, // PUSH
            0x3C, // EXTCODECOPY

            0x60, 0x20, // PUSH
            0x60, 0xE0, // PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t codeval = 32;
    int32_t words = ((0xe0 + 0x20) + 31)/32;
    int32_t mstore_size = (words*1) + (words*words)/512;
    ASSERT_EQ(gas - ((verylow*7) + extcode + (gcopy*((codeval + 31)/32)) + mstore_size + zero), result.gas_left);
    print_result(&result);
    struct evm_hash gt = { 0x11, 0x22, 0x33, 0x44 };

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testEXTCODECOPY2) {
    uint8_t const code[] = {
            0x60, 0x40, // total 32 bytes
            0x60, 0x40, // src index = 0x00
            0x60, 0xE0, // dst index = 0xE0
            0x60, 0x10, // size PUSH
            0x60, 0xE0, // offset PUSH
            0x60, 0x05, // value PUSH
            0xF0, // CREATE
            0x3C, // EXTCODECOPY

            0x60, 0x20, // PUSH
            0x60, 0xE0, // PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 2000000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t codeval = 64;
    int32_t words = ((0xe0 + 0x40) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words)/512);
    ASSERT_EQ(gas - ((verylow*8) + extcode + (gcopy*((codeval + 31)/32)) + create + mstore_size + zero), result.gas_left);
    print_result(&result);
    struct evm_hash gt = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

//======================================
// 40s: Block Information
//======================================
TEST(instructions, testBLOCKHASH) {
    uint8_t const code[] = {
            0x60, 0x12, // PUSH block number
            0x40, // BLOCKHASH

            0x60, 0xE0, 0x52, // PUSH + MSTORE
            0x60, 0xF0, 0x52, // PUSH + MSTORE
            0x60, 0x20, // PUSH
            0x60, 0xE0, // PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words)/512) + 1;
    int32_t extra_mstore = 1;
    ASSERT_EQ(gas - ((verylow*5) + blockhash + mstore_size + (extra_mstore*1)), result.gas_left);
    print_result(&result);
    struct evm_hash &gt = block_hash;

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testBLOCKHASHMAX) {
    uint8_t const code[] = {
            0x6F, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f, // PUSH block number
            0x40, // BLOCKHASH

            0x60, 0xE0, 0x52, // PUSH + MSTORE
            0x60, 0xF0, 0x52, // PUSH + MSTORE
            0x60, 0x20, // PUSH
            0x60, 0xE0, // PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words)/512) + 1;
    int32_t extra_mstore = 1;
    ASSERT_EQ(gas - ((verylow*5) + blockhash + mstore_size + (extra_mstore*1)), result.gas_left);
    print_result(&result);
    struct evm_hash gt = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testBLOCKHASHZERO) {
    uint8_t const code[] = {
            0x60, 0x00, // PUSH block number
            0x40, // BLOCKHASH

            0x60, 0xE0, 0x52, // PUSH + MSTORE
            0x60, 0xF0, 0x52, // PUSH + MSTORE
            0x60, 0x20, // PUSH
            0x60, 0xE0, // PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words)/512) + 1;
    int32_t extra_mstore = 1;
    ASSERT_EQ(gas - ((verylow*5) + blockhash + mstore_size + (extra_mstore*1)), result.gas_left);
    print_result(&result);
    struct evm_hash &gt = block_hash;

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testCOINBASE) {
    uint8_t const code[] = {
            0x41, // COINBASE

            0x60, 0xE0, 0x52, // PUSH + MSTORE
            0x60, 0xF0, 0x52, // PUSH + MSTORE
            0x60, 0x20, // PUSH
            0x60, 0xE0, // PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words)/512) + 1;
    int32_t extra_mstore = 1;
    ASSERT_EQ(gas - (base + (verylow*4) + mstore_size + (extra_mstore*1)), result.gas_left);
    print_result(&result);
    struct evm_hash gt;
    address2hash(&tx_context.block_coinbase, &gt);

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testTIMESTAMP) {
    uint8_t const code[] = {
            0x42, // TIMESTAMP

            0x60, 0xE0, //PUSH
            0x52, // MSTORE
            0x60, 0x10, // PUSH
            0x60, 0xE0, // PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words)/512) + 1;
    ASSERT_EQ(gas - (base + (verylow*3) + mstore_size), result.gas_left);
    print_result(&result);
    struct evm_word gt = {
            0, 0, 0, 0, 0, 0, 0, 0,
            (uint8_t) ((tx_context.block_timestamp >> 56) & 0xff),
            (uint8_t) ((tx_context.block_timestamp >> 48) & 0xff),
            (uint8_t) ((tx_context.block_timestamp >> 40) & 0xff),
            (uint8_t) ((tx_context.block_timestamp >> 32) & 0xff),
            (uint8_t) ((tx_context.block_timestamp >> 24) & 0xff),
            (uint8_t) ((tx_context.block_timestamp >> 16) & 0xff),
            (uint8_t) ((tx_context.block_timestamp >> 8) & 0xff),
            (uint8_t) (tx_context.block_timestamp & 0xff)
    };

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testNUMBER) {
    uint8_t const code[] = {
            0x43, // NUMBER

            0x60, 0xE0, // PUSH
            0x52, // MSTORE
            0x60, 0x10, // PUSH
            0x60, 0xE0, // PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words)/512) + 1;
    ASSERT_EQ(gas - (base + (verylow*3) + mstore_size), result.gas_left);
    print_result(&result);
    struct evm_word gt = {
            0, 0, 0, 0, 0, 0, 0, 0,
            (uint8_t) ((tx_context.block_number >> 56) & 0xff),
            (uint8_t) ((tx_context.block_number >> 48) & 0xff),
            (uint8_t) ((tx_context.block_number >> 40) & 0xff),
            (uint8_t) ((tx_context.block_number >> 32) & 0xff),
            (uint8_t) ((tx_context.block_number >> 24) & 0xff),
            (uint8_t) ((tx_context.block_number >> 16) & 0xff),
            (uint8_t) ((tx_context.block_number >> 8) & 0xff),
            (uint8_t) (tx_context.block_number & 0xff)
    };

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testDIFFICULTY) {
    uint8_t const code[] = {
            0x44, // DIFFICULTY

            0x60, 0xE0, // PUSH
            0x52, // MSTORE
            0x60, 0x10, // PUSH
            0x60, 0xE0, // PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words)/512) + 1;
    ASSERT_EQ(gas - (base + (verylow*3) + mstore_size), result.gas_left);
    print_result(&result);
    struct evm_word gt = tx_context.block_difficulty;

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testGASLIMIT) {
    uint8_t const code[] = {
            0x45, // GASLIMIT

            0x60, 0xE0, // PUSH
            0x52, // MSTORE
            0x60, 0x10, // PUSH
            0x60, 0xE0, // PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words)/512) + 1;
    ASSERT_EQ(gas - (base + (verylow*3) + mstore_size), result.gas_left);
    print_result(&result);
    struct evm_word gt = {
            0, 0, 0, 0, 0, 0, 0, 0,
            (uint8_t) ((tx_context.block_gas_limit >> 56) & 0xff),
            (uint8_t) ((tx_context.block_gas_limit >> 48) & 0xff),
            (uint8_t) ((tx_context.block_gas_limit >> 40) & 0xff),
            (uint8_t) ((tx_context.block_gas_limit >> 32) & 0xff),
            (uint8_t) ((tx_context.block_gas_limit >> 24) & 0xff),
            (uint8_t) ((tx_context.block_gas_limit >> 16) & 0xff),
            (uint8_t) ((tx_context.block_gas_limit >> 8) & 0xff),
            (uint8_t) (tx_context.block_gas_limit & 0xff)
    };

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

//======================================
// 50s: Stack, Memory, Storage and Flow Operations
//======================================

TEST(instructions, testPOP) {
    uint8_t const code[] = {
            0x60, 0x01, // PUSH
            0x60, 0x02, // PUSH
            0x50, // POP

            0x60, 0xE0, // PUSH
            0x52, // MSTORE
            0x60, 0x10, // PUSH
            0x60, 0xE0, // PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words)/512) + 1;
    ASSERT_EQ(gas - ((verylow*5) + base + mstore_size), result.gas_left);
    print_result(&result);
    struct evm_word gt = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x01};

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testPOPONE) {
    uint8_t const code[] = {
            0x60, 0x01, // PUSH
            0x50, // POP
            0x60, 0x01, // PUSH
            0x60, 0xE0, // PUSH
            0x52, // MSTORE
            0x60, 0x10, // PUSH
            0x60, 0xE0, // PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words)/512) + 1;
    ASSERT_EQ(gas - ((verylow*5) + base + mstore_size), result.gas_left);
    print_result(&result);
    struct evm_word gt = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x01};

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testMLOAD) {
    uint8_t const code[] = {
            0x6F, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f, // PUSH
            0x60, 0xE0, // PUSH
            0x51, // MLOAD from 0xE0

            0x60, 0xF0, // PUSH
            0x52, // MSTORE
            0x60, 0x10, // PUSH
            0x60, 0xF0, // PUSH
            0xF3 // RETURN (0xF0)
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words)/512) + 1;
    ASSERT_EQ(gas - ((verylow*6) + mstore_size), result.gas_left);
    print_result(&result);
    struct evm_word gt = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testMLOADEMPTY) {
    uint8_t const code[] = {
            0x6F, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f, // PUSH
            0x60, 0xE0, // PUSH
            0x51, // MLOAD
            0x60, 0xE0, // PUSH
            0x52, // MSTORE
            0x60, 0x10, // PUSH
            0x60, 0xE0, // PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words) / 512) + 1;
    ASSERT_EQ(gas - ((verylow*6) + mstore_size + zero), result.gas_left);
    print_result(&result);
    struct evm_word gt = {};
    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testMLOAD_MSTORE) {
    uint8_t const code[] = {
            0x6F, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f, // PUSH
            0x60, 0xE0, 0x52, // MSTORE to 0xEO
            0x60, 0xE0, 0x51, // MLOAD from 0xE0

            0x60, 0xF0, // PUSH
            0x52, // MSTORE
            0x60, 0x10, // PUSH
            0x60, 0xF0, // PUSH
            0xF3 // RETURN (0xF0)
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words)/512) + 1;
    int32_t extra_mstore = 1;
    ASSERT_EQ(gas - ((verylow*7) + mstore_size + (extra_mstore*1)), result.gas_left);
    print_result(&result);
    struct evm_word gt = { 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f};

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testMSTORE8) {
    uint8_t const code[] = {
            0x6F, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f, // PUSH
            0x60, 0xE0,
            0x53, // MSTORE8 to 0xEO
            0x60, 0xE0,
            0x51, // MLOAD from 0xE0

            0x60, 0xF0, // PUSH
            0x52, // MSTORE
            0x60, 0x10, // PUSH
            0x60, 0xF0, // PUSH
            0xF3 // RETURN (0xF0)
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words)/512) + 1;
    int32_t extra_mstore = 1;
    ASSERT_EQ(gas - ((verylow*6) + mstore_size + (extra_mstore*2)), result.gas_left);
    print_result(&result);
    struct evm_word gt = { 0x0f };

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testMSTORE8JUMP) {
    uint8_t const code[] = {
            0x60, 0x01, // PUSH 0x01
            0x60, 0x07, 0x56, // JUMP
            0x60, 0x02, // PUSH 0x02
            0x5B, // JUMPDEST

            0x6F, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f, // PUSH
            0x60, 0xE0,
            0x53, // MSTORE8 to 0xEO
            0x60, 0xE0,
            0x51, // MLOAD from 0xE0

            0x60, 0xF0, // PUSH
            0x52, // MSTORE
            0x60, 0x10, // PUSH
            0x60, 0xF0, // PUSH
            0xF3 // RETURN (0xF0)
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words)/512) + 1;
    int32_t extra_mstore = 1;
    ASSERT_EQ(gas - ((verylow*8) + mid + jumpdest + mstore_size + (extra_mstore*2)), result.gas_left);
    print_result(&result);
    struct evm_word gt = { 0x0f };

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testMSTORE8ADD) {
    uint8_t const code[] = {
            0x60, 0x01, // PUSH
            0x60, 0x02, // PUSH
            0x01, // ADD
            0x60, 0xE0,
            0x53, // MSTORE8 to 0xEO
            0x60, 0xE0, // 3
            0x51, // MLOAD from 0xE0

            0x60, 0xF0, // PUSH
            0x52, // MSTORE
            0x60, 0x10, // PUSH
            0x60, 0xF0, // PUSH
            0xF3 // RETURN (0xF0)
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words)/512) + 1;
    int32_t extra_mstore = 1;
    ASSERT_EQ(gas - ((verylow*8) + mstore_size + (extra_mstore*2)), result.gas_left);
    print_result(&result);
    struct evm_word gt = { 0x03, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testSLOAD) {
    uint8_t const code[] = {
            0x60, 0x22, 0x54, // SLOAD from 0x22

            0x60, 0xE0, // PUSH
            0x52, // MSTORE
            0x60, 0x10, // PUSH
            0x60, 0xE0, // PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words)/512) + 1;
    ASSERT_EQ(gas - ((verylow*4) + sload + mstore_size), result.gas_left);
    print_result(&result);
    struct evm_word gt = storage[0x22];

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testSLOAD_SSTORE) {
    uint8_t const code[] = {
            0x60, 0x01,
            0x60, 0x50,
            0x55, // SSTORE sset
            0x60, 0x50,
            0x54, // SLOAD from 0x22
            0x60, 0xE0, // PUSH
            0x52, // MSTORE
            0x60, 0x10, // PUSH
            0x60, 0xE0, // PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 200000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words)/512) + 1;
    ASSERT_EQ(gas - ((verylow*6) + sset + sload + mstore_size), result.gas_left);
    print_result(&result);
    struct evm_word gt = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x01};

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testSSTORE) {
    uint8_t const code[] = {
            0x6F, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, //
                  0x88, 0x99, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55, // PUSH
            0x60, 0x22, // PUSH
            0x55 // SSTORE to 0x22
    };
    uint8_t const input[] = {};
    int64_t gas = 20000 * 2; // insert => 20000

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    ASSERT_EQ(gas - ((verylow*2) + sset), result.gas_left);
    print_result(&result);
    struct evm_word gt = {
            0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, //
            0x88, 0x99, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55 //
    };

    ASSERT_TRUE(0 == memcmp(gt.bytes, storage[0x22].bytes, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testSSTORESRESET) {
    uint8_t const code[] = {
            0x60, 0x02,
            0x60, 0x01,
            0x60, 0x00, // PUSH
            0x60, 0x22, // PUSH
            0x55, // SSTORE to 0x22
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 200000; // reset = 5000
    int64_t words = ((0x01 + 2) + 31) / 32;
    int64_t mexpansion_size = (words*1) + ((words*words) / 512);
    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));

    ASSERT_EQ(gas - ((verylow*4) + sreset + mexpansion_size), result.gas_left);
    print_result(&result);
    struct evm_word gt = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };

    ASSERT_TRUE(0 == memcmp(gt.bytes, storage[0x22].bytes, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testJUMP) {
    uint8_t const code[] = {
            0x60, 0x01, // PUSH 0x01
            0x60, 0x07, 0x56, // JUMP
            0x60, 0x02, // PUSH 0x02
            0x5B, // JUMPDEST

            0x60, 0xE0, // PUSH
            0x52, // MSTORE
            0x60, 0x10, // PUSH
            0x60, 0xE0, // PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words)/512) + 1;
    ASSERT_EQ(gas - ((verylow*5) + mid + jumpdest + mstore_size), result.gas_left);
    print_result(&result);
    struct evm_word gt = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1};

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testJUMPI) {
    uint8_t const code[] = {
            0x60, 0x01, // PUSH 0x01
            0x60, 0x01, // PUSH
            0x60, 0x09, // PUSH
            0x57, // JUMPI
            0x60, 0x02, // PUSH 0x02
            0x5B, // JUMPDEST

            0x60, 0xE0, // PUSH
            0x52, // MSTORE
            0x60, 0x10, // PUSH
            0x60, 0xE0, // PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words)/512) + 1;
    ASSERT_EQ(gas - ((verylow*6) + high + jumpdest + mstore_size), result.gas_left);
    print_result(&result);
    struct evm_word gt = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1};

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testJUMPI2) {
    uint8_t const code[] = {
            0x60, 0x01, // PUSH 0x01
            0x60, 0x00, // PUSH
            0x60, 0x09, // PUSH
            0x57, // JUMPI
            0x60, 0x02, // PUSH 0x02
            0x5B, // JUMPDEST

            0x60, 0xE0, // PUSH
            0x52, // MSTORE
            0x60, 0x10, // PUSH
            0x60, 0xE0, // PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words)/512) + 1;
    ASSERT_EQ(gas - ((verylow*7) + high + jumpdest + mstore_size), result.gas_left);
    print_result(&result);
    struct evm_word gt = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2};

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testPC) {
    uint8_t const code[] = {
            0x60, 0x01, // PUSH 0x01
            0x58, // PC

            0x60, 0xE0, // PUSH
            0x52, // MSTORE
            0x60, 0x10, // PUSH
            0x60, 0xE0, // PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words)/512) + 1;
    ASSERT_EQ(gas - ((verylow*4) + base + mstore_size), result.gas_left);
    print_result(&result);
    struct evm_word gt = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2};

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testPCZERO) {
    uint8_t const code[] = {
            0x58, // PC

            0x60, 0xE0, // PUSH
            0x52, // MSTORE
            0x60, 0x10, // PUSH
            0x60, 0xE0, // PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words)/512) + 1;
    ASSERT_EQ(gas - ((verylow*3) + base + mstore_size + zero), result.gas_left);
    print_result(&result);
    struct evm_word gt = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testPCJUMP) {
    uint8_t const code[] = {
            0x60, 0x01, // PUSH 0x01
            0x60, 0x01, // PUSH
            0x60, 0x09, // PUSH
            0x57, // JUMPI
            0x60, 0x02, // PUSH 0x02
            0x5B, // JUMPDEST
            0x58, // PC

            0x60, 0xE0, // PUSH
            0x52, // MSTORE
            0x60, 0x10, // PUSH
            0x60, 0xE0, // PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 200000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words)/512) + 1;
    ASSERT_EQ(gas - ((verylow*6) + high + jumpdest + base + mstore_size + zero), result.gas_left);
    print_result(&result);
    struct evm_word gt = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x0A};

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testPCMSTORE) {
    uint8_t const code[] = {
            0x60, 0x01, // PUSH
            0x60, 0xE0, // PUSH
            0x52, // MSTORE
            0x60, 0x03, // PUSH
            0x60, 0xF0, // PUSH
            0x52, // MSTORE
            0x58, // PC
            0x60, 0x10, // PUSH
            0x60, 0xE0, // PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xf0) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words)/512) + 1;
    int32_t extra_mstore = 1;
    ASSERT_EQ(gas - ((verylow*6) + base + mstore_size + (extra_mstore*1) + zero), result.gas_left);
    print_result(&result);
    struct evm_word gt = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x01};

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testPCPUSH) {
    uint8_t const code[] = {
            0x60, 0x01,
            0x60, 0x10,
            0x60, 0x02,
            0x60, 0x03,
            0x60, 0x04,
            0x60, 0x05,
            0x60, 0x06,
            0x58, // PC

            0x60, 0xE0, // PUSH
            0x52, // MSTORE
            0x60, 0x10, // PUSH
            0x60, 0xE0, // PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words)/512) + 1;
    ASSERT_EQ(gas - ((verylow*10) + base + mstore_size + zero), result.gas_left);
    print_result(&result);
    struct evm_word gt = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x0e};

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testPCSELFDESTRUCT) {
    uint8_t const code[] = {
            0x6F, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88, //
                  0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88, // PUSH
            0x6F, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, //
                  0x00, 0x00, 0x00, 0x00, 0x11, 0x22, 0x33, 0x44, // beneficiary
            0xFF, // SELFDESTRUCT
            0x58, // PC

            0x60, 0xE0, // PUSH
            0x52, // MSTORE
            0x60, 0x10, // PUSH
            0x60, 0xE0, // PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    ASSERT_EQ(gas - ((verylow*2) + suicide + zero), result.gas_left);
    print_result(&result);

    ASSERT_EQ(0, result.output_size);
    ASSERT_EQ(NULL, result.output_data);
    release_result(&result);
}

TEST(instructions, testPCCREATE) {
    uint8_t const code[] = {
            0x60, 0x01, // PUSH 0x01
            0x60, 0x10, // size PUSH
            0x60, 0xE0, // offset PUSH
            0x60, 0x05, // value PUSH
            0xF0, // CREATE

            0x58, // PC

            0x60, 0xE0, // PUSH
            0x52, // MSTORE
            0x60, 0x10, // PUSH
            0x60, 0xE0, // PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 2000000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words)/512) + 1;
    ASSERT_EQ(gas - ((verylow*7) + base + create + mstore_size + zero), result.gas_left);
    print_result(&result);
    struct evm_word gt = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x09};

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testMSIZE) {
    uint8_t const code[] = {
            0x60, 0x01, // PUSH 0x01
            0x61, 0xFF, 0xF0, // PUSH
            0x52, // MSTORE, which will expand the memory
            0x59, // MSIZE

            0x60, 0xE0,  // PUSH
            0x52, // MSTORE
            0x60, 0x10, // PUSH
            0x60, 0xE0, // PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xfff0 + 16) + 31) / 32;
    int32_t mstore_size = words * 1 + words * words / 512 + 1;
    int32_t extra_mstore = 1;
    ASSERT_EQ(gas - ((verylow*5) + mstore_size + (extra_mstore*1) + base + zero), result.gas_left);
    print_result(&result);
    struct evm_word gt = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x01, 0x00, 0x00};

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testMSIZEEMPTY) {
    uint8_t const code[] = {
            0x59, // MSIZE

            0x60, 0xE0,  // PUSH
            0x52, // MSTORE
            0x60, 0x10, // PUSH
            0x60, 0xE0, // PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = words * 1 + words * words / 512 + 1;
    ASSERT_EQ(gas - ((verylow*3) + mstore_size + base + zero), result.gas_left);
    print_result(&result);
    struct evm_word gt = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x00, 0x00, 0x00};

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testMSIZESTOREMULTIPLE) {
    uint8_t const code[] = {
            0x60, 0x01, // PUSH 0x01
            0x61, 0xFF, 0xF0, // PUSH
            0x52, // MSTORE, which will expand the memory
            0x60, 0xFF, // PUSH 0xff
            0x60, 0xE0, // PUSH 0xE0
            0x52, // MSTORE
            0x6F, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
                  0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, // PUSH MAX INT
            0x60, 0xD0, // PUSH 0xD0
            0x52, // MSTORE
            0x59, // MSIZE

            0x60, 0xE0,  // PUSH
            0x52, // MSTORE
            0x60, 0x10, // PUSH
            0x60, 0xE0, // PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xfff0 + 16) + 31) / 32;
    int32_t mstore_size = words * 1 + words * words / 512 + 1;
    int32_t extra_mstore = 1;
    ASSERT_EQ(gas - ((verylow*9) + (extra_mstore*3) + mstore_size + base + zero), result.gas_left);
    print_result(&result);
    struct evm_word gt = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x01, 0x00, 0x00};

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testGAS) {
    uint8_t const code[] = {
            0x60, 0x01, // PUSH 0x01
            0x5A, // GAS

            0x60, 0xE0, // PUSH
            0x52, // MSTORE
            0x60, 0x10, // PUSH
            0x60, 0xE0, // PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words)/512) + 1;
    ASSERT_EQ(gas - ((verylow*4) + base + mstore_size), result.gas_left);
    print_result(&result);
    int64_t left = gas - (verylow*1) - base;
    struct evm_word gt = {
            0, 0, 0, 0, 0, 0, 0, 0,
            (uint8_t) ((left >> 56) & 0xff),
            (uint8_t) ((left >> 48) & 0xff),
            (uint8_t) ((left >> 40) & 0xff),
            (uint8_t) ((left >> 32) & 0xff),
            (uint8_t) ((left >> 24) & 0xff),
            (uint8_t) ((left >> 16) & 0xff),
            (uint8_t) ((left >> 8) & 0xff),
            (uint8_t) (left & 0xff)
    };

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}


TEST(instructions, testGASPUSH) {
    uint8_t const code[] = {
            0x60, 0x02,
            0x60, 0x03,
            0x60, 0x01, // PUSH 0x01
            0x5A, // GAS

            0x60, 0xE0, // PUSH
            0x52, // MSTORE
            0x60, 0x10, // PUSH
            0x60, 0xE0, // PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words)/512) + 1;
    ASSERT_EQ(gas - ((verylow*6) + base + mstore_size), result.gas_left);
    print_result(&result);
    int64_t left = gas - (verylow*3) - base;
    struct evm_word gt = {
            0, 0, 0, 0, 0, 0, 0, 0,
            (uint8_t) ((left >> 56) & 0xff),
            (uint8_t) ((left >> 48) & 0xff),
            (uint8_t) ((left >> 40) & 0xff),
            (uint8_t) ((left >> 32) & 0xff),
            (uint8_t) ((left >> 24) & 0xff),
            (uint8_t) ((left >> 16) & 0xff),
            (uint8_t) ((left >> 8) & 0xff),
            (uint8_t) (left & 0xff)
    };

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testGASMSTORE) {
    uint8_t const code[] = {
            0x60, 0x04,
            0x60, 0xE0,
            0x52, // MSTORE
            0x60, 0x01, // PUSH 0x01
            0x5A, // GAS

            0x60, 0xE0, // PUSH
            0x52, // MSTORE
            0x60, 0x10, // PUSH
            0x60, 0xE0, // PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words)/512) + 1;
    int32_t extra_mstore = 1;
    ASSERT_EQ(gas - ((verylow*6) + base + mstore_size + (extra_mstore*1)), result.gas_left);
    print_result(&result);
    int64_t left = gas - (verylow*3) - mstore_size - base;
    struct evm_word gt = {
            0, 0, 0, 0, 0, 0, 0, 0,
            (uint8_t) ((left >> 56) & 0xff),
            (uint8_t) ((left >> 48) & 0xff),
            (uint8_t) ((left >> 40) & 0xff),
            (uint8_t) ((left >> 32) & 0xff),
            (uint8_t) ((left >> 24) & 0xff),
            (uint8_t) ((left >> 16) & 0xff),
            (uint8_t) ((left >> 8) & 0xff),
            (uint8_t) (left & 0xff)
    };

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testGASJUMP) {
    uint8_t const code[] = {
            0x60, 0x01, // PUSH 0x01
            0x60, 0x01, // PUSH
            0x60, 0x09, // PUSH
            0x57, // JUMPI
            0x60, 0x02, // PUSH 0x02
            0x5B, // JUMPDEST
            0x5A, // GAS

            0x60, 0xE0, // PUSH
            0x52, // MSTORE
            0x60, 0x10, // PUSH
            0x60, 0xE0, // PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 200000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words)/512) + 1;
    ASSERT_EQ(gas - ((verylow*6) + mstore_size + base + high + jumpdest + zero), result.gas_left);
    print_result(&result);
    int64_t left = gas - (verylow*3) - high - jumpdest - base;
    struct evm_word gt = {
            0, 0, 0, 0, 0, 0, 0, 0,
            (uint8_t) ((left >> 56) & 0xff),
            (uint8_t) ((left >> 48) & 0xff),
            (uint8_t) ((left >> 40) & 0xff),
            (uint8_t) ((left >> 32) & 0xff),
            (uint8_t) ((left >> 24) & 0xff),
            (uint8_t) ((left >> 16) & 0xff),
            (uint8_t) ((left >> 8) & 0xff),
            (uint8_t) (left & 0xff)
    };

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

//======================================
// 60s & 70s: Push Operations
//======================================

TEST(instructions, testPUSH20) {
    uint8_t const code[] = {
            0x73, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f,
                  0x10, 0x11, 0x12, 0x13, // PUSH20

            0x60, 0xE0, 0x52, // PUSH + MSTORE
            0x60, 0xF0, 0x52, // PUSH + MSTORE
            0x60, 0x20, // PUSH
            0x60, 0xE0, // PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xf0 + 16) + 31) / 32;
    int32_t mstore_size = (words * 1) + ((words*words)/512) + 1;
    int32_t extra_mstore = 1;
    ASSERT_EQ(gas - ((verylow*5) + mstore_size + (extra_mstore*1)), result.gas_left);
    print_result(&result);
    struct evm_hash gt = {
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f,
            0x10, 0x11, 0x12, 0x13
    };

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testPUSH32) {
    uint8_t const code[] = {
            0x7f, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f,
                  0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18, 0x19, 0x20, 0x21, 0x22, 0x23, 0x24, 0x25,// PUSH32 3

            0x60, 0xE0, 0x52, // PUSH + MSTORE
            0x60, 0xF0, 0x52, // PUSH + MSTORE
            0x60, 0x20, // PUSH
            0x60, 0xE0, // PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xf0 + 16) + 31) / 32;
    int32_t mstore_size = (words * 1) + ((words*words)/512) + 1;
    int32_t extra_mstore = 1;
    ASSERT_EQ(gas - ((verylow*5) + mstore_size + (extra_mstore*1)), result.gas_left);
    print_result(&result);
    struct evm_hash gt = {
            0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f,
            0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18, 0x19, 0x20, 0x21, 0x22, 0x23, 0x24, 0x25
    };

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

//======================================
// 80s: Duplication Operations
//======================================

TEST(instructions, testDUP1) {
    uint8_t const code[] = {
            0x60, 0x04, // PUSH
            0x80, // DUP1

            0x60, 0xE0, // PUSH
            0x52, // MSTORE
            0x60, 0x10, // PUSH
            0x60, 0xE0, // PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words)/512) + 1;
    ASSERT_EQ(gas - ((verylow*5) + mstore_size + zero), result.gas_left);
    print_result(&result);
    struct evm_word gt = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 4 };

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testDUP10) {
    uint8_t const code[] = {
            0x60, 0x01, // PUSH
            0x60, 0x02, // PUSH
            0x60, 0x03, // PUSH
            0x60, 0x04, // PUSH
            0x60, 0x05,
            0x60, 0x06,
            0x60, 0x07,
            0x60, 0x08,
            0x60, 0x09,
            0x60, 0x10,
            0x82, // DUP2

            0x60, 0xE0, // PUSH
            0x52, // MSTORE
            0x60, 0x10, // PUSH
            0x60, 0xE0, // PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 200000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words)/512) + 1;
    ASSERT_EQ(gas - ((verylow*14) + mstore_size + zero), result.gas_left);
    print_result(&result);
    struct evm_word gt = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x08 };

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testDUP16) {
    uint8_t const code[] = {
            0x60, 0x01, // PUSH
            0x60, 0x02, // PUSH
            0x60, 0x03, // PUSH
            0x60, 0x04, // PUSH
            0x60, 0x05,
            0x60, 0x06,
            0x60, 0x07,
            0x60, 0x08,
            0x60, 0x09,
            0x60, 0x10,
            0x60, 0x11,
            0x60, 0x12,
            0x60, 0x13,
            0x60, 0x14,
            0x60, 0x15,
            0x60, 0x16,
            0x60, 0x17,
            0x8f, // DUP2

            0x60, 0xE0, // PUSH
            0x52, // MSTORE
            0x60, 0x10, // PUSH
            0x60, 0xE0, // PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words)/512) + 1;
    ASSERT_EQ(gas - ((verylow*21) + mstore_size), result.gas_left);
    print_result(&result);
    struct evm_word gt = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2 };

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

//======================================
// 90s: Exchange Operations
//======================================

TEST(instructions, testSWAP1) {
    uint8_t const code[] = {
            0x60, 0x01, // PUSH
            0x60, 0x02, // PUSH
            0x60, 0x03, // PUSH
            0x60, 0x04, // PUSH
            0x90, // SWAP1

            0x60, 0xE0, // PUSH
            0x52, // MSTORE
            0x60, 0x10, // PUSH
            0x60, 0xE0, // PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words)/512) + 1;
    ASSERT_EQ(gas - ((verylow*8) + mstore_size), result.gas_left);
    print_result(&result);
    struct evm_word gt = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3 };

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testSWAP2) {
    uint8_t const code[] = {
            0x60, 0x01, // PUSH
            0x60, 0x02, // PUSH
            0x60, 0x03, // PUSH
            0x60, 0x04, // PUSH
            0x91, // SWAP2

            0x60, 0xE0, // PUSH
            0x52, // MSTORE
            0x60, 0x10, // PUSH
            0x60, 0xE0, // PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words)/512) + 1;
    ASSERT_EQ(gas - ((verylow*8) + mstore_size), result.gas_left);
    print_result(&result);
    struct evm_word gt = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2 };

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testSWAP10) {
    uint8_t const code[] = {
            0x60, 0x01, // PUSH
            0x60, 0x02, // PUSH
            0x60, 0x03, // PUSH
            0x60, 0x04, // PUSH 4
            0x60, 0x50,
            0x60, 0x50,
            0x60, 0x50,
            0x60, 0x50,
            0x60, 0x50,
            0x60, 0x50,
            0x60, 0x50,
            0x60, 0x50,
            0x60, 0x50,
            0x99, // SWAP10

            0x60, 0xE0, // PUSH
            0x52, // MSTORE
            0x60, 0x10, // PUSH
            0x60, 0xE0, // PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words)/512) + 1;
    ASSERT_EQ(gas - ((verylow*17) + mstore_size), result.gas_left);
    print_result(&result);
    struct evm_word gt = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3 };

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testSWAP16) {
    uint8_t const code[] = {
            0x60, 0x01, // PUSH
            0x60, 0x02, // PUSH
            0x60, 0x03, // PUSH
            0x60, 0x04, // PUSH
            0x60, 0x05,
            0x60, 0x06,
            0x60, 0x07,
            0x60, 0x08,
            0x60, 0x09,
            0x60, 0x10,
            0x60, 0x11,
            0x60, 0x12,
            0x60, 0x13,
            0x60, 0x14,
            0x60, 0x15,
            0x60, 0x16,
            0x60, 0x17,
            0x60, 0x18,
            0x60, 0x19,
            0x60, 0x20,
            0x9f, // SWAP16

            0x60, 0xE0, // PUSH
            0x52, // MSTORE
            0x60, 0x10, // PUSH
            0x60, 0xE0, // PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words)/512) + 1;
    ASSERT_EQ(gas - ((verylow*24) + mstore_size), result.gas_left);
    print_result(&result);
    struct evm_word gt = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 4 };

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testSWAPPUSHMAX) {
    uint8_t const code[] = {
            0x60, 0x01, // PUSH
            0x60, 0x02, // PUSH
            0x60, 0x03, // PUSH
            0x60, 0x04, // PUSH
            0x6F, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
                  0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
            0x60, 0x06,
            0x60, 0x07,
            0x60, 0x08,
            0x60, 0x09,
            0x60, 0x10,
            0x94, // SWAP4

            0x60, 0xE0, // PUSH
            0x52, // MSTORE
            0x60, 0x10, // PUSH
            0x60, 0xE0, // PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words)/512) + 1;
    ASSERT_EQ(gas - ((verylow*14) + mstore_size), result.gas_left);
    print_result(&result);
    struct evm_word gt = { 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
                           0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff };

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}

TEST(instructions, testSWAPCREATE) {
    uint8_t const code[] = {
            0x60, 0x01, // PUSH
            0x60, 0x02, // PUSH
            0x60, 0x03, // PUSH
            0x60, 0x04, // PUSH
            0x60, 0x05, // PUSH
            0x60, 0x06, // PUSH
            0x60, 0x07, // PUSH
            0x60, 0x08, // PUSH
            0x60, 0x09, // PUSH

            0x6F, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, //
                  0x88, 0x99, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55, // push one word
            0x60, 0xE0, // PUSH
            0x52, // dump to memory MSTORE

            0x60, 0x10, // size PUSH
            0x60, 0xE0, // offset PUSH
            0x60, 0x05, // value PUSH
            0xF0, // CREATE

            0x60, 0x50, // PUSH
            0x60, 0x50, // PUSH
            0x60, 0x50, // PUSH
            0x60, 0x50, // PUSH
            0x60, 0x50, // PUSH
            0x99, // SWAP10 3

            0x60, 0xE0, // PUSH
            0x52, // MSTORE
            0x60, 0x10, // PUSH
            0x60, 0xE0, // PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 2000000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words)/512) + 1;
    ASSERT_EQ(gas - ((verylow*23) + mstore_size + (1*1) + create), result.gas_left);
    print_result(&result);
    struct evm_word gt = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 6 };

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(gt)));
    release_result(&result);
}


//======================================
// a0s: Logging Operations
//======================================

TEST(instructions, testLOG0) {
    uint8_t const code[] = {
            0x6F, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, //
                  0x88, 0x99, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55, // log PUSH
            0x60, 0xE0,
            0x52, // dump to memory

            0x60, 0x10, // size PUSH
            0x60, 0xE0, // offset PUSH
            0xA0, // LOG0
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words)/512) + 1;
    int32_t log_nrg = glog + (logdata*0x10);
    ASSERT_EQ(gas - ((verylow*4) + mstore_size + log_nrg), result.gas_left);
    print_result(&result);

    struct evm_word data = {
            0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, //
            0x88, 0x99, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55 //
    };

    ASSERT_EQ(0 * 2, log_topics_count);

    ASSERT_EQ(16, log_data_size);
    ASSERT_TRUE(0 == memcmp(data.bytes, log_data, sizeof(data)));
}


TEST(instructions, testLOG1) {
    uint8_t const code[] = {
            0x6F, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, //
                  0x88, 0x99, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55, // log PUSH
            0x60, 0xE0, 0x52, // dump to memory

            0x60, 0x01, // topic-1 PUSH
            0x60, 0x01, // topic-1 PUSH
            0x60, 0x10, // size PUSH
            0x60, 0xE0, // offset PUSH
            0xA1, // LOG1
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words)/512) + 1;
    int32_t log_nrg = glog + (logdata*0x10) + (logtopic*1);
    ASSERT_EQ(gas - ((verylow*6) + mstore_size + log_nrg), result.gas_left);
    print_result(&result);

    struct evm_word topic1 = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1 };

    struct evm_word data = {
            0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, //
            0x88, 0x99, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55 //
    };

    ASSERT_EQ(1 * 2, log_topics_count);
    ASSERT_TRUE(0 == memcmp(topic1.bytes, &log_topics[0], sizeof(topic1)));

    ASSERT_EQ(16, log_data_size);
    ASSERT_TRUE(0 == memcmp(data.bytes, log_data, sizeof(data)));
}

TEST(instructions, testLOG2) {
    uint8_t const code[] = {
            0x6F, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, //
                  0x88, 0x99, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55, // log PUSH
            0x60, 0xE0, 0x52, // dump to memory PUSH + MSTORE

            0x60, 0x02, // topic-2 PUSH
            0x60, 0x02, // topic-2 PUSH
            0x60, 0x01, // topic-1 PUSH
            0x60, 0x01, // topic-1 PUSH
            0x60, 0x10, // size PUSH
            0x60, 0xE0, // offset PUSH
            0xA2, // LOG2
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words)/512) + 1;
    ASSERT_EQ(gas - ((verylow*8) + mstore_size + glog + (logdata*16) + (2*logtopic)), result.gas_left);
    print_result(&result);

    struct evm_word topic1 = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1 };
    struct evm_word topic2 = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2 };

    struct evm_word data = {
            0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, //
            0x88, 0x99, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55 //
    };

    ASSERT_EQ(2 * 2, log_topics_count);
    ASSERT_TRUE(0 == memcmp(topic1.bytes, &log_topics[0], sizeof(topic1)));
    ASSERT_TRUE(0 == memcmp(topic1.bytes, &log_topics[1], sizeof(topic2)));
    ASSERT_TRUE(0 == memcmp(topic2.bytes, &log_topics[2], sizeof(topic1)));
    ASSERT_TRUE(0 == memcmp(topic2.bytes, &log_topics[3], sizeof(topic2)));

    ASSERT_EQ(16, log_data_size);
    ASSERT_TRUE(0 == memcmp(data.bytes, log_data, sizeof(data)));
}


TEST(instructions, testLOG3) {
    uint8_t const code[] = {
            0x6F, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, //
                  0x88, 0x99, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55, // log PUSH
            0x60, 0xE0, 0x52, // dump to memory PUSH + MSTORE

            0x60, 0x03, // topic-3 PUSH
            0x60, 0x03, // topic-3 PUSH
            0x60, 0x02, // topic-2 PUSH
            0x60, 0x02, // topic-2 PUSH
            0x60, 0x01, // topic-1 PUSH
            0x60, 0x01, // topic-1 PUSH
            0x60, 0x10, // size PUSH
            0x60, 0xE0, // offset PUSH
            0xA3, // LOG3
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words)/512) + 1;
    ASSERT_EQ(gas - ((verylow*10) + mstore_size + glog + (logdata*16) + (3*logtopic)), result.gas_left);
    print_result(&result);

    struct evm_word topic1 = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1 };
    struct evm_word topic2 = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2 };
    struct evm_word topic3 = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3 };

    struct evm_word data = {
            0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, //
            0x88, 0x99, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55 //
    };

    ASSERT_EQ(3 * 2, log_topics_count);
    ASSERT_TRUE(0 == memcmp(topic1.bytes, &log_topics[0], sizeof(topic1)));
    ASSERT_TRUE(0 == memcmp(topic1.bytes, &log_topics[1], sizeof(topic2)));
    ASSERT_TRUE(0 == memcmp(topic2.bytes, &log_topics[2], sizeof(topic1)));
    ASSERT_TRUE(0 == memcmp(topic2.bytes, &log_topics[3], sizeof(topic2)));
    ASSERT_TRUE(0 == memcmp(topic3.bytes, &log_topics[4], sizeof(topic1)));
    ASSERT_TRUE(0 == memcmp(topic3.bytes, &log_topics[5], sizeof(topic2)));

    ASSERT_EQ(16, log_data_size);
    ASSERT_TRUE(0 == memcmp(data.bytes, log_data, sizeof(data)));
}


TEST(instructions, testLOG4) {
    uint8_t const code[] = {
            0x6F, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, //
                  0x88, 0x99, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55, // log PUSH
            0x60, 0xE0, 0x52, // dump to memory PUSH + MSTORE

            0x60, 0x04, // topic-4 PUSH
            0x60, 0x04, // topic-4 PUSH
            0x60, 0x03, // topic-3 PUSH
            0x60, 0x03, // topic-3 PUSH
            0x60, 0x02, // topic-2 PUSH
            0x60, 0x02, // topic-2 PUSH
            0x60, 0x01, // topic-1 PUSH
            0x60, 0x01, // topic-1 PUSH
            0x60, 0x10, // size PUSH
            0x60, 0xE0, // offset PUSH
            0xA4, // LOG4
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words)/512) + 1;
    ASSERT_EQ(gas - ((verylow*12) + mstore_size + glog + (logdata*16) + (4*logtopic)), result.gas_left);
    print_result(&result);

    struct evm_word topic1 = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1 };
    struct evm_word topic2 = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2 };
    struct evm_word topic3 = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3 };
    struct evm_word topic4 = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 4 };
    struct evm_word data = {
            0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, //
            0x88, 0x99, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55 //
    };

    ASSERT_EQ(4 * 2, log_topics_count);
    ASSERT_TRUE(0 == memcmp(topic1.bytes, &log_topics[0], sizeof(topic1)));
    ASSERT_TRUE(0 == memcmp(topic1.bytes, &log_topics[1], sizeof(topic2)));
    ASSERT_TRUE(0 == memcmp(topic2.bytes, &log_topics[2], sizeof(topic1)));
    ASSERT_TRUE(0 == memcmp(topic2.bytes, &log_topics[3], sizeof(topic2)));
    ASSERT_TRUE(0 == memcmp(topic3.bytes, &log_topics[4], sizeof(topic1)));
    ASSERT_TRUE(0 == memcmp(topic3.bytes, &log_topics[5], sizeof(topic2)));
    ASSERT_TRUE(0 == memcmp(topic4.bytes, &log_topics[6], sizeof(topic1)));
    ASSERT_TRUE(0 == memcmp(topic4.bytes, &log_topics[7], sizeof(topic2)));

    ASSERT_EQ(16, log_data_size);
    ASSERT_TRUE(0 == memcmp(data.bytes, log_data, sizeof(data)));
}

//======================================
// f0s: System operations
//======================================

TEST(instructions, testCREATE) {
    uint8_t const code[] = {
            0x6F, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, //
                  0x88, 0x99, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55, // push one word
            0x60, 0xE0, 0x52, // dump to memory PUSH + MSTORE

            0x60, 0x10, // size PUSH
            0x60, 0xE0, // offset PUSH
            0x60, 0x05, // value PUSH
            0xF0, // CREATE

            0x60, 0xE0, 0x52, // PUSH + MSTORE
            0x60, 0xF0, 0x52, // PUSH + MSTORE
            0x60, 0x20, // PUSH
            0x60, 0xE0, // PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 2000000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words * 1) + ((words * words) / 512) + 1;
    int32_t extra_mstore = 1;
    ASSERT_EQ(gas - (mstore_size + (verylow*9) + create + (extra_mstore*2) + zero), result.gas_left);
    print_result(&result);

    struct evm_word value = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x05 };
    struct evm_word contract_code = {
            0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, //
            0x88, 0x99, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55 //
    };
    struct evm_hash contract_address;
    address2hash(&call_output_addr, &contract_address);

    // check stack
    ASSERT_EQ(sizeof(contract_address), result.output_size);
    ASSERT_TRUE(0 == memcmp(contract_address.bytes, result.output_data, sizeof(contract_address)));

    // check callback
    ASSERT_TRUE(0 == memcmp(address.bytes, call_msg.caller.bytes, sizeof(evm_address))); // caller should be the contract
    ASSERT_TRUE(0 == memcmp(value.bytes, call_msg.value.bytes, sizeof(evm_word)));
    ASSERT_EQ(16, call_msg.input_size);
    ASSERT_TRUE(0 == memcmp(&contract_code, call_msg.input, sizeof(contract_code)));
    ASSERT_GT(gas, call_msg.gas);
    ASSERT_LT(0, call_msg.gas);
    ASSERT_EQ(1, call_msg.depth);
    ASSERT_EQ(EVM_CREATE, call_msg.kind);
    ASSERT_EQ(0, call_msg.flags);
    // code hash is not checked here, as JIT does not provide such data to callback
}


TEST(instructions, testCALL) {
    uint8_t const code[] = {
            0x6F, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, //
                  0x88, 0x99, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55, // push one word
            0x60, 0xE0, 0x52, // dump to memory

            0x60, 0x10, // output size
            0x60, 0xF0, // output offset
            0x60, 0x10, // input size
            0x60, 0xE0, // input offset
            0x60, 0x05, // value
            0x6F, 0x0F, 0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, //
                  0x17, 0x18, 0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E, // PUSH
            0x6F, 0x01, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06,
                  0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, // PUSH
            0x61, 0x13, 0x88, // gas (5000)
            0xF1, // CALL

            0x60, 0x10, 0x60, 0xF0, 0xF3 // RETURN what call returns
    };
    uint8_t const input[] = {};
    int64_t gas = 200000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size =  (words*1) + ((words*words) / 512) + 1;
    int32_t call_nrg = callvalue + gcall + 5000;
    int32_t call_refund = 5000 + callstipend;
    ASSERT_EQ(gas - ((verylow*12) + mstore_size + call_nrg - call_refund + zero), result.gas_left);
    print_result(&result);

    struct evm_word value = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x05 };
    struct evm_address call_address = {
            0x01, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06,
            0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E,
            0x0F, 0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16,
            0x17, 0x18, 0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E,
    };
    struct evm_word call_input = {
            0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, //
            0x88, 0x99, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55 //
    };
    struct evm_word call_return = call_output;

    // check stack
    ASSERT_EQ(sizeof(call_return), result.output_size);
    ASSERT_TRUE(0 == memcmp(call_return.bytes, result.output_data, sizeof(call_return)));

    // check callback
    ASSERT_TRUE(0 == memcmp(call_address.bytes, call_msg.address.bytes, sizeof(evm_address)));
    ASSERT_TRUE(0 == memcmp(address.bytes, call_msg.caller.bytes, sizeof(evm_address))); // caller should be the contract
    ASSERT_TRUE(0 == memcmp(value.bytes, call_msg.value.bytes, sizeof(evm_word)));
    ASSERT_EQ(16, call_msg.input_size);
    ASSERT_TRUE(0 == memcmp(&call_input, call_msg.input, sizeof(call_input)));
    ASSERT_GT(gas, call_msg.gas);
    ASSERT_LT(0, call_msg.gas);
    ASSERT_EQ(1, call_msg.depth);
    ASSERT_EQ(EVM_CALL, call_msg.kind);
    ASSERT_EQ(0, call_msg.flags);
    // code hash is not checked here, as JIT does not provide such data to callback
}

TEST(instructions, testCALLACCTANDVALEXISTS) {
    uint8_t const code[] = {
            0x6F, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, //
                  0x88, 0x99, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55, // push one word
            0x60, 0xE0, 0x52, // dump to memory

            0x60, 0x10, // output size
            0x60, 0xF0, // output offset
            0x60, 0x10, // input size
            0x60, 0xE0, // input offset
            0x60, 0x11, // value
            0x6F, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, //
                  0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, // 3
            0x6F, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, //
                  0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, // code address
            0x61, 0x13, 0x88, // gas (5000)
            0xF1, // CALL

            0x60, 0x10, 0x60, 0xF0, 0xF3 // RETURN what call returns
    };
    uint8_t const input[] = {};
    int64_t gas = 200000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size =  (words*1) + ((words*words) / 512) + 1;
    int32_t call_nrg = newaccount + callvalue + gcall + 5000;
    int32_t call_refund = 5000 + callstipend;
    ASSERT_EQ(gas - ((verylow*12) + mstore_size + call_nrg - call_refund + zero), result.gas_left);
    print_result(&result);

    struct evm_word value = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x11 };
    struct evm_address call_address = {
            0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, //
            0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, //
            0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, //
            0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, //
    };
    struct evm_word call_input = {
            0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, //
            0x88, 0x99, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55 //
    };
    struct evm_word call_return = call_output;

    // check stack
    ASSERT_EQ(sizeof(call_return), result.output_size);
    ASSERT_TRUE(0 == memcmp(call_return.bytes, result.output_data, sizeof(call_return)));

    // check callback
    ASSERT_TRUE(0 == memcmp(call_address.bytes, call_msg.address.bytes, sizeof(evm_address)));
    ASSERT_TRUE(0 == memcmp(address.bytes, call_msg.caller.bytes, sizeof(evm_address))); // caller should be the contract
    ASSERT_TRUE(0 == memcmp(value.bytes, call_msg.value.bytes, sizeof(evm_word)));
    ASSERT_EQ(16, call_msg.input_size);
    ASSERT_TRUE(0 == memcmp(&call_input, call_msg.input, sizeof(call_input)));
    ASSERT_GT(gas, call_msg.gas);
    ASSERT_LT(0, call_msg.gas);
    ASSERT_EQ(1, call_msg.depth);
    ASSERT_EQ(EVM_CALL, call_msg.kind);
    ASSERT_EQ(0, call_msg.flags);
    // code hash is not checked here, as JIT does not provide such data to callback
}

TEST(instructions, testCALLACCTEXISTS) {
    uint8_t const code[] = {
            0x6F, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, //
                  0x88, 0x99, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55, // push one word
            0x60, 0xE0, 0x52, // dump to memory

            0x60, 0x10, // output size
            0x60, 0xF0, // output offset
            0x60, 0x10, // input size
            0x60, 0xE0, // input offset
            0x60, 0x00, // value
            0x6F, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, //
                  0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, //
            0x6F, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, //
                  0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, // code address
            0x61, 0x13, 0x88, // gas (5000)
            0xF1, // CALL

            0x60, 0x10, 0x60, 0xF0, 0xF3 // RETURN what call returns
    };
    uint8_t const input[] = {};
    int64_t gas = 200000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size =  (words*1) + ((words*words) / 512) + 1;
    int32_t call_nrg = gcall + 5000;
    int32_t call_refund = 5000;
    ASSERT_EQ(gas - ((verylow*12) + mstore_size + call_nrg - call_refund + zero), result.gas_left);
    print_result(&result);

    struct evm_word value = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x00 };
    struct evm_address call_address = {
            0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, //
            0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, //
            0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, //
            0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, //
    };
    struct evm_word call_input = {
            0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, //
            0x88, 0x99, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55 //
    };
    struct evm_word call_return = call_output;

    // check stack
    ASSERT_EQ(sizeof(call_return), result.output_size);
    ASSERT_TRUE(0 == memcmp(call_return.bytes, result.output_data, sizeof(call_return)));

    // check callback
    ASSERT_TRUE(0 == memcmp(call_address.bytes, call_msg.address.bytes, sizeof(evm_address)));
    ASSERT_TRUE(0 == memcmp(address.bytes, call_msg.caller.bytes, sizeof(evm_address))); // caller should be the contract
    ASSERT_TRUE(0 == memcmp(value.bytes, call_msg.value.bytes, sizeof(evm_word)));
    ASSERT_EQ(16, call_msg.input_size);
    ASSERT_TRUE(0 == memcmp(&call_input, call_msg.input, sizeof(call_input)));
    ASSERT_GT(gas, call_msg.gas);
    ASSERT_LT(0, call_msg.gas);
    ASSERT_EQ(1, call_msg.depth);
    ASSERT_EQ(EVM_CALL, call_msg.kind);
    ASSERT_EQ(0, call_msg.flags);
    // code hash is not checked here, as JIT does not provide such data to callback
}

TEST(instructions, testCALLCODE) {
    uint8_t const code[] = {
            0x6F, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, //
                  0x88, 0x99, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55, // push one word
            0x60, 0xE0, 0x52, // dump to memory PUSH + MSTORE

            0x60, 0x10, // output size PUSH
            0x60, 0xF0, // output offset PUSH
            0x60, 0x10, // input size PUSH
            0x60, 0xE0, // input offset PUSH
            0x60, 0x05, // value PUSH
            0x6F, 0x0F, 0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, //
                  0x17, 0x18, 0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E, // PUSH
            0x6F, 0x01, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06,
                  0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, // PUSH
            0x61, 0x13, 0x88, // gas (5000) PUSH
            0xF2, // CALLCODE

            0x60, 0x10, 0x60, 0xF0, 0xF3 // RETURN what call returns
    };
    uint8_t const input[] = {};
    int64_t gas = 200000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words) / 512) + 1;
    int32_t call_nrg = callvalue + gcall + 5000;
    int32_t call_refund = 5000 + callstipend;
    ASSERT_EQ(gas - ((verylow*12) + mstore_size + call_nrg - call_refund + zero), result.gas_left);
    print_result(&result);

    struct evm_word value = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x05 };
    struct evm_address call_address = {
            0x01, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06,
            0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E,
            0x0F, 0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16,
            0x17, 0x18, 0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E,
    };
    struct evm_word call_input = {
            0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, //
            0x88, 0x99, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55 //
    };
    struct evm_word call_return = call_output;

    // check stack
    ASSERT_EQ(sizeof(call_return), result.output_size);
    ASSERT_TRUE(0 == memcmp(call_return.bytes, result.output_data, sizeof(call_return)));

    // check callback
    /*
     * NOTE: the address callback receives is the address_of_contract_to_call instead of
     * address_of_current_contract, even though it's CALLCODE. It's callback's responsibility to
     * load the proper contract code and to set the runtime address to the
     * address_of_current_contract.
     */
    ASSERT_TRUE(0 == memcmp(call_address.bytes, call_msg.address.bytes, sizeof(evm_address)));
    ASSERT_TRUE(0 == memcmp(address.bytes, call_msg.caller.bytes, sizeof(evm_address))); // caller should be the contract
    ASSERT_TRUE(0 == memcmp(value.bytes, call_msg.value.bytes, sizeof(evm_word)));
    ASSERT_EQ(16, call_msg.input_size);
    ASSERT_TRUE(0 == memcmp(&call_input, call_msg.input, sizeof(call_input)));
    ASSERT_GT(gas, call_msg.gas);
    ASSERT_LT(0, call_msg.gas);
    ASSERT_EQ(1, call_msg.depth);
    ASSERT_EQ(EVM_CALLCODE, call_msg.kind);
    ASSERT_EQ(0, call_msg.flags);
    // code hash is not checked here, as JIT does not provide such data to callback
}

TEST(instructions, testCALLCODEACCTANDVALEXISTS) {
    uint8_t const code[] = {
            0x6F, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, //
                  0x88, 0x99, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55, // push one word
            0x60, 0xE0, 0x52, // dump to memory

            0x60, 0x10, // output size
            0x60, 0xF0, // output offset
            0x60, 0x10, // input size
            0x60, 0xE0, // input offset
            0x60, 0x11, // value
            0x6F, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, //
                  0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, //
            0x6F, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, //
                  0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, // code address
            0x61, 0x13, 0x88, // gas (5000)
            0xF2, // CALL

            0x60, 0x10, 0x60, 0xF0, 0xF3 // RETURN what call returns
    };
    uint8_t const input[] = {};
    int64_t gas = 200000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size =  (words*1) + ((words*words) / 512) + 1;
    int32_t call_nrg = callvalue + gcall + 5000;
    int32_t call_refund = 5000 + callstipend;
    ASSERT_EQ(gas - ((verylow*12) + mstore_size + call_nrg - call_refund + zero), result.gas_left);
    print_result(&result);

    struct evm_word value = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x11 };
    struct evm_address call_address = {
            0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, //
            0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, //
            0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, //
            0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, //
    };
    struct evm_word call_input = {
            0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, //
            0x88, 0x99, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55 //
    };
    struct evm_word call_return = call_output;

    // check stack
    ASSERT_EQ(sizeof(call_return), result.output_size);
    ASSERT_TRUE(0 == memcmp(call_return.bytes, result.output_data, sizeof(call_return)));

    // check callback
    /*
     * NOTE: the address callback receives is the address_of_contract_to_call instead of
     * address_of_current_contract, even though it's CALLCODE. It's callback's responsibility to
     * load the proper contract code and to set the runtime address to the
     * address_of_current_contract.
     */
    ASSERT_TRUE(0 == memcmp(call_address.bytes, call_msg.address.bytes, sizeof(evm_address)));
    ASSERT_TRUE(0 == memcmp(address.bytes, call_msg.caller.bytes, sizeof(evm_address))); // caller should be the contract
    ASSERT_TRUE(0 == memcmp(value.bytes, call_msg.value.bytes, sizeof(evm_word)));
    ASSERT_EQ(16, call_msg.input_size);
    ASSERT_TRUE(0 == memcmp(&call_input, call_msg.input, sizeof(call_input)));
    ASSERT_GT(gas, call_msg.gas);
    ASSERT_LT(0, call_msg.gas);
    ASSERT_EQ(1, call_msg.depth);
    ASSERT_EQ(EVM_CALLCODE, call_msg.kind);
    ASSERT_EQ(0, call_msg.flags);
    // code hash is not checked here, as JIT does not provide such data to callback
}

TEST(instructions, testCALLCODEACCTEXISTS) {
    uint8_t const code[] = {
            0x6F, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, //
                  0x88, 0x99, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55, // push one word
            0x60, 0xE0, 0x52, // dump to memory
            0x60, 0x10, // output size
            0x60, 0xF0, // output offset
            0x60, 0x10, // input size
            0x60, 0xE0, // input offset
            0x60, 0x00, // value
            0x6F, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, //
                  0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, //
            0x6F, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, //
                  0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, // code address
            0x61, 0x13, 0x88, // gas (5000)
            0xF2, // CALLCODE

            0x60, 0x10, 0x60, 0xF0, 0xF3 // RETURN what call returns
    };
    uint8_t const input[] = {};
    int64_t gas = 200000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size =  (words*1) + ((words*words) / 512) + 1;
    int32_t call_nrg = gcall + 5000;
    int32_t call_refund = 5000;
    ASSERT_EQ(gas - ((verylow*12) + mstore_size + call_nrg - call_refund + zero), result.gas_left);
    print_result(&result);

    struct evm_word value = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
    struct evm_address call_address = {
            0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, //
            0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, //
            0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, //
            0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, //
    };
    struct evm_word call_input = {
            0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, //
            0x88, 0x99, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55 //
    };
    struct evm_word call_return = call_output;

    // check stack
    ASSERT_EQ(sizeof(call_return), result.output_size);
    ASSERT_TRUE(0 == memcmp(call_return.bytes, result.output_data, sizeof(call_return)));

    // check callback
    /*
     * NOTE: the address callback receives is the address_of_contract_to_call instead of
     * address_of_current_contract, even though it's CALLCODE. It's callback's responsibility to
     * load the proper contract code and to set the runtime address to the
     * address_of_current_contract.
     */
    ASSERT_TRUE(0 == memcmp(call_address.bytes, call_msg.address.bytes, sizeof(evm_address)));
    ASSERT_TRUE(0 == memcmp(address.bytes, call_msg.caller.bytes, sizeof(evm_address))); // caller should be the contract
    ASSERT_TRUE(0 == memcmp(value.bytes, call_msg.value.bytes, sizeof(evm_word)));
    ASSERT_EQ(16, call_msg.input_size);
    ASSERT_TRUE(0 == memcmp(&call_input, call_msg.input, sizeof(call_input)));
    ASSERT_GT(gas, call_msg.gas);
    ASSERT_LT(0, call_msg.gas);
    ASSERT_EQ(1, call_msg.depth);
    ASSERT_EQ(EVM_CALLCODE, call_msg.kind);
    ASSERT_EQ(0, call_msg.flags);
    // code hash is not checked here, as JIT does not provide such data to callback
}

TEST(instructions, testDELEGATECALL) {
    uint8_t const code[] = {
            0x6F, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, //
                  0x88, 0x99, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55, // push one word
            0x60, 0xE0, 0x52, // dump to memory

            0x60, 0x10, // output size PUSH
            0x60, 0xF0, // output offset PUSH
            0x60, 0x10, // input size PUSH
            0x60, 0xE0, // input offset PUSH
            0x6F, 0x0F, 0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, //
                  0x17, 0x18, 0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E, // PUSH
            0x6F, 0x01, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06,
                  0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, // PUSH
            0x61, 0x13, 0x88, // gas (5000) PUSH
            0xF4, // DELEGATECALL

            0x60, 0x10, 0x60, 0xF0, 0xF3 // RETURN what call returns PUSH
    };
    uint8_t const input[] = {};
    int64_t gas = 200000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words)/512) + 1;
    int32_t call_nrg = gcall + 5000;
    int32_t call_refund = 5000;
    ASSERT_EQ(gas - ((verylow*11) + mstore_size + call_nrg - call_refund + zero), result.gas_left);
    print_result(&result);

    struct evm_address call_address = {
            0x01, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06,
            0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E,
            0x0F, 0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16,
            0x17, 0x18, 0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E,
    };
    struct evm_word call_input = {
            0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, //
            0x88, 0x99, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55 //
    };
    struct evm_word call_return = call_output;

    // check stack
    ASSERT_EQ(sizeof(call_return), result.output_size);
    ASSERT_TRUE(0 == memcmp(call_return.bytes, result.output_data, sizeof(call_return)));

    ASSERT_TRUE(0 == memcmp(call_address.bytes, call_msg.address.bytes, sizeof(evm_address)));
    ASSERT_TRUE(0 == memcmp(caller.bytes, call_msg.caller.bytes, sizeof(evm_address))); // caller should be the contract
    ASSERT_TRUE(0 == memcmp(value.bytes, call_msg.value.bytes, sizeof(evm_word)));
    ASSERT_EQ(16, call_msg.input_size);
    ASSERT_TRUE(0 == memcmp(&call_input, call_msg.input, sizeof(call_input)));
    ASSERT_GT(gas, call_msg.gas);
    ASSERT_LT(0, call_msg.gas);
    ASSERT_EQ(1, call_msg.depth);
    ASSERT_EQ(EVM_DELEGATECALL, call_msg.kind);
    ASSERT_EQ(0, call_msg.flags);
    // code hash is not checked here, as JIT does not provide such data to callback
}

TEST(instructions, testDELEGATECALLACCTANDVALEXISTS) {
    uint8_t const code[] = {
            0x6F, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, //
                  0x88, 0x99, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55, // push one word
            0x60, 0xE0, 0x52, // dump to memory

            0x60, 0x10, // output size
            0x60, 0xF0, // output offset
            0x60, 0x10, // input size
            0x60, 0xE0, // input offset
            0x60, 0x11, // value
            0x6F, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, //
                  0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, //
            0x6F, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, //
                  0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, // code address
            0x61, 0x13, 0x88, // gas (5000)
            0xF4, // DELEGATE CALL

            0x60, 0x10, 0x60, 0xF0, 0xF3 // RETURN what call returns
    };
    uint8_t const input[] = {};
    int64_t gas = 200000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size =  (words*1) + ((words*words) / 512) + 1;
    int32_t call_nrg =  gcall + 5000;
    int32_t call_refund = 5000;
    ASSERT_EQ(gas - ((verylow*12) + mstore_size + call_nrg - call_refund + zero), result.gas_left);
    print_result(&result);

    struct evm_address call_address = {
            0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, //
            0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, //
            0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, //
            0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, //
    };
    struct evm_word call_return = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };

    // check stack
    ASSERT_EQ(sizeof(call_return), result.output_size);
    ASSERT_TRUE(0 == memcmp(call_return.bytes, result.output_data, sizeof(call_return)));

    ASSERT_TRUE(0 == memcmp(call_address.bytes, call_msg.address.bytes, sizeof(evm_address)));
    ASSERT_TRUE(0 == memcmp(caller.bytes, call_msg.caller.bytes, sizeof(evm_address))); // caller should be the contract
    ASSERT_TRUE(0 == memcmp(value.bytes, call_msg.value.bytes, sizeof(evm_word)));
    ASSERT_GT(gas, call_msg.gas);
    ASSERT_LT(0, call_msg.gas);
    ASSERT_EQ(1, call_msg.depth);
    ASSERT_EQ(EVM_DELEGATECALL, call_msg.kind);
    ASSERT_EQ(0, call_msg.flags);
    // code hash is not checked here, as JIT does not provide such data to callback
}

TEST(instructions, testDELEGATECALLACCTEXISTS) {
    uint8_t const code[] = {
            0x6F, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, //
                  0x88, 0x99, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55, // push one word
            0x60, 0xE0, 0x52, // dump to memory
            0x60, 0x10, // output size
            0x60, 0xF0, // output offset
            0x60, 0x10, // input size
            0x60, 0xE0, // input offset
            0x60, 0x00, // value
            0x6F, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, //
                  0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, //
            0x6F, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, //
                   0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, // code address
            0x61, 0x13, 0x88, // gas (5000)
            0xF4, // DELEGATE CALL
            0x60, 0x10, 0x60, 0xF0, 0xF3 // RETURN what call returns
    };
    uint8_t const input[] = {};
    int64_t gas = 200000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size =  (words*1) + ((words*words) / 512) + 1;
    int32_t call_nrg = gcall + 5000;
    int32_t call_refund = 5000;
    ASSERT_EQ(gas - ((verylow*12) + mstore_size + call_nrg - call_refund + zero), result.gas_left);
    print_result(&result);

    struct evm_address call_address = {
            0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, //
            0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, //
            0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, //
            0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, //
    };

    struct evm_word call_return = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };

    // check stack
    ASSERT_EQ(sizeof(call_return), result.output_size);
    ASSERT_TRUE(0 == memcmp(call_return.bytes, result.output_data, sizeof(call_return)));

    // check callback
    ASSERT_TRUE(0 == memcmp(call_address.bytes, call_msg.address.bytes, sizeof(evm_address)));
    ASSERT_TRUE(0 == memcmp(caller.bytes, call_msg.caller.bytes, sizeof(evm_address))); // caller should be the contract
    ASSERT_TRUE(0 == memcmp(value.bytes, call_msg.value.bytes, sizeof(evm_word)));
    ASSERT_GT(gas, call_msg.gas);
    ASSERT_LT(0, call_msg.gas);
    ASSERT_EQ(1, call_msg.depth);
    ASSERT_EQ(EVM_DELEGATECALL, call_msg.kind);
    ASSERT_EQ(0, call_msg.flags);
    // code hash is not checked here, as JIT does not provide such data to callback
}

TEST(instructions, testINVALID) {
    uint8_t const code[] = {
            0xFE, // INVALID
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    ASSERT_EQ(0, result.gas_left);
    print_result(&result);

    ASSERT_EQ(0, result.gas_left);
}

TEST(instructions, testINVALIDRETURN) {
    uint8_t const code[] = {
            0x6F, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, //
                  0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, // push one word
            0xFE, // INVALID
            0xF3
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    ASSERT_EQ(0, result.gas_left);
    print_result(&result);

    ASSERT_EQ(0, result.gas_left);
}

TEST(instructions, testINVALIDCREATE) {
    uint8_t const code[] = {
            0x6F, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, //
                  0x88, 0x99, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55, // push one word
            0x60, 0xE0, 0x52, // dump to memory PUSH + MSTORE

            0x60, 0x10, // size PUSH
            0x60, 0xE0, // offset PUSH
            0x60, 0x05, // value PUSH
            0xF0, // CREATE
            0xFE // INVALID
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    ASSERT_EQ(0, result.gas_left);
    print_result(&result);

    ASSERT_EQ(0, result.gas_left);
}

TEST(instructions, testSELFDESTRUCT) {
    uint8_t const code[] = {
        0x6F, 0x0F, 0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, //
              0x17, 0x18, 0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E, // PUSH
        0x6F, 0x01, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06,
              0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, // PUSH
        0xFF, // SELFDESTRUCT 5000
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    ASSERT_EQ(gas - ((verylow*2) + suicide), result.gas_left);
    print_result(&result);

    struct evm_address beneficiary = {
            0x01, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06,
            0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, // PUSH 3
            0x0F, 0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, //
            0x17, 0x18, 0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E, // PUSH 3
    };

    ASSERT_TRUE(0 == memcmp(address.bytes, self_destruct_addr.bytes, sizeof(evm_address)));
    ASSERT_TRUE(0 == memcmp(beneficiary.bytes, self_destruct_bene.bytes, sizeof(evm_address)));
}

TEST(instructions, testSELFDESTRUCTMAX) {
    uint8_t const code[] = {
            0x6F, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, //
                  0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, // PUSH
            0x6F, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, //
                  0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, // beneficiary
            0xFF, // SELFDESTRUCT
    };
    uint8_t const input[] = {};
    int64_t gas = 20000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    ASSERT_EQ(gas - ((verylow*2) + suicide), result.gas_left);
    print_result(&result);

    struct evm_address beneficiary = {
            0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, //
            0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, //
            0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, //
            0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, //
    };

    ASSERT_TRUE(0 == memcmp(address.bytes, self_destruct_addr.bytes, sizeof(evm_address)));
    ASSERT_TRUE(0 == memcmp(beneficiary.bytes, self_destruct_bene.bytes, sizeof(evm_address)));
}

TEST(instructions, testRETURNDATASIZE) {
    uint8_t const code[] = {
            0x6F, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, //
                  0x88, 0x99, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55, // push one word
            0x60, 0xE0, 0x52, // dump to memory PUSH + MSTORE

            0x60, 0x10, // output size // PUSH
            0x60, 0xF0, // output offset // PUSH
            0x60, 0x10, // input size PUSH
            0x60, 0xE0, // input offset PUSH
            0x60, 0x05, // value
            0x6F, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88, //
                  0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88, // PUSH
            0x6F, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, //
                  0x00, 0x00, 0x00, 0x00, 0x11, 0x22, 0x33, 0x44, // code address PUSH
            0x61, 0x13, 0x88, // gas (5000) PUSH
            0xF1, // CALL

            0x3D, // RETURNDATASIZE

            0x60, 0xE0, // PUSH1 0xE0
            0x52, // MSTORE
            0x60, 0x10, // PUSH1 0x10
            0x60, 0xE0, // PUSH1 0xE0
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 200000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words)/512) + 1;
    int32_t extra_mstore = 1;
    int32_t call_nrg = callvalue + gcall + 5000;
    int32_t call_refund = 5000 + callstipend;
    ASSERT_EQ(gas - ((verylow*13) + mstore_size + call_nrg - call_refund + (extra_mstore*2) + zero), result.gas_left);
    print_result(&result);

    struct evm_word gt = {
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,//
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, sizeof(call_output) //
    };

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(evm_word)));
}

TEST(instructions, testRETURNDATASIZECREATE) {
    uint8_t const code[] = {
            0x6F, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, //
                  0x88, 0x99, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55, // push one word
            0x60, 0xE0, 0x52, // dump to memory

            0x60, 0x10, // size PUSH
            0x60, 0xE0, // offset PUSH
            0x60, 0x05, // value PUSH
            0xF0, // CREATE
            0x3D, // RETURNDATASIZE

            0x60, 0xE0, // PUSH1 0xE0
            0x52, // MSTORE
            0x60, 0x10, // PUSH1 0x10
            0x60, 0xE0, // PUSH1 0xE0
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 2000000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words)/512)  + 1;
    int32_t extra_mstore = 1;
    ASSERT_EQ(gas - ((verylow*8) + mstore_size + create + (extra_mstore*2) + zero), result.gas_left);
    print_result(&result);

    struct evm_word gt = {
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,//
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x20 //
    };

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(evm_word)));
}

TEST(instructions, testRETURNDATASIZESSTORE) {
    uint8_t const code[] = {
            0x6F, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77,
                  0x88, 0x99, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55, // push one word
            0x60, 0xE0,
            0x52, // dump to memory
            0x6F, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, //
                  0x88, 0x99, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55, // push one word
            0x6F, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, //
                  0x88, 0x99, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55, // PUSH
            0x60, 0x22, // PUSH
            0x55, // SSTORE to 0x22
            0x3D, // RETURNDATASIZE

            0x60, 0xE0, // PUSH1 0xE0
            0x52, // MSTORE
            0x60, 0x10, // PUSH1 0x10
            0x60, 0xE0, // PUSH1 0xE0
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 200000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words)/512)  + 1;
    int32_t extra_mstore = 1;
    ASSERT_EQ(gas - ((verylow*8) + mstore_size + sset + (extra_mstore*2) + zero), result.gas_left);
    print_result(&result);

    struct evm_word gt = {
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,//
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 //
    };

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(evm_word)));
}

TEST(instructions, testRETURNDATACOPY) {
    uint8_t const code[] = {
            0x6F, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, //
                  0x88, 0x99, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55, // push one word
            0x60, 0xE0,
            0x52, // dump to memory PUSH + mstore
            0x60, 0x10, // output size PUSH
            0x60, 0xF0, // output offset PUSH
            0x60, 0x10, // input size PUSH
            0x60, 0xE0, // input offset PUSH
            0x60, 0x05, // value PUSH
            0x6F, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88, //
                  0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88, // PUSH
            0x6F, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, //
                  0x00, 0x00, 0x00, 0x00, 0x11, 0x22, 0x33, 0x44, // code address PUSH
            0x61, 0x13, 0x88, // gas (5000) PUSH
            0xF1, // CALL

            0x60, 0x01, // 1 bytes PUSH
            0x60, 0x02, // src index = 0x02 PUSH
            0x60, 0xE0, // dst index = 0xE0 PUSH
            0x3E, // RETURNDATACOPY

            0x60, 0x01, // PUSH1 0x10 PUSH
            0x60, 0xE0, // PUSH1 0xE0 PUSH
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 200000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    int32_t words = ((0xe0 + 16) + 31)/32;
    int32_t mstore_size = ((words*1) + (words*words)/512) + 1;
    int32_t call_nrg = callvalue + gcall + 5000;
    int32_t call_refund = 5000 + callstipend;
    ASSERT_EQ(gas - ((verylow*15) + verylow + (gcopy*((0x01 + 31)/32)) + mstore_size + call_nrg - call_refund + zero), result.gas_left);
    print_result(&result);

    ASSERT_EQ(1, result.output_size);
    ASSERT_EQ(0x22, result.output_data[0]);
}


TEST(instructions, testRETURNDATACOPY_beyondRange1) {
    uint8_t const code[] = {
            0x6F, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, //
                  0x88, 0x99, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55, // push one word
            0x60, 0xE0, 0x52, // dump to memory

            0x60, 0x10, // output size
            0x60, 0xF0, // output offset
            0x60, 0x10, // input size
            0x60, 0xE0, // input offset
            0x60, 0x05, // value
            0x6F, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88, //
                  0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88, //
            0x6F, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, //
                  0x00, 0x00, 0x00, 0x00, 0x11, 0x22, 0x33, 0x44, // code address
            0x61, 0x13, 0x88, // gas (5000)
            0xF1, // CALL

            0x61, 0xFF, 0xFF, // 0xffff bytes
            0x60, 0x02, // src index = 0x02
            0x60, 0xE0, // dst index = 0xE0
            0x3E, // RETURNDATACOPY

            0x60, 0x01, // PUSH1 0x10
            0x60, 0xE0, // PUSH1 0xE0
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 200000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    ASSERT_EQ(0, result.gas_left);
    print_result(&result);


}

TEST(instructions, testRETURNDATACOPY_beyondRange2) {
    uint8_t const code[] = {
            0x6F, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, //
                  0x88, 0x99, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55, // push one word
            0x60, 0xE0, 0x52, // dump to memory PUSH + MSTORE

            0x60, 0x10, // output size PUSH
            0x60, 0xF0, // output offset PUSH
            0x60, 0x10, // input size PUSH
            0x60, 0xE0, // input offset PUSH
            0x60, 0x05, // value PUSH
            0x6F, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88, //
                  0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88, // PUSH
            0x6F, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, //
                  0x00, 0x00, 0x00, 0x00, 0x11, 0x22, 0x33, 0x44, // code address PUSH
            0x61, 0x13, 0x88, // gas (5000)
            0xF1, // CALL

            0x60, 0x01, // 1 bytes PUSH
            0x61, 0xFF, 0xFF, // src index = 0xffff PUSH
            0x60, 0xE0, // dst index = 0xE0 PUSH
            0x3E, // RETURNDATACOPY

            0x60, 0x01, // PUSH1 0x10
            0x60, 0xE0, // PUSH1 0xE0
            0xF3 // RETURN
    };
    uint8_t const input[] = {};
    int64_t gas = 200000;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    ASSERT_EQ(0, result.gas_left);
    print_result(&result);

    //ASSERT_EQ(gas - 50, result.gas_left);
}

TEST(instructions, testSTATICCALL) {
    uint8_t const code[] = {
            0x6F, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, //
                  0x88, 0x99, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55, // PUSH
            0x60, 0x22, // PUSH
            0x55, // SSTORE to 0x22
    };
    uint8_t const input[] = {};
    int64_t gas = 20000 * 2; // insert => 20000, reset = 5000

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    msg.flags = EVM_STATIC;
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
            code, sizeof(code));
    ASSERT_EQ(0, result.gas_left);
    print_result(&result);

    ASSERT_TRUE(0 != result.status_code);
}

//======================================
// Revert operations
//======================================

TEST(misc, testRevertExhaustedGas) {
    uint8_t const code[] = {
            0x6F, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, //
                  0x88, 0x99, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55, //PUSH
            0x60, 0x02, // PUSH 0x01
            0x01, // ADD
            0x60, 0xE0, // offset PUSH
            0x52, // MSTORE
            0x60, 0x01, // size PUSH
            0x60, 0xE0, // offset PUSH
            0xfd // REVERT

    };
    uint8_t const input[] = {};
    int64_t gas = 15;
    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg, code, sizeof(code));
    print_result(&result);
    ASSERT_EQ(0, result.gas_left);

    release_result(&result);

}

TEST(misc, testRevertNotExhaustedGas) {
    uint8_t const code[] = {
        0x6F, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, //
              0x88, 0x99, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55, //PUSH max_int
        0x60, 0x02, // PUSH 0x01
        0x01,         // ADD
        0x60, 0xE0,    // offset
        0x52,        // MSTORE
        0x60, 0x01,    // size
        0x60, 0xE0,    // offset
        0xfd        // REVERT
    };

    uint8_t const input[] = {};
    int64_t gas = 17;

    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg, code, sizeof(code));
    print_result(&result);
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words)/512) + 1;
    ASSERT_EQ(gas - ((verylow*6) + mstore_size), result.gas_left);

    ASSERT_EQ(NULL, result.output_data[0]);
    ASSERT_EQ( 1, result.output_size );

    release_result(&result);
}

TEST(misc, testRevertStackUnderflow) {
    uint8_t const code[] = {
            0x60, 0x01,    // PUSH 0x01
            0x60, 0xE0, // offset
            0x52,        // MSTORE
            0x60, 0xE0,    // offset
            0xfd        // REVERT

    };
    uint8_t const input[] = {};
    int64_t gas = 20000;
    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg, code, sizeof(code));
    print_result(&result);
    ASSERT_EQ(0, result.gas_left);
    ASSERT_EQ(NULL, result.output_data);
    ASSERT_EQ(0, result.output_size);

    release_result(&result);
}

TEST(misc, testRevertRemainingGas) {
    uint8_t const code[] = {
            0x6F, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, //
                  0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, //size
            0x60, 0xE0, // offset
            0x52,         // MSTORE
            0x60, 0x01, // size
            0x60, 0xE0, // offset
            0xfd         // REVERT

    };
    uint8_t const input[] = {};
    int64_t gas = 20000;
    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg, code, sizeof(code));
    print_result(&result);
    int32_t words = ((0xe0 + 16) + 31) / 32;
    int32_t mstore_size = (words*1) + ((words*words)/512) + 1;
    ASSERT_EQ(gas - ((verylow*4) + mstore_size), result.gas_left);

    uint8_t gt = 0xFF;
    ASSERT_EQ(gt, result.output_data[0]);
    ASSERT_EQ( 1, result.output_size );

    release_result(&result);

}

TEST(misc, testRevertCreate) {
    uint8_t const code[] = {
            0x6F, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77,
                  0x88, 0x99, 0x00, 0x11, 0x22, 0x33, 0xfd, 0x44, // PUSH one word
            0x60, 0xE0, // offset PUSH
            0x52, // dump to memory MSTORE
            0x60, 0x10, // size PUSH
            0x60, 0xE0, // offset PUSH
            0xF0, 0x05, // value PUSH
            0xF0, // CREATE
            0x60, 0xE0, // offset PUSH
            0x52, // MSTORE
            0x60, 0xF0, // offset PUSH
            0x52, // MSTORE
            0x60, 0x20, // PUSH
            0x60, 0xE0, // PUSH
            0xF3 // RETURN

    };

    uint8_t const input[] = {};
    int64_t gas = 2000000;
    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg, code, sizeof(code));

    ASSERT_EQ(NULL, result.output_data);
    ASSERT_EQ(0, result.output_size);
    ASSERT_EQ(0, result.gas_left);
    print_result(&result);
    release_result(&result);

    //check callback
     ASSERT_EQ(0, call_msg.depth);
     ASSERT_EQ(0, call_msg.flags);
}

TEST(misc, testRevertCall) {
    uint8_t const code[] = {
            0x6F, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, //
                  0x88, 0x99, 0x00, 0x11, 0x22, 0x33, 0xfd, 0x44, // push one word
            0x60, 0xE0, 0x52, // dump to memory // PUSH MSTORE
            0x60, 0x10, // output size PUSH
            0x60, 0xF0, // output offset PUSH
            0x60, 0x10, // input size PUSH
            0x60, 0xE0, // input offset PUSH
            0x60, 0x05, // value PUSH
            0x6F, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88, //
                  0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88, // PUSH
            0x6F, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, //
                  0x00, 0x00, 0x00, 0x00, 0x11, 0x22, 0x33, 0x44, // code address PUSH
            0x61, 0x13, 0x88, // gas (5000)
            0xF1,               // CALL
            0x60, 0xE0,       // offset PUSH
            0x52,               // MSTORE
            0x60, 0x20, // PUSH
            0x60, 0xE0, // PUSH
            0xF3 // RETURN
    };

    uint8_t const input[] = {};
    int64_t gas = 200000;
    setup_message(code, sizeof(code), input, sizeof(input), gas);
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg, code, sizeof(code));

    struct evm_hash gt = {
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88,
        0x99, 0xaa, 0xbb, 0xcc, 0xdd, 0xee, 0xff //
    };

    ASSERT_EQ(sizeof(gt), result.output_size);
    ASSERT_TRUE(0 == memcmp(gt.bytes, result.output_data, sizeof(evm_word)));

    int32_t words = ((0xe0 + 16) + 31)/32;
    int32_t mstore_size = (words*1) + ((words*words)/512) + 1;
    int32_t extra_mstore = 1;
    int32_t call_nrg = callvalue + gcall + 5000;
    int32_t call_refund = 5000 + callstipend;
    ASSERT_EQ(gas - ((verylow*13) + mstore_size + (extra_mstore*1) + call_nrg - call_refund + zero), result.gas_left);
    print_result(&result);
    release_result(&result);

    ASSERT_EQ(0, call_msg.depth);
    ASSERT_EQ(0, call_msg.flags);
}

//======================================
// Other stuff
//======================================

TEST(misc, DISABLED_testMemoryLeak) {
    for (int i = 0; i < 1000000; i++) {
        uint8_t const code[] = {
                0x00, // STOP
                0x63, // PUSH4
                (uint8_t)((i >> 24) & 0xFF),
                (uint8_t)((i >> 16) & 0xFF),
                (uint8_t)((i >> 8) & 0xFF),
                (uint8_t)(i & 0xFF),

                0x60, 0xE0, 0x52, 0x60, 0x10, 0x60, 0xE0, 0xF3 // RETURN
        };
        uint8_t const input[] = {};
        int64_t gas = 20000;

        setup_message(code, sizeof(code), input, sizeof(input), gas);
        struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg,
                code, sizeof(code));
        release_result(&result);

        if (i % 1000 == 0) {
            cout << i << endl;
        }
    }
}

TEST(misc, testVMBench) {
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
    uint8_t const input[] = { };
    int64_t gas = 200000;
    setup_message(code, sizeof(code), input, sizeof(input), gas);

    // compile once
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg, code, sizeof(code));
    printf("\n  Energy used: %" PRId64 "\n", gas - result.gas_left);
    printf("  Energy left: %" PRId64 "\n", result.gas_left);
    printf("  Output size: %zd\n", result.output_size);
    printf("  Output: ");
    size_t i = 0;
    for (i = 0; i < result.output_size; i++) {
        printf("%02x ", result.output_data[i]);
    }
    printf("\n");
    if (result.release) {
        result.release(&result);
    }

    // benchmark
    int repeat = 1000;
    clock_t begin = clock();
    for (int i = 0; i < repeat; i++) {
        // run the vm
        result = instance->execute(instance, &context, EVM_AION, &msg, code, sizeof(code));

        // release resources
        if (result.release) {
            result.release(&result);
        }
    }
    clock_t end = clock();
    printf("\n  Time elapsed: %zd μs per execution\n\n", 1000000 * (end - begin) / repeat / CLOCKS_PER_SEC);
}

TEST(misc, testBenchMath1) {
    char hex[] = "60506040526000356c01000000000000000000000000900463ffffffff16806357a7744b14610039578063ff40565e14610070575b600080fd5b341561004457600080fd5b61005a60048080359060100190919050506100a7565b6040518082815260100191505060405180910390f35b341561007b57600080fd5b61009160048080359060100190919050506100db565b6040518082815260100191505060405180910390f35b6000806000809150600190505b83811115156100d05780820191505b80806001019150506100b4565b8192505b5050919050565b60006001821115156100ef57819050610110565b6100fb600283036100db565b610107600184036100db565b019050610110565b5b9190505600a165627a7a7230582073a05b69a9a55296da80c7597f32df5ec7fcd109af08d9e7a7c6bc2a7bbd790a0029";
    uint8_t code[(sizeof(hex) - 1) / 2] = {};
    hex2bin(hex, (char*) code);

    uint8_t const input[] = { 0x57, 0xa7, 0x74, 0x4b, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x04, 0x00 };
    int64_t gas = 2000000;
    struct evm_word value = {};
    setup_message(code, sizeof(code), input, sizeof(input), gas, value);

    // compile once
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg, code, sizeof(code));
    printf("\n  Energy used: %" PRId64 "\n", gas - result.gas_left);
    printf("  Energy left: %" PRId64 "\n", result.gas_left);
    printf("  Output size: %zd\n", result.output_size);
    printf("  Output: ");
    size_t i = 0;
    for (i = 0; i < result.output_size; i++) {
        printf("%02x ", result.output_data[i]);
    }
    printf("\n");
    if (result.release) {
        result.release(&result);
    }

    // benchmark
    int repeat = 1000;
    clock_t begin = clock();
    for (int i = 0; i < repeat; i++) {
        // run the vm
        result = instance->execute(instance, &context, EVM_AION, &msg, code, sizeof(code));

        // release resources
        if (result.release) {
            result.release(&result);
        }
    }
    clock_t end = clock();
    printf("\n  Time elapsed: %zd μs per execution\n\n", 1000000 * (end - begin) / repeat / CLOCKS_PER_SEC);
}

TEST(misc, testBenchMath2) {
    char hex[] = "60506040526000356c01000000000000000000000000900463ffffffff16806357a7744b14610039578063ff40565e14610070575b600080fd5b341561004457600080fd5b61005a60048080359060100190919050506100a7565b6040518082815260100191505060405180910390f35b341561007b57600080fd5b61009160048080359060100190919050506100db565b6040518082815260100191505060405180910390f35b6000806000809150600190505b83811115156100d05780820191505b80806001019150506100b4565b8192505b5050919050565b60006001821115156100ef57819050610110565b6100fb600283036100db565b610107600184036100db565b019050610110565b5b9190505600a165627a7a7230582073a05b69a9a55296da80c7597f32df5ec7fcd109af08d9e7a7c6bc2a7bbd790a0029";
    uint8_t code[(sizeof(hex) - 1) / 2] = {};
    hex2bin(hex, (char*) code);

    uint8_t const input[] = { 0xff, 0x40, 0x56, 0x5e, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x10 };
    int64_t gas = 2000000;
    struct evm_word value = {};
    setup_message(code, sizeof(code), input, sizeof(input), gas, value);

    // compile once
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg, code, sizeof(code));
    printf("\n  Energy used: %" PRId64 "\n", gas - result.gas_left);
    printf("  Energy left: %" PRId64 "\n", result.gas_left);
    printf("  Output size: %zd\n", result.output_size);
    printf("  Output: ");
    size_t i = 0;
    for (i = 0; i < result.output_size; i++) {
        printf("%02x ", result.output_data[i]);
    }
    printf("\n");
    if (result.release) {
        result.release(&result);
    }

    // benchmark
    int repeat = 1000;
    clock_t begin = clock();
    for (int i = 0; i < repeat; i++) {
        // run the vm
        result = instance->execute(instance, &context, EVM_AION, &msg, code, sizeof(code));

        // release resources
        if (result.release) {
            result.release(&result);
        }
    }
    clock_t end = clock();
    printf("\n  Time elapsed: %zd μs per execution\n\n", 1000000 * (end - begin) / repeat / CLOCKS_PER_SEC);
}

TEST(misc, testBenchToken) {
    char hex[] = "60506040526000356c01000000000000000000000000900463ffffffff16806369d3e20e14610039578063fbb001d61461005c575b600080fd5b341561004457600080fd5b61005a60048080359060100190919050506100ae565b005b341561006757600080fd5b610094600480808060100135903563ffffffff1690916020019091929080359060100190919050506100e5565b604051808215151515815260100191505060405180910390f35b806000803363ffffffff1663ffffffff16825281601001526020019081526010016000209050600082825401925050819055505b50565b6000816000803363ffffffff1663ffffffff16825281601001526020019081526010016000209050541015801561011c5750600082115b80156101755750600080858563ffffffff1663ffffffff168252816010015260200190815260100160002090505482600080878763ffffffff1663ffffffff168252816010015260200190815260100160002090505401115b1561023257816000803363ffffffff1663ffffffff168252816010015260200190815260100160002090506000828254039250508190555081600080868663ffffffff1663ffffffff1682528160100152602001908152601001600020905060008282540192505081905550838363ffffffff163363ffffffff167f27772adc63db07aae765b71eb2b533064fa781bd57457e1b138592d8198d09599050866040518082815260100191505060405180910390a56001905061023c565b6000905061023c565b5b93925050505600a165627a7a72305820ab558bca80e952c903fdbb68155ff1c94c9135ccc90c2f610d4e91501e12dd8c0029";
    uint8_t code[(sizeof(hex) - 1) / 2] = {};
    hex2bin(hex, (char*) code);

    uint8_t const input[] = {
            0x69, 0xd3, 0xe2, 0x0e, // mint(uint128)
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x27, 0x10
    };
    int64_t gas = 2000000;
    struct evm_word value = {};
    setup_message(code, sizeof(code), input, sizeof(input), gas, value);

    // compile once
    struct evm_result result = instance->execute(instance, &context, EVM_AION, &msg, code, sizeof(code));
    printf("\n  Energy used: %" PRId64 "\n", gas - result.gas_left);
    printf("  Energy left: %" PRId64 "\n", result.gas_left);
    printf("  Output size: %zd\n", result.output_size);
    printf("  Output: ");
    size_t i = 0;
    for (i = 0; i < result.output_size; i++) {
        printf("%02x ", result.output_data[i]);
    }
    printf("\n");
    if (result.release) {
        result.release(&result);
    }

    // benchmark
    int repeat = 1000;
    clock_t begin = clock();
    for (int i = 0; i < repeat; i++) {
        // run the vm
        result = instance->execute(instance, &context, EVM_AION, &msg, code, sizeof(code));

        // release resources
        if (result.release) {
            result.release(&result);
        }
    }
    clock_t end = clock();
    printf("\n  Time elapsed: %zd μs per execution\n\n", 1000000 * (end - begin) / repeat / CLOCKS_PER_SEC);
}
