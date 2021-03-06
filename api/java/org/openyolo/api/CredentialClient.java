/*
 * Copyright 2016 The OpenYOLO Authors. All Rights Reserved.
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

package org.openyolo.api;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.openyolo.protocol.ProtocolConstants.CREDENTIAL_DATA_TYPE;
import static org.openyolo.protocol.ProtocolConstants.DELETE_CREDENTIAL_ACTION;
import static org.openyolo.protocol.ProtocolConstants.EXTRA_DELETE_REQUEST;
import static org.openyolo.protocol.ProtocolConstants.EXTRA_HINT_REQUEST;
import static org.openyolo.protocol.ProtocolConstants.EXTRA_HINT_RESULT;
import static org.openyolo.protocol.ProtocolConstants.EXTRA_RETRIEVE_RESULT;
import static org.openyolo.protocol.ProtocolConstants.EXTRA_SAVE_REQUEST;
import static org.openyolo.protocol.ProtocolConstants.EXTRA_SAVE_RESULT;
import static org.openyolo.protocol.ProtocolConstants.HINT_CREDENTIAL_ACTION;
import static org.openyolo.protocol.ProtocolConstants.OPENYOLO_CATEGORY;
import static org.openyolo.protocol.ProtocolConstants.SAVE_CREDENTIAL_ACTION;
import static org.valid4j.Assertive.require;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import com.google.bbq.BroadcastQueryClient;
import com.google.bbq.QueryCallback;
import com.google.bbq.QueryResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.openyolo.api.ui.ProviderPickerActivity;
import org.openyolo.protocol.Credential;
import org.openyolo.protocol.CredentialDeleteRequest;
import org.openyolo.protocol.CredentialDeleteResult;
import org.openyolo.protocol.CredentialRetrieveRequest;
import org.openyolo.protocol.CredentialRetrieveResult;
import org.openyolo.protocol.CredentialSaveRequest;
import org.openyolo.protocol.CredentialSaveResult;
import org.openyolo.protocol.HintRetrieveRequest;
import org.openyolo.protocol.HintRetrieveResult;
import org.openyolo.protocol.MalformedDataException;
import org.openyolo.protocol.Protobufs;
import org.openyolo.protocol.Protobufs.CredentialRetrieveBbqResponse;
import org.openyolo.protocol.RetrieveBbqResponse;
import org.openyolo.protocol.internal.IntentUtil;

/**
 * Interact with credential providers on the device which support OpenYOLO.
 */
public class CredentialClient {

    private static final String LOG_TAG = "CredentialClient";

    private static final AtomicReference<CredentialClient> INSTANCE_REF
            = new AtomicReference<>();

    private final Context mApplicationContext;

    private final BroadcastQueryClient mQueryClient;

    /**
     * Retrieves the singleton credential client instance associated with the application.
     */
    public static CredentialClient getApplicationBoundInstance(Context context) {
        Context applicationContext = context.getApplicationContext();
        CredentialClient client = new CredentialClient(applicationContext);
        if (!INSTANCE_REF.compareAndSet(null, client)) {
            client = INSTANCE_REF.get();
        }

        return client;
    }

    CredentialClient(Context applicationContext) {
        mApplicationContext = applicationContext;
        mQueryClient = BroadcastQueryClient.getApplicationBoundInstance(applicationContext);
    }

    /**
     * Provides an intent to request a login hint. This will target the user's prefered credential
     * provider, if this can be determined. If no providers are available, {@code null} is
     * returned.
     */
    @Nullable
    public Intent getHintRetrieveIntent(final HintRetrieveRequest request) {
        List<ComponentName> hintProviders = findProviders(HINT_CREDENTIAL_ACTION);

        if (hintProviders.isEmpty()) {
            return null;
        }

        byte[] encodedRequest = request.toProtocolBuffer().toByteArray();

        // if there is a preferred provider, directly invoke it.
        ComponentName preferredProviderActivity = getPreferredProvider(hintProviders);
        if (preferredProviderActivity != null) {
            return createHintIntent(preferredProviderActivity, encodedRequest);
        }

        // otherwise, display a picker for all the providers.
        ArrayList<Intent> hintIntents = new ArrayList<>();
        for (ComponentName providerActivity : hintProviders) {
            hintIntents.add(createHintIntent(providerActivity, encodedRequest));
        }

        return ProviderPickerActivity.createHintIntent(mApplicationContext, hintIntents);
    }

    /**
     * Requests and retrieves any available credentials from the credential providers on the device.
     * @param request  Properties of the credential request
     * @param callback Handler for the result of the credential request.
     */
    public void retrieve(
            final CredentialRetrieveRequest request,
            final RetrieveCallback callback) {
        mQueryClient.queryFor(
                CREDENTIAL_DATA_TYPE,
                request.toProtocolBuffer(),
                new CredentialRetrieveQueryCallback(callback));
    }

    /**
     * Provides an intent to save the provided credential. If no compatible credential providers
     * exist on the device, {@code null} will be returned. The intent should be started with a
     * call to
     * {@link android.app.Activity#startActivityForResult(Intent, int) startActivityForResult}.
     *
     * <p>If the credential is successfully saved, {@link android.app.Activity#RESULT_OK} will
     * be returned. Otherwise, {@link android.app.Activity#RESULT_CANCELED} will be returned.
     */
    @Nullable
    public Intent getSaveIntent(final CredentialSaveRequest saveRequest) {
        List<ComponentName> saveProviders = findProviders(SAVE_CREDENTIAL_ACTION);

        if (saveProviders.isEmpty()) {
            return null;
        }

        byte[] encodedSaveRequest = saveRequest.toProtocolBuffer().toByteArray();

        // if there is a preferred provider, directly invoke it.
        ComponentName preferredSaveActivity = getPreferredProvider(saveProviders);
        if (preferredSaveActivity != null) {
            return createSaveIntent(
                    preferredSaveActivity,
                    encodedSaveRequest);
        }

        // otherwise, display a picker for all the providers.
        ArrayList<Intent> saveIntents = new ArrayList<>(saveProviders.size());
        for (ComponentName providerActivity : saveProviders) {
            saveIntents.add(createSaveIntent(
                    providerActivity,
                    encodedSaveRequest));
        }

        return ProviderPickerActivity.createSaveIntent(mApplicationContext, saveIntents);
    }

    /**
     * Provides an intent to delete a credential. If no compatible credential providers exist
     * on the device, {@code null} will be returned. The intent should be started with a call
     * to {@link android.app.Activity#startActivityForResult(Intent, int) startActivityForResult}.
     *
     * <p>Upon completion of the request, the result data will be returned in the Intent passed
     * to {@link android.app.Activity#onActivityResult(int, int, android.content.Intent)}, and
     * can be extracted by calling {@link #getDeleteResult(Intent)}.
     */
    @Nullable
    public Intent getDeleteIntent(@NonNull Credential credentialToDelete) {
        require(credentialToDelete, notNullValue());
        List<ComponentName> deleteProviders =
                findProviders(DELETE_CREDENTIAL_ACTION);

        if (deleteProviders.isEmpty()) {
            return null;
        }

        CredentialDeleteRequest request =
                new CredentialDeleteRequest.Builder(credentialToDelete).build();

        byte[] encodedRequest = request.toProtobuf().toByteArray();

        // if there is a preferred provider, directly invoke it.
        ComponentName preferredDeleteActivity = getPreferredProvider(deleteProviders);
        if (preferredDeleteActivity != null) {
            return createDeleteIntent(preferredDeleteActivity, encodedRequest);
        }

        // otherwise, display a picker for all the providers
        ArrayList<Intent> deleteIntents = new ArrayList<>(deleteProviders.size());
        for (ComponentName providerActivity : deleteProviders) {
            deleteIntents.add(createDeleteIntent(
                    providerActivity,
                    encodedRequest));
        }

        return ProviderPickerActivity.createDeleteIntent(mApplicationContext, deleteIntents);
    }

    private Intent createIntent(ComponentName component, String action) {
        Intent intent = new Intent(action);
        intent.setClassName(
                component.getPackageName(),
                component.getClassName());
        intent.addCategory(OPENYOLO_CATEGORY);
        return intent;
    }

    private Intent createHintIntent(ComponentName providerActivity, byte[] hintRequest) {
        Intent hintIntent = createIntent(providerActivity, HINT_CREDENTIAL_ACTION);
        hintIntent.putExtra(EXTRA_HINT_REQUEST, hintRequest);
        return hintIntent;
    }

    private Intent createSaveIntent(
            ComponentName providerActivity,
            byte[] encodedSaveRequest) {
        Intent saveIntent = createIntent(providerActivity, SAVE_CREDENTIAL_ACTION);
        saveIntent.putExtra(EXTRA_SAVE_REQUEST, encodedSaveRequest);
        return saveIntent;
    }

    private Intent createDeleteIntent(
            ComponentName providerActivity,
            byte[] deleteRequest) {
        Intent deleteIntent = createIntent(providerActivity, DELETE_CREDENTIAL_ACTION);
        deleteIntent.putExtra(EXTRA_DELETE_REQUEST, deleteRequest);
        return deleteIntent;
    }

    /**
     * Extracts a credential from the data returned via
     * {@link android.app.Activity#onActivityResult(int, int, Intent) onActivityResult},
     * after a credential retrieve intent completes.
     */
    @NonNull
    public CredentialRetrieveResult getCredentialRetrieveResult(
            @Nullable Intent resultData) {
        if (resultData == null) {
            Log.i(LOG_TAG, "resultData is null, returning default response");
            return createDefaultCredentialRetrieveResult();
        }

        if (!resultData.hasExtra(EXTRA_RETRIEVE_RESULT)) {
            Log.i(LOG_TAG, "retrieve result missing from response, returning default response");
            return createDefaultCredentialRetrieveResult();
        }

        byte[] resultBytes = resultData.getByteArrayExtra(EXTRA_RETRIEVE_RESULT);
        if (resultBytes == null) {
            Log.i(LOG_TAG, "No retrieve result found in result data, returning default response");
            return createDefaultCredentialRetrieveResult();
        }

        try {
            return CredentialRetrieveResult.fromProtobufBytes(resultBytes);
        } catch (MalformedDataException ex) {
            Log.e(LOG_TAG, "validation of result proto failed, returning default response", ex);
            return createDefaultCredentialRetrieveResult();
        }
    }

    /**
     * Extracts the result of a hint retrieve request from the intent data returned by a provider.
     */
    @NonNull
    public HintRetrieveResult getHintRetrieveResult(Intent resultData) {
        if (resultData == null) {
            Log.i(LOG_TAG, "resultData is null, returning default response");
            return createDefaultHintRetrieveResult();
        }

        if (!resultData.hasExtra(EXTRA_HINT_RESULT)) {
            Log.i(LOG_TAG, "hint result missing from response, returning default response");
            return createDefaultHintRetrieveResult();
        }

        byte[] resultBytes = resultData.getByteArrayExtra(EXTRA_HINT_RESULT);
        if (resultBytes == null) {
            Log.i(LOG_TAG, "No hint result found in result data, returning default response");
            return createDefaultHintRetrieveResult();
        }

        try {
            return HintRetrieveResult.fromProtobufBytes(resultBytes);
        } catch (MalformedDataException ex) {
            Log.e(LOG_TAG, "hint result is malformed, returning default response", ex);
            return createDefaultHintRetrieveResult();
        }
    }

    /**
     * Extracts the result of a credential save request from the intent data returned by a provider.
     */
    @NonNull
    public CredentialSaveResult getCredentialSaveResult(Intent resultData) {
        if (resultData == null) {
            Log.i(LOG_TAG, "resultData is null, returning default response");
            return createDefaultCredentialSaveResult();
        }

        final byte[] resultBytes = resultData.getByteArrayExtra(EXTRA_SAVE_RESULT);
        if (resultBytes == null) {
            Log.i(LOG_TAG, "No save result found in result data, returning default response");
            return createDefaultCredentialSaveResult();
        }

        try {
            return CredentialSaveResult.fromProtobufBytes(resultBytes);
        } catch (MalformedDataException ex) {
            Log.e(LOG_TAG, "save result is malformed, returning default response", ex);
            return createDefaultCredentialSaveResult();
        }
    }

    @NonNull
    private static CredentialSaveResult createDefaultCredentialSaveResult() {
        return new CredentialSaveResult.Builder(CredentialSaveResult.CODE_UNKNOWN)
                .build();
    }

    /**
     * Extracts the result of a credential deletion request from the intent data returned by
     * a provider. If the result is missing or malformed, {@link CredentialDeleteResult#UNKNOWN}
     * is returned.
     */
    @NonNull
    public CredentialDeleteResult getDeleteResult(Intent resultData) {
        try {
            return CredentialDeleteResult.fromResultIntentData(resultData);
        } catch (MalformedDataException ex) {
            Log.w(LOG_TAG, "delete result is missing or malformed, returning default response", ex);
            return CredentialDeleteResult.UNKNOWN;
        }
    }

    @NonNull
    private static CredentialRetrieveResult createDefaultCredentialRetrieveResult() {
        return new CredentialRetrieveResult.Builder(CredentialRetrieveResult.CODE_UNKNOWN)
                .build();
    }

    @NonNull
    private static HintRetrieveResult createDefaultHintRetrieveResult() {
        return HintRetrieveResult.UNKNOWN;
    }

    private List<ComponentName> findProviders(@NonNull String action) {
        Intent saveIntent = new Intent(action);
        saveIntent.addCategory(OPENYOLO_CATEGORY);

        List<ResolveInfo> resolveInfos =
                mApplicationContext.getPackageManager().queryIntentActivities(saveIntent, 0);

        ArrayList<ComponentName> responders = new ArrayList<>();
        for (ResolveInfo info : resolveInfos) {
            responders.add(new ComponentName(
                    info.activityInfo.packageName,
                    info.activityInfo.name));
        }

        return responders;
    }

    @Nullable
    private ComponentName getPreferredProvider(@NonNull List<ComponentName> providers) {
        // In the future, the user will be able to explicitly set their preferred provider in
        // their device settings. For now, we heuristically determine the preferred provider based
        // on the following rules:

        // 1. If there are any unknown providers on the device, there is no preferred provider.
        List<ComponentName> knownProviders = filterToKnownProviders(providers);
        if (knownProviders.size() != providers.size()) {
            return null;
        }

        // 2. If there are no known providers, then there is no preferred provider. Unknown
        // providers are not trusted for automatic usage.
        if (knownProviders.isEmpty()) {
            return null;
        }

        // 3. If there is exactly one provider on the device and it is known,
        // it is the preferred provider.
        if (knownProviders.size() == 1) {
            return knownProviders.get(0);
        }

        // 3. If there are exactly two known providers on the device, and one of them is Google,
        // choose the other one.
        // This reflects the reality that Google is pre-installed on most devices, whereas a second
        // provider would likely have been explicitly installed by the user - it is likely that
        // their intent is to use this other provider.
        providers = filterOutGoogle(knownProviders);
        if (providers.size() == 1) {
            return knownProviders.get(0);
        }

        // 4. Otherwise, there is no preferred provider.
        return null;
    }

    private List<ComponentName> filterToKnownProviders(List<ComponentName> providers) {
        KnownProviders knownProviders =
                KnownProviders.getApplicationBoundInstance(mApplicationContext);

        ArrayList<ComponentName> filteredProviders = new ArrayList<>();
        for (ComponentName provider : providers) {
            if (knownProviders.isKnown(provider.getPackageName())) {
                filteredProviders.add(provider);
            }
        }

        return filteredProviders;
    }

    private List<ComponentName> filterOutGoogle(List<ComponentName> providers) {
        Iterator<ComponentName> providerIter = providers.iterator();
        while (providerIter.hasNext()) {
            ComponentName provider = providerIter.next();
            if (provider.getPackageName().equals(
                    KnownProviders.GOOGLE_PROVIDER.getAndroidPackageName())) {
                providerIter.remove();
            }
        }

        return providers;
    }

    private class CredentialRetrieveQueryCallback implements QueryCallback {

        private final RetrieveCallback mCallback;

        CredentialRetrieveQueryCallback(RetrieveCallback callback) {
            mCallback = callback;
        }

        @Override
        public void onResponse(long queryId, List<QueryResponse> queryResponses) {
            ArrayList<Intent> retrieveIntents = new ArrayList<>();
            Map<String, CredentialRetrieveBbqResponse> protoResponses = new HashMap<>();
            for (QueryResponse queryResponse : queryResponses) {
                Protobufs.CredentialRetrieveBbqResponse response;
                try {
                    response = Protobufs.CredentialRetrieveBbqResponse.parseFrom(
                            queryResponse.responseMessage);
                } catch (IOException e) {
                    Log.w(LOG_TAG, "Failed to decode credential retrieve response");
                    continue;
                }

                protoResponses.put(queryResponse.responderPackage, response);

                if (!response.getRetrieveIntent().isEmpty()) {
                    // TODO: handle failure to decode retrieve intent gracefully
                    // (i.e. ignore the response)
                    Intent retrieveIntent =
                            IntentUtil.fromBytes(response.getRetrieveIntent().toByteArray());

                    if (!queryResponse.responderPackage.equals(
                            retrieveIntent.getComponent().getPackageName())) {
                        Log.w(LOG_TAG, "Package mismatch between provider and retrieve intent");
                        continue;
                    }

                    retrieveIntents.add(retrieveIntent);
                }
            }

            Intent retrieveIntent = null;
            if (retrieveIntents.size() == 1) {
                retrieveIntent = retrieveIntents.get(0);
            } else if (retrieveIntents.size() > 1) {
                retrieveIntent = ProviderPickerActivity.createRetrieveIntent(
                        mApplicationContext,
                        retrieveIntents);
            }

            mCallback.onComplete(
                    new RetrieveBbqResponse.Builder()
                            .setProtoResponses(protoResponses)
                            .setRetrieveIntent(retrieveIntent)
                            .build(),
                    null);
        }
    }
}
