package com.kovanlabs.lynklog.startup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;

import com.kovanlabs.lynklog.client.VerificationClient;
import com.kovanlabs.lynklog.config.LynkLogProperties;
import com.kovanlabs.lynklog.context.ServiceContext;
import com.kovanlabs.lynklog.model.VerifyResponse;
import com.kovanlabs.lynklog.watcher.LynkLogFileWatcher;

@ExtendWith(MockitoExtension.class)
class LynkLogStartupVerifierTest {

    private LynkLogProperties properties;

    @Mock
    private VerificationClient verificationClient;

    @Mock
    private LynkLogFileWatcher fileWatcher;

    private ServiceContext serviceContext;
    private LynkLogStartupVerifier verifier;

    @Mock
    private ApplicationArguments applicationArguments;

    @BeforeEach
    void setUp() {
        properties = new LynkLogProperties();
        serviceContext = new ServiceContext();
        verifier = new LynkLogStartupVerifier(properties, verificationClient, serviceContext, fileWatcher);
    }

    @Test
    void run_disabledAgent_setsStatusToDisabledAndSkipsVerification() {
        properties.setEnabled(false);

        verifier.run(applicationArguments);

        assertThat(serviceContext.isVerified()).isFalse();
        assertThat(serviceContext.getStatus()).isEqualTo("DISABLED");
        assertThat(serviceContext.getVerifiedAt()).isNull();
        verifyNoInteractions(verificationClient);
        verifyNoInteractions(fileWatcher);
    }

    @Test
    void run_missingSecret_setsStatusToVerificationFailedAndSkipsVerification() {
        properties.setEnabled(true);
        properties.setServiceSecret(null);
        properties.setApiKey("ak_validkey");
 
        verifier.run(applicationArguments);
 
        assertThat(serviceContext.isVerified()).isFalse();
        assertThat(serviceContext.getStatus()).isEqualTo("VERIFICATION_FAILED");
        assertThat(serviceContext.getVerifiedAt()).isNull();
        verifyNoInteractions(verificationClient);
        verifyNoInteractions(fileWatcher);
    }
 
    @Test
    void run_missingApiKey_setsStatusToVerificationFailedAndSkipsVerification() {
        properties.setEnabled(true);
        properties.setServiceSecret("sv_validsecret");
        properties.setApiKey(null);
 
        verifier.run(applicationArguments);
 
        assertThat(serviceContext.isVerified()).isFalse();
        assertThat(serviceContext.getStatus()).isEqualTo("VERIFICATION_FAILED");
        assertThat(serviceContext.getVerifiedAt()).isNull();
        verifyNoInteractions(verificationClient);
        verifyNoInteractions(fileWatcher);
    }

    @Test
    void run_successfulVerification_setsVerifiedTrueAndStoresServiceNameAndTimestampAndStartsWatcher() {
        properties.setEnabled(true);
        properties.setServiceSecret("sv_validsecret");
        properties.setApiKey("ak_validkey");
        
        VerifyResponse response = new VerifyResponse(true, "gateway-service", "org-123");
        when(verificationClient.verify("ak_validkey", "sv_validsecret")).thenReturn(response);
 
        verifier.run(applicationArguments);
 
        assertThat(serviceContext.isVerified()).isTrue();
        assertThat(serviceContext.getServiceName()).isEqualTo("gateway-service");
        assertThat(serviceContext.getStatus()).isEqualTo("VERIFIED");
        assertThat(serviceContext.getVerifiedAt()).isNotNull();
        verify(fileWatcher, times(1)).start();
    }
 
    @Test
    void run_failedVerification_setsVerifiedFalseAndStoresVerificationFailedStatusAndDoesNotStartWatcher() {
        properties.setEnabled(true);
        properties.setServiceSecret("sv_invalidsecret");
        properties.setApiKey("ak_validkey");
        
        VerifyResponse response = new VerifyResponse(false, null, null);
        when(verificationClient.verify("ak_validkey", "sv_invalidsecret")).thenReturn(response);
 
        verifier.run(applicationArguments);
 
        assertThat(serviceContext.isVerified()).isFalse();
        assertThat(serviceContext.getServiceName()).isNull();
        assertThat(serviceContext.getStatus()).isEqualTo("VERIFICATION_FAILED");
        assertThat(serviceContext.getVerifiedAt()).isNull();
        verifyNoInteractions(fileWatcher);
    }
 
    @Test
    void run_clientException_setsVerifiedFalseAndStoresVerificationErrorAndDoesNotStartWatcher() {
        properties.setEnabled(true);
        properties.setServiceSecret("sv_errorsecret");
        properties.setApiKey("ak_validkey");
        
        when(verificationClient.verify("ak_validkey", "sv_errorsecret")).thenThrow(new RuntimeException("Connection Refused"));
 
        // Ensure running does not crash
        verifier.run(applicationArguments);
 
        assertThat(serviceContext.isVerified()).isFalse();
        assertThat(serviceContext.getStatus()).isEqualTo("VERIFICATION_ERROR");
        assertThat(serviceContext.getVerifiedAt()).isNull();
        verifyNoInteractions(fileWatcher);
    }
}
