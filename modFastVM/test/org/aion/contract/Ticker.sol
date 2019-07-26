pragma solidity ^0.4.0;

contract Ticker {
    
   uint128 ticks;

   function ticking() payable {
        ticks++;
    }

   function getTicker() payable returns (uint128){
        return ticks;
    }
}
