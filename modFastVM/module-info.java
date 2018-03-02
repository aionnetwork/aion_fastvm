module aion.fastvm {
    requires aion.base;
    requires aion.mcf;
    requires aion.log;
    requires slf4j.api;
    requires aion.crypto;
    requires aion.zero;

    exports org.aion.vm;
    exports org.aion.solidity;
}