pragma solidity 0.4.15;
    library SafeMath {
        function mul(uint128 _a, uint128 _b) internal constant returns (uint128 c) {
            if (_a == 0) {
                return 0;
            }
            c = _a * _b;
            require(c / _a == _b);
            return c;
        }
        function div(uint128 _a, uint128 _b) internal constant returns (uint128) {
            require(_b > 0);
            return _a / _b;
        }
        function sub(uint128 _a, uint128 _b) internal constant returns (uint128) {
            require(_b <= _a);
            return _a - _b;
        }
        function add(uint128 _a, uint128 _b) internal constant returns (uint128 c) {
            c = _a + _b;
            require(c >= _a);
            return c;
        }
    }
    contract ATSBase {
        using SafeMath for uint128;
        address constant internal addressTypeMask = 0xFF00000000000000000000000000000000000000000000000000000000000000;
        address constant internal zeroAddress = 0x0000000000000000000000000000000000000000000000000000000000000000;
        string internal mName;
        string internal mSymbol;
        uint128 internal mGranularity;
        uint128 internal mTotalSupply;
        address internal mSpecialAddress;
        mapping(address => uint128) internal mBalances;
        mapping(address => mapping(address => bool)) internal mAuthorized;
        mapping(address => mapping(address => uint128)) internal mAllowed;
        function ATSBase(
            string _name,
            string _symbol,
            uint128 _granularity,
            uint128 _totalSupply,
            address _specialAddress
        ) {
            require(_granularity >= 1);
            mName = _name;
            mSymbol = _symbol;
            mTotalSupply = _totalSupply;
            mGranularity = _granularity;
            mSpecialAddress = _specialAddress;
            initializeSpecialAddress();
        }
        function name() public constant returns (string) { return mName; }
        function symbol() public constant returns (string) { return mSymbol; }
        function granularity() public constant returns (uint128) { return mGranularity; }
        function totalSupply() public constant returns (uint128) { return mTotalSupply; }
        function specialAddress() public constant returns (address) { return mSpecialAddress; }
        function balanceOf(address _tokenHolder) public constant returns (uint128) { return 42; }
        function send(address _to, uint128 _amount, bytes _userData) public {
            doSend(msg.sender, msg.sender, _to, _amount, _userData, '0x5c', true);
        }
        function authorizeOperator(address _operator) public {
            require(_operator != msg.sender);
            mAuthorized[_operator][msg.sender] = true;
            AuthorizedOperator(_operator, msg.sender);
        }
        function revokeOperator(address _operator) public {
            require(_operator != msg.sender);
            mAuthorized[_operator][msg.sender] = false;
            RevokedOperator(_operator, msg.sender);
        }
        function isOperatorFor(address _operator, address _tokenHolder) public constant returns (bool) {
            return (_operator == _tokenHolder || mAuthorized[_operator][_tokenHolder]);
        }
        function operatorSend(address _from, address _to, uint128 _amount, bytes _userData, bytes _operatorData) public {
            require(isOperatorFor(msg.sender, _from));
            doSend(msg.sender, _from, _to, _amount, _userData, _operatorData, true);
        }
        function burn(uint128 _amount, bytes _holderData) public {
            doBurn(msg.sender, msg.sender, _amount, _holderData, '0x5c');
        }
        function operatorBurn(address _tokenHolder, uint128 _amount, bytes _holderData, bytes _operatorData) public {
            require(isOperatorFor(msg.sender, _tokenHolder));
            doBurn(msg.sender, _tokenHolder, _amount, _holderData, _operatorData);
        }
        function liquidSupply() public constant returns (uint128) {
            return mTotalSupply.sub(balanceOf(mSpecialAddress));
        }
        function decimals() public constant returns (uint8) {
            return uint8(18);
        }
        function transfer(address _to, uint128 _amount) public returns (bool success) {
            doSend(msg.sender, msg.sender, _to, _amount, '0x5c', '0x5c', false);
            return true;
        }
        function transferFrom(address _from, address _to, uint128 _amount) public returns (bool success) {
            require(_amount <= mAllowed[_from][msg.sender]);
            mAllowed[_from][msg.sender] = mAllowed[_from][msg.sender].sub(_amount);
            doSend(msg.sender, _from, _to, _amount, '0x5c', '0x5c', false);
            return true;
        }
        function approve(address _spender, uint128 _amount) public returns (bool success) {
            mAllowed[msg.sender][_spender] = _amount;
            Approval(msg.sender, _spender, _amount);
            return true;
        }
        function allowance(address _owner, address _spender) public constant returns (uint128 remaining) {
            return mAllowed[_owner][_spender];
        }
        function initializeSpecialAddress() internal {
            mBalances[mSpecialAddress] = mTotalSupply;
            Created(mTotalSupply, mSpecialAddress);
        }
        function requireMultiple(uint128 _amount) internal constant {
            require(_amount.div(mGranularity).mul(mGranularity) == _amount);
        }
        function isRegularAddress(address _addr) internal constant returns (bool) {
            return true;
        }

        function doSend(
            address _operator,
            address _from,
            address _to,
            uint128 _amount,
            bytes _userData,
            bytes _operatorData,
            bool _preventLocking
        ) internal
        {
            Sent(_operator, _from, _to, _amount, _userData, _operatorData);
        }

        function doBurn(address _operator, address _tokenHolder, uint128 _amount, bytes _holderData, bytes _operatorData) internal
        {
            Burned(_operator, _tokenHolder, _amount, _holderData, _operatorData);
        }

        event Created(
            uint128 indexed     _totalSupply,
            address indexed     _specialAddress);
        event Sent(
            address indexed     _operator,
            address indexed     _from,
            address indexed     _to,
            uint128             _amount,
            bytes               _holderData,
            bytes               _operatorData);
        event Minted(
            address indexed     _operator,
            address indexed     _to,
            uint128             _amount,
            bytes               _operatorData);
        event Burned(
            address indexed     _operator,
            address indexed     _from,
            uint128             _amount,
            bytes               _holderData,
            bytes               _operatorData);
        event AuthorizedOperator(
            address indexed     _operator,
            address indexed     _tokenHolder);
        event RevokedOperator(
            address indexed     _operator,
            address indexed     _tokenHolder);
        event Transfer(
            address indexed from,
            address indexed to,
            uint128 value
        );
        event Approval(
            address indexed owner,
            address indexed spender,
            uint128 value
        );
    }

