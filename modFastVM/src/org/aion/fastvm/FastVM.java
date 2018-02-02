package org.aion.fastvm;

import org.aion.base.db.IRepositoryCache;
import org.aion.base.util.NativeLoader;
import org.aion.core.AccountState;
import org.aion.db.IBlockStoreBase;
import org.aion.vm.ExecutionContext;
import org.aion.vm.ExecutionResult;
import org.aion.vm.VirtualMachine;
import org.aion.vm.types.DataWord;
import org.apache.commons.lang3.tuple.Pair;

/**
 * The FastVM implementation. It calls into the jit library via JNI.
 *
 * @author yulong
 */
public class FastVM implements VirtualMachine {

    public static int REVISION_FRONTIER = 0;
    public static int REVISION_HOMESTEAD = 1;
    public static int REVISION_TANGERINE_WHISTLE = 2;
    public static int REVISION_SPURIOUS_DRAGON = 3;
    public static int REVISION_BYZANTIUM = 4;
    public static int REVISION_AION = 5;

    public static int FLAG_STATIC = 1;

    static {
        NativeLoader.loadLibrary("fastvm");
        init();
    }

    /**
     * Creates a FastVM instance.
     */
    public FastVM() {
    }

    @Override
    public ExecutionResult run(byte[] code, ExecutionContext ctx,
                               IRepositoryCache<AccountState, DataWord, IBlockStoreBase<?, ?>> repo) {
        Callback.push(Pair.of(ctx, repo));
        long instance = create();
        byte[] result = run(instance, code, ctx.toBytes(), REVISION_AION);
        destroy(instance);
        Callback.pop();

        return ExecutionResult.parse(result);
    }

    /**
     * Initializes library. One time
     */
    private native static void init();

    /**
     * Creates a new VM instance.
     *
     * @return instance
     */
    private native static long create();

    /**
     * Executes the given code and returns the execution results.
     *
     * @param instance
     * @param code
     * @param context
     * @param revision
     * @return
     */
    private native static byte[] run(long instance, byte[] code, byte[] context, int revision);

    /**
     * Destroys the given VM instance.
     *
     * @param instance
     */
    private native static void destroy(long instance);
}
