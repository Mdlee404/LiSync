package mindrift.app.music.utils;

import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.X509EncodedKeySpec;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 加密工具类
 * 注意：MD5 仅用于非安全敏感场景（如文件校验、缓存键生成），不应用于密码或敏感数据
 */
public final class CryptoUtils {
    private CryptoUtils() {}

    /**
     * MD5 哈希 - 仅用于非安全敏感场景（文件校验、缓存键等）
     * 不应用于密码存储或安全验证
     */
    public static String md5(String input) {
        if (input == null) return "";
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            Logger.warn("MD5 failed: " + e.getMessage());
            return "";
        }
    }

    /**
     * SHA-256 哈希 - 用于安全敏感场景
     */
    public static String sha256(String input) {
        if (input == null) return "";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            Logger.warn("SHA-256 failed: " + e.getMessage());
            return "";
        }
    }

    /**
     * AES 加密 - 默认使用更安全的 CBC 模式
     * @param mode 加密模式，默认为 AES/CBC/PKCS5Padding
     */
    public static byte[] aesEncrypt(byte[] data, String mode, byte[] key, byte[] iv) {
        if (data == null || key == null) return new byte[0];
        try {
            // 默认使用 CBC 模式而非不安全的 ECB 模式
            String cipherMode = mode == null || mode.isEmpty() ? "AES/CBC/PKCS5Padding" : mode;
            Cipher cipher = Cipher.getInstance(cipherMode);
            SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
            // CBC/GCM 模式需要 IV，如果没有提供则生成随机 IV
            if (cipherMode.contains("CBC") || cipherMode.contains("GCM")) {
                byte[] actualIv = (iv != null && iv.length > 0) ? iv : randomBytes(16);
                if (cipherMode.contains("GCM")) {
                    cipher.init(Cipher.ENCRYPT_MODE, keySpec, new IvParameterSpec(actualIv));
                } else {
                    cipher.init(Cipher.ENCRYPT_MODE, keySpec, new IvParameterSpec(actualIv));
                }
            } else {
                cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            }
            return cipher.doFinal(data);
        } catch (Exception e) {
            Logger.warn("AES encrypt failed: " + e.getMessage());
            return new byte[0];
        }
    }

    /**
     * RSA 加密 - 使用安全的 OAEP 填充
     */
    public static byte[] rsaEncrypt(byte[] data, String publicKey) {
        if (data == null || publicKey == null) return new byte[0];
        try {
            byte[] keyBytes = java.util.Base64.getDecoder().decode(publicKey.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PublicKey key = keyFactory.generatePublic(spec);
            // 使用安全的 OAEP 填充而非不安全的 NoPadding
            Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            return cipher.doFinal(data);
        } catch (Exception e) {
            Logger.warn("RSA encrypt failed: " + e.getMessage());
            return new byte[0];
        }
    }

    /**
     * RSA 加密 - 兼容旧版本，使用传统方式（不推荐）
     * 仅用于向后兼容，新代码应使用 rsaEncrypt
     */
    public static byte[] rsaEncryptLegacy(byte[] data, String publicKey) {
        if (data == null || publicKey == null) return new byte[0];
        try {
            byte[] keyBytes = java.util.Base64.getDecoder().decode(publicKey.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PublicKey key = keyFactory.generatePublic(spec);
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            return cipher.doFinal(data);
        } catch (Exception e) {
            Logger.warn("RSA legacy encrypt failed: " + e.getMessage());
            return new byte[0];
        }
    }

    public static byte[] randomBytes(int size) {
        byte[] bytes = new byte[Math.max(size, 0)];
        new SecureRandom().nextBytes(bytes);
        return bytes;
    }
}








