/*******************************************************************************
 *
 * Copyright (c) 2017 Aion foundation.
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

import org.aion.base.type.Address;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.aion.crypto.ISignature;
import org.aion.fastvm.DummyRepository;
import org.aion.vm.ExecutionResult;
import org.junit.Before;
import org.junit.Test;

import java.nio.ByteBuffer;

import static com.google.common.truth.Truth.assertThat;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;

public class AionNameServiceContractTest {

    //private final static String DEFAULT_ADDRESS = "0101010111000000010000000000001111010111110001001111101010101011";
    //private Address domainAddress = Address.wrap(DEFAULT_ADDRESS);
    //private Address resolverAddress = Address.wrap(DEFAULT_ADDRESS);

    private Address contractAddress1 = Address.wrap("1111111111111111111111111111111101010101010101010101010101010101");
    private Address contractAddress2 = Address.wrap("2222222222222222222222222222222202020202020202020202020202020202");
    private Address contractAddress3 = Address.wrap("3333333333333333333333333333333303030303030303030303030303030303");

    private Address newAddress1 = Address.wrap("1000000000000000000000000000000000000000000000000000000000000001");
    private Address newAddress2 = Address.wrap("0100000000000000000000000000000000000000000000000000000000000010");
    private Address newAddress3 = Address.wrap("0010000000000000000000000000000000000000000000000000000000000100");
    private Address newAddress4 = Address.wrap("0001000000000000000000000000000000000000000000000000000000001000");
    private Address newAddress5 = Address.wrap("0000100000000000000000000000000000000000000000000000000000010000");
    private Address newAddress6 = Address.wrap("0000010000000000000000000000000000000000000000000000000000100000");
    private Address newAddress7 = Address.wrap("0000001000000000000000000000000000000000000000000000000001000000");
    private Address newAddress8 = Address.wrap("0000000100000000000000000000000000000000000000000000000010000000");

    @Before
    public void setup(){}

    /**
     * Test functions of ANS with multiple instances of ans in the database
     *
     * Test the transfer of ownership
     *
     * Initial set up:
     * Storage:    contract1: contractAddress1
     *                      resolver - newAddress1
     *                      ttl - newAddress2
     *                      owner - newAddress3
     *             contract2: contractAddress2
     *                      resolver - newAddress4
     *                      ttl -
     *                      owner -
     *
     */
    @Test
    public void bigTest(){
        // initialize input parameters
        final long inputEnergy = 5000L;
        ECKey k = ECKeyFac.inst().create();
        DummyRepository repo = new DummyRepository();

        // create ANS contracts
        AionNameServiceContract ansc = new AionNameServiceContract(repo, contractAddress1, Address.wrap(k.getAddress()));
        AionNameServiceContract ansc2 = new AionNameServiceContract(repo, contractAddress2, Address.wrap(k.getAddress()));

        // setup the inputs 1: set resolver-----------------------------------------------------------------------------
        ByteBuffer bb = ByteBuffer.allocate(34);
        bb.put((byte) 0x0)          // chainID
                .put((byte) 0x1)    // OPERATION HERE
                .put(newAddress1.toBytes(), 0, 32);
        byte[] data = bb.array();
        ISignature signature1 = k.sign(data);
        bb = ByteBuffer.allocate(34 + 96);
        bb.put(data);
        bb.put(signature1.toBytes());
        byte[] combined = bb.array();

        // setup the inputs 2: set TTL
        ByteBuffer bb2 = ByteBuffer.allocate(34);
        bb2.put((byte) 0x0)          // chainID
                .put((byte) 0x2)    // OPERATION HERE
                .put(newAddress2.toBytes(), 0, 32);
        byte[] data2 = bb2.array();
        ISignature signature2 = k.sign(data2);
        bb2 = ByteBuffer.allocate(34 + 96);
        bb2.put(data2);
        bb2.put(signature2.toBytes());
        byte[] combined2 = bb2.array();

        // setup the inputs 3: Transfer(set) owner
        ByteBuffer bb3 = ByteBuffer.allocate(34);
        bb3.put((byte) 0x0)          // chainID
                .put((byte) 0x3)    // OPERATION HERE
                .put(newAddress3.toBytes(), 0, 32);
        byte[] data3 = bb3.array();
        ISignature signature3 = k.sign(data3);
        bb3 = ByteBuffer.allocate(34 + 96);
        bb3.put(data3);
        bb3.put(signature3.toBytes());
        byte[] combined3 = bb3.array();

        // setup the inputs 4: set Resolver
        ByteBuffer bb4 = ByteBuffer.allocate(34);
        bb4.put((byte) 0x0)          // chainID
                .put((byte) 0x1)    // OPERATION HERE
                .put(newAddress4.toBytes(), 0, 32);
        byte[] data4 = bb4.array();
        ISignature signature4 = k.sign(data4);
        bb4 = ByteBuffer.allocate(34 + 96);
        bb4.put(data4);
        bb4.put(signature4.toBytes());
        byte[] combined4 = bb4.array();

        // execute contract --------------------------------------------------------------------------------------------
        ExecutionResult res1 = ansc.execute(combined, inputEnergy);
        ExecutionResult res2 = ansc.execute(combined2, inputEnergy);
        ExecutionResult res3 = ansc.execute(combined3, inputEnergy);

        // basic checks
        assertThat(res1.getCode()).isEqualTo(ExecutionResult.Code.SUCCESS);
        assertThat(res1.getNrgLeft()).isEqualTo(4000);
        assertThat(res2.getCode()).isEqualTo(ExecutionResult.Code.SUCCESS);
        assertThat(res2.getNrgLeft()).isEqualTo(4000);
        assertThat(res3.getCode()).isEqualTo(ExecutionResult.Code.SUCCESS);
        assertThat(res3.getNrgLeft()).isEqualTo(3000);

        // storage checks
        assertEquals(newAddress1, ansc.getResolverAddress());
        assertEquals(newAddress2, ansc.getTTL());
        assertEquals(newAddress3, ansc.getOwnerAddress());

        // contract2
        ExecutionResult res4 = ansc2.execute(combined4, inputEnergy);
        assertThat(res4.getCode()).isEqualTo(ExecutionResult.Code.SUCCESS);
        assertThat(res4.getNrgLeft()).isEqualTo(4000);
        assertEquals(newAddress4, ansc2.getResolverAddress());
    }


    @Test
    public void testSetResolver(){

        // initialize input parameters
        final long inputEnergy = 5000L;
        final long expectedEnergyLeft = 4000L;

        ECKey k = ECKeyFac.inst().create();
        DummyRepository repo = new DummyRepository();

        // create ANS contract
        AionNameServiceContract ansc = new AionNameServiceContract(repo, contractAddress1, Address.wrap(k.getAddress()));

        // setup the inputs
        ByteBuffer bb = ByteBuffer.allocate(34);
        bb.put((byte) 0x0)          // chainID
                .put((byte) 0x1)    // OPERATION HERE
                .put(newAddress1.toBytes(), 0, 32);

        byte[] payload = bb.array();
        ISignature signature = k.sign(payload);

        bb = ByteBuffer.allocate(34 + 96);
        bb.put(payload);
        bb.put(signature.toBytes());
        byte[] combined = bb.array();

        // execute ANS contract
        ExecutionResult res = ansc.execute(combined, inputEnergy);
        Address actualReturnedAddress = ansc.getResolverAddress();

        // check for success and failure
        assertThat(res.getCode()).isEqualTo(ExecutionResult.Code.SUCCESS);
        assertThat(res.getNrgLeft()).isEqualTo(expectedEnergyLeft);
        assertEquals(newAddress1, actualReturnedAddress);
    }

    @Test
    public void testIncorrectSignature(){

        // initialize input parameters
        final long inputEnergy = 5000L;
        final long expectedEnergyLeft = 0L;

        ECKey k = ECKeyFac.inst().create();
        DummyRepository repo = new DummyRepository();

        // create ANS contract
        AionNameServiceContract ansc = new AionNameServiceContract(repo, contractAddress1, Address.wrap(k.getAddress()));

        // setup the inputs
        ByteBuffer bb = ByteBuffer.allocate(34);
        bb.put((byte) 0x0)          // chainID
                .put((byte) 0x1)    // OPERATION HERE
                .put(newAddress1.toBytes(), 0, 32);

        byte[] payload = bb.array();
        ISignature signature = k.sign(payload);

        bb = ByteBuffer.allocate(34 + 96);
        bb.put(payload);
        bb.put(signature.toBytes());
        byte[] combined = bb.array();

        // modify the signature in the 110th byte (arbitrarily)
        combined[110] = (byte) (combined[110] + 1);

        // execute ANS contract
        ExecutionResult res = ansc.execute(combined, inputEnergy);
        Address actualReturnedAddress = ansc.getResolverAddress();

        // check for success and failure
        assertThat(res.getCode()).isEqualTo(ExecutionResult.Code.INTERNAL_ERROR);
        assertThat(res.getNrgLeft()).isEqualTo(expectedEnergyLeft);
        // since the signature is incorrect, contract is not modified
        assertNull(actualReturnedAddress);
    }

    @Test
    public void testIncorrectPublicKey(){

        // initialize input parameters
        final long inputEnergy = 5000L;
        final long expectedEnergyLeft = 0L;

        ECKey k = ECKeyFac.inst().create();
        ECKey notk = ECKeyFac.inst().create();

        DummyRepository repo = new DummyRepository();

        // create ANS contract
        AionNameServiceContract ansc = new AionNameServiceContract(repo, contractAddress1, Address.wrap(k.getAddress()));

        // setup the inputs
        ByteBuffer bb = ByteBuffer.allocate(34);
        bb.put((byte) 0x0)          // chainID
                .put((byte) 0x1)    // OPERATION HERE
                .put(newAddress1.toBytes(), 0, 32);

        byte[] payload = bb.array();
        ISignature signature = notk.sign(payload);

        bb = ByteBuffer.allocate(34 + 96);
        bb.put(payload);
        bb.put(signature.toBytes());
        byte[] combined = bb.array();

        // execute ANS contract
        ExecutionResult res = ansc.execute(combined, inputEnergy);
        Address actualReturnedAddress = ansc.getResolverAddress();

        // check for success and failure
        assertThat(res.getCode()).isEqualTo(ExecutionResult.Code.INTERNAL_ERROR);
        assertThat(res.getNrgLeft()).isEqualTo(expectedEnergyLeft);
        // since the signature is incorrect, contract is not modified
        assertNull(actualReturnedAddress);
    }

    @Test
    public void testTransferOwnership(){

        // initialize input parameters
        final long inputEnergy = 5000L;
        final long expectedEnergyLeft = 3000L;

        ECKey k = ECKeyFac.inst().create();
        DummyRepository repo = new DummyRepository();

        // create ANS contract
        AionNameServiceContract ansc = new AionNameServiceContract(repo, contractAddress1, Address.wrap(k.getAddress()));

        // setup the inputs
        ByteBuffer bb = ByteBuffer.allocate(34);
        bb.put((byte) 0x0)          // chainID
                .put((byte) 0x3)    // OPERATION HERE
                .put(newAddress1.toBytes(), 0, 32);

        byte[] payload = bb.array();
        ISignature signature = k.sign(payload);

        bb = ByteBuffer.allocate(34 + 96);
        bb.put(payload);
        bb.put(signature.toBytes());
        byte[] combined = bb.array();

        // execute ANS contract
        ExecutionResult res = ansc.execute(combined, inputEnergy);
        Address actualReturnedAddress = ansc.getOwnerAddress();

        // check for success and failure
        assertThat(res.getCode()).isEqualTo(ExecutionResult.Code.SUCCESS);
        assertThat(res.getNrgLeft()).isEqualTo(expectedEnergyLeft);
        //assertEquals(newAddress, actualReturnedAddress);
    }

    @Test
    public void testInsufficientEnergy(){

        // initialize input parameters
        final long inputEnergy = 300L;
        final long expectedEnergyLeft = 0L;

        ECKey k = ECKeyFac.inst().create();
        DummyRepository repo = new DummyRepository();

        // create ANS contract
        AionNameServiceContract ansc = new AionNameServiceContract(repo, contractAddress1, Address.wrap(k.getAddress()));

        // setup the inputs
        ByteBuffer bb = ByteBuffer.allocate(34);
        bb.put((byte) 0x0)          // chainID
                .put((byte) 0x1)    // OPERATION HERE
                .put(newAddress1.toBytes(), 0, 32);

        byte[] payload = bb.array();
        ISignature signature = k.sign(payload);

        bb = ByteBuffer.allocate(34 + 96);
        bb.put(payload);
        bb.put(signature.toBytes());
        byte[] combined = bb.array();

        // execute ANS contract
        ExecutionResult res = ansc.execute(combined, inputEnergy);
        Address actualReturnedAddress = ansc.getResolverAddress();

        // check for success and failure
        assertThat(res.getCode()).isEqualTo(ExecutionResult.Code.OUT_OF_NRG);
        assertThat(res.getNrgLeft()).isEqualTo(expectedEnergyLeft);
        // since there is not enough energy, the contract failed to execute, resolverAddress is unchanged
        assertNull(actualReturnedAddress);
    }

}