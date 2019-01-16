pragma solidity ^0.4.15;

import "Ticker.sol";
import "Wallet.sol";

contract Suicide {
    function f(address addr) {
        suicide(addr);
    }
}
