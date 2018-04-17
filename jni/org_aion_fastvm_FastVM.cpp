#include <cstdlib>
#include <cstring>
#include <algorithm>

#include "org_aion_fastvm_FastVM.h"
#include "evmjit.h"

using namespace std;

namespace dev {
namespace evmjit {
void keccak(uint8_t const *_data, uint64_t _size, uint8_t *o_hash);
}
}

// cb_env is updated every JNI call, as it's thread-specific
JNIEnv *cb_env;

// global references
jclass cb_cls;
jmethodID cb_get_block_hash;
jmethodID cb_get_code;
jmethodID cb_get_balance;
jmethodID cb_exists;
jmethodID cb_get_storage;
jmethodID cb_put_storage;
jmethodID cb_selfdestruct;
jmethodID cb_log;
jmethodID cb_call;

// do you need a stack for recursive call?
struct evm_tx_context ctx;

/* forward declaration */
void parse_context(JNIEnv *env, jbyte *b);
jbyteArray encode_message(JNIEnv *env, const struct evm_message *msg);
jbyteArray encode_message(JNIEnv *env, const struct evm_message *msg);
jbyteArray encode_result(JNIEnv *env, const struct evm_result *result);
void parse_result(struct evm_result *result, jbyte *p);

/**
 * allocate and copy memory of the given length
 */
uint8_t *alloc_and_copy(jbyte *b, uint32_t size)
{
    uint8_t *buf = (uint8_t *)malloc(size);
    memcpy(buf, b, size);
    return buf;
}

/**
 * free result
 */
void release_result(const struct evm_result *result)
{
    free(result->reserved.context);
}

/**
 * evm_account_exists_fn
 */
int account_exists(struct evm_context* context,
                   const struct evm_address* address)
{
    jbyteArray addr = cb_env->NewByteArray(sizeof(evm_address));
    cb_env->SetByteArrayRegion(addr, 0, sizeof(evm_address), (const jbyte *)address->bytes);

    jboolean exists = cb_env->CallStaticBooleanMethod(cb_cls, cb_exists, addr);

    cb_env->DeleteLocalRef(addr);
    return exists;
}

/**
 * evm_get_balance_fn
 */
void get_balance(struct evm_word* result,
                 struct evm_context* context,
                 const struct evm_address* address)
{
    jbyteArray addr = cb_env->NewByteArray(sizeof(evm_address));
    cb_env->SetByteArrayRegion(addr, 0, sizeof(evm_address), (const jbyte *)address->bytes);

    jbyteArray balance = (jbyteArray)cb_env->CallStaticObjectMethod(cb_cls, cb_get_balance, addr);

    jbyte *balance_ptr = cb_env->GetByteArrayElements(balance, NULL);
    memcpy(result->bytes, balance_ptr, sizeof(evm_word));
    cb_env->ReleaseByteArrayElements(balance, balance_ptr, JNI_ABORT);

    cb_env->DeleteLocalRef(balance);
    cb_env->DeleteLocalRef(addr);
}


/**
 * evm_get_code_fn
 */
size_t get_code(const uint8_t** result_code,
                struct evm_context* context,
                const struct evm_address* address)
{
    jbyteArray addr = cb_env->NewByteArray(sizeof(evm_address));
    cb_env->SetByteArrayRegion(addr, 0, sizeof(evm_address), (const jbyte *)address->bytes);

    // code_buf are consumed immediately and only used for once.
    static uint8_t *code_buf = nullptr;
    if (code_buf) {
        free(code_buf);
        code_buf = nullptr;
    }

    jbyteArray code = (jbyteArray)cb_env->CallStaticObjectMethod(cb_cls, cb_get_code, addr);
    jsize code_size = cb_env->GetArrayLength(code);

    if (result_code) {
        jbyte *code_ptr = cb_env->GetByteArrayElements(code, NULL);
        code_buf = alloc_and_copy(code_ptr, code_size);
        cb_env->ReleaseByteArrayElements(code, code_ptr, JNI_ABORT);

        *result_code = code_buf;
    }

    cb_env->DeleteLocalRef(code);
    cb_env->DeleteLocalRef(addr);
    return code_size;
}

/**
 * evm_get_storage_fn
 */
void get_storage(struct evm_word* result,
                 struct evm_context* context,
                 const struct evm_address* address,
                 const struct evm_word* key)
{
    jbyteArray addr = cb_env->NewByteArray(sizeof(evm_address));
    cb_env->SetByteArrayRegion(addr, 0, sizeof(evm_address), (const jbyte *)address->bytes);
    jbyteArray k = cb_env->NewByteArray(sizeof(evm_word));
    cb_env->SetByteArrayRegion(k, 0, sizeof(evm_word), (const jbyte *)key->bytes);
    jbyteArray v = (jbyteArray)cb_env->CallStaticObjectMethod(cb_cls, cb_get_storage, addr, k);

    jbyte *v_ptr = cb_env->GetByteArrayElements(v, NULL);
    memcpy(result->bytes, v_ptr, sizeof(evm_word));
    cb_env->ReleaseByteArrayElements(v, v_ptr, JNI_ABORT);

    cb_env->DeleteLocalRef(addr);
    cb_env->DeleteLocalRef(k);
    cb_env->DeleteLocalRef(v);
}

/**
 * evm_set_storage_fn
 */
void set_storage(struct evm_context* context,
                 const struct evm_address* address,
                 const struct evm_word* key,
                 const struct evm_word* value)
{
    jbyteArray addr = cb_env->NewByteArray(sizeof(evm_address));
    cb_env->SetByteArrayRegion(addr, 0, sizeof(evm_address), (const jbyte *)address->bytes);
    jbyteArray k = cb_env->NewByteArray(sizeof(evm_word));
    cb_env->SetByteArrayRegion(k, 0, sizeof(evm_word), (const jbyte *)key->bytes);
    jbyteArray v = cb_env->NewByteArray(sizeof(evm_word));
    cb_env->SetByteArrayRegion(v, 0, sizeof(evm_word), (const jbyte *)value->bytes);

    cb_env->CallStaticVoidMethod(cb_cls, cb_put_storage, addr, k, v);

    cb_env->DeleteLocalRef(addr);
    cb_env->DeleteLocalRef(k);
    cb_env->DeleteLocalRef(v);
}

/**
 * evm_get_tx_context_fn
 */
void get_tx_context(struct evm_tx_context* result,
                    struct evm_context* context)
{
    memcpy(result, &ctx, sizeof(evm_tx_context));
}

/**
 * evm_get_block_hash_fn
 */
void get_block_hash(struct evm_hash* result,
                    struct evm_context* context,
                    int64_t number)
{
    jbyteArray block_hash = (jbyteArray)cb_env->CallStaticObjectMethod(cb_cls, cb_get_block_hash, number);

    jbyte *block_hash_ptr = cb_env->GetByteArrayElements(block_hash, NULL);
    memcpy(result->bytes, block_hash_ptr, sizeof(evm_hash));
    cb_env->ReleaseByteArrayElements(block_hash, block_hash_ptr, JNI_ABORT);

    cb_env->DeleteLocalRef(block_hash);
}

/**
 * evm_selfdestruct_fn
 */
void selfdestruct(struct evm_context* context,
                  const struct evm_address* address,
                  const struct evm_address* beneficiary)
{
    jbyteArray addr = cb_env->NewByteArray(sizeof(evm_address));
    cb_env->SetByteArrayRegion(addr, 0, sizeof(evm_address), (const jbyte *)address->bytes);
    jbyteArray bene = cb_env->NewByteArray(sizeof(evm_address));
    cb_env->SetByteArrayRegion(bene, 0, sizeof(evm_address), (const jbyte *)beneficiary->bytes);

    cb_env->CallStaticVoidMethod(cb_cls, cb_selfdestruct, addr, bene);

    cb_env->DeleteLocalRef(addr);
    cb_env->DeleteLocalRef(bene);
}

/**
 * evm_log_fn
 */
void log(struct evm_context* context,
         const struct evm_address* address,
         const uint8_t* data,
         size_t data_size,
         const struct evm_word topics[],
         size_t topics_count)
{
    jbyteArray addr = cb_env->NewByteArray(sizeof(evm_address));
    cb_env->SetByteArrayRegion(addr, 0, sizeof(evm_address), (const jbyte *)address->bytes);
    jbyteArray t = cb_env->NewByteArray(sizeof(evm_word) * topics_count);
    cb_env->SetByteArrayRegion(t, 0, sizeof(evm_word) * topics_count, (const jbyte *)topics[0].bytes);
    jbyteArray d = cb_env->NewByteArray(data_size);
    cb_env->SetByteArrayRegion(d, 0, data_size, (const jbyte *)data);

    cb_env->CallStaticVoidMethod(cb_cls, cb_log, addr, t, d);

    cb_env->DeleteLocalRef(addr);
    cb_env->DeleteLocalRef(t);
    cb_env->DeleteLocalRef(d);
}

/**
 * evm_call_fn
 */
void call(struct evm_result* result,
          struct evm_context* context,
          const struct evm_message* msg)
{
    jbyteArray m = encode_message(cb_env, msg);

    jbyteArray r = (jbyteArray)cb_env->CallStaticObjectMethod(cb_cls, cb_call, m);
    jbyte *r_ptr = cb_env->GetByteArrayElements(r, NULL);
    parse_result(result, r_ptr);
    cb_env->ReleaseByteArrayElements(r, r_ptr, JNI_ABORT);

    cb_env->DeleteLocalRef(m);
    cb_env->DeleteLocalRef(r);
}

/**
 * Reads an integer from the byte stream
 */
uint32_t read_int(jbyte *b) {
    uint32_t ret = 0;
    for (int i = 0; i < 4; i++) {
        ret = (ret << 8) + (b[i] & 0xff);
    }
    return ret;
}

/**
 * Reads a long integer from the byte stream
 */
uint64_t read_long(jbyte *b) {
    uint64_t ret = 0;
    for (int i = 0; i < 8; i++) {
        ret = (ret << 8) + (b[i] & 0xff);
    }
    return ret;
}

/**
 * Writes an integer to the byte array
 */
void write_int(jbyte *b, uint32_t num) {
    for (int i = 0; i < 4; i++) {
        b[3 - i] = (num >> (i * 8)) & 0xff;
    }
}

/**
 * Reads a long integer to the byte array
 */
void write_long(jbyte *b, uint64_t num) {
    for (int i = 0; i < 8; i++) {
        b[7 - i] = (num >> (i * 8)) & 0xff;
    }
}

/**
 * Parse the JNI execution context
 */
void parse_context(JNIEnv *env, jbyte *b, struct evm_message *msg, struct evm_tx_context *ctx)
{
    unsigned address_len = 32;
    unsigned offset = 0;
    memcpy(msg->address.bytes, b + offset, address_len); offset += address_len; // address
    memcpy(ctx->tx_origin.bytes, b + offset, address_len); offset += address_len; // origin
    memcpy(msg->caller.bytes, b + offset, address_len); offset += address_len; // caller

    offset += 16; // gas price
    msg->gas = read_long(b + offset); offset += 8; // gas limit

    memcpy(msg->value.bytes, b + offset, 16); offset += 16; // call value
    msg->input_size = read_int(b + offset); offset += 4;
    msg->input = (const unsigned char*)(b + offset); offset += msg->input_size; // call data

    msg->depth = read_int(b + offset); offset += 4; // depth
    msg->kind = static_cast<evm_call_kind>(read_int(b + offset)); offset += 4; // kind
    msg->flags = read_int(b + offset); offset += 4; // flags

    memcpy(ctx->block_coinbase.bytes, b + offset, address_len); offset += address_len; // block coinbase
    ctx->block_number = read_long(b + offset); offset += 8; // block number
    ctx->block_timestamp = read_long(b + offset); offset += 8; // block timestamp
    ctx->block_gas_limit = read_long(b + offset); offset += 8; // block gas limit
    memcpy(ctx->block_difficulty.bytes, b + offset, 16); offset += 16; // call value
}

/**
 * Encodes call message
 */
jbyteArray encode_message(JNIEnv *env, const struct evm_message *msg)
{
    unsigned address_len = 32;
    unsigned size = address_len + address_len + 8 + 16 + 4 + msg->input_size + 4 + 4 + 4;
    jbyte *buf = (jbyte *)malloc(size);

    unsigned offset = 0;
    memcpy(buf + offset, msg->address.bytes, address_len); offset += address_len; // address
    memcpy(buf + offset, msg->caller.bytes, address_len); offset += address_len; // caller
    write_long(buf + offset, msg->gas); offset += 8; // gas
    memcpy(buf + offset, msg->value.bytes, 16); offset += 16; // call value
    write_int(buf + offset, msg->input_size); offset += 4; // call data size
    memcpy(buf + offset, msg->input, msg->input_size); offset += msg->input_size; // call data
    write_int(buf + offset, msg->depth); offset += 4; // depth
    write_int(buf + offset, msg->kind); offset += 4; // kind
    write_int(buf + offset, msg->flags); offset += 4; // flags

    jbyteArray ret = env->NewByteArray(size);
    env->SetByteArrayRegion(ret, 0, size, buf);

    free(buf);
    return ret;
}

/**
 * Parse a result
 */
void parse_result(struct evm_result *result, jbyte *p)
{
    unsigned offset = 0;
    result->status_code = static_cast<evm_status_code>(read_int(p + offset)); offset += 4; // code
    result->gas_left = read_long(p + offset); offset += 8; // gas left
    result->output_size = read_int(p + offset); offset += 4; // output size
    uint8_t *buf = alloc_and_copy(p + offset, result->output_size); offset += result->output_size; // output
    result->output_data = buf;

    result->release = &release_result;
    result->reserved.context = buf;
}

/**
 * Encodes execution result.
 */
jbyteArray encode_result(JNIEnv *env, const struct evm_result *result)
{
    unsigned size = 4 + 8 + 4 + result->output_size;
    jbyte *buf = (jbyte *)malloc(size);

    unsigned offset = 0;
    write_int(buf + offset, result->status_code); offset += 4; // code
    write_long(buf + offset, result->gas_left); offset += 8; // gas left
    write_int(buf + offset, result->output_size); offset += 4; // output size
    memcpy(buf + offset, result->output_data, result->output_size); offset += result->output_size; // output

    jbyteArray ret = env->NewByteArray(size);
    env->SetByteArrayRegion(ret, 0, size, buf);

    free(buf);
    return ret;
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

struct evm_context vm_context = { &ctx_fn_table };

JNIEXPORT void JNICALL Java_org_aion_fastvm_FastVM_init
  (JNIEnv *env, jclass cls)
{
    cb_env = env;

    jclass cb_cls_local = env->FindClass("org/aion/fastvm/Callback");
    cb_cls = (jclass) env->NewGlobalRef(cb_cls_local);

    cb_get_block_hash = env->GetStaticMethodID(cb_cls, "getBlockHash", "(J)[B");
    cb_get_code = env->GetStaticMethodID(cb_cls, "getCode", "([B)[B");
    cb_get_balance = env->GetStaticMethodID(cb_cls, "getBalance", "([B)[B");
    cb_exists = env->GetStaticMethodID(cb_cls, "exists", "([B)Z");
    cb_get_storage = env->GetStaticMethodID(cb_cls, "getStorage", "([B[B)[B");
    cb_put_storage = env->GetStaticMethodID(cb_cls, "putStorage", "([B[B[B)V");
    cb_selfdestruct = env->GetStaticMethodID(cb_cls, "selfDestruct", "([B[B)V");
    cb_log = env->GetStaticMethodID(cb_cls, "log", "([B[B[B)V");
    cb_call = env->GetStaticMethodID(cb_cls, "call", "([B)[B");

    env->DeleteLocalRef(cb_cls_local);
}

JNIEXPORT jlong JNICALL Java_org_aion_fastvm_FastVM_create
  (JNIEnv *env, jclass cls)
{
    cb_env = env;

    struct evm_instance *instance = evmjit_create();;
    return (jlong)instance;
}

JNIEXPORT jbyteArray JNICALL Java_org_aion_fastvm_FastVM_run
  (JNIEnv *env, jclass cls, jlong instance, jbyteArray code, jbyteArray context, jint revision)
{
    cb_env = env;

    struct evm_instance *inst = (struct evm_instance *)instance;
    jbyte *code_ptr = (jbyte *)env->GetByteArrayElements(code, NULL);
    jsize code_size = env->GetArrayLength(code);

    // parse execution context and compute code hash
    struct evm_message msg;
    jbyte *context_ptr = (jbyte *)env->GetByteArrayElements(context, NULL);
    parse_context(env, context_ptr, &msg, &ctx);
    dev::evmjit::keccak((const uint8_t*) code_ptr, code_size, msg.code_hash.bytes);

    // execute
    struct evm_result result = inst->execute(inst, &vm_context, static_cast<evm_revision>(revision), &msg,
            (uint8_t *)code_ptr, code_size);

    // encode execution result
    jbyteArray ret = encode_result(env, &result);

    // release
    if (result.release) {
        result.release(&result);
    }

    env->ReleaseByteArrayElements(context, context_ptr, JNI_ABORT);
    env->ReleaseByteArrayElements(code, code_ptr, 0 );
    return ret;
}

JNIEXPORT void JNICALL Java_org_aion_fastvm_FastVM_destroy
  (JNIEnv *env, jclass cls, jlong handler)
{
    cb_env = env;

    struct evm_instance *instance = (struct evm_instance *)handler;
    instance->destroy(instance);
}
