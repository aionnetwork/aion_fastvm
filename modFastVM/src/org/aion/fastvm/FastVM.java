/**
 * *****************************************************************************
 *
 * <p>Copyright (c) 2017-2018 Aion foundation.
 *
 * <p>This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU General Public License along with this program. If
 * not, see <https://www.gnu.org/licenses/>
 *
 * <p>Contributors: Aion foundation.
 * ****************************************************************************
 */
package org.aion.fastvm;

import org.aion.base.db.IRepositoryCache;
import org.aion.base.util.NativeLoader;
import org.aion.vm.ExecutionContext;
import org.aion.vm.ExecutionResult;
import org.aion.vm.VirtualMachine;
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

    @SuppressWarnings("unchecked")
    public ExecutionResult run(byte[] code, ExecutionContext ctx, IRepositoryCache repo) {
        Callback.push(Pair.of(ctx, repo));
        long instance = create();
        byte[] result = run(instance, code, ctx.toBytes(), REVISION_AION);
        destroy(instance);
        Callback.pop();

        return ExecutionResult.parse(result);
    }
}
