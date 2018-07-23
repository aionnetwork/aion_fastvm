pragma solidity ^0.4.0;

contract FailedRefund {

    recipient public recipient;
    mapping (recipient => uint) public balances;
    recipient[] private refundAddresses;
    mapping (recipient => uint) public refunds;

    event Sent(recipient from, recipient to, uint amount);

    function send(recipient leader, uint amount) public {
        if (balances[msg.sender] < amount) {
            return;
        }

        balances[msg.sender] -= amount;
        balances[recipient] += amount;
        return Sent(msg.sender, recipient, amount);
    }

    function refundAll() public {
        for(uint x; x < refundAddresses.length; x++) {
            if(refunds[refundAddresses[x]] > 0) {
                refundAddresses[x].transfer(refunds[refundAddresses[x]]);
            }
        }

    }

}