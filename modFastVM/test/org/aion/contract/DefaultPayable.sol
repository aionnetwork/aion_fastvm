pragma solidity ^0.4.0;

contract DefaultPayable {
   function () public payable {}

   function nonPayable() public {}
}
