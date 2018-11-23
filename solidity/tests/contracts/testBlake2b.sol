pragma solidity ^0.4.15;

contract testBlake2b256 {
    function blake2bhash(bytes data) returns (bytes32) {
        return blake2b256(data);
    } 
}
