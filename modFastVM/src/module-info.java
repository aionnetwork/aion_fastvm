module aion.fastvm {
    requires slf4j.api;
    requires commons.lang3;
    requires commons.collections4;
    requires org.json;
    requires aion.types;

    exports org.aion.solidity;
    exports org.aion.fastvm;
}
