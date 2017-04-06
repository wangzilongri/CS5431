package org.cs5431;

import org.bouncycastle.crypto.PBEParametersGenerator;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.generators.PKCS5S2ParametersGenerator;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.json.JSONObject;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.Random;

import static org.cs5431.Constants.DEBUG_MODE;

public class Encryption {
    public static byte[] pwdBasedHash(String pwd, byte[] salt) {
        PKCS5S2ParametersGenerator generator = new PKCS5S2ParametersGenerator();
        generator.init(PBEParametersGenerator.PKCS5PasswordToBytes(
                pwd.toCharArray()), salt, 3000);
        //TODO: 256 is currently the key length. Is this correct?
        KeyParameter kp = (KeyParameter) generator.generateDerivedParameters
                (128);
        return kp.getKey();
    }

    //[0] is the hashed password, [1] is the salt
    public static byte[] newPwdSalt() {
        Random random = new SecureRandom();
        //TODO: 32 is currently the salt length. Is this correct?
        byte salt[] = new byte[32];
        random.nextBytes(salt);
        return salt;
    }

    //[0] is the hashed password, [1] is the salt
    public static byte[][] pwdBasedKey(String pwd, byte[] salt) {
        byte[] hashedPW = pwdBasedHash(pwd, salt);
        if (DEBUG_MODE) {
            System.out.println("On Client side: key generated from pwd and salt:");
            System.out.println(Base64.getEncoder().encodeToString(hashedPW));
        }
        byte returnedValues[][] = new byte[2][128];
        returnedValues[0] = hashedPW;
        returnedValues[1] = salt;
        return returnedValues;
    }

    static String secondPwdHash(String pwd, byte[] salt) {
        PKCS5S2ParametersGenerator generator = new PKCS5S2ParametersGenerator();
        generator.init(PBEParametersGenerator.PKCS5PasswordToBytes(
                pwd.toCharArray()), salt, 10000);
        //TODO: 256 is currently the key length. Is this correct?
        KeyParameter kp = (KeyParameter) generator.generateDerivedParameters(256);
        return Base64.getEncoder().encodeToString(kp.getKey());
    }

    public static byte[] SHA256(String msg) {
        SHA256Digest sha256 = new SHA256Digest();
        byte msgByte[] = msg.getBytes();
        sha256.update(msgByte, 0, msgByte.length);
        Arrays.fill(msgByte, (byte)0 );    //an attempt to zero out pwd
        byte[] hashedPwd = new byte[sha256.getDigestSize()];
        sha256.doFinal(hashedPwd, 0);
        return hashedPwd;
    }

    static byte[] signCert(byte[] message, PrivateKey key) throws
            NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        //SHA-256 hashing algorithm with RSA, getting the signature object
        Signature dsa = Signature.getInstance("SHA256withRSA");
        dsa.initSign(key); //initializing the private key
        dsa.update(message);
        return dsa.sign();
    }

    //[0] is Base64 encoded public key, [1] is Base64 encoded password
    // encrypted private key, [2] is salt used for password based encryption
    public static String[] generateUserKeys(String password) throws
            NoSuchAlgorithmException, NoSuchProviderException,
            NoSuchPaddingException, InvalidKeyException,
            InvalidAlgorithmParameterException, IllegalBlockSizeException,
            BadPaddingException {
        String values[] = new String[3];
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA", new
                BouncyCastleProvider());
        kpg.initialize(4096, new SecureRandom());
        KeyPair keyPair = kpg.generateKeyPair();
        values[0] = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        //encrypt secret key using password based key
        //symmetric, uses AES
        byte salt[] = newPwdSalt();
        values[1] = encryptPrivateKey(password, keyPair.getPrivate()
                .getEncoded(), salt);
        values[2] = Base64.getEncoder().encodeToString(salt);
        return values;
    }

    public static String encryptPrivateKey(String password, byte[]
            privKeyByte, byte[] privKeySalt) throws NoSuchAlgorithmException,
            NoSuchProviderException, NoSuchPaddingException,
            InvalidKeyException, InvalidAlgorithmParameterException,
            IllegalBlockSizeException, BadPaddingException {
        byte keyAndSalt[][] = pwdBasedKey(password, privKeySalt);
        byte key[] = keyAndSalt[0];
        SecretKey secretKey = new SecretKeySpec(key, 0, key.length, "AES");
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS7Padding", "BC");
        IvParameterSpec iv = new IvParameterSpec(new byte[16]);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, iv);
        byte encryptedKey[] = cipher.doFinal(privKeyByte);
        return Base64.getEncoder().encodeToString(encryptedKey);
    }

    public static PublicKey getPubKeyFromJSON(String encodedPubKey) throws
            NoSuchAlgorithmException, InvalidKeySpecException {
        byte[] decodedPub = Base64.getDecoder().decode(encodedPubKey);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePublic(new X509EncodedKeySpec(decodedPub));
    }

    public static PrivateKey getPrivKeyFromJSON(String encodedPrivKey, String
            privKeySalt, String password)
            throws NoSuchAlgorithmException, NoSuchPaddingException,
            IllegalBlockSizeException, BadPaddingException,
            InvalidKeySpecException, InvalidKeyException,
            NoSuchProviderException, InvalidAlgorithmParameterException {
        byte[] decodedPriv = decryptPwdBasedKey(encodedPrivKey,
                password, privKeySalt);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePrivate(new PKCS8EncodedKeySpec(decodedPriv));
    }

    private static byte[] decryptPwdBasedKey(String enc, String pwd, String
            salt) throws NoSuchAlgorithmException, NoSuchPaddingException,
            IllegalBlockSizeException, BadPaddingException,
            InvalidKeyException, NoSuchProviderException, InvalidAlgorithmParameterException {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS7Padding", "BC");
        byte key[] = pwdBasedHash(pwd, Base64.getDecoder().decode(salt));
        if (DEBUG_MODE) {
            System.out.println("From server: key generated from pwd and salt:");
            System.out.println(Base64.getEncoder().encodeToString(key));
        }
        SecretKey secretKey = new SecretKeySpec(key, 0, key.length, "AES");
        IvParameterSpec iv = new IvParameterSpec(new byte[16]);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, iv);
        return cipher.doFinal(Base64.getDecoder().decode(enc));
    }
}