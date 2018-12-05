pragma solidity ^0.4.0;

contract Test {

    bytes32[] a;

    function f() returns(bytes32) {
        a.push(0x0011223344556677889900112233445566778899001122334455667788990011);
        return a[0];
    }

    function g(bytes32[] b) returns(bytes32[]) {
        a = b;
        return a;
    }
}
