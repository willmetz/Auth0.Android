/*
 * PKCETest.java
 *
 * Copyright (c) 2016 Auth0 (http://auth0.com)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.auth0.android.provider;

import com.auth0.android.authentication.AuthenticationAPIClient;
import com.auth0.android.authentication.AuthenticationException;
import com.auth0.android.callback.BaseCallback;
import com.auth0.android.request.Request;
import com.auth0.android.result.Credentials;

import org.hamcrest.CoreMatchers;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
public class PKCETest {

    private static final String CODE_VERIFIER = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
    private static final String CODE_CHALLENGE = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";
    private static final String REDIRECT_URI = "redirectUri";
    private static final String AUTHORIZATION_CODE = "authorizationCode";

    private PKCE pkce;
    @Mock
    private AuthenticationAPIClient apiClient;
    @Mock
    private AuthCallback callback;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        pkce = new PKCE(apiClient, new AlgorithmHelperMock(CODE_VERIFIER), REDIRECT_URI, Collections.emptyMap());
    }

    @Test
    public void shouldGenerateChallengeFromRandomVerifier() {
        PKCE pkce = new PKCE(apiClient, REDIRECT_URI, new HashMap<String, String>());
        assertThat(pkce.getCodeChallenge(), is(notNullValue()));
    }

    @Test
    public void shouldGenerateValidRandomCodeChallenge() {
        PKCE randomPKCE = new PKCE(apiClient, REDIRECT_URI, new HashMap<String, String>());
        String challenge = randomPKCE.getCodeChallenge();
        assertThat(challenge, is(notNullValue()));
        assertThat(challenge, CoreMatchers.not(Matchers.isEmptyString()));
        assertThat(challenge, not(containsString("=")));
        assertThat(challenge, not(containsString("+")));
        assertThat(challenge, not(containsString("/")));
    }

    @Test
    public void shouldGenerateExpectedCodeChallenge() {
        String challenge = pkce.getCodeChallenge();
        assertThat(challenge, is(equalTo(CODE_CHALLENGE)));
    }

    @Test
    public void shouldGetToken() {
        Request<Credentials, AuthenticationException> tokenRequest = mock(Request.class);
        when(apiClient.token(AUTHORIZATION_CODE, CODE_VERIFIER, REDIRECT_URI)).thenReturn(tokenRequest);
        pkce.getToken(AUTHORIZATION_CODE, callback);
        verify(apiClient).token(AUTHORIZATION_CODE, CODE_VERIFIER, REDIRECT_URI);
        ArgumentCaptor<BaseCallback> callbackCaptor = ArgumentCaptor.forClass(BaseCallback.class);
        verify(tokenRequest).start(callbackCaptor.capture());
        Credentials credentials = mock(Credentials.class);
        callbackCaptor.getValue().onSuccess(credentials);
        verify(callback).onSuccess(credentials);
    }

    @Test
    public void shouldAddHeaders() {
        String header1Name = "header1";
        String header1Value = "val1";
        String header2Name = "header2";
        String header2Value = "val2";
        Map<String, String> headers = new HashMap<>();
        headers.put(header1Name, header1Value);
        headers.put(header2Name, header2Value);
        PKCE pkce = new PKCE(apiClient, new AlgorithmHelperMock(CODE_VERIFIER), REDIRECT_URI, headers);
        TokenRequest tokenRequest = mock(TokenRequest.class);
        when(apiClient.token(AUTHORIZATION_CODE, REDIRECT_URI)).thenReturn(tokenRequest);
        when(tokenRequest.setCodeVerifier(CODE_VERIFIER)).thenReturn(tokenRequest);
        pkce.getToken(AUTHORIZATION_CODE, callback);
        verify(tokenRequest).addHeader(header1Name, header1Value);
        verify(tokenRequest).addHeader(header2Name, header2Value);
    }

    @Test
    public void shouldFailToGetToken() {
        Request<Credentials, AuthenticationException> tokenRequest = mock(Request.class);
        when(apiClient.token(AUTHORIZATION_CODE, CODE_VERIFIER, REDIRECT_URI)).thenReturn(tokenRequest);
        pkce.getToken(AUTHORIZATION_CODE, callback);
        verify(apiClient).token(AUTHORIZATION_CODE, CODE_VERIFIER, REDIRECT_URI);
        ArgumentCaptor<BaseCallback> callbackCaptor = ArgumentCaptor.forClass(BaseCallback.class);
        verify(tokenRequest).start(callbackCaptor.capture());
        callbackCaptor.getValue().onFailure(new AuthenticationException("Some error"));
        verify(callback).onFailure(any(AuthenticationException.class));
    }

    @Test
    public void shouldNotHavePKCEAvailableIfSHA256IsNotAvailable() {
        AlgorithmHelper algorithmHelper = Mockito.mock(AlgorithmHelper.class);
        when(algorithmHelper.getSHA256(any())).thenThrow(IllegalStateException.class);
        assertFalse(PKCE.isAvailable(algorithmHelper));
    }

    @Test
    public void shouldNotHavePKCEAvailableIfASCIIIsNotAvailable() {
        AlgorithmHelper algorithmHelper = Mockito.mock(AlgorithmHelper.class);
        when(algorithmHelper.getASCIIBytes(anyString())).thenThrow(IllegalStateException.class);
        assertFalse(PKCE.isAvailable(algorithmHelper));
    }

    @Test
    public void shouldHavePKCEAvailable() {
        AlgorithmHelper algorithmHelper = Mockito.mock(AlgorithmHelper.class);
        when(algorithmHelper.getSHA256(any(byte[].class))).thenReturn(new byte[]{1, 2, 1, 2, 1, 2, 1, 2, 1});
        when(algorithmHelper.getASCIIBytes(anyString())).thenReturn(new byte[]{1, 2, 1, 2, 1, 2, 1, 2, 1});

        assertTrue(PKCE.isAvailable(algorithmHelper));
    }
}