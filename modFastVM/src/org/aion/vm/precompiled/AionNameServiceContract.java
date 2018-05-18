/*******************************************************************************
 *
 * Copyright (c) 2017-2018 Aion foundation.
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>
 *
 * Contributors:
 *     Aion foundation.
 ******************************************************************************/
package org.aion.vm.precompiled;

import org.aion.base.db.IRepositoryCache;
import org.aion.base.type.Address;
//import org.aion.crypto.ECKey;
//import org.aion.crypto.ECKeyFac;
import org.aion.crypto.ed25519.ECKeyEd25519;
import org.aion.crypto.ed25519.Ed25519Signature;
//import org.aion.fastvm.*;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.mcf.vm.types.DataWord;
import org.aion.vm.ExecutionResult;
import org.aion.vm.PrecompiledContracts;

import static org.aion.crypto.HashUtil.blake128;

//import java.nio.ByteBuffer;

/**
 * Aion Name Service
 *
 * Architecture:
 * Registry consists of a single central contract that maintains a list of all domains and
 * sub-domains, and stores:
 *      owners of domain (external acc/user or smart contract)
 *      resolver of domain
 *      time-to-live for all records under the domain
 * Resolvers are responsible for the actual process of translating names into address
 *
 */


public class AionNameServiceContract extends PrecompiledContracts.StatefulPrecompiledContract{


    // set to a default cost for now, this will need to be adjusted
    //public final static long COST = 30000L;
    private final static long SET_COST = 1000;
    private final static long TRANSFER_COST = 2000;

    private Address address; // of contract
    //private Address domainAddress;
    private Address ownerAddress;
    private Address resolverAddressKey;
    private Address TTLKey;


    /**
     * Construct a new ANS Contract
     */

    public AionNameServiceContract(IRepositoryCache<AccountState, DataWord, IBlockStoreBase<?, ?>> track, Address
            address, Address ownerAddress){
        super(track);
        this.address = address;
        this.ownerAddress = ownerAddress;
    }

    /**
     *
     * Input is defined as:
     * [ <1b chainID> |<1b operation> | <32b address> | <96b signature>]
     *      total: 1 + 1 + 32 + 96 = 130
     *
     *      Where the chainId is intended to be our current chainId, in the case of the first AION network this should
     *      be set to 1. operation checks for which contract operation the user wishes to execute. The address represent
     *      the new address to be used, and lastly the signature for security.
     *
     */

    public ExecutionResult execute(byte[] input, long nrg) {

        // check for correct input format
        if (input.length != 130)
            return new ExecutionResult(ExecutionResult.Code.INTERNAL_ERROR, 0);

        // process input data
        int offset = 0;
        //DataWord chainId = new DataWord(input[0]);
        offset++;
        byte operation = input[1];
        offset++;

        //byte[] address = new byte[32];
        byte[] addressFirstPart = new byte[16];
        byte[] addressSecondPart = new byte[16];
        byte[] addressCombined = new byte[32];
        byte[] sign = new byte[96];

        System.arraycopy(input, offset, addressCombined, 0, 32);
        System.arraycopy(input, offset, addressFirstPart, 0, 16);
        offset +=16;
        System.arraycopy(input, offset, addressSecondPart, 0, 16);
        offset +=16;
        System.arraycopy(input, offset, sign, 0, 96);
        //offset +=96;

        // verify signature is correct
        Ed25519Signature sig = Ed25519Signature.fromBytes(sign);
        if (sig == null) {
            return new ExecutionResult(ExecutionResult.Code.INTERNAL_ERROR, 0);
        }

        byte[] payload = new byte[34];
        System.arraycopy(input, 0, payload, 0, 34);
        boolean b = ECKeyEd25519.verify(payload, sig.getSignature(), sig.getPubkey(null));

        if (!b) {
            return new ExecutionResult(ExecutionResult.Code.INTERNAL_ERROR, 0);
        }

        //Address mmm = Address.wrap(sig.getAddress());

        // verify public key matches owner
        if (!this.ownerAddress.equals(Address.wrap(sig.getAddress()))) {
            return new ExecutionResult(ExecutionResult.Code.INTERNAL_ERROR, 0);
        }

        // hashes of the name, 16 bytes each
        byte[] HashFirstPart = blake128(this.address.toBytes());
        byte[] HashSecondPart = blake128(HashFirstPart);

        // operation: {1-setResolver, 2-setTTL, 3-transferOwnership, 4-transferSubdomainOwnership}
        switch (operation){
            case 1: return setResolver(HashFirstPart, HashSecondPart, addressFirstPart, addressFirstPart, nrg);
            case 2: return setTTL(HashFirstPart, HashSecondPart, addressFirstPart, addressFirstPart, nrg);
            case 3: return transferOwnership(HashFirstPart, HashSecondPart, addressFirstPart, addressFirstPart, nrg);
            case 4: return transferSubdomainOwnership(HashFirstPart, HashSecondPart, addressFirstPart, addressFirstPart, nrg);
            default: return new ExecutionResult(ExecutionResult.Code.INTERNAL_ERROR, nrg); // unsupported operation
        }
    }

    /**
     * Set Resolver for this domain
     */

    private ExecutionResult setResolver (byte[] hash1, byte[] hash2, byte[] addr1, byte[] addr2, long nrg){
        if (nrg < SET_COST)
            return new ExecutionResult(ExecutionResult.Code.OUT_OF_NRG, 0);

        // store result
        this.track.addStorageRow(this.address, new DataWord(hash1), new DataWord(addr1));
        this.track.addStorageRow(this.address, new DataWord(hash2), new DataWord(addr2));

        //set the key
        byte[] combined = new byte[32];
        System.arraycopy(hash1, 0, combined, 0, 16);
        System.arraycopy(hash2, 0, combined, 16, 16);
        this.resolverAddressKey = new Address(combined);

        return new ExecutionResult(ExecutionResult.Code.SUCCESS, nrg - SET_COST);
    }

    /**
     * Set Time to Live for this domain
     */
    private ExecutionResult setTTL (byte[] hash1, byte[] hash2, byte[] addr1, byte[] addr2, long nrg){
        if (nrg < SET_COST)
            return new ExecutionResult(ExecutionResult.Code.OUT_OF_NRG, 0);

        // store result
        this.track.addStorageRow(this.address, new DataWord(hash1), new DataWord(addr1));
        this.track.addStorageRow(this.address, new DataWord(hash2), new DataWord(addr2));

        //set the key
        byte[] combined = new byte[32];
        System.arraycopy(hash1, 0, combined, 0, 16);
        System.arraycopy(hash2, 0, combined, 16, 16);
        this.TTLKey = new Address(combined);

        return new ExecutionResult(ExecutionResult.Code.SUCCESS, nrg - SET_COST);
    }

    /**
     * Transfer the ownership of this domain
     */
    private ExecutionResult transferOwnership (byte[] hash1, byte[] hash2, byte[] addr1, byte[] addr2, long nrg){
        if (nrg < TRANSFER_COST)
            return new ExecutionResult(ExecutionResult.Code.OUT_OF_NRG, 0);


        // store result
        this.track.addStorageRow(this.address, new DataWord(hash1), new DataWord(addr1));
        this.track.addStorageRow(this.address, new DataWord(hash2), new DataWord(addr2));

        //set the key
        byte[] combined = new byte[32];
        System.arraycopy(hash1, 0, combined, 0, 16);
        System.arraycopy(hash2, 0, combined, 16, 16);
        this.ownerAddress = new Address(combined);

        return new ExecutionResult(ExecutionResult.Code.SUCCESS, nrg - TRANSFER_COST);
    }

    /**
     * Transfer the ownership of subdomain
     *
     * have not implemented
    */
    private ExecutionResult transferSubdomainOwnership(byte[] hash1, byte[] hash2, byte[] addr1, byte[] addr2, long nrg){
        if(nrg < TRANSFER_COST)
            return new ExecutionResult(ExecutionResult.Code.OUT_OF_NRG,0);
        return null;
    }


    // getter functions
    public Address getResolverAddress() { return this.track.getStorageValue(); }

    public Address getDomainAddress(){ return  this.address; }

    public Address getTTL(){ return this.TTL; }

    public Address getOwnerAddress(){ return ownerAddress; }
}


