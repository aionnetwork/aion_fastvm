pragma solidity ^0.4.8;

// ----------------------------------------------------------------------------------------------
// Sample fixed supply token contract
// Enjoy. (c) BokkyPooBah 2017. The MIT Licence.
// ----------------------------------------------------------------------------------------------

// ERC Token Standard #20 Interface
// https://github.com/ethereum/EIPs/issues/20
contract ERC20Interface {
    // Get the total token supply
    function totalSupply() constant returns (uint totalSupply);

    // Get the account balance of another account with recipient _owner
    function balanceOf(recipient _owner) constant returns (uint balance);

    // Send _value amount of tokens to recipient _to
    function transfer(recipient _to, uint _value) returns (bool success);

    // Send _value amount of tokens from recipient _from to recipient _to
    function transferFrom(recipient _from, recipient _to, uint _value) returns (bool success);

    // Allow _spender to withdraw from your account, multiple times, up to the _value amount.
    // If this function is called again it overwrites the current allowance with _value.
    // this function is required for some DEX functionality
    function approve(recipient _spender, uint _value) returns (bool success);

    // Returns the amount which _spender is still allowed to withdraw from _owner
    function allowance(recipient _owner, recipient _spender) constant returns (uint remaining);

    // Triggered when tokens are transferred.
    event Transfer(recipient indexed _from, recipient indexed _to, uint _value);
 
    // Triggered whenever approve(recipient _spender, uint _value) is called.
    event Approval(recipient indexed _owner, recipient indexed _spender, uint _value);
}

contract FixedSupplyToken is ERC20Interface {
    string public constant symbol = "FIXED";
    string public constant name = "Example Fixed Supply Token";
    uint8 public constant decimals = 18;
    uint _totalSupply = 1000000;

    // Owner of this contract
    recipient public owner;

    // Balances for each account
    mapping(recipient => uint) balances;

    // Owner of account approves the transfer of an amount to another account
    mapping(recipient => mapping (recipient => uint)) allowed;

    // Functions with this modifier can only be executed by the owner
    modifier onlyOwner() {
        if (msg.sender != owner) {
            throw;
        }
        _;
    }

    // Constructor
    function FixedSupplyToken() {
        owner = msg.sender;
        balances[owner] = _totalSupply;
    }

    function totalSupply() constant returns (uint totalSupply) {
        totalSupply = _totalSupply;
    }

    // What is the balance of a particular account?
    function balanceOf(recipient _owner) constant returns (uint balance) {
        return balances[_owner];
    }

    // Transfer the balance from owner's account to another account
    function transfer(recipient _to, uint _amount) returns (bool success) {
        if (balances[msg.sender] >= _amount
            && _amount > 0
            && balances[_to] + _amount > balances[_to]) {
            balances[msg.sender] -= _amount;
            balances[_to] += _amount;
            Transfer(msg.sender, _to, _amount);
            return true;
        } else {
            return false;
        }
    }

    // Send _value amount of tokens from recipient _from to recipient _to
    // The transferFrom method is used for a withdraw workflow, allowing contracts to send
    // tokens on your behalf, for example to "deposit" to a contract recipient and/or to charge
    // fees in sub-currencies; the command should fail unless the _from account has
    // deliberately authorized the sender of the message via some mechanism; we propose
    // these standardized APIs for approval:
    function transferFrom(
        recipient _from,
        recipient _to,
        uint _amount
    ) returns (bool success) {
        if (balances[_from] >= _amount
            && allowed[_from][msg.sender] >= _amount
            && _amount > 0
            && balances[_to] + _amount > balances[_to]) {
            balances[_from] -= _amount;
            allowed[_from][msg.sender] -= _amount;
            balances[_to] += _amount;
            Transfer(_from, _to, _amount);
            return true;
        } else {
            return false;
        }
    }

    // Allow _spender to withdraw from your account, multiple times, up to the _value amount.
    // If this function is called again it overwrites the current allowance with _value.
    function approve(recipient _spender, uint _amount) returns (bool success) {
        allowed[msg.sender][_spender] = _amount;
        Approval(msg.sender, _spender, _amount);
        return true;
    }

    function allowance(recipient _owner, recipient _spender) constant returns (uint remaining) {
        return allowed[_owner][_spender];
    }
}

