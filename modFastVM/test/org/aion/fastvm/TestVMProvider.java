/*
 * Copyright (c) 2017-2018 Aion foundation.
 *
 *     This file is part of the aion network project.
 *
 *     The aion network project is free software: you can redistribute it
 *     and/or modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation, either version 3 of
 *     the License, or any later version.
 *
 *     The aion network project is distributed in the hope that it will
 *     be useful, but WITHOUT ANY WARRANTY; without even the implied
 *     warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *     See the GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with the aion network project source files.
 *     If not, see <https://www.gnu.org/licenses/>.
 *
 * Contributors:
 *     Aion foundation.
 */

package org.aion.fastvm;

import org.aion.precompiled.ContractFactory;
import org.aion.vm.ExecutorProvider;
import org.aion.vm.IContractFactory;
import org.aion.vm.IPrecompiledContract;
import org.aion.vm.KernelInterfaceForFastVM;
import org.aion.base.vm.VirtualMachine;
import org.aion.vm.api.interfaces.TransactionContext;

public class TestVMProvider implements ExecutorProvider {
    private IContractFactory factory = new ContractFactory();

    @Override
    public IPrecompiledContract getPrecompiledContract(
            TransactionContext context, KernelInterfaceForFastVM track) {
        return factory.getPrecompiledContract(context, track);
    }

    @Override
    public VirtualMachine getVM() {
        return new FastVM();
    }

    public void setFactory(IContractFactory factory) {
        this.factory = factory;
    }
}
