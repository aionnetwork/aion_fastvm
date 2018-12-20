pragma solidity ^0.4.15;

contract Owned {
    address public owner;
    address public newOwner;
    /**
     * Events
     */
    event ChangedOwner(address indexed new_owner);
    /**
     * Functionality
     */
    function Owned() {
        owner = msg.sender;
    }
    modifier onlyOwner() {
        require(msg.sender == owner);
        _;
    }
    function changeOwner(address _newOwner) onlyOwner external {
        newOwner = _newOwner;
    }
    function acceptOwnership() external {
        if (msg.sender == newOwner) {
            owner = newOwner;
            newOwner = 0x0;
            ChangedOwner(owner);
        }
    }
}

contract IOwned {
    function owner() returns (address);
    function changeOwner(address);
    function acceptOwnership();
}
/**
 * Savings is a contract that releases Tokens on a predefined
 * schedule, and allocates bonus tokens upon withdrawal on a
 * proportional basis, determined by the ratio of deposited tokens
 * to total owned tokens.
 *
 * The distribution schedule consists of a monthly withdrawal schedule
 * responsible for distribution 75% of the total savings, and a
 * one-off withdrawal event available before or at the start of the
 * withdrawal schedule, distributing 25% of the total savings.
 *
 * To be exact, upon contract deployment there may be a period of time in which
 * only the one-off withdrawal event is available, define this period of time as:
 * [timestamp(start), timestamp(startBlockTimestamp)),
 *
 * Then the periodic withdrawal range is defined as:
 * [timestamp(startBlockTimestamp), +inf)
 *
 * DO NOT SEND TOKENS TO THIS CONTRACT. Use the deposit() or depositTo() method.
 * As an exception, tokens transferred to this contract before locking are the
 * bonus tokens that are distributed.
 */
contract Savings is Owned {
    /**
     * Periods is the total monthly withdrawable amount, not counting the
     * special withdrawal.
     */
    uint public periods;
    /**
     * t0special is an additional multiplier that determines what
     * fraction of the total distribution is distributed in the
     * one-off withdrawal event. It is used in conjunction with
     * a periodic multiplier (p) to determine the total savings withdrawable
     * to the user at that point in time.
     *
     * The value is not set, it is calculated based on periods
     */
    uint public t0special;
    /**
     * This is set low to accomodate for tests, in the final version of the
     * contract, this will be set to the correct amount (1 month)
     *
     */
    uint constant public intervalSecs = 30 days;
    uint constant public precision = 10 ** 12;
    /**
     * Events
     */
    event Withdraws(address indexed who, uint amount);
    event Deposit(address indexed who, uint amount);
    event Mint(address indexed who, uint amount);
    bool public inited;
    bool public locked;
    uint public startBlockTimestamp = 0;

    // face value deposited by an address before locking
    mapping (address => uint) public deposited;
    // total face value deposited; sum of deposited
    uint public totalfv;
    // the total remaining value
    uint public remainder;
    /**
     * Total tokens owned by the contract after locking, and possibly
     * updated by the foundation after subsequent sales.
     */
    uint public total;
    // the total value withdrawn
    mapping (address => uint128) public withdrawn;
    bool public nullified;
    modifier notNullified() { require(!nullified); _; }
    modifier preLock() { require(!locked && startBlockTimestamp == 0); _; }
    /**
     * Lock called, deposits no longer available.
     */
    modifier postLock() { require(locked); _; }
    /**
     * Prestart, state is after lock, before start
     */
    modifier preStart() { require(locked && startBlockTimestamp == 0); _; }
    /**
     * Start called, the savings contract is now finalized, and withdrawals
     * are now permitted.
     */
    modifier postStart() { require(locked && startBlockTimestamp != 0); _; }
    /**
     * Uninitialized state, before init is called. Mainly used as a guard to
     * finalize periods and t0special.
     */
    modifier notInitialized() { require(!inited); _; }
    /**
     * Post initialization state, mainly used to guarantee that
     * periods and t0special have been set properly before starting
     * the withdrawal process.
     */
    modifier initialized() { require(inited); _; }
    /**
     * Revert under all conditions for fallback, cheaper mistakes
     * in the future?
     */
     /**
    function() {
        revert();       COMMENTED OUT SO WE CAN HAVE A PAYABLE FUNCTION
    }
    */
    /**
    * Changed so that we have a payable default function so we can send coins to contract.
    * VERIFY THIS CHANGE.
    */
    function () payable {}

    /**
     * Initialization function, should be called after contract deployment. The
     * addition of this function allows contract compilation to be simplified
     * to one contract, instead of two.
     *
     * periods and t0special are finalized, and effectively invariant, after
     * init is called for the first time.
     */
    function init(uint _periods, uint _t0special) onlyOwner notInitialized {
        require(_periods != 0);
        periods = _periods;
        t0special = _t0special;
    }

    function finalizeInit() onlyOwner notInitialized {
        inited = true;
    }

    /**
     * Lock is called by the owner to lock the savings contract
     * so that no more deposits may be made.
     */
    function lock() onlyOwner {
        locked = true;
    }
    /**
     * Starts the distribution of savings, it should be called
     * after lock(), once all of the bonus tokens are send to this contract,
     * and multiMint has been called.
     */
    function start(uint _startBlockTimestamp) onlyOwner initialized preStart {
        startBlockTimestamp = _startBlockTimestamp;
        uint128 tokenBalance = this.balance;
        total = tokenBalance;
        totalfv = tokenBalance;
        remainder = tokenBalance;
    }

    /**
     * Check withdrawal is live, useful for checking whether
     * the savings contract is "live", withdrawal enabled, started.
     */
    function isStarted() constant returns(bool) {
        return locked && startBlockTimestamp != 0;
    }

    // if someone accidentally transfers tokens to this contract,
    // the owner can return them as long as distribution hasn't started
    /**
     * Used to refund users who accidentaly transferred tokens to this
     * contract, only available before contract is locked
     */
    function refund(address addr, uint amount) onlyOwner preLock {
        addr.transfer(amount);
    }

    /**
     * Calculates the monthly period, starting after the startBlockTimestamp,
     * periodAt will return 0 for all timestamps before startBlockTimestamp.
     *
     * Therefore period 0 is the range of time in which we have called start(),
     * but have not yet passed startBlockTimestamp. Period 1 is the
     * first monthly period, and so-forth all the way until the last
     * period == periods.
     *
     * NOTE: not guarded since no state modifications are made. However,
     * it will return invalid data before the postStart state. It is
     * up to the user to manually check that the contract is in
     * postStart state.
     */
    function periodAt(uint _blockTimestamp) constant returns(uint) {
        /**
         * Lower bound, consider period 0 to be the time between
         * start() and startBlockTimestamp
         */
        if (startBlockTimestamp > _blockTimestamp)
            return 0;
        /**
         * Calculate the appropriate period, and set an upper bound of
         * periods - 1.
         */
        uint p = ((_blockTimestamp - startBlockTimestamp) / intervalSecs) + 1;
        if (p > periods)
            p = periods;
        return p;
    }
    // what withdrawal period are we in?
    // returns the period number from [0, periods)
    function period() constant returns(uint) {
        return periodAt(block.timestamp);
    }
    function mint(address account, uint amount) onlyOwner preLock notNullified {
        deposited[account] = amount;
        Mint(account, amount);
    }
    // REMOVE THIS: this function only exists so that we can verify in testing that mint works
    function checkBalance(address account) returns(uint) {
        return deposited[account];
    }
    // deposit your tokens to be saved
    //
    // the despositor must have approve()'d the tokens
    // to be transferred by this contract
    function deposit(uint tokens) onlyOwner notNullified {
        depositTo(msg.sender, tokens);
    }

    function depositTo(address beneficiary, uint tokens) onlyOwner preLock notNullified {
        //require(token.transferFrom(msg.sender, this, tokens)); COMMENTED OUT FOR EASE OF TESTING
        deposited[beneficiary] += tokens;
        totalfv += tokens;
        Deposit(beneficiary, tokens);
    }

    // withdraw withdraws tokens to the sender
    // withdraw can be called at most once per redemption period
    function withdraw() notNullified returns(bool) {
        return withdrawTo(msg.sender);
    }
    /**
     * Calculates the fraction of total (one-off + monthly) withdrawable
     * given the current timestamp. No guards due to function being constant.
     * Will output invalid data until the postStart state. It is up to the user
     * to manually confirm contract is in postStart state.
     */
    function availableForWithdrawalAt(uint128 blockTimestamp) constant returns (uint128) {

        return ((t0special + periodAt(blockTimestamp)) * precision) / (t0special + periods);
    }
    /**
     * Business logic of _withdrawTo, the code is separated this way mainly for
     * testing. We can inject and test parameters freely without worrying about the
     * blockchain model.
     *
     * NOTE: Since function is constant, no guards are applied. This function will give
     * invalid outputs unless in postStart state. It is up to user to manually check
     * that the correct state is given (isStart() == true)
     */
    function _withdrawTo(uint _deposit, uint _withdrawn, uint _blockTimestamp) constant returns (uint) {
        uint128 fraction = availableForWithdrawalAt(_blockTimestamp);

        uint128 withdrawable = (_deposit * fraction) / precision;
        // check that we can withdraw something
        if (withdrawable > _withdrawn) {
            return withdrawable - _withdrawn;
        }
        return 0;
    }
    /**
     * Public facing withdrawTo, injects business logic with
     * the correct model.
     */
    function withdrawTo(address addr) postStart notNullified returns (bool) {
        uint _d = deposited[addr];
        uint _w = withdrawn[addr];
        uint diff = _withdrawTo(_d, _w, block.timestamp);
        // no withdrawal could be made
        if (diff == 0) {
            return false;
        }
        // check that we cannot withdraw more than max
        require((diff + _w) <= _d);
        // transfer and increment
        addr.transfer(diff);
        withdrawn[addr] += diff;
        remainder -= diff;
        Withdraws(addr, diff);
        return true;
    }

    // force withdrawal to many addresses
    function bulkWithdraw(address[] addrs) notNullified {
        for (uint i=0; i<addrs.length; i++)
            withdrawTo(addrs[i]);
    }
}