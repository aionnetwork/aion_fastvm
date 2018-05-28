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
import org.aion.mcf.vm.types.DataWord;
import org.aion.vm.ExecutionResult;
import org.junit.Before;
import org.junit.Test;

import java.nio.ByteBuffer;

import static com.google.common.truth.Truth.assertThat;
import static junit.framework.TestCase.assertEquals;
import static org.aion.crypto.HashUtil.blake128;

public class AionNameServiceContractTest {

    private final static String RESOLVER_HASH = "ResolverHash";
    private final static String OWNER_HASH = "OwnerHash";
    private final static String TTL_HASH = "TTLHash";


    private String domainName1 = "aion";
    private String domainName2 = "aion.aion"; //subdomain of domainName1
    private String domainName3 = "aion.bion"; //subdomain of domainName1
    private String domainName4 = "aion.bion.cion"; //subdomain of domainName1 and domainName3
    private String domainName5 = "aion.bion.dion"; //subdomain of domainName1 and domainName3
    private String notSubdomain = "bion.bion.dion";  //not a subdomain of domainName1

    private Address emptyAddress = Address.wrap("0000000000000000000000000000000000000000000000000000000000000000");
    private Address domainAddress1 = Address.wrap("1111111111111111111111111111111101010101010101010101010101010101");
    private Address domainAddress2 = Address.wrap("2222222222222222222222222222222202020202020202020202020202020202");
    private Address domainAddress3 = Address.wrap("3333333333333333333333333333333303030303030303030303030303030303");
    private Address domainAddress4 = Address.wrap("4444444444444444444444444444444404040404040404040404040404040404");
    private Address domainAddress5 = Address.wrap("5555555555555555555555555555555505050505050050505050505050505050");

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


    // try to create errors 
    @Test
    public void testInvalidValues(){
        final long inputEnergy = 5000L;
        ECKey k = ECKeyFac.inst().create();
        ECKey k2 = ECKeyFac.inst().create();
        ECKey k3 = ECKeyFac.inst().create();
        DummyRepository repo = populateRepo();

        AionNameServiceContract ansc = new AionNameServiceContract(repo, domainAddress1, Address.wrap(k.getAddress()), domainName1);
        AionNameServiceContract ansc2 = new AionNameServiceContract(repo, domainAddress1, Address.wrap(k2.getAddress()), domainName2);


        byte[] combined = setupInputs(newAddress1, (byte)0x0, (byte)0x1, k);
        ExecutionResult res = ansc.execute(combined, inputEnergy);
        assertThat(res.getCode()).isEqualTo(ExecutionResult.Code.SUCCESS);
        assertThat(res.getNrgLeft()).isEqualTo(4000);

        byte[] combined2 = setupInputs(newAddress1, (byte)0x12, (byte)0x1, k2);
        ExecutionResult res2 = ansc2.execute(combined2, inputEnergy);
        assertThat(res2.getCode()).isEqualTo(ExecutionResult.Code.SUCCESS);
        assertThat(res2.getNrgLeft()).isEqualTo(4000);
    }

    /**
     * Based of multiple domains under Aion, mass testing of the AionNameService Contract
     *
     * domains:
     *      aion:                                   aion.aion:
     *          owner:                                  owner:
     *          resolver:                               resolver:
     *          ttl:                                    ttl:
     *
     *      aion.aion.bion:                         aion.aion.cion:
     *          owner:                                  owner:
     *          resolver:                               resolver:
     *          ttl:                                    ttl:
     *
     */
    @Test
    public void bigTest2(){
        final long inputEnergy = 5000L;
        ECKey k = ECKeyFac.inst().create();
        ECKey k2 = ECKeyFac.inst().create();
        ECKey k3 = ECKeyFac.inst().create();
        ECKey k4 = ECKeyFac.inst().create();
        ECKey k5 = ECKeyFac.inst().create();

        // populate the storage database
        DummyRepository repo = populateRepo();

        // create ANS contracts, contract1  will be valid parent domain, contract2 will not be
        AionNameServiceContract ansc1 = new AionNameServiceContract(repo, domainAddress1, Address.wrap(k.getAddress()), domainName1);
        AionNameServiceContract ansc2 = new AionNameServiceContract(repo, domainAddress2, Address.wrap(k2.getAddress()), domainName2);
        AionNameServiceContract ansc3 = new AionNameServiceContract(repo, domainAddress3, Address.wrap(k3.getAddress()), domainName3);

        byte[] combined2 = setupInputs(newAddress8, (byte)0x0,(byte)0x4, k2, domainAddress4);
        ExecutionResult res2 = ansc2.execute(combined2, inputEnergy, domainName4);

        byte[] combined3 = setupInputs(newAddress8, (byte)0x0,(byte)0x4, k3, domainAddress5);
        ExecutionResult res3 = ansc3.execute(combined3, inputEnergy, domainName5);

        byte[] combined4 = setupInputs(newAddress7, (byte)0x0, (byte)0x1, k3);
        ExecutionResult res4 = ansc3.execute(combined4, inputEnergy);

        assertThat(res2.getCode()).isEqualTo(ExecutionResult.Code.INTERNAL_ERROR);
        assertThat(res2.getNrgLeft()).isEqualTo(0);
        assertThat(res3.getCode()).isEqualTo(ExecutionResult.Code.SUCCESS);
        assertThat(res3.getNrgLeft()).isEqualTo(3000);
        assertThat(res4.getCode()).isEqualTo(ExecutionResult.Code.SUCCESS);
        assertThat(res4.getNrgLeft()).isEqualTo(4000);

        AionNameServiceContract ansc4 = new AionNameServiceContract(repo, domainAddress4, Address.wrap(k4.getAddress()), domainName4);
        AionNameServiceContract ansc5 = new AionNameServiceContract(repo, domainAddress5, Address.wrap(k5.getAddress()), domainName5);

        // storage checks
        assertEquals(newAddress6, ansc4.getOwnerAddress());
        assertEquals(newAddress8, ansc5.getOwnerAddress());

        byte[] combined = setupInputs(newAddress6, (byte)0x0, (byte)0x4, k, domainAddress5);
        ExecutionResult res = ansc1.execute(combined, inputEnergy, domainName5);

        assertThat(res.getCode()).isEqualTo(ExecutionResult.Code.SUCCESS);
        assertThat(res.getNrgLeft()).isEqualTo(3000);
        assertEquals(newAddress6, ansc5.getOwnerAddress());
    }

    /**
     * Test functions of ANS with multiple instances of ans in the database
     *
     * Test the transfer of ownership
     *
     * Initial set up:
     * Storage:    contract1: domainAddress1
     *                      resolver - newAddress1
     *                      ttl - newAddress2
     *                      owner - newAddress3
     *             contract2: domainAddress2
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
        AionNameServiceContract ansc = new AionNameServiceContract(repo, domainAddress1, Address.wrap(k.getAddress()), domainName1);
        AionNameServiceContract ansc2 = new AionNameServiceContract(repo, domainAddress2, Address.wrap(k.getAddress()), domainName2);

        // setup the inputs 1: set resolver-----------------------------------------------------------------------------
        byte[] combined = setupInputs(newAddress1, (byte)0x0,(byte)0x1, k);

        // setup the inputs 2: set TTL
        byte[] combined2 = setupInputs(newAddress2, (byte)0x0,(byte)0x2, k);

        // setup the inputs 3: Transfer(set) owner
        byte[] combined3 = setupInputs(newAddress3, (byte)0x0,(byte)0x3, k);

        // setup the inputs 4: set Resolver for ansc2
        byte[] combined4 = setupInputs(newAddress4, (byte)0x0,(byte)0x1, k);

        // setup the inputs 5: Transfer(set) owner
        byte[] combined5 = setupInputs(newAddress5, (byte)0x0,(byte)0x3, k);

        // execute contract
        ExecutionResult res1 = ansc.execute(combined, inputEnergy);
        ExecutionResult res2 = ansc.execute(combined2, inputEnergy);
        ExecutionResult res3 = ansc.execute(combined3, inputEnergy);

        // basic checks ------------------------------------------------------------------------------------------------
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

        // contract1 transfer owner, and checks if transfer is correctly executed
        ExecutionResult res5 = ansc.execute(combined5, inputEnergy);
        assertThat(res5.getCode()).isEqualTo(ExecutionResult.Code.SUCCESS);
        assertThat(res5.getNrgLeft()).isEqualTo(3000);
        assertEquals(newAddress5, ansc.getOwnerAddress());
    }

    /**
     * Set contract2 and contract3 as a subdomain of contract1
     * Through contract1, change owner of contract 2 from Address4 to Address 5
     *
     * Initial set up:                                  After Transfer:
     * Storage:    contract1: domainAddress1          Storage:    contract1: domainAddress1
     *                      resolver - newAddress1                          resolver - newAddress1
     *                      ttl - newAddress2                               ttl - newAddress2
     *                      owner - newAddress3                             owner - newAddress3
     *             contract2: domainAddress2                      contract2: domainAddress2
     *                      resolver -                                      resolver -
     *                      ttl -                                           ttl -
     *                      owner - newAddress4                             owner - newAddress5
     *             contract3: domainAddress3 (ttc)                contract3: domainAddress3 (ttc)
     *                      resolver -                                      resolver -
     *                      ttl -                                           ttl -
     *                      owner - newAddress6                             owner - newAddress7
     *
     */
    @Test
    public void testTransferSubdomainOwnership(){
        // initialize input parameters
        final long inputEnergy = 5000L;
        final long expectedEnergyLeft = 3000L;

        ECKey k = ECKeyFac.inst().create();
        DummyRepository repo = new DummyRepository();
        byte[] resolverHash1 = blake128(RESOLVER_HASH.getBytes());
        byte[] resolverHash2 = blake128(resolverHash1);

        byte[] TTLHash1 = blake128(TTL_HASH.getBytes());
        byte[] TTLHash2 = blake128(TTLHash1);

        byte[] ownerHash1 = blake128(OWNER_HASH.getBytes());
        byte[] ownerHash2 = blake128(ownerHash1);

        AionNameServiceContract ansc = new AionNameServiceContract(repo, domainAddress1, Address.wrap(k.getAddress()), domainName1);
        AionNameServiceContract ansc2 = new AionNameServiceContract(repo, domainAddress2, Address.wrap(k.getAddress()), domainName2);

        repo.addStorageRow(domainAddress1, new DataWord(resolverHash1), new DataWord("10000000000000000000000000000000"));
        repo.addStorageRow(domainAddress1, new DataWord(resolverHash2), new DataWord("00000000000000000000000000000001"));

        repo.addStorageRow(domainAddress1, new DataWord(TTLHash1), new DataWord("01000000000000000000000000000000"));
        repo.addStorageRow(domainAddress1, new DataWord(TTLHash2), new DataWord("00000000000000000000000000000010"));

        repo.addStorageRow(domainAddress1, new DataWord(ownerHash1), new DataWord("00100000000000000000000000000000"));
        repo.addStorageRow(domainAddress1, new DataWord(ownerHash2), new DataWord("00000000000000000000000000000100"));

        repo.addStorageRow(domainAddress2, new DataWord(ownerHash1), new DataWord("00010000000000000000000000000000"));
        repo.addStorageRow(domainAddress2, new DataWord(ownerHash2), new DataWord("00000000000000000000000000001000"));

        byte[] combined = setupInputs(newAddress5, (byte)0x0,(byte)0x4, k);
        byte[] addSubdomainAddress = new byte[162];
        System.arraycopy(combined, 0, addSubdomainAddress, 0, 130);
        System.arraycopy(domainAddress2.toBytes(), 0, addSubdomainAddress, 130, 32);

        ExecutionResult res = ansc.execute(addSubdomainAddress, inputEnergy, domainName2);
        Address actualReturnedAddress = ansc2.getOwnerAddress(Address.wrap(combineTwoBytes(ownerHash1,ownerHash2)));

        // check for success and failure
        assertThat(res.getCode()).isEqualTo(ExecutionResult.Code.SUCCESS);
        assertThat(res.getNrgLeft()).isEqualTo(expectedEnergyLeft);
        assertEquals(newAddress5, actualReturnedAddress);
    }

    @Test
    public void testSetResolver(){

        // initialize input parameters
        final long inputEnergy = 5000L;
        final long expectedEnergyLeft = 4000L;

        ECKey k = ECKeyFac.inst().create();
        DummyRepository repo = new DummyRepository();

        // create ANS contract
        AionNameServiceContract ansc = new AionNameServiceContract(repo, domainAddress1, Address.wrap(k.getAddress()), domainName1);

        byte[] combined = setupInputs(newAddress1, (byte)0x0,(byte)0x1, k);

        // execute ANS contract
        ExecutionResult res = ansc.execute(combined, inputEnergy);
        Address actualReturnedAddress = ansc.getResolverAddress();

        // check for success and failure
        assertThat(res.getCode()).isEqualTo(ExecutionResult.Code.SUCCESS);
        assertThat(res.getNrgLeft()).isEqualTo(expectedEnergyLeft);
        assertEquals(newAddress1, actualReturnedAddress);
    }

    @Test
    public void testNotASubdomain(){
        // initialize input parameters
        final long inputEnergy = 5000L;
        final long expectedEnergyLeft = 0;

        ECKey k = ECKeyFac.inst().create();
        DummyRepository repo = new DummyRepository();

        byte[] ownerHash1 = blake128(OWNER_HASH.getBytes());
        byte[] ownerHash2 = blake128(ownerHash1);

        AionNameServiceContract ansc = new AionNameServiceContract(repo, domainAddress1, Address.wrap(k.getAddress()), domainName1);
        AionNameServiceContract ansc2 = new AionNameServiceContract(repo, domainAddress2, Address.wrap(k.getAddress()), notSubdomain);

        repo.addStorageRow(domainAddress1, new DataWord(ownerHash1), new DataWord("00100000000000000000000000000000"));
        repo.addStorageRow(domainAddress1, new DataWord(ownerHash2), new DataWord("00000000000000000000000000000100"));

        repo.addStorageRow(domainAddress2, new DataWord(ownerHash1), new DataWord("00010000000000000000000000000000"));
        repo.addStorageRow(domainAddress2, new DataWord(ownerHash2), new DataWord("00000000000000000000000000001000"));

        byte[] combined = setupInputs(newAddress5, (byte)0x0,(byte)0x4, k);
        byte[] addSubdomainAddress = new byte[162];
        System.arraycopy(combined, 0, addSubdomainAddress, 0, 130);
        System.arraycopy(domainAddress2.toBytes(), 0, addSubdomainAddress, 130, 32);

        ExecutionResult res = ansc.execute(addSubdomainAddress, inputEnergy, notSubdomain);
        Address actualReturnedAddress = ansc2.getOwnerAddress(Address.wrap(combineTwoBytes(ownerHash1,ownerHash2)));

        // check for success and failure
        assertThat(res.getCode()).isEqualTo(ExecutionResult.Code.INTERNAL_ERROR);
        assertThat(res.getNrgLeft()).isEqualTo(expectedEnergyLeft);
        assertEquals(newAddress4, actualReturnedAddress);

    }

    @Test
    public void incorrectInputLength(){
        // initialize input parameters
        final long inputEnergy = 5000L;
        final long expectedEnergyLeft = 0L;

        ECKey k = ECKeyFac.inst().create();
        DummyRepository repo = new DummyRepository();

        // create ANS contract
        AionNameServiceContract ansc = new AionNameServiceContract(repo, domainAddress1, Address.wrap(k.getAddress()), domainName1);

        byte[] combined = setupInputs(newAddress1, (byte)0x0,(byte)0x1, k);
        byte[] wrongLength = new byte[130 - 1];
        System.arraycopy(combined, 0, wrongLength, 0, 130 - 1);

        // execute ANS contract
        ExecutionResult res = ansc.execute(wrongLength, inputEnergy);
        Address actualReturnedAddress = ansc.getResolverAddress();

        // check for success and failure
        assertThat(res.getCode()).isEqualTo(ExecutionResult.Code.INTERNAL_ERROR);
        assertThat(res.getNrgLeft()).isEqualTo(expectedEnergyLeft);
        assertEquals(emptyAddress, actualReturnedAddress);
    }

    @Test
    public void testIncorrectSignature(){

        // initialize input parameters
        final long inputEnergy = 5000L;
        final long expectedEnergyLeft = 0L;

        ECKey k = ECKeyFac.inst().create();
        DummyRepository repo = new DummyRepository();

        // create ANS contract
        AionNameServiceContract ansc = new AionNameServiceContract(repo, domainAddress1, Address.wrap(k.getAddress()), domainName1);

        byte[] combined = setupInputs(newAddress1, (byte)0x0,(byte)0x1, k);
        //byte[] combined2 = setupInputs(newAddress1, (byte)0x0,(byte)0x1, k);

        // modify the signature in the 110th byte (arbitrarily)
        combined[110] = (byte) (combined[110] + 1);
        for(int i = 34; i < 130; i++){
            combined[i] = (byte)0;
        }

        // execute ANS contract
        ExecutionResult res = ansc.execute(combined, inputEnergy);
        Address actualReturnedAddress = ansc.getResolverAddress();
        //ExecutionResult res2 = ansc.execute(combined2, inputEnergy);
       // Address actualReturnedAddress2 = ansc.getResolverAddress();

        // check for success and failure
        assertThat(res.getCode()).isEqualTo(ExecutionResult.Code.INTERNAL_ERROR);
        assertThat(res.getNrgLeft()).isEqualTo(expectedEnergyLeft);
        // since the signature is incorrect, contract is not modified
        assertEquals(emptyAddress, actualReturnedAddress);

        // check for success and failure
        //assertThat(res2.getCode()).isEqualTo(ExecutionResult.Code.INTERNAL_ERROR);
        //assertThat(res2.getNrgLeft()).isEqualTo(expectedEnergyLeft);
        // since the signature is incorrect, contract is not modified
        //assertNull(actualReturnedAddress2);
    }

    @Test
    public void testUnsupportedOperation(){
        // initialize input parameters
        final long inputEnergy = 5000L;
        final long expectedEnergyLeft = 5000;

        ECKey k = ECKeyFac.inst().create();
        DummyRepository repo = new DummyRepository();

        // create ANS contract
        AionNameServiceContract ansc = new AionNameServiceContract(repo, domainAddress1, Address.wrap(k.getAddress()), domainName1);

        byte[] combined = setupInputs(newAddress1, (byte)0x0,(byte)0x6, k); //put (byte) 6 into input as the invalid

        // execute ANS contract
        ExecutionResult res = ansc.execute(combined, inputEnergy);
        Address actualReturnedAddress = ansc.getResolverAddress();

        // check for success and failure
        assertThat(res.getCode()).isEqualTo(ExecutionResult.Code.INTERNAL_ERROR);
        assertThat(res.getNrgLeft()).isEqualTo(expectedEnergyLeft);
        assertEquals(emptyAddress, actualReturnedAddress);
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
        AionNameServiceContract ansc = new AionNameServiceContract(repo, domainAddress1, Address.wrap(k.getAddress()), domainName1);

        byte[] combined = setupInputs(newAddress1, (byte)0x0,(byte)0x1, notk);

        // execute ANS contract
        ExecutionResult res = ansc.execute(combined, inputEnergy);
        Address actualReturnedAddress = ansc.getResolverAddress();

        // check for success and failure
        assertThat(res.getCode()).isEqualTo(ExecutionResult.Code.INTERNAL_ERROR);
        assertThat(res.getNrgLeft()).isEqualTo(expectedEnergyLeft);
        // since the signature is incorrect, contract is not modified
        assertEquals(emptyAddress, actualReturnedAddress);
    }

    @Test
    public void testTransferOwnership(){

        // initialize input parameters
        final long inputEnergy = 5000L;
        final long expectedEnergyLeft = 3000L;

        ECKey k = ECKeyFac.inst().create();
        DummyRepository repo = new DummyRepository();

        // create ANS contract
        AionNameServiceContract ansc = new AionNameServiceContract(repo, domainAddress1, Address.wrap(k.getAddress()), domainName1);

        byte[] combined = setupInputs(newAddress1, (byte)0x0,(byte)0x3, k);

        // execute ANS contract
        ExecutionResult res = ansc.execute(combined, inputEnergy);
        Address actualReturnedAddress = ansc.getOwnerAddress();

        // check for success and failure
        assertThat(res.getCode()).isEqualTo(ExecutionResult.Code.SUCCESS);
        assertThat(res.getNrgLeft()).isEqualTo(expectedEnergyLeft);
        assertEquals(newAddress1, actualReturnedAddress);
    }

    @Test
    public void testInsufficientEnergy(){

        // initialize input parameters
        final long inputEnergy = 300L;
        final long expectedEnergyLeft = 0L;

        ECKey k = ECKeyFac.inst().create();
        DummyRepository repo = new DummyRepository();

        // create ANS contract
        AionNameServiceContract ansc = new AionNameServiceContract(repo, domainAddress1, Address.wrap(k.getAddress()), domainName1);
        AionNameServiceContract ansc2 = new AionNameServiceContract(repo, domainAddress2, Address.wrap(k.getAddress()) , domainName2);

        byte[] combined = setupInputs(newAddress1, (byte)0x0,(byte)0x1, k);
        byte[] combined2 = setupInputs(newAddress2, (byte)0x0,(byte)0x2, k);
        byte[] combined3 = setupInputs(newAddress3, (byte)0x0,(byte)0x3, k);
        byte[] combined4 = setupInputs(newAddress3, (byte)0x0,(byte)0x4, k);
        byte[] addSubdomainAddress = new byte[162];
        System.arraycopy(combined4, 0, addSubdomainAddress, 0, 130);
        System.arraycopy(domainAddress2.toBytes(), 0, addSubdomainAddress, 130, 32);

        // execute ANS contract
        ExecutionResult res = ansc.execute(combined, inputEnergy);
        ExecutionResult res2 = ansc.execute(combined2, inputEnergy);
        ExecutionResult res3 = ansc.execute(combined3, inputEnergy);
        ExecutionResult res4 = ansc.execute(addSubdomainAddress, inputEnergy, domainName2);

        Address actualReturnedAddress = ansc.getResolverAddress();
        Address actualReturnedAddress2 = ansc.getTTL();
        Address actualReturnedAddress3 = ansc.getOwnerAddress();
        Address actualReturnedAddress4 = ansc2.getOwnerAddress();

        // check for success and failure
        assertThat(res.getCode()).isEqualTo(ExecutionResult.Code.OUT_OF_NRG);
        assertThat(res.getNrgLeft()).isEqualTo(expectedEnergyLeft);
        // since there is not enough energy, the contract failed to execute, resolverAddress is unchanged
        assertEquals(emptyAddress, actualReturnedAddress);

        // check for success and failure
        assertThat(res2.getCode()).isEqualTo(ExecutionResult.Code.OUT_OF_NRG);
        assertThat(res2.getNrgLeft()).isEqualTo(expectedEnergyLeft);
        // since there is not enough energy, the contract failed to execute, resolverAddress is unchanged
        assertEquals(emptyAddress, actualReturnedAddress2);

        // check for success and failure
        assertThat(res3.getCode()).isEqualTo(ExecutionResult.Code.OUT_OF_NRG);
        assertThat(res3.getNrgLeft()).isEqualTo(expectedEnergyLeft);
        // since there is not enough energy, the contract failed to execute, resolverAddress is unchanged
        assertEquals(emptyAddress, actualReturnedAddress3);

        // check for success and failure
        assertThat(res4.getCode()).isEqualTo(ExecutionResult.Code.OUT_OF_NRG);
        assertThat(res4.getNrgLeft()).isEqualTo(expectedEnergyLeft);
        // since there is not enough energy, the contract failed to execute, resolverAddress is unchanged
        assertEquals(emptyAddress, actualReturnedAddress4);
    }

    @Test
    public void testSubdomainDoesNotExist(){

        // initialize input parameters
        final long inputEnergy = 5000L;
        final long expectedEnergyLeft = 3000L;
        final long expectedEnergyLeft2 = 0L;

        ECKey k = ECKeyFac.inst().create();
        DummyRepository repo = new DummyRepository();

        // create ANS contract
        AionNameServiceContract ansc = new AionNameServiceContract(repo, domainAddress1, Address.wrap(k.getAddress()) , domainName1);

        byte[] combined = setupInputs(newAddress1, (byte)0x0,(byte)0x3, k);
        byte[] combined2 = setupInputs(newAddress2, (byte)0x0,(byte)0x4, k);
        byte[] addSubdomainAddress = new byte[162];
        System.arraycopy(combined2, 0, addSubdomainAddress, 0, 130);
        System.arraycopy(domainAddress2.toBytes(), 0, addSubdomainAddress, 130, 32);

        ExecutionResult res = ansc.execute(combined, inputEnergy);
        ExecutionResult res2 = ansc.execute(addSubdomainAddress, inputEnergy);

        // check for success and failure
        assertThat(res.getCode()).isEqualTo(ExecutionResult.Code.SUCCESS);
        assertThat(res.getNrgLeft()).isEqualTo(expectedEnergyLeft);
        assertThat(res2.getCode()).isEqualTo(ExecutionResult.Code.INTERNAL_ERROR);
        assertThat(res2.getNrgLeft()).isEqualTo(expectedEnergyLeft2);
    }

    /**
    @Test
    public void nullSignature(){
     // initialize input parameters
     final long inputEnergy = 5000L;
     final long expectedEnergyLeft = 5000L;


     ECKey k = ECKeyFac.inst().create();
     DummyRepository repo = new DummyRepository();

     // create ANS contract
     AionNameServiceContract ansc  = new AionNameServiceContract(repo, domainAddress1, Address.wrap(k.getAddress()));

     ByteBuffer bb = ByteBuffer.allocate(130);
     bb.put((byte) 0x0)          // chainID
     .put((byte) 0x1)    // OPERATION HERE
     .put(newAddress1.toBytes(), 0, 32);

     byte[] combined = bb.array();

     // execute ANS contract
     ExecutionResult res = ansc.execute(combined, inputEnergy);
     Address actualReturnedAddress = ansc.getResolverAddress();

     // check for success and failure
     assertThat(res.getCode()).isEqualTo(ExecutionResult.Code.INTERNAL_ERROR);
     assertThat(res.getNrgLeft()).isEqualTo(expectedEnergyLeft);
     }
    */

    /**
     * Helper functions for setup, conversion, and storage
     */

    private byte[] setupInputs(Address newAddress, byte id, byte operation, ECKey k){
        ByteBuffer bb = ByteBuffer.allocate(34);
        bb.put(id)          // chainID
                .put(operation)    // OPERATION HERE
                .put(newAddress.toBytes(), 0, 32);

        byte[] payload = bb.array();
        ISignature signature = k.sign(payload);

        bb = ByteBuffer.allocate(34 + 96);
        bb.put(payload);
        bb.put(signature.toBytes());
        return bb.array();
    }

    private byte[] setupInputs(Address newAddress, byte id, byte operation, ECKey k, Address subdomainAddress){
        ByteBuffer bb = ByteBuffer.allocate(34);
        bb.put(id)          // chainID
                .put(operation)    // OPERATION HERE
                .put(newAddress.toBytes(), 0, 32);

        byte[] payload = bb.array();
        ISignature signature = k.sign(payload);

        bb = ByteBuffer.allocate(34 + 96 + 32);
        bb.put(payload);
        bb.put(signature.toBytes());
        bb.put(subdomainAddress.toBytes());
        return bb.array();
    }

    private byte[] combineTwoBytes(byte[] byte1, byte[] byte2){
        byte[] combined = new byte[32];
        System.arraycopy(byte1, 0, combined, 0, 16);
        System.arraycopy(byte2, 0, combined, 16, 16);
        return combined;
    }

    /**
     *  put some data into the database for testing
     *
     *  aion
     *  aion.aion
     *  aion.bion
     *  aion.bion.cion
     *  aion.bion.dion
     *
     */
    private DummyRepository populateRepo(){
        DummyRepository repo =  new DummyRepository();

        byte[] resolverHash1 = blake128(RESOLVER_HASH.getBytes());
        byte[] resolverHash2 = blake128(resolverHash1);

        byte[] TTLHash1 = blake128(TTL_HASH.getBytes());
        byte[] TTLHash2 = blake128(TTLHash1);

        byte[] ownerHash1 = blake128(OWNER_HASH.getBytes());
        byte[] ownerHash2 = blake128(ownerHash1);

        storeValueToRepo(repo, domainAddress1, resolverHash1, resolverHash2, newAddress1);
        storeValueToRepo(repo, domainAddress1, TTLHash1, TTLHash2, newAddress2);
        storeValueToRepo(repo, domainAddress1, ownerHash1, ownerHash2, newAddress3);

        storeValueToRepo(repo, domainAddress2, ownerHash1, ownerHash2, newAddress4);
        storeValueToRepo(repo, domainAddress3, ownerHash1, ownerHash2, newAddress5);
        storeValueToRepo(repo, domainAddress4, ownerHash1, ownerHash2, newAddress6);
        storeValueToRepo(repo, domainAddress5, ownerHash1, ownerHash2, newAddress7);
        return repo;
    }

    private void storeValueToRepo(DummyRepository repo, Address domainAddress, byte[] hash1, byte[] hash2, Address addr){
        byte[] combined = addr.toBytes();
        byte[] value1 = new byte[16];
        byte[] value2 = new byte[16];
        System.arraycopy(combined, 0, value1, 0, 16);
        System.arraycopy(combined, 16, value2, 0, 16);
        storeValueToRepo(repo, domainAddress, hash1, hash2, value1, value2);
    }

    private void storeValueToRepo(DummyRepository repo, Address domainAddress, byte[] hash1, byte[] hash2, byte[] value1, byte[] value2){
        repo.addStorageRow(domainAddress, new DataWord(hash1), new DataWord(value1));
        repo.addStorageRow(domainAddress, new DataWord(hash2), new DataWord(value2));
    }
}
