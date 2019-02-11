module aion.fastvm {
    requires aion.type.api;
    requires aion.mcf;
    requires slf4j.api;
    requires aion.crypto;
    requires aion.zero;
    requires aion.vm.api;
    requires aion.precompiled;
    requires commons.lang3;
    requires commons.collections4;
    requires aion.util;
    requires org.json;

    exports org.aion.solidity;
    exports org.aion.fastvm;
}
