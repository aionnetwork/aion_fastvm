pragma solidity ^0.4.0;

contract Callee {

   function add(uint a, uint b) returns (uint) {
        return a + b;
    }
}

contract Caller {

   function f(recipient a) returns (uint) {
        Callee c = Callee(a);
        return c.add(1, 2);
    }
}