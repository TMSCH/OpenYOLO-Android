/*
 * Copyright 2017 The OpenYOLO Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.openyolo.protocol;

import static org.assertj.core.api.Java6Assertions.assertThat;

import android.os.Parcel;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.openyolo.protocol.internal.ClientVersionUtil;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Tests for {@link HintRetrieveRequest}.
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class HintRetrieveRequestTest {

    @Test
    public void testBuild() {
        PasswordSpecification originalSpec = new PasswordSpecification.Builder()
                .ofLength(8, 10)
                .allow(PasswordSpecification.ALPHANUMERIC)
                .require(PasswordSpecification.NUMERALS, 1)
                .build();

        HintRetrieveRequest request = new HintRetrieveRequest.Builder(
                AuthenticationMethods.EMAIL,
                AuthenticationMethods.GOOGLE)
                .addAuthenticationMethod(AuthenticationMethods.FACEBOOK)
                .setTokenProviders(TestConstants.createTokenProviderMap())
                .setPasswordSpecification(originalSpec)
                .setAdditionalProperties(TestConstants.ADDITIONAL_PROPS)
                .build();

        assertThat(request.getAuthenticationMethods())
                .containsOnly(
                        AuthenticationMethods.EMAIL,
                        AuthenticationMethods.GOOGLE,
                        AuthenticationMethods.FACEBOOK);

        TestConstants.checkTokenProviderMap(request.getTokenProviders());
        TestConstants.checkAdditionalProps(request.getAdditionalProperties());

        assertThat(originalSpec).isEqualTo(request.getPasswordSpecification());
    }

    @Test
    public void testBuild_overwriteAuthenticationMethods() {
        HintRetrieveRequest request = new HintRetrieveRequest.Builder(AuthenticationMethods.EMAIL)
                .setAuthenticationMethods(
                        AuthenticationMethods.GOOGLE,
                        AuthenticationMethods.FACEBOOK)
                .build();

        assertThat(request.getAuthenticationMethods())
                .containsOnly(AuthenticationMethods.GOOGLE, AuthenticationMethods.FACEBOOK);
    }

    @Test
    public void testForEmailAndPasswordAccount() {
        HintRetrieveRequest request = HintRetrieveRequest.forEmailAndPasswordAccount();
        assertThat(request.getAuthenticationMethods())
                .containsOnly(AuthenticationMethods.EMAIL);
        assertThat(request.getPasswordSpecification())
                .isEqualTo(PasswordSpecification.DEFAULT);
        assertThat(request.getAdditionalProperties()).isEmpty();
    }

    @Test
    public void testToProtocolBuffer_includesClientVersion() {
        String vendor = "test";
        int major = 1;
        int minor = 2;
        int patch = 3;
        ClientVersionUtil.setClientVersion(
                Protobufs.ClientVersion.newBuilder()
                        .setVendor(vendor)
                        .setMajor(major)
                        .setMinor(minor)
                        .setPatch(patch)
                        .build());

        try {
            HintRetrieveRequest request = HintRetrieveRequest.forEmailAndPasswordAccount();
            Protobufs.HintRetrieveRequest proto = request.toProtocolBuffer();
            assertThat(proto.hasClientVersion());
            assertThat(proto.getClientVersion().getVendor()).isEqualTo(vendor);
            assertThat(proto.getClientVersion().getMajor()).isEqualTo(major);
            assertThat(proto.getClientVersion().getMinor()).isEqualTo(minor);
            assertThat(proto.getClientVersion().getPatch()).isEqualTo(patch);
        } finally {
            ClientVersionUtil.setClientVersion(null);
        }
    }

    @Test
    public void testSerialize() {
        HintRetrieveRequest request = new HintRetrieveRequest.Builder(
                AuthenticationMethods.EMAIL,
                AuthenticationMethods.FACEBOOK)
                .setPasswordSpecification(
                        new PasswordSpecification.Builder()
                                .ofLength(10, 100)
                                .allow(PasswordSpecification.ALPHANUMERIC)
                                .require(PasswordSpecification.NUMERALS, 1)
                                .build())
                .setTokenProviders(TestConstants.createTokenProviderMap())
                .setAdditionalProperties(TestConstants.ADDITIONAL_PROPS)
                .build();

        Parcel p = Parcel.obtain();
        try {
            request.writeToParcel(p, 0);
            p.setDataPosition(0);
            HintRetrieveRequest read = HintRetrieveRequest.CREATOR.createFromParcel(p);

            assertThat(read.getAuthenticationMethods())
                    .containsOnlyElementsOf(request.getAuthenticationMethods());
            assertThat(read.getPasswordSpecification())
                    .isEqualTo(request.getPasswordSpecification());

            TestConstants.checkTokenProviderMap(read.getTokenProviders());
            TestConstants.checkAdditionalProps(read.getAdditionalProperties());
        } finally {
            p.recycle();
        }
    }

    /* **************************** constraint violation test cases *******************************/

    @SuppressWarnings("ConstantConditions")
    @Test(expected = MalformedDataException.class)
    public void fromProto_withNull_throwsMalformedDataException() throws Exception {
        HintRetrieveRequest.fromProtobuf(null);
    }

    @SuppressWarnings("ConstantConditions")
    @Test(expected = IllegalArgumentException.class)
    public void testBuild_constructor_nullAuthenticationMethod() {
        new HintRetrieveRequest.Builder((AuthenticationMethod) null).build();
    }

    @SuppressWarnings("ConstantConditions")
    @Test(expected = IllegalArgumentException.class)
    public void testBuild_constructor_nullUriSet() {
        new HintRetrieveRequest.Builder((Set<AuthenticationMethod>) null).build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBuild_constructor_nullUriInVarargs() {
        new HintRetrieveRequest.Builder(
                AuthenticationMethods.EMAIL,
                null,
                AuthenticationMethods.GOOGLE).build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBuild_constructor_nullInVarargs() {
        new HintRetrieveRequest.Builder(
                AuthenticationMethods.EMAIL,
                null,
                AuthenticationMethods.PHONE)
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBuild_constructor_emptySet() {
        new HintRetrieveRequest.Builder(new HashSet<AuthenticationMethod>()).build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBuild_constructor_setContainingNull() {
        HashSet<AuthenticationMethod> authMethods = new HashSet<>();
        authMethods.add(null);
        new HintRetrieveRequest.Builder(new HashSet<>(authMethods)).build();
    }

    @SuppressWarnings("ConstantConditions")
    @Test(expected = IllegalArgumentException.class)
    public void testBuild_setAuthenticationMethods_nullUri() {
        new HintRetrieveRequest.Builder(AuthenticationMethods.EMAIL)
                .setAuthenticationMethods((AuthenticationMethod) null)
                .build();
    }

    @SuppressWarnings("ConstantConditions")
    @Test(expected = IllegalArgumentException.class)
    public void testBuild_setAuthenticationMethods_nullUriInVarargs() {
        new HintRetrieveRequest.Builder(AuthenticationMethods.EMAIL)
                .setAuthenticationMethods(
                        AuthenticationMethods.EMAIL,
                        null,
                        AuthenticationMethods.GOOGLE)
                .build();
    }

    @SuppressWarnings("ConstantConditions")
    @Test(expected = IllegalArgumentException.class)
    public void testBuild_setAuthenticationMethods_nullSet() {
        new HintRetrieveRequest.Builder(AuthenticationMethods.EMAIL)
                .setAuthenticationMethods(null)
                .build();
    }

    @SuppressWarnings("ConstantConditions")
    @Test(expected = IllegalArgumentException.class)
    public void testBuild_setAuthenticationMethods_emptySet() {
        new HintRetrieveRequest.Builder(AuthenticationMethods.EMAIL)
                .setAuthenticationMethods(new HashSet<AuthenticationMethod>())
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBuild_setAuthenticationMethods_setContainingNull() {
        HashSet<AuthenticationMethod> authMethods = new HashSet<>();
        authMethods.add(null);
        new HintRetrieveRequest.Builder(AuthenticationMethods.EMAIL)
                .setAuthenticationMethods(authMethods)
                .build();
    }

    @SuppressWarnings("ConstantConditions")
    @Test(expected = IllegalArgumentException.class)
    public void testBuild_addAuthenticationMethod_nullUri() {
        new HintRetrieveRequest.Builder(AuthenticationMethods.EMAIL)
                .addAuthenticationMethod(null)
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBuild_setAdditionalProperties_nullKey() {
        HashMap<String, byte[]> additionalProps = new HashMap<>();
        additionalProps.put(null, new byte[0]);
        new HintRetrieveRequest.Builder(AuthenticationMethods.EMAIL)
                .setAdditionalProperties(additionalProps)
                .build();
    }

    @SuppressWarnings("ConstantConditions")
    @Test(expected = IllegalArgumentException.class)
    public void testBuild_setAdditionalProperty_nullValue() {
        HashMap<String, byte[]> additionalProps = new HashMap<>();
        additionalProps.put("a", null);
        new HintRetrieveRequest.Builder(AuthenticationMethods.EMAIL)
                .setAdditionalProperties(additionalProps)
                .build();
    }

    @SuppressWarnings("ConstantConditions")
    @Test(expected = IllegalArgumentException.class)
    public void testBuild_setPasswordSpecification_null() {
        new HintRetrieveRequest.Builder(AuthenticationMethods.EMAIL)
                .setPasswordSpecification(null)
                .build();
    }
}