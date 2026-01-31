package mindrift.app.lisynchronization.utils;

import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.X509EncodedKeySpec;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class CryptoUtils {
    private CryptoUtils() {}

    public static String md5(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    public static byte[] aesEncrypt(byte[] data, String mode, byte[] key, byte[] iv) {
        try {
            String cipherMode = mode == null || mode.isEmpty() ? "AES/ECB/PKCS5Padding" : mode;
            Cipher cipher = Cipher.getInstance(cipherMode);
            SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
            if (iv != null && iv.length > 0 && cipherMode.contains("CBC")) {
                cipher.init(Cipher.ENCRYPT_MODE, keySpec, new IvParameterSpec(iv));
            } else {
                cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            }
            return cipher.doFinal(data);
        } catch (Exception e) {
            return new byte[0];
        }
    }

    public static byte[] rsaEncrypt(byte[] data, String publicKey) {
        try {
            byte[] keyBytes = java.util.Base64.getDecoder().decode(publicKey.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PublicKey key = keyFactory.generatePublic(spec);
            Cipher cipher = Cipher.getInstance("RSA/ECB/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            return cipher.doFinal(data);
        } catch (Exception e) {
            return new byte[0];
        }
    }

    public static byte[] randomBytes(int size) {
        byte[] bytes = new byte[Math.max(size, 0)];
        new SecureRandom().nextBytes(bytes);
        return bytes;
    }
}






