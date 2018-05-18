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

    private Address contractAddress = Address.wrap("0000000000000000000000000000000000000000000000000000000000000100");
    private Address domainAddress = Address.wrap("1111100000000000000000000000000000000000000000000000000011111100");
    private Address resolverAddress = Address.wrap("0000000000000000000000000000000000000000000000000000000011111100");
    private Address newAddress = Address.wrap("1010101010101001010101010101010101001010101010101011010100101010");

    @Before
    public void setup(){}

    @Test
    public void testSetResolver(){

        // initialize input parameters
        final long inputEnergy = 5000L;
        final long expectedEnergyLeft = 4000L;

        ECKey k = ECKeyFac.inst().create();
        DummyRepository repo = new DummyRepository();

        // create ANS contract
        AionNameServiceContract ansc = new AionNameServiceContract(repo, contractAddress, Address.wrap(k.getAddress()));

        // setup the inputs
        ByteBuffer bb = ByteBuffer.allocate(34);
        bb.put((byte) 0x0)          // chainID
                .put((byte) 0x1)    // OPERATION HERE
                .put(newAddress.toBytes(), 0, 32);

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
        assertEquals(actualReturnedAddress, newAddress);
    }


    @Test
    public void testIncorrectSignature(){

        // initialize input parameters
        final long inputEnergy = 5000L;
        final long expectedEnergyLeft = 0L;

        ECKey k = ECKeyFac.inst().create();
        DummyRepository repo = new DummyRepository();

        // create ANS contract
        AionNameServiceContract ansc = new AionNameServiceContract(repo, contractAddress, Address.wrap(k.getAddress()));

        // setup the inputs
        ByteBuffer bb = ByteBuffer.allocate(34);
        bb.put((byte) 0x0)          // chainID
                .put((byte) 0x1)    // OPERATION HERE
                .put(newAddress.toBytes(), 0, 32);

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

    /**
    @Test
    public void testIncorrectPublicKey(){

        // initialize input parameters
        final long inputEnergy = 5000L;
        final long expectedEnergyLeft = 0L;

        ECKey k = ECKeyFac.inst().create();
        ECKey notk = ECKeyFac.inst().create();

        DummyRepository repo = new DummyRepository();

        // create ANS contract
        AionNameServiceContract ansc = new AionNameServiceContract(repo, contractAddress, Address.wrap(k.getAddress()));

        // setup the inputs
        ByteBuffer bb = ByteBuffer.allocate(34);
        bb.put((byte) 0x0)          // chainID
                .put((byte) 0x1)    // OPERATION HERE
                .put(newAddress.toBytes(), 0, 32);

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
        //assertEquals(actualReturnedAddress, resolverAddress);
    }

    @Test
    public void testTransferOwnership(){

        // initialize input parameters
        final long inputEnergy = 5000L;
        final long expectedEnergyLeft = 3000L;

        ECKey k = ECKeyFac.inst().create();
        DummyRepository repo = new DummyRepository();

        // create ANS contract
        AionNameServiceContract ansc = new AionNameServiceContract(repo, contractAddress, Address.wrap(k.getAddress()),
                domainAddress, resolverAddress);

        // setup the inputs
        ByteBuffer bb = ByteBuffer.allocate(34);
        bb.put((byte) 0x0)          // chainID
                .put((byte) 0x3)    // OPERATION HERE
                .put(newAddress.toBytes(), 0, 32);

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
        assertEquals(actualReturnedAddress, newAddress);
    }

    @Test
    public void testInsufficientEnergy(){

        // initialize input parameters
        final long inputEnergy = 300L;
        final long expectedEnergyLeft = 0L;

        ECKey k = ECKeyFac.inst().create();
        DummyRepository repo = new DummyRepository();

        // create ANS contract
        AionNameServiceContract ansc = new AionNameServiceContract(repo, contractAddress, Address.wrap(k.getAddress()),
                domainAddress, resolverAddress);

        // setup the inputs
        ByteBuffer bb = ByteBuffer.allocate(34);
        bb.put((byte) 0x0)          // chainID
                .put((byte) 0x1)    // OPERATION HERE
                .put(newAddress.toBytes(), 0, 32);

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
        assertEquals(actualReturnedAddress, resolverAddress);
    }

    */

}