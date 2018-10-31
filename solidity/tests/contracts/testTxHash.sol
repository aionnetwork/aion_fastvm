pragma solidity ^0.4.15;

contract testTxHash {
    function txHash() returns (bytes32) {
        return transactionhash();
    }
}

