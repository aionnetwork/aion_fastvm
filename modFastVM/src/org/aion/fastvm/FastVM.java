/*******************************************************************************
 *
 * Copyright (c) 2017-2018 Aion foundation.
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>
 *
 * Contributors:
 *     Aion foundation.
 ******************************************************************************/
package org.aion.fastvm;

import org.aion.base.db.IRepositoryCache;
import org.aion.base.util.NativeLoader;
import org.aion.vm.VirtualMachine;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.vm.ExecutionContext;
import org.aion.vm.ExecutionResult;
import org.aion.mcf.vm.types.DataWord;
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
