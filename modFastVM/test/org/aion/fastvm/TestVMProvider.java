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
