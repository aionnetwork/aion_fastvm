module aion.fastvm {
    requires aion.base;
    requires aion.mcf;
    requires slf4j.api;
    requires aion.crypto;
    requires aion.zero;
    requires aion.vm.api;
    requires aion.precompiled;
    requires commons.lang3;
    requires commons.collections4;
    requires aion.util;

    exports org.aion.solidity;
    exports org.aion.fastvm;
}
