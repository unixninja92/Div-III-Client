package systems.obscure.client.ratchet;

import com.google.protobuf.ByteString;

import org.abstractj.kalium.crypto.Point;
import org.abstractj.kalium.crypto.SecretBox;
import org.abstractj.kalium.keys.KeyPair;
import org.abstractj.kalium.keys.VerifyKey;
import org.spongycastle.pqc.math.linearalgebra.ByteUtils;
import org.whispersystems.libaxolotl.util.ByteUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HashMap;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import systems.obscure.client.Globals;
import systems.obscure.client.protos.Pond;

/**
 * @author unixninja92
 */
public class Ratchet {
    private final static byte[] chainKeyLabel = "chain key".getBytes();
    private final static byte[] headerKeyLabel = "header key".getBytes();
    private final static byte[] nextRecvHeaderKeyLabel = "next receive header key".getBytes();
    private final static byte[] rootKeyLabel = "root key".getBytes();
    private final static byte[] rootKeyUpdateLabel = "root key update".getBytes();
    private final static byte[] sendHeaderKeyLabel = "next send header key".getBytes();
    private final static byte[] messageKeyLabel = "message key".getBytes();
    private final static byte[] chainKeyStepLabel = "chain key step".getBytes();

    private final static int headerSize = 4 /* uint32 message count */ +
            4 /* uint32 previous message count */ +
            32 /* curve25519 ratchet public */ +
            24 /* nonce for message */;

    private final static int sealedHeaderSize = 24 /* nonce */ + headerSize + Globals.SECRETBOX_OVERHEAD;
    private final static int nonceInHeaderOffset = 4 + 4 + 32;
    private final static int maxMissingMessages = 8;

    KeyPair myIdentity;
    KeyPair theirIdentity;
    VerifyKey myVerifyKey;
    VerifyKey theirVerifyKey;

    byte[] rootkey;
    byte[] sendHeaderKey;
    byte[] recvHeaderKey;
    byte[] nextSendHeaderKey;
    byte[] nextRecvHeaderKey;
    byte[] sendChainKey;
    byte[] recvChainKey;
    byte[] sendRatchetPrivate;
    byte[] recvRatchetPublic;
    long sendCount;
    long recvCount;
    long prevSendCount;

    boolean rachet;

    HashMap<byte[], HashMap<Long, SavedKey>> saved;

    byte[] kxPrivate0;
    byte[] kxPrivate1;

    SecureRandom rand;

    public Ratchet(SecureRandom rand) {
        this.rand = rand;
        kxPrivate0 = new byte[32];
        kxPrivate1 = new byte[32];
        rand.nextBytes(kxPrivate0);
        rand.nextBytes(kxPrivate1);
        saved = new HashMap<>();
    }

    public void fillKeyExchagne(Pond.KeyExchange.Builder kx) {
        if (kxPrivate0 == null || kxPrivate1 == null) {
            throw new RuntimeException("ratchet: handshake already complete");
        }
        KeyPair pair0 = new KeyPair(kxPrivate0);
        KeyPair pair1 = new KeyPair(kxPrivate1);

        kx.setDh(ByteString.copyFrom(pair0.getPublicKey().toBytes()));
        kx.setDh1(ByteString.copyFrom(pair1.getPublicKey().toBytes()));
    }
//
//    public byte[] deriveKey(byte[] label, Mac hmac) {
//        return hmac.doFinal(label);
//    }

    public byte[] getKXPrivateForTransition() {
        return kxPrivate0;
    }

    public void completeKeyExchange(Pond.KeyExchange kx) {
        if(kxPrivate0 == null)
            throw new RuntimeException("ratchet: handshake already complete");

        KeyPair pair = new KeyPair(kxPrivate0);

        ByteBuffer public0 = ByteBuffer.wrap(pair.getPublicKey().toBytes());

        if(kx.getDh().size() != public0.remaining())
            throw new RuntimeException("ratchet: peer's key exchange is invalid");
        if(kx.getDh1().size() != public0.remaining())
            throw new RuntimeException("ratchet: peer's key exchange is invalid");

        boolean amAlice = false;
        switch (public0.compareTo(ByteBuffer.wrap(kx.getDh().toByteArray()))){
            case -1:
                amAlice = true; break;
            case 1: amAlice = false; break;
            case 0:
                throw new RuntimeException("ratchet: peer echoed our own DH values back");
        }

        Point theirDH = new Point(kx.getDh().toByteArray());

        Point sharedKey = theirDH.mult(kxPrivate0);

        ByteBuffer keyMaterial = ByteBuffer.allocate(32*5);
        keyMaterial.put(sharedKey.toBytes());

        Point theirIdentityPublic = new Point(theirIdentity.getPublicKey().toBytes());

        if(amAlice) {
            keyMaterial.put(theirDH.mult(myIdentity.getPrivateKey().toBytes()).toBytes());
            keyMaterial.put(theirIdentityPublic.mult(kxPrivate0).toBytes());
        }
        else {
            keyMaterial.put(theirIdentityPublic.mult(kxPrivate0).toBytes());
            keyMaterial.put(theirDH.mult(myIdentity.getPrivateKey().toBytes()).toBytes());
        }

        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(keyMaterial.array(), "HmacSHA256"));
            rootkey = hmac.doFinal(rootKeyLabel);
            if(amAlice) {
                recvHeaderKey = hmac.doFinal(headerKeyLabel);
                nextSendHeaderKey = hmac.doFinal(sendHeaderKeyLabel);
                nextRecvHeaderKey = hmac.doFinal(nextRecvHeaderKeyLabel);
                recvChainKey = hmac.doFinal(chainKeyLabel);
                recvRatchetPublic = kx.getDh1().toByteArray();
            }
            else {
                sendHeaderKey = hmac.doFinal(headerKeyLabel);
                nextRecvHeaderKey = hmac.doFinal(sendHeaderKeyLabel);
                nextSendHeaderKey = hmac.doFinal(nextRecvHeaderKeyLabel);
                sendChainKey = hmac.doFinal(chainKeyLabel);
                sendRatchetPrivate = kxPrivate1;
            }

            rachet = amAlice;
            kxPrivate0 = null;
            kxPrivate1 = null;
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        }
    }

    public byte[] encrypt(byte[] msg) {
        try {
            Mac h = Mac.getInstance("HmacSHA256");
            if (rachet) {
                rand.nextBytes(sendRatchetPrivate);
                nextSendHeaderKey = sendHeaderKey.clone();
                Point recvRatchetPublicPoint = new Point(recvRatchetPublic);
                Point sharedKey = recvRatchetPublicPoint.mult(sendRatchetPrivate);
                MessageDigest md = MessageDigest.getInstance("SHA256");
                md.update(rootKeyUpdateLabel);
                md.update(rootkey);
                md.update(sharedKey.toBytes());
                SecretKey keyMaterial = new SecretKeySpec(md.digest(), "HmacSHA256");
                h.init(keyMaterial);
                rootkey = h.doFinal(rootKeyLabel);
                nextSendHeaderKey = h.doFinal(sendHeaderKeyLabel);
                sendChainKey = h.doFinal(chainKeyLabel);
                prevSendCount = sendCount;
                sendCount = 0;
                rachet = false;
            }
            h.reset();
            h.init(new SecretKeySpec(sendChainKey, "HmacSHA256"));

            byte[] messageKey = h.doFinal(messageKeyLabel);
            sendChainKey = h.doFinal(chainKeyStepLabel);
            KeyPair sendRachet = new KeyPair(sendRatchetPrivate);

            ByteBuffer header = ByteBuffer.allocate(headerSize);

            byte[] headerNonce = new byte[24];
            byte[] messageNonce = new byte[24];
            rand.nextBytes(headerNonce);
            rand.nextBytes(messageNonce);

            header.putLong(sendCount);
            header.putLong(prevSendCount);
            header.put(sendRachet.getPublicKey().toBytes());
            header.position(nonceInHeaderOffset);
            header.put(headerNonce);
            ByteBuffer out = ByteBuffer.allocate(headerNonce.length+headerSize+msg.length);
            out.put(headerNonce);

            SecretBox secretBox = new SecretBox(sendHeaderKey);
            out.put(secretBox.encrypt(headerNonce, header.array()));
            sendCount++;
            secretBox = new SecretBox(messageKey);
            out.put(secretBox.encrypt(messageNonce, msg));
            return out.array();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        }
        return null;
    }

    private byte[] trySavedKeys(byte[] ciphertext) {
        if(ciphertext.length < Globals.SECRETBOX_OVERHEAD)
            throw new RuntimeException("ratchet: header too small to be valid");
        ByteBuffer sealedHeaderBuffer = ByteBuffer.wrap(ciphertext, 0, sealedHeaderSize);
        byte[] nonce = new byte[24];
        sealedHeaderBuffer.get(nonce);
        byte[] sealedHeader = new byte[sealedHeaderSize - nonce.length];
        sealedHeaderBuffer.get(sealedHeader);
        for(byte[] headerKey : saved.keySet()){
            SecretBox secretBox = new SecretBox(headerKey);
            ByteBuffer header;
            try {
                header = ByteBuffer.wrap(secretBox.decrypt(nonce, sealedHeader));
                if(header.remaining() != headerSize)
                    continue;

            } catch (RuntimeException e) {
                continue;
            }
            long msgNum = header.getLong();
            SavedKey msgKey;

            if(!saved.get(headerKey).containsKey(msgNum))
                return null;
            msgKey = saved.get(headerKey).get(msgNum);

            ByteBuffer sealedMessage = ByteBuffer.wrap(ciphertext, sealedHeaderSize, ciphertext.length);
            header.position(nonceInHeaderOffset);
            header.get(nonce);
            secretBox = new SecretBox(msgKey.key);
            byte[] msg = secretBox.decrypt(nonce, sealedMessage.array());
            saved.get(headerKey).remove(msgNum);
            if(saved.get(headerKey).size() == 0)
                saved.remove(headerKey);
            return msg;

        }
        return null;
    }

    private class SavedKeysStruct {
        byte[] provisionalChainKey;
        byte[] messageKey;
        HashMap<byte[], HashMap<Long, SavedKey>> savedKey;

        public SavedKeysStruct(byte[] provisionalChainKey, byte[] messageKey) {
            this.messageKey = messageKey;
            this.provisionalChainKey = provisionalChainKey;
        }
    }

    private SavedKeysStruct saveKeys(byte[] headerKey, byte[] recvChainKey, long messageNum, long receivedCount) {
        if(messageNum < receivedCount)
            throw new RuntimeException("ratchet: duplicate message or message delayed longer than tolerance");

        long missingMessages = messageNum - receivedCount;
        if(missingMessages > maxMissingMessages)
            throw new RuntimeException("ratchet: message exceeds reordering limit");

        HashMap<Long, SavedKey> messageKeys = null;
        long now = System.nanoTime();
        if(missingMessages > 0) {
            messageKeys = new HashMap<>();
        }

        byte[] provisionalChainKey = recvChainKey.clone();
        byte[] messageKey = new byte[32];
        for(long i = receivedCount; i <= messageNum; i++ ){
            try {
                Mac h = Mac.getInstance("HmacSHA256");
                h.init(new SecretKeySpec(provisionalChainKey, "HmacSHA256"));
                messageKey = h.doFinal(messageKeyLabel);
                provisionalChainKey = h.doFinal(chainKeyStepLabel);
                if(i < messageNum)
                    messageKeys.put(i, new SavedKey(messageKey, now));
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            } catch (InvalidKeyException e) {
                e.printStackTrace();
            }
        }
        SavedKeysStruct savedKeys = new SavedKeysStruct(provisionalChainKey, messageKey);
        if(messageKeys != null) {
            savedKeys.savedKey = new HashMap<>();
            savedKeys.savedKey.put(headerKey, messageKeys);
        }
        return savedKeys;
    }

    private void mergeSavedKeys(HashMap<byte[], HashMap<Long, SavedKey>> keys) {
        //TODO merge that shit
    }

    private boolean isZeroKey(byte[] key){
        short x = 0;
        for(byte y : key)
            x |= y;
        return x == 0;
    }

    public byte[] decrypt(byte[] ciphertext) {
        byte[] msg = trySavedKeys(ciphertext);
        if(msg != null)
            return msg;
        ByteBuffer cipherbuffer = ByteBuffer.wrap(ciphertext);
        byte[] sealedHeader = new byte[sealedHeaderSize];
        cipherbuffer.get(sealedHeader);
        byte[] sealedMessage = new byte[cipherbuffer.remaining()];
        cipherbuffer.get(sealedMessage);
        byte[] nonce = ByteUtils.subArray(sealedHeader, 0, 24);
        sealedHeader = ByteUtils.subArray(sealedHeader, 24);

        SecretBox secretBox = new SecretBox(recvHeaderKey);
        boolean ok = !isZeroKey(recvHeaderKey);
        ByteBuffer header = null;
        try {
            header = ByteBuffer.wrap(secretBox.decrypt(nonce, sealedHeader));
        } catch (RuntimeException e){
            ok = false;
        }
        if(ok) {
            if(header.capacity() != headerSize)
                throw new RuntimeException("ratchet: incorrect header size");
            long messageNum = header.getLong();
            SavedKeysStruct keys = saveKeys(recvHeaderKey, recvChainKey, messageNum, recvCount);

            header.position(nonceInHeaderOffset);
            header.get(nonce);
            secretBox = new SecretBox(keys.messageKey);
            msg = secretBox.decrypt(nonce, sealedMessage);

            recvChainKey = keys.provisionalChainKey.clone();

            mergeSavedKeys(keys.savedKey);
            recvCount = messageNum + 1;
            return msg;
        }

        secretBox = new SecretBox(nextRecvHeaderKey);
        header = ByteBuffer.wrap(secretBox.decrypt(nonce, sealedHeader));
        if(header.remaining() != headerSize)
            throw new RuntimeException("ratchet: incorrect header size");

        if(rachet)
            throw new RuntimeException("ratchet: received message encrypted to next header key without ratchet flag set");

        long messageNum = header.getLong();
        long prevMessageCount = header.getLong();

        SavedKeysStruct oldKeys = saveKeys(recvHeaderKey, recvChainKey, prevMessageCount, recvCount);

        byte[] dhPublic = new byte[32];
        Point sharedKey;
        byte[] rootKey = new byte[32];
        byte[] chainKey = new byte[32];
        byte[] keyMaterial = new byte[32];

        header.get(dhPublic);
        Point dhPoint = new Point(dhPublic);
        sharedKey = dhPoint.mult(sendRatchetPrivate);

        try {
            MessageDigest sha = MessageDigest.getInstance("SHA256");
            sha.update(rootKeyUpdateLabel);
            sha.update(rootKey);
            sha.update(sharedKey.toBytes());
            keyMaterial = sha.digest();

            Mac rootKeyHMAC = Mac.getInstance("HmacSHA256");
            rootKeyHMAC.init(new SecretKeySpec(keyMaterial, "HmacSHA256"));
            rootKey = rootKeyHMAC.doFinal(rootKeyLabel);
            chainKey = rootKeyHMAC.doFinal(chainKeyLabel);
            
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        }
    }

}
