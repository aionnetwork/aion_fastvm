pragma solidity ^0.4.15;

contract testedvalidate {
	function validate(
		bytes32 messageHash,
		bytes32 publicKey,
		bytes32 sig1,
		bytes32 sig2)
	returns (address) {
		return edverify(messageHash, publicKey, sig1, sig2);
	}
}