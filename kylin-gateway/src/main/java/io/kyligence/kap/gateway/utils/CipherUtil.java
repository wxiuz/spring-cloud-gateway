package io.kyligence.kap.gateway.utils;

import com.google.common.base.Strings;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/**
 * @author xiuzhao.wu
 * @date 2022/9/15 12:08
 */
public class CipherUtil {
	private static final byte[] KEY_CONTENT = {0x74, 0x68, 0x69, 0x73, 0x49, 0x73, 0x41, 0x53, 0x65, 0x63, 0x72, 0x65, 0x74, 0x4b,
			0x65, 0x79};
	private static final String ALGORITHM = "AES/ECB/PKCS5Padding";

	private CipherUtil() {

	}

	public static String encrypt(String plaintext) {
		if (Strings.isNullOrEmpty(plaintext)) {
			return "";
		}
		Key key = new SecretKeySpec(KEY_CONTENT, "AES");
		try {
			final Cipher c = Cipher.getInstance(ALGORITHM);
			c.init(Cipher.ENCRYPT_MODE, key);
			return new String(Base64.getEncoder().encode(c.doFinal(plaintext.getBytes())), StandardCharsets.UTF_8);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static String decrypt(String ciphertext) {
		if (Strings.isNullOrEmpty(ciphertext)) {
			return "";
		}
		Key key = new SecretKeySpec(KEY_CONTENT, "AES");
		try {
			final Cipher c = Cipher.getInstance(ALGORITHM);
			c.init(Cipher.DECRYPT_MODE, key);
			return new String(c.doFinal(Base64.getDecoder().decode(ciphertext.getBytes(StandardCharsets.UTF_8))));
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
