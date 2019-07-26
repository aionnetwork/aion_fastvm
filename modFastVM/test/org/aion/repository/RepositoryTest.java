package org.aion.repository;

import java.math.BigInteger;
import org.aion.repository.AccountStateForTesting.VmType;
import org.aion.fastvm.FvmDataWord;
import org.aion.types.AionAddress;
import org.apache.commons.lang3.RandomUtils;
import org.junit.Assert;
import org.junit.Test;

public class RepositoryTest {

    @Test
    public void testRollback() {
        AionAddress address1 = randomAddress();
        AionAddress address2 = randomAddress();
        BigInteger balance = BigInteger.TEN;
        FvmDataWord key = FvmDataWord.fromInt(465);
        FvmDataWord value = FvmDataWord.fromInt(323);

        RepositoryForTesting repository = RepositoryForTesting.newRepository();
        repository.addToStorage(address1, key, value);
        repository.addToStorage(address2, key, value);
        repository.addBalance(address1, balance);
        repository.addBalance(address2, balance);

        repository.rollback();

        Assert.assertNull(repository.getStorageValue(address1, key));
        Assert.assertNull(repository.getStorageValue(address2, key));
        Assert.assertEquals(BigInteger.ZERO, repository.getBalance(address1));
        Assert.assertEquals(BigInteger.ZERO, repository.getBalance(address2));
    }

    @Test
    public void testRollbackInChildRepository() {
        AionAddress address = randomAddress();
        BigInteger balance = BigInteger.TEN;
        FvmDataWord key = FvmDataWord.fromInt(465);
        FvmDataWord value = FvmDataWord.fromInt(323);

        RepositoryForTesting repository = RepositoryForTesting.newRepository();
        RepositoryForTesting childRepository = repository.newChildRepository();

        repository.addToStorage(address, key, value);
        repository.addBalance(address, balance);
        childRepository.addToStorage(address, key, FvmDataWord.fromInt(23));
        childRepository.addBalance(address, balance);

        childRepository.rollback();
        childRepository.commit();

        Assert.assertEquals(value, repository.getStorageValue(address, key));
        Assert.assertEquals(balance, repository.getBalance(address));
    }

    @Test
    public void testAddToStorage() {
        AionAddress address = randomAddress();
        FvmDataWord key = FvmDataWord.fromInt(8);
        FvmDataWord value = FvmDataWord.fromInt(346);

        RepositoryForTesting repository = RepositoryForTesting.newRepository();

        repository.addToStorage(address, key, value);
        Assert.assertEquals(value, repository.getStorageValue(address, key));
    }

    @Test
    public void testAddToStorageInChildRepository() {
        AionAddress address = randomAddress();
        FvmDataWord key = FvmDataWord.fromInt(8);
        FvmDataWord value = FvmDataWord.fromInt(346);

        RepositoryForTesting repository = RepositoryForTesting.newRepository();
        RepositoryForTesting childRepository = repository.newChildRepository();

        repository.addToStorage(address, key, value);
        Assert.assertEquals(value, childRepository.getStorageValue(address, key));

        FvmDataWord value2 = FvmDataWord.fromInt(98);
        childRepository.addToStorage(address, key, value2);
        childRepository.commit();

        Assert.assertEquals(value2, repository.getStorageValue(address, key));
    }

    @Test
    public void testRemoveStorage() {
        AionAddress address = randomAddress();
        FvmDataWord key = FvmDataWord.fromInt(75);
        FvmDataWord value = FvmDataWord.fromInt(23);

        RepositoryForTesting repository = RepositoryForTesting.newRepository();

        repository.addToStorage(address, key, value);
        repository.removeFromStorage(address, key);
        Assert.assertNull(repository.getStorageValue(address, key));
    }

    @Test
    public void testRemoveFromStorageInChildRepository() {
        AionAddress address = randomAddress();
        FvmDataWord key = FvmDataWord.fromInt(75);
        FvmDataWord value = FvmDataWord.fromInt(23);

        RepositoryForTesting repository = RepositoryForTesting.newRepository();
        RepositoryForTesting childRepository = repository.newChildRepository();

        repository.addToStorage(address, key, value);
        childRepository.removeFromStorage(address, key);

        Assert.assertEquals(value, repository.getStorageValue(address, key));
        Assert.assertNull(childRepository.getStorageValue(address, key));

        childRepository.commit();
        Assert.assertNull(repository.getStorageValue(address, key));
    }

    @Test
    public void testRemoveRepositoryKeyInGrandchild() {
        AionAddress address = randomAddress();
        FvmDataWord key = FvmDataWord.fromInt(75);
        FvmDataWord value = FvmDataWord.fromInt(23);

        RepositoryForTesting repository = RepositoryForTesting.newRepository();
        RepositoryForTesting childRepository = repository.newChildRepository();
        RepositoryForTesting grandChildRepository = childRepository.newChildRepository();

        repository.addToStorage(address, key, value);
        grandChildRepository.removeFromStorage(address, key);

        Assert.assertEquals(value, repository.getStorageValue(address, key));
        Assert.assertEquals(value, childRepository.getStorageValue(address, key));

        grandChildRepository.commit();
        childRepository.commit();

        Assert.assertNull(repository.getStorageValue(address, key));
    }

    @Test
    public void testGetCode() {
        AionAddress address = randomAddress();
        byte[] code = RandomUtils.nextBytes(45);

        RepositoryForTesting repository = RepositoryForTesting.newRepository();
        Assert.assertNull(repository.getCode(address));

        repository.saveCode(address, code);
        Assert.assertArrayEquals(code, repository.getCode(address));
    }

    @Test
    public void testGetCodeInChildRepository() {
        AionAddress address = randomAddress();
        byte[] code = RandomUtils.nextBytes(45);

        RepositoryForTesting repository = RepositoryForTesting.newRepository();
        RepositoryForTesting childRepository = repository.newChildRepository();

        repository.saveCode(address, code);
        Assert.assertArrayEquals(code, childRepository.getCode(address));
    }

    @Test
    public void testOverwriteCodeInChildRepository() {
        AionAddress address = randomAddress();
        byte[] parentCode = RandomUtils.nextBytes(45);
        byte[] childCode = RandomUtils.nextBytes(98);

        RepositoryForTesting repository = RepositoryForTesting.newRepository();
        RepositoryForTesting childRepository = repository.newChildRepository();

        repository.saveCode(address, parentCode);
        childRepository.saveCode(address, childCode);

        Assert.assertArrayEquals(parentCode, repository.getCode(address));

        childRepository.commit();
        Assert.assertArrayEquals(childCode, repository.getCode(address));
    }

    @Test
    public void testHasAccountState() {
        AionAddress address = randomAddress();

        RepositoryForTesting repository = RepositoryForTesting.newRepository();
        Assert.assertFalse(repository.hasAccountState(address));

        FvmDataWord key = FvmDataWord.fromInt(6);
        FvmDataWord value = FvmDataWord.fromInt(98);

        // Ensure that having storage means you exist.
        RepositoryForTesting childRepository = repository.newChildRepository();
        Assert.assertFalse(childRepository.hasAccountState(address));
        childRepository.addToStorage(address, key, value);
        Assert.assertTrue(childRepository.hasAccountState(address));

        // Ensure that having state means you exist.
        childRepository = repository.newChildRepository();
        Assert.assertFalse(childRepository.hasAccountState(address));
        childRepository.addBalance(address, BigInteger.ONE);
        Assert.assertTrue(childRepository.hasAccountState(address));
    }

    @Test
    public void testCreateAccount() {
        AionAddress address = randomAddress();

        RepositoryForTesting repository = RepositoryForTesting.newRepository();
        repository.createAccount(address);
        Assert.assertTrue(repository.hasAccountState(address));
    }

    @Test
    public void testSetVmType() {
        AionAddress address = randomAddress();

        RepositoryForTesting repository = RepositoryForTesting.newRepository();
        repository.setVmType(address, VmType.FVM);
        Assert.assertEquals(VmType.FVM, repository.getVmType(address));
    }

    @Test
    public void testSetVmTypeInChildRepository() {
        AionAddress address = randomAddress();

        RepositoryForTesting repository = RepositoryForTesting.newRepository();
        RepositoryForTesting childRepository = repository.newChildRepository();

        repository.setVmType(address, VmType.FVM);
        childRepository.setVmType(address, VmType.PRECOMPILED);

        Assert.assertEquals(VmType.FVM, repository.getVmType(address));

        childRepository.commit();
        Assert.assertEquals(VmType.PRECOMPILED, repository.getVmType(address));
    }

    @Test
    public void testGetBalance() {
        AionAddress address = randomAddress();
        BigInteger balance = BigInteger.valueOf(2387);

        RepositoryForTesting repository = RepositoryForTesting.newRepository();
        Assert.assertEquals(BigInteger.ZERO, repository.getBalance(address));

        repository.addBalance(address, balance);
        Assert.assertEquals(balance, repository.getBalance(address));
    }

    @Test
    public void testGetBalanceInChildRepository() {
        AionAddress address = randomAddress();
        BigInteger parentBalance = BigInteger.valueOf(324);
        BigInteger childBalance = BigInteger.valueOf(2387);

        RepositoryForTesting repository = RepositoryForTesting.newRepository();
        RepositoryForTesting childRepository = repository.newChildRepository();

        repository.addBalance(address, parentBalance);
        childRepository.addBalance(address, childBalance);

        Assert.assertEquals(childBalance.add(parentBalance), childRepository.getBalance(address));
        Assert.assertEquals(parentBalance, repository.getBalance(address));

        childRepository.commit();
        Assert.assertEquals(parentBalance.add(childBalance), repository.getBalance(address));
    }

    @Test
    public void testAddBalanceInMultipleRepositories() {
        AionAddress address = randomAddress();
        BigInteger parentBalance = BigInteger.valueOf(324);
        BigInteger childBalance = BigInteger.valueOf(2387);
        BigInteger grandChildBalance = BigInteger.valueOf(4528);

        RepositoryForTesting repository = RepositoryForTesting.newRepository();
        RepositoryForTesting childRepository = repository.newChildRepository();
        RepositoryForTesting grandChildRepository = childRepository.newChildRepository();

        repository.addBalance(address, parentBalance);
        childRepository.addBalance(address, childBalance);
        grandChildRepository.addBalance(address, grandChildBalance);

        Assert.assertEquals(parentBalance, repository.getBalance(address));
        Assert.assertEquals(parentBalance.add(childBalance), childRepository.getBalance(address));
        Assert.assertEquals(parentBalance.add(childBalance).add(grandChildBalance), grandChildRepository.getBalance(address));
    }

    @Test
    public void testGetNonce() {
        AionAddress address = randomAddress();
        BigInteger nonce = BigInteger.valueOf(2387);

        RepositoryForTesting repository = RepositoryForTesting.newRepository();
        Assert.assertEquals(BigInteger.ZERO, repository.getNonce(address));

        repository.setNonce(address, nonce);
        Assert.assertEquals(nonce, repository.getNonce(address));
    }

    @Test
    public void testGetNonceInChildRepository() {
        AionAddress address = randomAddress();
        BigInteger parentNonce = BigInteger.valueOf(324);
        BigInteger childNonce = BigInteger.valueOf(2387);

        RepositoryForTesting repository = RepositoryForTesting.newRepository();
        RepositoryForTesting childRepository = repository.newChildRepository();

        repository.setNonce(address, parentNonce);
        childRepository.setNonce(address, childNonce);

        Assert.assertEquals(childNonce, childRepository.getNonce(address));
        Assert.assertEquals(parentNonce, repository.getNonce(address));

        childRepository.commit();
        Assert.assertEquals(childNonce, repository.getNonce(address));
    }

    @Test
    public void testIncrementNonceInMultipleRepositories() {
        AionAddress address = randomAddress();
        BigInteger parentNonce = BigInteger.valueOf(465);

        RepositoryForTesting repository = RepositoryForTesting.newRepository();
        RepositoryForTesting childRepository = repository.newChildRepository();
        RepositoryForTesting grandChildRepository = childRepository.newChildRepository();

        repository.setNonce(address, parentNonce);
        childRepository.incrementNonce(address);
        grandChildRepository.incrementNonce(address);

        Assert.assertEquals(parentNonce, repository.getNonce(address));
        Assert.assertEquals(parentNonce.add(BigInteger.ONE), childRepository.getNonce(address));
        Assert.assertEquals(parentNonce.add(BigInteger.TWO), grandChildRepository.getNonce(address));
    }

    private static AionAddress randomAddress() {
        byte[] bytes = RandomUtils.nextBytes(AionAddress.LENGTH);
        bytes[0] = (byte) 0xa0;
        return new AionAddress(bytes);
    }
}
