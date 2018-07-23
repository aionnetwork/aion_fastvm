package org.aion.fastvm;

import org.aion.base.db.IRepositoryCache;
import org.aion.precompiled.ContractFactory;
import org.aion.vm.ExecutionContext;
import org.aion.vm.ExecutorProvider;
import org.aion.vm.IPrecompiledContract;
import org.aion.vm.VirtualMachine;

public class TestVMProvider implements ExecutorProvider {
    @Override
    public IPrecompiledContract getPrecompiledContract(ExecutionContext context, IRepositoryCache track) {
        return ContractFactory.getPrecompiledContract(context, track);
    }

    @Override
    public VirtualMachine getVM() {
        return new FastVM();
    }
}
