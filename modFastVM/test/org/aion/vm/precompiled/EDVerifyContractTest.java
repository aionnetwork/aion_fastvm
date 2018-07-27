package org.aion.vm.precompiled;

import org.aion.base.util.Hex;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.aion.crypto.HashUtil;
import org.aion.crypto.ISignature;
import org.aion.crypto.ed25519.ECKeyEd25519;
import org.aion.vm.ExecutionResult;
import org.aion.vm.PrecompiledContracts;
import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;

public class EDVerifyContractTest {

    /**
     *  This is a sanity test to make sure the signing of a message and verifying the signatujre works correctly
     * */
    @Test
    public void shouldCorrectlyVerifyED25519Signature() {
        ECKeyFac.setType(ECKeyFac.ECKeyType.ED25519);
        ECKey ecKey = ECKeyFac.inst().create();

        byte[] pubKey = ecKey.getPubKey();

        byte[] data = "Our first test in AION1234567890".getBytes();

        HashUtil.setType(HashUtil.H512Type.KECCAK_512);
        byte[] hashedMessage = HashUtil.h512(data);

        ISignature signature = ecKey.sign(hashedMessage);

        boolean verify = ECKeyEd25519.verify(hashedMessage, signature.getSignature(), pubKey);

        assertThat(verify).isEqualTo(true);
    }

    @Test
    public void shouldReturnSuccessTestingWith256() {
        ECKeyFac.setType(ECKeyFac.ECKeyType.ED25519);
        ECKey ecKey = ECKeyFac.inst().create();
        ecKey = ecKey.fromPrivate(Hex.decode("5a90d8e67da5d1dfbf17916ae83bae04ef334f53ce8763932eba2c1116a62426fff4317ae351bda5e4fa24352904a9366d3a89e38d1ffa51498ba9acfbc65724"));


        byte[] pubKey = ecKey.getPubKey();

        byte[] data = "Our first test in AION1234567890".getBytes();

        HashUtil.setType(HashUtil.H256Type.KECCAK_256);
        byte[] hashedMessage = HashUtil.h256(data);

        ISignature signature = ecKey.sign(hashedMessage);

        byte[] input = new byte[128];
        System.arraycopy(hashedMessage, 0, input, 0, 32);
        System.arraycopy(signature.getSignature(), 0, input, 32, 64);
        System.arraycopy(pubKey, 0, input, 96, 32);

        PrecompiledContracts.PrecompiledContract contract = PrecompiledContracts.getPrecompiledContract(PrecompiledContracts.edVerifyAddress, null, null);
        ExecutionResult result = contract.execute(input, 21000L);
        assertThat(result.getOutput()[0]).isEqualTo(1);
    }

}