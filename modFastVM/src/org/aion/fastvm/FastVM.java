package org.aion.fastvm;

import org.aion.util.FvmNativeLoader;
import org.apache.commons.lang3.tuple.Pair;

/**
 * The FastVM implementation. It calls into the jit library via JNI.
 *
 * @author yulong
 */
public class FastVM implements IFastVm {

    public static int REVISION_FRONTIER = 0;
    public static int REVISION_HOMESTEAD = 1;
    public static int REVISION_TANGERINE_WHISTLE = 2;
    public static int REVISION_SPURIOUS_DRAGON = 3;
    public static int REVISION_BYZANTIUM = 4;
    public static int REVISION_AION = 5;
    public static int REVISION_AION_V1 = 7;

    public static int FLAG_STATIC = 1;

    static {
        FvmNativeLoader.loadLibrary();
        init();
    }

    /** Creates a FastVM instance. */
    public FastVM() {}

    /** Initializes library. One time */
    private static native void init();

    /**
     * Creates a new VM instance.
     *
     * @return instance
     */
    private static native long create();

    /** Executes the given code and returns the execution results. */
    private static native byte[] run(long instance, byte[] code, byte[] context, int revision);

    /** Destroys the given VM instance. */
    private static native void destroy(long instance);

    /**
     * Run the specified code in the given context.
     *
     * @implNote Since all contracts can possibly interact with the static state of the {@link
     *     Callback} class, this method is synchronized.
     * @param code The code to run.
     * @param ctx The execution context.
     * @param externalState The current state of the world.
     * @return the execution result.
     */
    @SuppressWarnings("unchecked")
    @Override
    public FastVmTransactionResult runPre040Fork(
            byte[] code, ExecutionContext ctx, IExternalStateForFvm externalState) {

        Callback.push(Pair.of(ctx, externalState));
        long instance = create();
        byte[] result = run(instance, code, ctx.toBytes(), REVISION_AION);
        destroy(instance);
        Callback.pop();

        return FastVmTransactionResult.fromBytes(result);
    }

    /**
     * Run the specified code in the given context.
     *
     * <p>This is the new Fvm execution point as of the 0.4.0 fork.
     *
     * @implNote Since all contracts can possibly interact with the static state of the {@link
     *     Callback} class, this method is synchronized.
     * @param code The code to run.
     * @param ctx The execution context.
     * @param externalState The current state of the world.
     * @return the execution result.
     */
    @SuppressWarnings("unchecked")
    @Override
    public FastVmTransactionResult runPost040Fork(
            byte[] code, ExecutionContext ctx, IExternalStateForFvm externalState) {

        Callback.push(Pair.of(ctx, externalState));
        long instance = create();
        byte[] result = run(instance, code, ctx.toBytes(), REVISION_AION_V1);
        destroy(instance);
        Callback.pop();

        return FastVmTransactionResult.fromBytes(result);
    }
}
