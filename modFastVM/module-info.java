module aion.fastvm {
    requires aion.base;
    requires aion.mcf;
    requires slf4j.api;
    requires aion.crypto;
    requires aion.zero;
    requires aion.precompiled;

    exports org.aion.vm;
    exports org.aion.solidity;
}
