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
import org.aion.mcf.vm.types.DataWordImpl;
import org.aion.mcf.vm.types.DoubleDataWord;
import org.aion.interfaces.vm.DataWord;
import org.aion.util.bytes.ByteUtil;
import org.aion.vm.api.interfaces.TransactionContext;
import org.aion.vm.api.interfaces.TransactionInterface;
import org.aion.vm.api.interfaces.TransactionSideEffects;

/**
 * Execution context, including both transaction and block information.
 *
 * @author yulong
 */
public class ExecutionContext implements TransactionContext {
    private static final int ENCODE_BASE_LEN =
            (AionAddress.LENGTH * 4) + (DataWordImpl.BYTES * 3) + (Long.BYTES * 4) + (Integer.BYTES * 4);
    public static int CALL = 0;
    public static int DELEGATECALL = 1;
    public static int CALLCODE = 2;
    public static int CREATE = 3;

    private SideEffects sideEffects;
    private AionAddress origin;
    private byte[] originalTxHash;

    private TransactionInterface transaction;

    public AionAddress address;
    public AionAddress sender;
    private AionAddress blockCoinbase;
    private DataWord nrgPrice;
    private DataWord callValue;
    private DataWord blockDifficulty;
    private byte[] callData;
    private byte[] txHash;
    private long nrg; // NOTE: nrg = tx_nrg_limit - tx_basic_cost
    private long blockNumber;
    private long blockTimestamp;
    private long blockNrgLimit;
    private int depth;
    private int kind;
    private int flags;

    /**
     * Creates a VM execution context.
     *
     * @param txHash The transaction hash
     * @param destination The transaction address.
     * @param origin The sender of the original transaction.
     * @param sender The transaction caller.
     * @param nrgPrice The nrg price in current environment.
     * @param nrg The nrg limit in current environment.
     * @param callValue The deposited value by instruction/trannsaction.
     * @param callData The call data.
     * @param depth The execution stack depth.
     * @param kind The transaction kind.
     * @param flags The transaction flags.
     * @param blockCoinbase The beneficiary of the block.
     * @param blockNumber The block number.
     * @param blockTimestamp The block timestamp.
     * @param blockNrgLimit The block energy limit.
     * @param blockDifficulty The block difficulty.
     * @throws IllegalArgumentException if any numeric quantities are negative or txHash is not
     *     length 32.
     */
    public ExecutionContext(
            TransactionInterface transaction,
            byte[] txHash,
            AionAddress destination,
            AionAddress origin,
            AionAddress sender,
            DataWord nrgPrice,
            long nrg,
            DataWord callValue,
            byte[] callData,
            int depth,
            int kind,
            int flags,
            AionAddress blockCoinbase,
            long blockNumber,
            long blockTimestamp,
            long blockNrgLimit,
            DataWord blockDifficulty) {

        this.transaction = transaction;
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

        this.sideEffects = new SideEffects();
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
    @Override
    public byte[] toBytes() {

        // If this is a CREATE then we do not want to serialize the callData.
        if (transaction != null && transaction.isContractCreationTransaction()) {
            callData = ByteUtil.EMPTY_BYTE_ARRAY;
        }

        ByteBuffer buffer = ByteBuffer.allocate(getEncodingLength());
        buffer.order(ByteOrder.BIG_ENDIAN);
        buffer.put(address.toByteArray());
        buffer.put(origin.toByteArray());
        buffer.put(sender.toByteArray());
        buffer.put(nrgPrice.getData());
        buffer.putLong(nrg);
        buffer.put(callValue.getData());
        buffer.putInt(callData.length); // length of the call data
        buffer.put(callData);
        buffer.putInt(depth);
        buffer.putInt(kind);
        buffer.putInt(flags);
        buffer.put(blockCoinbase.toByteArray());
        buffer.putLong(blockNumber);
        buffer.putLong(blockTimestamp);
        buffer.putLong(blockNrgLimit);
        buffer.put(blockDifficulty.getData());
        return buffer.array();
    }

    /** @return the transaction hash. */
    @Override
    public byte[] getTransactionHash() {
        return txHash;
    }

    @Override
    public void setDestinationAddress(AionAddress address) {
        this.address = address;
    }

    public AionAddress getContractAddress() {
        return this.transaction.getContractAddress();
    }

    /** @return the transaction address. */
    @Override
    public AionAddress getDestinationAddress() {
        return address;
    }

    /** @return the origination address, which is the sender of original transaction. */
    @Override
    public AionAddress getOriginAddress() {
        return origin;
    }

    /** @return the transaction caller. */
    @Override
    public AionAddress getSenderAddress() {
        return sender;
    }

    /** @return the nrg price in current environment. */
    @Override
    public long getTransactionEnergyPrice() {
        if (this.nrgPrice instanceof DataWordImpl) {
            return ((DataWordImpl) this.nrgPrice).longValue();
        } else {
            return ((DoubleDataWord) this.nrgPrice).longValue();
        }
    }

    /** @return the nrg limit in current environment. */
    public long getTransactionEnergy() {
        return nrg;
    }

    /** @return the deposited value by instruction/transaction. */
    @Override
    public BigInteger getTransferValue() {
        return callValue.value();
    }

    /** @return the call data. */
    @Override
    public byte[] getTransactionData() {
        return callData;
    }

    /** @return the execution stack depth. */
    @Override
    public int getTransactionStackDepth() {
        return depth;
    }

    /** @return the transaction kind. */
    @Override
    public int getTransactionKind() {
        return kind;
    }

    /** @return the transaction flags. */
    @Override
    public int getFlags() {
        return flags;
    }

    /** @return the block's beneficiary. */
    @Override
    public AionAddress getMinerAddress() {
        return blockCoinbase;
    }

    /** @return the block number. */
    @Override
    public long getBlockNumber() {
        return blockNumber;
    }

    /** @return the block timestamp. */
    @Override
    public long getBlockTimestamp() {
        return blockTimestamp;
    }

    /** @return the block energy limit. */
    @Override
    public long getBlockEnergyLimit() {
        return blockNrgLimit;
    }

    /** @return the block difficulty. */
    @Override
    public long getBlockDifficulty() {
        if (blockDifficulty instanceof DataWordImpl) {
            return ((DataWordImpl) blockDifficulty).longValue();
        } else {
            return ((DoubleDataWord) blockDifficulty).longValue();
        }
    }

    /** @return the transaction helper. */
    @Override
    public TransactionSideEffects getSideEffects() {
        return sideEffects;
    }

    /**
     * Sets the transaction hash to txHash.
     *
     * @param txHash The new transaction hash.
     */
    @Override
    public void setTransactionHash(byte[] txHash) {
        this.txHash = txHash;
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
    @Override
    public byte[] getHashOfOriginTransaction() {
        return originalTxHash;
    }

    @Override
    public TransactionInterface getTransaction() {
        return this.transaction;
    }
}
