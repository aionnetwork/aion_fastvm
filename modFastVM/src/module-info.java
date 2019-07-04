module aion.fastvm {
    requires aion.mcf;
    requires slf4j.api;
    requires aion.crypto;
    requires aion.zero;
    requires aion.precompiled;
    requires commons.lang3;
    requires commons.collections4;
    requires aion.util;
    requires org.json;
    requires aion.types;
    requires aion.base;

    exports org.aion.solidity;
    exports org.aion.fastvm;
}
