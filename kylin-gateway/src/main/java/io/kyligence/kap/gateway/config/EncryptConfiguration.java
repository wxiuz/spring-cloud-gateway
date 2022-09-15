package io.kyligence.kap.gateway.config;

import com.ulisesbocchio.jasyptspringboot.annotation.EnableEncryptableProperties;
import io.kyligence.kap.gateway.utils.CipherUtil;
import org.jasypt.encryption.StringEncryptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author xiuzhao.wu
 * @date 2022/9/15 12:13
 */
@Configuration
@EnableEncryptableProperties
public class EncryptConfiguration {

	@Bean
	public StringEncryptor jasyptStringEncryptor() {
		return new PasswordEncryptor();
	}

	private static class PasswordEncryptor implements StringEncryptor {
		@Override
		public String encrypt(String message) {
			return CipherUtil.encrypt(message);
		}

		@Override
		public String decrypt(String encryptedMessage) {
			return CipherUtil.decrypt(encryptedMessage);
		}
	}
}
