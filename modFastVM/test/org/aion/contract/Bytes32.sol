pragma solidity ^0.4.0;

contract Test {

    bytes32[] a;

    function f() returns(bytes32) {
        a.push(0x1122334455667788112233445566778811223344556677881122334455667788);
        return a[0];
    }

    function g(bytes32[] b) returns(bytes32[]) {
        a = b;
        return a;
    }
}
