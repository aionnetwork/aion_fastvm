package org.aion.fastvm;

import java.math.BigInteger;
import org.aion.types.AionAddress;

/**
 * An external capabilities interface in which the caller passes in capabilities to the Fvm.
 */
public interface IExternalCapabilities {

    /**
     * Returns a new address that will be the newly created address for a transaction that was sent
     * by the given sender, who has the given nonce.
     *
     * The returned address should be deterministic given the two inputs.
     *
     * @param sender The sender.
     * @param senderNonce The sender's nonce.
     * @return the new contract address.
     */
    AionAddress computeNewContractAddress(AionAddress sender, BigInteger senderNonce);

    /**
     * Returns the SHA-256 hash of the specified paylaod.
     *
     * @param payload The payload to hash.
     * @return the hash.
     */
    byte[] hash256(byte[] payload);
}
