/*
 * Copyright (c) 2017-2018 Aion foundation.
 *
 *     This file is part of the aion network project.
 *
 *     The aion network project is free software: you can redistribute it
 *     and/or modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation, either version 3 of
 *     the License, or any later version.
 *
 *     The aion network project is distributed in the hope that it will
 *     be useful, but WITHOUT ANY WARRANTY; without even the implied
 *     warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *     See the GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with the aion network project source files.
 *     If not, see <https://www.gnu.org/licenses/>.
 *
 * Contributors:
 *     Aion foundation.
 */
package org.aion.fastvm;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.aion.types.AionAddress;
import org.aion.fastvm.util.ByteUtil;
import org.aion.types.Transaction;
import org.aion.fastvm.util.TransactionUtil;
import org.apache.commons.lang3.ArrayUtils;

/**
 * Execution context, including both transaction and block information.
 *
 * @author yulong
 */
public class ExecutionContext {
    private static final int ENCODE_BASE_LEN =
            (AionAddress.LENGTH * 4)
                    + (FvmDataWord.SIZE * 3)
                    + (Long.BYTES * 4)
                    + (Integer.BYTES * 4);

    private SideEffects sideEffects;
    private AionAddress origin;
    private byte[] originalTxHash;

    public AionAddress address;
    public AionAddress sender;
    private AionAddress blockCoinbase;
    private FvmDataWord nrgPrice;
    private FvmDataWord callValue;
    private FvmDataWord blockDifficulty;
    private byte[] callData;
    private byte[] txHash;
    private long nrg;
    private long blockNumber;
    private long blockTimestamp;
    private long blockNrgLimit;
    private int depth;
    private TransactionKind kind;
    private int flags;
    private final AionAddress contractAddress;
    private final boolean isCreate;

    private ExecutionContext(
            boolean isCreate,
            byte[] txHash,
            AionAddress destination,
            AionAddress contract,
            AionAddress origin,
            AionAddress sender,
            FvmDataWord nrgPrice,
            long nrg,
            FvmDataWord callValue,
            byte[] callData,
            int depth,
            TransactionKind kind,
            int flags,
            AionAddress blockCoinbase,
            long blockNumber,
            long blockTimestamp,
            long blockNrgLimit,
            FvmDataWord blockDifficulty) {

        this.address = destination;
        this.origin = origin;
        this.sender = sender;
        this.nrgPrice = nrgPrice;
        this.blockDifficulty = blockDifficulty;
        this.nrg = nrg;
        this.callValue = callValue;
        this.callData = callData;
        this.depth = depth;
        this.kind = kind;
        this.flags = flags;
        this.blockCoinbase = blockCoinbase;
        this.blockNumber = blockNumber;
        this.blockTimestamp = blockTimestamp;
        this.blockNrgLimit = blockNrgLimit;
        this.txHash = txHash;
        this.originalTxHash = txHash;
        this.contractAddress = contract;
        this.isCreate = isCreate;
        this.sideEffects = new SideEffects();
    }

    /**
     * Returns a new execution context from a transaction, as well as given the additional parameters
     * that are not present in transaction objects.
     *
     * @param transaction The transaction.
     * @param miner The miner address.
     * @param blockNumber The current block number.
     * @param blockTimestamp The current block's timestamp.
     * @param blockEnergyLimit The current block's energy limit.
     * @param blockDifficulty The current block's difficulty.
     * @return the context.
     */
    public static ExecutionContext fromTransaction(Transaction transaction, AionAddress contract, AionAddress miner, long blockNumber, long blockTimestamp, long blockEnergyLimit, FvmDataWord blockDifficulty) {
        if (transaction == null) {
            throw new NullPointerException("Cannot create context from null transaction!");
        }
        if (miner == null) {
            throw new NullPointerException("Cannot create context with null miner!");
        }
        if (blockDifficulty == null) {
            throw new NullPointerException("Cannot create context with null blockDifficulty!");
        }

        byte[] transactionHash = transaction.copyOfTransactionHash();
        AionAddress originAddress = transaction.senderAddress;
        AionAddress callerAddress = transaction.senderAddress;
        FvmDataWord energyPrice = FvmDataWord.fromLong(transaction.energyPrice);
        long energy = transaction.energyLimit - TransactionUtil.computeTransactionCost(transaction);
        FvmDataWord transferValue = FvmDataWord.fromBigInteger(transaction.value);
        byte[] data = ArrayUtils.nullToEmpty(transaction.copyOfTransactionData());
        AionAddress destinationAddress = transaction.isCreate ? contract : transaction.destinationAddress;
        TransactionKind kind = transaction.isCreate ? TransactionKind.CREATE : TransactionKind.CALL;

        return new ExecutionContext(transaction.isCreate, transactionHash, destinationAddress, contract, originAddress, callerAddress, energyPrice, energy, transferValue, data, 0, kind, 0, miner, blockNumber, blockTimestamp, blockEnergyLimit, blockDifficulty);
    }

    /**
     * Returns a new execution context from the specified parameters.
     *
     * @param transactionHash The transaction hash.
     * @param destination The destination (for CREATE this is the new contract address).
     * @param origin The origin address that sent the initial external transaction.
     * @param sender The sender address that sent the current transaction.
     * @param energyPrice The energy price.
     * @param energy The current amount of energy.
     * @param value The value to be transferred.
     * @param data The data.
     * @param depth The stack depth.
     * @param kind The transaction kind.
     * @param flags The flags.
     * @param miner The miner address.
     * @param blockNumber The current block number.
     * @param blockTimestamp The current block's timestamp.
     * @param blockEnergyLimit The current block's energy limit.
     * @param blockDifficulty The current block's difficulty.
     * @return the context.
     */
    public static ExecutionContext from(byte[] transactionHash, AionAddress destination, AionAddress origin, AionAddress sender, long energyPrice, long energy, BigInteger value, byte[] data, int depth, TransactionKind kind, int flags, AionAddress miner, long blockNumber, long blockTimestamp, long blockEnergyLimit, FvmDataWord blockDifficulty) {
        if (transactionHash == null) {
            throw new NullPointerException("Cannot create context with null transaction hash!");
        }
        if (destination == null) {
            throw new NullPointerException("Cannot create context with null destination!");
        }
        if (origin == null) {
            throw new NullPointerException("Cannot create context with null origin!");
        }
        if (sender == null) {
            throw new NullPointerException("Cannot create context with null sender!");
        }
        if (value == null) {
            throw new NullPointerException("Cannot create context with null value!");
        }
        if (data == null) {
            throw new NullPointerException("Cannot create context with null data!");
        }
        if (kind == null) {
            throw new NullPointerException("Cannot create context with null kind!");
        }
        if (miner == null) {
            throw new NullPointerException("Cannot create context with null miner!");
        }
        if (blockDifficulty == null) {
            throw new NullPointerException("Cannot create context with null blockDifficulty!");
        }

        //TODO: AKI-289 stop passing null in as the contract address in all cases. We should pass the address when we have a CREATE.
        return new ExecutionContext(kind == TransactionKind.CREATE, transactionHash, destination, null, origin, sender, FvmDataWord.fromLong(energyPrice), energy, FvmDataWord.fromBigInteger(value), data, depth, kind, flags, miner, blockNumber, blockTimestamp, blockEnergyLimit, blockDifficulty);
    }

    /**
     * Returns a big-endian binary encoding of this ExecutionContext in the following format:
     *
     * <p>|32b - address|32b - origin|32b - caller|16b - nrgPrice|8b - nrgLimit|16b - callValue| 4b
     * - callDataLength|?b - callData|4b - depth|4b - kind|4b - flags|32b - blockCoinbase| 8b -
     * blockNumber|8b - blockTimestamp|8b - blockNrgLimit|16b - blockDifficulty|
     *
     * <p>where callDataLength is the length of callData.
     *
     * @return a binary encoding of this ExecutionContext.
     */
    public byte[] toBytes() {

        // If this is a CREATE then we do not want to serialize the callData.
        if (isCreate) {
            callData = ByteUtil.EMPTY_BYTE_ARRAY;
        }

        ByteBuffer buffer = ByteBuffer.allocate(getEncodingLength());
        buffer.order(ByteOrder.BIG_ENDIAN);
        buffer.put(address.toByteArray());
        buffer.put(origin.toByteArray());
        buffer.put(sender.toByteArray());
        buffer.put(nrgPrice.copyOfData());
        buffer.putLong(nrg);
        buffer.put(callValue.copyOfData());
        buffer.putInt(callData.length); // length of the call data
        buffer.put(callData);
        buffer.putInt(depth);
        buffer.putInt(kind.intValue);
        buffer.putInt(flags);
        buffer.put(blockCoinbase.toByteArray());
        buffer.putLong(blockNumber);
        buffer.putLong(blockTimestamp);
        buffer.putLong(blockNrgLimit);
        buffer.put(blockDifficulty.copyOfData());
        return buffer.array();
    }

    /** @return the transaction hash. */
    public byte[] getTransactionHash() {
        return txHash;
    }

    public void setDestinationAddress(AionAddress address) {
        this.address = address;
    }

    public AionAddress getContractAddress() {
        return this.contractAddress;
    }

    /** @return the transaction address. */
    public AionAddress getDestinationAddress() {
        return address;
    }

    /** @return the origination address, which is the sender of original transaction. */
    public AionAddress getOriginAddress() {
        return origin;
    }

    /** @return the transaction caller. */
    public AionAddress getSenderAddress() {
        return sender;
    }

    /** @return the nrg price in current environment. */
    public long getTransactionEnergyPrice() {
        return this.nrgPrice.toLong();
    }

    /**
     * Note that this quantity is not the same as the energy limit, and is typically the energy
     * limit minus the transaction cost.
     * 
     * @return the current amount of energy remaining in current environment.
     */
    public long getTransactionEnergy() {
        return nrg;
    }

    /** @return the deposited value by instruction/transaction. */
    public BigInteger getTransferValue() {
        return callValue.toBigInteger();
    }

    /** @return the call data. */
    public byte[] getTransactionData() {
        return callData;
    }

    /** @return the execution stack depth. */
    public int getTransactionStackDepth() {
        return depth;
    }

    /** @return the transaction kind. */
    public TransactionKind getTransactionKind() {
        return kind;
    }

    /** @return the transaction flags. */
    public int getFlags() {
        return flags;
    }

    /** @return the block's beneficiary. */
    public AionAddress getMinerAddress() {
        return blockCoinbase;
    }

    /** @return the block number. */
    public long getBlockNumber() {
        return blockNumber;
    }

    /** @return the block timestamp. */
    public long getBlockTimestamp() {
        return blockTimestamp;
    }

    /** @return the block energy limit. */
    public long getBlockEnergyLimit() {
        return blockNrgLimit;
    }

    /** @return the block difficulty. */
    public FvmDataWord getBlockDifficulty() {
        return blockDifficulty;
    }

    /** @return the transaction helper. */
    public SideEffects getSideEffects() {
        return sideEffects;
    }

    /**
     * Returns the length of the big-endian binary encoding of this ExecutionContext.
     *
     * @return the legtn of this ExecutionContext's binary encoding.
     */
    private int getEncodingLength() {
        return ENCODE_BASE_LEN + callData.length;
    }

    /** @return the original transaction hash. */
    public byte[] getHashOfOriginTransaction() {
        return originalTxHash;
    }
}
