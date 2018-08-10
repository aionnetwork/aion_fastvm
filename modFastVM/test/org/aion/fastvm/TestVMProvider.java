package org.aion.fastvm;

import org.aion.base.db.IRepositoryCache;
import org.aion.precompiled.ContractFactory;
import org.aion.vm.ExecutionContext;
import org.aion.vm.ExecutorProvider;
import org.aion.vm.IPrecompiledContract;
import org.aion.vm.VirtualMachine;

public class TestVMProvider implements ExecutorProvider {
    private ContractFactory factory = new ContractFactory();

    @Override
    public IPrecompiledContract getPrecompiledContract(ExecutionContext context, IRepositoryCache track) {
        return factory.fetchPrecompiledContract(context, track);
    }

    @Override
    public VirtualMachine getVM() {
        return new FastVM();
    }

    public void setFactory(ContractFactory factory) {
        this.factory = factory;
    }
}
